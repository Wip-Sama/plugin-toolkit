package org.wip.plugintoolkit.api

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.JsonElement
import org.koin.core.module.Module

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
     * Get the plugin manifest information.
     */
    fun getManifest(): Result<PluginManifest>

    /**
     * Provide the worker instance.
     */
    fun getProcessor(): Result<DataProcessor>


    /**
     * Set the debug mode for the plugin.
     * @param isDebug True if the application is running in debug mode.
     */
    fun setDebug(isDebug: Boolean)

    /**
     * Optional load step called by the host application after initialization.
     * @param context The execution context providing logger, progress, and file system access.
     */
    suspend fun performLoad(context: PluginContext): Result<Unit> = Result.success(Unit)

    /**
     * Optional setup step called by the host application.
     * @param context The execution context providing logger, progress, and file system access.
     */
    suspend fun performSetup(context: PluginContext): Result<Unit> = Result.success(Unit)

    /**
     * Optional update step called by the host application during plugin update.
     * 
     * NOTE: This function must be implemented in a **version-agnostic** way. Users may skip versions
     * during an upgrade (e.g., from 1.0.0 directly to 1.3.0). This function is meant for updating internal
     * plugin state, such as files or local databases, and should query the current state defensively rather
     * than assuming a specific previous version. Flow node config migrations are handled separately
     * by the Host Application using `migrations.json`.
     *
     * @param context The execution context providing logger, progress, and file system access.
     */
    suspend fun performUpdate(context: PluginContext): Result<Unit> = Result.success(Unit)

    /**
     * Validate the plugin installation and status.
     * @param context The execution context providing logger, progress, and file system access.
     */
    suspend fun validate(context: PluginContext): Result<Unit> = Result.success(Unit)

    /**
     * Clean up resources.
     */
    fun shutdown() {}
}

/**
 * Interface used by ServiceLoader to discover the plugin's Koin module.
 */
interface PluginModuleProvider {
    /**
     * Provide the plugin's Koin module, pre-configured with the given settings.
     * @param settings The current plugin settings.
     */
    fun getKoinModule(settings: Map<String, JsonElement>): Module
}

/**
 * Base interface for isolated file systems, restricting operations to a specific managed folder.
 *
 * All paths provided to these methods are relative to the base directory.
 * This ensures that operations cannot interfere with files outside the managed scope.
 * 
 * **SECURITY WARNING:** Implementations of this interface must rigidly validate all relative paths
 * to prevent Path Traversal attacks (e.g., verifying paths do not contain "../" or start with "/").
 */
interface ScopedFileSystem {
    suspend fun readFile(relativePath: RelativePath): ByteArray?
    suspend fun readTextFile(relativePath: RelativePath): String?
    suspend fun writeFile(relativePath: RelativePath, data: ByteArray): Result<Unit>
    suspend fun writeTextFile(relativePath: RelativePath, text: String): Result<Unit>
    suspend fun exists(relativePath: RelativePath): Boolean
    suspend fun listFiles(relativePath: RelativePath = RelativePath.ROOT): List<String>
    suspend fun deleteFile(relativePath: RelativePath): Result<Unit>

    /**
     * Get the absolute base path of the managed file area.
     */
    fun getBasePath(): String

    /**
     * Read file content as a stream of byte chunks.
     */
    suspend fun readStream(relativePath: RelativePath): Flow<ByteArray> = flow {
        val bytes = readFile(relativePath) ?: return@flow
        emit(bytes)
    }

    /**
     * Write a stream of byte chunks to a file.
     */
    suspend fun writeStream(relativePath: RelativePath, stream: Flow<ByteArray>): Result<Unit> = try {
        val byteArrayOutputStream = mutableListOf<Byte>()
        stream.collect { chunk ->
            chunk.forEach { byteArrayOutputStream.add(it) }
        }
        writeFile(relativePath, byteArrayOutputStream.toByteArray())
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Copy a file from source relative path to destination relative path.
     */
    suspend fun copyFile(source: RelativePath, destination: RelativePath): Result<Unit> = try {
        val content = readFile(source) ?: return Result.failure(IllegalArgumentException("Source file does not exist"))
        writeFile(destination, content)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Move a file from source relative path to destination relative path.
     */
    suspend fun moveFile(source: RelativePath, destination: RelativePath): Result<Unit> = try {
        val copyResult = copyFile(source, destination)
        if (copyResult.isFailure) {
            copyResult
        } else {
            deleteFile(source)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
}

/**
 * File system interface for persistent plugin storage (e.g. settings, downloaded models).
 */
interface PluginFileSystem : ScopedFileSystem {
    /**
     * Extract a resource bundled inside the plugin JAR to the plugin's managed file area.
     * @param resourcePath Path to the resource inside the JAR (e.g. "scripts/install.bat").
     * @param targetRelativePath Relative path within the plugin's managed folder to write to.
     */
    suspend fun extractResource(resourcePath: String, targetRelativePath: RelativePath): Result<Unit>
}

/**
 * Interface for key-value plugin data storage.
 * Allows plugins to persist internal data without exposing it as user-configurable settings.
 */
interface PluginStorage {
    suspend fun get(key: String): JsonElement?
    suspend fun put(key: String, value: JsonElement)
    suspend fun getAll(): Map<String, JsonElement>
    suspend fun remove(key: String)
}

/**
 * File system interface for temporary execution storage (e.g. job sandboxes).
 */
interface ExecutionFileSystem : ScopedFileSystem

/**
 * File system interface for interacting with the host machine's external file system.
 * 
 * Operations are strictly limited by the File Access rules declared in the plugin's capabilities.
 * If a capability attempts to perform unauthorized operations, a SecurityException will be thrown.
 */
interface HostFileSystem {
    suspend fun readFile(absolutePath: String): ByteArray?
    suspend fun readTextFile(absolutePath: String): String?
    suspend fun writeFile(absolutePath: String, data: ByteArray): Result<Unit>
    suspend fun writeTextFile(absolutePath: String, text: String): Result<Unit>
    suspend fun exists(absolutePath: String): Boolean
    suspend fun listFiles(absolutePath: String): List<String>
    suspend fun deleteFile(absolutePath: String): Result<Unit>
    suspend fun createDirectory(absolutePath: String): Result<Unit>

    /**
     * Read file content as a stream of byte chunks.
     */
    suspend fun readStream(absolutePath: String): Flow<ByteArray> = flow {
        val bytes = readFile(absolutePath) ?: return@flow
        emit(bytes)
    }

    /**
     * Write a stream of byte chunks to a file.
     */
    suspend fun writeStream(absolutePath: String, stream: Flow<ByteArray>): Result<Unit> = try {
        val byteArrayOutputStream = mutableListOf<Byte>()
        stream.collect { chunk ->
            chunk.forEach { byteArrayOutputStream.add(it) }
        }
        writeFile(absolutePath, byteArrayOutputStream.toByteArray())
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Copy a file from source absolute path to destination absolute path.
     */
    suspend fun copyFile(sourceAbsolutePath: String, destinationAbsolutePath: String): Result<Unit> = try {
        val content = readFile(sourceAbsolutePath) ?: return Result.failure(IllegalArgumentException("Source file does not exist"))
        writeFile(destinationAbsolutePath, content)
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Move a file from source absolute path to destination absolute path.
     */
    suspend fun moveFile(sourceAbsolutePath: String, destinationAbsolutePath: String): Result<Unit> = try {
        val copyResult = copyFile(sourceAbsolutePath, destinationAbsolutePath)
        if (copyResult.isFailure) {
            copyResult
        } else {
            deleteFile(sourceAbsolutePath)
        }
    } catch (e: Exception) {
        Result.failure(e)
    }
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
interface PluginContext {
    val logger: PluginLogger
    val progress: ProgressReporter
    val fileSystem: PluginFileSystem
    val cacheFileSystem: PluginFileSystem
    val executionFileSystem: ExecutionFileSystem
    val hostFileSystem: HostFileSystem
    val settings: Map<String, JsonElement>
    val storage: PluginStorage
    val signals: PluginSignalManager

    /**
     * Inform the host that a user action is required for the plugin to function correctly.
     * @param actionName The function name of the action to be displayed, or null to clear.
     */
    fun setRequiredAction(actionName: String?)

    /**
     * Display a toast notification in the host application UI.
     * @param message The message to display to the user.
     */
    fun showToast(message: String) {}

    /**
     * Helper to display a toast notification in the host application UI.
     * @param message The message to display to the user.
     */
    fun toast(message: String) = showToast(message)

    /**
     * Typed helpers for settings access.
     */
    fun getStringSetting(key: String, defaultValue: String = ""): String {
        return try {
            settings[key]?.let {
                if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString()
            } ?: defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }

    fun getIntSetting(key: String, defaultValue: Int = 0): Int {
        return try {
            settings[key]?.let {
                if (it is kotlinx.serialization.json.JsonPrimitive) it.content.toIntOrNull() else null
            } ?: defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }

    fun getBooleanSetting(key: String, defaultValue: Boolean = false): Boolean {
        return try {
            settings[key]?.let {
                if (it is kotlinx.serialization.json.JsonPrimitive) {
                    it.content.toBooleanStrictOrNull()
                } else null
            } ?: defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }

    fun getDoubleSetting(key: String, defaultValue: Double = 0.0): Double {
        return try {
            settings[key]?.let {
                if (it is kotlinx.serialization.json.JsonPrimitive) it.content.toDoubleOrNull() else null
            } ?: defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }

    /**
     * Register a block to handle lifecycle signals (Pause, Cancel).
     */
    fun onSignal(handler: suspend (PluginSignal) -> Unit) = signals.onSignal(handler)

    /**
     * Update or set a specific setting value for this plugin.
     * Persists the change to disk and updates in-memory settings.
     *
     * @param key The setting key.
     * @param value The setting value as a [JsonElement].
     */
    suspend fun updateSetting(key: String, value: JsonElement) {}

    /**
     * Update multiple settings for this plugin simultaneously.
     * Persists the changes to disk and updates in-memory settings.
     *
     * @param newSettings A map of setting keys to their new [JsonElement] values.
     */
    suspend fun updateSettings(newSettings: Map<String, JsonElement>) {}

    suspend fun updateSetting(key: String, value: String) {
        updateSetting(key, kotlinx.serialization.json.JsonPrimitive(value))
    }

    suspend fun updateSetting(key: String, value: Boolean) {
        updateSetting(key, kotlinx.serialization.json.JsonPrimitive(value))
    }

    suspend fun updateSetting(key: String, value: Number) {
        updateSetting(key, kotlinx.serialization.json.JsonPrimitive(value))
    }
}

/**
 * The actual processor performing the business logic.
 *
 * This interface is typically implemented by the class annotated with `@PluginInfo`.
 * It handles the execution of capabilities.
 * 
 * Execution is inherently asynchronous. The host application will wrap the `process`
 * invocation in a managed coroutine to provide cancellation and handle generation.
 */
interface DataProcessor {
    /**
     * Process data natively without serialization.
     * @param request Native request object.
     * @param context The execution context.
     * @return Result wrapping native response.
     */
    suspend fun process(request: PluginRequest, context: PluginContext): ExecutionResult

    /**
     * Set the debug mode for the processor.
     */
    fun setDebug(isDebug: Boolean) {}

    /**
     * Observe processing progress (0.0 to 1.0).
     */
    fun observeProgress(): kotlinx.coroutines.flow.StateFlow<Float>? = null

    /**
     * Run a custom action.
     * @param action The metadata of the action to call.
     * @param parameters The map of parameters provided for this action execution.
     * @param context The execution context.
     */
    suspend fun runAction(
        action: PluginAction,
        parameters: Map<String, JsonElement> = emptyMap(),
        context: PluginContext
    ): Result<Unit> {
        return Result.failure(NotImplementedError("runAction not implemented"))
    }

    /**
     * Evaluate custom lock/unlock conditions defined by the plugin.
     * @param context The execution context.
     * @return Map of lock keys to boolean state (true = unlocked/satisfied, false = locked).
     */
    suspend fun refreshLocks(context: PluginContext): Map<String, Boolean> {
        return emptyMap()
    }
}
