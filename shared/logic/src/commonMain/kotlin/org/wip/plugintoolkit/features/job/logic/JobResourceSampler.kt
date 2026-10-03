package org.wip.plugintoolkit.features.job.logic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.wip.plugintoolkit.core.utils.MemoryUtils
import org.wip.plugintoolkit.features.job.model.ResourceUsageSample
import org.wip.plugintoolkit.features.job.utils.ProcessCpuUtils
import org.wip.plugintoolkit.features.job.utils.ProcessGpuUtils
import org.wip.plugintoolkit.features.job.utils.ProcessMemoryUtils
import kotlin.math.round
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Encapsulates periodic and event-driven resource sampling (RAM, VRAM, CPU) during job and flow execution.
 */
class JobResourceSampler(
    val jobId: String,
    private val samplingIntervalMs: Long = 5000L,
    private val getActivePids: () -> Collection<Long> = { emptyList() },
    private val manager: JobManager
) {
    private val startMark = TimeSource.Monotonic.markNow()
    private val samples = mutableListOf<ResourceUsageSample>()
    private val lock = Any()

    @Volatile
    var peakMemory: Long = 0L
        private set

    @Volatile
    var peakVram: Long = 0L
        private set

    @Volatile
    var peakProcessCpu: Double? = null
        private set

    init {
        // Record initial baseline snapshot
        recordSnapshot()
    }

    fun start(scope: CoroutineScope): Job {
        val interval = samplingIntervalMs.coerceAtLeast(100L).milliseconds
        return scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(interval)
                recordSnapshot()
            }
        }
    }

    /**
     * Captures a discrete resource snapshot, optionally associated with an active capability or flow node.
     */
    fun recordSnapshot(
        activeCapability: String? = null,
        activeNodeId: String? = null
    ): ResourceUsageSample {
        val now = Clock.System.now()
        val elapsedMs = startMark.elapsedNow().inWholeMilliseconds
        val extraPids = getActivePids()

        val jvmMem = MemoryUtils.getCurrentMemoryUsageBytes()
        val procMem = ProcessMemoryUtils.getTotalTrackedMemoryBytes(extraPids)
        val totalRam = jvmMem + procMem

        val procVram = ProcessGpuUtils.getTotalTrackedVramBytes(extraPids).takeIf { it > 0L }
        val sysVram = ProcessGpuUtils.getSystemVramBytes()

        val procCpu = ProcessCpuUtils.getProcessCpuUsagePercent(extraPids)
        val sysCpu = ProcessCpuUtils.getSystemCpuUsagePercent()
        val cores = ProcessCpuUtils.getAvailableProcessors()

        synchronized(lock) {
            if (totalRam > peakMemory) {
                peakMemory = totalRam
                manager.recordLivePeakMemory(jobId, peakMemory)
            }
            if (procVram != null && procVram > peakVram) {
                peakVram = procVram
                manager.recordLivePeakVram(jobId, peakVram)
            }
            if (procCpu != null) {
                val currentPeak = peakProcessCpu
                if (currentPeak == null || procCpu > currentPeak) {
                    peakProcessCpu = procCpu
                    manager.recordLivePeakCpu(jobId, procCpu)
                }
            }
        }

        val sample = ResourceUsageSample(
            timestamp = now,
            elapsedMs = elapsedMs,
            ramUsageBytes = totalRam,
            totalRamBytes = MemoryUtils.getMaxMemoryBytes(),
            processVramBytes = procVram,
            systemVramUsedBytes = sysVram?.first,
            systemVramTotalBytes = sysVram?.second,
            processCpuPercent = procCpu,
            systemCpuPercent = sysCpu,
            availableCores = cores,
            activeCapability = activeCapability,
            activeNodeId = activeNodeId
        )

        synchronized(lock) {
            samples.add(sample)
        }

        manager.recordResourceSample(jobId, sample)
        return sample
    }

    fun getSamples(): List<ResourceUsageSample> = synchronized(lock) { samples.toList() }

    fun finish(
        activeCapability: String? = null,
        activeNodeId: String? = null
    ): FinishedResourceMetrics {
        recordSnapshot(activeCapability, activeNodeId)
        val allSamples = getSamples()

        val avgProcCpu = allSamples.mapNotNull { it.processCpuPercent }.takeIf { it.isNotEmpty() }?.let {
            round(it.sum() / it.size * 10.0) / 10.0
        }
        val peakProcCpu = allSamples.mapNotNull { it.processCpuPercent }.maxOrNull()
        val avgSysCpu = allSamples.mapNotNull { it.systemCpuPercent }.takeIf { it.isNotEmpty() }?.let {
            round(it.sum() / it.size * 10.0) / 10.0
        }

        return FinishedResourceMetrics(
            peakMemoryBytes = peakMemory,
            peakVramBytes = peakVram.takeIf { it > 0L },
            avgProcessCpuPercent = avgProcCpu,
            peakProcessCpuPercent = peakProcCpu,
            avgSystemCpuPercent = avgSysCpu,
            timeline = allSamples
        )
    }

    data class FinishedResourceMetrics(
        val peakMemoryBytes: Long,
        val peakVramBytes: Long?,
        val avgProcessCpuPercent: Double?,
        val peakProcessCpuPercent: Double?,
        val avgSystemCpuPercent: Double?,
        val timeline: List<ResourceUsageSample>
    )
}
