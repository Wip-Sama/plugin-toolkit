package org.wip.plugintoolkit.shared.components.plugin

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
    capabilityBreakdownLabel: String
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

    if (metrics != null && metrics.capabilityMetrics.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine(capabilityBreakdownLabel.uppercase())
        sb.appendLine("--------------------------------------------------------------------------------")
        val durations = metrics.totalDurationPerCapability
        val counts = metrics.executionCountPerCapability
        durations.forEach { (capName, ms) ->
            val count = counts[capName] ?: 1
            val countStr = if (count > 1) " (x$count)" else ""
            val pctStr = if (durationMs != null && durationMs > 0L) {
                " [${((ms.toDouble() / durationMs) * 100.0).roundToInt()}%]"
            } else ""
            sb.appendLine("- $capName: $ms ms (${formatDuration(ms)})$countStr$pctStr")
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
