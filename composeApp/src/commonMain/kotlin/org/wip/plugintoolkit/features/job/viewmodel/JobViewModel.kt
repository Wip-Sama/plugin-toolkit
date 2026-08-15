package org.wip.plugintoolkit.features.job.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus

class JobViewModel(
    private val jobManager: JobManager
) : ViewModel() {
    val jobs = jobManager.jobs
    val jobProgress = jobManager.jobProgress
    val jobLogs = jobManager.jobLogs
    val history = jobManager.history
    val endedJobs = jobManager.endedJobs
    val schedules = jobManager.schedules
    val scheduleLoadFailed = jobManager.scheduleLoadFailed
    private val _scheduleOperationFailed = MutableStateFlow(false)
    val scheduleOperationFailed = _scheduleOperationFailed.asStateFlow()

    val runningJobs = jobs.map { list ->
        list.filter { it.status == JobStatus.Running }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val queuedJobs = jobs.map { list ->
        list.filter { it.status == JobStatus.Queued }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pausedJobs = jobs.map { list ->
        list.filter { it.status == JobStatus.Paused }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun clearEndedJob(jobId: String) {
        viewModelScope.launch {
            jobManager.clearEndedJob(jobId)
        }
    }

    fun clearAllEndedJobs() {
        viewModelScope.launch {
            jobManager.clearAllEndedJobs()
        }
    }

    fun scheduleRecurring(job: BackgroundJob, intervalMinutes: Long, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val succeeded = jobManager.scheduleJob(job, intervalMinutes) != null
            _scheduleOperationFailed.value = !succeeded
            onResult(succeeded)
        }
    }

    fun removeSchedule(id: String) {
        viewModelScope.launch { _scheduleOperationFailed.value = !jobManager.removeSchedule(id) }
    }

    fun setScheduleEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { _scheduleOperationFailed.value = !jobManager.setScheduleEnabled(id, enabled) }
    }

    fun runScheduleNow(id: String) {
        viewModelScope.launch { _scheduleOperationFailed.value = !jobManager.runScheduleNow(id) }
    }

    fun clearScheduleError() { _scheduleOperationFailed.value = false }
}
