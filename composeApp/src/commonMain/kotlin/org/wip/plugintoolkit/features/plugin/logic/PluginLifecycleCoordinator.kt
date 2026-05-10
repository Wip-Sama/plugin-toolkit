package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobType
import kotlin.time.Clock

/**
 * Coordinates the lifecycle state transitions for plugins.
 * Ensures plugins move through Setup -> Update -> Validation -> Loaded sequentially
 * without relying on global reactive job observation.
 */
class PluginLifecycleCoordinator(
    private val registry: PluginRegistry,
    private val jobManager: JobManager,
    private val lifecycleManager: PluginLifecycleManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Called by JobWorker when a lifecycle job completes successfully.
     */
    fun onLifecycleJobCompleted(job: BackgroundJob) {
        scope.launch {
            when (job.type) {
                JobType.Validation -> {
                    markAsValidated(job.pluginId)
                }
                JobType.Setup, JobType.Update -> {
                    triggerValidation(job.pluginId)
                }
                JobType.PluginAction -> {
                    clearRequiredAction(job.pluginId)
                }
                else -> {
                    // Not a lifecycle job we manage state for directly here
                }
            }
        }
    }

    /**
     * Called by JobWorker when a lifecycle job fails.
     */
    fun onLifecycleJobFailed(job: BackgroundJob, error: String?) {
        scope.launch {
            if (job.type == JobType.Validation || job.type == JobType.Setup || job.type == JobType.Update) {
                markAsInvalidated(job.pluginId)
            }
        }
    }

    // --- State Transitions ---

    suspend fun handlePostInstall(pkg: String, manifest: org.wip.plugintoolkit.api.PluginManifest) {
        if (manifest.hasSetupHandler) {
            enqueueSetupJob(pkg)
        } else {
            triggerValidation(pkg)
        }
    }

    suspend fun handlePostUpdate(pkg: String, manifest: org.wip.plugintoolkit.api.PluginManifest, installer: PluginInstaller) {
        if (manifest.hasUpdateHandler) {
            enqueueUpdateJob(pkg)
        } else if (manifest.hasSetupHandler) {
            installer.clearFiles(pkg)
            enqueueSetupJob(pkg)
        } else {
            triggerValidation(pkg)
        }
    }

    suspend fun enqueueSetupJob(pkg: String) {
        val plugin = registry.getPlugin(pkg) ?: return
        val loadResult = lifecycleManager.loadPlugin(pkg)
        if (loadResult.isFailure) {
            Logger.e { "Failed to load plugin $pkg for setup: ${loadResult.exceptionOrNull()?.message}" }
            return
        }

        val manifest = PluginLoader.getPluginById(pkg)?.getManifest()
        if (manifest?.hasSetupHandler != true) {
            Logger.i { "Plugin $pkg has no setup handler, skipping setup job and triggering validation." }
            triggerValidation(pkg)
            return
        }

        if (hasMissingRequiredSettings(pkg)) {
            Logger.w { "Plugin $pkg has missing required settings, blocking setup." }
            registry.updatePlugin(pkg) { it.copy(requiredAction = "CONFIGURE_SETTINGS") }
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

    suspend fun enqueueUpdateJob(pkg: String) {
        val plugin = registry.getPlugin(pkg) ?: return
        val loadResult = lifecycleManager.loadPlugin(pkg)
        if (loadResult.isFailure) {
            Logger.e { "Failed to load plugin $pkg for update: ${loadResult.exceptionOrNull()?.message}" }
            return
        }

        val manifest = PluginLoader.getPluginById(pkg)?.getManifest()
        if (manifest?.hasUpdateHandler != true) {
            Logger.i { "Plugin $pkg has no update handler, skipping update job and triggering validation." }
            triggerValidation(pkg)
            return
        }

        if (hasMissingRequiredSettings(pkg)) {
            Logger.w { "Plugin $pkg has missing required settings, blocking update." }
            registry.updatePlugin(pkg) { it.copy(requiredAction = "CONFIGURE_SETTINGS") }
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

    suspend fun triggerValidation(pkg: String): Result<Unit> {
        val plugin = registry.getPlugin(pkg) ?: return Result.failure(Exception("Plugin not found"))

        if (hasMissingRequiredSettings(pkg)) {
            Logger.w { "Plugin $pkg has missing required settings, blocking validation." }
            registry.updatePlugin(pkg) { it.copy(requiredAction = "CONFIGURE_SETTINGS") }
            return Result.failure(Exception("Missing required settings"))
        }

        val loadResult = lifecycleManager.loadPlugin(pkg)
        if (loadResult.isFailure) return Result.failure(loadResult.exceptionOrNull()!!)

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

    suspend fun checkAndResumeSetup(pkg: String) {
        val plugin = registry.getPlugin(pkg) ?: return
        if (plugin.requiredAction == "CONFIGURE_SETTINGS" && !hasMissingRequiredSettings(pkg)) {
            Logger.i { "Required settings provided for $pkg. Resuming setup/update." }
            clearRequiredAction(pkg)
            val manifest = PluginLoader.getPluginById(pkg)?.getManifest()
            if (manifest?.hasUpdateHandler == true) {
                enqueueUpdateJob(pkg)
            } else if (manifest?.hasSetupHandler == true) {
                enqueueSetupJob(pkg)
            } else {
                triggerValidation(pkg)
            }
        }
    }
    
    suspend fun rerunSetup(pkg: String, installer: PluginInstaller) {
        Logger.i { "Rerunning setup for plugin: $pkg" }
        // 1. Unload
        lifecycleManager.unloadPlugin(pkg)
        // 2. Clear files
        installer.clearFiles(pkg)
        // 3. Mark as not validated and clear errors
        registry.updatePlugin(pkg) { it.copy(isValidated = false, loadError = null) }
        // 4. Enqueue setup job
        enqueueSetupJob(pkg)
    }


    // --- Internal Helpers ---

    private suspend fun markAsValidated(pkg: String) {
        val plugin = registry.getPlugin(pkg) ?: return
        if (plugin.isValidated) return

        Logger.i { "Marking plugin $pkg as validated and activating" }
        registry.updatePlugin(pkg) { it.copy(isValidated = true) }
        lifecycleManager.loadPlugin(pkg)
    }

    private suspend fun markAsInvalidated(pkg: String) {
        val plugin = registry.getPlugin(pkg) ?: return
        if (!plugin.isValidated) return

        Logger.i { "Marking plugin $pkg as invalidated" }
        registry.updatePlugin(pkg) { it.copy(isValidated = false) }
    }

    private suspend fun clearRequiredAction(pkg: String) {
        registry.updatePlugin(pkg) { it.copy(requiredAction = null) }
    }

    private fun hasMissingRequiredSettings(pkg: String): Boolean {
        val manifest = PluginLoader.getPluginById(pkg)?.getManifest() ?: return false
        if (manifest.settings.isNullOrEmpty()) return false

        val store = lifecycleManager.loadPluginSettings(pkg)
        val missing = manifest.settings!!.any { (key, meta) ->
            if (!meta.required) return@any false

            val value = store.settings[key] ?: return@any true

            // For string primitives, check if they are blank
            val type = meta.type
            if (type is DataType.Primitive &&
                type.primitiveType == PrimitiveType.STRING) {
                val strValue = (value as? kotlinx.serialization.json.JsonPrimitive)?.content ?: ""
                return@any strValue.isBlank()
            }

            false
        }
        return missing
    }
}
