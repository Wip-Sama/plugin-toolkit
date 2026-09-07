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
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.MemoryUtils
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.job_capability_breakdown_title
import plugintoolkit.composeapp.generated.resources.job_completed_at_label
import plugintoolkit.composeapp.generated.resources.job_duration_label
import plugintoolkit.composeapp.generated.resources.job_execution_count_format
import plugintoolkit.composeapp.generated.resources.job_execution_info_title
import plugintoolkit.composeapp.generated.resources.job_memory_label
import plugintoolkit.composeapp.generated.resources.job_started_at_label
import kotlin.math.roundToInt
import kotlin.time.Clock

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ExecutionInfoSection(
    job: BackgroundJob,
    modifier: Modifier = Modifier
) {
    var ticker by remember { mutableStateOf(0L) }
    LaunchedEffect(job.id, job.status) {
        if (job.status == JobStatus.Running || job.status == JobStatus.Queued) {
            while (true) {
                delay(1000)
                ticker++
            }
        }
    }

    val metrics = job.executionMetrics
    val startedAt = metrics?.startedAt ?: job.startedAt
    val completedAt = metrics?.completedAt ?: job.completedAt
    val isRunning = job.status == JobStatus.Running || job.status == JobStatus.Queued

    val durationMs = if (isRunning && startedAt != null) {
        if (ticker >= 0) {
            (Clock.System.now() - startedAt).inWholeMilliseconds.coerceAtLeast(0L)
        } else 0L
    } else {
        metrics?.totalDurationMs ?: run {
            if (startedAt != null) {
                val end = completedAt ?: Clock.System.now()
                (end - startedAt).inWholeMilliseconds.coerceAtLeast(0L)
            } else null
        }
    }

    val memoryUsage = if (isRunning) {
        if (ticker >= 0) MemoryUtils.getCurrentMemoryUsageBytes() else null
    } else {
        metrics?.memoryUsageBytes
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
                label = stringResource(Res.string.job_memory_label),
                value = if (memoryUsage != null) MemoryUtils.formatMemoryBytes(memoryUsage) else "—"
            )
        }

        // Per-capability breakdown
        val capDurations = metrics?.totalDurationPerCapability ?: emptyMap()
        val capCounts = metrics?.executionCountPerCapability ?: emptyMap()
        if (capDurations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
            Text(
                text = stringResource(Res.string.job_capability_breakdown_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))

            val totalMs = durationMs?.coerceAtLeast(1L) ?: capDurations.values.sum().coerceAtLeast(1L)
            Column(
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                modifier = Modifier.fillMaxWidth()
            ) {
                capDurations.forEach { (capName, ms) ->
                    val count = capCounts[capName] ?: 1
                    val fraction = (ms.toFloat() / totalMs).coerceIn(0f, 1f)
                    val percent = (fraction * 100).roundToInt()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerLowest,
                                ToolkitTheme.shapes.small
                            )
                            .padding(ToolkitTheme.spacing.small)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = capName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (count > 1) {
                                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                                    Text(
                                        text = stringResource(Res.string.job_execution_count_format, count),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Text(
                                text = "${formatDuration(ms)} ($percent%)",
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
