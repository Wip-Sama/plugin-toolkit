package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.material3.AppBarRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import org.wip.plugintoolkit.shared.components.AdaptiveAppBarOverflowButton
import org.wip.plugintoolkit.shared.components.AdaptiveBackNavigationButton
import org.wip.plugintoolkit.shared.components.AdaptiveConstrainedContent
import org.wip.plugintoolkit.shared.components.AdaptivePaneHeader
import org.wip.plugintoolkit.shared.components.DesktopPaneDragHandle
import org.wip.plugintoolkit.shared.components.adaptiveOuterHorizontalPadding
import org.wip.plugintoolkit.shared.components.rememberAdaptivePaneScaffoldDirective
import org.wip.plugintoolkit.shared.components.rememberSupportingPaneExpansionState
import plugintoolkit.composeapp.generated.resources.flow_manager_subtitle
import plugintoolkit.composeapp.generated.resources.flow_summary_issues_count
import plugintoolkit.composeapp.generated.resources.flow_summary_pane_title
import plugintoolkit.composeapp.generated.resources.flow_summary_ready_count

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Surface
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.BoxWithConstraints
import kotlinx.coroutines.delay
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
import org.wip.plugintoolkit.shared.components.ToolkitTooltipBox
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

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun FlowManagerView(
    viewModel: FlowViewModel,
    onEditFlow: (String) -> Unit,
    onRunFlow: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val pluginManager = org.koin.compose.koinInject<org.wip.plugintoolkit.features.plugin.logic.PluginManager>()
    val jobManager = org.koin.compose.koinInject<org.wip.plugintoolkit.features.job.logic.JobManager>()
    val allJobs by jobManager.jobs.collectAsState(emptyList())
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
    val scope = rememberCoroutineScope()
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
        PluginLoader.getPlugins().flatMap {
            it.getManifest().getOrNull()?.capabilities
                ?.filter { cap -> !cap.isInteractive && cap.context != org.wip.plugintoolkit.api.CapabilityContext.INTERACTIVE_ONLY }
                ?.map { cap -> cap.name } ?: emptyList()
        }.toSet()
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
            val effectiveConns = flow.getEffectiveConnections()
            val notReadyNodes = flow.nodes.filter { node ->
                val settings =
                    if (node is Node.CapabilityNode) pluginManager.loadPluginSettings(node.pluginInfo.id).settings else null
                val locks = if (node is Node.CapabilityNode) {
                    pluginLocksState[node.pluginInfo.id] ?: pluginLocksState.values.fold(emptyMap()) { acc, m -> acc + m }
                } else null
                !node.isReady(flow.connections, settings, locks, effectiveConns)
            }
            val hasSourcelessInput = flow.getSourcelessInputConnections().isNotEmpty()
            val isReady = missingCapabilities.isEmpty() && notReadyNodes.isEmpty() && !hasSourcelessInput

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
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(50)
        runCatching { searchFocusRequester.requestFocus() }
    }

    val directive = rememberAdaptivePaneScaffoldDirective()
    val navigator = rememberSupportingPaneScaffoldNavigator<Unit>(scaffoldDirective = directive)
    val paneExpansionState = rememberSupportingPaneExpansionState(
        initialWidth = ToolkitTheme.dimensions.adaptiveSupportingPaneWidth
    )

    SupportingPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = adaptiveOuterHorizontalPadding(),
                vertical = ToolkitTheme.spacing.medium
            ),
        paneExpansionState = paneExpansionState,
        paneExpansionDragHandle = { state ->
            DesktopPaneDragHandle(paneExpansionState = state)
        },
        mainPane = {
            AnimatedPane {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = ToolkitTheme.shapes.large,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                horizontal = adaptiveOuterHorizontalPadding(),
                                vertical = ToolkitTheme.spacing.medium
                            )
                            .onPreviewKeyEvent { event ->
                                if (event.isCtrlPressed && event.key == Key.F && event.type == KeyEventType.KeyDown) {
                                    searchFocusRequester.requestFocus()
                                    true
                                } else false
                            }
                    ) {
        val importFileLabel = stringResource(Res.string.flow_import_file)
        val importClipboardLabel = stringResource(Res.string.flow_import_clipboard)
        val createFlowLabel = stringResource(Res.string.flow_create_new)
        val summaryPaneLabel = stringResource(Res.string.flow_summary_pane_title)
        AdaptivePaneHeader(
            title = stringResource(Res.string.flow_manager_title),
            subtitle = stringResource(Res.string.flow_manager_subtitle, state.flows.size),
            actions = {
                AppBarRow(
                    maxItemCount = 3,
                    overflowIndicator = { menuState ->
                        AdaptiveAppBarOverflowButton(menuState = menuState)
                    }
                ) {
                    clickableItem(
                        onClick = { showCreateDialog = true },
                        icon = { Icon(Icons.Default.Add, contentDescription = createFlowLabel) },
                        label = createFlowLabel
                    )
                    clickableItem(
                        onClick = { viewModel.onEvent(FlowEvent.TriggerImport) },
                        icon = { Icon(Icons.Default.Download, contentDescription = importFileLabel) },
                        label = importFileLabel
                    )
                    clickableItem(
                        onClick = {
                            val base64 = textClipboardManager.getText()?.text
                            if (!base64.isNullOrBlank()) {
                                viewModel.onEvent(FlowEvent.TriggerImportFromClipboard(base64))
                            }
                        },
                        icon = { Icon(Icons.Default.ContentCopy, contentDescription = importClipboardLabel) },
                        label = importClipboardLabel
                    )
                    clickableItem(
                        onClick = {
                            scope.launch {
                                navigator.navigateTo(SupportingPaneScaffoldRole.Supporting)
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = summaryPaneLabel) },
                        label = summaryPaneLabel
                    )
                }
            }
        )

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
            modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
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

        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

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
                        val effectiveConns = flow.getEffectiveConnections()
                        flow.nodes.filter { node ->
                            val settings =
                                if (node is Node.CapabilityNode) pluginManager.loadPluginSettings(node.pluginInfo.id).settings else null
                            val locks = if (node is Node.CapabilityNode) {
                                pluginLocksState[node.pluginInfo.id] ?: pluginLocksState.values.fold(emptyMap()) { acc, m -> acc + m }
                            } else null
                            !node.isReady(flow.connections, settings, locks, effectiveConns)
                        }
                    }

                    val isRunning = remember(flow.name, state.flows, allJobs) {
                        viewModel.isFlowRunning(flow.name)
                    }
                    val isPaused = remember(flow.name, allJobs) {
                        viewModel.isFlowPaused(flow.name)
                    }

                    FlowItem(
                        name = flow.name,
                        nodeCount = flow.nodes.size,
                        parentFlows = parentFlows,
                        missingCapabilities = missingCapabilities,
                        notReadyNodes = notReadyNodes,
                        isRunning = isRunning,
                        isPaused = isPaused,
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
                }
            }
        },
        supportingPane = {
            AnimatedPane(
                modifier = Modifier.preferredWidth(ToolkitTheme.dimensions.adaptiveSupportingPaneWidth)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = ToolkitTheme.shapes.large,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val readyCount = remember(state.flows) {
                        state.flows.count { flow ->
                            flow.nodes.all { it.isReady(flow.connections, null, null, null) }
                        }
                    }
                    val issuesCount = state.flows.size - readyCount

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(ToolkitTheme.spacing.large),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
                    ) {
                        if (navigator.canNavigateBack()) {
                            AdaptiveBackNavigationButton(
                                onClick = { scope.launch { navigator.navigateBack() } }
                            )
                        }
                        Text(
                            text = stringResource(Res.string.flow_summary_pane_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = ToolkitTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(ToolkitTheme.spacing.medium),
                                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                            ) {
                                Text(
                                    text = stringResource(Res.string.flow_summary_ready_count, readyCount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                HorizontalDivider()
                                Text(
                                    text = stringResource(Res.string.flow_summary_issues_count, issuesCount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (issuesCount > 0) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                        Button(
                            onClick = { showCreateDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                            Text(stringResource(Res.string.flow_create_new))
                        }
                        OutlinedButton(
                            onClick = { viewModel.onEvent(FlowEvent.TriggerImport) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                            Text(stringResource(Res.string.flow_import_file))
                        }
                    }
                }
            }
        }
    )
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
