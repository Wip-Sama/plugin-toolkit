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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.wip.plugintoolkit.api.ExecutionFileSystem
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.HostFileSystem
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginFileSystem
import org.wip.plugintoolkit.api.PluginLogger
import org.wip.plugintoolkit.api.PluginNetworkClient
import org.wip.plugintoolkit.api.PluginResponse
import org.wip.plugintoolkit.api.PluginSignalManager
import org.wip.plugintoolkit.api.PluginStorage
import org.wip.plugintoolkit.api.ProgressReporter
import org.wip.plugintoolkit.api.sdui.ButtonNode
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
import kotlin.concurrent.Volatile

/**
 * Handle controlling an active interactive UI session.
 */
class PluginUiSessionHandle(
    val sessionId: String,
    val pluginId: String,
    val capabilityName: String,
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
 * Host service managing the lifecycle and event routing for all interactive SDUI sessions.
 */
class PluginUiSessionManager(
    private val appScope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val activeSessions = mutableMapOf<String, PluginUiSessionHandle>()
    private val sessionLock = Any()
    private var sessionCounter = 0

    fun startSession(
        pluginId: String,
        capabilityName: String,
        context: PluginContext,
        processor: InteractiveUiProcessor
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

        val sessionContext = object : UiSessionContext {
            override val logger: PluginLogger get() = context.logger
            override val progress: ProgressReporter get() = context.progress
            override val fileSystem: PluginFileSystem get() = context.fileSystem
            override val cacheFileSystem: PluginFileSystem get() = context.cacheFileSystem
            override val executionFileSystem: ExecutionFileSystem get() = context.executionFileSystem
            override val hostFileSystem: HostFileSystem get() = context.hostFileSystem
            override val settings: Map<String, kotlinx.serialization.json.JsonElement> get() = context.settings
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
