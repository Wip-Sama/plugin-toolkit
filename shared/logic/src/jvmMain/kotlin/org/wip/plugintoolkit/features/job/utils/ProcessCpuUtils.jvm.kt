package org.wip.plugintoolkit.features.job.utils

import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

actual object ProcessCpuUtils {

    private data class PidCpuSample(
        val timestampNanos: Long,
        val cpuDurationNanos: Long
    )

    private val lastChildSamples = ConcurrentHashMap<Long, PidCpuSample>()

    actual fun getAvailableProcessors(): Int {
        return Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
    }

    actual fun getProcessCpuUsagePercent(extraPids: Collection<Long>): Double? {
        return try {
            val cores = getAvailableProcessors()
            val osBean = ManagementFactory.getOperatingSystemMXBean()
            val jvmLoad = if (osBean is com.sun.management.OperatingSystemMXBean) {
                val rawLoad = osBean.processCpuLoad
                if (rawLoad in 0.0..1.0) rawLoad * 100.0 else null
            } else null

            val allChildPids = (ProcessMemoryUtils.getDescendantPids() + extraPids.filter { it > 0L }).distinct()
            val nowNanos = System.nanoTime()

            var childLoadSum = 0.0
            val currentPids = mutableSetOf<Long>()

            for (pid in allChildPids) {
                currentPids.add(pid)
                val handleOpt = ProcessHandle.of(pid)
                if (handleOpt.isPresent) {
                    val handle = handleOpt.get()
                    val durationOpt = handle.info().totalCpuDuration()
                    if (durationOpt.isPresent) {
                        val currentCpuNanos = durationOpt.get().toNanos()
                        val previous = lastChildSamples.put(pid, PidCpuSample(nowNanos, currentCpuNanos))
                        if (previous != null && nowNanos > previous.timestampNanos && currentCpuNanos >= previous.cpuDurationNanos) {
                            val deltaRealNanos = nowNanos - previous.timestampNanos
                            val deltaCpuNanos = currentCpuNanos - previous.cpuDurationNanos
                            val childPct = (deltaCpuNanos.toDouble() / (deltaRealNanos.toDouble() * cores)) * 100.0
                            childLoadSum += childPct
                        }
                    }
                }
            }

            // Cleanup dead child PIDs from tracking cache
            lastChildSamples.keys.retainAll(currentPids)

            val totalLoad = when {
                jvmLoad != null -> (jvmLoad + childLoadSum).coerceIn(0.0, 100.0)
                childLoadSum > 0.0 -> childLoadSum.coerceIn(0.0, 100.0)
                else -> null
            }

            totalLoad?.let { ((it * 10.0).roundToInt() / 10.0) }
        } catch (_: Throwable) {
            null
        }
    }

    actual fun getSystemCpuUsagePercent(): Double? {
        return try {
            val osBean = ManagementFactory.getOperatingSystemMXBean()
            if (osBean is com.sun.management.OperatingSystemMXBean) {
                val rawLoad = osBean.cpuLoad
                if (rawLoad in 0.0..1.0) {
                    val pct = rawLoad * 100.0
                    (pct * 10.0).roundToInt() / 10.0
                } else null
            } else null
        } catch (_: Throwable) {
            null
        }
    }
}
