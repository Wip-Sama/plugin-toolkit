package org.wip.plugintoolkit.core.utils

import co.touchlab.kermit.Logger
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/**
 * Result of attempting to acquire the single-instance application lock.
 */
sealed interface LockResult {
    /** The lock was successfully acquired. */
    data object Acquired : LockResult

    /** Another instance is actively running and holds the lock. */
    data class AlreadyRunning(
        val pid: Long?,
        val timestamp: Long?,
        val lockFile: File
    ) : LockResult

    /** An error occurred while evaluating the lock. */
    data class Error(val message: String, val throwable: Throwable? = null) : LockResult
}

/**
 * Manages the single-instance application lock via an OS-level file lock on `.lock`.
 *
 * Prevents multiple instances of PluginToolkit from spawning simultaneously,
 * protecting internal configs, databases, and caches from concurrency corruption.
 */
object AppLockManager {
    private var randomAccessFile: RandomAccessFile? = null
    private var fileChannel: FileChannel? = null
    private var fileLock: FileLock? = null
    private var activeLockFile: File? = null
    private var shutdownHookThread: Thread? = null
    private var currentPid: Long? = null
    private var currentTimestamp: Long? = null

    fun getCurrentLockPid(): Long? = currentPid
    fun getCurrentLockTimestamp(): Long? = currentTimestamp

    /**
     * Attempts to acquire an exclusive lock on the application lock file.
     *
     * @param lockDir The directory where the lock file is placed (e.g., app data folder).
     * @param lockFileName The name of the lock file (default: `.lock`).
     * @return [LockResult] indicating whether the lock was acquired or held by another instance.
     */
    @Synchronized
    fun acquireLock(lockDir: File, lockFileName: String = ".lock"): LockResult {
        try {
            if (!lockDir.exists()) {
                lockDir.mkdirs()
            }
            val targetFile = File(lockDir, lockFileName)
            activeLockFile = targetFile

            // Check if file exists and extract existing info before attempting lock
            var existingPid: Long? = null
            var existingTimestamp: Long? = null
            if (targetFile.exists() && targetFile.length() > 0) {
                try {
                    val lines = targetFile.readLines()
                    existingPid = lines.firstOrNull()?.trim()?.toLongOrNull()
                    existingTimestamp = lines.getOrNull(1)?.trim()?.toLongOrNull()
                } catch (e: Exception) {
                    Logger.w { "AppLockManager: Could not parse existing lock file: ${e.message}" }
                }
            }

            val raf = RandomAccessFile(targetFile, "rw")
            val channel = raf.channel

            val lock = try {
                channel.tryLock()
            } catch (e: OverlappingFileLockException) {
                null
            } catch (e: Exception) {
                null
            }

            if (lock != null && lock.isValid) {
                // We got the lock!
                randomAccessFile = raf
                fileChannel = channel
                fileLock = lock

                val currentPid = try {
                    ProcessHandle.current().pid()
                } catch (t: Throwable) {
                    0L
                }
                val currentTimestamp = System.currentTimeMillis()

                // Overwrite with our own PID and timestamp
                channel.truncate(0)
                val content = "$currentPid\n$currentTimestamp\n"
                raf.write(content.toByteArray(Charsets.UTF_8))
                channel.force(true)

                this.currentPid = currentPid
                this.currentTimestamp = currentTimestamp

                registerShutdownHook()
                Logger.i { "AppLockManager: Successfully acquired lock on ${targetFile.absolutePath} (PID=$currentPid)" }
                return LockResult.Acquired
            } else {
                // Lock held by another process
                raf.close()

                // If existing PID is known, verify if process is still alive
                if (existingPid != null && existingPid > 0) {
                    val isAlive = try {
                        ProcessHandle.of(existingPid).map { it.isAlive }.orElse(false)
                    } catch (t: Throwable) {
                        true // Assume alive if inspection fails
                    }

                    if (!isAlive) {
                        Logger.w { "AppLockManager: Stale lock detected (PID $existingPid is not alive). Reclaiming lock." }
                        clearLockFileOnly(targetFile)
                        return acquireLock(lockDir, lockFileName)
                    }
                }

                Logger.w { "AppLockManager: Lock is held by running instance (PID=$existingPid)" }
                return LockResult.AlreadyRunning(existingPid, existingTimestamp, targetFile)
            }
        } catch (t: Throwable) {
            Logger.e(t) { "AppLockManager: Unexpected error while acquiring lock: ${t.message}" }
            return LockResult.Error(t.message ?: "Failed to acquire lock", t)
        }
    }

    /**
     * Forcibly removes/clears the lock file and acquires a fresh lock.
     *
     * @param lockDir The directory containing the lock file.
     * @param lockFileName The name of the lock file.
     */
    @Synchronized
    fun forceClearAndAcquire(lockDir: File, lockFileName: String = ".lock"): LockResult {
        val targetFile = File(lockDir, lockFileName)
        releaseLock()
        clearLockFileOnly(targetFile)
        return acquireLock(lockDir, lockFileName)
    }

    /**
     * Checks if this instance currently holds a valid lock.
     */
    fun isLockAcquired(): Boolean = fileLock?.isValid == true

    /**
     * Release the active lock and remove the lock file.
     */
    @Synchronized
    fun releaseLock() {
        try {
            fileLock?.let {
                if (it.isValid) {
                    it.release()
                }
            }
        } catch (t: Throwable) {
            Logger.w { "AppLockManager: Error releasing file lock: ${t.message}" }
        } finally {
            fileLock = null
        }

        try {
            fileChannel?.close()
        } catch (t: Throwable) {
            Logger.w { "AppLockManager: Error closing channel: ${t.message}" }
        } finally {
            fileChannel = null
        }

        try {
            randomAccessFile?.close()
        } catch (t: Throwable) {
            Logger.w { "AppLockManager: Error closing RandomAccessFile: ${t.message}" }
        } finally {
            randomAccessFile = null
        }

        activeLockFile?.let { clearLockFileOnly(it) }
        activeLockFile = null
        currentPid = null
        currentTimestamp = null
        deregisterShutdownHook()
    }

    private fun clearLockFileOnly(file: File) {
        try {
            if (file.exists()) {
                file.delete()
            }
        } catch (t: Throwable) {
            Logger.w { "AppLockManager: Error deleting lock file ${file.absolutePath}: ${t.message}" }
        }
    }

    private fun registerShutdownHook() {
        if (shutdownHookThread == null) {
            shutdownHookThread = Thread({
                releaseLock()
            }, "AppLockManager-ShutdownHook").also {
                Runtime.getRuntime().addShutdownHook(it)
            }
        }
    }

    private fun deregisterShutdownHook() {
        shutdownHookThread?.let {
            try {
                Runtime.getRuntime().removeShutdownHook(it)
            } catch (_: IllegalStateException) {
                // VM is already shutting down
            }
        }
        shutdownHookThread = null
    }
}
