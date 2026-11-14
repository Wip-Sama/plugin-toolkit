package org.wip.plugintoolkit.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import org.koin.core.module.Module
import kotlin.jvm.JvmOverloads

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
     * Optional interactive UI processor for plugins that provide reactive SDUI capabilities.
     */
    fun getInteractiveProcessor(): Result<org.wip.plugintoolkit.api.sdui.InteractiveUiProcessor>? = null


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
 * Progress reporter for plugins to push primary, secondary, and sub-task progress updates,
 * including percentages, ratios (e.g. 12.3 / 14.5 MB), dynamic messages, and throughput/status info.
 */
interface ProgressReporter {
    /**
     * Report progress fraction from 0.0 to 1.0.
     */
    fun report(progress: Float)

    /**
     * Report progress fraction from 0.0 to 1.0 with a status/detail message (e.g. download speed, remaining data).
     */
    fun report(progress: Float, message: String?) = report(progress)

    /**
     * Report structured progress data (supports percentage, ratio, and indeterminate).
     */
    fun report(data: ProgressData) {
        data.fraction?.let { report(it, data.message) }
    }

    /**
     * Report progress as a ratio (e.g. current = 12.3, total = 14.5, unit = "MB").
     */
    fun report(current: Double, total: Double, unit: String? = null, message: String? = null) {
        report(ProgressData.ratio(current, total, unit, message))
    }

    /**
     * Report secondary progress (0.0 to 1.0), useful when waiting for API rate limits or retry backoffs.
     */
    fun reportSecondary(progress: Float) {}

    /**
     * Report secondary progress with a status/detail message.
     */
    fun reportSecondary(progress: Float, message: String?) = reportSecondary(progress)

    /**
     * Report structured secondary progress data.
     */
    fun reportSecondary(data: ProgressData) {
        data.fraction?.let { reportSecondary(it, data.message) }
    }

    /**
     * Report secondary progress as a ratio.
     */
    fun reportSecondary(current: Double, total: Double, unit: String? = null, message: String? = null) {
        reportSecondary(ProgressData.ratio(current, total, unit, message))
    }

    /**
     * Clear the secondary progress indicator.
     */
    fun clearSecondary() {}

    /**
     * Report progress for a named sub-task / sub-progress bar within the job.
     */
    fun reportSubProgress(name: String, data: ProgressData) {}

    /**
     * Report progress fraction for a named sub-task within the job.
     */
    fun reportSubProgress(name: String, progress: Float, message: String? = null) {
        reportSubProgress(name, ProgressData.percentage(progress, message))
    }

    /**
     * Remove or clear a named sub-task progress bar.
     */
    fun clearSubProgress(name: String) {}
}

/**
 * Encrypted key-value storage scoped to a plugin for sensitive runtime credentials
 * (API tokens, refresh tokens, session cookies, passwords).
 *
 * Values are persisted by the host via platform-backed encryption rather than plaintext JSON.
 */
interface PluginSecretStorage {
    suspend fun getSecret(key: String): String?
    suspend fun putSecret(key: String, value: String)
    suspend fun removeSecret(key: String)
    suspend fun listSecretKeys(): List<String>
    suspend fun clearSecrets() {
        listSecretKeys().forEach { removeSecret(it) }
    }
}

/**
 * Fallback no-op implementation of [PluginSecretStorage].
 */
object NoOpPluginSecretStorage : PluginSecretStorage {
    override suspend fun getSecret(key: String): String? = null
    override suspend fun putSecret(key: String, value: String) {}
    override suspend fun removeSecret(key: String) {}
    override suspend fun listSecretKeys(): List<String> = emptyList()
}

/**
 * Severity levels for host-persistent notifications raised by a plugin via [PluginContext.showNotification].
 */
@Serializable
enum class PluginNotificationType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

/**
 * Supported HTTP methods for [PluginNetworkClient.request].
 */
@Serializable
enum class HttpMethod {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
    HEAD
}

/**
 * Single part inside a `multipart/form-data` HTTP request body.
 */
@Serializable
data class MultipartPart(
    val name: String,
    val data: ByteArray,
    val filename: String? = null,
    val contentType: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MultipartPart) return false
        return name == other.name &&
            filename == other.filename &&
            contentType == other.contentType &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + data.contentHashCode()
        result = 31 * result + (filename?.hashCode() ?: 0)
        result = 31 * result + (contentType?.hashCode() ?: 0)
        return result
    }
}

/**
 * Structured HTTP request descriptor supporting REST verbs, custom headers, raw binary/JSON payloads,
 * multipart uploads, and per-request timeouts.
 */
@Serializable
data class HttpRequest @JvmOverloads constructor(
    val url: String,
    val method: HttpMethod = HttpMethod.GET,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
    val multipartParts: List<MultipartPart> = emptyList(),
    val timeoutMs: Long? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HttpRequest) return false
        return url == other.url &&
            method == other.method &&
            headers == other.headers &&
            ((body == null && other.body == null) || (body != null && other.body != null && body.contentEquals(other.body))) &&
            multipartParts == other.multipartParts &&
            timeoutMs == other.timeoutMs
    }

    override fun hashCode(): Int {
        var result = url.hashCode()
        result = 31 * result + method.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + (body?.contentHashCode() ?: 0)
        result = 31 * result + multipartParts.hashCode()
        result = 31 * result + (timeoutMs?.hashCode() ?: 0)
        return result
    }
}

/**
 * Structured HTTP response returned by [PluginNetworkClient.request], preserving HTTP status codes,
 * response headers (e.g. `Retry-After`, `ETag`), and raw body bytes even on non-2xx responses.
 */
@Serializable
data class HttpResponse @JvmOverloads constructor(
    val statusCode: Int,
    val headers: Map<String, List<String>> = emptyMap(),
    val body: ByteArray = ByteArray(0),
    val errorMessage: String? = null
) {
    val isSuccess: Boolean get() = statusCode in 200..299

    fun bodyAsText(): String = body.decodeToString()

    fun header(name: String): String? = headers.entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.firstOrNull()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HttpResponse) return false
        return statusCode == other.statusCode &&
            headers == other.headers &&
            body.contentEquals(other.body) &&
            errorMessage == other.errorMessage
    }

    override fun hashCode(): Int {
        var result = statusCode
        result = 31 * result + headers.hashCode()
        result = 31 * result + body.contentHashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        return result
    }
}

/**
 * Network client for plugins that tracks throughput, downloaded bytes, and uploaded bytes.
 */
interface PluginNetworkClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): ByteArray
    suspend fun getText(url: String, headers: Map<String, String> = emptyMap()): String
    suspend fun post(url: String, body: ByteArray, headers: Map<String, String> = emptyMap()): ByteArray
    suspend fun postText(url: String, body: String, headers: Map<String, String> = emptyMap()): String

    /**
     * Execute a full HTTP request returning status code, headers, and raw payload.
     * Preserves non-2xx responses (e.g. 401/404/429) rather than discarding status details.
     */
    suspend fun request(request: HttpRequest): HttpResponse {
        return try {
            when (request.method) {
                HttpMethod.GET -> HttpResponse(statusCode = 200, body = get(request.url, request.headers))
                HttpMethod.POST -> HttpResponse(statusCode = 200, body = post(request.url, request.body ?: ByteArray(0), request.headers))
                else -> HttpResponse(
                    statusCode = 501,
                    errorMessage = "HTTP ${request.method.name} is not supported by this legacy PluginNetworkClient implementation"
                )
            }
        } catch (t: Throwable) {
            HttpResponse(statusCode = 0, errorMessage = t.message ?: t::class.simpleName)
        }
    }
}

/**
 * Fallback no-op implementation of [PluginNetworkClient].
 */
object NoOpPluginNetworkClient : PluginNetworkClient {
    override suspend fun get(url: String, headers: Map<String, String>): ByteArray = ByteArray(0)
    override suspend fun getText(url: String, headers: Map<String, String>): String = ""
    override suspend fun post(url: String, body: ByteArray, headers: Map<String, String>): ByteArray = ByteArray(0)
    override suspend fun postText(url: String, body: String, headers: Map<String, String>): String = ""
    override suspend fun request(request: HttpRequest): HttpResponse = HttpResponse(statusCode = 0, errorMessage = "No network client configured")
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
 * Structured, serializable detail for an execution error.
 * Suitable for cross-process IPC and RPC protocols.
 */
@Serializable
data class ErrorDetail(
    val message: String,
    val exceptionType: String? = null,
    val cause: String? = null,
    val details: Map<String, String> = emptyMap(),
    val stackTrace: String? = null
) {
    val code: String? get() = exceptionType

    fun toThrowable(): Throwable = Exception(message)

    companion object {
        fun fromThrowable(throwable: Throwable?, code: String? = null): ErrorDetail? {
            if (throwable == null) return null
            return ErrorDetail(
                message = throwable.message ?: "Unknown error",
                exceptionType = code ?: throwable::class.simpleName,
                cause = throwable.cause?.message,
                stackTrace = throwable.stackTraceToString()
            )
        }
    }
}

/**
 * The result of a plugin capability execution.
 */
@Serializable
sealed class ExecutionResult {
    /**
     * The task completed successfully.
     * @property response The response data.
     */
    @Serializable
    @SerialName("Success")
    data class Success(val response: PluginResponse) : ExecutionResult()

    /**
     * The task has paused and saved its state.
     * @property resumeState The state to be used for resumption.
     */
    @Serializable
    @SerialName("Paused")
    data class Paused(val resumeState: JsonElement) : ExecutionResult()

    /**
     * The task failed with an error.
     * @property message A human-readable error message.
     * @property detail Structured serializable error detail.
     */
    @Serializable
    @SerialName("Error")
    data class Error(
        val message: String = "Execution failed",
        val detail: ErrorDetail? = null
    ) : ExecutionResult() {
        constructor(throwable: Throwable) : this(
            message = throwable.message ?: "Execution failed",
            detail = ErrorDetail.fromThrowable(throwable)
        )
        constructor(message: String, throwable: Throwable?) : this(
            message = message,
            detail = ErrorDetail.fromThrowable(throwable)
        )
        constructor(error: ErrorDetail) : this(
            message = error.message,
            detail = error
        )

        val error: ErrorDetail get() = detail ?: ErrorDetail(message = message, exceptionType = "Exception")
        val throwable: Throwable get() = detail?.toThrowable() ?: Exception(message)
    }
}

/**
 * Handle to control a running plugin task.
 *
 * Allows for checking the result, pausing, or cancelling the task.
 */
interface JobHandle {
    /**
     * Await the execution result of the task asynchronously.
     * Decoupled from in-memory kotlinx.coroutines Deferred to support IPC/RPC boundaries.
     */
    suspend fun awaitResult(): ExecutionResult

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
 * Extension helper to adapt a [JobHandle] into an in-memory [Deferred] if needed.
 */
fun JobHandle.asDeferred(scope: CoroutineScope): Deferred<ExecutionResult> = scope.async { awaitResult() }

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
     * Encrypted runtime secret storage scoped to this plugin for sensitive tokens and passwords
     * obtained dynamically (e.g. OAuth sessions, API keys).
     */
    val secrets: PluginSecretStorage get() = NoOpPluginSecretStorage

    /**
     * Managed network client with automatic download/upload byte tracking and throughput calculation.
     */
    val networkClient: PluginNetworkClient get() = NoOpPluginNetworkClient

    /**
     * Explicitly record network usage (bytes and throughput) when using external or custom network libraries.
     */
    fun recordNetworkUsage(bytesRead: Long, bytesWritten: Long = 0L, throughputBytesPerSec: Long? = null) {}

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
     * Raise a persistent notification recorded in the host notification center (and surfaced as an alert).
     */
    fun showNotification(
        title: String,
        message: String,
        type: PluginNotificationType = PluginNotificationType.INFO
    ) {
        showToast("$title: $message")
    }

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

    /**
     * Watch an external process spawned by this plugin (e.g. via ProcessBuilder or terminal command).
     * The monitored memory will automatically contribute to the capability's execution metrics.
     *
     * @param pid The operating system Process ID (PID).
     * @return A [ProcessWatcher] handle.
     */
    fun watchProcess(pid: Long): ProcessWatcher = NoOpProcessWatcher(pid)

    /**
     * Return all active process watchers registered in this context.
     */
    fun getActiveProcessWatchers(): List<ProcessWatcher> = emptyList()
}

/**
 * Handle to monitor the memory usage of an external process (e.g. terminal command, Python script, CLI binary).
 */
interface ProcessWatcher : AutoCloseable {
    /**
     * The operating system process ID (PID) of the watched process.
     */
    val pid: Long

    /**
     * Whether the process is currently alive.
     */
    val isAlive: Boolean get() = getCurrentMemoryBytes() > 0L

    /**
     * Current memory consumption in bytes (resident set size / working set) of the process and its child processes.
     */
    fun getCurrentMemoryBytes(): Long

    /**
     * Peak memory consumption in bytes observed since watching started.
     */
    fun getPeakMemoryBytes(): Long

    /**
     * Stop watching the process.
     */
    override fun close()
}

/**
 * Fallback no-op implementation of [ProcessWatcher].
 */
open class NoOpProcessWatcher(override val pid: Long) : ProcessWatcher {
    override fun getCurrentMemoryBytes(): Long = 0L
    override fun getPeakMemoryBytes(): Long = 0L
    override fun close() {}
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
