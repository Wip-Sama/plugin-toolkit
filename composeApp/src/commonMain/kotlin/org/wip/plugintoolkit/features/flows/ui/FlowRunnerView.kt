package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.api.format
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEvent
import org.wip.plugintoolkit.features.flows.viewmodel.FlowViewModel
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.viewmodel.JobViewModel
import org.wip.plugintoolkit.features.plugin.ui.JobResultItem
import org.wip.plugintoolkit.shared.components.GlassCard
import org.wip.plugintoolkit.shared.components.SectionHeader
import kotlinx.serialization.json.Json

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
        // Sidebar: Select Flow to Run
        Surface(
            modifier = Modifier.width(ToolkitTheme.dimensions.sidebarExpandedWidth).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(ToolkitTheme.dimensions.cardElevation)
        ) {
            Column(modifier = Modifier.padding(ToolkitTheme.spacing.medium)) {
                Text("Select Flow", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)) {
                    state.flows.forEach { flow ->
                        val isSelected = selectedFlowToRun?.name == flow.name
                        Surface(
                            onClick = { selectedFlowToRun = flow },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                flow.name,
                                modifier = Modifier.padding(ToolkitTheme.spacing.mediumSmall),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Main Area: Run and History
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(ToolkitTheme.spacing.extraLarge)
        ) {
            val currentFlow = selectedFlowToRun
            if (currentFlow == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a flow to see options", color = MaterialTheme.colorScheme.onSurfaceVariant)
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

                // Maintain local state for input values
                val parameterValues = remember(currentFlow, flowParameters) {
                    val map = mutableStateMapOf<String, String>()
                    flowParameters.forEach { param ->
                        if (param.type != ParameterType.OUTPUT) {
                            map["${param.nodeId}"] = param.defaultValue
                        }
                    }
                    map
                }

                // Filter parameters
                val inputs = flowParameters.filter { it.type != ParameterType.OUTPUT }
                val outputs = flowParameters.filter { it.type == ParameterType.OUTPUT }

                // Runner Card
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(
                            title = "Run Flow: ${currentFlow.name}",
                            icon = Icons.Default.PlayArrow,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                    Text("This will execute all nodes in topological DAG order.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.large))

                    // Parameter Inputs
                    if (inputs.isNotEmpty()) {
                        Text(
                            "Execution Inputs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = ToolkitTheme.spacing.small)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)) {
                            inputs.forEach { param ->
                                var value by remember(param) { mutableStateOf(parameterValues["${param.nodeId}"] ?: param.defaultValue) }
                                OutlinedTextField(
                                    value = value,
                                    onValueChange = {
                                        value = it
                                        parameterValues["${param.nodeId}"] = it
                                    },
                                    label = { Text("${param.label} (${param.dataType.format()})") },
                                    placeholder = { Text(param.defaultValue) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.large))
                    }

                    // Expected Outputs
                    if (outputs.isNotEmpty()) {
                        Text(
                            "Expected Outputs",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(vertical = ToolkitTheme.spacing.small)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)) {
                            outputs.forEach { param ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                    )
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
                                            "Collected automatically",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                                        )
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
                        Text("Execute Flow")
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
                        "Execution History",
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
                    Text("No runs recorded for this flow.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)) {
                        flowJobs.forEach { job ->
                            var logsExpanded by remember { mutableStateOf(false) }

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(ToolkitTheme.spacing.medium)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                "Run ID: ${job.id.takeLast(12)}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                "Triggered: ${job.enqueuedAt}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        // Status Pill Badge
                                        Surface(
                                            color = when (job.status) {
                                                JobStatus.Completed -> MaterialTheme.colorScheme.primaryContainer
                                                JobStatus.Failed -> MaterialTheme.colorScheme.errorContainer
                                                JobStatus.Running -> MaterialTheme.colorScheme.secondaryContainer
                                                else -> MaterialTheme.colorScheme.surface
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                job.status.name,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = when (job.status) {
                                                    JobStatus.Completed -> MaterialTheme.colorScheme.onPrimaryContainer
                                                    JobStatus.Failed -> MaterialTheme.colorScheme.onErrorContainer
                                                    JobStatus.Running -> MaterialTheme.colorScheme.onSecondaryContainer
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))

                                        IconButton(onClick = { logsExpanded = !logsExpanded }) {
                                            Icon(
                                                if (logsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Toggle logs"
                                            )
                                        }
                                    }

                                    // Progress Bar for Running
                                    if (job.status == JobStatus.Running) {
                                        val progressState = jobViewModel.jobProgress.collectAsState()
                                        val progress = progressState.value[job.id] ?: 0f
                                        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                                        LinearProgressIndicator(
                                            progress = progress,
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    // Expanded Log Drawer
                                    AnimatedVisibility(
                                        visible = logsExpanded,
                                        enter = expandVertically(),
                                        exit = shrinkVertically()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = ToolkitTheme.spacing.medium)
                                        ) {
                                            Divider(color = MaterialTheme.colorScheme.outlineVariant)
                                            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

                                            // Render parsed outputs if completed successfully
                                            if (job.status == JobStatus.Completed) {
                                                val outputsParsed = remember(job.result) {
                                                    try {
                                                        job.result?.let {
                                                            Json.decodeFromString<Map<String, kotlinx.serialization.json.JsonElement>>(it)
                                                        } ?: emptyMap()
                                                    } catch (e: Exception) {
                                                        emptyMap()
                                                    }
                                                }

                                                if (outputsParsed.isNotEmpty()) {
                                                    Text(
                                                        "Output Results",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                                                    Column(
                                                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                            .padding(ToolkitTheme.spacing.medium)
                                                    ) {
                                                        outputsParsed.forEach { (portName, element) ->
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text(
                                                                    portName,
                                                                    style = MaterialTheme.typography.bodyMedium,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    color = MaterialTheme.colorScheme.onSurface
                                                                )
                                                                Text(
                                                                    when (element) {
                                                                        is kotlinx.serialization.json.JsonPrimitive -> {
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
                                            }

                                            // Console-Style Run Logs
                                            Text(
                                                "Console Log Output",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))

                                            val allLogs by jobViewModel.jobLogs.collectAsState()
                                            val logs = allLogs[job.id] ?: emptyList()

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(200.dp)
                                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                                    .padding(ToolkitTheme.spacing.medium)
                                                    .verticalScroll(rememberScrollState())
                                            ) {
                                                if (logs.isEmpty()) {
                                                    Text(
                                                        "No console output recorded.",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                    )
                                                } else {
                                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                        logs.forEach { logLine ->
                                                            Text(
                                                                logLine,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontFamily = FontFamily.Monospace,
                                                                color = when {
                                                                    logLine.contains("[ERROR]", ignoreCase = true) || logLine.startsWith("ERROR:") -> MaterialTheme.colorScheme.error
                                                                    logLine.contains("[WARN]", ignoreCase = true) || logLine.startsWith("WARN:") -> MaterialTheme.colorScheme.tertiary
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
                        }
                    }
                }
            }
        }
    }
}
