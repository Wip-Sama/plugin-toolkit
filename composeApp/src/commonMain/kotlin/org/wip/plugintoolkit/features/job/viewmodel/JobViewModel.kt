package org.wip.plugintoolkit.features.job.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JobViewModel(
    private val jobManager: JobManager
) : ViewModel() {
    val jobs = jobManager.jobs
    val jobProgress = jobManager.jobProgress
    val jobLogs = jobManager.jobLogs
    val history = jobManager.history

    val runningJobs = jobs.map { list ->
        list.filter { it.status == JobStatus.Running }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val queuedJobs = jobs.map { list ->
        list.filter { it.status == JobStatus.Queued }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pausedJobs = jobs.map { list ->
        list.filter { it.status == JobStatus.Paused }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun enqueueJob(job: BackgroundJob) {
        viewModelScope.launch {
            jobManager.enqueueJob(job)
        }
    }

    fun cancelJob(jobId: String, force: Boolean = false) {
        viewModelScope.launch {
            jobManager.cancelJob(jobId, force)
        }
    }

    fun pauseJob(jobId: String) {
        viewModelScope.launch {
            jobManager.pauseJob(jobId)
        }
    }

    fun resumeJob(jobId: String) {
        viewModelScope.launch {
            jobManager.resumeJob(jobId)
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            jobManager.reorderQueue(fromIndex, toIndex)
        }
    }
}
