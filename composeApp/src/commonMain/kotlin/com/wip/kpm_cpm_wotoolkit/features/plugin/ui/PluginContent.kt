package com.wip.kpm_cpm_wotoolkit.features.plugin.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun PluginContent(
    viewModel: PluginViewModel,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxHeight()) {
        val selectedCapability = viewModel.selectedCapability
        val activeJobs by viewModel.activeJobs.collectAsState(initial = emptyList<BackgroundJob>())
        val currentJob = activeJobs.find { it.id == viewModel.lastEnqueuedJobId }

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
                Column(modifier = Modifier.fillMaxWidth()) {
                    val isJobActive = currentJob?.status == JobStatus.Running || currentJob?.status == JobStatus.Queued
                    
                    CapabilityTester(
                        capability = selectedCapability,
                        parameterValues = viewModel.parameterValues,
                        isExecuting = isJobActive,
                        currentJob = currentJob,
                        onExecute = { viewModel.executeCapability() }
                    )

                    AnimatedVisibility(
                        visible = viewModel.executionResult != null && !isJobActive,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        viewModel.executionResult?.let { result ->
                            Column {
                                Spacer(modifier = Modifier.height(24.dp))
                                if (result.isSuccess) {
                                    ResponseView(result.getOrThrow())
                                } else {
                                    ErrorView(result.exceptionOrNull()?.message ?: "Unknown error")
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
    isExecuting: Boolean,
    currentJob: BackgroundJob?,
    onExecute: () -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(title = "Capability Tester: ${capability.name}", icon = Icons.Default.PlayArrow)
            if (currentJob != null) {
                StatusBadge(currentJob.status)
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
        
        if (currentJob != null && currentJob.status == JobStatus.Running) {
            val currentProgress by currentJob.progress.collectAsState()
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
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
        }

        Button(
            onClick = onExecute,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isExecuting,
            shape = MaterialTheme.shapes.medium
        ) {
            if (isExecuting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Execute Capability")
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
