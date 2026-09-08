package org.wip.plugintoolkit.features.job.utils

import org.wip.plugintoolkit.api.ProcessWatcher

/**
 * Concrete implementation of [ProcessWatcher] backed by [ProcessMemoryUtils].
 */
class LiveProcessWatcher(
    override val pid: Long,
    private val onClose: (LiveProcessWatcher) -> Unit = {}
) : ProcessWatcher {
    private var peakMemoryBytes: Long = 0L
    private var isClosed = false

    override val isAlive: Boolean
        get() = !isClosed && ProcessMemoryUtils.isProcessAlive(pid)

    override fun getCurrentMemoryBytes(): Long {
        if (isClosed) return 0L
        val current = ProcessMemoryUtils.getProcessMemoryBytes(pid)
        if (current > peakMemoryBytes) {
            peakMemoryBytes = current
        }
        return current
    }

    override fun getPeakMemoryBytes(): Long {
        getCurrentMemoryBytes()
        return peakMemoryBytes
    }

    override fun close() {
        if (!isClosed) {
            isClosed = true
            onClose(this)
        }
    }
}
