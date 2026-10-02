package org.wip.plugintoolkit.core.utils

import co.touchlab.kermit.Logger
import java.io.File

/**
 * Details of a previous unclosed session detected by [CrashDetector].
 */
data class PreviousSessionInfo(
    val pid: Long?,
    val timestamp: Long?,
    val markerFile: File
)

/**
 * Detects whether the application closed unexpectedly (e.g. JVM crash, SIGKILL, sudden power loss)
 * during its previous session by using a marker file in the application data directory.
 *
 * Workflow:
 * 1. On startup, [checkAndRecordSessionStart] is invoked.
 * 2. If a marker file already exists from a prior execution, a warning is logged indicating
 *    that the previous session was closed unexpectedly or crashed, and [PreviousSessionInfo] is returned.
 * 3. A new marker file is recorded with the current PID and start timestamp.
 * 4. A JVM shutdown hook is registered to delete the marker file on normal JVM termination.
 * 5. On graceful user exit, [recordCleanShutdown] can also be called directly to remove the marker.
 */
object CrashDetector {
    const val DEFAULT_MARKER_FILE_NAME = ".session_active"

    private var activeMarkerFile: File? = null
    private var shutdownHookThread: Thread? = null

    /**
     * Checks if a marker file exists from a previous session that did not clean up cleanly.
     * Logs an informative warning if found, then writes the current session's marker.
     *
     * @param directory The application directory where the marker file resides.
     * @param markerFileName Name of the marker file (defaults to [.session_active]).
     * @return [PreviousSessionInfo] if an unclean shutdown was detected, or `null` if the previous session exited cleanly.
     */
    @Synchronized
    fun checkAndRecordSessionStart(
        directory: File,
        markerFileName: String = DEFAULT_MARKER_FILE_NAME
    ): PreviousSessionInfo? {
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val marker = File(directory, markerFileName)
        var previousSession: PreviousSessionInfo? = null

        if (marker.exists()) {
            previousSession = readSessionInfo(marker)
            Logger.w {
                "CrashDetector: Application previously crashed or terminated unexpectedly! " +
                        "Found active session marker at ${marker.absolutePath} " +
                        "(Previous PID: ${previousSession.pid ?: "unknown"}, " +
                        "Started At: ${previousSession.timestamp ?: "unknown"})"
            }
        } else {
            Logger.d { "CrashDetector: Clean startup detected. No previous session marker found." }
        }

        // Record current session
        try {
            val currentPid = getCurrentProcessId()
            val currentTimestamp = System.currentTimeMillis()
            marker.writeText("$currentPid\n$currentTimestamp")
            activeMarkerFile = marker
            Logger.d { "CrashDetector: Session active marker written for PID $currentPid at $currentTimestamp" }
        } catch (e: Exception) {
            Logger.e(e) { "CrashDetector: Failed to write session marker file ${marker.absolutePath}" }
        }

        // Register shutdown hook if not already registered
        if (shutdownHookThread == null) {
            val hook = Thread({
                recordCleanShutdown()
            }, "CrashDetector-ShutdownHook")
            shutdownHookThread = hook
            try {
                Runtime.getRuntime().addShutdownHook(hook)
            } catch (e: Exception) {
                Logger.w(e) { "CrashDetector: Could not register JVM shutdown hook" }
            }
        }

        return previousSession
    }

    /**
     * Cleans up the session marker file, marking this session as cleanly terminated.
     *
     * @return `true` if the marker existed and was deleted, `false` otherwise.
     */
    @Synchronized
    fun recordCleanShutdown(): Boolean {
        val file = activeMarkerFile ?: return false
        return try {
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    Logger.i { "CrashDetector: Clean shutdown recorded. Removed session marker ${file.absolutePath}" }
                }
                deleted
            } else {
                false
            }
        } catch (e: Exception) {
            Logger.w(e) { "CrashDetector: Failed to delete session marker ${file.absolutePath}" }
            false
        } finally {
            activeMarkerFile = null
        }
    }

    /**
     * Resets internal references and unregisters the shutdown hook if possible.
     * Primarily used for unit testing.
     */
    @Synchronized
    internal fun resetForTesting() {
        shutdownHookThread?.let { hook ->
            try {
                Runtime.getRuntime().removeShutdownHook(hook)
            } catch (_: Exception) {}
        }
        shutdownHookThread = null
        activeMarkerFile = null
    }

    private fun readSessionInfo(markerFile: File): PreviousSessionInfo {
        var pid: Long? = null
        var timestamp: Long? = null
        try {
            val lines = markerFile.readLines()
            if (lines.isNotEmpty()) {
                pid = lines[0].trim().toLongOrNull()
            }
            if (lines.size > 1) {
                timestamp = lines[1].trim().toLongOrNull()
            }
        } catch (e: Exception) {
            Logger.w(e) { "CrashDetector: Could not read previous session info from ${markerFile.absolutePath}" }
        }
        return PreviousSessionInfo(pid = pid, timestamp = timestamp, markerFile = markerFile)
    }

    private fun getCurrentProcessId(): Long {
        return try {
            ProcessHandle.current().pid()
        } catch (_: Throwable) {
            -1L
        }
    }
}
