package com.wip.kpm_cpm_wotoolkit.features.job.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wip.kpm_cpm_wotoolkit.features.job.logic.JobManager
import com.wip.kpm_cpm_wotoolkit.features.job.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.collections.emptyList

class JobViewModel(
    private val jobManager: JobManager
) : ViewModel() {
    val jobs = jobManager.jobs
    val history = jobManager.history

    val runningJobs = jobs.map { list ->
        list.filter { it.status == JobStatus.Running }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<BackgroundJob>())

    val queuedJobs = jobs.map { list ->
        list.filter { it.status == JobStatus.Queued }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<BackgroundJob>())

    val pausedJobs = jobs.map { list ->
        list.filter { it.status == JobStatus.Paused }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList<BackgroundJob>())

    fun enqueueJob(job: BackgroundJob) {
        viewModelScope.launch {
            jobManager.enqueueJob(job)
        }
    }

    fun cancelJob(jobId: String) {
        viewModelScope.launch {
            jobManager.cancelJob(jobId)
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
