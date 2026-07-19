package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.wip.plugintoolkit.api.PluginAction
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobType
import kotlin.time.Clock

sealed interface LifecycleAction {
    data class OnJobCompleted(val job: BackgroundJob, val response: CompletableDeferred<Unit>) : LifecycleAction
    data class OnJobFailed(val job: BackgroundJob, val error: String?, val response: CompletableDeferred<Unit>) :
        LifecycleAction

    data class OnManualValidation(val pkg: String, val result: Result<Unit>, val response: CompletableDeferred<Unit>) :
        LifecycleAction

    data class LoadPlugin(val pkg: String, val response: CompletableDeferred<Result<Unit>>) : LifecycleAction
    data class UnloadPlugin(val pkg: String, val response: CompletableDeferred<Unit>) : LifecycleAction
    data class ReloadPlugin(val pkg: String, val response: CompletableDeferred<Unit>) : LifecycleAction
    data class HandlePostInstall(
        val pkg: String,
        val manifest: org.wip.plugintoolkit.api.PluginManifest,
        val response: CompletableDeferred<Unit>
    ) : LifecycleAction

    data class HandlePostUpdate(
        val pkg: String,
        val manifest: org.wip.plugintoolkit.api.PluginManifest,
        val installer: PluginInstaller,
        val response: CompletableDeferred<Unit>
    ) : LifecycleAction

    data class EnqueueSetupJob(val pkg: String, val response: CompletableDeferred<Unit>) : LifecycleAction
    data class EnqueueUpdateJob(val pkg: String, val response: CompletableDeferred<Unit>) : LifecycleAction
    data class TriggerValidation(val pkg: String, val response: CompletableDeferred<Result<Unit>>) : LifecycleAction
    data class CheckAndResumeSetup(val pkg: String, val response: CompletableDeferred<Unit>) : LifecycleAction
    data class RerunSetup(val pkg: String, val installer: PluginInstaller, val response: CompletableDeferred<Unit>) :
        LifecycleAction

    data class SetEnabled(val pkg: String, val enabled: Boolean, val response: CompletableDeferred<Result<Unit>>) :
        LifecycleAction

    data class ValidatePlugin(val pkg: String, val response: CompletableDeferred<Result<Unit>>) : LifecycleAction
    data class RunAction(val pkg: String, val action: PluginAction, val response: CompletableDeferred<Unit>) :
        LifecycleAction
}

/**
 * Coordinates the lifecycle state transitions for plugins.
 * Ensures plugins move through Setup -> Update -> Validation -> Loaded sequentially
 * using the Actor pattern to avoid concurrent modifications.
 */
class PluginLifecycleCoordinator(
    private val registry: PluginRegistry,
    private val jobManager: JobManager,
    private val lifecycleManager: PluginLifecycleManager,
    /** Injected [AppScope] for non-blocking logic and state transitions. */
    scope: CoroutineScope
) {

    private val actorChannel = Channel<LifecycleAction>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (action in actorChannel) {
                try {
                    processAction(action)
                } catch (e: Exception) {
                    Logger.e(e) { "Error processing LifecycleAction: $action" }
                }
            }
        }
    }

    private suspend fun processAction(action: LifecycleAction) {
        when (action) {
            is LifecycleAction.OnJobCompleted -> {
                val job = action.job
                Logger.d { "Lifecycle job completed for ${job.pluginId}: ${job.type}" }
                when (job.type) {
                    JobType.Validation -> markAsValidated(job.pluginId)
                    JobType.Setup, JobType.Update -> triggerValidationInternal(job.pluginId)
                    JobType.PluginAction -> clearRequiredAction(job.pluginId)
                    else -> {}
                }
                action.response.complete(Unit)
            }

            is LifecycleAction.OnJobFailed -> {
                val job = action.job
                Logger.w { "Lifecycle job failed for ${job.pluginId}: ${job.type} - ${action.error}" }
                if (job.type == JobType.Validation || job.type == JobType.Setup || job.type == JobType.Update) {
                    markAsInvalidated(job.pluginId, action.error)
                }
                action.response.complete(Unit)
            }

            is LifecycleAction.OnManualValidation -> {
                if (action.result.isSuccess) {
                    markAsValidated(action.pkg)
                } else {
                    markAsInvalidated(action.pkg, action.result.exceptionOrNull()?.message)
                }
                action.response.complete(Unit)
            }

            is LifecycleAction.LoadPlugin -> {
                action.response.complete(lifecycleManager.loadPlugin(action.pkg))
            }

            is LifecycleAction.UnloadPlugin -> {
                lifecycleManager.unloadPlugin(action.pkg)
                action.response.complete(Unit)
            }

            is LifecycleAction.ReloadPlugin -> {
                lifecycleManager.reloadPlugin(action.pkg)
                action.response.complete(Unit)
            }

            is LifecycleAction.HandlePostInstall -> {
                if (action.manifest.hasSetupHandler) {
                    enqueueSetupJobInternal(action.pkg)
                } else {
                    triggerValidationInternal(action.pkg)
                }
                action.response.complete(Unit)
            }

            is LifecycleAction.HandlePostUpdate -> {
                if (action.manifest.hasUpdateHandler) {
                    enqueueUpdateJobInternal(action.pkg)
                } else if (action.manifest.hasSetupHandler) {
                    action.installer.clearFiles(action.pkg)
                    enqueueSetupJobInternal(action.pkg)
                } else {
                    triggerValidationInternal(action.pkg)
                }
                action.response.complete(Unit)
            }

            is LifecycleAction.EnqueueSetupJob -> {
                enqueueSetupJobInternal(action.pkg)
                action.response.complete(Unit)
            }

            is LifecycleAction.EnqueueUpdateJob -> {
                enqueueUpdateJobInternal(action.pkg)
                action.response.complete(Unit)
            }

            is LifecycleAction.TriggerValidation -> {
                action.response.complete(triggerValidationInternal(action.pkg))
            }

            is LifecycleAction.CheckAndResumeSetup -> {
                val plugin = registry.getPlugin(action.pkg)
                if (plugin != null) {
                    if (hasMissingRequiredSettings(action.pkg)) {
                        Logger.w { "Plugin ${action.pkg} is missing required settings, marking as broken." }
                        registry.updatePlugin(action.pkg) { it.copy(requiredAction = "CONFIGURE_SETTINGS") }
                        if (lifecycleManager.loadedPlugins.value.contains(action.pkg)) {
                            try {
                                lifecycleManager.unloadPlugin(action.pkg)
                            } catch (e: Exception) {
                                Logger.e(e) { "Failed to unload plugin ${action.pkg} after settings were removed" }
                            }
                        }
                    } else if (plugin.requiredAction == "CONFIGURE_SETTINGS") {
                        Logger.i { "Required settings provided for ${action.pkg}. Resuming setup/update." }
                        clearRequiredAction(action.pkg)
                        val manifest = lifecycleManager.getManifest(action.pkg)
                        if (manifest?.hasUpdateHandler == true) {
                            enqueueUpdateJobInternal(action.pkg)
                        } else if (manifest?.hasSetupHandler == true) {
                            enqueueSetupJobInternal(action.pkg)
                        } else {
                            if (plugin.isValidated) {
                                if (plugin.isEnabled && !lifecycleManager.loadedPlugins.value.contains(action.pkg)) {
                                    val loadResult = lifecycleManager.loadPlugin(action.pkg)
                                    if (loadResult.isFailure) {
                                        markAsInvalidated(action.pkg, loadResult.exceptionOrNull()?.message)
                                    }
                                }
                            } else {
                                triggerValidationInternal(action.pkg)
                            }
                        }
                    } else {
                        // Settings were updated but plugin is not broken and doesn't require configuring settings.
                        // We must reload the plugin if it's active so that Koin re-injects the updated settings.
                        if (plugin.isEnabled && lifecycleManager.loadedPlugins.value.contains(action.pkg)) {
                            try {
                                lifecycleManager.reloadPlugin(action.pkg)
                            } catch (e: Exception) {
                                Logger.e(e) { "Failed to reload plugin ${action.pkg} after settings update" }
                            }
                        }
                    }
                }
                action.response.complete(Unit)
            }

            is LifecycleAction.RerunSetup -> {
                Logger.i { "Rerunning setup for plugin: ${action.pkg}" }
                lifecycleManager.unloadPlugin(action.pkg)
                action.installer.clearFiles(action.pkg)
                registry.updatePlugin(action.pkg) { it.copy(isValidated = false, loadError = null) }
                enqueueSetupJobInternal(action.pkg)
                action.response.complete(Unit)
            }

            is LifecycleAction.SetEnabled -> {
                Logger.i { "Setting plugin ${action.pkg} enabled: ${action.enabled}" }
                if (!action.enabled) {
                    val safeRes = lifecycleManager.ensureSafeToUnload(listOf(action.pkg))
                    if (safeRes.isFailure) {
                        action.response.complete(Result.failure(safeRes.exceptionOrNull()!!))
                        return
                    }
                    lifecycleManager.unloadPlugin(action.pkg)
                }

                registry.updatePlugin(action.pkg) { it.copy(isEnabled = action.enabled) }

                if (action.enabled) {
                    val plugin = registry.getPlugin(action.pkg)
                    if (plugin != null) {
                        if (plugin.requiredAction != null) {
                            Logger.w { "Cannot load plugin ${action.pkg} because it requires action: ${plugin.requiredAction}" }
                        } else if (plugin.isValidated) {
                            val loadResult = lifecycleManager.loadPlugin(action.pkg)
                            if (loadResult.isFailure) {
                                markAsInvalidated(action.pkg, loadResult.exceptionOrNull()?.message)
                            }
                        } else {
                            val manifest = lifecycleManager.getManifest(action.pkg)
                            if (manifest?.hasSetupHandler == true) {
                                enqueueSetupJobInternal(action.pkg)
                            } else {
                                triggerValidationInternal(action.pkg)
                            }
                        }
                    }
                }
                action.response.complete(Result.success(Unit))
            }

            is LifecycleAction.ValidatePlugin -> {
                val plugin = PluginLoader.getPluginById(action.pkg)
                if (plugin == null) {
                    val error = "Plugin not loaded"
                    markAsInvalidated(action.pkg, error)
                    action.response.complete(Result.failure(Exception(error)))
                } else {
                    val result = plugin.validate(lifecycleManager.createPluginContext(action.pkg))
                    if (result.isSuccess) {
                        markAsValidated(action.pkg)
                    } else {
                        markAsInvalidated(action.pkg, result.exceptionOrNull()?.message)
                    }
                    action.response.complete(result)
                }
            }

            is LifecycleAction.RunAction -> {
                val plugin = registry.getPlugin(action.pkg)
                if (plugin != null) {
                    Logger.i { "Enqueuing custom action: ${action.action.name} for plugin: ${action.pkg}" }
                    val loadResult = lifecycleManager.loadPlugin(action.pkg)
                    if (loadResult.isFailure) {
                        val errorMsg = loadResult.exceptionOrNull()?.message
                        Logger.e { "Failed to load plugin ${action.pkg} for action ${action.action.name}: $errorMsg" }
                        markAsInvalidated(action.pkg, errorMsg)
                    } else {
                        val job = BackgroundJob(
                            id = "action_${action.pkg}_${action.action.functionName}_${
                                Clock.System.now().toEpochMilliseconds()
                            }",
                            name = "Action: ${action.action.name} (${plugin.name})",
                            type = JobType.PluginAction,
                            pluginId = action.pkg,
                            capabilityName = action.action.functionName,
                            keepResult = false
                        )
                        jobManager.enqueueJob(job)
                    }
                }
                action.response.complete(Unit)
            }
        }
    }

    // --- Public API ---

    suspend fun onLifecycleJobCompleted(job: BackgroundJob) {
        val deferred = CompletableDeferred<Unit>()
        actorChannel.send(LifecycleAction.OnJobCompleted(job, deferred))
        deferred.await()
    }

    suspend fun onLifecycleJobFailed(job: BackgroundJob, error: String?) {
        val deferred = CompletableDeferred<Unit>()
        actorChannel.send(LifecycleAction.OnJobFailed(job, error, deferred))
        deferred.await()
    }

    suspend fun onManualValidationCompleted(pkg: String, result: Result<Unit>) {
        val deferred = CompletableDeferred<Unit>()
        actorChannel.send(LifecycleAction.OnManualValidation(pkg, result, deferred))
        deferred.await()
    }

    suspend fun loadPlugin(pkg: String): Result<Unit> {
        val deferred = CompletableDeferred<Result<Unit>>()
        actorChannel.send(LifecycleAction.LoadPlugin(pkg, deferred))
        return deferred.await()
    }

    suspend fun unloadPlugin(pkg: String) {
        val deferred = CompletableDeferred<Unit>()
        actorChannel.send(LifecycleAction.UnloadPlugin(pkg, deferred))
        deferred.await()
    }

    suspend fun reloadPlugin(pkg: String) {
        val deferred = CompletableDeferred<Unit>()
        actorChannel.send(LifecycleAction.ReloadPlugin(pkg, deferred))
        deferred.await()
    }

    suspend fun handlePostInstall(pkg: String, manifest: org.wip.plugintoolkit.api.PluginManifest) {
        val deferred = CompletableDeferred<Unit>()
        actorChannel.send(LifecycleAction.HandlePostInstall(pkg, manifest, deferred))
        deferred.await()
    }

    suspend fun handlePostUpdate(
        pkg: String,
        manifest: org.wip.plugintoolkit.api.PluginManifest,
        installer: PluginInstaller
    ) {
        val deferred = CompletableDeferred<Unit>()
        actorChannel.send(LifecycleAction.HandlePostUpdate(pkg, manifest, installer, deferred))
        deferred.await()
    }

    suspend fun enqueueSetupJob(pkg: String) {
        val deferred = CompletableDeferred<Unit>()
        actorChannel.send(LifecycleAction.EnqueueSetupJob(pkg, deferred))
        deferred.await()
    }

    suspend fun enqueueUpdateJob(pkg: String) {
        val deferred = CompletableDeferred<Unit>()
        actorChannel.send(LifecycleAction.EnqueueUpdateJob(pkg, deferred))
        deferred.await()
    }

    suspend fun triggerValidation(pkg: String): Result<Unit> {
        val deferred = CompletableDeferred<Result<Unit>>()
        actorChannel.send(LifecycleAction.TriggerValidation(pkg, deferred))
        return deferred.await()
    }

    suspend fun checkAndResumeSetup(pkg: String) {
        val deferred = CompletableDeferred<Unit>()
        actorChannel.send(LifecycleAction.CheckAndResumeSetup(pkg, deferred))
        deferred.await()
    }

    suspend fun rerunSetup(pkg: String, installer: PluginInstaller) {
        val deferred = CompletableDeferred<Unit>()
        actorChannel.send(LifecycleAction.RerunSetup(pkg, installer, deferred))
        deferred.await()
    }

    suspend fun setEnabled(pkg: String, enabled: Boolean): Result<Unit> {
        val deferred = CompletableDeferred<Result<Unit>>()
        actorChannel.send(LifecycleAction.SetEnabled(pkg, enabled, deferred))
        return deferred.await()
    }

    suspend fun validatePlugin(pkg: String): Result<Unit> {
        val deferred = CompletableDeferred<Result<Unit>>()
        actorChannel.send(LifecycleAction.ValidatePlugin(pkg, deferred))
        return deferred.await()
    }

    suspend fun runAction(pkg: String, action: PluginAction) {
        val deferred = CompletableDeferred<Unit>()
        actorChannel.send(LifecycleAction.RunAction(pkg, action, deferred))
        deferred.await()
    }

    // --- Internal Helpers (Actor Thread Only) ---

    private suspend fun enqueueSetupJobInternal(pkg: String) {
        val plugin = registry.getPlugin(pkg) ?: return
        if (isJobPendingOrRunning("setup_$pkg")) return

        val manifest = lifecycleManager.getManifest(pkg)
        if (hasMissingRequiredSettings(pkg, manifest)) {
            registry.updatePlugin(pkg) { it.copy(requiredAction = "CONFIGURE_SETTINGS") }
            return
        }

        val loadResult = lifecycleManager.loadPlugin(pkg)
        if (loadResult.isFailure) {
            markAsInvalidated(pkg, loadResult.exceptionOrNull()?.message)
            return
        }

        if (manifest?.hasSetupHandler != true) {
            triggerValidationInternal(pkg)
            return
        }

        val job = BackgroundJob(
            id = "setup_$pkg",
            name = "Setup: ${plugin.name}",
            type = JobType.Setup,
            pluginId = pkg,
            capabilityName = "setup",
            keepResult = false
        )
        jobManager.enqueueJob(job)
    }

    private suspend fun enqueueUpdateJobInternal(pkg: String) {
        val plugin = registry.getPlugin(pkg) ?: return
        if (isJobPendingOrRunning("update_$pkg")) return

        val manifest = lifecycleManager.getManifest(pkg)
        if (hasMissingRequiredSettings(pkg, manifest)) {
            registry.updatePlugin(pkg) { it.copy(requiredAction = "CONFIGURE_SETTINGS") }
            return
        }

        val loadResult = lifecycleManager.loadPlugin(pkg)
        if (loadResult.isFailure) {
            markAsInvalidated(pkg, loadResult.exceptionOrNull()?.message)
            return
        }

        if (manifest?.hasUpdateHandler != true) {
            triggerValidationInternal(pkg)
            return
        }

        val job = BackgroundJob(
            id = "update_$pkg",
            name = "Update: ${plugin.name}",
            type = JobType.Update,
            pluginId = pkg,
            capabilityName = "update",
            keepResult = false
        )
        jobManager.enqueueJob(job)
    }

    private suspend fun triggerValidationInternal(pkg: String): Result<Unit> {
        val plugin = registry.getPlugin(pkg) ?: return Result.failure(Exception("Plugin not found"))
        if (plugin.isValidated) return Result.success(Unit)
        if (isJobPendingOrRunning("val_$pkg")) return Result.success(Unit)

        val manifest = lifecycleManager.getManifest(pkg)
        if (hasMissingRequiredSettings(pkg, manifest)) {
            registry.updatePlugin(pkg) { it.copy(requiredAction = "CONFIGURE_SETTINGS") }
            return Result.failure(Exception("Missing required settings"))
        }

        val loadResult = lifecycleManager.loadPlugin(pkg)
        if (loadResult.isFailure) {
            markAsInvalidated(pkg, loadResult.exceptionOrNull()?.message)
            return Result.failure(loadResult.exceptionOrNull()!!)
        }

        val job = BackgroundJob(
            id = "val_$pkg",
            name = "Validation: ${plugin.name}",
            type = JobType.Validation,
            pluginId = pkg,
            capabilityName = "validate",
            keepResult = false
        )
        jobManager.enqueueJob(job)
        return Result.success(Unit)
    }

    private fun isJobPendingOrRunning(jobId: String): Boolean {
        return jobManager.activeJobIds.value.contains(jobId)
    }

    private suspend fun markAsValidated(pkg: String) {
        val plugin = registry.getPlugin(pkg) ?: return
        if (plugin.isValidated) return
        registry.updatePlugin(pkg) { it.copy(isValidated = true) }
        lifecycleManager.loadPlugin(pkg)
    }

    private suspend fun markAsInvalidated(pkg: String, error: String? = null) {
        val plugin = registry.getPlugin(pkg) ?: return
        registry.updatePlugin(pkg) { it.copy(isValidated = false, loadError = error) }
        if (lifecycleManager.loadedPlugins.value.contains(pkg)) {
            lifecycleManager.unloadPlugin(pkg)
        }
    }

    private suspend fun clearRequiredAction(pkg: String) {
        registry.updatePlugin(pkg) { it.copy(requiredAction = null) }
    }

    private fun hasMissingRequiredSettings(
        pkg: String,
        manifest: org.wip.plugintoolkit.api.PluginManifest? = null
    ): Boolean {
        val actualManifest = manifest ?: lifecycleManager.getManifest(pkg) ?: return false
        if (actualManifest.settings.isNullOrEmpty()) return false

        val store = lifecycleManager.loadPluginSettings(pkg)
        return actualManifest.settings!!.any { (key, meta) ->
            if (!meta.required) return@any false
            val value = store.settings[key] ?: return@any true
            if (!meta.type.isProvided(value)) return@any true
            false
        }
    }
}
