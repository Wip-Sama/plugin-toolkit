package org.wip.plugintoolkit.features.job.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.JobHandle
import org.wip.plugintoolkit.api.PluginResponse
import org.wip.plugintoolkit.api.PluginRequest
import org.wip.plugintoolkit.api.ExecutionContext
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.plugin.logic.PluginManager

class JobWorker(
    val workerId: Int,
    private val manager: JobManager,
    private val scope: CoroutineScope
) : KoinComponent {
    private val pluginManager: PluginManager by inject()
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
                            manager.unregisterJobHandle(next.id)
                        }
                    }

                    try {
                        // Registration now happens inside executeJob because we need the JobHandle from the plugin

                        // Just wait for the job to complete or be canceled.
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
                    delay(2000) //TODO: maybe remove this
                    // Cooling-off period on error
                }
            }
        }
    }

    private suspend fun executeJob(job: BackgroundJob) {
        val latestJob = manager.jobs.value.find { it.id == job.id }
        if (latestJob == null || latestJob.status != JobStatus.Running) {
            Logger.w { "Worker $workerId: Job ${job.name} (${job.id}) is not in Running state, aborting" }
            return
        }

        Logger.i { "Worker $workerId starting job ${job.name} (${job.id}) of type ${job.type}" }

        try {
            when (job.type) {
                JobType.Capability -> executeCapabilityJob(job)
                JobType.Setup -> executeSetupJob(job)
                JobType.Validation -> executeValidationJob(job)
                JobType.PluginAction -> executePluginActionJob(job)
                else -> throw Exception("Unsupported job type: ${job.type}")
            }
        } catch (e: CancellationException) {
            Logger.w { "Worker $workerId: Job ${job.name} was cancelled" }
            throw e
        } catch (e: Exception) {
            manager.tryFailJob(job.id, e.message)
            Logger.e(e) { "Worker $workerId exception during job ${job.name}" }
        }
    }

    private suspend fun executeCapabilityJob(job: BackgroundJob) {
        val plugin = PluginLoader.getPluginById(job.pluginId)
            ?: throw Exception("Plugin ${job.pluginId} not found")

        val processor = plugin.getProcessor()
        val context = pluginManager.createPluginContext(job.pluginId, job.id)
        processor.setExecutionContext(context)

        val request = PluginRequest(
            method = job.capabilityName,
            parameters = job.parameters,
            resumeState = job.resumeState
        )

        val progressFlow = processor.observeProgress()
        val progressJob = progressFlow?.let { flow ->
            workerScope.launch {
                flow.collect { p ->
                    val current = manager.jobs.value.find { it.id == job.id }
                    if (current?.status == JobStatus.Running) {
                        manager.updateJobProgress(job.id, p)
                    } else {
                        this.cancel()
                    }
                }
            }
        }

        val handle = processor.processAsync(request)
        manager.registerJobHandle(job.id, handle)

        val result = try {
            handle.result.await()
        } catch (e: Exception) {
            ExecutionResult.Error(e.message ?: "Unknown error", e)
        } finally {
            progressJob?.cancel()
        }

        when (result) {
            is ExecutionResult.Success -> {
                val response = result.response
                manager.tryCompleteJob(job.id, response.result?.toString())
            }
            is ExecutionResult.Paused -> {
                manager.tryPauseJob(job.id, result.resumeState)
            }
            is ExecutionResult.Error -> {
                manager.tryFailJob(job.id, result.message)
            }
        }
    }

    private suspend fun executeSetupJob(job: BackgroundJob) {
        val plugin = PluginLoader.getPluginById(job.pluginId)
            ?: throw Exception("Plugin ${job.pluginId} not found")

        val context = pluginManager.createPluginContext(job.pluginId, job.id)

        // Register a simple handle for cancellation
        val jobExecution = currentCoroutineContext()[kotlinx.coroutines.Job]!!
        manager.registerJobHandle(job.id, object : JobHandle {
            override val result: kotlinx.coroutines.Deferred<ExecutionResult>
                get() = throw UnsupportedOperationException("Not used for setup")

            override fun pause() { /* Not supported */
            }

            override fun cancel(force: Boolean) {
                jobExecution.cancel()
            }
        })

        manager.updateJobProgress(job.id, 0.1f)
        manager.addJobLog(job.id, "Starting validation for ${plugin.getManifest().plugin.name}...")

        val validationResult = plugin.validate(context)
        if (validationResult.isFailure) {
            val error = validationResult.exceptionOrNull()?.message ?: "Validation failed"
            manager.tryFailJob(job.id, error)
            return
        }

        manager.updateJobProgress(job.id, 0.5f)
        manager.addJobLog(job.id, "Validation successful. Performing setup...")

        val setupResult = plugin.performSetup(context)
        if (setupResult.isFailure) {
            val error = setupResult.exceptionOrNull()?.message ?: "Setup failed"
            manager.tryFailJob(job.id, error)
            return
        }

        manager.updateJobProgress(job.id, 1.0f)
        manager.addJobLog(job.id, "Setup completed successfully.")
        manager.tryCompleteJob(job.id, "Success")
    }

    private suspend fun executeValidationJob(job: BackgroundJob) {
        val plugin = PluginLoader.getPluginById(job.pluginId)
            ?: throw Exception("Plugin ${job.pluginId} not found")

        val context = pluginManager.createPluginContext(job.pluginId, job.id)
 
         // Register a simple handle for cancellation
         val jobExecution = currentCoroutineContext()[kotlinx.coroutines.Job]!!
         manager.registerJobHandle(job.id, object : JobHandle {
            override val result: kotlinx.coroutines.Deferred<ExecutionResult>
                get() = throw UnsupportedOperationException("Not used for validation")

            override fun pause() { /* Not supported */
            }

            override fun cancel(force: Boolean) {
                jobExecution.cancel()
            }
        })

        manager.updateJobProgress(job.id, 0.2f)
        manager.addJobLog(job.id, "Running validation for ${plugin.getManifest().plugin.name}...")

        val validationResult = plugin.validate(context)
        if (validationResult.isFailure) {
            val error = validationResult.exceptionOrNull()?.message ?: "Validation failed"
            manager.tryFailJob(job.id, error)
            return
        }

        manager.updateJobProgress(job.id, 1.0f)
        manager.addJobLog(job.id, "Validation successful.")
        manager.tryCompleteJob(job.id, "Success")
    }

    private suspend fun executePluginActionJob(job: BackgroundJob) {
        val plugin = PluginLoader.getPluginById(job.pluginId)
            ?: throw Exception("Plugin ${job.pluginId} not found")

        val processor = plugin.getProcessor()
        val context = pluginManager.createPluginContext(job.pluginId, job.id)

        // Register a simple handle for cancellation
        val jobExecution = currentCoroutineContext()[kotlinx.coroutines.Job]!!
        manager.registerJobHandle(job.id, object : JobHandle {
            override val result: kotlinx.coroutines.Deferred<ExecutionResult>
                get() = throw UnsupportedOperationException("Not used for actions")

            override fun pause() { /* Not supported for actions currently */ }

            override fun cancel(force: Boolean) {
                jobExecution.cancel()
            }
        })

        manager.updateJobProgress(job.id, 0.1f)
        manager.addJobLog(job.id, "Executing action: ${job.capabilityName}")

        val manifest = plugin.getManifest()
        val action = manifest.actions.find { it.functionName == job.capabilityName }
            ?: throw Exception("Action ${job.capabilityName} not found in manifest")

        val result = processor.runAction(action, context)
        
        if (result.isSuccess) {
            manager.updateJobProgress(job.id, 1.0f)
            manager.addJobLog(job.id, "Action completed successfully.")
            manager.tryCompleteJob(job.id, "Success")
        } else {
            val error = result.exceptionOrNull()?.message ?: "Action failed"
            manager.tryFailJob(job.id, error)
        }
    }

    fun stop() {
        isActive = false
        workerJob.cancel()
    }
}
