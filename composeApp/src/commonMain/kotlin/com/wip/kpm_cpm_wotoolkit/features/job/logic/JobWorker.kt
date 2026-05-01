package com.wip.kpm_cpm_wotoolkit.features.job.logic

import co.touchlab.kermit.Logger
import com.wip.kpm_cpm_wotoolkit.features.job.model.BackgroundJob
import com.wip.kpm_cpm_wotoolkit.features.job.model.JobStatus
import com.wip.kpm_cpm_wotoolkit.features.plugin.logic.DefaultPluginFileSystem
import com.wip.kpm_cpm_wotoolkit.features.plugin.logic.PluginLoader
import com.wip.plugin.api.ExecutionContext
import com.wip.plugin.api.PluginLogger
import com.wip.plugin.api.PluginRequest
import com.wip.plugin.api.ProgressReporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus

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
            val plugin = PluginLoader.getPluginById(job.pluginId)
                ?: throw Exception("Plugin ${job.pluginId} not found")

            val processor = plugin.getProcessor()

            // Provide execution context with Logger and ProgressReporter
            val context = ExecutionContext(
                logger = object : PluginLogger {
                    override fun verbose(message: String) {
                        val fullMessage = "[${job.pluginId}] $message"
                        Logger.v { fullMessage }
                        manager.addJobLog(job.id, fullMessage, "VERBOSE")
                    }

                    override fun debug(message: String) {
                        val fullMessage = "[${job.pluginId}] $message"
                        Logger.d { fullMessage }
                        manager.addJobLog(job.id, fullMessage, "DEBUG")
                    }

                    override fun info(message: String) {
                        val fullMessage = "[${job.pluginId}] $message"
                        Logger.i { fullMessage }
                        manager.addJobLog(job.id, fullMessage, "INFO")
                    }

                    override fun warn(message: String) {
                        val fullMessage = "[${job.pluginId}] $message"
                        Logger.w { fullMessage }
                        manager.addJobLog(job.id, fullMessage, "WARN")
                    }

                    override fun error(message: String, throwable: Throwable?) {
                        val fullMessage = "[${job.pluginId}] $message"
                        Logger.e(throwable) { fullMessage }
                        manager.addJobLog(job.id, fullMessage + (throwable?.let { ": ${it.message}" } ?: ""), "ERROR")
                    }
                },
                progress = object : ProgressReporter {
                    override fun report(progress: Float) {
                        manager.updateJobProgress(job.id, progress)
                    }
                },
                fileSystem = DefaultPluginFileSystem(
                    PluginLoader.getPluginInstallPath(job.pluginId) ?: "",
                    PluginLoader.getPluginJarPath(job.pluginId)
                )
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

