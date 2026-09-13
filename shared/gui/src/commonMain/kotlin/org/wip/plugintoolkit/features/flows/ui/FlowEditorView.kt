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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
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
import org.koin.compose.koinInject
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.canConvert
import org.wip.plugintoolkit.api.format
import org.wip.plugintoolkit.api.isCompatibleWith
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.NodeSerializationUtils
import org.wip.plugintoolkit.features.flows.ui.toModelOffset
import org.wip.plugintoolkit.features.flows.ui.canvas.BoardInteractionState
import org.wip.plugintoolkit.features.flows.ui.canvas.ConnectionHitTester
import org.wip.plugintoolkit.features.flows.ui.canvas.StructuredConnectionStartInfo
import org.wip.plugintoolkit.api.ParameterConditionEvaluator
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorViewModel
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEvent
import org.wip.plugintoolkit.features.flows.viewmodel.ReadOnlyReason
import org.wip.plugintoolkit.shared.components.LocalOverlayHost
import org.wip.plugintoolkit.shared.components.OverlayHost
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
import org.wip.plugintoolkit.features.navigation.GlobalRouter

@Composable
fun FlowEditorView(
    viewModel: FlowEditorViewModel,
    notificationService: NotificationService,
    onExit: () -> Unit,
    router: GlobalRouter? = null,
    onNavigateToPluginSetting: ((pluginId: String, settingKey: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val flow = state.flow
    val pluginManager = koinInject<org.wip.plugintoolkit.features.plugin.logic.PluginManager>()
    val pluginLocksState by pluginManager.pluginLocksState.collectAsState()
    val density = androidx.compose.ui.platform.LocalDensity.current

    var boardSize by remember { mutableStateOf(IntSize.Zero) }
    var boardLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var rootLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    var overlayContent by remember { mutableStateOf<(@Composable () -> Unit)?>(null) }
    var overlayBounds by remember { mutableStateOf<Rect?>(null) }
    var overlayOnDismiss by remember { mutableStateOf<(() -> Unit)?>(null) }

    val dropdownOverlay = remember {
        object : OverlayHost {
            override fun show(bounds: Rect, onDismiss: () -> Unit, content: @Composable () -> Unit) {
                overlayBounds = bounds
                overlayContent = content
                overlayOnDismiss = onDismiss
            }

            override fun hide() {
                overlayContent = null
                overlayBounds = null
                overlayOnDismiss = null
            }
        }
    }

    var showSaveAsDialog by remember { mutableStateOf(false) }
    var saveAsName by remember { mutableStateOf("") }

    // State for temporary connection drawing
    var isDrawingConnection by remember { mutableStateOf(false) }
    var connectionStartNodeId by remember { mutableStateOf<Long?>(null) }
    var connectionStartPortId by remember { mutableStateOf<String?>(null) }
    var connectionStartIsOutput by remember { mutableStateOf(true) }
    var connectionCurrentPos by remember { mutableStateOf(Offset.Zero) }
    val interactionState = remember { BoardInteractionState() }
    var structuredConnectionStartInfo by remember { mutableStateOf<StructuredConnectionStartInfo?>(null) }
    var portLayoutVersion by remember { mutableStateOf(0) }
    val portLayouts = remember { mutableStateMapOf<Triple<Long, String, Boolean>, LayoutCoordinates>() }
    val getPortBoardPosition = { nodeId: Long, portId: String, isOutput: Boolean ->
        val coords = portLayouts[Triple(nodeId, portId, isOutput)]
        val boardCoords = boardLayoutCoordinates
        if (coords != null && boardCoords != null && coords.isAttached) {
            val center = boardCoords.localBoundingBoxOf(coords, false).center
            (center - state.offset) / state.scale
        } else {
            null
        }
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

    val problematicConnections = remember(flow.connections, flow.nodes, pluginLocksState) {
        val result = mutableSetOf<Connection>()
        val nodeMap = flow.nodes.associateBy { it.id }
        val effectiveConns = flow.getEffectiveConnections()
        val multiConnectedInputs = effectiveConns.groupBy { Pair(it.targetNodeId, it.targetPortId) }
            .filter { (targetPair, conns) ->
                if (conns.size <= 1) false
                else {
                    val targetNode = nodeMap[targetPair.first]
                    val targetPort = targetNode?.inputs?.find { it.id == targetPair.second }
                    targetPort != null && targetPort.dataType !is DataType.Array
                }
            }.keys

        for (connection in flow.connections) {
            val sourceNode = nodeMap[connection.sourceNodeId]
            val targetNode = nodeMap[connection.targetNodeId]

            if (Pair(connection.targetNodeId, connection.targetPortId) in multiConnectedInputs) {
                result.add(connection)
            }

            var isSourceInactive = false
            if (sourceNode is Node.CapabilityNode) {
                val outPort = sourceNode.outputs.find { it.id == connection.sourcePortId }
                if (outPort?.condition != null) {
                    val currentParams = sourceNode.inputs.associate {
                        it.id to NodeSerializationUtils.anyToJsonElement(it.value ?: it.defaultValue)
                    }
                    val settings = pluginManager.loadPluginSettings(sourceNode.pluginInfo.id).settings
                    val locks = pluginLocksState[sourceNode.pluginInfo.id] ?: emptyMap()
                    if (!ParameterConditionEvaluator.isSatisfied(outPort.condition, currentParams, settings, locks)) {
                        isSourceInactive = true
                    }
                }
            }

            var isTargetInactive = false
            if (targetNode is Node.CapabilityNode) {
                val inPort = targetNode.inputs.find { it.id == connection.targetPortId }
                if (inPort?.condition != null) {
                    val currentParams = targetNode.inputs.associate {
                        it.id to NodeSerializationUtils.anyToJsonElement(it.value ?: it.defaultValue)
                    }
                    val settings = pluginManager.loadPluginSettings(targetNode.pluginInfo.id).settings
                    val locks = pluginLocksState[targetNode.pluginInfo.id] ?: emptyMap()
                    if (!ParameterConditionEvaluator.isSatisfied(inPort.condition, currentParams, settings, locks)) {
                        isTargetInactive = true
                    }
                }
            }

            if (isSourceInactive || isTargetInactive) {
                result.add(connection)
            }
        }
        result
    }

    val isPortTargetable = { node: Node, portId: String, isOutput: Boolean ->
        if (!isOutput) {
            val inPort = node.inputs.find { it.id == portId }
            if (inPort != null && inPort.dataType !is DataType.Array && flow.isInputPortAlreadyConnected(node.id, portId)) {
                false
            } else if (node is Node.CapabilityNode) {
                val isConnected = flow.connections.any { it.targetNodeId == node.id && it.targetPortId == portId }
                if (isConnected) {
                    true
                } else {
                    val port = node.inputs.find { it.id == portId }
                    val condition = (port as? InputPort)?.condition
                    if (condition != null) {
                        val currentParams = node.inputs.associate {
                            it.id to NodeSerializationUtils.anyToJsonElement(it.value ?: it.defaultValue)
                        }
                        val settings = pluginManager.loadPluginSettings(node.pluginInfo.id).settings
                        val locks = pluginLocksState[node.pluginInfo.id] ?: emptyMap()
                        ParameterConditionEvaluator.isSatisfied(condition, currentParams, settings, locks)
                    } else {
                        true
                    }
                }
            } else {
                true
            }
        } else if (node is Node.CapabilityNode) {
            val isConnected = flow.connections.any { it.sourceNodeId == node.id && it.sourcePortId == portId }
            if (isConnected) {
                true
            } else {
                val port = node.outputs.find { it.id == portId }
                val condition = (port as? OutputPort)?.condition
                if (condition != null) {
                    val currentParams = node.inputs.associate {
                        it.id to NodeSerializationUtils.anyToJsonElement(it.value ?: it.defaultValue)
                    }
                    val settings = pluginManager.loadPluginSettings(node.pluginInfo.id).settings
                    val locks = pluginLocksState[node.pluginInfo.id] ?: emptyMap()
                    ParameterConditionEvaluator.isSatisfied(condition, currentParams, settings, locks)
                } else {
                    true
                }
            }
        } else {
            true
        }
    }

    // Capture standard error strings for localization
    val sameNodeWarning = stringResource(Res.string.flow_editor_same_node_warning)
    val incompatibleTypesMsg = stringResource(Res.string.flow_editor_incompatible_types)
    val incompatibleSemanticsMsg = stringResource(Res.string.flow_editor_incompatible_semantics)
    val duplicateFlowMsg = stringResource(Res.string.flow_editor_duplicate_error)
    LaunchedEffect(state.flow.connections) {
        if (state.flow.connections.any { it.waypoints.isNotEmpty() }) {
            viewModel.onEvent(FlowEvent.NormalizeWirePoints)
        }
    }

    val handleConnectionDrop: (Boolean) -> Unit = { isShiftPressed ->
        if (interactionState.isDrawingStructuredConnection || !isDrawingConnection) {
            // Do not handle normal drop during structured connection drawing or if not actively drawing
        } else {
            isDrawingConnection = false
            val startNodeId = connectionStartNodeId
            val startPortId = connectionStartPortId
            val startIsOutput = connectionStartIsOutput
            val hlPortId = highlightedPortId
            val hlNodeId = highlightedNodeId
            val hJuncId = interactionState.hoveredJunctionId
            val currPos = connectionCurrentPos

            connectionStartNodeId = null
            connectionStartPortId = null
            highlightedPortId = null
            highlightedNodeId = null

            if (hlPortId != null && hlNodeId != null && startNodeId != null && startPortId != null) {
                val sourceNodeId = if (startIsOutput) startNodeId else hlNodeId
                val sourcePortId = if (startIsOutput) startPortId else hlPortId
                val targetNodeId = if (startIsOutput) hlNodeId else startNodeId
                val targetPortId = if (startIsOutput) hlPortId else startPortId

                viewModel.onEvent(
                    FlowEvent.TryConnectPorts(
                        sourceNodeId,
                        sourcePortId,
                        targetNodeId,
                        targetPortId,
                        isShiftPressed
                    )
                )
            } else if (hJuncId != null && startNodeId != null && startPortId != null) {
                if (startIsOutput) {
                    viewModel.onEvent(
                        FlowEvent.ConnectPortsWithWaypoints(
                            sourceNodeId = startNodeId,
                            sourcePortId = startPortId,
                            targetNodeId = -1L,
                            targetPortId = "",
                            targetJunctionId = hJuncId,
                            isStructured = false
                        )
                    )
                } else {
                    viewModel.onEvent(
                        FlowEvent.ConnectPortsWithWaypoints(
                            sourceNodeId = -1L,
                            sourcePortId = "",
                            targetNodeId = startNodeId,
                            targetPortId = startPortId,
                            sourceJunctionId = hJuncId,
                            isStructured = false
                        )
                    )
                }
            } else if (startNodeId != null && startPortId != null) {
                val dropScreenPos = (currPos * state.scale) + state.offset
                val closestConnAndProj = ConnectionHitTester.findClosestConnectionWithProjection(
                    position = dropScreenPos,
                    connections = flow.connections,
                    getPortBoardPosition = getPortBoardPosition,
                    scale = state.scale,
                    offset = state.offset,
                    junctions = flow.junctions,
                    curveStyle = state.connectionCurveStyle,
                    roundness = state.connectionRoundness
                )
                if (closestConnAndProj != null) {
                    val (closestConn, splitPos) = closestConnAndProj
                    val branchSrcNodeId = if (startIsOutput) startNodeId else null
                    val branchSrcPortId = if (startIsOutput) startPortId else null
                    val branchTgtNodeId = if (!startIsOutput) startNodeId else null
                    val branchTgtPortId = if (!startIsOutput) startPortId else null
                    viewModel.onEvent(
                        FlowEvent.AddJunctionAndBranch(
                            connection = closestConn,
                            splitPosition = splitPos.toModelOffset(),
                            branchSourceNodeId = branchSrcNodeId,
                            branchSourcePortId = branchSrcPortId,
                            branchTargetNodeId = branchTgtNodeId,
                            branchTargetPortId = branchTgtPortId
                        )
                    )
                } else if (startIsOutput) {
                    viewModel.onEvent(
                        FlowEvent.CreateFloatingConnection(
                            sourceNodeId = startNodeId,
                            sourcePortId = startPortId,
                            floatingTarget = currPos.toModelOffset()
                        )
                    )
                }
            }
        }
    }

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
                if (!state.isReadOnly) {
                    viewModel.onEvent(FlowEvent.DeleteConnection(connection))
                    if (connection.isStructured) {
                        val wps = if (isSource) {
                            connection.waypoints.asReversed().map { it.toComposeOffset() }
                        } else {
                            connection.waypoints.map { it.toComposeOffset() }
                        }
                        structuredConnectionStartInfo = StructuredConnectionStartInfo(
                            nodeId = if (isSource) connection.targetNodeId else connection.sourceNodeId,
                            portId = if (isSource) connection.targetPortId else connection.sourcePortId,
                            isOutput = !isSource,
                            sourceJunctionId = if (isSource) connection.targetJunctionId else connection.sourceJunctionId,
                            initialWaypoints = wps,
                            livePos = offset
                        )
                    } else {
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
                    }
                }
            },
            onConnectionDrag = { boardPosition ->
                connectionCurrentPos = boardPosition
                val isOutput = if (interactionState.isDrawingStructuredConnection) {
                    interactionState.structuredConnectionStartIsOutput
                } else {
                    connectionStartIsOutput
                }
                val (closestNodeId, closestPortId) = findClosestPort(
                    boardPosition, flow, isOutput, state.scale, getPortBoardPosition, isPortTargetable
                )
                highlightedNodeId = closestNodeId
                highlightedPortId = closestPortId
            },
            onConnectionDrop = handleConnectionDrop,
            onMoveConnectionFirst = { viewModel.onEvent(FlowEvent.MoveConnectionFirst(it)) },
            onMoveConnectionLast = { viewModel.onEvent(FlowEvent.MoveConnectionLast(it)) },
            selectedNodeIds = state.selectedNodeIds,
            onSelectNodes = { viewModel.onEvent(FlowEvent.SelectNodes(it)) },
            onClearSelection = { viewModel.onEvent(FlowEvent.ClearSelection) },
            onDeleteSelectedNodes = {
                portLayouts.keys.removeAll { state.selectedNodeIds.contains(it.first) }
                viewModel.onEvent(FlowEvent.DeleteSelectedNodes)
            },
            onCopy = { viewModel.onEvent(FlowEvent.CopySelectedNodes) },
            onPaste = { viewModel.onEvent(FlowEvent.PasteNodes(it)) },
            onUndo = { viewModel.undo() },
            onRedo = { viewModel.redo() },
            nodeSizes = nodeSizes,
            isReadOnly = state.isReadOnly,
            problematicConnections = problematicConnections,
            portLayoutVersion = portLayoutVersion,
            onTogglePaintTool = { viewModel.onEvent(FlowEvent.TogglePaintTool) },
            onToggleWashTool = { viewModel.onEvent(FlowEvent.ToggleWashTool) },
            onSelectPaintColor = { viewModel.onEvent(FlowEvent.SetActivePaintColor(it)) },
            onAddGroup = {
                val centerPos = (Offset(boardSize.width / 2f, boardSize.height / 2f) - state.offset) / state.scale
                viewModel.onEvent(FlowEvent.AddGroup(centerPos.toModelOffset()))
            },
            onAddLabel = {
                val centerPos = (Offset(boardSize.width / 2f, boardSize.height / 2f) - state.offset) / state.scale
                viewModel.onEvent(FlowEvent.AddLabel(centerPos.toModelOffset()))
            },
            onUpdateGroup = { viewModel.onEvent(FlowEvent.UpdateGroup(it)) },
            onDeleteGroup = { viewModel.onEvent(FlowEvent.DeleteGroup(it)) },
            onMoveGroup = { id, delta -> viewModel.onEvent(FlowEvent.MoveGroup(id, delta)) },
            onUpdateLabel = { viewModel.onEvent(FlowEvent.UpdateLabel(it)) },
            onDeleteLabel = { viewModel.onEvent(FlowEvent.DeleteLabel(it)) },
            onMoveLabel = { id, delta -> viewModel.onEvent(FlowEvent.MoveLabel(id, delta)) },
            onChangeConnectionStyle = { viewModel.onEvent(FlowEvent.UpdateConnectionCurveStyle(it)) },
            onChangeConnectionRoundness = { viewModel.onEvent(FlowEvent.UpdateConnectionRoundness(it)) },
            onPaintConnection = { viewModel.onEvent(FlowEvent.PaintConnection(it)) },
            onWashConnection = { viewModel.onEvent(FlowEvent.WashConnection(it)) },
            onPaintGroup = { viewModel.onEvent(FlowEvent.PaintGroup(it)) },
            onWashGroup = { viewModel.onEvent(FlowEvent.WashGroup(it)) },
            onPaintLabel = { viewModel.onEvent(FlowEvent.PaintLabel(it)) },
            onWashLabel = { viewModel.onEvent(FlowEvent.WashLabel(it)) },
            onMoveJunction = { id, delta -> viewModel.onEvent(FlowEvent.MoveJunction(id, delta, isTransient = true)) },
            onEndMoveJunction = { pointMoves, nodeMoves, groupMoves, labelMoves ->
                viewModel.onEvent(FlowEvent.EndMoveJunction(pointMoves, nodeMoves, groupMoves, labelMoves))
            },
            onDeleteJunction = { viewModel.onEvent(FlowEvent.DeleteJunction(it)) },
            onSampleColor = { viewModel.onEvent(FlowEvent.SampleColor(it)) },
            onResizeGroup = { id, delta -> viewModel.onEvent(FlowEvent.ResizeGroup(id, delta.toModelOffset())) },
            onSelectLabels = { viewModel.onEvent(FlowEvent.SelectLabels(it)) },
            onSelectGroups = { viewModel.onEvent(FlowEvent.SelectGroups(it)) },
            onSelectPoints = { viewModel.onEvent(FlowEvent.SelectPoints(it)) },
            onAddWaypoint = { conn, pos -> viewModel.onEvent(FlowEvent.AddWaypoint(conn, pos.toModelOffset())) },
            onMoveWaypoint = { conn, index, newPos ->
                viewModel.onEvent(FlowEvent.MoveWaypoint(conn, index, newPos))
            },
            onDeleteWaypoint = { conn, index -> viewModel.onEvent(FlowEvent.DeleteWaypoint(conn, index)) },
            onDeleteConnectionSegment = { conn, index ->
                viewModel.onEvent(FlowEvent.DeleteConnectionSegment(conn, index))
            },
            onInsertWaypoint = { conn, index, pos ->
                viewModel.onEvent(FlowEvent.AddJunctionAndBranch(conn, pos, index))
            },
            onFinalizeStructuredConnection = { srcNodeId, srcPortId, srcJuncId, tgtNodeId, tgtPortId, waypoints, tgtJuncId ->
                if (waypoints.isNotEmpty()) {
                    viewModel.onEvent(
                        FlowEvent.FinalizeStructuredConnectionWithPoints(
                            sourceNodeId = srcNodeId,
                            sourcePortId = srcPortId,
                            targetNodeId = tgtNodeId,
                            targetPortId = tgtPortId,
                            points = waypoints,
                            sourceJunctionId = srcJuncId,
                            targetJunctionId = tgtJuncId
                        )
                    )
                } else {
                    viewModel.onEvent(
                        FlowEvent.ConnectPortsWithWaypoints(
                            sourceNodeId = srcNodeId ?: -1L,
                            sourcePortId = srcPortId ?: "",
                            targetNodeId = tgtNodeId,
                            targetPortId = tgtPortId,
                            waypoints = emptyList(),
                            sourceJunctionId = srcJuncId,
                            targetJunctionId = tgtJuncId,
                            isStructured = true
                        )
                    )
                }
            },
            onLeaveStructuredConnectionAtLastPoint = { srcNodeId, srcPortId, srcJuncId, waypoints ->
                if (srcNodeId != null && srcPortId != null && waypoints.isNotEmpty()) {
                    val lastPt = waypoints.last()
                    val intermediateWaypoints = waypoints.dropLast(1)
                    viewModel.onEvent(
                        FlowEvent.CreateFloatingConnection(
                            sourceNodeId = srcNodeId,
                            sourcePortId = srcPortId,
                            floatingTarget = lastPt,
                            waypoints = intermediateWaypoints,
                            sourceJunctionId = srcJuncId,
                            isStructured = true
                        )
                    )
                }
            },
            onSplitConnectionAndConnect = { conn, splitPos, srcNodeId, srcPortId, srcJuncId, tgtNodeId, tgtPortId, tgtJuncId, interPts ->
                viewModel.onEvent(
                    FlowEvent.SplitConnectionAndConnect(
                        connection = conn,
                        splitPosition = splitPos,
                        sourceNodeId = srcNodeId,
                        sourcePortId = srcPortId,
                        sourceJunctionId = srcJuncId,
                        targetNodeId = tgtNodeId,
                        targetPortId = tgtPortId,
                        targetJunctionId = tgtJuncId,
                        intermediatePoints = interPts
                    )
                )
            },
            onResetDrawingConnection = {
                isDrawingConnection = false
                connectionStartNodeId = null
                connectionStartPortId = null
                connectionStartIsOutput = true
                connectionCurrentPos = Offset.Zero
                highlightedPortId = null
                highlightedNodeId = null
            },
            structuredConnectionStartInfo = structuredConnectionStartInfo,
            onClearStructuredConnectionStartInfo = { structuredConnectionStartInfo = null },
            interactionState = interactionState,
            onToggleStructuredConnectionMode = {
                viewModel.onEvent(FlowEvent.ToggleStructuredConnectionMode)
            },
            onAddJunctionAndBranch = { conn, pos, segIdx ->
                viewModel.onEvent(FlowEvent.AddJunctionAndBranch(conn, pos.toModelOffset(), segIdx))
            },
            onToggleEyedropper = { viewModel.onEvent(FlowEvent.ToggleEyedropper) },
            onToggleAdvancedConnectionMode = { viewModel.onEvent(FlowEvent.ToggleAdvancedConnectionMode) },
            onPaintSelection = { viewModel.onEvent(FlowEvent.PaintSelection) },
            onWashSelection = { viewModel.onEvent(FlowEvent.WashSelection) }
        ) { hoveredConnection, hoveredNodeId, onHoverNode ->
            CompositionLocalProvider(LocalOverlayHost provides dropdownOverlay) {
                // 1.1 Ghost Preview for Snapping (rendered underneath nodes)
                state.ghostPosition?.let { ghostPos ->
                    val draggedNode = state.flow.nodes.find { it.id == state.draggedNodeId }
                    val heightDp = draggedNode?.let { node ->
                        nodeSizes[node.id]?.let { size ->
                            with(density) { size.height.toDp() }
                        }
                    } ?: ToolkitTheme.dimensions.containerHeightLarge

                    NodeCardContainer(
                        nodePosition = ghostPos.toComposeOffset(),
                        dragOffset = Offset.Zero,
                        scale = state.scale,
                        boardOffset = state.offset,
                        modifier = Modifier.alpha(0.3f)
                    ) {
                        draggedNode?.let { NodeComponentPlaceholder(it, heightDp) }
                    }
                }

                // 1.2 Nodes
                val collapsedGroupNodeIds = remember(flow.groups, flow.nodes) {
                    flow.groups.filter { it.isCollapsed }.flatMap { group ->
                        group.nodeIds + flow.nodes.filter { node ->
                            node.position.x >= group.position.x &&
                            node.position.x <= group.position.x + group.size.x &&
                            node.position.y >= group.position.y &&
                            node.position.y <= group.position.y + group.size.y
                        }.map { it.id }
                    }.toSet()
                }

                flow.nodes.forEach { node ->
                    if (collapsedGroupNodeIds.contains(node.id)) return@forEach
                    key(node.id) {
                        val isDragged = state.draggedNodeId == node.id
                        val isDraggedInSelection = state.draggedNodeId != null && (
                            state.selectedNodeIds.contains(state.draggedNodeId) ||
                            state.selectedGroupIds.contains(state.draggedNodeId) ||
                            state.selectedLabelIds.contains(state.draggedNodeId) ||
                            state.selectedPointIds.contains(state.draggedNodeId)
                        )
                        val isPartOfSelectionDrag = isDraggedInSelection && state.selectedNodeIds.contains(node.id)
                        val isContainedInMovingGroup = state.draggedNodeId != null && (
                            (isDraggedInSelection && state.selectedGroupIds.any { gId -> flow.groups.find { it.id == gId }?.nodeIds?.contains(node.id) == true }) ||
                            (flow.groups.find { it.id == state.draggedNodeId }?.nodeIds?.contains(node.id) == true)
                        )
                        val dragOffset =
                            if (isDragged || isPartOfSelectionDrag || isContainedInMovingGroup) state.currentDragOffset.toComposeOffset() else Offset.Zero


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
                                    if (compatible) null else ToolkitTheme.colors.red
                                } else null
                            } else null

                        val nodeHighlightedPortIds = remember(node.id, nodeHighlightedPortId, hoveredConnection) {
                            val ids = mutableSetOf<String>()
                            if (nodeHighlightedPortId != null) {
                                ids.add(nodeHighlightedPortId)
                            }
                            if (hoveredConnection != null) {
                                if (hoveredConnection.sourceNodeId == node.id) {
                                    ids.add(hoveredConnection.sourcePortId)
                                }
                                if (hoveredConnection.targetNodeId == node.id) {
                                    ids.add(hoveredConnection.targetPortId)
                                }
                            }
                            ids
                        }

                        val isConnectedToHoveredNode = remember(hoveredNodeId, node.id, flow.connections) {
                            if (hoveredNodeId == null) false
                            else flow.connections.any {
                                (it.sourceNodeId == hoveredNodeId && it.targetNodeId == node.id) ||
                                (it.targetNodeId == hoveredNodeId && it.sourceNodeId == node.id)
                            }
                        }

                        val isDimmedByConnectionHover = hoveredConnection != null && 
                                hoveredConnection.sourceNodeId != node.id && 
                                hoveredConnection.targetNodeId != node.id

                        val isDimmedByNodeHover = hoveredNodeId != null &&
                                hoveredNodeId != node.id &&
                                !isConnectedToHoveredNode

                        val isDimmed = isDimmedByConnectionHover || isDimmedByNodeHover
                                
                        val targetAlpha = if (isDimmed) 0.4f else 1f
                        val animatedAlpha by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = targetAlpha,
                            animationSpec = androidx.compose.animation.core.tween(
                                durationMillis = 200,
                                delayMillis = if (isDimmed) 500 else 0
                            ),
                            label = "NodeAlpha"
                        )

                        NodeCardContainer(
                            nodePosition = node.position.toComposeOffset(),
                            dragOffset = dragOffset,
                            scale = state.scale,
                            boardOffset = state.offset,
                            alpha = animatedAlpha
                        ) {


                            NodeComponent(
                                node = node,
                                connectedInputPortIds = flow.connections.filter { it.targetNodeId == node.id }
                                    .map { it.targetPortId }.toSet(),
                                connectedOutputPortIds = flow.connections.filter { it.sourceNodeId == node.id }
                                    .map { it.sourcePortId }.toSet(),
                                inferredTypes = state.inferredTypes,
                                inferredSemanticTypes = state.inferredSemanticTypes,
                                validationErrors = state.validationErrors,
                                isReady = node.isReady(
                                    flow.connections,
                                    if (node is Node.CapabilityNode) pluginManager.loadPluginSettings(node.pluginInfo.id).settings else null,
                                    if (node is Node.CapabilityNode) {
                                        pluginLocksState[node.pluginInfo.id] ?: pluginLocksState.values.fold(emptyMap()) { acc, m -> acc + m }
                                    } else null
                                ),
                                onFocusLost = {
                                    if (viewModel.isAutoSaveEnabled && !state.isReadOnly) {
                                        viewModel.onEvent(FlowEvent.Save)
                                    }
                                },
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
                                onDelete = { id ->
                                    portLayouts.keys.removeAll { it.first == id }
                                    viewModel.onEvent(FlowEvent.DeleteNode(id))
                                },
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
                                onUpdateBoundaryNode = { id, name, dataType, semanticTypes, constraints, isList, isRequired, defaultValue ->
                                     viewModel.onEvent(
                                         FlowEvent.UpdateBoundaryNode(
                                             id,
                                             name,
                                             dataType,
                                             semanticTypes,
                                             constraints,
                                             isList,
                                             isRequired,
                                             defaultValue
                                         )
                                     )
                                 },
                                 onUpdateInputPortDefault = { id, portId, defaultValue ->
                                     viewModel.onEvent(
                                         FlowEvent.UpdateInputPortDefault(
                                             id,
                                             portId,
                                             defaultValue
                                         )
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
                                onPortPositioned = { nodeId, portId, isOutput, coords ->
                                    portLayouts[Triple(nodeId, portId, isOutput)] = coords
                                    portLayoutVersion++
                                },
                                 onPortDisposed = { nodeId, portId, isOutput ->
                                     val targetNode = flow.nodes.find { it.id == nodeId }
                                     val portStillExists = if (isOutput) {
                                         targetNode?.outputs?.any { it.id == portId } == true
                                     } else {
                                         targetNode?.inputs?.any { it.id == portId } == true
                                     }
                                     if (!portStillExists) {
                                         portLayouts.remove(Triple(nodeId, portId, isOutput))
                                         portLayoutVersion++
                                     }
                                 },
                                  onStartConnection = { nodeId, portId, isOutput ->
                                      if (interactionState.isDrawingStructuredConnection) {
                                          if (interactionState.structuredConnectionStartIsOutput != isOutput) {
                                              val finalTargetNodeId = if (interactionState.structuredConnectionStartIsOutput) nodeId else (interactionState.structuredConnectionStartNodeId ?: -1L)
                                              val finalTargetPortId = if (interactionState.structuredConnectionStartIsOutput) portId else (interactionState.structuredConnectionStartPortId ?: "")
                                              val finalSourceNodeId = if (interactionState.structuredConnectionStartIsOutput) (interactionState.structuredConnectionStartNodeId ?: -1L) else nodeId
                                              val finalSourcePortId = if (interactionState.structuredConnectionStartIsOutput) (interactionState.structuredConnectionStartPortId ?: "") else portId

                                              viewModel.onEvent(
                                                  FlowEvent.ConnectPortsWithWaypoints(
                                                      sourceNodeId = finalSourceNodeId,
                                                      sourcePortId = finalSourcePortId,
                                                      sourceJunctionId = interactionState.structuredConnectionSourceJunctionId,
                                                      targetNodeId = finalTargetNodeId,
                                                      targetPortId = finalTargetPortId,
                                                      waypoints = interactionState.structuredConnectionPoints.map { it.toModelOffset() },
                                                      isStructured = true
                                                  )
                                              )
                                              interactionState.resetStructuredConnection()
                                          }
                                      } else if (state.isAdvancedConnectionMode) {
                                          structuredConnectionStartInfo = StructuredConnectionStartInfo(
                                              nodeId = nodeId,
                                              portId = portId,
                                              isOutput = isOutput
                                          )
                                      } else {
                                          isDrawingConnection = true
                                          connectionStartNodeId = nodeId
                                          connectionStartPortId = portId
                                          connectionStartIsOutput = isOutput
                                          getPortBoardPosition(nodeId, portId, isOutput)?.let {
                                              connectionCurrentPos = it
                                          }
                                      }
                                  },
                                onDragConnection = {
                                    // Ignored, BoardCanvas handles it
                                },
                                 onDropConnection = handleConnectionDrop,
                                onPress = { id -> viewModel.onEvent(FlowEvent.BringToFront(id)) },
                                highlightedPortId = nodeHighlightedPortId,
                                highlightedPortIds = nodeHighlightedPortIds,
                                highlightedPortColor = nodeHighlightedPortColor,
                                onHoverNode = onHoverNode,
                                stateScale = state.scale,
                                stateOffset = state.offset,
                                selectedNodeIds = state.selectedNodeIds,
                                isReadOnly = state.isReadOnly,
                                isPaintToolActive = state.isPaintToolActive,
                                isWashToolActive = state.isWashToolActive,
                                onPaintNode = { nodeId, isForce -> viewModel.onEvent(FlowEvent.PaintNode(nodeId, isForce)) },
                                onWashNode = { nodeId -> viewModel.onEvent(FlowEvent.WashNode(nodeId)) },
                                modifier = Modifier.onSizeChanged { size ->
                                    nodeSizes[node.id] = size
                                }
                            )
                        }
                    }
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
                onClick = handlePaletteClick,
                hasUnsavedChanges = state.hasUnsavedChanges,
                onNavigateToPluginSetting = onNavigateToPluginSetting
            )
        }

        // 3. Top Status Bar (Overlay Layer)
        FlowEditorTopBar(
            flowName = flow.name,
            isReadOnly = state.isReadOnly,
            readOnlyReasons = state.readOnlyReasons,
            hasUnsavedChanges = state.hasUnsavedChanges,
            onSave = {
                if (flow.name.isBlank()) {
                    saveAsName = ""
                    showSaveAsDialog = true
                } else {
                    viewModel.onEvent(FlowEvent.Save)
                }
            },
            onExit = onExit,
            modifier = Modifier.align(Alignment.TopEnd)
        )

        // 4. Global Dragging Preview (Highest Layer)
        draggingNodeFromPalette?.let { node ->
            Box(
                modifier = Modifier
                    .offset { IntOffset(draggingNodePos.x.roundToInt(), draggingNodePos.y.roundToInt()) }
                    .graphicsLayer(
                        scaleX = draggingNodeScale,
                        scaleY = draggingNodeScale,
                        transformOrigin = TransformOrigin(0f, 0f)
                    )
                    .alpha(0.7f)
            ) {
                PaletteItemPreview(node)
            }
        }

        state.pendingConnection?.let { pendingConn ->
            IncompatibleConnectionDialog(
                pendingConnection = pendingConn,
                onConvertAndConnect = {
                    viewModel.onEvent(
                        FlowEvent.AutoConvertAndConnect(
                            pendingConn.sourceNodeId,
                            pendingConn.sourcePortId,
                            pendingConn.targetNodeId,
                            pendingConn.targetPortId
                        )
                    )
                    viewModel.onEvent(FlowEvent.CancelPendingConnection)
                },
                onDismiss = { viewModel.onEvent(FlowEvent.CancelPendingConnection) }
            )
        }

        if (showSaveAsDialog) {
            FlowEditorSaveAsDialog(
                initialName = saveAsName,
                existingFlowNames = state.flows.map { it.name },
                duplicateErrorMessage = duplicateFlowMsg,
                onShowToast = { notificationService.toast(it) },
                onConfirm = {
                    viewModel.onEvent(FlowEvent.SaveAs(it))
                    showSaveAsDialog = false
                },
                onDismiss = { showSaveAsDialog = false }
            )
        }
        
        overlayContent?.invoke()
    }
}

