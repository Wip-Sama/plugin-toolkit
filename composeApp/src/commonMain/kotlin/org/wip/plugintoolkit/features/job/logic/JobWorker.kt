package org.wip.plugintoolkit.features.job.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.JobHandle
import org.wip.plugintoolkit.api.PluginRequest
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.logic.PluginLifecycleCoordinator
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import kotlin.time.Duration.Companion.minutes

class JobWorker(
    val workerId: Int,
    private val manager: JobManager,
    private val scope: CoroutineScope
) : KoinComponent {
    private val pluginManager: PluginManager by inject()
    private val lifecycleCoordinator: PluginLifecycleCoordinator by inject()
    private var isWorkerActive = true
    private val workerJob = SupervisorJob(scope.coroutineContext[kotlinx.coroutines.Job])
    private val workerScope = scope + workerJob

    private val executorRegistry: SystemNodeExecutorRegistry by lazy {
        try {
            get<SystemNodeExecutorRegistry>()
        } catch (e: Exception) {
            DefaultSystemNodeExecutorRegistry()
        }
    }

    fun start() {
        workerScope.launch {
            while (isWorkerActive && coroutineContext.isActive) {
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
                    if (!isWorkerActive || !isActive) {
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
                JobType.Flow -> executeFlowJob(job)
                else -> throw Exception("Unsupported job type: ${job.type}")
            }
        } catch (e: CancellationException) {
            Logger.w { "Worker $workerId: Job ${job.name} was cancelled" }
            throw e
        } catch (e: Throwable) {
            manager.tryFailJob(job.id, e.message)
            lifecycleCoordinator.onLifecycleJobFailed(job, e.message)
            Logger.e(e) { "Worker $workerId exception during job ${job.name}" }
        } finally {
            val settingsPersistence: SettingsPersistence = get()
            val appDataDir = settingsPersistence.getSettingsDir()
            val sandboxDir = kotlinx.io.files.Path("$appDataDir/jobs/${job.id}/sandbox")
            deleteRecursively(sandboxDir)
        }
    }

    private suspend fun executeCapabilityJob(job: BackgroundJob) {
        val plugin = PluginLoader.getPluginById(job.pluginId)
            ?: throw Exception("Plugin ${job.pluginId} not found")

        val manifest = plugin.getManifest().getOrThrow()
        val mutableParams = job.parameters.toMutableMap()
        val settingsPersistence: SettingsPersistence = get()
        val sandboxDir = "${settingsPersistence.getSettingsDir()}/jobs/${job.id}/sandbox/node_0"
        val (allowedPaths, isDestructive) = resolveFileAccess(manifest, job.capabilityName, mutableParams, sandboxDir)

        validateCapabilityParameters(manifest, job.capabilityName, mutableParams)

        val processor = plugin.getProcessor().getOrThrow()
        val execFs = org.wip.plugintoolkit.features.plugin.logic.DefaultExecutionFileSystem(sandboxDir)
        val context = pluginManager.createPluginContext(job.pluginId, job.id, allowedPaths = allowedPaths, isDestructiveAllowed = isDestructive, executionFileSystem = execFs)

        val request = PluginRequest(
            method = job.capabilityName,
            parameters = mutableParams,
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

        val currentJob = currentCoroutineContext()[kotlinx.coroutines.Job]!!

        val deferredResult = workerScope.async {
            kotlinx.coroutines.withTimeout(10.minutes) {
                processor.process(request, context)
            }
        }

        val handle = object : JobHandle {
            override val result: Deferred<ExecutionResult> = deferredResult

            override fun pause() {
                workerScope.launch { context.signals.sendSignal(org.wip.plugintoolkit.api.PluginSignal.PAUSE) }
            }

            override fun cancel(force: Boolean) {
                Logger.d { "Worker $workerId: Cancelling capability job ${job.id} (force=$force)" }
                workerScope.launch { context.signals.sendSignal(org.wip.plugintoolkit.api.PluginSignal.CANCEL) }
                deferredResult.cancel()
                currentJob.cancel()
            }
        }
        manager.registerJobHandle(job.id, handle)

        val result = try {
            handle.result.await()
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Logger.e(e) { "CRITICAL WARNING: Worker $workerId: Capability job ${job.id} timed out. The plugin may be hanging and could leak classloaders." }
            ExecutionResult.Error("Plugin execution timed out and may have leaked resources.", e)
        } catch (e: CancellationException) {
            Logger.w { "Worker $workerId: Capability job ${job.id} was cancelled during await" }
            throw e
        } catch (e: Exception) {
            Logger.e(e) { "Worker $workerId: Capability job ${job.id} failed with exception" }
            ExecutionResult.Error(
                "Capability invocation failed (Plugin: '${job.pluginId}', Capability: '${job.capabilityName}'): ${e.message ?: "Unknown error"}",
                e
            )
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
                val enhancedMessage =
                    "Capability invocation failed (Plugin: '${job.pluginId}', Capability: '${job.capabilityName}'): ${result.message}"
                manager.tryFailJob(job.id, enhancedMessage)
                lifecycleCoordinator.onLifecycleJobFailed(job, enhancedMessage)
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
            override val result: Deferred<ExecutionResult>
                get() = throw UnsupportedOperationException("Not used for setup")

            override fun pause() { /* Not supported */
            }

            override fun cancel(force: Boolean) {
                jobExecution.cancel()
            }
        })

        manager.updateJobProgress(job.id, 0.1f)
        manager.addJobLog(job.id, "Performing setup for ${plugin.getManifest().getOrThrow().plugin.name}...")

        if (!plugin.getManifest().getOrThrow().hasSetupHandler) {
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
            override val result: Deferred<ExecutionResult>
                get() = throw UnsupportedOperationException("Not used for update")

            override fun pause() { /* Not supported */
            }

            override fun cancel(force: Boolean) {
                jobExecution.cancel()
            }
        })

        manager.updateJobProgress(job.id, 0.2f)
        manager.addJobLog(job.id, "Running update handler for ${plugin.getManifest().getOrThrow().plugin.name}...")

        if (!plugin.getManifest().getOrThrow().hasUpdateHandler) {
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
            override val result: Deferred<ExecutionResult>
                get() = throw UnsupportedOperationException("Not used for validation")

            override fun pause() { /* Not supported */
            }

            override fun cancel(force: Boolean) {
                jobExecution.cancel()
            }
        })

        manager.updateJobProgress(job.id, 0.2f)
        manager.addJobLog(job.id, "Running validation for ${plugin.getManifest().getOrThrow().plugin.name}...")

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

        val processor = plugin.getProcessor().getOrThrow()
        val context = pluginManager.createPluginContext(job.pluginId, job.id)

        // Register a simple handle for cancellation
        val jobExecution = currentCoroutineContext()[kotlinx.coroutines.Job]!!
        manager.registerJobHandle(job.id, object : JobHandle {
            override val result: Deferred<ExecutionResult>
                get() = throw UnsupportedOperationException("Not used for actions")

            override fun pause() { /* Not supported for actions currently */
            }

            override fun cancel(force: Boolean) {
                jobExecution.cancel()
            }
        })

        manager.updateJobProgress(job.id, 0.1f)
        manager.addJobLog(job.id, "Executing action: ${job.capabilityName}")

        val manifest = plugin.getManifest().getOrThrow()
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
            override val result: Deferred<ExecutionResult>
                get() = throw UnsupportedOperationException("Not used for installation")

            override fun pause() { /* Not supported */
            }

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

    private suspend fun executeFlowJob(job: BackgroundJob) {
        manager.addJobLog(job.id, "Executing via FlowEngine...")
        val engine = FlowEngine(manager, executorRegistry, pluginManager, lifecycleCoordinator, workerScope)
        engine.executeFlowJob(job)
    }

    fun stop() {
        isWorkerActive = false
        workerJob.cancel()
    }
}
