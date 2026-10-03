package org.wip.plugintoolkit.features.job.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.core.utils.FileUtils
import org.wip.plugintoolkit.features.plugin.logic.DefaultPluginFileSystem
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.core.utils.MemoryUtils
import org.wip.plugintoolkit.features.job.utils.ProcessMemoryUtils
import org.wip.plugintoolkit.features.job.utils.ProcessGpuUtils
import org.wip.plugintoolkit.features.job.utils.ProcessCpuUtils
import org.wip.plugintoolkit.features.job.model.CapabilityExecutionMetric
import org.wip.plugintoolkit.features.job.model.JobExecutionMetrics
import org.wip.plugintoolkit.features.job.model.ResourceUsageSample
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.round
import kotlin.time.Clock

class JobManager(
    /** Injected [AppScope] for managing job lifecycles and worker coordination. */
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository
) {
    private val maxConcurrentJobs get() = maxOf(1, settingsRepository.settings.value.jobs.maxConcurrentJobs)
    private val maxEndedJobs get() = settingsRepository.settings.value.jobs.maxEndedJobs
    private val maxHistoryLength get() = settingsRepository.settings.value.jobs.maxHistoryLength

    private val _jobs = MutableStateFlow<List<BackgroundJob>>(emptyList())
    val jobs: StateFlow<List<BackgroundJob>> = _jobs.asStateFlow()

    val activeJobIds: StateFlow<Set<String>> = _jobs.map { list ->
        list.filter { it.status == JobStatus.Queued || it.status == JobStatus.Running || it.status == JobStatus.PauseRequested || it.status == JobStatus.Paused }.map { it.id }.toSet()
    }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    fun isJobPendingOrRunning(jobId: String): Boolean {
        return _jobs.value.any { it.id == jobId && (it.status == JobStatus.Queued || it.status == JobStatus.Running || it.status == JobStatus.PauseRequested || it.status == JobStatus.Paused) }
    }

    private val _endedJobs = MutableStateFlow<List<BackgroundJob>>(emptyList())
    val endedJobs: StateFlow<List<BackgroundJob>> = _endedJobs.asStateFlow()

    private val _jobProgress = MutableStateFlow<Map<String, org.wip.plugintoolkit.features.job.model.JobProgress>>(emptyMap())
    val jobProgress: StateFlow<Map<String, org.wip.plugintoolkit.features.job.model.JobProgress>> = _jobProgress.asStateFlow()

    private val _history = MutableStateFlow<List<JobHistoryEntry>>(emptyList())
    val history: StateFlow<List<JobHistoryEntry>> = _history.asStateFlow()

    private val _jobLogs = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val jobLogs: StateFlow<Map<String, List<String>>> = _jobLogs.asStateFlow()

    private val workersMutex = Mutex()
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

    private val lastLoggedProgress = ConcurrentHashMap<String, String>()
    private val activeJobCapabilityMetrics = ConcurrentHashMap<String, CopyOnWriteArrayList<CapabilityExecutionMetric>>()
    private val activeJobPeakMemory = ConcurrentHashMap<String, Long>()
    private val activeJobPeakVram = ConcurrentHashMap<String, Long>()
    private val activeJobPeakCpu = ConcurrentHashMap<String, Double>()
    private val activeJobResourceTimeline = ConcurrentHashMap<String, CopyOnWriteArrayList<ResourceUsageSample>>()
    private val flowConcurrencyLimits = ConcurrentHashMap<String, Int>()
    private val capabilityConcurrencyLimits = ConcurrentHashMap<Pair<String, String>, Int>()

    fun setFlowMaxConcurrent(flowName: String, maxConcurrent: Int?) {
        if (maxConcurrent != null && maxConcurrent > 0) {
            flowConcurrencyLimits[flowName] = maxConcurrent
        } else {
            flowConcurrencyLimits.remove(flowName)
        }
        jobSignal.trySend(Unit)
    }

    fun getFlowMaxConcurrent(flowName: String): Int? = flowConcurrencyLimits[flowName]

    fun setCapabilityMaxConcurrent(pluginId: String, capabilityName: String, maxConcurrent: Int?) {
        val key = Pair(pluginId, capabilityName)
        if (maxConcurrent != null && maxConcurrent > 0) {
            capabilityConcurrencyLimits[key] = maxConcurrent
        } else {
            capabilityConcurrencyLimits.remove(key)
        }
        jobSignal.trySend(Unit)
    }

    fun getCapabilityMaxConcurrent(pluginId: String, capabilityName: String): Int? =
        capabilityConcurrencyLimits[Pair(pluginId, capabilityName)]

    fun recordCapabilityMetric(
        jobId: String,
        capabilityName: String,
        durationMs: Long,
        memoryBytes: Long? = null,
        totalMemoryBytes: Long? = null,
        bytesRead: Long? = null,
        bytesWritten: Long? = null,
        networkBytesRead: Long? = null,
        networkBytesWritten: Long? = null,
        throughputBytesPerSec: Long? = null,
        peakVramBytes: Long? = null,
        avgProcessCpuPercent: Double? = null,
        peakProcessCpuPercent: Double? = null,
        avgSystemCpuPercent: Double? = null
    ) {
        val list = activeJobCapabilityMetrics.computeIfAbsent(jobId) { CopyOnWriteArrayList() }
        list.add(
            CapabilityExecutionMetric(
                capabilityName = capabilityName,
                durationMs = durationMs,
                memoryUsageBytes = memoryBytes,
                totalMemoryBytes = totalMemoryBytes,
                bytesRead = bytesRead,
                bytesWritten = bytesWritten,
                networkBytesRead = networkBytesRead,
                networkBytesWritten = networkBytesWritten,
                throughputBytesPerSec = throughputBytesPerSec,
                peakVramBytes = peakVramBytes,
                avgProcessCpuPercent = avgProcessCpuPercent,
                peakProcessCpuPercent = peakProcessCpuPercent,
                avgSystemCpuPercent = avgSystemCpuPercent
            )
        )
        if (memoryBytes != null && memoryBytes > 0L) {
            activeJobPeakMemory.compute(jobId) { _, current ->
                kotlin.math.max(current ?: 0L, memoryBytes)
            }
        }
        if (peakVramBytes != null && peakVramBytes > 0L) {
            activeJobPeakVram.compute(jobId) { _, current ->
                kotlin.math.max(current ?: 0L, peakVramBytes)
            }
        }
        val effectiveCpu = peakProcessCpuPercent ?: avgProcessCpuPercent
        if (effectiveCpu != null && effectiveCpu > 0.0) {
            activeJobPeakCpu.compute(jobId) { _, current ->
                kotlin.math.max(current ?: 0.0, effectiveCpu)
            }
        }
    }

    fun recordLivePeakMemory(jobId: String, memoryBytes: Long) {
        if (memoryBytes <= 0L) return
        activeJobPeakMemory.compute(jobId) { _, current ->
            kotlin.math.max(current ?: 0L, memoryBytes)
        }
    }

    fun getLivePeakMemory(jobId: String): Long? = activeJobPeakMemory[jobId]

    fun recordLivePeakVram(jobId: String, vramBytes: Long) {
        if (vramBytes <= 0L) return
        activeJobPeakVram.compute(jobId) { _, current ->
            kotlin.math.max(current ?: 0L, vramBytes)
        }
    }

    fun getLivePeakVram(jobId: String): Long? = activeJobPeakVram[jobId]

    fun recordLivePeakCpu(jobId: String, cpuPercent: Double) {
        if (cpuPercent <= 0.0) return
        activeJobPeakCpu.compute(jobId) { _, current ->
            kotlin.math.max(current ?: 0.0, cpuPercent)
        }
    }

    fun getLivePeakCpu(jobId: String): Double? = activeJobPeakCpu[jobId]

    fun recordResourceSample(jobId: String, sample: ResourceUsageSample) {
        val list = activeJobResourceTimeline.computeIfAbsent(jobId) { CopyOnWriteArrayList() }
        list.add(sample)
    }

    fun getResourceTimeline(jobId: String): List<ResourceUsageSample> =
        activeJobResourceTimeline[jobId]?.toList() ?: emptyList()

    init {
        scope.launch {
            try {
                val savedJobs = jobRepository.loadJobs()
                val (activeSaved, endedSaved) = savedJobs.partition {
                    it.status == JobStatus.Queued || it.status == JobStatus.Running || it.status == JobStatus.Paused || it.status == JobStatus.PauseRequested
                }
                _jobs.update { currentJobs ->
                    val newJobIds = currentJobs.map { it.id }.toSet()
                    activeSaved.filterNot { it.id in newJobIds } + currentJobs
                }
                if (endedSaved.isNotEmpty()) {
                    _endedJobs.update { currentEnded ->
                        (endedSaved + currentEnded).distinctBy { it.id }.take(maxEndedJobs)
                    }
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
            adjustWorkers(maxConcurrentJobs)

            // Observe and save job state changes
            launch {
                _jobs.collect { currentJobs ->
                    jobRepository.saveJobs(currentJobs)
                }
            }

            // Observe live changes to maxConcurrentJobs setting
            launch {
                settingsRepository.settings
                    .map { maxOf(1, it.jobs.maxConcurrentJobs) }
                    .distinctUntilChanged()
                    .collect { targetWorkers ->
                        adjustWorkers(targetWorkers)
                        jobSignal.trySend(Unit)
                    }
            }
        }
    }

    private suspend fun adjustWorkers(targetCount: Int) {
        workersMutex.withLock {
            if (targetCount > workers.size) {
                for (i in workers.size until targetCount) {
                    val worker = JobWorker(i, this, scope)
                    workers.add(worker)
                    worker.start()
                }
            } else if (targetCount < workers.size) {
                val excessCount = workers.size - targetCount
                val excessWorkers = workers.takeLast(excessCount)
                repeat(excessCount) {
                    workers.removeAt(workers.lastIndex)
                }
                excessWorkers.forEach { it.stopGracefully() }
            }
        }
    }

    fun enqueueJob(job: BackgroundJob) {
        // Remove previous ended jobs of the same ID or capability/flow that should not be saved in history
        val jobsToRemove = _endedJobs.value.filter {
            it.id == job.id || (
                it.pluginId == job.pluginId &&
                it.capabilityName == job.capabilityName &&
                !it.keepResult
            )
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
            val filtered = currentList.filterNot {
                it.id == job.id || (
                    !job.keepResult &&
                    it.pluginId == job.pluginId &&
                    it.capabilityName == job.capabilityName &&
                    (it.status == JobStatus.Completed || it.status == JobStatus.Failed || it.status == JobStatus.Cancelled)
                )
            }
            filtered + job.copy(status = JobStatus.Queued, enqueuedAt = Clock.System.now())
        }
        addHistoryEntryInternal(job.id, job.name, "Enqueued")
        Logger.i { "Job ${job.id} (${job.name}) enqueued (type=${job.type}, plugin=${job.pluginId})" }
        jobSignal.trySend(Unit)
    }

    fun updateJob(jobId: String, update: (BackgroundJob) -> BackgroundJob) {
        _jobs.update { currentList ->
            currentList.map { if (it.id == jobId) update(it) else it }
        }
    }

    private fun createExecutionMetrics(
        jobId: String,
        startedAt: kotlin.time.Instant,
        completedAt: kotlin.time.Instant,
        currentMem: Long
    ): JobExecutionMetrics {
        val totalDuration = (completedAt - startedAt).inWholeMilliseconds.coerceAtLeast(0L)
        val recordedPeak = activeJobPeakMemory.remove(jobId) ?: 0L
        val peakMem = kotlin.math.max(recordedPeak, currentMem)
        val capMetrics = activeJobCapabilityMetrics.remove(jobId)?.toList() ?: emptyList()
        val totalCapMem = capMetrics.mapNotNull { it.memoryUsageBytes }.sum().takeIf { it > 0L }
        val totalMem = totalCapMem ?: if (peakMem > 0L) peakMem else null

        val timeline = activeJobResourceTimeline.remove(jobId)?.toList() ?: emptyList()
        val recordedPeakVram = activeJobPeakVram.remove(jobId)
        val timelinePeakVram = timeline.mapNotNull { it.processVramBytes }.maxOrNull()
        val peakVram = listOfNotNull(recordedPeakVram, timelinePeakVram).maxOrNull()
        val maxSysVram = ProcessGpuUtils.getSystemVramBytes()?.second
        val cores = ProcessCpuUtils.getAvailableProcessors()

        val recordedPeakCpu = activeJobPeakCpu.remove(jobId)
        val capPeakCpu = capMetrics.mapNotNull { it.peakProcessCpuPercent ?: it.avgProcessCpuPercent }.maxOrNull()
        val timelinePeakCpu = timeline.mapNotNull { it.processCpuPercent }.maxOrNull()
        val peakProcCpu = listOfNotNull(recordedPeakCpu, timelinePeakCpu, capPeakCpu).maxOrNull()

        val capAvgCpu = capMetrics.mapNotNull { it.avgProcessCpuPercent }.takeIf { it.isNotEmpty() }?.let {
            round(it.sum() / it.size * 10.0) / 10.0
        }
        val avgProcCpu = timeline.mapNotNull { it.processCpuPercent }.takeIf { it.isNotEmpty() }?.let {
            round(it.sum() / it.size * 10.0) / 10.0
        } ?: capAvgCpu
        val avgSysCpu = timeline.mapNotNull { it.systemCpuPercent }.takeIf { it.isNotEmpty() }?.let {
            round(it.sum() / it.size * 10.0) / 10.0
        }

        return JobExecutionMetrics(
            startedAt = startedAt,
            completedAt = completedAt,
            totalDurationMs = totalDuration,
            memoryUsageBytes = if (peakMem > 0L) peakMem else null,
            totalMemoryUsageBytes = totalMem,
            capabilityMetrics = capMetrics,
            peakProcessVramBytes = peakVram,
            maxSystemVramBytes = maxSysVram,
            avgProcessCpuPercent = avgProcCpu,
            peakProcessCpuPercent = peakProcCpu,
            avgSystemCpuPercent = avgSysCpu,
            availableCores = cores,
            resourceTimeline = timeline
        )
    }

    suspend fun cancelJob(jobId: String, force: Boolean = false) {
        var jobName = ""
        var cancelled = false
        _jobs.update { currentList ->
            val job = currentList.find { it.id == jobId } ?: return@update currentList
            jobName = job.name
            if (job.status == JobStatus.Running || job.status == JobStatus.Queued || job.status == JobStatus.Paused || job.status == JobStatus.PauseRequested) {
                cancelled = true
                val completedAt = Clock.System.now()
                val startedAt = job.startedAt ?: job.enqueuedAt
                val currentMem = MemoryUtils.getCurrentMemoryUsageBytes() + ProcessMemoryUtils.getAllDescendantsMemoryBytes()
                lastLoggedProgress.remove(jobId)

                val metrics = createExecutionMetrics(jobId, startedAt, completedAt, currentMem)

                currentList.map {
                    if (it.id == jobId) it.copy(
                        status = JobStatus.Cancelled,
                        completedAt = completedAt,
                        executionMetrics = metrics
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
        var isQueued = false
        var isRunning = false
        _jobs.update { currentList ->
            val job = currentList.find { it.id == jobId } ?: return@update currentList
            jobName = job.name
            when (job.status) {
                JobStatus.Queued -> {
                    isQueued = true
                    currentList.map { if (it.id == jobId) it.copy(status = JobStatus.Paused) else it }
                }
                JobStatus.Running -> {
                    isRunning = true
                    currentList.map { if (it.id == jobId) it.copy(status = JobStatus.PauseRequested) else it }
                }
                else -> currentList
            }
        }

        if (isQueued) {
            addHistoryEntryInternal(jobId, jobName, "Paused")
            Logger.i { "Job $jobId ($jobName) paused directly from queued state" }
            val job = _jobs.value.find { it.id == jobId }
            if (job != null) {
                saveResumeState(job)
            }
            jobSignal.trySend(Unit)
        } else if (isRunning) {
            handlesMutex.withLock {
                activeJobHandles[jobId]?.pause()
            }
            addHistoryEntryInternal(jobId, jobName, "Pause Requested")
            Logger.i { "Job $jobId ($jobName) pause requested" }
            jobSignal.trySend(Unit)
        }
    }

    private fun isJobSchedulable(job: BackgroundJob, currentList: List<BackgroundJob>): Boolean {
        if (job.status != JobStatus.Queued) return false
        if (job.type == JobType.Flow) {
            val limit = job.maxConcurrentExecutions ?: getFlowMaxConcurrent(job.capabilityName)
            if (limit != null && limit > 0) {
                val runningFlows = currentList.count {
                    it.type == JobType.Flow && it.capabilityName == job.capabilityName &&
                            (it.status == JobStatus.Running || it.status == JobStatus.PauseRequested)
                }
                if (runningFlows >= limit) return false
            }
        } else if (job.type == JobType.Capability) {
            val limit = job.maxConcurrentExecutions ?: getCapabilityMaxConcurrent(job.pluginId, job.capabilityName)
            if (limit != null && limit > 0) {
                val runningCaps = currentList.count {
                    it.type == JobType.Capability && it.pluginId == job.pluginId && it.capabilityName == job.capabilityName &&
                            (it.status == JobStatus.Running || it.status == JobStatus.PauseRequested)
                }
                if (runningCaps >= limit) return false
            }
        }
        return true
    }

    suspend fun resumeJob(jobId: String) {
        var resumed = false
        var jobName = ""
        _jobs.update { currentList ->
            val job = currentList.find { it.id == jobId }
            if (job != null) {
                jobName = job.name
                if (job.status == JobStatus.Paused || (job.status == JobStatus.Failed && job.resumeState != null)) {
                    resumed = true
                    currentList.map {
                        if (it.id == jobId) it.copy(
                            status = JobStatus.Queued,
                            errorMessage = null,
                            completedAt = null
                        ) else it
                    }
                } else {
                    currentList
                }
            } else {
                currentList
            }
        }

        if (!resumed) {
            val ended = _endedJobs.value.find { it.id == jobId }
            if (ended != null && (ended.status == JobStatus.Paused || (ended.status == JobStatus.Failed && ended.resumeState != null))) {
                _endedJobs.update { it.filterNot { k -> k.id == jobId } }
                val queuedJob = ended.copy(
                    status = JobStatus.Queued,
                    errorMessage = null,
                    completedAt = null
                )
                _jobs.update { it + queuedJob }
                resumed = true
                jobName = ended.name
            }
        }

        if (resumed) {
            addHistoryEntryInternal(jobId, jobName, "Resumed")
            Logger.i { "Job $jobId ($jobName) resumed" }
            jobSignal.trySend(Unit)
        }
    }

    suspend fun restartJob(jobId: String): String? {
        val existingJob = _jobs.value.find { it.id == jobId }
            ?: _endedJobs.value.find { it.id == jobId }
            ?: return null

        val prefix = if (existingJob.type == JobType.Flow) {
            "flow-${existingJob.capabilityName.replace(" ", "_")}"
        } else {
            existingJob.capabilityName
        }
        val newId = "$prefix-${kotlin.time.TimeSource.Monotonic.markNow().elapsedNow().inWholeNanoseconds.let { Clock.System.now().toEpochMilliseconds() }}"
        val newJob = existingJob.copy(
            id = newId,
            status = JobStatus.Queued,
            resumeState = null,
            result = null,
            errorMessage = null,
            completedAt = null,
            enqueuedAt = Clock.System.now(),
            executionMetrics = null
        )
        enqueueJob(newJob)
        addHistoryEntryInternal(newId, newJob.name, "Restarted from job $jobId")
        Logger.i { "Job $jobId (${existingJob.name}) restarted as new job $newId" }
        return newId
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
                val runningCount = currentList.count {
                    it.status == JobStatus.Running || it.status == JobStatus.PauseRequested
                }
                if (runningCount < maxConcurrentJobs) {
                    val candidate = currentList.firstOrNull { isJobSchedulable(it, currentList) }

                    if (candidate != null) {
                        claimedJob = candidate.copy(status = JobStatus.Running, startedAt = Clock.System.now())
                        activeJobPeakMemory[candidate.id] = MemoryUtils.getCurrentMemoryUsageBytes() + ProcessMemoryUtils.getAllDescendantsMemoryBytes()
                        val initialVram = ProcessGpuUtils.getTotalTrackedVramBytes()
                        if (initialVram > 0L) {
                            activeJobPeakVram[candidate.id] = initialVram
                        }
                        currentList.map { if (it.id == candidate.id) claimedJob else it }
                    } else {
                        currentList
                    }
                } else {
                    currentList
                }
            }

            if (claimedJob != null) {
                val runningCount = _jobs.value.count {
                    it.status == JobStatus.Running || it.status == JobStatus.PauseRequested
                }
                if (runningCount < maxConcurrentJobs && _jobs.value.any { isJobSchedulable(it, _jobs.value) }) {
                    jobSignal.trySend(Unit)
                }
                return claimedJob
            }

            Logger.v { "No queued jobs or limit reached ($maxConcurrentJobs), worker waiting for signal..." }

            val currentRunning = _jobs.value.count {
                it.status == JobStatus.Running || it.status == JobStatus.PauseRequested
            }
            if (currentRunning < maxConcurrentJobs && _jobs.value.any { isJobSchedulable(it, _jobs.value) }) {
                continue
            }

            jobSignal.receive()
        }
    }

    fun updateJobProgress(
        jobId: String,
        progress: Float,
        message: String? = null,
        current: Double? = null,
        total: Double? = null,
        unit: String? = null,
        displayMode: org.wip.plugintoolkit.api.ProgressDisplayMode = org.wip.plugintoolkit.api.ProgressDisplayMode.PERCENTAGE
    ) {
        val clampedProgress = progress.coerceIn(0f, 1f)
        _jobProgress.update { curr ->
            val currentProgress = curr[jobId] ?: org.wip.plugintoolkit.features.job.model.JobProgress()
            curr + (jobId to currentProgress.copy(
                mainProgress = clampedProgress,
                mainMessage = message,
                mainCurrent = current,
                mainTotal = total,
                mainUnit = unit,
                mainDisplayMode = displayMode
            ))
        }
        val percent = clampedProgress * 100f
        val formattedPercent = formatProgressPercent(percent)
        val logDetails = if (!message.isNullOrBlank()) "$formattedPercent ($message)" else formattedPercent
        val previous = lastLoggedProgress[jobId]
        if (previous != logDetails) {
            lastLoggedProgress[jobId] = logDetails
            addJobLog(jobId, "Progress: $logDetails", "VERBOSE")
        }
    }

    fun updateJobProgress(jobId: String, data: org.wip.plugintoolkit.api.ProgressData) {
        updateJobProgress(
            jobId = jobId,
            progress = data.fraction ?: 0f,
            message = data.message,
            current = data.current,
            total = data.total,
            unit = data.unit,
            displayMode = data.displayMode
        )
    }

    fun updateJobSecondaryProgress(
        jobId: String,
        progress: Float?,
        message: String? = null,
        current: Double? = null,
        total: Double? = null,
        unit: String? = null,
        displayMode: org.wip.plugintoolkit.api.ProgressDisplayMode = org.wip.plugintoolkit.api.ProgressDisplayMode.PERCENTAGE
    ) {
        val clamped = progress?.coerceIn(0f, 1f)
        _jobProgress.update { curr ->
            val currentProgress = curr[jobId] ?: org.wip.plugintoolkit.features.job.model.JobProgress()
            curr + (jobId to currentProgress.copy(
                secondaryProgress = clamped,
                secondaryMessage = message,
                secondaryCurrent = current,
                secondaryTotal = total,
                secondaryUnit = unit,
                secondaryDisplayMode = displayMode
            ))
        }
    }

    fun updateJobSecondaryProgress(jobId: String, data: org.wip.plugintoolkit.api.ProgressData) {
        updateJobSecondaryProgress(
            jobId = jobId,
            progress = data.fraction,
            message = data.message,
            current = data.current,
            total = data.total,
            unit = data.unit,
            displayMode = data.displayMode
        )
    }

    fun clearJobSecondaryProgress(jobId: String) {
        _jobProgress.update { curr ->
            val currentProgress = curr[jobId] ?: return@update curr
            curr + (jobId to currentProgress.copy(
                secondaryProgress = null,
                secondaryMessage = null,
                secondaryCurrent = null,
                secondaryTotal = null,
                secondaryUnit = null
            ))
        }
    }

    private fun formatProgressPercent(percent: Float): String {
        val intPercent = percent.toInt().coerceIn(0, 100)
        return "$intPercent%"
    }

    fun updateCapabilityProgress(jobId: String, capabilityName: String, progress: Float) {
        updateCapabilityProgress(jobId, capabilityName, org.wip.plugintoolkit.api.ProgressData.percentage(progress))
    }

    fun updateCapabilityProgress(
        jobId: String,
        capabilityName: String,
        data: org.wip.plugintoolkit.api.ProgressData
    ) {
        _jobProgress.update { curr ->
            val currentProgress = curr[jobId] ?: org.wip.plugintoolkit.features.job.model.JobProgress()
            val existingItem = currentProgress.capabilitiesDetailedProgress[capabilityName]
            val newItem = (existingItem ?: org.wip.plugintoolkit.features.job.model.CapabilityProgressItem()).copy(
                progress = data.fraction ?: 0f,
                message = data.message,
                current = data.current,
                total = data.total,
                unit = data.unit,
                displayMode = data.displayMode
            )
            val updatedCaps = currentProgress.capabilitiesProgress + (capabilityName to (data.fraction ?: 0f))
            val updatedDetailed = currentProgress.capabilitiesDetailedProgress + (capabilityName to newItem)
            curr + (jobId to currentProgress.copy(
                capabilitiesProgress = updatedCaps,
                capabilitiesDetailedProgress = updatedDetailed
            ))
        }
    }

    fun updateCapabilitySecondaryProgress(
        jobId: String,
        capabilityName: String,
        data: org.wip.plugintoolkit.api.ProgressData
    ) {
        _jobProgress.update { curr ->
            val currentProgress = curr[jobId] ?: org.wip.plugintoolkit.features.job.model.JobProgress()
            val existingItem = currentProgress.capabilitiesDetailedProgress[capabilityName]
                ?: org.wip.plugintoolkit.features.job.model.CapabilityProgressItem()
            val newItem = existingItem.copy(
                secondaryProgress = data.fraction,
                secondaryMessage = data.message,
                secondaryCurrent = data.current,
                secondaryTotal = data.total,
                secondaryUnit = data.unit,
                secondaryDisplayMode = data.displayMode
            )
            val updatedDetailed = currentProgress.capabilitiesDetailedProgress + (capabilityName to newItem)
            curr + (jobId to currentProgress.copy(capabilitiesDetailedProgress = updatedDetailed))
        }
    }

    fun clearCapabilitySecondaryProgress(jobId: String, capabilityName: String) {
        _jobProgress.update { curr ->
            val currentProgress = curr[jobId] ?: return@update curr
            val existingItem = currentProgress.capabilitiesDetailedProgress[capabilityName] ?: return@update curr
            val newItem = existingItem.copy(
                secondaryProgress = null,
                secondaryMessage = null,
                secondaryCurrent = null,
                secondaryTotal = null,
                secondaryUnit = null
            )
            curr + (jobId to currentProgress.copy(
                capabilitiesDetailedProgress = currentProgress.capabilitiesDetailedProgress + (capabilityName to newItem)
            ))
        }
    }

    fun removeCapabilityProgress(jobId: String, capabilityName: String) {
        _jobProgress.update { current ->
            val currentProgress = current[jobId] ?: return@update current
            val updatedCaps = currentProgress.capabilitiesProgress - capabilityName
            val updatedDetailed = currentProgress.capabilitiesDetailedProgress - capabilityName
            current + (jobId to currentProgress.copy(
                capabilitiesProgress = updatedCaps,
                capabilitiesDetailedProgress = updatedDetailed
            ))
        }
    }

    fun addJobLog(jobId: String, message: String, level: String = "INFO") {
        val now = Clock.System.now()
        val local = now.toLocalDateTime(TimeZone.currentSystemDefault())
        val timestamp = "${local.hour.toString().padStart(2, '0')}:${
            local.minute.toString().padStart(2, '0')
        }:${local.second.toString().padStart(2, '0')}"

        val prefix = if (level == "VERBOSE") "[$jobId] " else ""

        // Log to global logger as well
        when (level) {
            "VERBOSE" -> Logger.v { "[$jobId] $message" }
            "DEBUG" -> Logger.d { "[$jobId] $message" }
            "INFO" -> Logger.i { "[$jobId] $message" }
            "WARN" -> Logger.w { "[$jobId] $message" }
            "ERROR" -> Logger.e { "[$jobId] $message" }
        }

        val jobSettings = settingsRepository.settings.value.jobs
        val maxLines = jobSettings.maxLogLines
        val maxLineLength = jobSettings.maxLogLineLength

        val rawLines = message.lines()
        val formattedLines = rawLines.mapIndexed { index, rawLine ->
            val header = if (index == 0) "[$timestamp] [$level] $prefix" else "        "
            val fullLine = "$header$rawLine"
            if (maxLineLength > 0 && fullLine.length > maxLineLength) {
                fullLine.take(maxLineLength) + "... [truncated]"
            } else {
                fullLine
            }
        }

        _jobLogs.update { currentLogs ->
            val logs = currentLogs[jobId] ?: emptyList()
            val combined = logs + formattedLines
            val pruned = if (maxLines == -1) combined else combined.takeLast(maxLines)
            currentLogs + (jobId to pruned)
        }
    }

    fun tryCompleteJob(jobId: String, result: String?): Boolean {
        var completed = false
        var jobName = ""
        _jobs.update { currentList ->
            val job = currentList.find { it.id == jobId } ?: return@update currentList
            jobName = job.name
            if (job.status == JobStatus.Running || job.status == JobStatus.PauseRequested) {
                completed = true
                updateJobProgress(jobId, 1.0f)
                val completedAt = Clock.System.now()
                val startedAt = job.startedAt ?: job.enqueuedAt
                val currentMem = MemoryUtils.getCurrentMemoryUsageBytes() + ProcessMemoryUtils.getAllDescendantsMemoryBytes()
                lastLoggedProgress.remove(jobId)

                val metrics = createExecutionMetrics(jobId, startedAt, completedAt, currentMem)

                currentList.map {
                    if (it.id == jobId) it.copy(
                        status = JobStatus.Completed,
                        completedAt = completedAt,
                        result = result,
                        executionMetrics = metrics
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
            jobSignal.trySend(Unit)
        } else {
            val job = _jobs.value.find { it.id == jobId }
            Logger.w { "Attempted to complete job $jobId, but it was not in Running state (current: ${job?.status})" }
        }
        return completed
    }

    fun tryFailJob(jobId: String, errorMessage: String?, resumeState: JsonElement? = null): Boolean {
        var failed = false
        var jobName = ""
        _jobs.update { currentList ->
            val job = currentList.find { it.id == jobId } ?: return@update currentList
            jobName = job.name
            if (job.status == JobStatus.Running || job.status == JobStatus.PauseRequested) {
                failed = true
                val completedAt = Clock.System.now()
                val startedAt = job.startedAt ?: job.enqueuedAt
                val currentMem = MemoryUtils.getCurrentMemoryUsageBytes() + ProcessMemoryUtils.getAllDescendantsMemoryBytes()
                lastLoggedProgress.remove(jobId)

                val metrics = createExecutionMetrics(jobId, startedAt, completedAt, currentMem)

                currentList.map {
                    if (it.id == jobId) it.copy(
                        status = JobStatus.Failed,
                        errorMessage = errorMessage,
                        completedAt = completedAt,
                        executionMetrics = metrics,
                        resumeState = resumeState ?: it.resumeState
                    ) else it
                }
            } else {
                currentList
            }
        }

        if (failed) {
            val finishedJob = _jobs.value.find { it.id == jobId }
            if (finishedJob != null) {
                if (finishedJob.resumeState != null) {
                    saveResumeState(finishedJob)
                }
                _jobs.update { it.filterNot { k -> k.id == jobId } }
                _endedJobs.update { (listOf(finishedJob) + it).take(maxEndedJobs) }
            }
            addHistoryEntryInternal(jobId, jobName, "Failed", errorMessage)
            Logger.e { "Job $jobId ($jobName) failed: $errorMessage" }
            jobSignal.trySend(Unit)
        } else {
            val job = _jobs.value.find { it.id == jobId }
            Logger.w { "Attempted to fail job $jobId, but it was not in Running state (current: ${job?.status})" }
        }
        return failed
    }

    suspend fun registerJobHandle(jobId: String, handle: JobHandle) {
        val job = _jobs.value.find { it.id == jobId }
        if (job == null || (job.status != JobStatus.Running && job.status != JobStatus.Paused && job.status != JobStatus.PauseRequested)) {
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
            if (job.status == JobStatus.Running || job.status == JobStatus.PauseRequested || job.status == JobStatus.Paused) {
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
            jobSignal.trySend(Unit)
        }
        return paused
    }

    fun getPluginLogger(pkg: String, jobId: String? = null): PluginLogger {
        val tag = if (jobId != null) "$pkg/$jobId" else pkg
        return object : PluginLogger {
            override fun verbose(message: String) {
                if (jobId != null) addJobLog(jobId, message, "VERBOSE")
                Logger.v { "[$tag] $message" }
            }

            override fun debug(message: String) {
                if (jobId != null) addJobLog(jobId, message, "DEBUG")
                Logger.d { "[$tag] $message" }
            }

            override fun info(message: String) {
                if (jobId != null) addJobLog(jobId, message, "INFO")
                Logger.i { "[$tag] $message" }
            }

            override fun warn(message: String) {
                if (jobId != null) addJobLog(jobId, message, "WARN")
                Logger.w { "[$tag] $message" }
            }

            override fun error(message: String, throwable: Throwable?) {
                val msg = message + (throwable?.let { ": ${it.message}" } ?: "")
                if (jobId != null) addJobLog(jobId, msg, "ERROR")
                Logger.e(throwable ?: Exception(msg)) { "[$tag] $message" }
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
        val job = _endedJobs.value.find { it.id == jobId }
        _endedJobs.update { it.filterNot { k -> k.id == jobId } }
        _jobLogs.update { it - jobId }
        lastLoggedProgress.remove(jobId)
        activeJobCapabilityMetrics.remove(jobId)
        activeJobPeakMemory.remove(jobId)
        if (job != null && job.resumeState != null) {
            try {
                val appDataDir = settingsPersistence.getSettingsDir()
                FileUtils.deleteDirectory("$appDataDir/jobs/$jobId/sandbox")
            } catch (_: Exception) {}
        }
    }

    fun clearAllEndedJobs() {
        val ended = _endedJobs.value
        val endedIds = ended.map { it.id }
        _endedJobs.value = emptyList()
        _jobLogs.update { it.filterKeys { k -> k !in endedIds } }
        ended.forEach { job ->
            lastLoggedProgress.remove(job.id)
            activeJobCapabilityMetrics.remove(job.id)
            activeJobPeakMemory.remove(job.id)
            if (job.resumeState != null) {
                try {
                    val appDataDir = settingsPersistence.getSettingsDir()
                    FileUtils.deleteDirectory("$appDataDir/jobs/${job.id}/sandbox")
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun stopAll() {
        workersMutex.withLock {
            workers.forEach { it.stop() }
            workers.clear()
        }
        handlesMutex.withLock {
            activeJobHandles.values.forEach { it.cancel(force = true) }
            activeJobHandles.clear()
        }
    }
}

