package org.wip.plugintoolkit.api

import org.koin.core.module.Module
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.Deferred
import kotlinx.serialization.json.JsonElement

/**
 * The root interface for any plugin.
 *
 * Implementations of this interface are discovered by the host application via ServiceLoader.
 * Usually, you should use the `@PluginInfo` annotation on your main class to have the
 * boilerplate implementation generated for you.
 */
interface PluginEntry {
    /**
     * Initialize the plugin. Runs in a background thread.
     * @param context The execution context providing logger and file system access.
     */
    suspend fun initialize(context: PluginContext): Result<Unit> = Result.success(Unit)

    /**
     * Provide the plugin's Koin module.
     */
    fun getKoinModule(): Module

    /**
     * Provide the worker instance.
     */
    fun getProcessor(): DataProcessor

    /**
     * Get the plugin manifest information.
     */
    fun getManifest(): PluginManifest

    /**
     * Set the debug mode for the plugin.
     * @param isDebug True if the application is running in debug mode.
     */
    fun setDebug(isDebug: Boolean)

    /**
     * Optional setup step called by the host application.
     * @param context The execution context providing logger, progress, and file system access.
     */
    suspend fun performSetup(context: PluginContext): Result<Unit> = Result.success(Unit)

    /**
     * Validate the plugin installation and status.
     * @param context The execution context providing logger, progress, and file system access.
     */
    suspend fun validate(context: PluginContext): Result<Unit> = Result.success(Unit)

    /**
     * Clean up resources.
     */
    fun shutdown()
}

/**
 * File system interface for plugins, restricting operations to the plugin's managed folder.
 *
 * All paths provided to these methods are relative to the plugin's base directory.
 * This ensures that plugins cannot interfere with each other or the host application's files.
 */
interface PluginFileSystem {
    suspend fun readFile(relativePath: String): ByteArray?
    suspend fun readTextFile(relativePath: String): String?
    suspend fun writeFile(relativePath: String, data: ByteArray): Result<Unit>
    suspend fun writeTextFile(relativePath: String, text: String): Result<Unit>
    suspend fun exists(relativePath: String): Boolean
    suspend fun listFiles(relativePath: String = ""): List<String>
    suspend fun deleteFile(relativePath: String): Result<Unit>

    /**
     * Extract a resource bundled inside the plugin JAR to the plugin's managed file area.
     * @param resourcePath Path to the resource inside the JAR (e.g. "scripts/install.bat").
     * @param targetRelativePath Relative path within the plugin's managed folder to write to.
     */
    suspend fun extractResource(resourcePath: String, targetRelativePath: String): Result<Unit>

    /**
     * Get the absolute base path of the plugin's managed file area.
     */
    fun getBasePath(): String
}

/**
 * Logger interface for plugins to send logs to the host application.
 *
 * Logs are routed through the host's logging system, allowing for centralized
 * collection and display in the UI.
 */
interface PluginLogger {
    fun verbose(message: String)
    fun debug(message: String)
    fun info(message: String)
    fun warn(message: String)
    fun error(message: String, throwable: Throwable? = null)
    
    // Compatibility/helper method
    fun log(message: String) = info(message)
}

/**
 * Progress reporter for plugins to push progress updates.
 */
interface ProgressReporter {
    fun report(progress: Float)
}

/**
 * Signals that can be sent to a running plugin capability.
 */
enum class PluginSignal {
    PAUSE, CANCEL
}

/**
 * Manage signals for a running plugin task.
 */
interface PluginSignalManager {
    /**
     * Register a block to handle lifecycle signals (Pause, Cancel).
     */
    fun onSignal(handler: suspend (PluginSignal) -> Unit)

    /**
     * Internal method for the Host to send signals to the plugin.
     */
    suspend fun sendSignal(signal: PluginSignal)
}

/**
 * The result of a plugin capability execution.
 */
sealed class ExecutionResult {
    /**
     * The task completed successfully.
     * @property response The response data.
     */
    data class Success(val response: PluginResponse) : ExecutionResult()

    /**
     * The task has paused and saved its state.
     * @property resumeState The state to be used for resumption.
     */
    data class Paused(val resumeState: JsonElement) : ExecutionResult()

    /**
     * The task failed with an error.
     * @property message A human-readable error message.
     * @property throwable The underlying cause of the failure.
     */
    data class Error(val message: String, val throwable: Throwable? = null) : ExecutionResult()
}

/**
 * Handle to control a running plugin task.
 *
 * Allows for checking the result, pausing, or cancelling the task.
 */
interface JobHandle {
    /**
     * The deferred result of the task.
     */
    val result: Deferred<ExecutionResult>

    /**
     * Request the task to pause. The task must handle this by saving its state
     * and returning [ExecutionResult.Paused].
     */
    fun pause()

    /**
     * Request the task to cancel.
     * @param force If true, the host may terminate the process/thread more aggressively.
     */
    fun cancel(force: Boolean = false)
}

/**
 * Context provided to a plugin during execution.
 *
 * It provides access to infrastructure services like logging, progress reporting,
 * and isolated file system access.
 */
interface PluginContext : PluginLogger, ProgressReporter {
    val logger: PluginLogger
    val progress: ProgressReporter
    val fileSystem: PluginFileSystem
    val cacheFileSystem: PluginFileSystem
    val settings: Map<String, JsonElement>
    val signals: PluginSignalManager

    // Forwarding methods for convenience
    override fun verbose(message: String) = logger.verbose(message)
    override fun debug(message: String) = logger.debug(message)
    override fun info(message: String) = logger.info(message)
    override fun warn(message: String) = logger.warn(message)
    override fun error(message: String, throwable: Throwable?) = logger.error(message, throwable)
    override fun report(progress: Float) = this.progress.report(progress)

    /**
     * Typed helpers for settings access.
     */
    fun getStringSetting(key: String, defaultValue: String = ""): String {
        return settings[key]?.let {
            if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString()
        } ?: defaultValue
    }

    fun getIntSetting(key: String, defaultValue: Int = 0): Int {
        return settings[key]?.let {
            if (it is kotlinx.serialization.json.JsonPrimitive) it.content.toIntOrNull() else null
        } ?: defaultValue
    }

    fun getBooleanSetting(key: String, defaultValue: Boolean = false): Boolean {
        return settings[key]?.let {
            if (it is kotlinx.serialization.json.JsonPrimitive) it.content.toBooleanStrictOrNull() else null
        } ?: defaultValue
    }

    fun getDoubleSetting(key: String, defaultValue: Double = 0.0): Double {
        return settings[key]?.let {
            if (it is kotlinx.serialization.json.JsonPrimitive) it.content.toDoubleOrNull() else null
        } ?: defaultValue
    }

    /**
     * Register a block to handle lifecycle signals (Pause, Cancel).
     */
    fun onSignal(handler: suspend (PluginSignal) -> Unit) = signals.onSignal(handler)
}

/**
 * The actual processor performing the business logic.
 *
 * This interface is typically implemented by the class annotated with `@PluginInfo`.
 * It handles the execution of capabilities.
 */
interface DataProcessor {
    /**
     * Process data natively without serialization.
     * @param request Native request object.
     * @return Result wrapping native response.
     */
    suspend fun process(request: PluginRequest): ExecutionResult

    /**
     * Set the debug mode for the processor.
     */
    fun setDebug(isDebug: Boolean) {}

    /**
     * Set the execution context for the processor.
     */
    fun setPluginContext(context: PluginContext) {}

    /**
     * Observe processing progress (0.0 to 1.0).
     */
    fun observeProgress(): Flow<Float>? = null

    /**
     * Process data asynchronously, returning a handle to control the task.
     */
    fun processAsync(request: PluginRequest): JobHandle {
        throw NotImplementedError("processAsync not implemented")
    }

    /**
     * Run a custom action.
     * @param action The metadata of the action to call.
     * @param context The execution context.
     */
    suspend fun runAction(action: PluginAction, context: PluginContext): Result<Unit> {
        return Result.failure(NotImplementedError("runAction not implemented"))
    }
}
