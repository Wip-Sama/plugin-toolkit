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
import org.wip.plugintoolkit.features.plugin.model.PluginLifecycleStatus
import kotlin.time.Clock

sealed interface LifecycleAction {
    val targetPkg: String

    data class OnJobCompleted(val job: BackgroundJob, val response: CompletableDeferred<Unit>) : LifecycleAction {
        override val targetPkg: String get() = job.pluginId
    }

    data class OnJobFailed(val job: BackgroundJob, val error: String?, val response: CompletableDeferred<Unit>) :
        LifecycleAction {
        override val targetPkg: String get() = job.pluginId
    }

    data class OnManualValidation(val pkg: String, val result: Result<Unit>, val response: CompletableDeferred<Unit>) :
        LifecycleAction {
        override val targetPkg: String get() = pkg
    }

    data class LoadPlugin(val pkg: String, val response: CompletableDeferred<Result<Unit>>) : LifecycleAction {
        override val targetPkg: String get() = pkg
    }

    data class UnloadPlugin(val pkg: String, val response: CompletableDeferred<Unit>) : LifecycleAction {
        override val targetPkg: String get() = pkg
    }

    data class ReloadPlugin(val pkg: String, val response: CompletableDeferred<Unit>) : LifecycleAction {
        override val targetPkg: String get() = pkg
    }

    data class HandlePostInstall(
        val pkg: String,
        val manifest: org.wip.plugintoolkit.api.PluginManifest,
        val response: CompletableDeferred<Unit>
    ) : LifecycleAction {
        override val targetPkg: String get() = pkg
    }

    data class HandlePostUpdate(
        val pkg: String,
        val manifest: org.wip.plugintoolkit.api.PluginManifest,
        val installer: PluginInstaller,
        val response: CompletableDeferred<Unit>
    ) : LifecycleAction {
        override val targetPkg: String get() = pkg
    }

    data class EnqueueSetupJob(val pkg: String, val response: CompletableDeferred<Unit>) : LifecycleAction {
        override val targetPkg: String get() = pkg
    }

    data class EnqueueUpdateJob(val pkg: String, val response: CompletableDeferred<Unit>) : LifecycleAction {
        override val targetPkg: String get() = pkg
    }

    data class TriggerValidation(val pkg: String, val response: CompletableDeferred<Result<Unit>>) : LifecycleAction {
        override val targetPkg: String get() = pkg
    }

    data class CheckAndResumeSetup(val pkg: String, val response: CompletableDeferred<Unit>) : LifecycleAction {
        override val targetPkg: String get() = pkg
    }

    data class RerunSetup(val pkg: String, val installer: PluginInstaller, val response: CompletableDeferred<Unit>) :
        LifecycleAction {
        override val targetPkg: String get() = pkg
    }

    data class SetEnabled(val pkg: String, val enabled: Boolean, val response: CompletableDeferred<Result<Unit>>) :
        LifecycleAction {
        override val targetPkg: String get() = pkg
    }

    data class ValidatePlugin(val pkg: String, val response: CompletableDeferred<Result<Unit>>) : LifecycleAction {
        override val targetPkg: String get() = pkg
    }

    data class RunAction(
        val pkg: String,
        val action: PluginAction,
        val parameters: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
        val response: CompletableDeferred<Unit>
    ) : LifecycleAction {
        override val targetPkg: String get() = pkg
    }
}

/**
 * Coordinates the lifecycle state transitions for plugins.
 * Ensures plugins move through Setup -> Update -> Validation -> Loaded sequentially
 * using per-plugin Actors to avoid concurrent modifications without blocking other plugins.
 */
class PluginLifecycleCoordinator(
    private val registry: PluginRegistry,
    private val jobManager: JobManager,
    private val lifecycleManager: PluginLifecycleManager,
    /** Injected [AppScope] for non-blocking logic and state transitions. */
    private val scope: CoroutineScope
) {

    private val pluginActors = java.util.concurrent.ConcurrentHashMap<String, Channel<LifecycleAction>>()

    private fun getActor(pkg: String): Channel<LifecycleAction> {
        return pluginActors.getOrPut(pkg) {
            val channel = Channel<LifecycleAction>(Channel.UNLIMITED)
            scope.launch {
                for (action in channel) {
                    try {
                        processAction(action)
                    } catch (t: Throwable) {
                        Logger.e(t) { "Error processing LifecycleAction: $action" }
                        completeActionExceptionally(action, t)
                    }
                }
            }
            channel
        }
    }

    private fun completeActionExceptionally(action: LifecycleAction, t: Throwable) {
        val error = if (t is Exception) t else Exception(t.message, t)
        try {
            when (action) {
                is LifecycleAction.LoadPlugin -> action.response.complete(Result.failure(error))
                is LifecycleAction.SetEnabled -> action.response.complete(Result.failure(error))
                is LifecycleAction.TriggerValidation -> action.response.complete(Result.failure(error))
                is LifecycleAction.ValidatePlugin -> action.response.complete(Result.failure(error))
                is LifecycleAction.OnJobCompleted -> action.response.complete(Unit)
                is LifecycleAction.OnJobFailed -> action.response.complete(Unit)
                is LifecycleAction.OnManualValidation -> action.response.complete(Unit)
                is LifecycleAction.UnloadPlugin -> action.response.complete(Unit)
                is LifecycleAction.ReloadPlugin -> action.response.complete(Unit)
                is LifecycleAction.HandlePostInstall -> action.response.complete(Unit)
                is LifecycleAction.HandlePostUpdate -> action.response.complete(Unit)
                is LifecycleAction.EnqueueSetupJob -> action.response.complete(Unit)
                is LifecycleAction.EnqueueUpdateJob -> action.response.complete(Unit)
                is LifecycleAction.CheckAndResumeSetup -> action.response.complete(Unit)
                is LifecycleAction.RerunSetup -> action.response.complete(Unit)
                is LifecycleAction.RunAction -> action.response.complete(Unit)
            }
        } catch (e: Throwable) {
            Logger.e(e) { "Failed to complete deferred for action $action" }
        }
    }

    private suspend fun processAction(action: LifecycleAction) {
        Logger.d { "LifecycleCoordinator: Processing action ${action::class.simpleName} for pkg=${action.targetPkg}" }
        when (action) {
            is LifecycleAction.OnJobCompleted -> {
                val job = action.job
                Logger.i { "LifecycleCoordinator: Lifecycle job completed for ${job.pluginId}: ${job.type}" }
                when (job.type) {
                    JobType.Validation -> markAsValidated(job.pluginId)
                    JobType.Setup -> {
                        registry.updatePlugin(job.pluginId) { it.copy(isSetupCompleted = true) }
                        triggerValidationInternal(job.pluginId)
                    }
                    JobType.Update -> triggerValidationInternal(job.pluginId)
                    JobType.PluginAction -> clearRequiredAction(job.pluginId)
                    else -> {}
                }
                lifecycleManager.refreshLocks(job.pluginId)
                action.response.complete(Unit)
            }

            is LifecycleAction.OnJobFailed -> {
                val job = action.job
                Logger.w { "LifecycleCoordinator: Lifecycle job failed for ${job.pluginId}: ${job.type} - ${action.error}" }
                if (job.type == JobType.Setup) {
                    registry.updatePlugin(job.pluginId) {
                        it.copy(
                            isValidated = false,
                            status = PluginLifecycleStatus.PENDING_SETUP,
                            loadError = action.error
                        )
                    }
                    if (lifecycleManager.loadedPlugins.value.contains(job.pluginId) || PluginLoader.getPluginById(job.pluginId) != null) {
                        lifecycleManager.unloadPlugin(job.pluginId)
                    }
                } else if (job.type == JobType.Validation || job.type == JobType.Update) {
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
                val res = lifecycleManager.loadPlugin(action.pkg)
                if (res.isSuccess) {
                    lifecycleManager.refreshLocks(action.pkg)
                }
                action.response.complete(res)
            }

            is LifecycleAction.UnloadPlugin -> {
                lifecycleManager.unloadPlugin(action.pkg)
                action.response.complete(Unit)
            }

            is LifecycleAction.ReloadPlugin -> {
                Logger.i { "LifecycleCoordinator: Reloading plugin ${action.pkg}" }
                lifecycleManager.unloadPlugin(action.pkg)
                val loadRes = lifecycleManager.loadPlugin(action.pkg, forceReload = true)
                if (loadRes.isFailure) {
                    markAsInvalidated(action.pkg, loadRes.exceptionOrNull()?.message)
                } else {
                    val plugin = PluginLoader.getPluginById(action.pkg)
                    val valResult = plugin?.validate(lifecycleManager.createPluginContext(action.pkg))
                        ?: Result.failure(Exception("Plugin not loaded after reload: ${action.pkg}"))
                    if (valResult.isSuccess) {
                        markAsValidated(action.pkg)
                    } else {
                        markAsInvalidated(action.pkg, valResult.exceptionOrNull()?.message)
                    }
                }
                action.response.complete(Unit)
            }

            is LifecycleAction.HandlePostInstall -> {
                val hasSetup = action.manifest.hasSetupHandler
                registry.updatePlugin(action.pkg) {
                    it.copy(
                        isValidated = false,
                        isSetupCompleted = !hasSetup,
                        status = if (hasSetup) PluginLifecycleStatus.PENDING_SETUP else PluginLifecycleStatus.VALIDATING
                    )
                }
                if (hasSetup) {
                    enqueueSetupJobInternal(action.pkg)
                } else {
                    triggerValidationInternal(action.pkg)
                }
                action.response.complete(Unit)
            }

            is LifecycleAction.HandlePostUpdate -> {
                registry.updatePlugin(action.pkg) {
                    it.copy(
                        isValidated = false,
                        status = PluginLifecycleStatus.PENDING_SETUP
                    )
                }
                if (action.manifest.hasUpdateHandler) {
                    enqueueUpdateJobInternal(action.pkg)
                } else if (action.manifest.hasSetupHandler) {
                    action.installer.clearFiles(action.pkg)
                    enqueueSetupJobInternal(action.pkg)
                } else {
                    registry.updatePlugin(action.pkg) { it.copy(isSetupCompleted = true) }
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
                    lifecycleManager.refreshLocks(action.pkg)
                }
                action.response.complete(Unit)
            }

            is LifecycleAction.RerunSetup -> {
                Logger.i { "Rerunning setup for plugin: ${action.pkg}" }
                lifecycleManager.unloadPlugin(action.pkg)
                action.installer.clearFiles(action.pkg)
                registry.updatePlugin(action.pkg) {
                    it.copy(
                        isValidated = false,
                        isSetupCompleted = false,
                        status = PluginLifecycleStatus.PENDING_SETUP,
                        loadError = null
                    )
                }
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
                    try {
                        lifecycleManager.unloadPlugin(action.pkg)
                    } catch (t: Throwable) {
                        Logger.e(t) { "Error unloading plugin ${action.pkg} during setEnabled(false)" }
                    }
                }

                registry.updatePlugin(action.pkg) {
                    it.copy(
                        isEnabled = action.enabled,
                        status = if (!action.enabled) {
                            PluginLifecycleStatus.DISABLED
                        } else if (it.isValidated) {
                            PluginLifecycleStatus.VALIDATED
                        } else {
                            PluginLifecycleStatus.PENDING_SETUP
                        }
                    )
                }

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
                            parameters = action.parameters,
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
        val action = LifecycleAction.OnJobCompleted(job, deferred)
        getActor(action.targetPkg).send(action)
        deferred.await()
    }

    suspend fun onLifecycleJobFailed(job: BackgroundJob, error: String?) {
        val deferred = CompletableDeferred<Unit>()
        val action = LifecycleAction.OnJobFailed(job, error, deferred)
        getActor(action.targetPkg).send(action)
        deferred.await()
    }

    suspend fun onManualValidationCompleted(pkg: String, result: Result<Unit>) {
        val deferred = CompletableDeferred<Unit>()
        val action = LifecycleAction.OnManualValidation(pkg, result, deferred)
        getActor(action.targetPkg).send(action)
        deferred.await()
    }

    suspend fun loadPlugin(pkg: String): Result<Unit> {
        val deferred = CompletableDeferred<Result<Unit>>()
        val action = LifecycleAction.LoadPlugin(pkg, deferred)
        getActor(action.targetPkg).send(action)
        return deferred.await()
    }

    suspend fun unloadPlugin(pkg: String) {
        val deferred = CompletableDeferred<Unit>()
        val action = LifecycleAction.UnloadPlugin(pkg, deferred)
        getActor(action.targetPkg).send(action)
        deferred.await()
    }

    suspend fun reloadPlugin(pkg: String) {
        val deferred = CompletableDeferred<Unit>()
        val action = LifecycleAction.ReloadPlugin(pkg, deferred)
        getActor(action.targetPkg).send(action)
        deferred.await()
    }

    suspend fun handlePostInstall(pkg: String, manifest: org.wip.plugintoolkit.api.PluginManifest) {
        val deferred = CompletableDeferred<Unit>()
        val action = LifecycleAction.HandlePostInstall(pkg, manifest, deferred)
        getActor(action.targetPkg).send(action)
        deferred.await()
    }

    suspend fun handlePostUpdate(
        pkg: String,
        manifest: org.wip.plugintoolkit.api.PluginManifest,
        installer: PluginInstaller
    ) {
        val deferred = CompletableDeferred<Unit>()
        val action = LifecycleAction.HandlePostUpdate(pkg, manifest, installer, deferred)
        getActor(action.targetPkg).send(action)
        deferred.await()
    }

    suspend fun enqueueSetupJob(pkg: String) {
        val deferred = CompletableDeferred<Unit>()
        val action = LifecycleAction.EnqueueSetupJob(pkg, deferred)
        getActor(action.targetPkg).send(action)
        deferred.await()
    }

    suspend fun enqueueUpdateJob(pkg: String) {
        val deferred = CompletableDeferred<Unit>()
        val action = LifecycleAction.EnqueueUpdateJob(pkg, deferred)
        getActor(action.targetPkg).send(action)
        deferred.await()
    }

    suspend fun triggerValidation(pkg: String): Result<Unit> {
        val deferred = CompletableDeferred<Result<Unit>>()
        val action = LifecycleAction.TriggerValidation(pkg, deferred)
        getActor(action.targetPkg).send(action)
        return deferred.await()
    }

    suspend fun checkAndResumeSetup(pkg: String) {
        val deferred = CompletableDeferred<Unit>()
        val action = LifecycleAction.CheckAndResumeSetup(pkg, deferred)
        getActor(action.targetPkg).send(action)
        deferred.await()
    }

    suspend fun rerunSetup(pkg: String, installer: PluginInstaller) {
        val deferred = CompletableDeferred<Unit>()
        val action = LifecycleAction.RerunSetup(pkg, installer, deferred)
        getActor(action.targetPkg).send(action)
        deferred.await()
    }

    suspend fun setEnabled(pkg: String, enabled: Boolean): Result<Unit> {
        val deferred = CompletableDeferred<Result<Unit>>()
        val action = LifecycleAction.SetEnabled(pkg, enabled, deferred)
        getActor(action.targetPkg).send(action)
        return deferred.await()
    }

    suspend fun validatePlugin(pkg: String): Result<Unit> {
        val deferred = CompletableDeferred<Result<Unit>>()
        val action = LifecycleAction.ValidatePlugin(pkg, deferred)
        getActor(action.targetPkg).send(action)
        return deferred.await()
    }

    suspend fun runAction(
        pkg: String,
        action: PluginAction,
        parameters: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap()
    ) {
        val deferred = CompletableDeferred<Unit>()
        val actionObj = LifecycleAction.RunAction(pkg, action, parameters, deferred)
        getActor(actionObj.targetPkg).send(actionObj)
        deferred.await()
    }

    // --- Internal Helpers (Actor Thread Only) ---

    private suspend fun enqueueSetupJobInternal(pkg: String) {
        val plugin = registry.getPlugin(pkg) ?: run {
            Logger.w { "LifecycleCoordinator: Cannot enqueue setup for $pkg: plugin not found in registry" }
            return
        }
        if (isJobPendingOrRunning("setup_$pkg")) {
            Logger.d { "LifecycleCoordinator: Setup job setup_$pkg is already pending or running, skipping." }
            return
        }

        val manifest = lifecycleManager.getManifest(pkg)
        if (hasMissingRequiredSettings(pkg, manifest)) {
            Logger.i { "LifecycleCoordinator: Plugin $pkg has missing required settings; requesting CONFIGURE_SETTINGS" }
            registry.updatePlugin(pkg) { it.copy(requiredAction = "CONFIGURE_SETTINGS") }
            return
        }

        val loadResult = lifecycleManager.loadPlugin(pkg)
        if (loadResult.isFailure) {
            val error = loadResult.exceptionOrNull()?.message
            Logger.e { "LifecycleCoordinator: Failed to load plugin $pkg before setup: $error" }
            markAsInvalidated(pkg, error)
            return
        }

        if (manifest?.hasSetupHandler != true) {
            Logger.d { "LifecycleCoordinator: Plugin $pkg has no setup handler, proceeding to validation" }
            registry.updatePlugin(pkg) { it.copy(isSetupCompleted = true) }
            triggerValidationInternal(pkg)
            return
        }

        Logger.i { "LifecycleCoordinator: Enqueuing setup job setup_$pkg for $pkg" }
        registry.updatePlugin(pkg) { it.copy(status = PluginLifecycleStatus.SETTING_UP) }
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
        val plugin = registry.getPlugin(pkg) ?: run {
            Logger.w { "LifecycleCoordinator: Cannot enqueue update for $pkg: plugin not found in registry" }
            return
        }
        if (isJobPendingOrRunning("update_$pkg")) {
            Logger.d { "LifecycleCoordinator: Update job update_$pkg is already pending or running, skipping." }
            return
        }

        val manifest = lifecycleManager.getManifest(pkg)
        if (hasMissingRequiredSettings(pkg, manifest)) {
            Logger.i { "LifecycleCoordinator: Plugin $pkg has missing required settings; requesting CONFIGURE_SETTINGS" }
            registry.updatePlugin(pkg) { it.copy(requiredAction = "CONFIGURE_SETTINGS") }
            return
        }

        val loadResult = lifecycleManager.loadPlugin(pkg)
        if (loadResult.isFailure) {
            val error = loadResult.exceptionOrNull()?.message
            Logger.e { "LifecycleCoordinator: Failed to load plugin $pkg before update: $error" }
            markAsInvalidated(pkg, error)
            return
        }

        if (manifest?.hasUpdateHandler != true) {
            Logger.d { "LifecycleCoordinator: Plugin $pkg has no update handler, proceeding to validation" }
            triggerValidationInternal(pkg)
            return
        }

        Logger.i { "LifecycleCoordinator: Enqueuing update job update_$pkg for $pkg" }
        registry.updatePlugin(pkg) { it.copy(status = PluginLifecycleStatus.SETTING_UP) }
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
        val plugin = registry.getPlugin(pkg) ?: return Result.failure(Exception("Plugin not found: $pkg"))
        if (plugin.isValidated) {
            Logger.d { "LifecycleCoordinator: Plugin $pkg is already validated, skipping validation." }
            return Result.success(Unit)
        }
        if (isJobPendingOrRunning("val_$pkg")) {
            Logger.d { "LifecycleCoordinator: Validation job val_$pkg is already pending or running, skipping." }
            return Result.success(Unit)
        }

        val manifest = lifecycleManager.getManifest(pkg)
        if (hasMissingRequiredSettings(pkg, manifest)) {
            Logger.i { "LifecycleCoordinator: Plugin $pkg has missing required settings, cannot validate; requesting CONFIGURE_SETTINGS" }
            registry.updatePlugin(pkg) { it.copy(requiredAction = "CONFIGURE_SETTINGS") }
            return Result.failure(Exception("Missing required settings for $pkg"))
        }

        val loadResult = lifecycleManager.loadPlugin(pkg)
        if (loadResult.isFailure) {
            val error = loadResult.exceptionOrNull()?.message
            Logger.e { "LifecycleCoordinator: Failed to load plugin $pkg before validation: $error" }
            markAsInvalidated(pkg, error)
            return Result.failure(loadResult.exceptionOrNull()!!)
        }

        Logger.i { "LifecycleCoordinator: Enqueuing validation job val_$pkg for $pkg" }
        registry.updatePlugin(pkg) { it.copy(status = PluginLifecycleStatus.VALIDATING) }
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
        return jobManager.isJobPendingOrRunning(jobId)
    }

    private suspend fun markAsValidated(pkg: String) {
        val plugin = registry.getPlugin(pkg) ?: return
        Logger.i { "LifecycleCoordinator: Marking plugin $pkg as validated and loading plugin" }
        registry.updatePlugin(pkg) {
            it.copy(
                isValidated = true,
                isSetupCompleted = true,
                status = PluginLifecycleStatus.VALIDATED,
                loadError = null
            )
        }
        val loadResult = lifecycleManager.loadPlugin(pkg)
        if (loadResult.isFailure) {
            Logger.e { "LifecycleCoordinator: Failed to activate validated plugin $pkg: ${loadResult.exceptionOrNull()?.message}" }
        }
    }

    private suspend fun markAsInvalidated(pkg: String, error: String? = null) {
        val plugin = registry.getPlugin(pkg) ?: return
        Logger.w { "LifecycleCoordinator: Marking plugin $pkg as invalidated (error=$error)" }
        registry.updatePlugin(pkg) {
            it.copy(
                isValidated = false,
                status = PluginLifecycleStatus.VALIDATION_FAILED,
                loadError = error
            )
        }
        if (lifecycleManager.loadedPlugins.value.contains(pkg) || PluginLoader.getPluginById(pkg) != null) {
            Logger.i { "LifecycleCoordinator: Unloading invalidated plugin $pkg" }
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
