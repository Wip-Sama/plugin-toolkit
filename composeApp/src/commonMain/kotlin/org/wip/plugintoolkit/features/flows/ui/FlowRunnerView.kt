package org.wip.plugintoolkit.features.flows.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.api.format
import org.wip.plugintoolkit.core.model.localized
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.viewmodel.FlowViewModel
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.job.viewmodel.JobViewModel
import org.wip.plugintoolkit.shared.components.GlassCard
import org.wip.plugintoolkit.shared.components.SectionHeader
import org.wip.plugintoolkit.shared.components.plugin.JobResultCard
import org.wip.plugintoolkit.shared.components.sidebar.NavigationSidebar
import org.wip.plugintoolkit.shared.components.sidebar.SidebarElement
import org.wip.plugintoolkit.shared.components.sidebar.SidebarSectionData
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.flow_collected_automatically
import plugintoolkit.composeapp.generated.resources.flow_execute_button
import plugintoolkit.composeapp.generated.resources.flow_history_title
import plugintoolkit.composeapp.generated.resources.flow_inputs_title
import plugintoolkit.composeapp.generated.resources.flow_no_history
import plugintoolkit.composeapp.generated.resources.flow_outputs_title
import plugintoolkit.composeapp.generated.resources.flow_run_description
import plugintoolkit.composeapp.generated.resources.flow_run_title
import plugintoolkit.composeapp.generated.resources.flow_select_hint
import plugintoolkit.composeapp.generated.resources.flow_select_title
import plugintoolkit.composeapp.generated.resources.plugin_save_results

data class FlowParameter(
    val nodeId: Long,
    val type: ParameterType,
    val label: String,
    val portId: String,
    val dataType: DataType,
    val defaultValue: String
)

enum class ParameterType {
    INPUT, LOAD, SAVE, OUTPUT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowRunnerView(
    viewModel: FlowViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val jobViewModel: JobViewModel = koinInject()
    
    // Using a simple selection for flows to run
    var selectedFlowToRun by remember { mutableStateOf(state.flows.firstOrNull()) }

    LaunchedEffect(Unit) {
        viewModel.reloadFlows()
    }

    LaunchedEffect(state.flows) {
        if (selectedFlowToRun == null || !state.flows.any { it.name == selectedFlowToRun?.name }) {
            selectedFlowToRun = state.flows.firstOrNull()
        } else {
            selectedFlowToRun = state.flows.find { it.name == selectedFlowToRun?.name }
        }
    }
    
    Row(modifier = modifier.fillMaxSize()) {
        // Sidebar: Select Flow to Run (Standard NavigationSidebar)
        val flowElements = state.flows.map { flow ->
            SidebarElement(
                id = flow,
                icon = Icons.Default.PlayArrow,
                title = flow.name.localized
            )
        }

        NavigationSidebar(
            title = Res.string.flow_select_title.localized,
            bodySections = listOf(SidebarSectionData(title = null, elements = flowElements)),
            currentScreen = selectedFlowToRun,
            onScreenSelected = { selectedFlowToRun = it },
            isNavbarCollapsed = false,
            onToggleNavbar = {},
            canCollapse = false
        )

        // Main Area: Run and History
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(ToolkitTheme.spacing.extraLarge)
        ) {
            val currentFlow = selectedFlowToRun
            if (currentFlow == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(Res.string.flow_select_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // Parse parameters
                val flowParameters = remember(currentFlow) {
                    currentFlow.nodes.flatMap { node ->
                        when (node) {
                            is Node.FlowInputNode -> {
                                val outPort = node.outputs.firstOrNull()
                                if (outPort != null) {
                                    val inferredType = if (outPort.dataType is DataType.Primitive && outPort.dataType.primitiveType == PrimitiveType.ANY) {
                                        val connection = currentFlow.connections.find { it.sourceNodeId == node.id && it.sourcePortId == outPort.id }
                                        val targetNode = currentFlow.nodes.find { it.id == connection?.targetNodeId }
                                        val targetPort = targetNode?.inputs?.find { it.id == connection?.targetPortId }
                                        targetPort?.dataType ?: outPort.dataType
                                    } else outPort.dataType

                                    listOf(
                                        FlowParameter(
                                            nodeId = node.id,
                                            type = ParameterType.INPUT,
                                            label = outPort.name.replaceFirstChar { it.uppercase() },
                                            portId = outPort.id,
                                            dataType = inferredType,
                                            defaultValue = ""
                                        )
                                    )
                                } else emptyList()
                            }
                            is Node.SystemNode -> {
                                when (node.systemAction.lowercase()) {
                                    "load" -> {
                                        val filePort = node.inputs.find { it.id == "file_path" }
                                        if (filePort != null) {
                                            listOf(
                                                FlowParameter(
                                                    nodeId = node.id,
                                                    type = ParameterType.LOAD,
                                                    label = "${node.title} -> File Path",
                                                    portId = filePort.id,
                                                    dataType = filePort.dataType,
                                                    defaultValue = filePort.value as? String ?: filePort.defaultValue as? String ?: "output.txt"
                                                )
                                            )
                                        } else emptyList()
                                    }
                                    "save" -> {
                                        val filePort = node.inputs.find { it.id == "file_path" }
                                        if (filePort != null) {
                                            listOf(
                                                FlowParameter(
                                                    nodeId = node.id,
                                                    type = ParameterType.SAVE,
                                                    label = "${node.title} -> File Path",
                                                    portId = filePort.id,
                                                    dataType = filePort.dataType,
                                                    defaultValue = filePort.value as? String ?: filePort.defaultValue as? String ?: "output.txt"
                                                )
                                            )
                                        } else emptyList()
                                    }
                                    else -> emptyList()
                                }
                            }
                            is Node.FlowOutputNode -> {
                                val inPort = node.inputs.firstOrNull()
                                if (inPort != null) {
                                    val inferredType = if (inPort.dataType is DataType.Primitive && inPort.dataType.primitiveType == PrimitiveType.ANY) {
                                        val connection = currentFlow.connections.find { it.targetNodeId == node.id && it.targetPortId == inPort.id }
                                        val sourceNode = currentFlow.nodes.find { it.id == connection?.sourceNodeId }
                                        val sourcePort = sourceNode?.outputs?.find { it.id == connection?.sourcePortId }
                                        sourcePort?.dataType ?: inPort.dataType
                                    } else inPort.dataType

                                    listOf(
                                        FlowParameter(
                                            nodeId = node.id,
                                            type = ParameterType.OUTPUT,
                                            label = inPort.name.replaceFirstChar { it.uppercase() },
                                            portId = inPort.id,
                                            dataType = inferredType,
                                            defaultValue = ""
                                        )
                                    )
                                } else emptyList()
                            }
                            else -> emptyList()
                        }
                    }
                }

                // Maintain local state for input values (excluding OUTPUT)
                val parameterValues = remember(currentFlow, flowParameters) {
                    val map = mutableStateMapOf<String, String>()
                    flowParameters.forEach { param ->
                        if (param.type != ParameterType.OUTPUT) {
                            map["${param.nodeId}"] = param.defaultValue
                        }
                    }
                    map
                }

                // Filter parameters: SAVE nodes are classified as outputs!
                val inputs = flowParameters.filter { it.type != ParameterType.OUTPUT && it.type != ParameterType.SAVE }
                val outputs = flowParameters.filter { it.type == ParameterType.OUTPUT || it.type == ParameterType.SAVE }

                // Runner Card (Standard GlassCard)
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(
                            title = stringResource(Res.string.flow_run_title, currentFlow.name),
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
                                checked = viewModel.saveResults,
                                onCheckedChange = { viewModel.saveResults = it },
                                modifier = Modifier.scale(0.8f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                    Text(stringResource(Res.string.flow_run_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.large))

                    // Parameter Inputs (DynamicParameterInput sharing)
                    if (inputs.isNotEmpty()) {
                        Text(
                            stringResource(Res.string.flow_inputs_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = ToolkitTheme.spacing.small)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)) {
                            inputs.forEach { param ->
                                val metadata = remember(param) {
                                    org.wip.plugintoolkit.api.ParameterMetadata(
                                        defaultValue = kotlinx.serialization.json.JsonPrimitive(param.defaultValue),
                                        description = "",
                                        type = param.dataType,
                                        required = true
                                    )
                                }
                                var value by remember(param) { mutableStateOf(parameterValues["${param.nodeId}"] ?: param.defaultValue) }
                                
                                org.wip.plugintoolkit.shared.components.plugin.DynamicParameterInput(
                                    name = param.label,
                                    metadata = metadata,
                                    value = value,
                                    onValueChange = { newValue ->
                                        value = newValue
                                        parameterValues["${param.nodeId}"] = newValue
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.large))
                    }

                    // Expected Outputs (including SAVE node outputs)
                    if (outputs.isNotEmpty()) {
                        Text(
                            stringResource(Res.string.flow_outputs_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(vertical = ToolkitTheme.spacing.small)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)) {
                            outputs.forEach { param ->
                                if (param.type == ParameterType.SAVE) {
                                    val metadata = remember(param) {
                                        org.wip.plugintoolkit.api.ParameterMetadata(
                                            defaultValue = kotlinx.serialization.json.JsonPrimitive(param.defaultValue),
                                            description = "Target path to save flow results",
                                            type = param.dataType,
                                            required = true
                                        )
                                    }
                                    var value by remember(param) { mutableStateOf(parameterValues["${param.nodeId}"] ?: param.defaultValue) }
                                    
                                    org.wip.plugintoolkit.shared.components.plugin.DynamicParameterInput(
                                        name = param.label,
                                        metadata = metadata,
                                        value = value,
                                        onValueChange = { newValue ->
                                            value = newValue
                                            parameterValues["${param.nodeId}"] = newValue
                                        }
                                    )
                                } else {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(ToolkitTheme.spacing.medium),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "${param.label} (${param.dataType.format()})",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                stringResource(Res.string.flow_collected_automatically),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.large))
                    }
                    
                    Button(
                        onClick = { viewModel.executeFlow(currentFlow, parameterValues.toMap()) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                        Text(stringResource(Res.string.flow_execute_button))
                    }
                }

                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraLarge))

                // Real Execution History
                val allJobs by jobViewModel.jobs.collectAsState(emptyList())
                val endedJobs by jobViewModel.endedJobs.collectAsState(emptyList())
                
                val flowJobs = remember(allJobs, endedJobs, currentFlow) {
                    (allJobs + endedJobs)
                        .filter { it.type == JobType.Flow && it.capabilityName == currentFlow.name }
                        .distinctBy { it.id }
                        .sortedByDescending { it.enqueuedAt }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = ToolkitTheme.spacing.medium),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(Res.string.flow_history_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    if (flowJobs.any { it.status == JobStatus.Completed || it.status == JobStatus.Failed || it.status == JobStatus.Cancelled }) {
                        TextButton(onClick = {
                            flowJobs.forEach { job ->
                                if (job.status == JobStatus.Completed || job.status == JobStatus.Failed || job.status == JobStatus.Cancelled) {
                                    jobViewModel.clearEndedJob(job.id)
                                }
                            }
                        }) {
                            Icon(Icons.Default.ClearAll, contentDescription = null)
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text("Clear History")
                        }
                    }
                }

                if (flowJobs.isEmpty()) {
                    Text(stringResource(Res.string.flow_no_history), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)) {
                        flowJobs.forEach { job ->
                            val progressState = jobViewModel.jobProgress.collectAsState()
                            val progress = progressState.value[job.id] ?: 0f

                            val allLogs by jobViewModel.jobLogs.collectAsState()
                            val logs = allLogs[job.id] ?: emptyList()

                            JobResultCard(
                                job = job,
                                progress = progress,
                                logs = logs,
                                onDelete = {
                                    if (job.status == JobStatus.Completed || job.status == JobStatus.Failed || job.status == JobStatus.Cancelled) {
                                        jobViewModel.clearEndedJob(job.id)
                                    } else {
                                        jobViewModel.cancelJob(job.id, force = true)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                        }
                    }
                }
            }
        }
    }
}
