package org.wip.plugintoolkit.features.job.logic

import co.touchlab.kermit.Logger
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobHistoryEntry
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.plugin.logic.DefaultPluginFileSystem
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.api.JobHandle
import org.wip.plugintoolkit.api.PluginLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

class JobManager(
    private val scope: CoroutineScope,
    private val maxConcurrentJobs: Int = 2
) {
    private val _jobs = MutableStateFlow<List<BackgroundJob>>(emptyList())
    val jobs: StateFlow<List<BackgroundJob>> = _jobs.asStateFlow()

    private val _jobProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val jobProgress: StateFlow<Map<String, Float>> = _jobProgress.asStateFlow()

    private val _history = MutableStateFlow<List<JobHistoryEntry>>(emptyList())
    val history: StateFlow<List<JobHistoryEntry>> = _history.asStateFlow()

    private val _jobLogs = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val jobLogs: StateFlow<Map<String, List<String>>> = _jobLogs.asStateFlow()

    private val workers = mutableListOf<JobWorker>()

    // Channel to signal workers that a new job is available.
    // CONFLATED means if multiple jobs are added rapidly, we wake up at least one worker.
    private val jobSignal = Channel<Unit>(Channel.CONFLATED)

    // Mutex strictly for managing the active handles map, avoiding global contention.
    private val handlesMutex = Mutex()
    private val activeJobHandles = mutableMapOf<String, JobHandle>()

    private val MAX_HISTORY_SIZE = 100

    init {
        startWorkers()
    }

    private fun startWorkers() {
        repeat(maxConcurrentJobs) {
            val worker = JobWorker(it, this, scope)
            workers.add(worker)
            worker.start()
        }
    }

    fun enqueueJob(job: BackgroundJob) {
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
            addHistoryEntryInternal(jobId, jobName, "Cancelled")
            Logger.i { "Job $jobId ($jobName) cancelled (force=$force)" }
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
            jobSignal.receive()
        }
    }

    fun updateJobProgress(jobId: String, progress: Float) {
        _jobProgress.update { it + (jobId to progress) }
        val progressPercent = (progress * 100).toInt()
        addJobLog(jobId, "Progress: $progressPercent%", "VERBOSE")
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
            currentLogs + (jobId to (logs + formattedLog).takeLast(100))
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
            if (job.status == JobStatus.Running) {
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
            scope.launch(Dispatchers.Default) {
                val fs = DefaultPluginFileSystem.createCacheOnly(installPath)
                val json = Json { prettyPrint = true }
                val stateString = json.encodeToString(JsonElement.serializer(), job.resumeState ?: JsonNull)
                fs.writeTextFile("resumes/${job.id}.json", stateString)
                
                // Update resumes.json index
                val indexFile = "resumes.json"
                val currentIndex = if (fs.exists(indexFile)) {
                    val content = fs.readTextFile(indexFile) ?: "{}"
                    try { json.decodeFromString<Map<String, String>>(content) } catch(e: Exception) {
                        Logger.w(e) { "Error decoding resumes.json index" }
                        emptyMap()
                    }
                } else emptyMap()
                
                val newIndex = currentIndex + (job.id to "resumes/${job.id}.json")
                fs.writeTextFile(indexFile, json.encodeToString(newIndex))
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
            if (newList.size > MAX_HISTORY_SIZE) {
                newList.take(MAX_HISTORY_SIZE)
            } else {
                newList
            }
        }
    }

    fun removeJobs(predicate: (BackgroundJob) -> Boolean) {
        _jobs.update { it.filterNot(predicate) }
    }

    suspend fun stopAll() {
        workers.forEach { it.stop() }
        handlesMutex.withLock {
            activeJobHandles.values.forEach { it.cancel(force = true) }
            activeJobHandles.clear()
        }
    }
}

