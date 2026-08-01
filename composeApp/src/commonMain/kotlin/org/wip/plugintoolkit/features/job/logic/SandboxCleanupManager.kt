package org.wip.plugintoolkit.features.job.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

/**
 * Service responsible for deleting job sandbox directories and cleaning orphaned sandboxes
 * when initial deletions fail (e.g. file lock on Windows).
 */
class SandboxCleanupManager {
    private val pendingDeletions = mutableSetOf<String>()
    private val mutex = Mutex()

    /**
     * Registers a directory path for deferred deletion if immediate deletion failed.
     */
    suspend fun registerFailedDeletion(dirPath: String) {
        mutex.withLock {
            pendingDeletions.add(dirPath)
        }
        Logger.w { "SandboxCleanupManager: Registered path for deferred deletion: $dirPath" }
    }

    /**
     * Attempts to delete all pending failed sandbox paths and scans the sandbox root directory
     * for orphaned job directories that are no longer active in [JobManager].
     */
    suspend fun cleanOrphanSandboxes(appDataDir: String, activeJobIds: Set<String>) {
        // 1. Attempt pending deferred deletions
        val currentPending = mutex.withLock { pendingDeletions.toList() }
        for (pathStr in currentPending) {
            val path = Path(pathStr)
            if (SystemFileSystem.exists(path)) {
                try {
                    deleteRecursively(path)
                    mutex.withLock { pendingDeletions.remove(pathStr) }
                    Logger.i { "SandboxCleanupManager: Successfully cleaned deferred path: $pathStr" }
                } catch (e: Exception) {
                    Logger.w { "SandboxCleanupManager: Deferred cleanup still failing for $pathStr: ${e.message}" }
                }
            } else {
                mutex.withLock { pendingDeletions.remove(pathStr) }
            }
        }

        val jobsDir = Path("$appDataDir/jobs")
        if (!SystemFileSystem.exists(jobsDir)) return

        // 2. Scan jobs directory for orphaned job sandboxes
        try {
            val entries = SystemFileSystem.list(jobsDir)
            for (entry in entries) {
                val metadata = SystemFileSystem.metadataOrNull(entry)
                if (metadata?.isDirectory == true) {
                    val jobId = entry.name
                    if (!activeJobIds.contains(jobId)) {
                        try {
                            deleteRecursively(entry)
                            Logger.i { "SandboxCleanupManager: Cleaned orphan job directory: ${entry.name}" }
                        } catch (e: Exception) {
                            Logger.w { "SandboxCleanupManager: Failed to delete orphan job directory ${entry.name}: ${e.message}" }
                            registerFailedDeletion(entry.toString())
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(e) { "SandboxCleanupManager: Error scanning jobs directory for orphans" }
        }
    }
}
