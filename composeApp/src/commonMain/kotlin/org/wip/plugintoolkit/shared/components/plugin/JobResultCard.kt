package org.wip.plugintoolkit.shared.components.plugin

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.job.ui.StatusBadge
import org.wip.plugintoolkit.shared.components.GlassCard
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_delete
import plugintoolkit.composeapp.generated.resources.flow_console_logs_title
import plugintoolkit.composeapp.generated.resources.flow_output_results_title
import plugintoolkit.composeapp.generated.resources.flow_run_id_label
import plugintoolkit.composeapp.generated.resources.flow_triggered_label
import plugintoolkit.composeapp.generated.resources.plugin_executing_progress
import plugintoolkit.composeapp.generated.resources.plugin_execution_id_format
import plugintoolkit.composeapp.generated.resources.*

@Composable
fun JobResultCard(
    job: BackgroundJob,
    progress: Float,
    logs: List<String>,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    // Outer GlassCard with animateContentSize enables extremely smooth expansion transitions!
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(ToolkitTheme.spacing.medium)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))

                    val title = if (job.type == JobType.Flow) {
                        stringResource(Res.string.flow_run_id_label, job.id.takeLast(12))
                    } else {
                        stringResource(Res.string.plugin_execution_id_format, job.id)
                    }

                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(Res.string.flow_triggered_label, job.enqueuedAt.toString()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(job.status)
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.action_delete),
                            modifier = Modifier.size(ToolkitTheme.dimensions.circularProgressSize),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }

                    // Only show expand button if there are details to show (success result, error, or logs)
                    val hasDetails = job.status == JobStatus.Completed ||
                            job.status == JobStatus.Failed ||
                            job.status == JobStatus.Cancelled ||
                            logs.isNotEmpty()

                    if (hasDetails) {
                        IconButton(
                            onClick = { expanded = !expanded },
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                        ) {
                            Icon(
                                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(Res.string.action_toggle_details),
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumLarge),
                                tint = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            // Progress bar for running jobs
            if (job.status == JobStatus.Running || job.status == JobStatus.Queued) {
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ToolkitTheme.dimensions.heightSmall)
                            .clip(MaterialTheme.shapes.small),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraSmall))
                    Text(
                        text = stringResource(Res.string.plugin_executing_progress, (progress * 100).toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Expanded Details Section
            if (expanded) {
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                HorizontalDivider(Modifier, DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

                when (job.status) {
                    JobStatus.Completed -> {
                        if (job.type == JobType.Flow) {
                            // Render flow parsed outputs beautifully
                            val outputsParsed = remember(job.result) {
                                try {
                                    job.result?.let {
                                        Json.decodeFromString<Map<String, JsonElement>>(it)
                                    } ?: emptyMap()
                                } catch (e: Exception) {
                                    emptyMap()
                                }
                            }

                            if (outputsParsed.isNotEmpty()) {
                                Text(
                                    text = stringResource(Res.string.flow_output_results_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = ToolkitTheme.opacity.divider),
                                            MaterialTheme.shapes.medium
                                        )
                                        .padding(ToolkitTheme.spacing.medium)
                                ) {
                                    outputsParsed.forEach { (portName, element) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = portName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = when (element) {
                                                    is JsonPrimitive -> {
                                                        if (element.isString) element.content else element.toString()
                                                    }

                                                    else -> element.toString()
                                                },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                            }
                        } else {
                            // Render plugin capability output result
                            val jsonResult = remember(job.result) {
                                job.result?.let {
                                    try {
                                        Json.parseToJsonElement(it)
                                    } catch (e: Exception) {
                                        JsonNull
                                    }
                                } ?: JsonNull
                            }

                            Text(
                                text = "Execution Result",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = ToolkitTheme.opacity.divider),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = jsonResult.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(ToolkitTheme.spacing.medium)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                        }
                    }

                    JobStatus.Failed, JobStatus.Cancelled -> {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(ToolkitTheme.spacing.medium),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))
                                Text(
                                    text = job.errorMessage ?: job.status.name,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                    }

                    else -> {}
                }

                // Render Console logs if present
                if (logs.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.flow_console_logs_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ToolkitTheme.dimensions.containerHeightLarge)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.high),
                                MaterialTheme.shapes.medium
                            )
                            .padding(ToolkitTheme.spacing.medium)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)) {
                            logs.forEach { logLine ->
                                Text(
                                    text = logLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = when {
                                        logLine.contains(
                                            "[ERROR]",
                                            ignoreCase = true
                                        ) || logLine.startsWith("ERROR:") -> MaterialTheme.colorScheme.error

                                        logLine.contains(
                                            "[WARN]",
                                            ignoreCase = true
                                        ) || logLine.startsWith("WARN:") -> MaterialTheme.colorScheme.tertiary

                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
