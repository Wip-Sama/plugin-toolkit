package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.canConvert
import org.wip.plugintoolkit.api.format
import org.wip.plugintoolkit.api.isCompatibleWith
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.NodeSerializationUtils
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorViewModel
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEvent
import org.wip.plugintoolkit.features.flows.viewmodel.ReadOnlyReason
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_save
import plugintoolkit.composeapp.generated.resources.dialog_cancel
import plugintoolkit.composeapp.generated.resources.flow_editor_btn_exit
import plugintoolkit.composeapp.generated.resources.flow_editor_convert_connect
import plugintoolkit.composeapp.generated.resources.flow_editor_duplicate_error
import plugintoolkit.composeapp.generated.resources.flow_editor_enter_name
import plugintoolkit.composeapp.generated.resources.flow_editor_flow_selected
import plugintoolkit.composeapp.generated.resources.flow_editor_incompatible_message
import plugintoolkit.composeapp.generated.resources.flow_editor_incompatible_semantics
import plugintoolkit.composeapp.generated.resources.flow_editor_incompatible_title
import plugintoolkit.composeapp.generated.resources.flow_editor_incompatible_types
import plugintoolkit.composeapp.generated.resources.flow_editor_no_flow_selected
import plugintoolkit.composeapp.generated.resources.flow_editor_read_only
import plugintoolkit.composeapp.generated.resources.flow_editor_read_only_reason
import plugintoolkit.composeapp.generated.resources.flow_editor_same_node_warning
import plugintoolkit.composeapp.generated.resources.flow_editor_save_as_title
import plugintoolkit.composeapp.generated.resources.flow_editor_save_changes
import plugintoolkit.composeapp.generated.resources.flow_name_label
import plugintoolkit.composeapp.generated.resources.flow_readonly_reason_running
import plugintoolkit.composeapp.generated.resources.flow_readonly_reason_used_in_other
import kotlin.math.roundToInt

@Composable
fun FlowEditorView(
    viewModel: FlowEditorViewModel,
    notificationService: NotificationService,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val flow = state.flow
    val pluginManager = org.koin.compose.koinInject<org.wip.plugintoolkit.features.plugin.logic.PluginManager>()
    val density = androidx.compose.ui.platform.LocalDensity.current

    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    var boardLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var rootLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    var showSaveAsDialog by remember { mutableStateOf(false) }
    var saveAsName by remember { mutableStateOf("") }

    // State for temporary connection drawing
    var isDrawingConnection by remember { mutableStateOf(false) }
    var connectionStartNodeId by remember { mutableStateOf<Long?>(null) }
    var connectionStartPortId by remember { mutableStateOf<String?>(null) }
    var connectionStartIsOutput by remember { mutableStateOf(true) }
    var connectionCurrentPos by remember { mutableStateOf(Offset.Zero) }

    // State for port position tracking
    val portPositions = remember { mutableStateMapOf<Pair<Long, String>, Offset>() }
    val getPortBoardPosition = { nodeId: Long, portId: String ->
        portPositions[Pair(nodeId, portId)]
    }

    val nodeSizes = remember { mutableStateMapOf<Long, IntSize>() }
    var highlightedPortId by remember { mutableStateOf<String?>(null) }
    var highlightedNodeId by remember { mutableStateOf<Long?>(null) }

    // State for drag and drop from palette
    var draggingNodeFromPalette by remember { mutableStateOf<PaletteNode?>(null) }
    var draggingNodePos by remember { mutableStateOf(Offset.Zero) }
    var dragStartPosition by remember { mutableStateOf(Offset.Zero) }
    var dragGrabOffset by remember { mutableStateOf(Offset.Zero) }
    var draggingNodeScale by remember { mutableStateOf(1f) }

    val handlePaletteClick = { paletteNode: PaletteNode ->
        val dropPos = (Offset(boardSize.width / 2f, boardSize.height / 2f) - state.offset) / state.scale
        val d = density.density
        when (paletteNode) {
            is PaletteNode.Capability -> viewModel.onEvent(
                FlowEvent.AddCapabilityNode(
                    paletteNode.pluginInfo,
                    paletteNode.capability,
                    dropPos,
                    d
                )
            )

            is PaletteNode.System -> viewModel.onEvent(FlowEvent.AddSystemNode(paletteNode.action, dropPos, d))
            is PaletteNode.FlowInput -> viewModel.onEvent(FlowEvent.AddFlowInputNode(dropPos, d))
            is PaletteNode.FlowOutput -> viewModel.onEvent(FlowEvent.AddFlowOutputNode(dropPos, d))
            is PaletteNode.SubFlow -> viewModel.onEvent(FlowEvent.AddSubFlowNode(paletteNode.name, dropPos, d))
        }
    }

    // Capture standard error strings for localization
    val sameNodeWarning = stringResource(Res.string.flow_editor_same_node_warning)
    val incompatibleTypesMsg = stringResource(Res.string.flow_editor_incompatible_types)
    val incompatibleSemanticsMsg = stringResource(Res.string.flow_editor_incompatible_semantics)
    val duplicateFlowMsg = stringResource(Res.string.flow_editor_duplicate_error)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { rootLayoutCoordinates = it }
    ) {
        // 1. Main Board Area (Background Layer)
        BoardCanvas(
            state = state,
            flow = flow,
            onPan = { viewModel.onEvent(FlowEvent.Pan(it)) },
            onZoom = { delta, position, isShiftPressed ->
                viewModel.onEvent(
                    FlowEvent.Zoom(
                        delta,
                        position,
                        isShiftPressed
                    )
                )
            },
            isDrawingConnection = isDrawingConnection,
            draggingNodeFromPalette = draggingNodeFromPalette,
            getPortBoardPosition = getPortBoardPosition,
            highlightedPortId = highlightedPortId,
            highlightedNodeId = highlightedNodeId,
            connectionStartNodeId = connectionStartNodeId,
            connectionStartPortId = connectionStartPortId,
            connectionStartIsOutput = connectionStartIsOutput,
            connectionCurrentPos = connectionCurrentPos,
            onBoardLayoutCoordinatesChanged = { boardLayoutCoordinates = it },
            onBoardSizeChanged = { boardSize = it },
            onDeleteConnection = { viewModel.onEvent(FlowEvent.DeleteConnection(it)) },
            onDetachConnection = { connection, isSource, offset ->
                viewModel.onEvent(FlowEvent.DeleteConnection(connection))
                if (isSource) {
                    connectionStartNodeId = connection.targetNodeId
                    connectionStartPortId = connection.targetPortId
                    connectionStartIsOutput = false
                } else {
                    connectionStartNodeId = connection.sourceNodeId
                    connectionStartPortId = connection.sourcePortId
                    connectionStartIsOutput = true
                }
                connectionCurrentPos = offset
                isDrawingConnection = true
            },
            onConnectionDrag = { boardPosition ->
                connectionCurrentPos = boardPosition
                val (closestNodeId, closestPortId) = findClosestPort(
                    boardPosition, flow, connectionStartIsOutput, state.scale, getPortBoardPosition
                )
                highlightedNodeId = closestNodeId
                highlightedPortId = closestPortId
            },
            onConnectionDrop = { isShiftPressed ->
                if (highlightedPortId != null && highlightedNodeId != null && connectionStartNodeId != null && connectionStartPortId != null) {
                    val sourceNodeId =
                        if (connectionStartIsOutput) connectionStartNodeId!! else highlightedNodeId!!
                    val sourcePortId =
                        if (connectionStartIsOutput) connectionStartPortId!! else highlightedPortId!!
                    val targetNodeId =
                        if (connectionStartIsOutput) highlightedNodeId!! else connectionStartNodeId!!
                    val targetPortId =
                        if (connectionStartIsOutput) highlightedPortId!! else connectionStartPortId!!

                    viewModel.onEvent(
                        FlowEvent.TryConnectPorts(
                            sourceNodeId,
                            sourcePortId,
                            targetNodeId,
                            targetPortId,
                            isShiftPressed
                        )
                    )
                }
                isDrawingConnection = false
                connectionStartNodeId = null
                connectionStartPortId = null
                highlightedPortId = null
                highlightedNodeId = null
            },
            onMoveConnectionFirst = { viewModel.onEvent(FlowEvent.MoveConnectionFirst(it)) },
            onMoveConnectionLast = { viewModel.onEvent(FlowEvent.MoveConnectionLast(it)) },
            selectedNodeIds = state.selectedNodeIds,
            onSelectNodes = { viewModel.onEvent(FlowEvent.SelectNodes(it)) },
            onClearSelection = { viewModel.onEvent(FlowEvent.ClearSelection) },
            onDeleteSelectedNodes = { viewModel.onEvent(FlowEvent.DeleteSelectedNodes) },
            onUndo = { viewModel.undo() },
            onRedo = { viewModel.redo() },
            nodeSizes = nodeSizes
        ) {
            // 1.2 Nodes
            flow.nodes.forEach { node ->
                key(node.id) {
                    val isDragged = state.draggedNodeId == node.id
                    val isPartofSelectedGroupDrag = state.draggedNodeId != null &&
                            state.selectedNodeIds.contains(state.draggedNodeId) &&
                            state.selectedNodeIds.contains(node.id)
                    val dragOffset =
                        if (isDragged || isPartofSelectedGroupDrag) state.currentDragOffset else Offset.Zero

                    val isNodeHighlighted = highlightedNodeId == node.id
                    val nodeHighlightedPortId = if (isNodeHighlighted) highlightedPortId else null

                    val nodeHighlightedPortColor =
                        if (isNodeHighlighted && highlightedPortId != null && connectionStartNodeId != null && connectionStartPortId != null) {
                            val startNode = flow.nodes.find { it.id == connectionStartNodeId }
                            val startPort = if (connectionStartIsOutput) {
                                startNode?.outputs?.find { it.id == connectionStartPortId }
                            } else {
                                startNode?.inputs?.find { it.id == connectionStartPortId }
                            }

                            val targetPort = if (connectionStartIsOutput) {
                                node.inputs.find { it.id == highlightedPortId }
                            } else {
                                node.outputs.find { it.id == highlightedPortId }
                            }

                            if (startPort != null && targetPort != null) {
                                val startInferredType =
                                    state.inferredTypes[Pair(connectionStartNodeId!!, connectionStartPortId!!)]
                                        ?: startPort.dataType
                                val targetInferredType =
                                    state.inferredTypes[Pair(node.id, highlightedPortId!!)] ?: targetPort.dataType
                                val startInferredSemantic =
                                    state.inferredSemanticTypes[Pair(connectionStartNodeId!!, connectionStartPortId!!)]
                                        ?: startPort.semanticTypes
                                val targetInferredSemantic =
                                    state.inferredSemanticTypes[Pair(node.id, highlightedPortId!!)]
                                        ?: targetPort.semanticTypes

                                val compatible =
                                    (startInferredType.isCompatibleWith(targetInferredType) || startInferredType.canConvert(
                                        targetInferredType
                                    )) &&
                                            org.wip.plugintoolkit.api.checkSemanticCompatibility(
                                                startInferredSemantic,
                                                targetInferredSemantic
                                            ) !is org.wip.plugintoolkit.api.CompatibilityResult.Incompatible
                                if (compatible) null else Color.Red
                            } else null
                        } else null

                    NodeCardContainer(
                        nodePosition = node.position,
                        dragOffset = dragOffset,
                        scale = state.scale,
                        boardOffset = state.offset
                    ) {
                        NodeComponent(
                            node = node,
                            connectedInputPortIds = flow.connections.filter { it.targetNodeId == node.id }
                                .map { it.targetPortId }.toSet(),
                            inferredTypes = state.inferredTypes,
                            inferredSemanticTypes = state.inferredSemanticTypes,
                            validationErrors = state.validationErrors,
                            isReady = node.isReady(
                                flow.connections,
                                if (node is Node.CapabilityNode) pluginManager.loadPluginSettings(node.pluginInfo.id).settings else null
                            ),
                            onFocusLost = { viewModel.onEvent(FlowEvent.Save) },
                            onMove = { id, delta, snap, showGhost ->
                                viewModel.onEvent(
                                    FlowEvent.MoveNode(
                                        id,
                                        delta,
                                        snap,
                                        showGhost
                                    )
                                )
                            },
                            onEndMove = { id -> viewModel.onEvent(FlowEvent.EndMoveNode(id, density.density)) },
                            onDelete = { id -> viewModel.onEvent(FlowEvent.DeleteNode(id)) },
                            onExpand = { id -> viewModel.onEvent(FlowEvent.ExpandSubFlow(id)) },
                            onUpdateValue = { id, portId, value ->
                                viewModel.onEvent(
                                    FlowEvent.UpdateInputPortValue(
                                        id,
                                        portId,
                                        NodeSerializationUtils.anyToJsonElement(value)
                                    )
                                )
                            },
                            onUpdateBoundaryNode = { id, name, dataType, semanticTypes, constraints, isList ->
                                viewModel.onEvent(
                                    FlowEvent.UpdateBoundaryNode(id, name, dataType, semanticTypes, constraints, isList)
                                )
                            },
                            onUpdateSystemNodeSettings = { id, portId, semanticTypes, inputPortId, extensions ->
                                viewModel.onEvent(
                                    FlowEvent.UpdateSystemNodeSettings(
                                        id,
                                        portId,
                                        semanticTypes,
                                        inputPortId,
                                        extensions
                                    )
                                )
                            },
                            onToggleCollapse = { id -> viewModel.onEvent(FlowEvent.ToggleNodeCollapse(id)) },
                            onToggleInputsCollapse = { id -> viewModel.onEvent(FlowEvent.ToggleNodeInputsCollapse(id)) },
                            onToggleOutputsCollapse = { id -> viewModel.onEvent(FlowEvent.ToggleNodeOutputsCollapse(id)) },
                            onPortPositioned = { nodeId, portId, pos ->
                                portPositions[Pair(nodeId, portId)] = (pos - state.offset) / state.scale
                            },
                            onStartConnection = { nodeId, portId, isOutput ->
                                isDrawingConnection = true
                                connectionStartNodeId = nodeId
                                connectionStartPortId = portId
                                connectionStartIsOutput = isOutput
                                getPortBoardPosition(nodeId, portId)?.let {
                                    connectionCurrentPos = it
                                }
                            },
                            onDragConnection = { cumulativeDragOffset ->
                                if (connectionStartNodeId != null && connectionStartPortId != null) {
                                    val startBoardPos =
                                        getPortBoardPosition(connectionStartNodeId!!, connectionStartPortId!!)
                                    if (startBoardPos != null) {
                                        val boardPosition = startBoardPos + cumulativeDragOffset
                                        connectionCurrentPos = boardPosition

                                        val (closestNodeId, closestPortId) = findClosestPort(
                                            boardPosition,
                                            flow,
                                            connectionStartIsOutput,
                                            state.scale,
                                            getPortBoardPosition
                                        )
                                        highlightedNodeId = closestNodeId
                                        highlightedPortId = closestPortId
                                    }
                                }
                            },
                            onDropConnection = { isShiftPressed ->
                                if (highlightedPortId != null && highlightedNodeId != null && connectionStartNodeId != null && connectionStartPortId != null) {
                                    val sourceNodeId =
                                        if (connectionStartIsOutput) connectionStartNodeId!! else highlightedNodeId!!
                                    val sourcePortId =
                                        if (connectionStartIsOutput) connectionStartPortId!! else highlightedPortId!!
                                    val targetNodeId =
                                        if (connectionStartIsOutput) highlightedNodeId!! else connectionStartNodeId!!
                                    val targetPortId =
                                        if (connectionStartIsOutput) highlightedPortId!! else connectionStartPortId!!

                                    viewModel.onEvent(
                                        FlowEvent.TryConnectPorts(
                                            sourceNodeId,
                                            sourcePortId,
                                            targetNodeId,
                                            targetPortId,
                                            isShiftPressed
                                        )
                                    )
                                }
                                isDrawingConnection = false
                                connectionStartNodeId = null
                                connectionStartPortId = null
                                highlightedPortId = null
                                highlightedNodeId = null
                            },

                            onPress = { id -> viewModel.onEvent(FlowEvent.BringToFront(id)) },
                            highlightedPortId = nodeHighlightedPortId,
                            highlightedPortColor = nodeHighlightedPortColor,
                            boardLayoutCoordinates = boardLayoutCoordinates,
                            stateScale = state.scale,
                            stateOffset = state.offset,
                            selectedNodeIds = state.selectedNodeIds,
                            isReadOnly = state.isReadOnly,
                            modifier = Modifier.onSizeChanged { size ->
                                nodeSizes[node.id] = size
                            }
                        )
                    }
                }
            }

            // Ghost Preview for Snapping
            state.ghostPosition?.let { ghostPos ->
                val draggedNode = state.flow.nodes.find { it.id == state.draggedNodeId }
                val heightDp = draggedNode?.let { node ->
                    nodeSizes[node.id]?.let { size ->
                        with(density) { size.height.toDp() }
                    }
                } ?: 120.dp

                NodeCardContainer(
                    nodePosition = ghostPos,
                    dragOffset = Offset.Zero,
                    scale = state.scale,
                    boardOffset = state.offset,
                    modifier = Modifier.alpha(0.3f)
                ) {
                    draggedNode?.let { NodeComponentPlaceholder(it, heightDp) }
                }
            }
        }

        // 2. Left Sidebar (Overlay Layer)
        if (!state.isReadOnly) {
            PaletteSidebar(
                flows = state.flows,
                currentFlowName = flow.name,
                plugins = viewModel.plugins.collectAsState().value,
                rootLayoutCoordinates = rootLayoutCoordinates,
                onDragStart = { paletteNode, initialPos, grabOffset ->
                    draggingNodeFromPalette = paletteNode
                    dragStartPosition = initialPos
                    dragGrabOffset = grabOffset
                    draggingNodeScale = 1.0f
                    draggingNodePos = initialPos
                },
                onDrag = { cumulativeDrag ->
                    val cursorPos = dragStartPosition + cumulativeDrag
                    val boardOffset = boardLayoutCoordinates?.positionInParent() ?: Offset.Zero
                    val relativeToBoard = cursorPos - boardOffset
                    val isOverBoard = boardSize != IntSize.Zero &&
                            relativeToBoard.x >= 0 && relativeToBoard.y >= 0 &&
                            relativeToBoard.x <= boardSize.width && relativeToBoard.y <= boardSize.height

                    draggingNodeScale = if (isOverBoard) state.scale else 1.0f
                    draggingNodePos = cursorPos - dragGrabOffset * draggingNodeScale
                },
                onDragEnd = {
                    val position = draggingNodePos
                    val boardOffset = boardLayoutCoordinates?.positionInParent() ?: Offset.Zero
                    val relativeToBoard = position - boardOffset
                    val isOverBoard =
                        relativeToBoard.x >= 0 && relativeToBoard.y >= 0 && relativeToBoard.x <= boardSize.width && relativeToBoard.y <= boardSize.height

                    if (isOverBoard) {
                        val dropPos = (relativeToBoard - state.offset) / state.scale
                        draggingNodeFromPalette?.let { paletteNode ->
                            val d = density.density
                            when (paletteNode) {
                                is PaletteNode.Capability -> viewModel.onEvent(
                                    FlowEvent.AddCapabilityNode(
                                        paletteNode.pluginInfo,
                                        paletteNode.capability,
                                        dropPos,
                                        d
                                    )
                                )

                                is PaletteNode.System -> viewModel.onEvent(
                                    FlowEvent.AddSystemNode(
                                        paletteNode.action,
                                        dropPos,
                                        d
                                    )
                                )

                                is PaletteNode.FlowInput -> viewModel.onEvent(FlowEvent.AddFlowInputNode(dropPos, d))
                                is PaletteNode.FlowOutput -> viewModel.onEvent(FlowEvent.AddFlowOutputNode(dropPos, d))
                                is PaletteNode.SubFlow -> viewModel.onEvent(
                                    FlowEvent.AddSubFlowNode(
                                        paletteNode.name,
                                        dropPos,
                                        d
                                    )
                                )
                            }
                        }
                    }
                    draggingNodeFromPalette = null
                },
                onClick = handlePaletteClick
            )
        }

        // 3. Top Status Bar (Overlay Layer)
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(ToolkitTheme.spacing.medium),
            shape = RoundedCornerShape(ToolkitTheme.spacing.large),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            tonalElevation = ToolkitTheme.spacing.small,
            shadowElevation = ToolkitTheme.spacing.extraSmall
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = ToolkitTheme.spacing.medium,
                    vertical = ToolkitTheme.spacing.small
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    if (flow.name.isBlank()) {
                        Text(
                            text = stringResource(Res.string.flow_editor_no_flow_selected),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                        ) {
                            Column {
                                Text(
                                    text = stringResource(Res.string.flow_editor_flow_selected),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = flow.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (state.isReadOnly) {
                                @Suppress("SimplifiableCallChain")
                                val reasonString = state.readOnlyReasons.map { reason ->
                                    when (reason) {
                                        ReadOnlyReason.Running -> stringResource(Res.string.flow_readonly_reason_running)
                                        ReadOnlyReason.UsedInOtherFlows -> stringResource(Res.string.flow_readonly_reason_used_in_other)
                                    }
                                }.joinToString(", ")

                                val displayText = if (reasonString.isNotEmpty()) {
                                    stringResource(Res.string.flow_editor_read_only_reason, reasonString)
                                } else {
                                    stringResource(Res.string.flow_editor_read_only)
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = displayText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(
                                            horizontal = ToolkitTheme.spacing.small,
                                            vertical = ToolkitTheme.spacing.extraSmall
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (flow.name.isBlank()) {
                            saveAsName = ""
                            showSaveAsDialog = true
                        } else {
                            viewModel.onEvent(FlowEvent.Save)
                        }
                    },
                    enabled = state.hasUnsavedChanges && !state.isReadOnly,
                    contentPadding = PaddingValues(
                        horizontal = ToolkitTheme.spacing.medium,
                        vertical = ToolkitTheme.spacing.small
                    )
                ) {
                    Text(stringResource(Res.string.flow_editor_save_changes))
                }

                OutlinedButton(
                    onClick = onExit,
                    contentPadding = PaddingValues(
                        horizontal = ToolkitTheme.spacing.medium,
                        vertical = ToolkitTheme.spacing.small
                    )
                ) {
                    Text(stringResource(Res.string.flow_editor_btn_exit))
                }
            }
        }

        // 4. Global Dragging Preview (Highest Layer)
        draggingNodeFromPalette?.let { node ->
            Box(
                modifier = Modifier
                    .offset { IntOffset(draggingNodePos.x.roundToInt(), draggingNodePos.y.roundToInt()) }
                    .graphicsLayer(
                        scaleX = draggingNodeScale,
                        scaleY = draggingNodeScale,
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    )
                    .alpha(0.7f)
            ) {
                PaletteItemPreview(node)
            }
        }

        state.pendingConnection?.let { pendingConn ->
            AlertDialog(
                onDismissRequest = { viewModel.onEvent(FlowEvent.CancelPendingConnection) },
                title = { Text(stringResource(Res.string.flow_editor_incompatible_title)) },
                text = {
                    Text(
                        stringResource(
                            Res.string.flow_editor_incompatible_message,
                            pendingConn.sourceType.format(),
                            pendingConn.targetType.format()
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.onEvent(
                                FlowEvent.AutoConvertAndConnect(
                                    pendingConn.sourceNodeId,
                                    pendingConn.sourcePortId,
                                    pendingConn.targetNodeId,
                                    pendingConn.targetPortId
                                )
                            )
                            viewModel.onEvent(FlowEvent.CancelPendingConnection)
                        }
                    ) {
                        Text(stringResource(Res.string.flow_editor_convert_connect))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.onEvent(FlowEvent.CancelPendingConnection) }
                    ) {
                        Text(stringResource(Res.string.dialog_cancel))
                    }
                }
            )
        }

        if (showSaveAsDialog) {
            AlertDialog(
                onDismissRequest = { showSaveAsDialog = false },
                title = { Text(stringResource(Res.string.flow_editor_save_as_title)) },
                text = {
                    Column {
                        Text(
                            text = stringResource(Res.string.flow_editor_enter_name),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = ToolkitTheme.spacing.medium)
                        )
                        ToolkitTextField(
                            value = saveAsName,
                            onValueChange = { saveAsName = it },
                            label = { Text(stringResource(Res.string.flow_name_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmedName = saveAsName.trim()
                            if (trimmedName.isNotBlank()) {
                                val exists = state.flows.any { it.name.equals(trimmedName, ignoreCase = true) }
                                if (exists) {
                                    notificationService.toast(duplicateFlowMsg.format(trimmedName))
                                } else {
                                    viewModel.onEvent(FlowEvent.SaveAs(trimmedName))
                                    showSaveAsDialog = false
                                }
                            }
                        },
                        enabled = saveAsName.isNotBlank()
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveAsDialog = false }) {
                        Text(stringResource(Res.string.dialog_cancel))
                    }
                }
            )
        }
    }
}

@Composable
private fun NodeComponentPlaceholder(node: Node, height: Dp = 120.dp) {
    Surface(
        modifier = Modifier.width(380.dp).height(height),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(node.title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

private fun getPortRelativeOffset(node: Node, portId: String, density: androidx.compose.ui.unit.Density): Offset {
    val headerHeight = 48f
    val bodyTopPadding = 12f
    val rowHeight = 48f
    val spacing = 12f
    val dividerHeight = 0.5f

    val isInput = node.inputs.any { it.id == portId }
    val isOutput = node.outputs.any { it.id == portId }

    val xDp = if (isInput) 19f else 281f

    var yDp = headerHeight + bodyTopPadding
    val numInputs = node.inputs.size
    val numOutputs = node.outputs.size

    if (isInput) {
        val index = node.inputs.indexOfFirst { it.id == portId }
        if (index != -1) {
            yDp += index * (rowHeight + spacing) + (rowHeight / 2f)
        }
    } else if (isOutput) {
        val index = node.outputs.indexOfFirst { it.id == portId }
        if (index != -1) {
            var precedingHeight = numInputs * rowHeight
            var numGaps = numInputs
            if (numInputs > 0) {
                precedingHeight += dividerHeight
                numGaps += 1
            }
            yDp += precedingHeight + (numGaps + index) * spacing + index * rowHeight + (rowHeight / 2f)
        }
    }

    return with(density) {
        Offset(xDp.dp.toPx(), yDp.dp.toPx())
    }
}

private fun findClosestPort(
    boardPosition: Offset,
    flow: Flow,
    connectionStartIsOutput: Boolean,
    scale: Float,
    getPortBoardPosition: (Long, String) -> Offset?
): Pair<Long?, String?> {
    var closestPortId: String? = null
    var closestNodeId: Long? = null
    var minDistance = 30f / scale

    flow.nodes.forEach { n ->
        val portsToTrack = if (connectionStartIsOutput) n.inputs else n.outputs
        portsToTrack.forEach { port ->
            val portBoardPos = getPortBoardPosition(n.id, port.id) ?: return@forEach
            val dist = (boardPosition - portBoardPos).getDistance()
            if (dist < minDistance) {
                minDistance = dist
                closestPortId = port.id
                closestNodeId = n.id
            }
        }
    }
    return Pair(closestNodeId, closestPortId)
}
