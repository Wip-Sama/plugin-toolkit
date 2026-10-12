package org.wip.plugintoolkit.features.job.logic

import co.touchlab.kermit.Logger
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.koin.core.component.KoinComponent
import org.wip.plugintoolkit.core.utils.PlatformPathUtils
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.ScratchDiskConfig

/**
 * Manages allocation, prioritization, and cleanup of scratch disk storage locations.
 *
 * When multiple scratch disks are configured, they are prioritized based on their priority rank.
 * If configured scratch disks are inaccessible or full, operations gracefully fall back to the
 * default AppData scratch location.
 */
class ScratchDiskManager(
    private val settingsRepository: SettingsRepository? = null,
    private val settingsPersistence: SettingsPersistence? = null,
    private val customScratchDisksProvider: (() -> List<ScratchDiskConfig>)? = null,
    private val customFallbackDirProvider: (() -> String)? = null
) : KoinComponent {

    companion object {
        const val MIN_FREE_SPACE_BYTES: Long = 10L * 1024L * 1024L // 10 MB minimum space
        const val JOBS_SUBDIR: String = "jobs"
        const val SANDBOX_SUBDIR: String = "sandbox"
    }

    /**
     * Retrieves the currently configured scratch disks from settings or custom provider.
     */
    fun getConfiguredScratchDisks(): List<ScratchDiskConfig> {
        return customScratchDisksProvider?.invoke()
            ?: settingsRepository?.settings?.value?.general?.scratchDisks
            ?: emptyList()
    }

    /**
     * Returns the default fallback scratch directory in AppData.
     */
    fun getDefaultScratchDir(): String {
        return customFallbackDirProvider?.invoke()
            ?: settingsPersistence?.getSettingsDir()
            ?: PlatformPathUtils.getAppDataDir()
    }

    /**
     * Resolves the highest priority available scratch root directory.
     * Evaluates configured disks in priority order (ascending: 1 is highest priority).
     * Falls back to AppData if no configured disks are usable.
     */
    fun resolveScratchRoot(): String {
        val configured = getConfiguredScratchDisks()
            .filter { it.enabled && it.path.isNotBlank() }
            .sortedWith(compareBy<ScratchDiskConfig> { it.priority }.thenBy { it.path })

        for (disk in configured) {
            val normalizedPath = disk.path.trim().replace('\\', '/')
            if (isDiskUsable(normalizedPath)) {
                Logger.d { "ScratchDiskManager: Selected scratch disk '$normalizedPath' with priority ${disk.priority}" }
                return normalizedPath
            } else {
                Logger.w { "ScratchDiskManager: Scratch disk '$normalizedPath' (priority ${disk.priority}) is not accessible or writable. Trying next disk." }
            }
        }

        val fallback = getDefaultScratchDir().replace('\\', '/')
        Logger.d { "ScratchDiskManager: Using default AppData scratch directory '$fallback'" }
        return fallback
    }

    /**
     * Determines whether a directory path exists or can be created, is writable, and has sufficient free space.
     */
    fun isDiskUsable(path: String): Boolean {
        return try {
            val file = java.io.File(path)
            if (!file.exists()) {
                val created = file.mkdirs()
                if (!created && !file.exists()) return false
            }
            if (!file.isDirectory || !file.canWrite()) return false

            val usable = file.usableSpace
            if (usable in 1 until MIN_FREE_SPACE_BYTES) {
                Logger.w { "ScratchDiskManager: Path '$path' has low free space ($usable bytes)" }
                return false
            }
            true
        } catch (e: Exception) {
            Logger.w(e) { "ScratchDiskManager: Failed to check path '$path'" }
            false
        }
    }

    /**
     * Returns the sandbox directory for a given job.
     * If [preferredRoot] is provided, uses that scratch root.
     * Otherwise, checks whether an existing sandbox directory exists across candidate roots.
     * If none exists, allocates one on the highest-priority usable scratch root.
     */
    fun getJobSandboxDir(jobId: String, preferredRoot: String? = null): String {
        if (!preferredRoot.isNullOrBlank()) {
            val root = preferredRoot.replace('\\', '/')
            return "$root/$JOBS_SUBDIR/$jobId/$SANDBOX_SUBDIR"
        }

        // Check if sandbox already exists on any candidate root (e.g. paused job)
        val candidateRoots = getAllCandidateRoots()
        for (root in candidateRoots) {
            val candidatePath = Path("$root/$JOBS_SUBDIR/$jobId/$SANDBOX_SUBDIR")
            if (SystemFileSystem.exists(candidatePath)) {
                return candidatePath.toString().replace('\\', '/')
            }
        }

        val activeRoot = resolveScratchRoot()
        return "$activeRoot/$JOBS_SUBDIR/$jobId/$SANDBOX_SUBDIR"
    }

    /**
     * Returns a node-specific sandbox directory inside a job's sandbox.
     */
    fun getJobNodeSandboxDir(jobId: String, nodeSubpath: String, preferredRoot: String? = null): String {
        val baseSandbox = getJobSandboxDir(jobId, preferredRoot)
        return "$baseSandbox/$nodeSubpath"
    }

    /**
     * Deletes the sandbox directory for the specified job across all scratch locations.
     */
    fun deleteJobSandbox(jobId: String, preferredRoot: String? = null) {
        if (!preferredRoot.isNullOrBlank()) {
            val directPath = Path("${preferredRoot.replace('\\', '/')}/$JOBS_SUBDIR/$jobId/$SANDBOX_SUBDIR")
            deleteRecursively(directPath)
        }

        for (root in getAllCandidateRoots()) {
            val path = Path("$root/$JOBS_SUBDIR/$jobId/$SANDBOX_SUBDIR")
            if (SystemFileSystem.exists(path)) {
                try {
                    deleteRecursively(path)
                    Logger.d { "ScratchDiskManager: Deleted job sandbox at $path" }
                } catch (e: Exception) {
                    Logger.w(e) { "ScratchDiskManager: Failed to delete sandbox at $path" }
                }
            }
        }
    }

    /**
     * Returns all candidate scratch roots: all configured scratch disks plus the fallback AppData directory.
     */
    fun getAllCandidateRoots(): List<String> {
        val roots = mutableListOf<String>()
        for (disk in getConfiguredScratchDisks()) {
            val trimmed = disk.path.trim().replace('\\', '/')
            if (trimmed.isNotBlank() && !roots.contains(trimmed)) {
                roots.add(trimmed)
            }
        }
        val defaultRoot = getDefaultScratchDir().replace('\\', '/')
        if (!roots.contains(defaultRoot)) {
            roots.add(defaultRoot)
        }
        return roots
    }

    /**
     * Cleans orphaned job sandbox directories across all scratch roots that are not active in [activeJobIds].
     */
    fun cleanOrphanSandboxes(activeJobIds: Set<String>) {
        for (root in getAllCandidateRoots()) {
            val jobsDir = Path("$root/$JOBS_SUBDIR")
            if (!SystemFileSystem.exists(jobsDir)) continue
            try {
                val entries = SystemFileSystem.list(jobsDir)
                for (entry in entries) {
                    val metadata = SystemFileSystem.metadataOrNull(entry)
                    if (metadata?.isDirectory == true) {
                        val jobId = entry.name
                        if (!activeJobIds.contains(jobId)) {
                            val sandboxDir = Path(entry, SANDBOX_SUBDIR)
                            if (SystemFileSystem.exists(sandboxDir)) {
                                try {
                                    deleteRecursively(sandboxDir)
                                    Logger.i { "ScratchDiskManager: Cleaned orphan sandbox in '$entry'" }
                                } catch (e: Exception) {
                                    Logger.w(e) { "ScratchDiskManager: Failed to delete orphan sandbox in '$entry'" }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(e) { "ScratchDiskManager: Error scanning scratch root '$root' for orphans" }
            }
        }
    }
}
