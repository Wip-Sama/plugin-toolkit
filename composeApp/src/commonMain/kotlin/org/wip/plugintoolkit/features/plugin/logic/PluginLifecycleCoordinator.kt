package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.wip.plugintoolkit.api.PluginAction
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.job.model.JobStatus
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock

/**
 * Coordinates the lifecycle state transitions for plugins.
 * Ensures plugins move through Setup -> Update -> Validation -> Loaded sequentially
 * without relying on global reactive job observation.
 */
class PluginLifecycleCoordinator(
    private val registry: PluginRegistry,
    private val jobManager: JobManager,
    private val lifecycleManager: PluginLifecycleManager,
    /** Injected [AppScope] for non-blocking logic and state transitions. */
    private val scope: CoroutineScope
) {

    private val pluginMutexes = ConcurrentHashMap<String, Mutex>()
    private fun getMutex(pkg: String) = pluginMutexes.getOrPut(pkg) { Mutex() }

    private suspend inline fun <T> withPluginLock(pkg: String, crossinline block: suspend () -> T): T {
        return getMutex(pkg).withLock { block() }
    }

    /**
     * Called by JobWorker when a lifecycle job completes successfully.
     */
    suspend fun onLifecycleJobCompleted(job: BackgroundJob) = withPluginLock(job.pluginId) {
        Logger.d { "Lifecycle job completed for ${job.pluginId}: ${job.type}" }
        when (job.type) {
            JobType.Validation -> {
                markAsValidated(job.pluginId)
            }
            JobType.Setup, JobType.Update -> {
                triggerValidationInternal(job.pluginId)
            }
            JobType.PluginAction -> {
                clearRequiredAction(job.pluginId)
            }
            else -> {
                // Not a lifecycle job we manage state for directly here
            }
        }
    }

    /**
     * Called by JobWorker when a lifecycle job fails.
     */
    suspend fun onLifecycleJobFailed(job: BackgroundJob, error: String?) = withPluginLock(job.pluginId) {
        Logger.w { "Lifecycle job failed for ${job.pluginId}: ${job.type} - $error" }
        if (job.type == JobType.Validation || job.type == JobType.Setup || job.type == JobType.Update) {
            markAsInvalidated(job.pluginId)
        }
    }

    /**
     * Called by PluginManager when a manual (synchronous) validation completes.
     */
    suspend fun onManualValidationCompleted(pkg: String, result: Result<Unit>) = withPluginLock(pkg) {
        if (result.isSuccess) {
            markAsValidated(pkg)
        } else {
            markAsInvalidated(pkg)
        }
    }

    // --- State Transitions ---

    suspend fun handlePostInstall(pkg: String, manifest: org.wip.plugintoolkit.api.PluginManifest) = withPluginLock(pkg) {
        if (manifest.hasSetupHandler) {
            enqueueSetupJobInternal(pkg)
        } else {
            triggerValidationInternal(pkg)
        }
    }

    suspend fun handlePostUpdate(pkg: String, manifest: org.wip.plugintoolkit.api.PluginManifest, installer: PluginInstaller) = withPluginLock(pkg) {
        if (manifest.hasUpdateHandler) {
            enqueueUpdateJobInternal(pkg)
        } else if (manifest.hasSetupHandler) {
            installer.clearFiles(pkg)
            enqueueSetupJobInternal(pkg)
        } else {
            triggerValidationInternal(pkg)
        }
    }

    suspend fun enqueueSetupJob(pkg: String) = withPluginLock(pkg) {
        enqueueSetupJobInternal(pkg)
    }

    private suspend fun enqueueSetupJobInternal(pkg: String) {
        val plugin = registry.getPlugin(pkg) ?: return
        
        // Prevent redundant setup jobs
        if (isJobPendingOrRunning("setup_$pkg")) {
            Logger.d { "Setup job for $pkg is already pending or running, skipping enqueue." }
            return
        }

        val loadResult = lifecycleManager.loadPlugin(pkg)
        if (loadResult.isFailure) {
            Logger.e { "Failed to load plugin $pkg for setup: ${loadResult.exceptionOrNull()?.message}" }
            return
        }

        val manifest = PluginLoader.getPluginById(pkg)?.getManifest()
        if (manifest?.hasSetupHandler != true) {
            Logger.i { "Plugin $pkg has no setup handler, skipping setup job and triggering validation." }
            triggerValidationInternal(pkg)
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

    suspend fun enqueueUpdateJob(pkg: String) = withPluginLock(pkg) {
        enqueueUpdateJobInternal(pkg)
    }

    private suspend fun enqueueUpdateJobInternal(pkg: String) {
        val plugin = registry.getPlugin(pkg) ?: return

        // Prevent redundant update jobs
        if (isJobPendingOrRunning("update_$pkg")) {
            Logger.d { "Update job for $pkg is already pending or running, skipping enqueue." }
            return
        }

        val loadResult = lifecycleManager.loadPlugin(pkg)
        if (loadResult.isFailure) {
            Logger.e { "Failed to load plugin $pkg for update: ${loadResult.exceptionOrNull()?.message}" }
            return
        }

        val manifest = PluginLoader.getPluginById(pkg)?.getManifest()
        if (manifest?.hasUpdateHandler != true) {
            Logger.i { "Plugin $pkg has no update handler, skipping update job and triggering validation." }
            triggerValidationInternal(pkg)
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

    suspend fun triggerValidation(pkg: String): Result<Unit> = withPluginLock(pkg) {
        triggerValidationInternal(pkg)
    }

    private suspend fun triggerValidationInternal(pkg: String): Result<Unit> {
        val plugin = registry.getPlugin(pkg) ?: return Result.failure(Exception("Plugin not found"))

        if (plugin.isValidated) {
            Logger.d { "Plugin $pkg is already validated, skipping trigger." }
            return Result.success(Unit)
        }

        // Prevent redundant validation jobs
        if (isJobPendingOrRunning("val_$pkg")) {
            Logger.d { "Validation job for $pkg is already pending or running." }
            return Result.success(Unit)
        }

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

    suspend fun checkAndResumeSetup(pkg: String) = withPluginLock(pkg) {
        val plugin = registry.getPlugin(pkg) ?: return@withPluginLock
        if (plugin.requiredAction == "CONFIGURE_SETTINGS" && !hasMissingRequiredSettings(pkg)) {
            Logger.i { "Required settings provided for $pkg. Resuming setup/update." }
            clearRequiredAction(pkg)
            val manifest = PluginLoader.getPluginById(pkg)?.getManifest()
            if (manifest?.hasUpdateHandler == true) {
                enqueueUpdateJobInternal(pkg)
            } else if (manifest?.hasSetupHandler == true) {
                enqueueSetupJobInternal(pkg)
            } else {
                triggerValidationInternal(pkg)
            }
        }
    }
    
    suspend fun rerunSetup(pkg: String, installer: PluginInstaller) = withPluginLock(pkg) {
        Logger.i { "Rerunning setup for plugin: $pkg" }
        // 1. Unload
        lifecycleManager.unloadPlugin(pkg)
        // 2. Clear files
        installer.clearFiles(pkg)
        // 3. Mark as not validated and clear errors
        registry.updatePlugin(pkg) { it.copy(isValidated = false, loadError = null) }
        // 4. Enqueue setup job
        enqueueSetupJobInternal(pkg)
    }

    suspend fun setEnabled(pkg: String, enabled: Boolean): Result<Unit> = withPluginLock(pkg) {
        Logger.i { "Setting plugin $pkg enabled: $enabled" }

        if (!enabled) {
            lifecycleManager.ensureSafeToUnload(listOf(pkg)).onFailure { return@withPluginLock Result.failure(it) }
            lifecycleManager.unloadPlugin(pkg)
        }

        registry.updatePlugin(pkg) { it.copy(isEnabled = enabled) }

        if (enabled) {
            val plugin = registry.getPlugin(pkg)
            if (plugin != null) {
                if (plugin.isValidated) {
                    lifecycleManager.loadPlugin(pkg)
                } else {
                    // Try to get manifest to check for setup handler
                    val manifest = PluginLoader.getPluginById(pkg)?.getManifest()
                    if (manifest?.hasSetupHandler == true) {
                        enqueueSetupJobInternal(pkg)
                    } else {
                        triggerValidationInternal(pkg)
                    }
                }
            }
        }
        Result.success(Unit)
    }

    suspend fun validatePlugin(pkg: String): Result<Unit> = withPluginLock(pkg) {
        val plugin = PluginLoader.getPluginById(pkg) ?: return@withPluginLock Result.failure(Exception("Plugin not loaded"))
        val result = plugin.validate(lifecycleManager.createPluginContext(pkg))
        if (result.isSuccess) {
            markAsValidated(pkg)
        } else {
            markAsInvalidated(pkg)
        }
        result
    }

    suspend fun runAction(pkg: String, action: PluginAction) = withPluginLock(pkg) {
        val plugin = registry.getPlugin(pkg) ?: return@withPluginLock
        Logger.i { "Enqueuing custom action: ${action.name} for plugin: $pkg" }

        val loadResult = lifecycleManager.loadPlugin(pkg)
        if (loadResult.isFailure) {
            Logger.e { "Failed to load plugin $pkg for action ${action.name}" }
            return@withPluginLock
        }

        val job = BackgroundJob(
            id = "action_${pkg}_${action.functionName}_${Clock.System.now().toEpochMilliseconds()}",
            name = "Action: ${action.name} (${plugin.name})",
            type = JobType.PluginAction,
            pluginId = pkg,
            capabilityName = action.functionName,
            keepResult = false
        )
        jobManager.enqueueJob(job)
    }

    private fun isJobPendingOrRunning(jobId: String): Boolean {
        return jobManager.jobs.value.any { it.id == jobId && (it.status == JobStatus.Queued || it.status == JobStatus.Running) }
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

            if (!meta.type.isProvided(value)) return@any true

            false
        }
        return missing
    }
}
