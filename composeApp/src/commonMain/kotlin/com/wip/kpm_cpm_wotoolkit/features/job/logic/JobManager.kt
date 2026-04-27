package com.wip.kpm_cpm_wotoolkit.features.job.logic

import com.wip.kpm_cpm_wotoolkit.features.job.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.collections.emptyList

class JobManager(
    private val scope: CoroutineScope,
    private val maxConcurrentJobs: Int = 2
) {
    private val _jobs = MutableStateFlow<List<BackgroundJob>>(emptyList())
    val jobs: StateFlow<List<BackgroundJob>> = _jobs.asStateFlow()

    private val _history = MutableStateFlow<List<JobHistoryEntry>>(emptyList())
    val history: StateFlow<List<JobHistoryEntry>> = _history.asStateFlow()

    private val mutex = Mutex()
    private val workers = mutableListOf<JobWorker>()

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

    suspend fun enqueueJob(job: BackgroundJob) {
        mutex.withLock {
            _jobs.value = _jobs.value + job
            addHistoryEntry(job.id, job.name, "Enqueued")
        }
    }

    suspend fun updateJob(jobId: String, update: (BackgroundJob) -> BackgroundJob) {
        mutex.withLock {
            val currentList = _jobs.value.toMutableList()
            val index = currentList.indexOfFirst { it.id == jobId }
            if (index != -1) {
                currentList[index] = update(currentList[index])
                _jobs.value = currentList
            }
        }
    }

    suspend fun cancelJob(jobId: String) {
        mutex.withLock {
            val job = _jobs.value.find { it.id == jobId } ?: return
            if (job.status == JobStatus.Running || job.status == JobStatus.Queued || job.status == JobStatus.Paused) {
                updateJob(jobId) { it.copy(status = JobStatus.Cancelled, completedAt = Clock.System.now()) }
                addHistoryEntry(jobId, job.name, "Cancelled")
            }
        }
    }

    suspend fun pauseJob(jobId: String) {
        mutex.withLock {
            val job = _jobs.value.find { it.id == jobId } ?: return
            if (job.status == JobStatus.Running) {
                updateJob(jobId) { it.copy(status = JobStatus.Paused) }
                addHistoryEntry(jobId, job.name, "Paused")
            }
        }
    }

    suspend fun resumeJob(jobId: String) {
        mutex.withLock {
            val job = _jobs.value.find { it.id == jobId } ?: return
            if (job.status == JobStatus.Paused) {
                updateJob(jobId) { it.copy(status = JobStatus.Queued) }
                addHistoryEntry(jobId, job.name, "Resumed")
            }
        }
    }

    suspend fun reorderQueue(fromIndex: Int, toIndex: Int) {
        mutex.withLock {
            val currentList = _jobs.value.toMutableList()
            // We only reorder Queued jobs? Or the whole list?
            // Usually dashboard shows all. Reordering affects which one is picked next.
            if (fromIndex in currentList.indices && toIndex in currentList.indices) {
                val item = currentList.removeAt(fromIndex)
                currentList.add(toIndex, item)
                _jobs.value = currentList
            }
        }
    }

    suspend fun pickNextJob(): BackgroundJob? {
        mutex.withLock {
            val job = _jobs.value.firstOrNull { it.status == JobStatus.Queued }
            if (job != null) {
                val updatedJob = job.copy(status = JobStatus.Running, startedAt = Clock.System.now())
                updateJobInternal(job.id) { updatedJob }
                return updatedJob
            }
            return null
        }
    }

    private fun updateJobInternal(jobId: String, update: (BackgroundJob) -> BackgroundJob) {
        val currentList = _jobs.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == jobId }
        if (index != -1) {
            currentList[index] = update(currentList[index])
            _jobs.value = currentList
        }
    }

    private fun addHistoryEntry(jobId: String, jobName: String, event: String, details: String? = null) {
        val entry = JobHistoryEntry(jobId, jobName, event = event, details = details)
        _history.value = listOf(entry) + _history.value
    }

    fun stopAll() {
        workers.forEach { it.stop() }
    }
}
