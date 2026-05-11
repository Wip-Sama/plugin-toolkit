package org.wip.plugintoolkit.features.job.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.JobHandle
import org.wip.plugintoolkit.api.PluginRequest
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.logic.PluginLifecycleCoordinator

class JobWorker(
    val workerId: Int,
    private val manager: JobManager,
    private val scope: CoroutineScope
) : KoinComponent {
    private val pluginManager: PluginManager by inject()
    private val lifecycleCoordinator: PluginLifecycleCoordinator by inject()
    private var isActive = true
    private val workerJob = SupervisorJob(scope.coroutineContext[kotlinx.coroutines.Job])
    private val workerScope = scope + workerJob

    fun start() {
        workerScope.launch {
            while (isActive) {
                try {
                    Logger.d { "Worker $workerId: Waiting for next job..." }
                    val next = manager.waitForNextJob()
                    Logger.d { "Worker $workerId: Picked up job ${next.id} (${next.name})" }

                    // Link this execution to the manager for cancellation support
                    val jobExecution = launch {
                        try {
                            executeJob(next)
                        } finally {
                            Logger.d { "Worker $workerId: Finished job ${next.id}" }
                            manager.unregisterJobHandle(next.id)
                        }
                    }

                    try {
                        jobExecution.join()
                    } catch (e: Exception) {
                        if (e is CancellationException) {
                            Logger.i { "Worker $workerId: Loop coroutine cancelled while joining job execution" }
                            jobExecution.cancelAndJoin()
                            throw e
                        }
                        Logger.e(e) { "Worker $workerId: Error during job coordination" }
                        jobExecution.cancelAndJoin()
                    }

                } catch (e: CancellationException) {
                    Logger.i { "Worker $workerId: Loop received cancellation" }
                    if (!isActive) {
                        Logger.i { "Worker $workerId: Stopping loop" }
                        break
                    }
                } catch (e: Exception) {
                    Logger.e(e) { "Worker $workerId encountered error in loop" }
                    // Cooling-off period on error
                    kotlinx.coroutines.delay(1000)
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
                JobType.Update -> executeUpdateJob(job)
                JobType.Validation -> executeValidationJob(job)
                JobType.PluginAction -> executePluginActionJob(job)
                JobType.PluginInstallation -> executeInstallationJob(job)
                else -> throw Exception("Unsupported job type: ${job.type}")
            }
        } catch (e: CancellationException) {
            Logger.w { "Worker $workerId: Job ${job.name} was cancelled" }
            throw e
        } catch (e: Exception) {
            manager.tryFailJob(job.id, e.message)
            lifecycleCoordinator.onLifecycleJobFailed(job, e.message)
            Logger.e(e) { "Worker $workerId exception during job ${job.name}" }
        }
    }

    private suspend fun executeCapabilityJob(job: BackgroundJob) {
        val plugin = PluginLoader.getPluginById(job.pluginId)
            ?: throw Exception("Plugin ${job.pluginId} not found")

        val processor = plugin.getProcessor()
        val context = pluginManager.createPluginContext(job.pluginId, job.id)

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

        val handle = processor.processAsync(request, context)
        val currentJob = currentCoroutineContext()[kotlinx.coroutines.Job]!!

        // Wrap the handle to ensure that cancelling it also cancels our worker coroutine.
        // This prevents hanging in await() if the processor doesn't handle cancellation correctly.
        val wrappedHandle = object : JobHandle by handle {
            override fun cancel(force: Boolean) {
                Logger.d { "Worker $workerId: Cancelling capability job ${job.id} (force=$force)" }
                handle.cancel(force)
                currentJob.cancel()
            }
        }
        manager.registerJobHandle(job.id, wrappedHandle)

        val result = try {
            handle.result.await()
        } catch (e: CancellationException) {
            Logger.w { "Worker $workerId: Capability job ${job.id} was cancelled during await" }
            throw e
        } catch (e: Exception) {
            Logger.e(e) { "Worker $workerId: Capability job ${job.id} failed with exception" }
            ExecutionResult.Error(e.message ?: "Unknown error", e)
        } finally {
            progressJob?.cancel()
        }

        when (result) {
            is ExecutionResult.Success -> {
                val response = result.response
                manager.tryCompleteJob(job.id, response.result?.toString())
                lifecycleCoordinator.onLifecycleJobCompleted(job)
            }
            is ExecutionResult.Paused -> {
                manager.tryPauseJob(job.id, result.resumeState)
            }
            is ExecutionResult.Error -> {
                manager.tryFailJob(job.id, result.message)
                lifecycleCoordinator.onLifecycleJobFailed(job, result.message)
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
        manager.addJobLog(job.id, "Performing setup for ${plugin.getManifest().plugin.name}...")

        if (!plugin.getManifest().hasSetupHandler) {
            manager.addJobLog(job.id, "No setup handler found, skipping setup phase.")
        } else {
            val setupResult = plugin.performSetup(context)
            if (setupResult.isFailure) {
                val error = setupResult.exceptionOrNull()?.message ?: "Setup failed"
                manager.tryFailJob(job.id, error)
                return
            }
            manager.addJobLog(job.id, "Setup successful.")
        }

        manager.updateJobProgress(job.id, 1.0f)
        manager.addJobLog(job.id, "Setup phase completed.")
        manager.tryCompleteJob(job.id, "Success")
        lifecycleCoordinator.onLifecycleJobCompleted(job)
    }

    private suspend fun executeUpdateJob(job: BackgroundJob) {
        val plugin = PluginLoader.getPluginById(job.pluginId)
            ?: throw Exception("Plugin ${job.pluginId} not found")

        val context = pluginManager.createPluginContext(job.pluginId, job.id)

        // Register a simple handle for cancellation
        val jobExecution = currentCoroutineContext()[kotlinx.coroutines.Job]!!
        manager.registerJobHandle(job.id, object : JobHandle {
            override val result: kotlinx.coroutines.Deferred<ExecutionResult>
                get() = throw UnsupportedOperationException("Not used for update")

            override fun pause() { /* Not supported */ }

            override fun cancel(force: Boolean) {
                jobExecution.cancel()
            }
        })

        manager.updateJobProgress(job.id, 0.2f)
        manager.addJobLog(job.id, "Running update handler for ${plugin.getManifest().plugin.name}...")

        if (!plugin.getManifest().hasUpdateHandler) {
            manager.addJobLog(job.id, "No update handler found, skipping update phase.")
        } else {
            val updateResult = plugin.performUpdate(context)
            if (updateResult.isFailure) {
                val error = updateResult.exceptionOrNull()?.message ?: "Update failed"
                manager.tryFailJob(job.id, error)
                return
            }
            manager.addJobLog(job.id, "Update successful.")
        }

        manager.updateJobProgress(job.id, 1.0f)
        manager.addJobLog(job.id, "Update phase completed.")
        manager.tryCompleteJob(job.id, "Success")
        lifecycleCoordinator.onLifecycleJobCompleted(job)
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
        lifecycleCoordinator.onLifecycleJobCompleted(job)
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
            lifecycleCoordinator.onLifecycleJobCompleted(job)
        } else {
            val error = result.exceptionOrNull()?.message ?: "Action failed"
            manager.tryFailJob(job.id, error)
            lifecycleCoordinator.onLifecycleJobFailed(job, error)
        }
    }

    private suspend fun executeInstallationJob(job: BackgroundJob) {
        manager.addJobLog(job.id, "Starting remote installation for ${job.pluginId}...")
        manager.updateJobProgress(job.id, 0.05f)

        val pluginJson = job.parameters["pluginJson"] ?: throw Exception("Missing pluginJson parameter")
        val targetFolderPath = job.parameters["targetFolderPath"]?.let {
            if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString().removeSurrounding("\"")
        } ?: throw Exception("Missing targetFolderPath parameter")

        val plugin = kotlinx.serialization.json.Json.decodeFromJsonElement(
            org.wip.plugintoolkit.features.repository.model.ExtensionPlugin.serializer(),
            pluginJson
        )

        // Register a simple handle for cancellation
        val jobExecution = currentCoroutineContext()[kotlinx.coroutines.Job]!!
        manager.registerJobHandle(job.id, object : JobHandle {
            override val result: kotlinx.coroutines.Deferred<ExecutionResult>
                get() = throw UnsupportedOperationException("Not used for installation")

            override fun pause() { /* Not supported */ }

            override fun cancel(force: Boolean) {
                jobExecution.cancel()
            }
        })

        val result = pluginManager.installRemote(plugin, targetFolderPath) { progress ->
            manager.updateJobProgress(job.id, progress)
        }

        if (result.isSuccess) {
            manager.updateJobProgress(job.id, 1.0f)
            manager.addJobLog(job.id, "Installation completed successfully.")
            manager.tryCompleteJob(job.id, "Success")
            lifecycleCoordinator.onLifecycleJobCompleted(job)
        } else {
            val error = result.exceptionOrNull()?.message ?: "Installation failed"
            manager.tryFailJob(job.id, error)
            lifecycleCoordinator.onLifecycleJobFailed(job, error)
        }
    }

    fun stop() {
        isActive = false
        workerJob.cancel()
    }
}
