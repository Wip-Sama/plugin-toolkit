package org.wip.plugintoolkit.features.job.utils

/**
 * Platform-independent utility to inspect GPU / Video RAM (VRAM) usage.
 * Supports tracking process-tree dedicated VRAM and system-wide VRAM with graceful fallbacks.
 */
expect object ProcessGpuUtils {
    /**
     * Returns true if dedicated VRAM monitoring is supported and available on this platform.
     */
    fun isVramMonitoringSupported(): Boolean

    /**
     * Get the dedicated VRAM in bytes used by a specific process PID.
     * Returns 0L if unsupported, process not found, or no dedicated VRAM is currently allocated.
     */
    fun getProcessVramBytes(pid: Long): Long

    /**
     * Get the total dedicated VRAM in bytes used by the current process tree
     * (descendant processes of the JVM plus any explicitly tracked extra PIDs, plus JVM if applicable).
     */
    fun getTotalTrackedVramBytes(extraPids: Collection<Long> = emptyList()): Long

    /**
     * Get the system-wide dedicated VRAM usage as a Pair of (usedBytes, totalBytes).
     * Returns null if unsupported or if no dedicated GPU is detected.
     */
    fun getSystemVramBytes(): Pair<Long, Long>?
}
