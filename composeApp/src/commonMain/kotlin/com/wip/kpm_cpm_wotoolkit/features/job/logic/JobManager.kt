package com.wip.kpm_cpm_wotoolkit.features.job.logic

import com.wip.kpm_cpm_wotoolkit.features.job.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import co.touchlab.kermit.Logger
import kotlin.time.Clock
import kotlin.collections.emptyList

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

    private val workers = mutableListOf<JobWorker>()
    
    // Channel to signal workers that a new job is available.
    // CONFLATED means if multiple jobs are added rapidly, we wake up at least one worker.
    private val jobSignal = Channel<Unit>(Channel.CONFLATED)
    
    // Mutex strictly for managing the active coroutines map, avoiding global contention.
    private val coroutinesMutex = Mutex()
    private val activeJobCoroutines = mutableMapOf<String, Job>()
    
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

    suspend fun cancelJob(jobId: String) {
        var jobName = ""
        var cancelled = false
        _jobs.update { currentList ->
            val job = currentList.find { it.id == jobId } ?: return@update currentList
            jobName = job.name
            if (job.status == JobStatus.Running || job.status == JobStatus.Queued || job.status == JobStatus.Paused) {
                cancelled = true
                currentList.map { if (it.id == jobId) it.copy(status = JobStatus.Cancelled, completedAt = Clock.System.now()) else it }
            } else {
                currentList
            }
        }
        
        if (cancelled) {
            coroutinesMutex.withLock {
                activeJobCoroutines.remove(jobId)?.cancel()
            }
            addHistoryEntryInternal(jobId, jobName, "Cancelled")
            Logger.i { "Job $jobId ($jobName) cancelled" }
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
            coroutinesMutex.withLock {
                activeJobCoroutines.remove(jobId)?.cancel()
            }
            addHistoryEntryInternal(jobId, jobName, "Paused")
            Logger.i { "Job $jobId ($jobName) paused" }
        }
    }

    fun resumeJob(jobId: String) {
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
                    currentList.map { if (it.id == candidate.id) claimedJob!! else it }
                } else {
                    currentList
                }
            }
            
            if (claimedJob != null) {
                // If there are more jobs, signal again to wake up other idle workers
                if (_jobs.value.any { it.status == JobStatus.Queued }) {
                    jobSignal.trySend(Unit)
                }
                return claimedJob!!
            }
            
            // Wait for signal if no jobs are queued
            Logger.v { "No queued jobs, worker waiting for signal..." }
            jobSignal.receive()
        }
    }

    fun updateJobProgress(jobId: String, progress: Float) {
        _jobProgress.update { it + (jobId to progress) }
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

    suspend fun registerJobCoroutine(jobId: String, coroutineJob: Job) {
        val job = _jobs.value.find { it.id == jobId }
        // Critical check: if the job is no longer supposed to be running (e.g. paused or cancelled
        // while the worker was starting up), we cancel the coroutine immediately and don't register it.
        if (job == null || job.status != JobStatus.Running) {
            coroutineJob.cancel()
            return
        }
        coroutinesMutex.withLock {
            activeJobCoroutines[jobId] = coroutineJob
        }
    }

    suspend fun unregisterJobCoroutine(jobId: String) {
        coroutinesMutex.withLock {
            activeJobCoroutines.remove(jobId)
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
        coroutinesMutex.withLock {
            activeJobCoroutines.values.forEach { it.cancel() }
            activeJobCoroutines.clear()
        }
    }
}

