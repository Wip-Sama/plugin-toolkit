package com.wip.kpm_cpm_wotoolkit.features.job.logic

import com.wip.kpm_cpm_wotoolkit.features.job.model.*
import com.wip.kpm_cpm_wotoolkit.features.plugin.logic.ModuleLoader
import com.wip.plugin.api.*
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
    private val workerJob = SupervisorJob()
    private val workerScope = scope + workerJob + Dispatchers.Default

    fun start() {
        workerScope.launch {
            while (isActive) {
                try {
                    val next = manager.waitForNextJob()
                    
                    // Link this execution to the manager for cancellation support
                    val jobExecution = launch {
                        try {
                            executeJob(next)
                        } finally {
                            manager.unregisterJobCoroutine(next.id)
                        }
                    }
                    
                    try {
                        manager.registerJobCoroutine(next.id, jobExecution)
                        
                        // Just wait for the job to complete or be cancelled.
                        // Cancellation is cooperatively handled by jobExecution.cancel() from JobManager.
                        jobExecution.join()
                    } catch (e: Exception) {
                        Logger.e(e) { "Worker $workerId: Error during job coordination" }
                        jobExecution.cancelAndJoin()
                    }
                    
                } catch (e: CancellationException) {
                    // Normal cancellation, just continue or exit if worker is stopping
                    if (!isActive) break
                } catch (e: Exception) {
                    Logger.e(e) { "Worker $workerId encountered error in loop" }
                    delay(2000) // Cooling off period on error
                }
            }
        }
    }

    private suspend fun executeJob(job: BackgroundJob) {
        // Double check status - it might have been cancelled or paused while we were starting up
        val latestJob = manager.jobs.value.find { it.id == job.id }
        if (latestJob == null || latestJob.status != JobStatus.Running) {
            Logger.w { "Worker $workerId: Job ${job.name} (${job.id}) is not in Running state (status: ${latestJob?.status}), aborting execution" }
            return
        }

        Logger.i { "Worker $workerId starting job ${job.name} (${job.id})" }
        
        try {
            val plugin = ModuleLoader.getPluginById(job.pluginId) 
                ?: throw Exception("Plugin ${job.pluginId} not found")
            
            val processor = plugin.getProcessor()
            
            // Provide execution context with Logger and ProgressReporter
            val context = ExecutionContext(
                logger = object : PluginLogger {
                    override fun verbose(message: String) {
                        Logger.v { "Plugin[${job.pluginId}]: $message" }
                    }
                    override fun debug(message: String) {
                        Logger.d { "Plugin[${job.pluginId}]: $message" }
                    }
                    override fun info(message: String) {
                        Logger.i { "Plugin[${job.pluginId}]: $message" }
                    }
                    override fun warn(message: String) {
                        Logger.w { "Plugin[${job.pluginId}]: $message" }
                    }
                    override fun error(message: String, throwable: Throwable?) {
                        Logger.e(throwable) { "Plugin[${job.pluginId}]: $message" }
                    }
                },
                progress = object : ProgressReporter {
                    override fun report(progress: Float) {
                        manager.updateJobProgress(job.id, progress)
                    }
                }
            )
            processor.setExecutionContext(context)

            val request = PluginRequest(
                method = job.capabilityName,
                parameters = job.parameters
            )

            // Progress tracking - tied to this job's lifecycle
            val progressFlow = processor.observeProgress()
            val progressJob = progressFlow?.let { flow ->
                workerScope.launch {
                    flow.collect { p ->
                        // Extra safety check in loop
                        val current = manager.jobs.value.find { it.id == job.id }
                        if (current?.status == JobStatus.Running) {
                            manager.updateJobProgress(job.id, p)
                        } else {
                            // If it's no longer running, stop collecting progress
                            cancel()
                        }
                    }
                }
            }

            // Execute the plugin task
            val result = try {
                processor.process(request)
            } finally {
                progressJob?.cancel()
            }
            
            if (result.isSuccess) {
                manager.tryCompleteJob(job.id, result.getOrNull()?.result?.toString())
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                manager.tryFailJob(job.id, error)
            }
        } catch (e: CancellationException) {
            Logger.w { "Worker $workerId: Job ${job.name} was cancelled" }
            throw e // Re-throw to allow join() to finish
        } catch (e: Exception) {
            manager.tryFailJob(job.id, e.message)
            Logger.e(e) { "Worker $workerId exception during job ${job.name}" }
        }
    }

    fun stop() {
        isActive = false
        workerJob.cancel()
    }
}

