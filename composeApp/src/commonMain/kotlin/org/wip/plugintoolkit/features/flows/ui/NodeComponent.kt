package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.ParameterRole
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.PortConstraints
import org.wip.plugintoolkit.features.flows.ui.node.InputSectionType
import org.wip.plugintoolkit.features.flows.ui.node.NodeInputSection
import org.wip.plugintoolkit.features.flows.ui.node.NodeOutputSection
import org.wip.plugintoolkit.features.flows.viewmodel.ValidationError
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.node_parameters_section

@Composable
fun NodeComponent(
    node: Node,
    connectedInputPortIds: Set<String>,
    inferredTypes: Map<Pair<Long, String>, DataType> = emptyMap(),
    inferredSemanticTypes: Map<Pair<Long, String>, List<SemanticType>> = emptyMap(),
    validationErrors: List<ValidationError> = emptyList(),
    onMove: (Long, Offset, Boolean, Boolean) -> Unit, // id, delta, snap, showGhost
    onEndMove: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onExpand: (Long) -> Unit,
    onUpdateValue: (Long, String, Any?) -> Unit,
    onStartConnection: (Long, String, Boolean) -> Unit,
    onDragConnection: (Offset) -> Unit = {},
    onDropConnection: (isShiftPressed: Boolean) -> Unit = {},
    onPortPositioned: (Long, String, Boolean, LayoutCoordinates) -> Unit = { _, _, _, _ -> },
    onPress: (Long) -> Unit = {},
    onUpdateBoundaryNode: (Long, String, DataType, List<SemanticType>, PortConstraints?, Boolean, Boolean) -> Unit = { _, _, _, _, _, _, _ -> },
    onUpdateSystemNodeSettings: (Long, String, List<SemanticType>, String?, List<String>?) -> Unit = { _, _, _, _, _ -> },
    onToggleCollapse: (Long) -> Unit = {},
    onToggleInputsCollapse: (Long) -> Unit = {},
    onToggleOutputsCollapse: (Long) -> Unit = {},
    highlightedPortId: String? = null,
    highlightedPortColor: Color? = null,
    stateScale: Float,
    stateOffset: Offset,
    selectedNodeIds: Set<Long> = emptySet(),
    isReadOnly: Boolean = false,
    isReady: Boolean = true,
    onFocusLost: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showEditBoundaryDialog by remember { mutableStateOf(false) }
    var showLoadSettingsDialog by remember { mutableStateOf(false) }

    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnEndMove by rememberUpdatedState(onEndMove)
    val currentOnPress by rememberUpdatedState(onPress)

    var showColorPicker by remember { mutableStateOf(false) }
    var activeColorInputId by remember { mutableStateOf<String?>(null) }
    var inputLocationsCollapsed by remember(node.id) { mutableStateOf(node.isCollapsed) }
    var outputLocationsCollapsed by remember(node.id) { mutableStateOf(node.isCollapsed) }

    LaunchedEffect(node.isCollapsed) {
        inputLocationsCollapsed = node.isCollapsed
        outputLocationsCollapsed = node.isCollapsed
    }

    val pluginManager = koinInject<PluginManager>()
    val providedSettings = remember(node) {
        if (node is Node.CapabilityNode) {
            pluginManager.loadPluginSettings(node.pluginInfo.id).settings
        } else {
            emptyMap()
        }
    }

    val (headerColor, onHeaderColor) = when (node) {
        is Node.CapabilityNode -> {
            if (node.isBroken) {
                Pair(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
            } else if (!isReady) {
                Pair(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
            } else {
                Pair(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
            }
        }

        is Node.SystemNode -> {
            if (node.systemAction.lowercase() == "error") {
                Pair(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.onError)
            } else {
                Pair(ToolkitTheme.colors.success, ToolkitTheme.colors.white)
            }
        }

        is Node.FlowInputNode -> Pair(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
        is Node.FlowOutputNode -> Pair(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
        is Node.SubFlowNode -> Pair(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)
    }

    val isSelected = remember(node.id, selectedNodeIds) { selectedNodeIds.contains(node.id) }
    val cardBorder = if (isSelected) {
        BorderStroke(ToolkitTheme.dimensions.progressIndicatorStroke, MaterialTheme.colorScheme.primary)
    } else {
        null
    }

    Box(
        modifier = modifier
            .width(ToolkitTheme.dimensions.nodeWidth)
            .testTag("node_card_${node.id}")
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = ToolkitTheme.spacing.small),
            shape = MaterialTheme.shapes.medium,
            border = cardBorder,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column {
                // Header
                NodeHeader(
                    node = node,
                    headerColor = headerColor,
                    onHeaderColor = onHeaderColor,
                    isReady = isReady,
                    isReadOnly = isReadOnly,
                    onPress = currentOnPress,
                    onMove = currentOnMove,
                    onEndMove = currentOnEndMove,
                    onExpand = onExpand,
                    onToggleCollapse = onToggleCollapse,
                    onDelete = onDelete,
                    onShowDeleteConfirmation = { showDeleteConfirmation = true },
                    onShowLoadSettings = { showLoadSettingsDialog = true },
                    onShowEditBoundary = { showEditBoundaryDialog = true }
                )

                // Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ToolkitTheme.spacing.mediumSmall),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.mediumSmall)
                ) {
                    val capNode = node as? Node.CapabilityNode
                    val visibleOutputs = node.outputs.filter { output ->
                        capNode?.capability?.parameters?.get(output.id)?.role != ParameterRole.OUTPUT_LOCATION
                    }
                    val parameters =
                        node.inputs.filter { capNode?.capability?.parameters?.get(it.id)?.role.let { r -> r == null || r == ParameterRole.STANDARD } }
                    val inputLocations =
                        node.inputs.filter { capNode?.capability?.parameters?.get(it.id)?.role == ParameterRole.INPUT_LOCATION }
                    val outputLocations =
                        node.inputs.filter { capNode?.capability?.parameters?.get(it.id)?.role == ParameterRole.OUTPUT_LOCATION }

                    val inputSections = listOf(
                        Triple(InputSectionType.PARAMETERS, stringResource(Res.string.node_parameters_section), parameters),
                        Triple(InputSectionType.INPUT_LOCATIONS, "Input Locations", inputLocations),
                        Triple(InputSectionType.OUTPUT_LOCATIONS, "Output Locations", outputLocations)
                    ).filter { it.third.isNotEmpty() }

                    inputSections.forEachIndexed { index, (sectionType, title, sectionInputs) ->
                        val isSectionCollapsed = when (sectionType) {
                            InputSectionType.INPUT_LOCATIONS -> inputLocationsCollapsed
                            InputSectionType.OUTPUT_LOCATIONS -> outputLocationsCollapsed
                            InputSectionType.PARAMETERS -> node.isInputsCollapsed
                        }

                        if (index > 0) {
                            HorizontalDivider(
                                Modifier.padding(vertical = ToolkitTheme.spacing.small),
                                thickness = ToolkitTheme.dimensions.borderThin,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }

                        NodeInputSection(
                            sectionType = sectionType,
                            title = title,
                            inputs = sectionInputs,
                            isSectionCollapsed = isSectionCollapsed,
                            onToggleCollapse = {
                                when (sectionType) {
                                    InputSectionType.INPUT_LOCATIONS -> inputLocationsCollapsed = !inputLocationsCollapsed
                                    InputSectionType.OUTPUT_LOCATIONS -> outputLocationsCollapsed = !outputLocationsCollapsed
                                    InputSectionType.PARAMETERS -> onToggleInputsCollapse(node.id)
                                }
                            },
                            node = node,
                            headerColor = headerColor,
                            connectedInputPortIds = connectedInputPortIds,
                            inferredTypes = inferredTypes,
                            inferredSemanticTypes = inferredSemanticTypes,
                            validationErrors = validationErrors,
                            providedSettings = providedSettings,
                            highlightedPortId = highlightedPortId,
                            highlightedPortColor = highlightedPortColor,
                            isReadOnly = isReadOnly,
                            onStartConnection = onStartConnection,
                            onDragConnection = onDragConnection,
                            onDropConnection = onDropConnection,
                            onPortPositioned = onPortPositioned,
                            onUpdateValue = onUpdateValue,
                            onFocusLost = onFocusLost
                        )
                    }

                    if (node.inputs.isNotEmpty() && visibleOutputs.isNotEmpty()) {
                        HorizontalDivider(
                            Modifier,
                            thickness = ToolkitTheme.dimensions.borderThin,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }

                    // Outputs Section
                    NodeOutputSection(
                        outputs = visibleOutputs,
                        isOutputsCollapsed = node.isOutputsCollapsed,
                        onToggleOutputsCollapse = { onToggleOutputsCollapse(node.id) },
                        node = node,
                        headerColor = headerColor,
                        inferredTypes = inferredTypes,
                        inferredSemanticTypes = inferredSemanticTypes,
                        validationErrors = validationErrors,
                        highlightedPortId = highlightedPortId,
                        highlightedPortColor = highlightedPortColor,
                        isReadOnly = isReadOnly,
                        onStartConnection = onStartConnection,
                        onDragConnection = onDragConnection,
                        onDropConnection = onDropConnection,
                        onPortPositioned = onPortPositioned
                    )
                }
            }
        }
    }

    NodeDialogs(
        node = node,
        inferredSemanticTypes = inferredSemanticTypes,
        showDeleteConfirmation = showDeleteConfirmation,
        onDismissDelete = { showDeleteConfirmation = false },
        onConfirmDelete = { onDelete(node.id); showDeleteConfirmation = false },
        showEditBoundaryDialog = showEditBoundaryDialog,
        onDismissEditBoundary = { showEditBoundaryDialog = false },
        onUpdateBoundaryNode = onUpdateBoundaryNode,
        showColorPicker = showColorPicker,
        activeColorInputId = activeColorInputId,
        onDismissColorPicker = { showColorPicker = false; activeColorInputId = null },
        onUpdateValue = onUpdateValue,
        showLoadSettingsDialog = showLoadSettingsDialog,
        onDismissLoadSettings = { showLoadSettingsDialog = false },
        onUpdateSystemNodeSettings = onUpdateSystemNodeSettings
    )
}
