package org.wip.plugintoolkit.features.plugin.logic

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.persistentListOf

/**
 * Thread-safe tracker for capability and plugin execution resource usage,
 * including file system I/O (bytes read/written) and internet throughput/transfer.
 */
class ResourceUsageTracker {
    private val _bytesRead = atomic(0L)
    private val _bytesWritten = atomic(0L)
    private val _networkBytesRead = atomic(0L)
    private val _networkBytesWritten = atomic(0L)
    private val _throughputSamples = atomic(persistentListOf<Long>())

    val bytesRead: Long get() = _bytesRead.value
    val bytesWritten: Long get() = _bytesWritten.value
    val networkBytesRead: Long get() = _networkBytesRead.value
    val networkBytesWritten: Long get() = _networkBytesWritten.value

    fun recordFileRead(bytes: Long) {
        if (bytes > 0L) {
            _bytesRead.addAndGet(bytes)
        }
    }

    fun recordFileWrite(bytes: Long) {
        if (bytes > 0L) {
            _bytesWritten.addAndGet(bytes)
        }
    }

    fun recordNetworkUsage(read: Long, written: Long = 0L, throughputBytesPerSec: Long? = null) {
        if (read > 0L) {
            _networkBytesRead.addAndGet(read)
        }
        if (written > 0L) {
            _networkBytesWritten.addAndGet(written)
        }
        if (throughputBytesPerSec != null && throughputBytesPerSec > 0L) {
            _throughputSamples.update { it.add(throughputBytesPerSec) }
        }
    }

    val averageThroughputBytesPerSec: Long?
        get() {
            val samples = _throughputSamples.value
            return if (samples.isEmpty()) null else samples.sum() / samples.size
        }
}
