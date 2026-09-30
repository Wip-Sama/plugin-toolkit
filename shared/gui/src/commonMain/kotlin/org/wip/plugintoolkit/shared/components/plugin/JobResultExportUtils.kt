package org.wip.plugintoolkit.shared.components.plugin

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.wip.plugintoolkit.core.utils.FormatUtils
import org.wip.plugintoolkit.core.utils.MemoryUtils
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Instant

internal fun formatDuration(ms: Long): String {
    if (ms < 1000L) return "${ms}ms"
    val totalSeconds = ms / 1000.0
    val minutes = (ms / 60000L)
    val seconds = ((ms % 60000L) / 1000.0 * 10.0).roundToLong() / 10.0
    return if (minutes > 0) "${minutes}m ${seconds.toInt()}s" else "${seconds}s"
}

internal fun formatTimestamp(instant: Instant?): String {
    if (instant == null) return "—"
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${
        local.minute.toString().padStart(2, '0')
    }:${local.second.toString().padStart(2, '0')}"
}

internal fun buildJobExportReport(
    job: BackgroundJob,
    logs: List<String>,
    startedAtLabel: String,
    completedAtLabel: String,
    durationLabel: String,
    memoryLabel: String,
    capabilityBreakdownLabel: String,
    totalMemoryLabel: String? = null
): String {
    val sb = StringBuilder()
    sb.appendLine("================================================================================")
    sb.appendLine("JOB EXECUTION REPORT")
    sb.appendLine("================================================================================")
    sb.appendLine("Job ID:         ${job.id}")
    sb.appendLine("Job Name:       ${job.name}")
    sb.appendLine("Job Type:       ${job.type}")
    sb.appendLine("Status:         ${job.status}")
    sb.appendLine("Triggered At:   ${job.enqueuedAt}")

    val metrics = job.executionMetrics
    val startedAt = metrics?.startedAt ?: job.startedAt
    val completedAt = metrics?.completedAt ?: job.completedAt
    val durationMs = metrics?.totalDurationMs ?: run {
        if (startedAt != null) {
            val end = completedAt ?: Clock.System.now()
            (end - startedAt).inWholeMilliseconds.coerceAtLeast(0L)
        } else null
    }

    if (startedAt != null) {
        sb.appendLine("$startedAtLabel:     $startedAt")
    }
    if (completedAt != null) {
        sb.appendLine("$completedAtLabel:   $completedAt")
    }
    if (durationMs != null) {
        sb.appendLine("$durationLabel: $durationMs ms (${formatDuration(durationMs)})")
    }
    val memoryUsage = metrics?.memoryUsageBytes
    if (memoryUsage != null) {
        sb.appendLine("$memoryLabel:   ${MemoryUtils.formatMemoryBytes(memoryUsage)}")
    }
    val totalMemoryUsage = metrics?.effectiveTotalMemoryUsageBytes
    if (totalMemoryUsage != null && totalMemoryLabel != null) {
        sb.appendLine("$totalMemoryLabel: ${MemoryUtils.formatMemoryBytes(totalMemoryUsage)}")
    }

    if (metrics != null && metrics.capabilityMetrics.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine(capabilityBreakdownLabel.uppercase())
        sb.appendLine("--------------------------------------------------------------------------------")
        val durations = metrics.totalDurationPerCapability
        val counts = metrics.executionCountPerCapability
        val metricsByCap = metrics.metricsPerCapability
        durations.forEach { (capName, ms) ->
            val count = counts[capName] ?: 1
            val countStr = if (count > 1) " (x$count)" else ""
            val pctStr = if (durationMs != null && durationMs > 0L) {
                " [${((ms.toDouble() / durationMs) * 100.0).roundToInt()}%]"
            } else ""
            sb.appendLine("- $capName: $ms ms (${formatDuration(ms)})$countStr$pctStr")

            val execs = metricsByCap[capName] ?: emptyList()
            val peak = execs.mapNotNull { it.memoryUsageBytes }.maxOrNull()
            val totMem = execs.mapNotNull { it.totalMemoryBytes ?: it.memoryUsageBytes }.sum().takeIf { it > 0L }
            val r = execs.mapNotNull { it.bytesRead }.sum().takeIf { it > 0L }
            val w = execs.mapNotNull { it.bytesWritten }.sum().takeIf { it > 0L }
            val netR = execs.mapNotNull { it.networkBytesRead }.sum().takeIf { it > 0L }
            val netW = execs.mapNotNull { it.networkBytesWritten }.sum().takeIf { it > 0L }
            val tputs = execs.mapNotNull { it.throughputBytesPerSec }.filter { it > 0L }
            val avgTput = if (tputs.isNotEmpty()) tputs.sum() / tputs.size else null

            val details = mutableListOf<String>()
            if (peak != null) details.add("Peak Memory: ${MemoryUtils.formatMemoryBytes(peak)}")
            if (totMem != null) details.add("Total Memory: ${MemoryUtils.formatMemoryBytes(totMem)}")
            if (r != null || w != null) details.add("File I/O: R ${FormatUtils.formatFileSize(r ?: 0L)} / W ${FormatUtils.formatFileSize(w ?: 0L)}")
            if (netR != null || netW != null || avgTput != null) {
                val netBytes = (netR ?: 0L) + (netW ?: 0L)
                val tputStr = if (avgTput != null) " (${FormatUtils.formatThroughput(avgTput)})" else ""
                details.add("Network: ${FormatUtils.formatFileSize(netBytes)}$tputStr")
            }
            if (details.isNotEmpty()) {
                sb.appendLine("  * ${details.joinToString(" | ")}")
            }

            if (count > 1) {
                execs.forEachIndexed { idx, exec ->
                    val runDetails = mutableListOf<String>()
                    exec.memoryUsageBytes?.let { runDetails.add("Peak: ${MemoryUtils.formatMemoryBytes(it)}") }
                    exec.totalMemoryBytes?.let { runDetails.add("Tot: ${MemoryUtils.formatMemoryBytes(it)}") }
                    val bRead = exec.bytesRead
                    val bWritten = exec.bytesWritten
                    if (bRead != null || bWritten != null) {
                        runDetails.add("IO: R ${FormatUtils.formatFileSize(bRead ?: 0L)} / W ${FormatUtils.formatFileSize(bWritten ?: 0L)}")
                    }
                    val netRead = exec.networkBytesRead
                    val netWritten = exec.networkBytesWritten
                    val tput = exec.throughputBytesPerSec
                    if (netRead != null || netWritten != null || tput != null) {
                        val net = (netRead ?: 0L) + (netWritten ?: 0L)
                        val tp = if (tput != null) " @ ${FormatUtils.formatThroughput(tput)}" else ""
                        runDetails.add("Net: ${FormatUtils.formatFileSize(net)}$tp")
                    }
                    val runExtra = if (runDetails.isNotEmpty()) " (${runDetails.joinToString(", ")})" else ""
                    sb.appendLine("    #${idx + 1}: ${exec.durationMs} ms (${formatDuration(exec.durationMs)})$runExtra")
                }
            }
        }
    }

    if (job.result != null) {
        sb.appendLine()
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("RESULT")
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine(job.result)
    }

    if (job.errorMessage != null) {
        sb.appendLine()
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("ERROR")
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine(job.errorMessage)
    }

    if (logs.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("================================================================================")
        sb.appendLine("CONSOLE LOGS (${logs.size} lines)")
        sb.appendLine("================================================================================")
        sb.appendLine(logs.joinToString("\n"))
    }

    return sb.toString()
}
