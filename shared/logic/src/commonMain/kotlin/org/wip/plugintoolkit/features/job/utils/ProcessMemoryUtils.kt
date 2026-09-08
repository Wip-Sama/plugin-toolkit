package org.wip.plugintoolkit.features.job.utils

expect object ProcessMemoryUtils {
    /**
     * Get the current memory usage (resident set / working set) in bytes of the process with the given PID.
     * Returns 0L if the process is not found or cannot be queried.
     */
    fun getProcessMemoryBytes(pid: Long): Long

    /**
     * Get the PIDs of all active child/descendant processes spawned by the current JVM process.
     */
    fun getDescendantPids(): List<Long>

    /**
     * Get the total memory in bytes used by all active descendant processes of the current JVM.
     */
    fun getAllDescendantsMemoryBytes(): Long

    /**
     * Check if a process with the given PID is currently alive.
     */
    fun isProcessAlive(pid: Long): Boolean

    /**
     * Get the combined memory in bytes used by all active descendant processes plus any extra tracked PIDs.
     */
    fun getTotalTrackedMemoryBytes(extraPids: Collection<Long> = emptyList()): Long
}
