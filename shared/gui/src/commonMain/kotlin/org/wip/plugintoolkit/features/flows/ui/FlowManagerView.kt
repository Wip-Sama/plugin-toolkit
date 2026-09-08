package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.animation.AnimatedVisibility
import co.touchlab.kermit.Logger
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.FlowChipFilter
import org.wip.plugintoolkit.features.flows.model.FlowSortMode
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.ui.components.FlowFilterChipsRow
import org.wip.plugintoolkit.features.flows.ui.components.FlowSortDropdownChip
import org.wip.plugintoolkit.features.flows.viewmodel.ConflictResolutionAction
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEvent
import org.wip.plugintoolkit.features.flows.viewmodel.FlowViewModel
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.shared.components.ToolkitCard
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
import plugintoolkit.composeapp.generated.resources.flow_clear_defaults
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

@Composable
fun FlowManagerView(
    viewModel: FlowViewModel,
    onEditFlow: (String) -> Unit,
    onRunFlow: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val pluginManager = org.koin.compose.koinInject<org.wip.plugintoolkit.features.plugin.logic.PluginManager>()
    val pluginLocksState by pluginManager.pluginLocksState.collectAsState()
    val loadedPlugins by pluginManager.loadedPlugins.collectAsState()
    val installedPlugins by pluginManager.installedPlugins.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFlowName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var chipFilter by remember { mutableStateOf(FlowChipFilter.All) }
    var sortMode by remember { mutableStateOf(FlowSortMode.NameAsc) }
    var flowToDelete by remember { mutableStateOf<String?>(null) }
    var flowToEditMetadata by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboard.current
    val textClipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    androidx.compose.runtime.LaunchedEffect(loadedPlugins) {
        loadedPlugins.forEach { pkg ->
            try {
                pluginManager.refreshLocks(pkg)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    val activeCapabilities = remember(state.flows, loadedPlugins, installedPlugins) {
        PluginLoader.getPlugins().flatMap { it.getManifest().getOrNull()?.capabilities?.map { cap -> cap.name } ?: emptyList() }.toSet()
    }

    val parentFlowsMap = remember(state.flows) {
        val map = mutableMapOf<String, MutableList<String>>()
        state.flows.forEach { parent ->
            parent.nodes.filterIsInstance<Node.SubFlowNode>().forEach { subNode ->
                map.getOrPut(subNode.flowName) { mutableListOf() }.add(parent.name)
            }
        }
        map
    }

    val filteredFlows = remember(
        state.flows,
        searchQuery,
        chipFilter,
        sortMode,
        activeCapabilities,
        pluginLocksState,
        loadedPlugins,
        parentFlowsMap
    ) {
        state.flows.filter { flow ->
            val matchesSearch = flow.name.contains(searchQuery, ignoreCase = true) ||
                (flow.description?.contains(searchQuery, ignoreCase = true) == true)

            val parentFlows = parentFlowsMap[flow.name] ?: emptyList()
            val missingCapabilities = flow.nodes.filterIsInstance<Node.CapabilityNode>()
                .map { it.capability.name }
                .filter { it !in activeCapabilities }
            val notReadyNodes = flow.nodes.filter { node ->
                val settings =
                    if (node is Node.CapabilityNode) pluginManager.loadPluginSettings(node.pluginInfo.id).settings else null
                val locks = if (node is Node.CapabilityNode) {
                    pluginLocksState[node.pluginInfo.id] ?: pluginLocksState.values.fold(emptyMap()) { acc, m -> acc + m }
                } else null
                !node.isReady(flow.connections, settings, locks)
            }
            val isReady = missingCapabilities.isEmpty() && notReadyNodes.isEmpty()

            val matchesFilter = when (chipFilter) {
                FlowChipFilter.All -> true
                FlowChipFilter.Ready -> isReady
                FlowChipFilter.Broken -> !isReady
                FlowChipFilter.Subflows -> parentFlows.isNotEmpty()
            }

            matchesSearch && matchesFilter
        }.let { list ->
            when (sortMode) {
                FlowSortMode.NameAsc -> list.sortedBy { it.name.lowercase() }
                FlowSortMode.NameDesc -> list.sortedByDescending { it.name.lowercase() }
                FlowSortMode.NodeCountDesc -> list.sortedByDescending { it.nodes.size }
                FlowSortMode.NodeCountAsc -> list.sortedBy { it.nodes.size }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(ToolkitTheme.spacing.extraLarge)) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val isCompact = maxWidth < ToolkitTheme.dimensions.breakpointCompact
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
                        val label = stringResource(Res.string.flow_import_file)
                        Button(
                            onClick = { viewModel.onEvent(FlowEvent.TriggerImport) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            shape = shape,
                            modifier = modifierSpec.tooltip(label)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = label)
                            if (!isCompact) {
                                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                                Text(label, maxLines = 1, softWrap = false)
                            }
                        }
                    }

                    item { shape, modifierSpec ->
                        val label = stringResource(Res.string.flow_import_clipboard)
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
                            modifier = modifierSpec.tooltip(label)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = label)
                            if (!isCompact) {
                                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                                Text(label, maxLines = 1, softWrap = false)
                            }
                        }
                    }

                    item { shape, modifierSpec ->
                        val label = stringResource(Res.string.flow_create_new)
                        Button(
                            onClick = { showCreateDialog = true },
                            shape = shape,
                            modifier = modifierSpec.tooltip(label)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = label)
                            if (!isCompact) {
                                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                                Text(label, maxLines = 1, softWrap = false)
                            }
                        }
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

        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))

        // Filter chips and sort row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlowFilterChipsRow(
                selectedFilter = chipFilter,
                onFilterSelected = { chipFilter = it },
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))

            FlowSortDropdownChip(
                sortMode = sortMode,
                onSortModeChange = { sortMode = it }
            )
        }

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
                        text = if (searchQuery.isEmpty() && chipFilter == FlowChipFilter.All) {
                            stringResource(Res.string.flow_no_flows)
                        } else {
                            stringResource(Res.string.flow_no_search_results)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                filteredFlows.forEach { flow ->
                    // Calculate parent relationships and missing capabilities reactively
                    val parentFlows = remember(flow.name, parentFlowsMap) {
                        parentFlowsMap[flow.name] ?: emptyList()
                    }

                    val missingCapabilities = remember(flow, activeCapabilities) {
                        flow.nodes.filterIsInstance<Node.CapabilityNode>()
                            .map { it.capability.name }
                            .filter { it !in activeCapabilities }
                            .distinct()
                    }

                    val notReadyNodes = remember(flow, state.flows, pluginLocksState, loadedPlugins) {
                        flow.nodes.filter { node ->
                            val settings =
                                if (node is Node.CapabilityNode) pluginManager.loadPluginSettings(node.pluginInfo.id).settings else null
                            val locks = if (node is Node.CapabilityNode) {
                                pluginLocksState[node.pluginInfo.id] ?: pluginLocksState.values.fold(emptyMap()) { acc, m -> acc + m }
                            } else null
                            !node.isReady(flow.connections, settings, locks)
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
                        onSelect = {
                            if (missingCapabilities.isNotEmpty() || notReadyNodes.isNotEmpty()) {
                                Logger.w { "Cannot run flow '${flow.name}': Flow is not ready (missingCapabilities: $missingCapabilities, notReadyNodes: $notReadyNodes)" }
                            }
                            onRunFlow(flow.name)
                        },
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
        CreateFlowDialog(
            initialName = newFlowName,
            onConfirm = {
                viewModel.onEvent(FlowEvent.CreateFlow(it))
                showCreateDialog = false
                newFlowName = ""
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    if (flowToDelete != null) {
        val nameOfFlow = flowToDelete!!
        val parentFlows = state.flows.filter { parent ->
            parent.nodes.any { it is Node.SubFlowNode && it.flowName == nameOfFlow }
        }.map { it.name }

        DeleteFlowDialog(
            flowName = nameOfFlow,
            parentFlows = parentFlows,
            onConfirm = {
                viewModel.onEvent(FlowEvent.DeleteFlow(nameOfFlow))
                flowToDelete = null
            },
            onDismiss = { flowToDelete = null }
        )
    }

    if (flowToEditMetadata != null) {
        val flow = state.flows.find { it.name == flowToEditMetadata }
        if (flow != null) {
            EditFlowMetadataDialog(
                flow = flow,
                onSave = { version, description ->
                    viewModel.onEvent(
                        FlowEvent.UpdateFlowMetadata(
                            flow.name,
                            version,
                            description
                        )
                    )
                    flowToEditMetadata = null
                },
                onClearDefaults = {
                    viewModel.clearFlowDefaults(flow)
                },
                onDismiss = { flowToEditMetadata = null }
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
