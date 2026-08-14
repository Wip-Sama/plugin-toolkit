package org.wip.plugintoolkit.features.job.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import org.wip.plugintoolkit.api.JobHandle
import org.wip.plugintoolkit.api.PluginLogger
import org.wip.plugintoolkit.api.RelativePath
import org.wip.plugintoolkit.core.loomDispatcher
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobHistoryEntry
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.ScheduledJob
import org.wip.plugintoolkit.features.job.model.canBeScheduled
import org.wip.plugintoolkit.features.job.model.normalizedScheduleInterval
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.plugin.logic.DefaultPluginFileSystem
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid

class JobManager(
    /** Injected [AppScope] for managing job lifecycles and worker coordination. */
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository
) {
    internal var scheduleReadinessOverride: ((BackgroundJob) -> Boolean)? = null
    private val scheduleFlowJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val maxConcurrentJobs get() = settingsRepository.settings.value.jobs.maxConcurrentJobs
    private val maxEndedJobs get() = settingsRepository.settings.value.jobs.maxEndedJobs
    private val maxHistoryLength get() = settingsRepository.settings.value.jobs.maxHistoryLength

    private val _jobs = MutableStateFlow<List<BackgroundJob>>(emptyList())
    val jobs: StateFlow<List<BackgroundJob>> = _jobs.asStateFlow()

    val activeJobIds: StateFlow<Set<String>> = _jobs.map { list ->
        list.filter { it.status == JobStatus.Queued || it.status == JobStatus.Running }.map { it.id }.toSet()
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _endedJobs = MutableStateFlow<List<BackgroundJob>>(emptyList())
    val endedJobs: StateFlow<List<BackgroundJob>> = _endedJobs.asStateFlow()

    private val _jobProgress = MutableStateFlow<Map<String, org.wip.plugintoolkit.features.job.model.JobProgress>>(emptyMap())
    val jobProgress: StateFlow<Map<String, org.wip.plugintoolkit.features.job.model.JobProgress>> = _jobProgress.asStateFlow()

    private val _history = MutableStateFlow<List<JobHistoryEntry>>(emptyList())
    val history: StateFlow<List<JobHistoryEntry>> = _history.asStateFlow()

    private val _schedules = MutableStateFlow<List<ScheduledJob>>(emptyList())
    val schedules: StateFlow<List<ScheduledJob>> = _schedules.asStateFlow()
    private val _scheduleLoadFailed = MutableStateFlow(false)
    val scheduleLoadFailed: StateFlow<Boolean> = _scheduleLoadFailed.asStateFlow()

    private val _jobLogs = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val jobLogs: StateFlow<Map<String, List<String>>> = _jobLogs.asStateFlow()

    private val workers = mutableListOf<JobWorker>()

    // Channel to signal workers that a new job is available.
    // CONFLATED means if multiple jobs are added rapidly, we wake up at least one worker.
    private val jobSignal = Channel<Unit>(Channel.CONFLATED)

    // Mutex strictly for managing the active handles map, avoiding global contention.
    private val handlesMutex = Mutex()
    private val activeJobHandles = mutableMapOf<String, JobHandle>()

    private val settingsPersistence: org.wip.plugintoolkit.features.settings.logic.SettingsPersistence =
        settingsRepository.persistence
    private val jobRepository = JobRepository(settingsPersistence)
    private val scheduleRepository = ScheduleRepository(settingsPersistence)
    private val scheduleMutex = Mutex()
    private val scheduleStartMutex = Mutex()
    private val scheduleSignal = Channel<Unit>(Channel.CONFLATED)
    private var schedulerStarted = false
    private var schedulesLoaded = false
    private var consecutiveSchedulePersistenceFailures = 0
    private var scheduleRetryNotBefore: kotlin.time.Instant? = null

    init {
        scope.launch {
            try {
                val savedJobs = jobRepository.loadJobs()
                _jobs.update { currentJobs ->
                    val newJobIds = currentJobs.map { it.id }.toSet()
                    savedJobs.filterNot { it.id in newJobIds } + currentJobs
                }

                // Wake up workers in case we loaded queued jobs
                if (_jobs.value.any { it.status == JobStatus.Queued }) {
                    jobSignal.trySend(Unit)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Logger.e(e) { "Error loading jobs from repository" }
            }

            // Start workers after initial load
            startWorkers()

            // Observe and save job state changes
            launch {
                _jobs.collect { currentJobs ->
                    jobRepository.saveJobs(currentJobs)
                }
            }
        }
        // Hydrate schedule state eagerly so the Scheduler UI never presents an empty,
        // mutable snapshot while startup is still loading plugins.
        scope.launch {
            scheduleMutex.withLock { hydrateSchedulesLocked() }
        }
    }

    /** Starts recurring execution after startup has finished loading plugins. Safe to call more than once. */
    suspend fun startScheduler(): Boolean = scheduleStartMutex.withLock start@{
        val initialized = scheduleMutex.withLock { hydrateSchedulesLocked() }
        if (schedulerStarted) return@start initialized

        schedulerStarted = true
        scope.launch {
            while (isActive) {
                val hydrated = scheduleMutex.withLock { hydrateSchedulesLocked() }
                if (!hydrated) {
                    withTimeoutOrNull(SCHEDULE_LOAD_RETRY_MS) { scheduleSignal.receive() }
                    continue
                }
                val now = Clock.System.now()
                runDueSchedules(now)
                val waitMillis = nextSchedulerWaitMillis(Clock.System.now())
                withTimeoutOrNull(waitMillis) { scheduleSignal.receive() }
            }
        }
        initialized
    }

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    suspend fun scheduleJob(job: BackgroundJob, intervalMinutes: Long = 24 * 60L): ScheduledJob? = scheduleMutex.withLock {
        if (!hydrateSchedulesLocked()) return@withLock null
        if (!job.type.canBeScheduled()) {
            Logger.w { "Refusing to schedule unsupported job type ${job.type}" }
            return@withLock null
        }
        val now = Clock.System.now()
        val normalizedInterval = intervalMinutes.normalizedScheduleInterval()
        val schedule = ScheduledJob(
            id = "schedule-${Uuid.random()}",
            jobTemplate = job.asFreshRun(now),
            intervalMinutes = normalizedInterval,
            nextRunAt = now + normalizedInterval.minutes
        )
        if (!replaceSchedules(_schedules.value + schedule)) return@withLock null
        return schedule
    }

    suspend fun removeSchedule(id: String): Boolean = scheduleMutex.withLock {
        if (!hydrateSchedulesLocked()) return@withLock false
        val updated = _schedules.value.filterNot { it.id == id }
        if (updated == _schedules.value) return@withLock true
        replaceSchedules(updated)
    }

    suspend fun setScheduleEnabled(id: String, enabled: Boolean): Boolean = scheduleMutex.withLock {
        if (!hydrateSchedulesLocked()) return@withLock false
        val updated = _schedules.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        if (updated == _schedules.value) return@withLock true
        replaceSchedules(updated)
    }

    suspend fun runScheduleNow(id: String): Boolean = scheduleMutex.withLock {
        if (!hydrateSchedulesLocked()) return@withLock false
        val now = Clock.System.now()
        val current = _schedules.value
        val schedule = current.firstOrNull { it.id == id } ?: return@withLock false
        val updated = current.map { if (it.id == id) it.afterRun(now) else it }
        if (!replaceSchedules(updated)) return@withLock false
        enqueueJob(schedule.jobTemplate.asFreshRun(now))
        true
    }

    internal suspend fun runDueSchedules(now: kotlin.time.Instant) = scheduleMutex.withLock {
        if (!hydrateSchedulesLocked()) return@withLock
        val current = _schedules.value
        // Do not consume an occurrence until all plugin code needed by the job is available.
        // A failed/hung plugin startup therefore cannot make other schedules miss their run.
        val due = current.filter {
            it.isDue(now) && (scheduleReadinessOverride?.invoke(it.jobTemplate) ?: isScheduledJobReady(it.jobTemplate))
        }
        if (due.isNotEmpty()) {
            val dueIds = due.mapTo(mutableSetOf()) { it.id }
            val updated = current.map { if (it.id in dueIds) it.afterRun(now) else it }
            // Persist the next occurrence first. A crash can skip a run, but cannot replay it twice.
            if (!replaceSchedules(updated)) return@withLock
            due.forEach { enqueueJob(it.jobTemplate.asFreshRun(now)) }
        }
    }

    private fun isScheduledJobReady(job: BackgroundJob): Boolean = when (job.type) {
        org.wip.plugintoolkit.features.job.model.JobType.Capability ->
            PluginLoader.getPluginById(job.pluginId) != null
        org.wip.plugintoolkit.features.job.model.JobType.Flow ->
            isStoredFlowReady(job.capabilityName, mutableSetOf())
        else -> false
    }

    private fun isStoredFlowReady(flowName: String, visited: MutableSet<String>): Boolean {
        if (!visited.add(flowName)) return true
        return runCatching {
            val safeName = flowName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val flowPath = Path("${settingsPersistence.getSettingsDir()}/flows/$safeName.json")
            if (!SystemFileSystem.exists(flowPath)) return@runCatching false
            val content = SystemFileSystem.source(flowPath).buffered().use { it.readString() }
            val flow = scheduleFlowJson.decodeFromString<Flow>(content)
            flow.nodes.all { node ->
                when (node) {
                    is Node.CapabilityNode -> PluginLoader.getPluginById(node.pluginInfo.id) != null
                    is Node.SubFlowNode -> isStoredFlowReady(node.flowName, visited)
                    else -> true
                }
            }
        }.getOrElse { error ->
            Logger.w(error) { "Scheduled flow '$flowName' is not ready for execution" }
            false
        }
    }

    @OptIn(kotlin.uuid.ExperimentalUuidApi::class)
    private fun BackgroundJob.asFreshRun(now: kotlin.time.Instant): BackgroundJob = copy(
        id = "$id-${Uuid.random()}",
        status = JobStatus.Queued,
        enqueuedAt = now,
        startedAt = null,
        completedAt = null,
        errorMessage = null,
        result = null,
        resumeState = null
    )

    private suspend fun replaceSchedules(updated: List<ScheduledJob>): Boolean {
        if (!persistSchedules(updated)) return false
        _schedules.value = updated
        scheduleSignal.trySend(Unit)
        return true
    }

    private suspend fun hydrateSchedulesLocked(): Boolean {
        if (schedulesLoaded) return true
        val loaded = scheduleRepository.load().getOrElse { error ->
            _scheduleLoadFailed.value = true
            Logger.e(error) { "Schedules could not be recovered from persistent storage" }
            return false
        }
        val supported = loaded.filter { it.jobTemplate.type.canBeScheduled() }
        if (supported != loaded && !persistSchedules(supported)) {
            _scheduleLoadFailed.value = true
            return false
        }
        _schedules.value = supported
        schedulesLoaded = true
        _scheduleLoadFailed.value = false
        return true
    }

    private suspend fun persistSchedules(updated: List<ScheduledJob>): Boolean =
        scheduleRepository.save(updated).fold(
            onSuccess = {
                consecutiveSchedulePersistenceFailures = 0
                scheduleRetryNotBefore = null
                true
            },
            onFailure = {
                consecutiveSchedulePersistenceFailures++
                val multiplier = 1L shl (consecutiveSchedulePersistenceFailures - 1).coerceAtMost(4)
                val retryDelay = (SCHEDULER_RETRY_BASE_MS * multiplier).coerceAtMost(MAX_SCHEDULER_WAIT_MS)
                scheduleRetryNotBefore = Clock.System.now() + retryDelay.milliseconds
                Logger.e(it) { "Schedule change was not applied because persistence failed" }
                false
            }
        )

    private fun nextSchedulerWaitMillis(now: kotlin.time.Instant): Long {
        val dueWait = _schedules.value.asSequence()
            .filter { it.enabled }
            .map { (it.nextRunAt - now).inWholeMilliseconds }
            .minOrNull()
            ?.coerceIn(MIN_SCHEDULER_WAIT_MS, MAX_SCHEDULER_WAIT_MS)
            ?: MAX_SCHEDULER_WAIT_MS
        val retryWait = scheduleRetryNotBefore
            ?.let { (it - now).inWholeMilliseconds.coerceAtLeast(0) }
            ?: 0L
        return maxOf(dueWait, retryWait).coerceAtMost(MAX_SCHEDULER_WAIT_MS)
    }

    private fun startWorkers() {
        repeat(maxConcurrentJobs) {
            val worker = JobWorker(it, this, scope)
            workers.add(worker)
            worker.start()
        }
    }

    fun enqueueJob(job: BackgroundJob) {
        // Remove previous ended jobs of the same capability/flow that should not be saved in history
        val jobsToRemove = _endedJobs.value.filter {
            it.pluginId == job.pluginId &&
                    it.capabilityName == job.capabilityName &&
                    !it.keepResult
        }
        if (jobsToRemove.isNotEmpty()) {
            val idsToRemove = jobsToRemove.map { it.id }.toSet()
            _endedJobs.update { currentList ->
                currentList.filterNot { it.id in idsToRemove }
            }
            _jobLogs.update { currentLogs ->
                currentLogs.filterKeys { it !in idsToRemove }
            }
        }

        _jobs.update { currentList ->
            val filtered = if (!job.keepResult) {
                currentList.filterNot {
                    it.pluginId == job.pluginId &&
                            it.capabilityName == job.capabilityName &&
                            !it.keepResult &&
                            (it.status == JobStatus.Completed || it.status == JobStatus.Failed || it.status == JobStatus.Cancelled)
                }
            } else {
                currentList
            }
            filtered + job
        }
        addHistoryEntryInternal(job.id, job.name, "Enqueued")
        Logger.i { "Job ${job.id} (${job.name}) enqueued" }
        jobSignal.trySend(Unit)
    }

    fun updateJob(jobId: String, update: (BackgroundJob) -> BackgroundJob) {
        _jobs.update { currentList ->
            currentList.map { if (it.id == jobId) update(it) else it }
        }
    }

    suspend fun cancelJob(jobId: String, force: Boolean = false) {
        var jobName = ""
        var cancelled = false
        _jobs.update { currentList ->
            val job = currentList.find { it.id == jobId } ?: return@update currentList
            jobName = job.name
            if (job.status == JobStatus.Running || job.status == JobStatus.Queued || job.status == JobStatus.Paused) {
                cancelled = true
                currentList.map {
                    if (it.id == jobId) it.copy(
                        status = JobStatus.Cancelled,
                        completedAt = Clock.System.now()
                    ) else it
                }
            } else {
                currentList
            }
        }

        if (cancelled) {
            handlesMutex.withLock {
                activeJobHandles.remove(jobId)?.cancel(force = force)
            }
            val finishedJob = _jobs.value.find { it.id == jobId }
            if (finishedJob != null) {
                _jobs.update { it.filterNot { k -> k.id == jobId } }
                _endedJobs.update { (listOf(finishedJob) + it).take(maxEndedJobs) }
            }
            addHistoryEntryInternal(jobId, jobName, "Cancelled")
            Logger.i { "Job $jobId ($jobName) cancelled (force=$force)" }
            // Wake up any workers waiting for jobs in case this was the only job they were waiting for
            // or in case we want to re-evaluate the queue immediately.
            jobSignal.trySend(Unit)
        }
    }

    suspend fun pauseJob(jobId: String) {
        var jobName = ""
        var paused = false
        _jobs.update { currentList ->
            val job = currentList.find { it.id == jobId } ?: return@update currentList
            jobName = job.name
            if (job.status == JobStatus.Running || job.status == JobStatus.Queued) {
                paused = true
                currentList.map { if (it.id == jobId) it.copy(status = JobStatus.Paused) else it }
            } else {
                currentList
            }
        }

        if (paused) {
            handlesMutex.withLock {
                activeJobHandles[jobId]?.pause()
            }
            // We don't mark it as paused here yet.
            // We wait for the job execution to finish and return a resumeState.
            addHistoryEntryInternal(jobId, jobName, "Pause Requested")
            Logger.i { "Job $jobId ($jobName) pause requested" }
        }
    }

    suspend fun resumeJob(jobId: String) {
        var resumed = false
        var jobName = ""
        _jobs.update { currentList ->
            val job = currentList.find { it.id == jobId } ?: return@update currentList
            jobName = job.name
            if (job.status == JobStatus.Paused) {
                resumed = true
                currentList.map { if (it.id == jobId) it.copy(status = JobStatus.Queued) else it }
            } else {
                currentList
            }
        }

        if (resumed) {
            addHistoryEntryInternal(jobId, jobName, "Resumed")
            Logger.i { "Job $jobId ($jobName) resumed" }
            jobSignal.trySend(Unit)
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        _jobs.update { currentList ->
            val newList = currentList.toMutableList()
            if (fromIndex in newList.indices && toIndex in newList.indices) {
                val item = newList.removeAt(fromIndex)
                newList.add(toIndex, item)
            }
            newList
        }
    }

    /**
     * Workers call this to wait for a job.
     */
    suspend fun waitForNextJob(): BackgroundJob {
        while (true) {
            var claimedJob: BackgroundJob? = null

            _jobs.update { currentList ->
                // PriorityQueue behavior: FIFO based on list order
                val candidate = currentList.firstOrNull { it.status == JobStatus.Queued }

                if (candidate != null) {
                    claimedJob = candidate.copy(status = JobStatus.Running, startedAt = Clock.System.now())
                    currentList.map { if (it.id == candidate.id) claimedJob else it }
                } else {
                    currentList
                }
            }

            if (claimedJob != null) {
                // If there are more jobs, signal again to wake up other idle workers
                if (_jobs.value.any { it.status == JobStatus.Queued }) {
                    jobSignal.trySend(Unit)
                }
                return claimedJob
            }

            // Wait for signal if no jobs are queued
            Logger.v { "No queued jobs, worker waiting for signal..." }

            // Before receiving, do one more quick check to avoid unnecessary suspension
            if (_jobs.value.any { it.status == JobStatus.Queued }) {
                continue
            }

            jobSignal.receive()
        }
    }

    fun updateJobProgress(jobId: String, progress: Float) {
        _jobProgress.update { current ->
            val currentProgress = current[jobId] ?: org.wip.plugintoolkit.features.job.model.JobProgress()
            current + (jobId to currentProgress.copy(mainProgress = progress))
        }
        val progressPercent = (progress * 100).toInt()
        addJobLog(jobId, "Progress: $progressPercent%", "VERBOSE")
    }

    fun updateCapabilityProgress(jobId: String, capabilityName: String, progress: Float) {
        _jobProgress.update { current ->
            val currentProgress = current[jobId] ?: org.wip.plugintoolkit.features.job.model.JobProgress()
            val updatedCaps = currentProgress.capabilitiesProgress + (capabilityName to progress)
            current + (jobId to currentProgress.copy(capabilitiesProgress = updatedCaps))
        }
    }

    fun removeCapabilityProgress(jobId: String, capabilityName: String) {
        _jobProgress.update { current ->
            val currentProgress = current[jobId] ?: return@update current
            val updatedCaps = currentProgress.capabilitiesProgress - capabilityName
            current + (jobId to currentProgress.copy(capabilitiesProgress = updatedCaps))
        }
    }

    fun addJobLog(jobId: String, message: String, level: String = "INFO") {
        val now = Clock.System.now()
        val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val timestamp = "${local.hour.toString().padStart(2, '0')}:${
            local.minute.toString().padStart(2, '0')
        }:${local.second.toString().padStart(2, '0')}"

        val prefix = if (level == "VERBOSE") "[$jobId] " else ""
        val formattedLog = "[$timestamp] [$level] $prefix$message"

        // Log to global logger as well
        when (level) {
            "VERBOSE" -> Logger.v { "[$jobId] $message" }
            "DEBUG" -> Logger.d { "[$jobId] $message" }
            "INFO" -> Logger.i { "[$jobId] $message" }
            "WARN" -> Logger.w { "[$jobId] $message" }
            "ERROR" -> Logger.e { "[$jobId] $message" }
        }

        _jobLogs.update { currentLogs ->
            val logs = currentLogs[jobId] ?: emptyList()
            val maxLines = settingsRepository.settings.value.jobs.maxLogLines
            val newLogs = logs + formattedLog
            currentLogs + (jobId to if (maxLines == -1) newLogs else newLogs.takeLast(maxLines))
        }
    }

    fun tryCompleteJob(jobId: String, result: String?): Boolean {
        var completed = false
        var jobName = ""
        _jobs.update { currentList ->
            val job = currentList.find { it.id == jobId } ?: return@update currentList
            jobName = job.name
            if (job.status == JobStatus.Running) {
                completed = true
                updateJobProgress(jobId, 1.0f)
                currentList.map {
                    if (it.id == jobId) it.copy(
                        status = JobStatus.Completed,
                        completedAt = Clock.System.now(),
                        result = result
                    ) else it
                }
            } else {
                currentList
            }
        }

        if (completed) {
            val finishedJob = _jobs.value.find { it.id == jobId }
            if (finishedJob != null) {
                _jobs.update { it.filterNot { k -> k.id == jobId } }
                _endedJobs.update { (listOf(finishedJob) + it).take(maxEndedJobs) }
            }
            addHistoryEntryInternal(jobId, jobName, "Completed")
            Logger.i { "Job $jobId ($jobName) completed successfully" }
        } else {
            val job = _jobs.value.find { it.id == jobId }
            Logger.w { "Attempted to complete job $jobId, but it was not in Running state (current: ${job?.status})" }
        }
        return completed
    }

    fun tryFailJob(jobId: String, errorMessage: String?): Boolean {
        var failed = false
        var jobName = ""
        _jobs.update { currentList ->
            val job = currentList.find { it.id == jobId } ?: return@update currentList
            jobName = job.name
            if (job.status == JobStatus.Running) {
                failed = true
                currentList.map {
                    if (it.id == jobId) it.copy(
                        status = JobStatus.Failed,
                        errorMessage = errorMessage,
                        completedAt = Clock.System.now()
                    ) else it
                }
            } else {
                currentList
            }
        }

        if (failed) {
            val finishedJob = _jobs.value.find { it.id == jobId }
            if (finishedJob != null) {
                _jobs.update { it.filterNot { k -> k.id == jobId } }
                _endedJobs.update { (listOf(finishedJob) + it).take(maxEndedJobs) }
            }
            addHistoryEntryInternal(jobId, jobName, "Failed", errorMessage)
            Logger.e { "Job $jobId ($jobName) failed: $errorMessage" }
        } else {
            val job = _jobs.value.find { it.id == jobId }
            Logger.w { "Attempted to fail job $jobId, but it was not in Running state (current: ${job?.status})" }
        }
        return failed
    }

    suspend fun registerJobHandle(jobId: String, handle: JobHandle) {
        val job = _jobs.value.find { it.id == jobId }
        if (job == null || (job.status != JobStatus.Running && job.status != JobStatus.Paused)) {
            handle.cancel(force = true)
            return
        }
        handlesMutex.withLock {
            activeJobHandles[jobId] = handle
        }
    }

    suspend fun unregisterJobHandle(jobId: String) {
        handlesMutex.withLock {
            activeJobHandles.remove(jobId)
        }
    }

    fun tryPauseJob(jobId: String, resumeState: JsonElement): Boolean {
        var paused = false
        var jobName = ""
        _jobs.update { currentList ->
            val job = currentList.find { it.id == jobId } ?: return@update currentList
            jobName = job.name
            if (job.status == JobStatus.Running || job.status == JobStatus.Paused) {
                paused = true
                currentList.map {
                    if (it.id == jobId) it.copy(
                        status = JobStatus.Paused,
                        resumeState = resumeState,
                        completedAt = null // It's not finished
                    ) else it
                }
            } else {
                currentList
            }
        }

        if (paused) {
            addHistoryEntryInternal(jobId, jobName, "Paused")
            Logger.i { "Job $jobId ($jobName) successfully paused with state" }

            // Persist the state
            val job = _jobs.value.find { it.id == jobId }
            if (job != null) {
                saveResumeState(job)
            }
        }
        return paused
    }

    fun getPluginLogger(pkg: String, jobId: String? = null): PluginLogger {
        return object : PluginLogger {
            override fun verbose(message: String) {
                if (jobId != null) addJobLog(jobId, message, "VERBOSE")
                else Logger.v { "[$pkg] $message" }
            }

            override fun debug(message: String) {
                if (jobId != null) addJobLog(jobId, message, "DEBUG")
                else Logger.d { "[$pkg] $message" }
            }

            override fun info(message: String) {
                if (jobId != null) addJobLog(jobId, message, "INFO")
                else Logger.i { "[$pkg] $message" }
            }

            override fun warn(message: String) {
                if (jobId != null) addJobLog(jobId, message, "WARN")
                else Logger.w { "[$pkg] $message" }
            }

            override fun error(message: String, throwable: Throwable?) {
                val msg = message + (throwable?.let { ": ${it.message}" } ?: "")
                if (jobId != null) addJobLog(jobId, msg, "ERROR")
                else Logger.e(throwable ?: Exception()) { "[$pkg] $message" }
            }
        }
    }

    private fun saveResumeState(job: BackgroundJob) {
        // In a real app, this would write to a file in the plugin's cache folder.
        // For now, we rely on the BackgroundJob list persistence if it exists.
        // But the user requested a specific JSON file.
        // We need PluginLoader to get the path.
        val installPath = PluginLoader.getPluginInstallPath(job.pluginId)
        if (installPath != null) {
            scope.launch(loomDispatcher) {
                val fs = DefaultPluginFileSystem.createCacheOnly(installPath)
                val json = Json { prettyPrint = true }
                val stateString = json.encodeToString(JsonElement.serializer(), job.resumeState ?: JsonNull)
                val resumePathResult = RelativePath.from("resumes/${job.id}.json")
                if (resumePathResult.isSuccess) {
                    val resumePath = resumePathResult.getOrThrow()
                    fs.writeTextFile(resumePath, stateString)

                    // Update resumes.json index
                    val indexFileResult = RelativePath.from("resumes.json")
                    if (indexFileResult.isSuccess) {
                        val indexFile = indexFileResult.getOrThrow()
                        val currentIndex = if (fs.exists(indexFile)) {
                            val content = fs.readTextFile(indexFile) ?: "{}"
                            try {
                                json.decodeFromString<Map<String, String>>(content)
                            } catch (e: Exception) {
                                Logger.w(e) { "Error decoding resumes.json index" }
                                emptyMap()
                            }
                        } else emptyMap()

                        val newIndex = currentIndex + (job.id to "resumes/${job.id}.json")
                        fs.writeTextFile(indexFile, json.encodeToString(newIndex))
                    }
                }
            }
        }
    }

    fun addHistoryEntry(jobId: String, jobName: String, event: String, details: String? = null) {
        addHistoryEntryInternal(jobId, jobName, event, details)
    }

    private fun addHistoryEntryInternal(jobId: String, jobName: String, event: String, details: String? = null) {
        val entry = JobHistoryEntry(jobId, jobName, event = event, details = details)
        _history.update { currentList ->
            val newList = listOf(entry) + currentList
            if (newList.size > maxHistoryLength) {
                newList.take(maxHistoryLength)
            } else {
                newList
            }
        }
    }

    fun removeJobs(predicate: (BackgroundJob) -> Boolean) {
        _jobs.update { it.filterNot(predicate) }
    }

    fun clearEndedJob(jobId: String) {
        _endedJobs.update { it.filterNot { k -> k.id == jobId } }
        _jobLogs.update { it - jobId }
    }

    fun clearAllEndedJobs() {
        val endedIds = _endedJobs.value.map { it.id }
        _endedJobs.value = emptyList()
        _jobLogs.update { it.filterKeys { k -> k !in endedIds } }
    }

    suspend fun stopAll() {
        workers.forEach { it.stop() }
        handlesMutex.withLock {
            activeJobHandles.values.forEach { it.cancel(force = true) }
            activeJobHandles.clear()
        }
    }
}

private const val MIN_SCHEDULER_WAIT_MS = 1_000L
private const val MAX_SCHEDULER_WAIT_MS = 60_000L
private const val SCHEDULER_RETRY_BASE_MS = 5_000L
private const val SCHEDULE_LOAD_RETRY_MS = 30_000L
