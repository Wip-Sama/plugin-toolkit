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
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.ParameterRole
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginViewModel
import org.wip.plugintoolkit.shared.components.GlassCard
import org.wip.plugintoolkit.shared.components.SectionHeader
import org.wip.plugintoolkit.shared.components.plugin.DynamicParameterInput
import org.wip.plugintoolkit.shared.components.plugin.JobResultCard
import org.wip.plugintoolkit.shared.components.plugin.FileAccessChips
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_clear
import plugintoolkit.composeapp.generated.resources.plugin_execute_capability
import plugintoolkit.composeapp.generated.resources.plugin_execute_capability_running
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
            val pluginId = viewModel.selectedPlugin?.let { it.getManifest().getOrThrow().plugin.id }
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
                            JobResultCard(
                                job = job,
                                progress = jobProgressMap[job.id] ?: 0f,
                                logs = emptyList(),
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
            
            if (capability.semanticTypes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    capability.semanticTypes.forEach { type ->
                        Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                            Text(
                                "${type.namespace}/${type.name}",
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
            }
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
    val capabilityParameters = capability.parameters ?: emptyMap()
    val allParams = capabilityParameters.toMutableMap()
    
    if (saveResults && capability.returnType != org.wip.plugintoolkit.api.DataType.Primitive(org.wip.plugintoolkit.api.PrimitiveType.UNIT)) {
        allParams["_outputFile"] = org.wip.plugintoolkit.api.ParameterMetadata(
            description = "Optional file to save the capability's return result",
            type = org.wip.plugintoolkit.api.DataType.Primitive(org.wip.plugintoolkit.api.PrimitiveType.STRING),
            required = false,
            role = ParameterRole.OUTPUT_LOCATION,
            semanticTypes = listOf(org.wip.plugintoolkit.api.SemanticType("file", "output", null))
        )
    }

    val executionParameters = allParams.map { (name, meta) ->
        org.wip.plugintoolkit.shared.components.plugin.ExecutionParameter(
            name = name,
            value = parameterValues[name] ?: "",
            metadata = meta,
            onValueChange = { newValue -> onParameterChange(name, newValue) },
            enabled = meta.autogeneratedPattern == null
        )
    }

    org.wip.plugintoolkit.shared.components.plugin.ExecutionParametersCard(
        title = stringResource(Res.string.plugin_tester_title, capability.name),
        icon = Icons.Default.PlayArrow,
        description = null,
        fileAccess = capability.fileAccess,
        isDestructive = capability.fileAccess?.isDestructive == true || allParams.values.any { it.isDestructive },
        saveResults = saveResults,
        onSaveResultsChange = onSaveResultsChange,
        parameters = executionParameters
    )

    if (allParams.isNotEmpty()) {
            // Calculate autogenerated patterns
            val lastGeneratedPatterns = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
            LaunchedEffect(parameterValues.toMap()) {
                allParams.forEach { (name, meta) ->
                    val pattern = meta.autogeneratedPattern
                    if (!pattern.isNullOrEmpty()) {
                        var evaluated = pattern!!
                        var canEvaluate = true
                        val regex = Regex("\\{([^}]+)\\}")
                        regex.findAll(pattern).forEach { match ->
                            val depName = match.groupValues[1]
                            val depValue = parameterValues[depName]
                            if (!depValue.isNullOrEmpty()) {
                                evaluated = evaluated.replace(match.value, depValue)
                            } else {
                                canEvaluate = false
                            }
                        }
                        if (canEvaluate) {
                            // Normalize path
                            val parts = evaluated.replace("\\", "/").split("/")
                            val stack = mutableListOf<String>()
                            for (part in parts) {
                                if (part == "..") {
                                    if (stack.isNotEmpty() && stack.last() != "..") stack.removeLast()
                                    else stack.add(part)
                                } else if (part != "." && part.isNotEmpty()) {
                                    stack.add(part)
                                } else if (part.isEmpty() && stack.isEmpty()) {
                                    stack.add("")
                                }
                            }
                            val finalPath = stack.joinToString("/")
                            val normalized = if (evaluated.contains("\\")) finalPath.replace("/", "\\") else finalPath
                            
                            val currentValue = parameterValues[name]
                            val lastGenerated = lastGeneratedPatterns[name]
                            
                            // Only update if it's empty, or it equals the last generated value (meaning the user didn't modify it)
                            if (normalized != currentValue && (currentValue.isNullOrEmpty() || currentValue == lastGenerated)) {
                                onParameterChange(name, normalized)
                                lastGeneratedPatterns[name] = normalized
                            } else if (normalized == currentValue) {
                                // Keep lastGenerated in sync
                                lastGeneratedPatterns[name] = normalized
                            }
                        }
                    }
                }
            }
        }

        if (capability.parameters.isNullOrEmpty()) {
            Text(stringResource(Res.string.plugin_no_parameters), style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))

        val validationErrors = remember(parameterValues.toMap(), capability.parameters) {
            org.wip.plugintoolkit.features.plugin.utils.SettingsUtils.validateAllParameters(
                parameterValues = parameterValues,
                parameters = capability.parameters
            )
        }
        val isValid = validationErrors.isEmpty()

        Button(
            onClick = onExecute,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            enabled = isValid
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
        if (!isValid) {
            Text(
                text = "Fix parameter errors before executing",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp)
            )
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
