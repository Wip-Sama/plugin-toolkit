package com.wip.kpm_cpm_wotoolkit.features.plugin.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
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
import com.wip.kpm_cpm_wotoolkit.shared.components.SectionHeader
import com.wip.kpm_cpm_wotoolkit.shared.components.GlassCard
import com.wip.kpm_cpm_wotoolkit.shared.components.plugin.ResponseView
import com.wip.kpm_cpm_wotoolkit.shared.components.plugin.DynamicParameterInput
import com.wip.plugin.api.Capability
import com.wip.plugin.api.PluginManifest

@Composable
fun PluginContent(
    viewModel: PluginViewModel,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxHeight()) {
        val selectedCapability = viewModel.selectedCapability
        val allJobs by viewModel.activeJobs.collectAsState(initial = emptyList<BackgroundJob>())
        
        val capabilityJobs = remember(allJobs, viewModel.selectedPlugin, selectedCapability) {
            val pluginId = viewModel.selectedPlugin?.getManifest()?.module?.id
            val capName = selectedCapability?.name
            if (pluginId == null || capName == null) emptyList<BackgroundJob>()
            else allJobs.filter { it.pluginId == pluginId && it.capabilityName == capName }
                .sortedByDescending { it.enqueuedAt }
        }

        if (selectedCapability == null) {
            EmptyState("Select a capability to begin testing")
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
                    saveResults = viewModel.saveResults,
                    onSaveResultsChange = { viewModel.saveResults = it },
                    activeJobs = capabilityJobs.filter { it.status == JobStatus.Running || it.status == JobStatus.Queued },
                    onExecute = { viewModel.executeCapability() }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // History / Results Area
                val visibleJobs = if (viewModel.saveResults) {
                    capabilityJobs
                } else {
                    capabilityJobs.take(1) // Only show latest if not saving
                }

                if (visibleJobs.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (viewModel.saveResults) "Execution History" else "Latest Result",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        
                        if (viewModel.saveResults) {
                            TextButton(onClick = { viewModel.clearCapabilityHistory() }) {
                                Icon(Icons.Default.ClearAll, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear")
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        visibleJobs.forEach { job ->
                            JobResultItem(job)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ModuleHeader(manifest: PluginManifest) {
    Column {
        Text(
            manifest.module.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            manifest.module.description,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge { Text("ID: ${manifest.module.id}") }
            Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer) { 
                Text("Mem: ${manifest.requirements.minMemoryMb}MB") 
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
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
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
                title = "Capability Tester: ${capability.name}", 
                icon = Icons.Default.PlayArrow,
                modifier = Modifier.weight(1f)
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Save Results", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
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
                    onValueChange = { (parameterValues as MutableMap)[name] = it }
                )
            }
        }
        
        if (capability.parameters.isNullOrEmpty()) {
            Text("No parameters required for this capability.", style = MaterialTheme.typography.bodyMedium)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onExecute,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            if (activeJobs.isNotEmpty()) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Execute Capability (${activeJobs.size} running)")
            } else {
                Text("Execute Capability")
            }
        }
    }
}

@Composable
fun JobResultItem(job: BackgroundJob) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Execution ${job.id}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                StatusBadge(job.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (job.status == JobStatus.Running || job.status == JobStatus.Queued) {
                val currentProgress by job.progress.collectAsState()
                Column(modifier = Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(
                        progress = { currentProgress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small),
                        color = ProgressIndicatorDefaults.linearColor,
                        trackColor = ProgressIndicatorDefaults.linearTrackColor,
                        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Executing... ${(currentProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else if (job.status == JobStatus.Completed) {
                val jsonResult = remember(job.result) {
                    job.result?.let { 
                        try { kotlinx.serialization.json.Json.parseToJsonElement(it) } catch (e: Exception) { kotlinx.serialization.json.JsonNull }
                    } ?: kotlinx.serialization.json.JsonNull
                }
                ResponseView(com.wip.plugin.api.PluginResponse(result = jsonResult))
            } else if (job.status == JobStatus.Failed || job.status == JobStatus.Cancelled) {
                ErrorView(job.errorMessage ?: "Execution ${job.status.name}")
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
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
