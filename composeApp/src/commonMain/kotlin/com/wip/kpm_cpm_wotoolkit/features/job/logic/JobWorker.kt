package com.wip.kpm_cpm_wotoolkit.features.job.logic

import com.wip.kpm_cpm_wotoolkit.features.job.model.*
import com.wip.kpm_cpm_wotoolkit.features.plugin.logic.ModuleLoader
import com.wip.plugin.api.PluginRequest
import kotlinx.coroutines.*
import co.touchlab.kermit.Logger
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock

class JobWorker(
    val workerId: Int,
    private val manager: JobManager,
    private val scope: CoroutineScope
) {
    private var isActive = true
    private var currentJob: BackgroundJob? = null

    fun start() {
        scope.launch(Dispatchers.Default) {
            while (isActive) {
                val next = manager.pickNextJob()
                if (next != null) {
                    currentJob = next
                    executeJob(next)
                    currentJob = null
                } else {
                    delay(1000) // Poll for new jobs
                }
            }
        }
    }

    private suspend fun executeJob(job: BackgroundJob) {
        Logger.i { "Worker $workerId starting job ${job.name}" }
        
        try {
            val plugin = ModuleLoader.getPluginById(job.pluginId) 
                ?: throw Exception("Plugin ${job.pluginId} not found")
            
            val processor = plugin.getProcessor()
            val request = PluginRequest(
                method = job.capabilityName,
                parameters = job.parameters
            )

            // Progress tracking
            val progressJob = processor.observeProgress()?.let { progressFlow ->
                scope.launch {
                    progressFlow.collect { p ->
                        manager.updateJob(job.id) { it.copy(progress = p) }
                    }
                }
            }

            // Run on Default dispatcher to ensure non-blocking
            val result = withContext(Dispatchers.Default) {
                processor.process(request)
            }
            
            progressJob?.cancel()

            if (result.isSuccess) {
                manager.updateJob(job.id) { 
                    it.copy(
                        status = JobStatus.Completed, 
                        progress = 1.0f,
                        completedAt = Clock.System.now(),
                        result = result.getOrNull()?.result?.toString()
                    ) 
                }
                Logger.i { "Worker $workerId completed job ${job.name}" }
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                manager.updateJob(job.id) { 
                    it.copy(
                        status = JobStatus.Failed, 
                        errorMessage = error,
                        completedAt = Clock.System.now()
                    ) 
                }
                Logger.e { "Worker $workerId failed job ${job.name}: $error" }
            }
        } catch (e: Exception) {
            manager.updateJob(job.id) { 
                it.copy(
                    status = JobStatus.Failed, 
                    errorMessage = e.message,
                    completedAt = Clock.System.now()
                ) 
            }
            Logger.e(e) { "Worker $workerId encountered exception during job ${job.name}" }
        }
    }

    fun stop() {
        isActive = false
    }
}
