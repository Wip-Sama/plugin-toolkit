package com.wip.kpm_cpm_wotoolkit.features.plugin.ui

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
import com.wip.kpm_cpm_wotoolkit.features.job.model.BackgroundJob
import com.wip.kpm_cpm_wotoolkit.features.job.model.JobStatus
import com.wip.kpm_cpm_wotoolkit.features.job.ui.StatusBadge
import com.wip.kpm_cpm_wotoolkit.features.plugin.viewmodel.PluginViewModel
import com.wip.kpm_cpm_wotoolkit.shared.components.GlassCard
import com.wip.kpm_cpm_wotoolkit.shared.components.SectionHeader
import com.wip.kpm_cpm_wotoolkit.shared.components.plugin.DynamicParameterInput
import com.wip.kpm_cpm_wotoolkit.shared.components.plugin.ResponseView
import com.wip.plugin.api.Capability
import com.wip.plugin.api.PluginManifest
import kpm_cpm_wotoolkit.composeapp.generated.resources.Res
import kpm_cpm_wotoolkit.composeapp.generated.resources.action_clear
import kpm_cpm_wotoolkit.composeapp.generated.resources.action_delete
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_execute_capability
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_execute_capability_running
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_executing_progress
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_execution_id_format
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_execution_results
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_id_format
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_mem_format
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_no_parameters
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_save_results
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_select_capability_hint
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_tester_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun PluginContent(
    viewModel: PluginViewModel,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxHeight()) {
        val selectedCapability = viewModel.selectedCapability
        val allJobs by viewModel.activeJobs.collectAsState(initial = emptyList<BackgroundJob>())

        val jobProgressMap by viewModel.jobProgress.collectAsState(initial = emptyMap())
        val capabilityJobs = remember(allJobs, viewModel.selectedPlugin, selectedCapability) {
            val pluginId = viewModel.selectedPlugin?.getManifest()?.plugin?.id
            val capName = selectedCapability?.name
            if (pluginId == null || capName == null) emptyList<BackgroundJob>()
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
                                onDelete = { viewModel.removeJob(job.id) }
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

            if (job.status == JobStatus.Running || job.status == JobStatus.Queued) {
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
            } else if (job.status == JobStatus.Completed) {
                val jsonResult = remember(job.result) {
                    job.result?.let {
                        try {
                            kotlinx.serialization.json.Json.parseToJsonElement(it)
                        } catch (e: Exception) {
                            kotlinx.serialization.json.JsonNull
                        }
                    } ?: kotlinx.serialization.json.JsonNull
                }
                ResponseView(com.wip.plugin.api.PluginResponse(result = jsonResult))
            } else if (job.status == JobStatus.Failed || job.status == JobStatus.Cancelled) {
                ErrorView(job.errorMessage ?: stringResource(Res.string.plugin_execution_id_format, job.status.name))
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
