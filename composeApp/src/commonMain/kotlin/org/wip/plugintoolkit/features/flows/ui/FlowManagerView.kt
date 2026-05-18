package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEvent
import org.wip.plugintoolkit.features.flows.viewmodel.FlowViewModel
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.shared.components.GlassCard
import org.wip.plugintoolkit.shared.components.SectionHeader
import org.jetbrains.compose.resources.stringResource
import plugintoolkit.composeapp.generated.resources.*

@Composable
fun FlowManagerView(
    viewModel: FlowViewModel,
    onEditFlow: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFlowName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var flowToDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.reloadFlows()
    }

    val activeCapabilities = remember(state.flows) {
        PluginLoader.getPlugins().flatMap { it.getManifest().capabilities.map { cap -> cap.name } }.toSet()
    }

    val filteredFlows = remember(state.flows, searchQuery) {
        state.flows.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = modifier.fillMaxSize().padding(ToolkitTheme.spacing.extraLarge)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(
                title = stringResource(Res.string.flow_manager_title),
                icon = Icons.Default.Add,
                modifier = Modifier.weight(1f)
            )

            Button(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                Text(stringResource(Res.string.flow_create_new))
            }
        }

        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

        // Premium Search Bar matching application design system
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text(stringResource(Res.string.flow_search_placeholder), style = MaterialTheme.typography.bodyMedium) },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            singleLine = true,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
            )
        )

        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.large))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
        ) {
            if (filteredFlows.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(vertical = ToolkitTheme.spacing.extraLarge), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isEmpty()) stringResource(Res.string.flow_no_flows) else stringResource(Res.string.flow_no_search_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                filteredFlows.forEach { flow ->
                    // Calculate parent relationships and missing capabilities reactively
                    val parentFlows = remember(flow, state.flows) {
                        state.flows.filter { parent ->
                            parent.nodes.any { it is Node.SubFlowNode && it.flowName == flow.name }
                        }.map { it.name }
                    }

                    val missingCapabilities = remember(flow, activeCapabilities) {
                        flow.nodes.filterIsInstance<Node.CapabilityNode>()
                            .map { it.capability.name }
                            .filter { it !in activeCapabilities }
                            .distinct()
                    }

                    FlowItem(
                        name = flow.name,
                        nodeCount = flow.nodes.size,
                        parentFlows = parentFlows,
                        missingCapabilities = missingCapabilities,
                        isSelected = state.selectedFlowId == flow.name,
                        onSelect = { viewModel.onEvent(FlowEvent.SelectFlow(flow.name)) },
                        onEdit = { 
                            viewModel.onEvent(FlowEvent.SelectFlow(flow.name))
                            onEditFlow(flow.name)
                        },
                        onDelete = { flowToDelete = flow.name }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(stringResource(Res.string.flow_create_new)) },
            text = {
                OutlinedTextField(
                    value = newFlowName,
                    onValueChange = { newFlowName = it },
                    label = { Text(stringResource(Res.string.flow_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newFlowName.isNotBlank()) {
                        viewModel.onEvent(FlowEvent.CreateFlow(newFlowName))
                        showCreateDialog = false
                        newFlowName = ""
                    }
                }) { Text(stringResource(Res.string.flow_create_button)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text(stringResource(Res.string.dialog_cancel)) }
            }
        )
    }

    // Modern Delete Confirmation Dialog with Unpacking Alerts
    if (flowToDelete != null) {
        val nameOfFlow = flowToDelete!!
        val parentFlows = state.flows.filter { parent ->
            parent.nodes.any { it is Node.SubFlowNode && it.flowName == nameOfFlow }
        }.map { it.name }

        AlertDialog(
            onDismissRequest = { flowToDelete = null },
            title = { Text(stringResource(Res.string.flow_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)) {
                    Text(stringResource(Res.string.flow_delete_confirm, nameOfFlow))
                    if (parentFlows.isNotEmpty()) {
                        Text(
                            text = stringResource(Res.string.flow_delete_warning_subflow, parentFlows.joinToString(", ")),
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.onEvent(FlowEvent.DeleteFlow(nameOfFlow))
                        flowToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(Res.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { flowToDelete = null }) {
                    Text(stringResource(Res.string.dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun FlowItem(
    name: String,
    nodeCount: Int,
    parentFlows: List<String>,
    missingCapabilities: List<String>,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isBroken = missingCapabilities.isNotEmpty()
    
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ToolkitTheme.spacing.medium, vertical = ToolkitTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (isBroken) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = CircleShape
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.extraSmall),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = stringResource(Res.string.flow_broken_tag),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                ) {
                    Text(
                        text = stringResource(Res.string.flow_nodes_count, nodeCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (parentFlows.isNotEmpty()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Text(
                            text = stringResource(Res.string.flow_used_in_parents, parentFlows.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Chips Flow Row (parent flows and missing capabilities)
                if (parentFlows.isNotEmpty() || missingCapabilities.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = ToolkitTheme.spacing.extraSmall)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        parentFlows.forEach { parentName ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = stringResource(Res.string.flow_used_in_chip, parentName),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.extraSmall)
                                )
                            }
                        }
                        
                        missingCapabilities.forEach { capName ->
                            Surface(
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = stringResource(Res.string.flow_missing_chip, capName),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.extraSmall)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
