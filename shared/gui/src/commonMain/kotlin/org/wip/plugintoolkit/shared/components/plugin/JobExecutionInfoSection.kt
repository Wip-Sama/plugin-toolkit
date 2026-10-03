package org.wip.plugintoolkit.shared.components.plugin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.DeveloperBoard
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.FormatUtils
import org.wip.plugintoolkit.core.utils.MemoryUtils
import org.wip.plugintoolkit.features.job.utils.ProcessMemoryUtils
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.CapabilityExecutionMetric
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.utils.ProcessGpuUtils
import org.wip.plugintoolkit.features.job.utils.ProcessCpuUtils
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.job_capability_breakdown_title
import plugintoolkit.composeapp.generated.resources.job_completed_at_label
import plugintoolkit.composeapp.generated.resources.job_duration_label
import plugintoolkit.composeapp.generated.resources.job_execution_count_format
import plugintoolkit.composeapp.generated.resources.job_execution_info_title
import plugintoolkit.composeapp.generated.resources.job_execution_run_format
import plugintoolkit.composeapp.generated.resources.job_memory_label
import plugintoolkit.composeapp.generated.resources.job_metric_io
import plugintoolkit.composeapp.generated.resources.job_metric_network
import plugintoolkit.composeapp.generated.resources.job_peak_memory_label
import plugintoolkit.composeapp.generated.resources.job_started_at_label
import plugintoolkit.composeapp.generated.resources.job_total_memory_label
import plugintoolkit.composeapp.generated.resources.job_vram_label
import plugintoolkit.composeapp.generated.resources.job_actual_vram_label
import plugintoolkit.composeapp.generated.resources.job_peak_vram_label
import plugintoolkit.composeapp.generated.resources.job_system_vram_label
import plugintoolkit.composeapp.generated.resources.job_cpu_label
import plugintoolkit.composeapp.generated.resources.job_avg_cpu_label
import plugintoolkit.composeapp.generated.resources.job_peak_cpu_label
import plugintoolkit.composeapp.generated.resources.job_view_individual_runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.time.Clock

private data class LiveResourceSnapshot(
    val memoryBytes: Long,
    val vramBytes: Long,
    val cpuPercent: Double?,
    val systemVramUsedBytes: Long? = null,
    val systemVramTotalBytes: Long? = null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExecutionInfoSection(
    job: BackgroundJob,
    modifier: Modifier = Modifier
) {
    val metrics = job.executionMetrics
    val startedAt = metrics?.startedAt ?: job.startedAt
    val completedAt = metrics?.completedAt ?: job.completedAt
    val isRunning = job.status == JobStatus.Running || job.status == JobStatus.Queued

    var liveSnapshot by remember(job.id) { mutableStateOf<LiveResourceSnapshot?>(null) }
    var runningPeakMemory by remember(job.id) { mutableStateOf<Long?>(null) }
    var runningPeakVram by remember(job.id) { mutableStateOf<Long?>(null) }
    var runningPeakCpu by remember(job.id) { mutableStateOf<Double?>(null) }
    var liveDurationMs by remember(job.id) { mutableStateOf<Long?>(null) }

    val isVramSupported = remember { ProcessGpuUtils.isVramMonitoringSupported() }

    LaunchedEffect(job.id, isRunning) {
        if (isRunning) {
            while (isActive) {
                val start = job.executionMetrics?.startedAt ?: job.startedAt
                if (start != null) {
                    liveDurationMs = (Clock.System.now() - start).inWholeMilliseconds.coerceAtLeast(0L)
                }
                val snap = withContext(Dispatchers.Default) {
                    val jvmMem = MemoryUtils.getCurrentMemoryUsageBytes()
                    val procMem = ProcessMemoryUtils.getAllDescendantsMemoryBytes()
                    val vram = ProcessGpuUtils.getTotalTrackedVramBytes()
                    val cpu = ProcessCpuUtils.getProcessCpuUsagePercent()
                    val sysVram = ProcessGpuUtils.getSystemVramBytes()
                    LiveResourceSnapshot(
                        memoryBytes = jvmMem + procMem,
                        vramBytes = vram,
                        cpuPercent = cpu,
                        systemVramUsedBytes = sysVram?.first,
                        systemVramTotalBytes = sysVram?.second
                    )
                }
                liveSnapshot = snap
                if (snap.memoryBytes > (runningPeakMemory ?: 0L)) {
                    runningPeakMemory = snap.memoryBytes
                }
                if (snap.vramBytes > (runningPeakVram ?: 0L)) {
                    runningPeakVram = snap.vramBytes
                }
                if (snap.cpuPercent != null && snap.cpuPercent > (runningPeakCpu ?: 0.0)) {
                    runningPeakCpu = snap.cpuPercent
                }
                delay(1000)
            }
        }
    }

    val durationMs = if (isRunning) {
        liveDurationMs ?: startedAt?.let { (Clock.System.now() - it).inWholeMilliseconds.coerceAtLeast(0L) }
    } else {
        metrics?.totalDurationMs ?: run {
            if (startedAt != null) {
                val end = completedAt ?: Clock.System.now()
                (end - startedAt).inWholeMilliseconds.coerceAtLeast(0L)
            } else null
        }
    }

    val currentMem = liveSnapshot?.memoryBytes
    val peakMemoryUsage = if (isRunning) {
        val recordedJobPeak = metrics?.memoryUsageBytes ?: 0L
        maxOf(runningPeakMemory ?: 0L, recordedJobPeak, currentMem ?: 0L).takeIf { it > 0L }
    } else {
        metrics?.memoryUsageBytes
    }

    val totalMemoryUsage = if (isRunning) {
        val completedCapMem = metrics?.capabilityMetrics?.mapNotNull { it.memoryUsageBytes }?.sum() ?: 0L
        if (completedCapMem > 0L) {
            completedCapMem + (currentMem ?: 0L)
        } else {
            currentMem
        }
    } else {
        metrics?.effectiveTotalMemoryUsageBytes ?: metrics?.totalMemoryUsageBytes ?: metrics?.memoryUsageBytes
    }

    val currentVram = liveSnapshot?.vramBytes
    val peakVramUsage = if (isRunning) {
        val recordedJobPeakVram = metrics?.peakProcessVramBytes ?: 0L
        maxOf(runningPeakVram ?: 0L, recordedJobPeakVram, currentVram ?: 0L)
    } else {
        metrics?.peakProcessVramBytes
    }

    val avgCpuUsage = if (isRunning) {
        liveSnapshot?.cpuPercent
    } else {
        metrics?.avgProcessCpuPercent
    }

    val peakCpuUsage = if (isRunning) {
        runningPeakCpu ?: liveSnapshot?.cpuPercent
    } else {
        metrics?.peakProcessCpuPercent ?: metrics?.avgProcessCpuPercent
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(Res.string.job_execution_info_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
            modifier = Modifier.fillMaxWidth()
        ) {
            MetricTile(
                icon = Icons.Default.PlayCircleOutline,
                label = stringResource(Res.string.job_started_at_label),
                value = formatTimestamp(startedAt)
            )
            MetricTile(
                icon = Icons.Default.StopCircle,
                label = stringResource(Res.string.job_completed_at_label),
                value = if (isRunning) "..." else formatTimestamp(completedAt)
            )
            MetricTile(
                icon = Icons.Default.Timer,
                label = stringResource(Res.string.job_duration_label),
                value = if (durationMs != null) formatDuration(durationMs) else "—"
            )
            MetricTile(
                icon = Icons.Default.Memory,
                label = stringResource(Res.string.job_peak_memory_label),
                value = if (peakMemoryUsage != null) MemoryUtils.formatMemoryBytes(peakMemoryUsage) else "—"
            )
            MetricTile(
                icon = Icons.Default.Storage,
                label = stringResource(Res.string.job_total_memory_label),
                value = if (totalMemoryUsage != null) MemoryUtils.formatMemoryBytes(totalMemoryUsage) else "—"
            )
            if (isVramSupported || peakVramUsage != null || metrics?.maxSystemVramBytes != null) {
                MetricTile(
                    icon = Icons.Default.DeveloperBoard,
                    label = stringResource(Res.string.job_peak_vram_label),
                    value = MemoryUtils.formatMemoryBytes(peakVramUsage ?: 0L)
                )
                val actualVramText = if (isRunning) {
                    val proc = MemoryUtils.formatMemoryBytes(currentVram ?: 0L)
                    val sys = liveSnapshot?.systemVramUsedBytes?.let { " (Sys: ${MemoryUtils.formatMemoryBytes(it)})" } ?: ""
                    "$proc$sys"
                } else {
                    val sysMax = metrics?.maxSystemVramBytes
                    if (sysMax != null) {
                        "Sys: ${MemoryUtils.formatMemoryBytes(sysMax)}"
                    } else {
                        MemoryUtils.formatMemoryBytes(peakVramUsage ?: 0L)
                    }
                }
                MetricTile(
                    icon = Icons.Default.DeveloperBoard,
                    label = stringResource(Res.string.job_actual_vram_label),
                    value = actualVramText
                )
            }
            if (avgCpuUsage != null || peakCpuUsage != null) {
                MetricTile(
                    icon = Icons.Default.Speed,
                    label = stringResource(Res.string.job_avg_cpu_label),
                    value = avgCpuUsage?.let { "$it%" } ?: "—"
                )
                MetricTile(
                    icon = Icons.Default.Speed,
                    label = stringResource(Res.string.job_peak_cpu_label),
                    value = peakCpuUsage?.let { "$it%" } ?: "—"
                )
            }
        }

        // Per-capability breakdown
        val metricsByCapability = metrics?.capabilityMetrics?.groupBy { it.capabilityName } ?: emptyMap()
        if (metricsByCapability.isNotEmpty()) {
            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
            Text(
                text = stringResource(Res.string.job_capability_breakdown_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))

            val totalAllCapMs = metricsByCapability.values.sumOf { execs -> execs.sumOf { it.durationMs } }
            val totalMs = durationMs?.coerceAtLeast(1L) ?: totalAllCapMs.coerceAtLeast(1L)

            Column(
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                modifier = Modifier.fillMaxWidth()
            ) {
                metricsByCapability.forEach { (capName, executions) ->
                    CapabilityBreakdownCard(
                        capabilityName = capName,
                        executions = executions,
                        totalJobDurationMs = totalMs
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilityBreakdownCard(
    capabilityName: String,
    executions: List<CapabilityExecutionMetric>,
    totalJobDurationMs: Long,
    modifier: Modifier = Modifier
) {
    val count = executions.size
    val totalCapMs = executions.sumOf { it.durationMs }
    val fraction = (totalCapMs.toFloat() / totalJobDurationMs).coerceIn(0f, 1f)
    val percent = (fraction * 100).roundToInt()

    val peakMemory = executions.mapNotNull { it.memoryUsageBytes }.maxOrNull()
    val totalMemory = executions.mapNotNull { it.totalMemoryBytes ?: it.memoryUsageBytes }.sum().takeIf { it > 0L }
    val peakVram = executions.mapNotNull { it.peakVramBytes }.maxOrNull()
    val avgCpu = executions.mapNotNull { it.avgProcessCpuPercent }.takeIf { it.isNotEmpty() }?.let {
        ((it.sum() / it.size) * 10.0).roundToInt() / 10.0
    }
    val peakCpu = executions.mapNotNull { it.peakProcessCpuPercent }.maxOrNull()
    val totalBytesRead = executions.mapNotNull { it.bytesRead }.sum().takeIf { it > 0L }
    val totalBytesWritten = executions.mapNotNull { it.bytesWritten }.sum().takeIf { it > 0L }
    val totalNetRead = executions.mapNotNull { it.networkBytesRead }.sum().takeIf { it > 0L }
    val totalNetWritten = executions.mapNotNull { it.networkBytesWritten }.sum().takeIf { it > 0L }
    val throughputs = executions.mapNotNull { it.throughputBytesPerSec }.filter { it > 0L }
    val avgThroughput = if (throughputs.isNotEmpty()) throughputs.sum() / throughputs.size else null

    var isDrawerExpanded by remember(capabilityName, count) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainerLowest,
                ToolkitTheme.shapes.small
            )
            .padding(ToolkitTheme.spacing.small)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (count > 1) {
                        Modifier.clickable { isDrawerExpanded = !isDrawerExpanded }
                    } else {
                        Modifier
                    }
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = capabilityName,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ToolkitTheme.codeFontFamily,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (count > 1) {
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                    Text(
                        text = stringResource(Res.string.job_execution_count_format, count),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                    Icon(
                        imageVector = if (isDrawerExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(Res.string.job_view_individual_runs),
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconExtraSmall),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
            Text(
                text = "${formatDuration(totalCapMs)} ($percent%)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraSmall))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(ToolkitTheme.dimensions.capabilityProgressBarHeight)
                .clip(MaterialTheme.shapes.extraSmall),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        // Metrics row (Peak Memory, Total Memory, VRAM, CPU, File I/O, Network Throughput)
        val hasMetrics = peakMemory != null || totalMemory != null || peakVram != null || avgCpu != null || peakCpu != null || totalBytesRead != null || totalBytesWritten != null || totalNetRead != null || totalNetWritten != null || avgThroughput != null
        if (hasMetrics) {
            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.smallMedium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (peakMemory != null) {
                    Text(
                        text = "${stringResource(Res.string.job_peak_memory_label)}: ${MemoryUtils.formatMemoryBytes(peakMemory)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (totalMemory != null) {
                    Text(
                        text = "${stringResource(Res.string.job_total_memory_label)}: ${MemoryUtils.formatMemoryBytes(totalMemory)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (peakVram != null && peakVram > 0L) {
                    Text(
                        text = "${stringResource(Res.string.job_peak_vram_label)}: ${MemoryUtils.formatMemoryBytes(peakVram)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (avgCpu != null || peakCpu != null) {
                    val cpuStr = buildString {
                        append("${stringResource(Res.string.job_cpu_label)}: ")
                        if (avgCpu != null) append("$avgCpu%")
                        if (peakCpu != null) append(" [Peak $peakCpu%]")
                    }
                    Text(
                        text = cpuStr,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (totalBytesRead != null || totalBytesWritten != null) {
                    val readStr = FormatUtils.formatFileSize(totalBytesRead ?: 0L)
                    val writeStr = FormatUtils.formatFileSize(totalBytesWritten ?: 0L)
                    Text(
                        text = "${stringResource(Res.string.job_metric_io)}: R: $readStr / W: $writeStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (totalNetRead != null || totalNetWritten != null || avgThroughput != null) {
                    val netBytes = (totalNetRead ?: 0L) + (totalNetWritten ?: 0L)
                    val netStr = FormatUtils.formatFileSize(netBytes)
                    val tputStr = if (avgThroughput != null) " (${FormatUtils.formatThroughput(avgThroughput)})" else ""
                    Text(
                        text = "${stringResource(Res.string.job_metric_network)}: $netStr$tputStr",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Expandable drawer for individual executions when capability ran multiple times
        if (count > 1) {
            AnimatedVisibility(visible = isDrawerExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = ToolkitTheme.spacing.small)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            ToolkitTheme.shapes.small
                        )
                        .padding(ToolkitTheme.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
                ) {
                    Text(
                        text = stringResource(Res.string.job_view_individual_runs),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    executions.forEachIndexed { index, exec ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(Res.string.job_execution_run_format, index + 1),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatDuration(exec.durationMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                                val memBytes = exec.memoryUsageBytes
                                if (memBytes != null) {
                                    Text(
                                        text = "Peak: ${MemoryUtils.formatMemoryBytes(memBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                val totBytes = exec.totalMemoryBytes
                                if (totBytes != null) {
                                    Text(
                                        text = "Tot: ${MemoryUtils.formatMemoryBytes(totBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                val execVram = exec.peakVramBytes
                                if (execVram != null && execVram > 0L) {
                                    Text(
                                        text = "VRAM: ${MemoryUtils.formatMemoryBytes(execVram)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                val execAvgCpu = exec.avgProcessCpuPercent
                                val execPeakCpu = exec.peakProcessCpuPercent
                                if (execAvgCpu != null || execPeakCpu != null) {
                                    val cpuStr = buildString {
                                        if (execAvgCpu != null) append("CPU: $execAvgCpu%")
                                        if (execPeakCpu != null) append(" (pk: $execPeakCpu%)")
                                    }
                                    Text(
                                        text = cpuStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                val bRead = exec.bytesRead
                                val bWritten = exec.bytesWritten
                                if (bRead != null || bWritten != null) {
                                    val r = FormatUtils.formatFileSize(bRead ?: 0L)
                                    val w = FormatUtils.formatFileSize(bWritten ?: 0L)
                                    Text(
                                        text = "IO: $r/$w",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                                val netRead = exec.networkBytesRead
                                val netWritten = exec.networkBytesWritten
                                val tputVal = exec.throughputBytesPerSec
                                if (netRead != null || netWritten != null || tputVal != null) {
                                    val totalNet = (netRead ?: 0L) + (netWritten ?: 0L)
                                    val tput = if (tputVal != null) " @ ${FormatUtils.formatThroughput(tputVal)}" else ""
                                    Text(
                                        text = "Net: ${FormatUtils.formatFileSize(totalNet)}$tput",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = ToolkitTheme.shapes.small,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.smallMedium, vertical = ToolkitTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
