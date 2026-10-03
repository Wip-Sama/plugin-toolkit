package org.wip.plugintoolkit.features.job.utils

/**
 * Platform-independent utility to inspect CPU core topology and utilization percentages.
 */
expect object ProcessCpuUtils {
    /**
     * Returns the number of logical CPU cores / execution threads available to the system.
     */
    fun getAvailableProcessors(): Int

    /**
     * Returns the recent CPU utilization percentage (0.0 to 100.0) for the current process tree
     * (the host JVM and any descendant or tracked child processes).
     * Returns null if CPU load cannot be determined.
     */
    fun getProcessCpuUsagePercent(extraPids: Collection<Long> = emptyList()): Double?

    /**
     * Returns the recent system-wide overall CPU utilization percentage (0.0 to 100.0).
     * Returns null if system CPU load cannot be determined.
     */
    fun getSystemCpuUsagePercent(): Double?
}
