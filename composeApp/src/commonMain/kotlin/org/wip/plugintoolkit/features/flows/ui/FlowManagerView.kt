package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.viewmodel.ConflictResolutionAction
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEvent
import org.wip.plugintoolkit.features.flows.viewmodel.FlowViewModel
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.shared.components.GlassCard
import org.wip.plugintoolkit.shared.components.SectionHeader
import org.wip.plugintoolkit.shared.components.ToolkitButtonGroup
import org.wip.plugintoolkit.shared.components.ToolkitChip
import org.wip.plugintoolkit.shared.components.ToolkitChipStyle
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import org.wip.plugintoolkit.shared.components.tooltip
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_delete
import plugintoolkit.composeapp.generated.resources.action_save
import plugintoolkit.composeapp.generated.resources.dialog_cancel
import plugintoolkit.composeapp.generated.resources.error_invalid_version_format
import plugintoolkit.composeapp.generated.resources.flow_metadata_edit_title
import plugintoolkit.composeapp.generated.resources.flow_version
import plugintoolkit.composeapp.generated.resources.flow_description
import plugintoolkit.composeapp.generated.resources.flow_broken_tag
import plugintoolkit.composeapp.generated.resources.flow_create_button
import plugintoolkit.composeapp.generated.resources.flow_create_new
import plugintoolkit.composeapp.generated.resources.flow_import_file
import plugintoolkit.composeapp.generated.resources.flow_import_clipboard
import plugintoolkit.composeapp.generated.resources.flow_delete_confirm
import plugintoolkit.composeapp.generated.resources.flow_delete_title
import plugintoolkit.composeapp.generated.resources.flow_delete_warning_subflow
import plugintoolkit.composeapp.generated.resources.flow_editor_read_only
import plugintoolkit.composeapp.generated.resources.flow_editor_read_only_reason
import plugintoolkit.composeapp.generated.resources.flow_manager_title
import plugintoolkit.composeapp.generated.resources.flow_missing_chip
import plugintoolkit.composeapp.generated.resources.flow_name_label
import plugintoolkit.composeapp.generated.resources.flow_no_flows
import plugintoolkit.composeapp.generated.resources.flow_no_search_results
import plugintoolkit.composeapp.generated.resources.flow_nodes_count
import plugintoolkit.composeapp.generated.resources.flow_readonly_reason_running
import plugintoolkit.composeapp.generated.resources.flow_readonly_reason_used_in_other
import plugintoolkit.composeapp.generated.resources.flow_search_placeholder
import plugintoolkit.composeapp.generated.resources.flow_used_in_chip
import plugintoolkit.composeapp.generated.resources.flow_used_in_parents
import plugintoolkit.composeapp.generated.resources.*

@Composable
fun FlowManagerView(
    viewModel: FlowViewModel,
    onEditFlow: (String) -> Unit,
    onRunFlow: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val pluginManager = org.koin.compose.koinInject<org.wip.plugintoolkit.features.plugin.logic.PluginManager>()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFlowName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var flowToDelete by remember { mutableStateOf<String?>(null) }
    var flowToEditMetadata by remember { mutableStateOf<String?>(null) }
    val clipboard = androidx.compose.ui.platform.LocalClipboard.current
    val textClipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

//    LaunchedEffect(Unit) {
//        // flows are reloaded automatically via flowRepository
//    }

    val activeCapabilities = remember(state.flows) {
        PluginLoader.getPlugins().flatMap { it.getManifest().getOrThrow().capabilities.map { cap -> cap.name } }.toSet()
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

            ToolkitButtonGroup {
                item { shape, modifierSpec ->
                    Button(
                        onClick = { viewModel.onEvent(FlowEvent.TriggerImport) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        shape = shape,
                        modifier = modifierSpec
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Import from file")
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                        Text(stringResource(Res.string.flow_import_file))
                    }
                }

                item { shape, modifierSpec ->
                    Button(
                        onClick = {
                            val base64 = textClipboardManager.getText()?.text
                            if (!base64.isNullOrBlank()) {
                                viewModel.onEvent(FlowEvent.TriggerImportFromClipboard(base64))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        shape = shape,
                        modifier = modifierSpec
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Import from clipboard")
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                        Text(stringResource(Res.string.flow_import_clipboard))
                    }
                }

                item { shape, modifierSpec ->
                    Button(
                        onClick = { showCreateDialog = true },
                        shape = shape,
                        modifier = modifierSpec
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                        Text(stringResource(Res.string.flow_create_new))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

        // Premium Search Bar matching application design system
        ToolkitTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = {
                Text(
                    stringResource(Res.string.flow_search_placeholder),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.large))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
        ) {
            if (filteredFlows.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(vertical = ToolkitTheme.spacing.extraLarge),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isEmpty()) stringResource(Res.string.flow_no_flows) else stringResource(
                            Res.string.flow_no_search_results
                        ),
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

                    val notReadyNodes = remember(flow, state.flows) {
                        flow.nodes.filter { node ->
                            val settings =
                                if (node is Node.CapabilityNode) pluginManager.loadPluginSettings(node.pluginInfo.id).settings else null
                            !node.isReady(flow.connections, settings)
                        }
                    }

                    val isRunning = remember(flow.name, state.flows) {
                        viewModel.isFlowRunning(flow.name)
                    }

                    FlowItem(
                        name = flow.name,
                        nodeCount = flow.nodes.size,
                        parentFlows = parentFlows,
                        missingCapabilities = missingCapabilities,
                        notReadyNodes = notReadyNodes,
                        isRunning = isRunning,
                        isSelected = state.selectedFlowId == flow.name,
                        onSelect = { onRunFlow(flow.name) },
                        onEditMetadata = { flowToEditMetadata = flow.name },
                        onEdit = {
                            viewModel.onEvent(FlowEvent.SelectFlow(flow.name))
                            onEditFlow(flow.name)
                        },
                        onDelete = { flowToDelete = flow.name },
                        onExport = { viewModel.onEvent(FlowEvent.ExportFlow(flow.name)) },
                        onShareClipboard = {
                            viewModel.onEvent(FlowEvent.ShareFlowToClipboard(flow.name) { base64 ->
                                scope.launch {
                                    clipboard.setClipEntry(PlatformUtils.clipEntryOf(base64))
                                }
                            })
                        }
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
                ToolkitTextField(
                    value = newFlowName,
                    onValueChange = { newFlowName = it },
                    label = { Text(stringResource(Res.string.flow_name_label)) },
                    modifier = Modifier.fillMaxWidth()
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
                            text = stringResource(
                                Res.string.flow_delete_warning_subflow,
                                parentFlows.joinToString(", ")
                            ),
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

    if (flowToEditMetadata != null) {
        val flow = state.flows.find { it.name == flowToEditMetadata }
        if (flow != null) {
            var version by remember { mutableStateOf(flow.version) }
            var description by remember { mutableStateOf(flow.description ?: "") }
            val isVersionValid = remember(version) { version.matches(Regex("^\\d+\\.\\d+\\.\\d+$")) }

            AlertDialog(
                onDismissRequest = { flowToEditMetadata = null },
                title = { Text(stringResource(Res.string.flow_metadata_edit_title)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)) {
                        ToolkitTextField(
                            value = version,
                            onValueChange = { version = it },
                            label = { Text(stringResource(Res.string.flow_version)) },
                            modifier = Modifier.fillMaxWidth(),
                            isError = !isVersionValid,
                            supportingText = if (!isVersionValid) {
                                { Text(stringResource(Res.string.error_invalid_version_format)) }
                            } else null
                        )
                        ToolkitTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text(stringResource(Res.string.flow_description)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.onEvent(
                                FlowEvent.UpdateFlowMetadata(
                                    flow.name,
                                    version,
                                    description.takeIf { it.isNotBlank() }
                                )
                            )
                            flowToEditMetadata = null
                        },
                        enabled = isVersionValid
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { flowToEditMetadata = null }) {
                        Text(stringResource(Res.string.dialog_cancel))
                    }
                }
            )
        }
    }

    if (state.importConflicts.isNotEmpty()) {
        ConflictResolutionDialog(
            importConflicts = state.importConflicts,
            onResolve = { finalResolutions, customNames ->
                viewModel.onEvent(FlowEvent.ResolveImportConflicts(finalResolutions, customNames))
            },
            onCancel = { viewModel.onEvent(FlowEvent.CancelImport) }
        )
    }

}

@Composable
private fun ConflictResolutionDialog(
    importConflicts: List<String>,
    onResolve: (Map<String, ConflictResolutionAction>, Map<String, String>) -> Unit,
    onCancel: () -> Unit
) {
    val resolutions = remember(importConflicts) {
        mutableStateMapOf<String, ConflictResolutionAction>().apply {
            importConflicts.forEach { name ->
                put(name, ConflictResolutionAction.RENAME)
            }
        }
    }

    val customNames = remember(importConflicts) {
        mutableStateMapOf<String, String>().apply {
            importConflicts.forEach { name ->
                put(name, "")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "Resolve Import Conflicts", //TODO: localize
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
            ) {
                Text(
                    text = "The following imported flows already exist. Choose an action for each flow:", //TODO: localize
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                for (clashingName in importConflicts) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.divider)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(ToolkitTheme.spacing.medium),
                            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Conflict",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                                Text(
                                    text = clashingName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            SegmentedButtonGroup(
                                options = ConflictResolutionAction.entries,
                                selectedOption = resolutions[clashingName] ?: ConflictResolutionAction.RENAME,
                                onOptionSelected = { resolutions[clashingName] = it }
                            )

                            AnimatedVisibility(
                                visible = resolutions[clashingName] == ConflictResolutionAction.RENAME
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraSmall))
                                    ToolkitTextField(
                                        value = customNames[clashingName] ?: "",
                                        onValueChange = { customNames[clashingName] = it },
                                        label = { Text("New flow name (optional)") }, //TODO: localize
                                        placeholder = { Text("Auto-unique fallback") }, //TODO: localize
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onResolve(resolutions.toMap(), customNames.toMap())
                }
            ) {
                Text("Import") //TODO: localize
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel
            ) {
                Text("Cancel") //TODO: localize
            }
        }
    )
}

@Composable
private fun FlowItem(
    name: String,
    nodeCount: Int,
    parentFlows: List<String>,
    missingCapabilities: List<String>,
    notReadyNodes: List<Node>,
    isRunning: Boolean,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEditMetadata: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onShareClipboard: () -> Unit
) {
    val isBroken = missingCapabilities.isNotEmpty() || notReadyNodes.isNotEmpty()

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                        ToolkitChip(
                            text = stringResource(Res.string.flow_broken_tag),
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                )
                            },
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            style = ToolkitChipStyle.Filled,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isRunning) {
                        ToolkitChip(
                            text = stringResource(Res.string.flow_readonly_reason_running),
                            icon = {
                                Box(
                                    modifier = Modifier
                                        .size(ToolkitTheme.spacing.badgeHorizontal)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                )
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = ToolkitChipStyle.Filled,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isRunning || parentFlows.isNotEmpty()) {
                        val reasons = mutableListOf<String>()
                        if (isRunning) reasons.add(stringResource(Res.string.flow_readonly_reason_running))
                        if (parentFlows.isNotEmpty()) reasons.add(stringResource(Res.string.flow_readonly_reason_used_in_other))

                        val displayText = if (reasons.isNotEmpty()) {
                            stringResource(Res.string.flow_editor_read_only_reason, reasons.joinToString(", "))
                        } else {
                            stringResource(Res.string.flow_editor_read_only)
                        }

                        ToolkitChip(
                            text = displayText,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = ToolkitChipStyle.Filled,
                            fontWeight = FontWeight.Bold
                        )
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = ToolkitTheme.opacity.divider)
                        )
                        Text(
                            text = stringResource(Res.string.flow_used_in_parents, parentFlows.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Chips Flow Row (parent flows and missing capabilities)
                if (parentFlows.isNotEmpty() || missingCapabilities.isNotEmpty() || notReadyNodes.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = ToolkitTheme.spacing.extraSmall)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        parentFlows.forEach { parentName ->
                            ToolkitChip(
                                text = stringResource(Res.string.flow_used_in_chip, parentName),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = ToolkitChipStyle.Tinted,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        missingCapabilities.forEach { capName ->
                            ToolkitChip(
                                text = stringResource(Res.string.flow_missing_chip, capName),
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.error,
                                style = ToolkitChipStyle.Outlined,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        notReadyNodes.forEach { node ->
                            ToolkitChip(
                                text = "Not Ready: ${node.title}",
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.error,
                                style = ToolkitChipStyle.Outlined,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            ToolkitButtonGroup {
                item { shape, modifierSpec ->
                    FilledTonalIconButton(
                        onClick = onExport,
                        shape = shape,
                        modifier = modifierSpec.size(ToolkitTheme.dimensions.menuItem)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Export Flow",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .size(ToolkitTheme.dimensions.iconMediumSmall)
                                .tooltip("Export Flow to file")
                        )
                    }
                }
                item { shape, modifierSpec ->
                    FilledTonalIconButton(
                        onClick = onShareClipboard,
                        shape = shape,
                        modifier = modifierSpec.size(ToolkitTheme.dimensions.menuItem)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy to Clipboard",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .size(ToolkitTheme.dimensions.iconMediumSmall)
                                .tooltip("Copy flow to clipboard")
                        )
                    }
                }
                item { shape, modifierSpec ->
                    FilledTonalIconButton(
                        onClick = onEditMetadata,
                        shape = shape,
                        modifier = modifierSpec.size(ToolkitTheme.dimensions.menuItem)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Edit Metadata",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                        )
                    }
                }
                item { shape, modifierSpec ->
                    FilledTonalIconButton(
                        onClick = onEdit,
                        shape = shape,
                        modifier = modifierSpec.size(ToolkitTheme.dimensions.menuItem)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                        )
                    }
                }
                item { shape, modifierSpec ->
                    FilledTonalIconButton(
                        onClick = onDelete,
                        shape = shape,
                        modifier = modifierSpec.size(ToolkitTheme.dimensions.menuItem)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.action_delete),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedButtonGroup(
    options: List<ConflictResolutionAction>,
    selectedOption: ConflictResolutionAction,
    onOptionSelected: (ConflictResolutionAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(ToolkitTheme.dimensions.borderUnselected, outlineColor),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = option == selectedOption

                val containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    ToolkitTheme.colors.transparent
                }

                val contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(containerColor)
                        .clickable { onOptionSelected(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.extraSmall)
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                modifier = Modifier.size(16.dp),
                                tint = contentColor
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                        }
                        Text(
                            text = when (option) {
                                ConflictResolutionAction.RENAME -> "Rename"
                                ConflictResolutionAction.KEEP_LOCAL -> "Keep Local"
                                ConflictResolutionAction.KEEP_IMPORTED -> "Keep Imported"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }

                // Add vertical divider if not the last item
                if (index < options.size - 1) {
                    Spacer(
                        modifier = Modifier
                            .width(ToolkitTheme.dimensions.borderUnselected)
                            .fillMaxHeight()
                            .background(outlineColor)
                    )
                }
            }
        }
    }
}
