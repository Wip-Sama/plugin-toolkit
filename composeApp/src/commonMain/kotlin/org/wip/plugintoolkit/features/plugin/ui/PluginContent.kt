package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.api.PluginResponse
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.ui.StatusBadge
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginViewModel
import org.wip.plugintoolkit.shared.components.GlassCard
import org.wip.plugintoolkit.shared.components.SectionHeader
import org.wip.plugintoolkit.shared.components.plugin.DynamicParameterInput
import org.wip.plugintoolkit.shared.components.plugin.ResponseView
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_clear
import plugintoolkit.composeapp.generated.resources.action_delete
import plugintoolkit.composeapp.generated.resources.plugin_execute_capability
import plugintoolkit.composeapp.generated.resources.plugin_execute_capability_running
import plugintoolkit.composeapp.generated.resources.plugin_executing_progress
import plugintoolkit.composeapp.generated.resources.plugin_execution_id_format
import plugintoolkit.composeapp.generated.resources.plugin_execution_results
import plugintoolkit.composeapp.generated.resources.plugin_id_format
import plugintoolkit.composeapp.generated.resources.plugin_mem_format
import plugintoolkit.composeapp.generated.resources.plugin_no_parameters
import plugintoolkit.composeapp.generated.resources.plugin_save_results
import plugintoolkit.composeapp.generated.resources.plugin_select_capability_hint
import plugintoolkit.composeapp.generated.resources.plugin_tester_title

@Composable
fun PluginContent(
    viewModel: PluginViewModel,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxHeight()) {
        val selectedCapability = viewModel.selectedCapability
        val activeJobs by viewModel.activeJobs.collectAsState(initial = emptyList())
        val endedJobs by viewModel.endedJobs.collectAsState(initial = emptyList())
        val allJobs = remember(activeJobs, endedJobs) { activeJobs + endedJobs }

        val jobProgressMap by viewModel.jobProgress.collectAsState(initial = emptyMap())
        val capabilityJobs = remember(allJobs, viewModel.selectedPlugin, selectedCapability) {
            val pluginId = viewModel.selectedPlugin?.getManifest()?.plugin?.id
            val capName = selectedCapability?.name
            if (pluginId == null || capName == null) emptyList()
            else allJobs.filter { it.pluginId == pluginId && it.capabilityName == capName }
                .sortedByDescending { it.enqueuedAt }
        }

        if (selectedCapability == null) {
            EmptyState(stringResource(Res.string.plugin_select_capability_hint))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(32.dp)
            ) {
                // Tester Area
                CapabilityTester(
                    capability = selectedCapability,
                    parameterValues = viewModel.parameterValues,
                    onParameterChange = { name, value -> viewModel.updateParameter(name, value) },
                    saveResults = viewModel.saveResults,
                    onSaveResultsChange = { viewModel.saveResults = it },
                    activeJobs = capabilityJobs.filter { it.status == JobStatus.Running || it.status == JobStatus.Queued },
                    onExecute = { viewModel.executeCapability() }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // History / Results Area
                val visibleJobs = capabilityJobs

                if (visibleJobs.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(Res.string.plugin_execution_results),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        TextButton(onClick = { viewModel.clearCapabilityHistory() }) {
                            Icon(Icons.Default.ClearAll, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(Res.string.action_clear))
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        visibleJobs.forEach { job ->
                            JobResultItem(
                                job = job,
                                progress = jobProgressMap[job.id] ?: 0f,
                                onDelete = { 
                                    if (job.status == JobStatus.Completed || job.status == JobStatus.Failed || job.status == JobStatus.Cancelled) {
                                        viewModel.removeEndedJob(job.id)
                                    } else {
                                        viewModel.removeJob(job.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PluginHeader(manifest: PluginManifest) {
    Column {
        Text(
            manifest.plugin.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            manifest.plugin.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge { Text(stringResource(Res.string.plugin_id_format, manifest.plugin.id)) }
            Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
                Text(stringResource(Res.string.plugin_mem_format, manifest.requirements.minMemoryMb))
            }
        }
    }
}

@Composable
fun CapabilityItem(
    capability: Capability,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(
            alpha = 0.3f
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text(capability.name, fontWeight = FontWeight.Bold)
            Text(
                capability.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CapabilityTester(
    capability: Capability,
    parameterValues: Map<String, String>,
    onParameterChange: (String, String) -> Unit,
    saveResults: Boolean,
    onSaveResultsChange: (Boolean) -> Unit,
    activeJobs: List<BackgroundJob>,
    onExecute: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(
                title = stringResource(Res.string.plugin_tester_title, capability.name),
                icon = Icons.Default.PlayArrow,
                modifier = Modifier.weight(1f)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.plugin_save_results),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = saveResults,
                    onCheckedChange = onSaveResultsChange,
                    modifier = Modifier.scale(0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (capability.parameters != null) {
            for ((name, meta) in capability.parameters!!) {
                DynamicParameterInput(
                    name = name,
                    metadata = meta,
                    value = parameterValues[name] ?: "",
                    onValueChange = { onParameterChange(name, it) }
                )
            }
        }

        if (capability.parameters.isNullOrEmpty()) {
            Text(stringResource(Res.string.plugin_no_parameters), style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onExecute,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            if (activeJobs.isNotEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(stringResource(Res.string.plugin_execute_capability_running, activeJobs.size))
            } else {
                Text(stringResource(Res.string.plugin_execute_capability))
            }
        }
    }
}

@Composable
fun JobResultItem(
    job: BackgroundJob,
    progress: Float = 0f,
    onDelete: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.plugin_execution_id_format, job.id),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(job.status)
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.action_delete),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (job.status) {
                JobStatus.Running, JobStatus.Queued -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small),
                            color = ProgressIndicatorDefaults.linearColor,
                            trackColor = ProgressIndicatorDefaults.linearTrackColor,
                            strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(Res.string.plugin_executing_progress, (progress * 100).toInt()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                JobStatus.Completed -> {
                    val jsonResult = remember(job.result) {
                        job.result?.let {
                            try {
                                Json.parseToJsonElement(it)
                            } catch (e: Exception) {
                                JsonNull
                            }
                        } ?: JsonNull
                    }
                    ResponseView(PluginResponse(result = jsonResult))
                }

                JobStatus.Failed, JobStatus.Cancelled -> {
                    ErrorView(
                        job.errorMessage ?: stringResource(
                            Res.string.plugin_execution_id_format,
                            job.status.name
                        )
                    )
                }

                else -> {}
            }
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Inbox,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun ErrorView(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
