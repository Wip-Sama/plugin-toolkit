package org.wip.plugintoolkit.features.plugin.sdui

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.ExecutionFileSystem
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.HostFileSystem
import org.wip.plugintoolkit.api.PluginAction
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginFileSystem
import org.wip.plugintoolkit.api.PluginLogger
import org.wip.plugintoolkit.api.PluginNetworkClient
import org.wip.plugintoolkit.api.PluginRequest
import org.wip.plugintoolkit.api.PluginResponse
import org.wip.plugintoolkit.api.PluginSignal
import org.wip.plugintoolkit.api.PluginSignalManager
import org.wip.plugintoolkit.api.PluginStorage
import org.wip.plugintoolkit.api.ProcessWatcher
import org.wip.plugintoolkit.api.ProgressData
import org.wip.plugintoolkit.api.ProgressReporter
import org.wip.plugintoolkit.api.sdui.ButtonNode
import org.wip.plugintoolkit.api.sdui.CapabilityJobSnapshot
import org.wip.plugintoolkit.api.sdui.CapabilityJobStatus
import org.wip.plugintoolkit.api.sdui.CardNode
import org.wip.plugintoolkit.api.sdui.ColumnNode
import org.wip.plugintoolkit.api.sdui.InteractiveUiProcessor
import org.wip.plugintoolkit.api.sdui.SemanticColor
import org.wip.plugintoolkit.api.sdui.SpacingSize
import org.wip.plugintoolkit.api.sdui.TextNode
import org.wip.plugintoolkit.api.sdui.TypographyRole
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.api.sdui.UiModifier
import org.wip.plugintoolkit.api.sdui.UiNodeTree
import org.wip.plugintoolkit.api.sdui.UiSessionContext
import org.wip.plugintoolkit.api.sdui.uiTree
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.logic.DefaultPluginSignalManager
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.Volatile

/**
 * Handle controlling an active interactive UI session.
 */
class PluginUiSessionHandle(
    val sessionId: String,
    val pluginId: String,
    val capabilityName: String,
    val initialParameters: Map<String, JsonElement> = emptyMap(),
    val fileSystem: PluginFileSystem? = null,
    val cacheFileSystem: PluginFileSystem? = null,
    private val sessionScope: CoroutineScope,
    private val transactionalChannel: Channel<UiEvent>,
    private val continuousChannel: Channel<UiEvent>,
    private val _uiNodeTree: MutableStateFlow<UiNodeTree>,
    private val onClosed: (String) -> Unit
) {
    val uiNodeTree: StateFlow<UiNodeTree> = _uiNodeTree.asStateFlow()

    val completionDeferred = CompletableDeferred<ExecutionResult>()

    @Volatile
    var isActive: Boolean = true
        private set

    /**
     * Dispatch an event from the Host UI to the plugin.
     * Transactional commands use a lossless buffer; continuous streams are conflatable.
     */
    fun dispatchEvent(event: UiEvent) {
        if (!isActive) return
        when (event) {
            is UiEvent.Click,
            is UiEvent.PathCommitted,
            is UiEvent.OptionSelected,
            is UiEvent.ToggleChanged,
            is UiEvent.BoardLayerSelected,
            is UiEvent.BoardLayerMoved,
            is UiEvent.BoardLayerResized,
            is UiEvent.BoardTextEdited,
            is UiEvent.BoardTextPlaced,
            is UiEvent.BoardShapeCommitted,
            is UiEvent.BoardLayerDeleted,
            is UiEvent.BoardCanvasClicked,
            is UiEvent.Custom -> {
                transactionalChannel.trySend(event)
            }
            is UiEvent.TextChanged,
            is UiEvent.ValueChanged -> {
                continuousChannel.trySend(event)
            }
        }
    }

    suspend fun awaitResult(): ExecutionResult = completionDeferred.await()

    fun complete(response: PluginResponse) {
        if (!isActive) return
        isActive = false
        completionDeferred.complete(ExecutionResult.Success(response))
        close()
    }

    fun cancel(reason: String = "User cancelled session") {
        if (!isActive) return
        isActive = false
        completionDeferred.complete(ExecutionResult.Error(reason))
        close()
    }

    fun close() {
        isActive = false
        transactionalChannel.close()
        continuousChannel.close()
        sessionScope.cancel()
        onClosed(sessionId)
    }
}

/**
 * Host service managing the lifecycle, capability execution, and reactive event routing for all interactive SDUI sessions.
 */
class PluginUiSessionManager(
    private val appScope: CoroutineScope,
    private val jobManager: JobManager? = null,
    private val pluginManager: PluginManager? = null,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    // Secondary constructor for compatibility with existing callers passing (appScope, dispatcher)
    constructor(appScope: CoroutineScope, dispatcher: CoroutineDispatcher) : this(
        appScope = appScope,
        jobManager = null,
        pluginManager = null,
        dispatcher = dispatcher
    )

    private val activeSessions = mutableMapOf<String, PluginUiSessionHandle>()
    private val sessionLock = Any()
    private var sessionCounter = 0

    fun startSession(
        pluginId: String,
        capabilityName: String,
        context: PluginContext,
        processor: InteractiveUiProcessor,
        initialParameters: Map<String, JsonElement> = emptyMap()
    ): PluginUiSessionHandle {
        val sessionId = synchronized(sessionLock) {
            "session_${pluginId}_${capabilityName}_${++sessionCounter}"
        }

        // Dual-lane event buffering: Lossless transactional lane + conflatable continuous lane
        val transactionalChannel = Channel<UiEvent>(capacity = 64, onBufferOverflow = BufferOverflow.SUSPEND)
        val continuousChannel = Channel<UiEvent>(capacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        val mergedEvents = merge(
            transactionalChannel.receiveAsFlow(),
            continuousChannel.receiveAsFlow().conflate()
        )

        val initialTree = uiTree {
            column(spacing = SpacingSize.MEDIUM) {
                text("Loading interface...", role = TypographyRole.BODY_MEDIUM)
            }
        }
        val treeState = MutableStateFlow(initialTree)

        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            Logger.e(throwable) { "Unhandled exception inside plugin SDUI session $sessionId" }
            val errorTree = uiTree {
                card(title = "Plugin Error") {
                    column(spacing = SpacingSize.SMALL) {
                        text(
                            text = throwable.message ?: "An unexpected error occurred in the plugin interface.",
                            color = SemanticColor.ERROR,
                            role = TypographyRole.BODY_MEDIUM
                        )
                        button(id = "retry_session", label = "Reset Session", isPrimary = false)
                    }
                }
            }
            treeState.value = errorTree
        }

        val sessionJob = SupervisorJob(appScope.coroutineContext[kotlinx.coroutines.Job])
        val sessionScope = CoroutineScope(dispatcher + sessionJob + exceptionHandler)

        val handle = PluginUiSessionHandle(
            sessionId = sessionId,
            pluginId = pluginId,
            capabilityName = capabilityName,
            initialParameters = initialParameters,
            fileSystem = context.fileSystem,
            cacheFileSystem = context.cacheFileSystem,
            sessionScope = sessionScope,
            transactionalChannel = transactionalChannel,
            continuousChannel = continuousChannel,
            _uiNodeTree = treeState,
            onClosed = { id ->
                synchronized(sessionLock) {
                    activeSessions.remove(id)
                }
            }
        )

        // Reactive flows for settings, capability locks, and executed capability jobs
        val _settingsFlow = MutableStateFlow(context.settings)
        val _locksFlow = MutableStateFlow<Map<String, Boolean>>(emptyMap())
        val _capabilityJobs = MutableStateFlow<List<CapabilityJobSnapshot>>(emptyList())
        var executionJobCounter = 0

        // Synchronize settings from PluginManager if available
        if (pluginManager != null) {
            sessionScope.launch {
                pluginManager.pluginSettingsState.collect { stores ->
                    stores[pluginId]?.let { store ->
                        _settingsFlow.value = store.settings
                    }
                }
            }
            sessionScope.launch {
                pluginManager.pluginLocksState.collect { allLocks ->
                    _locksFlow.value = allLocks[pluginId] ?: emptyMap()
                }
            }
            sessionScope.launch {
                try {
                    pluginManager.refreshLocks(pluginId)
                } catch (t: Throwable) {
                    Logger.w(t) { "Failed initial lock refresh for $pluginId" }
                }
            }
        }

        class InlineJobController(
            val job: kotlinx.coroutines.Job,
            val signals: PluginSignalManager,
            val onPause: suspend () -> Unit = {},
            val onResume: suspend () -> Unit = {},
            val onCancel: suspend () -> Unit = {}
        ) {
            suspend fun pause() {
                signals.sendSignal(PluginSignal.PAUSE)
                onPause()
            }
            suspend fun resume() {
                onResume()
            }
            suspend fun cancel() {
                signals.sendSignal(PluginSignal.CANCEL)
                job.cancel()
                onCancel()
            }
        }

        val inlineControllers = ConcurrentHashMap<String, InlineJobController>()

        fun executeInlineCapabilityInternal(
            targetCapability: String,
            parameters: Map<String, JsonElement>,
            completionDeferred: CompletableDeferred<ExecutionResult>? = null
        ): String {
            val jobId = "inline_${pluginId}_${targetCapability}_${Clock.System.now().toEpochMilliseconds()}_${++executionJobCounter}"
            val initialSnapshot = CapabilityJobSnapshot(
                jobId = jobId,
                capabilityName = targetCapability,
                status = CapabilityJobStatus.RUNNING,
                startedAtEpochMs = Clock.System.now().toEpochMilliseconds()
            )
            _capabilityJobs.update { it + initialSnapshot }

            val inlineSignals = DefaultPluginSignalManager()
            val inlineLogs = mutableListOf<String>()

            val inlineLogger = object : PluginLogger {
                private fun appendLog(msg: String) {
                    context.logger.info("[$targetCapability] $msg")
                    synchronized(inlineLogs) {
                        inlineLogs.add(msg)
                        val trimmed = inlineLogs.takeLast(100)
                        _capabilityJobs.update { list ->
                            list.map { snap ->
                                if (snap.jobId == jobId) snap.copy(recentLogs = trimmed) else snap
                            }
                        }
                    }
                }
                override fun verbose(message: String) = appendLog("[VERBOSE] $message")
                override fun debug(message: String) = appendLog("[DEBUG] $message")
                override fun info(message: String) = appendLog("[INFO] $message")
                override fun warn(message: String) = appendLog("[WARN] $message")
                override fun error(message: String, throwable: Throwable?) = appendLog("[ERROR] $message" + (throwable?.let { " - ${it.message}" } ?: ""))
            }

            val inlineProgress = object : ProgressReporter {
                override fun report(progress: Float) {
                    report(ProgressData.percentage(progress))
                }
                override fun report(progress: Float, message: String?) {
                    report(ProgressData.percentage(progress, message))
                }
                override fun report(data: ProgressData) {
                    _capabilityJobs.update { list ->
                        list.map { snap ->
                            if (snap.jobId == jobId) snap.copy(primaryProgress = data) else snap
                        }
                    }
                }
                override fun reportSecondary(progress: Float) {
                    reportSecondary(ProgressData.percentage(progress))
                }
                override fun reportSecondary(progress: Float, message: String?) {
                    reportSecondary(ProgressData.percentage(progress, message))
                }
                override fun reportSecondary(data: ProgressData) {
                    _capabilityJobs.update { list ->
                        list.map { snap ->
                            if (snap.jobId == jobId) snap.copy(secondaryProgress = data) else snap
                        }
                    }
                }
                override fun reportSubProgress(name: String, data: ProgressData) {
                    _capabilityJobs.update { list ->
                        list.map { snap ->
                            if (snap.jobId == jobId) snap.copy(subProgress = snap.subProgress + (name to data)) else snap
                        }
                    }
                }
            }

            val inlineContext = object : PluginContext {
                override val logger: PluginLogger get() = inlineLogger
                override val progress: ProgressReporter get() = inlineProgress
                override val fileSystem: PluginFileSystem get() = context.fileSystem
                override val cacheFileSystem: PluginFileSystem get() = context.cacheFileSystem
                override val executionFileSystem: ExecutionFileSystem get() = context.executionFileSystem
                override val hostFileSystem: HostFileSystem get() = context.hostFileSystem
                override val settings: Map<String, JsonElement> get() = _settingsFlow.value
                override val storage: PluginStorage get() = context.storage
                override val signals: PluginSignalManager get() = inlineSignals
                override val networkClient: PluginNetworkClient get() = context.networkClient

                override fun setRequiredAction(actionName: String?) = context.setRequiredAction(actionName)
                override fun showToast(message: String) = context.showToast(message)
                override suspend fun updateSetting(key: String, value: JsonElement) {
                    _settingsFlow.update { it + (key to value) }
                    context.updateSetting(key, value)
                }
                override suspend fun updateSettings(newSettings: Map<String, JsonElement>) {
                    _settingsFlow.update { it + newSettings }
                    context.updateSettings(newSettings)
                }
                override fun recordNetworkUsage(bytesRead: Long, bytesWritten: Long, throughputBytesPerSec: Long?) {
                    context.recordNetworkUsage(bytesRead, bytesWritten, throughputBytesPerSec)
                }
                override fun watchProcess(pid: Long): ProcessWatcher = context.watchProcess(pid)
                override fun getActiveProcessWatchers(): List<ProcessWatcher> = context.getActiveProcessWatchers()
            }

            val coroutineJob = sessionScope.launch {
                try {
                    val proc = PluginLoader.getPluginById(pluginId)?.getProcessor()?.getOrNull()
                    if (proc != null) {
                        val request = PluginRequest(method = targetCapability, parameters = parameters)
                        val execResult = proc.process(request, inlineContext)
                        val terminalStatus = when (execResult) {
                            is ExecutionResult.Success -> CapabilityJobStatus.COMPLETED
                            is ExecutionResult.Paused -> CapabilityJobStatus.PAUSED
                            is ExecutionResult.Error -> CapabilityJobStatus.FAILED
                        }
                        _capabilityJobs.update { list ->
                            list.map { snap ->
                                if (snap.jobId == jobId) snap.copy(
                                    status = terminalStatus,
                                    result = execResult,
                                    completedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                                    errorMessage = (execResult as? ExecutionResult.Error)?.message
                                ) else snap
                            }
                        }
                        completionDeferred?.complete(execResult)
                    } else {
                        val err = ExecutionResult.Error("Plugin processor not found for plugin $pluginId")
                        _capabilityJobs.update { list ->
                            list.map { snap ->
                                if (snap.jobId == jobId) snap.copy(
                                    status = CapabilityJobStatus.FAILED,
                                    result = err,
                                    completedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                                    errorMessage = err.message
                                ) else snap
                            }
                        }
                        completionDeferred?.complete(err)
                    }
                } catch (t: Throwable) {
                    val err = ExecutionResult.Error(t.message ?: "Inline capability execution failed", t)
                    _capabilityJobs.update { list ->
                        list.map { snap ->
                            if (snap.jobId == jobId) snap.copy(
                                status = CapabilityJobStatus.FAILED,
                                result = err,
                                completedAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                                errorMessage = t.message
                            ) else snap
                        }
                    }
                    completionDeferred?.complete(err)
                } finally {
                    inlineControllers.remove(jobId)
                }
            }

            val controller = InlineJobController(
                job = coroutineJob,
                signals = inlineSignals,
                onPause = {
                    _capabilityJobs.update { list ->
                        list.map { snap ->
                            if (snap.jobId == jobId) snap.copy(status = CapabilityJobStatus.PAUSED) else snap
                        }
                    }
                },
                onResume = {
                    _capabilityJobs.update { list ->
                        list.map { snap ->
                            if (snap.jobId == jobId) snap.copy(status = CapabilityJobStatus.RUNNING) else snap
                        }
                    }
                },
                onCancel = {
                    _capabilityJobs.update { list ->
                        list.map { snap ->
                            if (snap.jobId == jobId) snap.copy(
                                status = CapabilityJobStatus.CANCELLED,
                                completedAtEpochMs = Clock.System.now().toEpochMilliseconds()
                            ) else snap
                        }
                    }
                    completionDeferred?.complete(ExecutionResult.Error("Capability cancelled"))
                }
            )
            inlineControllers[jobId] = controller
            return jobId
        }

        val sessionContext = object : UiSessionContext {
            override val sessionId: String get() = handle.sessionId
            override val capabilityName: String get() = handle.capabilityName
            override val initialParameters: Map<String, JsonElement> get() = initialParameters
            override val settingsFlow: StateFlow<Map<String, JsonElement>> get() = _settingsFlow.asStateFlow()
            override val locksFlow: StateFlow<Map<String, Boolean>> get() = _locksFlow.asStateFlow()
            override val capabilityJobs: StateFlow<List<CapabilityJobSnapshot>> get() = _capabilityJobs.asStateFlow()

            override val logger: PluginLogger get() = context.logger
            override val progress: ProgressReporter get() = context.progress
            override val fileSystem: PluginFileSystem get() = context.fileSystem
            override val cacheFileSystem: PluginFileSystem get() = context.cacheFileSystem
            override val executionFileSystem: ExecutionFileSystem get() = context.executionFileSystem
            override val hostFileSystem: HostFileSystem get() = context.hostFileSystem
            override val settings: Map<String, JsonElement> get() = _settingsFlow.value
            override val storage: PluginStorage get() = context.storage
            override val signals: PluginSignalManager get() = context.signals
            override val networkClient: PluginNetworkClient get() = context.networkClient

            override fun completeSession(response: PluginResponse) {
                handle.complete(response)
            }

            override fun cancelSession(reason: String) {
                handle.cancel(reason)
            }

            override fun setRequiredAction(actionName: String?) = context.setRequiredAction(actionName)
            override fun showToast(message: String) = context.showToast(message)

            override suspend fun updateSetting(key: String, value: JsonElement) {
                _settingsFlow.update { it + (key to value) }
                context.updateSetting(key, value)
            }

            override suspend fun updateSettings(newSettings: Map<String, JsonElement>) {
                _settingsFlow.update { it + newSettings }
                context.updateSettings(newSettings)
            }

            override fun recordNetworkUsage(bytesRead: Long, bytesWritten: Long, throughputBytesPerSec: Long?) {
                context.recordNetworkUsage(bytesRead, bytesWritten, throughputBytesPerSec)
            }

            override fun watchProcess(pid: Long): ProcessWatcher = context.watchProcess(pid)

            override fun getActiveProcessWatchers(): List<ProcessWatcher> = context.getActiveProcessWatchers()

            override suspend fun launchCapability(
                capabilityName: String,
                parameters: Map<String, JsonElement>,
                runInBackgroundQueue: Boolean
            ): String {
                if (runInBackgroundQueue && jobManager != null) {
                    val jobId = "job_${pluginId}_${capabilityName}_${Clock.System.now().toEpochMilliseconds()}_${++executionJobCounter}"
                    val backgroundJob = BackgroundJob(
                        id = jobId,
                        name = "$capabilityName ($sessionId)",
                        type = JobType.Capability,
                        pluginId = pluginId,
                        capabilityName = capabilityName,
                        parameters = parameters,
                        keepResult = true,
                        isPausable = true,
                        isCancellable = true
                    )
                    val initialSnapshot = CapabilityJobSnapshot(
                        jobId = jobId,
                        capabilityName = capabilityName,
                        status = CapabilityJobStatus.QUEUED,
                        startedAtEpochMs = Clock.System.now().toEpochMilliseconds()
                    )
                    _capabilityJobs.update { it + initialSnapshot }

                    sessionScope.launch {
                        combine(
                            jobManager.jobs,
                            jobManager.endedJobs,
                            jobManager.jobProgress,
                            jobManager.jobLogs
                        ) { activeList, endedList, progressMap, logsMap ->
                            val bgJob = activeList.find { it.id == jobId } ?: endedList.find { it.id == jobId }
                            if (bgJob != null) {
                                val prog = progressMap[jobId]
                                val logs = logsMap[jobId] ?: emptyList()
                                val status = when (bgJob.status) {
                                    JobStatus.Queued -> CapabilityJobStatus.QUEUED
                                    JobStatus.Running -> CapabilityJobStatus.RUNNING
                                    JobStatus.PauseRequested -> CapabilityJobStatus.PAUSE_REQUESTED
                                    JobStatus.Paused -> CapabilityJobStatus.PAUSED
                                    JobStatus.Completed -> CapabilityJobStatus.COMPLETED
                                    JobStatus.Failed -> CapabilityJobStatus.FAILED
                                    JobStatus.Cancelled -> CapabilityJobStatus.CANCELLED
                                }
                                val primaryProgress = prog?.let {
                                    ProgressData(
                                        fraction = it.mainProgress,
                                        current = it.mainCurrent,
                                        total = it.mainTotal,
                                        unit = it.mainUnit,
                                        message = it.mainMessage,
                                        displayMode = it.mainDisplayMode
                                    )
                                }
                                val secondaryProgress = prog?.secondaryProgress?.let {
                                    ProgressData(
                                        fraction = it,
                                        current = prog.secondaryCurrent,
                                        total = prog.secondaryTotal,
                                        unit = prog.secondaryUnit,
                                        message = prog.secondaryMessage,
                                        displayMode = prog.secondaryDisplayMode
                                    )
                                }
                                val subProgress = prog?.capabilitiesDetailedProgress?.mapValues { (_, v) ->
                                    ProgressData(
                                        fraction = v.progress,
                                        current = v.current,
                                        total = v.total,
                                        unit = v.unit,
                                        message = v.message,
                                        displayMode = v.displayMode
                                    )
                                } ?: emptyMap()

                                val resultObj = if (bgJob.status == JobStatus.Completed && bgJob.result != null) {
                                    try {
                                        Json.decodeFromString<PluginResponse>(bgJob.result).let {
                                            ExecutionResult.Success(it)
                                        }
                                    } catch (_: Exception) {
                                        ExecutionResult.Success(PluginResponse(JsonPrimitive(bgJob.result)))
                                    }
                                } else if (bgJob.status == JobStatus.Failed) {
                                    ExecutionResult.Error(bgJob.errorMessage ?: "Job failed")
                                } else null

                                CapabilityJobSnapshot(
                                    jobId = jobId,
                                    capabilityName = capabilityName,
                                    status = status,
                                    primaryProgress = primaryProgress,
                                    secondaryProgress = secondaryProgress,
                                    subProgress = subProgress,
                                    recentLogs = logs.takeLast(50),
                                    result = resultObj,
                                    errorMessage = bgJob.errorMessage,
                                    startedAtEpochMs = bgJob.startedAt?.toEpochMilliseconds(),
                                    completedAtEpochMs = bgJob.completedAt?.toEpochMilliseconds()
                                )
                            } else null
                        }.collect { snapshot ->
                            if (snapshot != null) {
                                _capabilityJobs.update { list ->
                                    val index = list.indexOfFirst { it.jobId == jobId }
                                    if (index >= 0) {
                                        list.toMutableList().apply { set(index, snapshot) }
                                    } else {
                                        list + snapshot
                                    }
                                }
                            }
                        }
                    }
                    jobManager.enqueueJob(backgroundJob)
                    return jobId
                } else {
                    return executeInlineCapabilityInternal(capabilityName, parameters)
                }
            }

            override suspend fun executeCapability(
                capabilityName: String,
                parameters: Map<String, JsonElement>
            ): ExecutionResult {
                val completionDeferred = CompletableDeferred<ExecutionResult>()
                executeInlineCapabilityInternal(capabilityName, parameters, completionDeferred)
                return completionDeferred.await()
            }

            override suspend fun launchAction(
                actionName: String,
                parameters: Map<String, JsonElement>
            ): Result<Unit> {
                return try {
                    if (pluginManager != null) {
                        val manifest = PluginLoader.getPluginById(pluginId)?.getManifest()?.getOrNull()
                        val action = manifest?.actions?.find { it.functionName == actionName || it.name == actionName }
                            ?: PluginAction(name = actionName, description = "", functionName = actionName)
                        pluginManager.runAction(pluginId, action, parameters)
                        Result.success(Unit)
                    } else {
                        Result.success(Unit)
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            override fun observeCapabilityJob(jobId: String): Flow<CapabilityJobSnapshot> {
                return _capabilityJobs.mapNotNull { list ->
                    list.find { it.jobId == jobId }
                }.distinctUntilChanged()
            }

            override suspend fun pauseCapabilityJob(jobId: String) {
                val inline = inlineControllers[jobId]
                if (inline != null) {
                    inline.pause()
                } else if (jobManager != null) {
                    jobManager.pauseJob(jobId)
                }
            }

            override suspend fun resumeCapabilityJob(jobId: String) {
                val inline = inlineControllers[jobId]
                if (inline != null) {
                    inline.resume()
                } else if (jobManager != null) {
                    jobManager.resumeJob(jobId)
                }
            }

            override suspend fun cancelCapabilityJob(jobId: String) {
                val inline = inlineControllers[jobId]
                if (inline != null) {
                    inline.cancel()
                } else if (jobManager != null) {
                    jobManager.cancelJob(jobId, force = true)
                }
            }
        }

        sessionScope.launch {
            try {
                processor.openUiSession(sessionContext, mergedEvents).collect { rawTree ->
                    val sanitized = UiAstSanitizer.sanitize(rawTree)
                    treeState.value = sanitized
                }
            } catch (t: Throwable) {
                if (t !is kotlinx.coroutines.CancellationException) {
                    Logger.e(t) { "Plugin session $sessionId stream terminated with exception" }
                    handle.cancel(t.message ?: "Session terminated with exception")
                }
            }
        }

        synchronized(sessionLock) {
            activeSessions[sessionId] = handle
        }

        return handle
    }

    fun getSession(sessionId: String): PluginUiSessionHandle? = synchronized(sessionLock) {
        activeSessions[sessionId]
    }

    fun closeSession(sessionId: String) {
        val handle = synchronized(sessionLock) {
            activeSessions.remove(sessionId)
        }
        handle?.close()
    }

    fun closeAll() {
        val sessions = synchronized(sessionLock) {
            val list = activeSessions.values.toList()
            activeSessions.clear()
            list
        }
        sessions.forEach { it.close() }
    }
}
