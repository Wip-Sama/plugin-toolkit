package com.wip.plugin.api

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
    suspend fun initialize(context: ExecutionContext): Result<Unit> = Result.success(Unit)

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
    suspend fun performSetup(context: ExecutionContext): Result<Unit> = Result.success(Unit)

    /**
     * Validate the plugin installation and status.
     * @param context The execution context providing logger, progress, and file system access.
     */
    suspend fun validate(context: ExecutionContext): Result<Unit> = Result.success(Unit)

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
 * Exception thrown by a plugin to indicate it has paused and saved its state.
 */
class PluginPausedException(val resumeState: JsonElement) : Exception("Plugin paused")

/**
 * Handle to control a running plugin task.
 *
 * Allows for checking the result, pausing, or cancelling the task.
 */
interface JobHandle {
    /**
     * The deferred result of the task.
     */
    val result: Deferred<Result<PluginResponse>>

    /**
     * Request the task to pause. The task must handle this by saving its state
     * and throwing a [PluginPausedException].
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
 * and isolated file system access. It also handles lifecycle signals like PAUSE and CANCEL.
 *
 * @property logger The logger to use for reporting status and errors.
 * @property progress The reporter to use for updating task progress.
 * @property fileSystem Access to the plugin's persistent data storage.
 * @property cacheFileSystem Access to the plugin's temporary/cache storage.
 */
class ExecutionContext(
    val logger: PluginLogger,
    val progress: ProgressReporter,
    val fileSystem: PluginFileSystem,
    val cacheFileSystem: PluginFileSystem
) {
    private val signalHandlers = mutableListOf<suspend (PluginSignal) -> Unit>()

    /**
     * Internal method for the Host to send signals to the plugin.
     */
    suspend fun sendSignal(signal: PluginSignal) {
        signalHandlers.forEach { it(signal) }
    }

    /**
     * Register a block to handle lifecycle signals (Pause, Resume, Cancel).
     */
    fun onSignal(handler: suspend (PluginSignal) -> Unit) {
        signalHandlers.add(handler)
    }

    /**
     * Terminate the current execution and report a resume state.
     * This should be called by the plugin when it handles a PAUSE signal.
     */
    fun pause(resumeState: JsonElement): Nothing {
        throw PluginPausedException(resumeState)
    }
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
    suspend fun process(request: PluginRequest): Result<PluginResponse>

    /**
     * Set the debug mode for the processor.
     */
    fun setDebug(isDebug: Boolean) {}

    /**
     * Set the execution context for the processor.
     */
    fun setExecutionContext(context: ExecutionContext) {}

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
}
