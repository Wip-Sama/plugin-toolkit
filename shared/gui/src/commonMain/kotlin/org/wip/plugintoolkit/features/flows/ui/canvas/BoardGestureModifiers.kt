package org.wip.plugintoolkit.features.flows.ui.canvas

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isForwardPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import org.wip.plugintoolkit.features.shortcuts.logic.ShortcutManager
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutActionId
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutGesture
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutPointerButton
import org.wip.plugintoolkit.features.shortcuts.ui.shortcutDrag
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowJunction
import org.wip.plugintoolkit.features.flows.model.FlowLabel
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.ui.snapToGrid
import org.wip.plugintoolkit.features.flows.ui.toComposeOffset
import org.wip.plugintoolkit.features.flows.ui.toModelOffset
import org.wip.plugintoolkit.features.flows.utils.SplineMathUtils
import org.wip.plugintoolkit.features.settings.model.ConnectionCurveStyle
import org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode

internal fun isPointerOverAnyNode(
    screenPos: Offset,
    nodes: List<Node>,
    nodeSizes: Map<Long, IntSize>,
    scale: Float,
    offset: Offset,
    defaultNodeWidthPx: Float,
    densityValue: Float,
    collapsedGroupNodeIds: Set<Long> = emptySet(),
    hoveredNodeId: Long? = null
): Boolean {
    if (nodes.isEmpty()) return false
    val boardPos = (screenPos - offset) / scale
    if (hoveredNodeId != null && hoveredNodeId !in collapsedGroupNodeIds) {
        val hoveredNode = nodes.find { it.id == hoveredNodeId }
        if (hoveredNode != null) {
            val nodeLeft = hoveredNode.position.x
            val nodeTop = hoveredNode.position.y
            val nodeWidth = nodeSizes[hoveredNode.id]?.width?.toFloat() ?: defaultNodeWidthPx
            val nodeHeight = nodeSizes[hoveredNode.id]?.height?.toFloat() ?: (180f * densityValue)
            val nodeRight = nodeLeft + nodeWidth
            val nodeBottom = nodeTop + nodeHeight
            if (boardPos.x in (nodeLeft - 4f)..(nodeRight + 4f) && boardPos.y in (nodeTop - 4f)..(nodeBottom + 4f)) {
                return true
            }
        }
    }
    return nodes.any { node ->
        if (node.id in collapsedGroupNodeIds) return@any false
        val nodeLeft = node.position.x
        val nodeTop = node.position.y
        val nodeWidth = nodeSizes[node.id]?.width?.toFloat() ?: defaultNodeWidthPx
        val nodeHeight = nodeSizes[node.id]?.height?.toFloat() ?: (180f * densityValue)
        val nodeRight = nodeLeft + nodeWidth
        val nodeBottom = nodeTop + nodeHeight
        boardPos.x in nodeLeft..nodeRight && boardPos.y in nodeTop..nodeBottom
    }
}

fun isPointerOverAnyGroup(
    screenPos: Offset,
    groups: List<FlowGroup>,
    scale: Float,
    offset: Offset
): Boolean {
    if (groups.isEmpty()) return false
    val boardPos = (screenPos - offset) / scale
    return groups.any { group ->
        boardPos.x >= group.position.x &&
        boardPos.x <= group.position.x + group.size.x &&
        boardPos.y >= group.position.y &&
        boardPos.y <= group.position.y + group.size.y
    }
}

fun isPointerOverAnyLabel(
    screenPos: Offset,
    labels: List<FlowLabel>,
    scale: Float,
    offset: Offset,
    densityValue: Float
): Boolean {
    if (labels.isEmpty()) return false
    val boardPos = (screenPos - offset) / scale
    return labels.any { label ->
        val width = maxOf(120f, (label.text.length * 9f) + 32f)
        val height = 48f
        boardPos.x >= label.position.x &&
        boardPos.x <= label.position.x + width &&
        boardPos.y >= label.position.y &&
        boardPos.y <= label.position.y + height
    }
}

fun Modifier.boardConnectionTapGesture(
    interactionState: BoardInteractionState,
    connections: List<Connection>,
    scale: Float,
    offset: Offset,
    getPortBoardPosition: (Long, String, Boolean) -> Offset?,
    focusRequester: FocusRequester,
    nodes: List<Node> = emptyList(),
    nodeSizes: Map<Long, IntSize> = emptyMap(),
    density: Density? = null,
    defaultNodeWidthPx: Float = 0f,
    onClearSelection: () -> Unit,
    isPaintToolActive: Boolean = false,
    isWashToolActive: Boolean = false,
    isEyedropperActive: Boolean = false,
    onPaintConnection: ((Connection) -> Unit)? = null,
    onWashConnection: ((Connection) -> Unit)? = null,
    onSampleColor: ((String) -> Unit)? = null,
    junctions: List<FlowJunction> = emptyList(),
    curveStyle: ConnectionCurveStyle = ConnectionCurveStyle.Bezier,
    roundness: Float = 0.5f,
    orthogonalStepMode: OrthogonalStepMode = OrthogonalStepMode.Auto,
    orthogonalPortLead: Boolean = false,
    groups: List<FlowGroup> = emptyList(),
    labels: List<FlowLabel> = emptyList()
): Modifier = this.pointerInput(
    connections,
    getPortBoardPosition,
    scale,
    offset,
    nodes,
    nodeSizes,
    isPaintToolActive,
    isWashToolActive,
    isEyedropperActive,
    junctions,
    curveStyle,
    roundness,
    orthogonalStepMode,
    orthogonalPortLead,
    groups,
    labels
) {
    val d = density?.density ?: 1f
    detectTapGestures(
        onTap = { tapOffset ->
            val collapsedGroupNodeIds = groups.filter { it.isCollapsed }.flatMap { it.nodeIds }.toSet()
            val isOverNode = isPointerOverAnyNode(
                screenPos = tapOffset,
                nodes = nodes,
                nodeSizes = nodeSizes,
                scale = scale,
                offset = offset,
                defaultNodeWidthPx = defaultNodeWidthPx,
                densityValue = d,
                collapsedGroupNodeIds = collapsedGroupNodeIds,
                hoveredNodeId = interactionState.hoveredNodeId
            )
            val isOverGroup = isPointerOverAnyGroup(tapOffset, groups, scale, offset)
            val isOverLabel = isPointerOverAnyLabel(tapOffset, labels, scale, offset, d)
            if (isOverNode || isOverGroup || isOverLabel) {
                return@detectTapGestures
            }
            focusRequester.requestFocus()
            val bestConnection = ConnectionHitTester.findClosestConnection(
                position = tapOffset,
                connections = connections,
                getPortBoardPosition = getPortBoardPosition,
                scale = scale,
                offset = offset,
                junctions = junctions,
                curveStyle = curveStyle,
                roundness = roundness,
                groups = groups,
                density = d,
                stepMode = orthogonalStepMode,
                orthogonalPortLead = orthogonalPortLead,
                nodes = nodes
            )
            if (bestConnection != null) {
                if (isEyedropperActive && onSampleColor != null) {
                    onSampleColor(bestConnection.color ?: "#808080")
                } else if (isPaintToolActive && onPaintConnection != null) {
                    onPaintConnection(bestConnection)
                } else if (isWashToolActive && onWashConnection != null) {
                    onWashConnection(bestConnection)
                }
            } else {
                interactionState.selectedConnection = null
                interactionState.selectedJunctionId = null
                onClearSelection()
            }
        }
    )
}

fun Modifier.boardPanGesture(
    focusRequester: FocusRequester,
    shortcutManager: ShortcutManager? = null,
    onPan: (Offset) -> Unit
): Modifier = this.shortcutDrag(
    shortcutManager = shortcutManager,
    actionId = ShortcutActionId.FLOW_PAN_CANVAS,
    onDragStart = { focusRequester.requestFocus() },
    onDrag = onPan
)

@Composable
fun Modifier.boardSelectionBoxGesture(
    interactionState: BoardInteractionState,
    isDrawingConnection: Boolean,
    nodes: List<Node>,
    nodeSizes: Map<Long, IntSize>,
    density: Density,
    scale: Float,
    offset: Offset,
    defaultNodeWidthPx: Float,
    focusRequester: FocusRequester,
    onSelectNodes: (Set<Long>) -> Unit,
    labels: List<FlowLabel> = emptyList(),
    groups: List<FlowGroup> = emptyList(),
    junctions: List<FlowJunction> = emptyList(),
    onSelectLabels: ((Set<Long>) -> Unit)? = null,
    onSelectGroups: ((Set<Long>) -> Unit)? = null,
    onSelectPoints: ((Set<Long>) -> Unit)? = null,
    isPaintToolActive: Boolean = false,
    isWashToolActive: Boolean = false,
    onPaintSelection: (() -> Unit)? = null,
    onWashSelection: (() -> Unit)? = null,
    shortcutManager: ShortcutManager? = null
): Modifier {
    val currentScale by rememberUpdatedState(scale)
    val currentOffset by rememberUpdatedState(offset)
    val currentNodes by rememberUpdatedState(nodes)
    val currentNodeSizes by rememberUpdatedState(nodeSizes)
    val currentDensity by rememberUpdatedState(density)
    val currentDefaultNodeWidthPx by rememberUpdatedState(defaultNodeWidthPx)
    val currentFocusRequester by rememberUpdatedState(focusRequester)
    val currentOnSelectNodes by rememberUpdatedState(onSelectNodes)
    val currentLabels by rememberUpdatedState(labels)
    val currentGroups by rememberUpdatedState(groups)
    val currentJunctions by rememberUpdatedState(junctions)
    val currentOnSelectLabels by rememberUpdatedState(onSelectLabels)
    val currentOnSelectGroups by rememberUpdatedState(onSelectGroups)
    val currentOnSelectPoints by rememberUpdatedState(onSelectPoints)
    val currentIsPaintToolActive by rememberUpdatedState(isPaintToolActive)
    val currentIsWashToolActive by rememberUpdatedState(isWashToolActive)
    val currentOnPaintSelection by rememberUpdatedState(onPaintSelection)
    val currentOnWashSelection by rememberUpdatedState(onWashSelection)
    val currentShortcutManager by rememberUpdatedState(shortcutManager)
    val currentIsDrawingConnection by rememberUpdatedState(isDrawingConnection)

    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val isBoxSelectTriggered = if (currentShortcutManager != null) {
                    currentShortcutManager!!.matchesPointer(ShortcutActionId.FLOW_BOX_SELECT, event, ShortcutGesture.Drag)
                } else {
                    !event.keyboardModifiers.isCtrlPressed &&
                    !event.keyboardModifiers.isShiftPressed &&
                    !event.keyboardModifiers.isAltPressed &&
                    !event.keyboardModifiers.isMetaPressed &&
                    event.buttons.isPrimaryPressed &&
                    !event.buttons.isSecondaryPressed &&
                    !event.buttons.isTertiaryPressed
                }

                if (event.type == PointerEventType.Press && isBoxSelectTriggered) {
                    val startChange = event.changes.firstOrNull() ?: continue
                    if (startChange.isConsumed) continue

                    val collapsedGroupNodeIds = currentGroups.filter { it.isCollapsed }.flatMap { it.nodeIds }.toSet()
                    val isOverNode = isPointerOverAnyNode(
                        screenPos = startChange.position,
                        nodes = currentNodes,
                        nodeSizes = currentNodeSizes,
                        scale = currentScale,
                        offset = currentOffset,
                        defaultNodeWidthPx = currentDefaultNodeWidthPx,
                        densityValue = currentDensity.density,
                        collapsedGroupNodeIds = collapsedGroupNodeIds,
                        hoveredNodeId = interactionState.hoveredNodeId
                    )
                    val isOverGroup = isPointerOverAnyGroup(startChange.position, currentGroups, currentScale, currentOffset)
                    val isOverLabel = isPointerOverAnyLabel(startChange.position, currentLabels, currentScale, currentOffset, currentDensity.density)
                    if (isOverNode || isOverGroup || isOverLabel) continue

                    val isOverElement = interactionState.hoveredNodeId != null ||
                        interactionState.hoveredJunctionId != null ||
                        interactionState.hoveredWaypoint != null ||
                        interactionState.hoveredMidpoint != null ||
                        interactionState.hoveredConnection != null ||
                        interactionState.draggingJunctionId != null ||
                        interactionState.draggingWaypoint != null ||
                        interactionState.pendingMidpoint != null
                    if (isOverElement) continue

                    if (currentIsDrawingConnection || interactionState.isDrawingStructuredConnection) continue

                    val startScreenPos = startChange.position
                    val modelStart = (startScreenPos - currentOffset) / currentScale
                    var dragStarted = false

                    // Determine active pointer button from effective triggers
                    val effectiveTriggers = currentShortcutManager?.getEffectiveTriggers(ShortcutActionId.FLOW_BOX_SELECT)
                    val matchedTrigger = effectiveTriggers?.firstOrNull { trigger ->
                        val modMatch = trigger.matchesModifiers(
                            ctrl = event.keyboardModifiers.isCtrlPressed,
                            shift = event.keyboardModifiers.isShiftPressed,
                            alt = event.keyboardModifiers.isAltPressed,
                            meta = event.keyboardModifiers.isMetaPressed
                        )
                        if (!modMatch) return@firstOrNull false
                        when (trigger.pointerButton) {
                            ShortcutPointerButton.Left -> event.buttons.isPrimaryPressed && !event.buttons.isSecondaryPressed && !event.buttons.isTertiaryPressed
                            ShortcutPointerButton.Right -> event.buttons.isSecondaryPressed
                            ShortcutPointerButton.Middle -> event.buttons.isTertiaryPressed
                            ShortcutPointerButton.Back -> event.buttons.isBackPressed
                            ShortcutPointerButton.Forward -> event.buttons.isForwardPressed
                            ShortcutPointerButton.None -> event.buttons.isPrimaryPressed
                        }
                    }
                    val activePointerButton = matchedTrigger?.pointerButton ?: when {
                        event.buttons.isPrimaryPressed -> ShortcutPointerButton.Left
                        event.buttons.isSecondaryPressed -> ShortcutPointerButton.Right
                        event.buttons.isTertiaryPressed -> ShortcutPointerButton.Middle
                        else -> ShortcutPointerButton.Left
                    }

                    try {
                        while (true) {
                            val dragEvent = awaitPointerEvent()
                            val isButtonDown = when (activePointerButton) {
                                ShortcutPointerButton.Left -> dragEvent.buttons.isPrimaryPressed
                                ShortcutPointerButton.Right -> dragEvent.buttons.isSecondaryPressed
                                ShortcutPointerButton.Middle -> dragEvent.buttons.isTertiaryPressed
                                ShortcutPointerButton.Back -> dragEvent.buttons.isBackPressed
                                ShortcutPointerButton.Forward -> dragEvent.buttons.isForwardPressed
                                ShortcutPointerButton.None -> dragEvent.buttons.isPrimaryPressed
                            }

                            if (!isButtonDown || dragEvent.type == PointerEventType.Release) {
                                if (dragStarted) {
                                    if (currentIsPaintToolActive) {
                                        currentOnPaintSelection?.invoke()
                                    } else if (currentIsWashToolActive) {
                                        currentOnWashSelection?.invoke()
                                    }
                                    interactionState.clearSelectionBox()
                                } else {
                                    // Tapped empty canvas without dragging: clear selection and reset focus
                                    currentFocusRequester.requestFocus()
                                    currentOnSelectNodes(emptySet())
                                    currentOnSelectLabels?.invoke(emptySet())
                                    currentOnSelectGroups?.invoke(emptySet())
                                    currentOnSelectPoints?.invoke(emptySet())
                                    interactionState.selectedConnection = null
                                    interactionState.selectedJunctionId = null
                                }
                                break
                            }

                            if (dragEvent.type == PointerEventType.Move || dragEvent.type == PointerEventType.Scroll) {
                                val currentChange = dragEvent.changes.firstOrNull() ?: continue
                                val currentScreenPos = currentChange.position
                                val currentModelPos = (currentScreenPos - currentOffset) / currentScale

                                if (!dragStarted) {
                                    val dist = (currentScreenPos - startScreenPos).getDistance()
                                    if (dist >= 6f) {
                                        dragStarted = true
                                        currentFocusRequester.requestFocus()
                                        interactionState.selectionStart = modelStart
                                        interactionState.selectionEnd = currentModelPos
                                        currentShortcutManager?.eat(dragEvent, ShortcutActionId.FLOW_BOX_SELECT) ?: currentChange.consume()
                                    }
                                } else {
                                    interactionState.selectionEnd = currentModelPos
                                    currentShortcutManager?.eat(dragEvent, ShortcutActionId.FLOW_BOX_SELECT) ?: currentChange.consume()

                                    val selectLeft = minOf(modelStart.x, currentModelPos.x)
                                    val selectRight = maxOf(modelStart.x, currentModelPos.x)
                                    val selectTop = minOf(modelStart.y, currentModelPos.y)
                                    val selectBottom = maxOf(modelStart.y, currentModelPos.y)

                                    val selectedNodeIds = mutableSetOf<Long>()
                                    currentNodes.forEach { node ->
                                        val nodeLeft = node.position.x
                                        val nodeTop = node.position.y
                                        val nodeWidth = currentNodeSizes[node.id]?.width?.toFloat() ?: currentDefaultNodeWidthPx
                                        val nodeHeight = currentNodeSizes[node.id]?.height?.toFloat() ?: (180f * currentDensity.density)
                                        val nodeRight = nodeLeft + nodeWidth
                                        val nodeBottom = nodeTop + nodeHeight

                                        if (selectLeft < nodeRight && selectRight > nodeLeft &&
                                            selectTop < nodeBottom && selectBottom > nodeTop
                                        ) {
                                            selectedNodeIds.add(node.id)
                                        }
                                    }
                                    currentOnSelectNodes(selectedNodeIds)

                                    if (currentOnSelectLabels != null) {
                                        val selectedLabelIds = mutableSetOf<Long>()
                                        currentLabels.forEach { label ->
                                            val lLeft = label.position.x
                                            val lTop = label.position.y
                                            val lWidth = maxOf(80f, label.text.length * 9f)
                                            val lHeight = 36f
                                            val lRight = lLeft + lWidth
                                            val lBottom = lTop + lHeight

                                            if (selectLeft < lRight && selectRight > lLeft &&
                                                selectTop < lBottom && selectBottom > lTop
                                            ) {
                                                selectedLabelIds.add(label.id)
                                            }
                                        }
                                        currentOnSelectLabels?.invoke(selectedLabelIds)
                                    }

                                    if (currentOnSelectGroups != null) {
                                        val selectedGroupIds = mutableSetOf<Long>()
                                        currentGroups.forEach { group ->
                                            val gLeft = group.position.x
                                            val gTop = group.position.y
                                            val gWidth = group.size.x
                                            val gHeight = if (group.isCollapsed) 48f else group.size.y
                                            val gRight = gLeft + gWidth
                                            val gBottom = gTop + gHeight

                                            if (selectLeft < gRight && selectRight > gLeft &&
                                                selectTop < gBottom && selectBottom > gTop
                                            ) {
                                                selectedGroupIds.add(group.id)
                                            }
                                        }
                                        currentOnSelectGroups?.invoke(selectedGroupIds)
                                    }

                                    if (currentOnSelectPoints != null) {
                                        val selectedPtIds = mutableSetOf<Long>()
                                        currentJunctions.forEach { junc ->
                                            val jx = junc.position.x
                                            val jy = junc.position.y
                                            if (selectLeft < jx + 8f && selectRight > jx - 8f &&
                                                selectTop < jy + 8f && selectBottom > jy - 8f
                                            ) {
                                                selectedPtIds.add(junc.id)
                                            }
                                        }
                                        currentOnSelectPoints?.invoke(selectedPtIds)
                                    }
                                }
                            }
                        }
                    } finally {
                        if (dragStarted) {
                            interactionState.clearSelectionBox()
                        }
                    }
                }
            }
        }
    }
}

internal class BoardGestureSessionState {
    var junctionDragStartPointPositions: Map<Long, org.wip.plugintoolkit.features.flows.model.Offset> = emptyMap()
    var junctionDragStartNodePositions: Map<Long, org.wip.plugintoolkit.features.flows.model.Offset> = emptyMap()
    var junctionDragStartGroupPositions: Map<Long, org.wip.plugintoolkit.features.flows.model.Offset> = emptyMap()
    var junctionDragStartLabelPositions: Map<Long, org.wip.plugintoolkit.features.flows.model.Offset> = emptyMap()
    var junctionDragStartPointerPosition: Offset? = null
    var junctionLastDispatchedBoardPos: Offset? = null
    var segmentDragStartPos: Offset? = null

    var rightClickStartPos: Offset? = null
    var rightClickLastPos: Offset? = null
    var rightClickDidDrag: Boolean = false
    var middleClickLastPos: Offset? = null

    fun resetRightClick() {
        rightClickStartPos = null
        rightClickLastPos = null
        rightClickDidDrag = false
    }

    fun resetJunctionDrag() {
        junctionDragStartPointPositions = emptyMap()
        junctionDragStartNodePositions = emptyMap()
        junctionDragStartGroupPositions = emptyMap()
        junctionDragStartLabelPositions = emptyMap()
        junctionDragStartPointerPosition = null
        junctionLastDispatchedBoardPos = null
    }
}

internal class BoardPointerGestureContext(
    val interactionState: BoardInteractionState,
    val session: BoardGestureSessionState,
    val isDrawingConnection: () -> Boolean,
    val scale: () -> Float,
    val offset: () -> Offset,
    val nodes: () -> List<Node>,
    val connections: () -> List<Connection>,
    val junctions: () -> List<FlowJunction>,
    val groups: () -> List<FlowGroup>,
    val labels: () -> List<FlowLabel>,
    val nodeSizes: () -> Map<Long, IntSize>,
    val density: () -> Density,
    val defaultNodeWidthPx: () -> Float,
    val getPortBoardPosition: (Long, String, Boolean) -> Offset?,
    val onZoom: (Float, Offset, Boolean) -> Unit,
    val onConnectionDrag: (Offset) -> Unit,
    val onConnectionDrop: (Boolean) -> Unit,
    val onDeleteConnection: (Connection) -> Unit,
    val onDetachConnection: (Connection, Boolean, Offset) -> Unit,
    val onMoveJunction: ((Long, org.wip.plugintoolkit.features.flows.model.Offset) -> Unit)?,
    val onEndMoveJunction: ((Map<Long, Pair<org.wip.plugintoolkit.features.flows.model.Offset, org.wip.plugintoolkit.features.flows.model.Offset>>, Map<Long, Pair<org.wip.plugintoolkit.features.flows.model.Offset, org.wip.plugintoolkit.features.flows.model.Offset>>, Map<Long, Pair<org.wip.plugintoolkit.features.flows.model.Offset, org.wip.plugintoolkit.features.flows.model.Offset>>, Map<Long, Pair<org.wip.plugintoolkit.features.flows.model.Offset, org.wip.plugintoolkit.features.flows.model.Offset>>) -> Unit)?,
    val onDeleteJunction: ((Long) -> Unit)?,
    val onAddWaypoint: ((Connection, Offset) -> Unit)?,
    val onMoveWaypoint: ((Connection, Int, org.wip.plugintoolkit.features.flows.model.Offset) -> Unit)?,
    val onDeleteWaypoint: ((Connection, Int) -> Unit)?,
    val onInsertWaypoint: ((Connection, Int, org.wip.plugintoolkit.features.flows.model.Offset) -> Unit)?,
    val onFinalizeStructuredConnection: ((Long?, String?, Long?, Long, String, List<org.wip.plugintoolkit.features.flows.model.Offset>, Long?) -> Unit)?,
    val connectionStartNodeId: () -> Long?,
    val connectionStartPortId: () -> String?,
    val connectionStartIsOutput: () -> Boolean,
    val curveStyle: () -> ConnectionCurveStyle,
    val roundness: () -> Float,
    val orthogonalStepMode: () -> OrthogonalStepMode,
    val orthogonalPortLead: () -> Boolean,
    val isAdvancedConnectionMode: () -> Boolean,
    val onAddJunctionAndBranch: ((Connection, Offset, Int) -> Unit)?,
    val onDeleteConnectionSegment: ((Connection, Int) -> Unit)?,
    val selectedPointIds: () -> Set<Long>,
    val selectedNodeIds: () -> Set<Long>,
    val selectedGroupIds: () -> Set<Long>,
    val selectedLabelIds: () -> Set<Long>,
    val onSelectPoints: ((Set<Long>) -> Unit)?,
    val onSplitConnectionAndConnect: ((Connection, org.wip.plugintoolkit.features.flows.model.Offset, Long?, String?, Long?, Long?, String?, Long?, List<org.wip.plugintoolkit.features.flows.model.Offset>) -> Unit)?,
    val onResetDrawingConnection: (() -> Unit)?,
    val shortcutManager: () -> ShortcutManager?,
    val highlightedNodeId: () -> Long?,
    val highlightedPortId: () -> String?,
    val onSelectNodes: ((Set<Long>) -> Unit)?,
    val onSelectGroups: ((Set<Long>) -> Unit)?,
    val onSelectLabels: ((Set<Long>) -> Unit)?,
    val isPaintToolActive: () -> Boolean,
    val isWashToolActive: () -> Boolean,
    val isEyedropperActive: () -> Boolean,
    val onPaintConnection: ((Connection) -> Unit)?,
    val onWashConnection: ((Connection) -> Unit)?,
    val onPaintJunction: ((Long) -> Unit)?,
    val onWashJunction: ((Long) -> Unit)?,
    val onSampleColor: ((String) -> Unit)?,
    val onMoveSegment: ((Connection, Int, org.wip.plugintoolkit.features.flows.model.Offset) -> Unit)?,
    val onEndMoveSegment: ((Connection, Int, org.wip.plugintoolkit.features.flows.model.Offset) -> Unit)?
)

private fun handlePointerScroll(
    event: PointerEvent,
    change: PointerInputChange,
    position: Offset,
    ctx: BoardPointerGestureContext
) {
    val scrollDelta = change.scrollDelta
    val delta = if (scrollDelta.y != 0f) scrollDelta.y else scrollDelta.x
    if (delta != 0f) {
        val isShiftPressed = event.keyboardModifiers.isShiftPressed
        ctx.onZoom(-delta, position, isShiftPressed)
    }
}

private fun handleActiveDragsOnMove(
    event: PointerEvent,
    position: Offset,
    prevPointerPosition: Offset,
    ctx: BoardPointerGestureContext
) {
    val interactionState = ctx.interactionState
    val session = ctx.session
    val draggingJuncId = interactionState.draggingJunctionId
    if (draggingJuncId != null) {
        val isCtrl = event.keyboardModifiers.isCtrlPressed || interactionState.isCtrlModifierPressed
        val startPos = session.junctionDragStartPointPositions[draggingJuncId]
        val startPointer = session.junctionDragStartPointerPosition
        if (startPos != null && startPointer != null) {
            val totalDelta = (position - startPointer) / ctx.scale()
            val targetBoardPos = if (isCtrl) {
                (startPos.toComposeOffset() + totalDelta).snapToGrid()
            } else {
                startPos.toComposeOffset() + totalDelta
            }
            val prevBoardPos = session.junctionLastDispatchedBoardPos ?: startPos.toComposeOffset()
            val deltaToApply = targetBoardPos - prevBoardPos
            if (deltaToApply.x != 0f || deltaToApply.y != 0f) {
                ctx.onMoveJunction?.invoke(draggingJuncId, deltaToApply.toModelOffset())
                session.junctionLastDispatchedBoardPos = targetBoardPos
            }
        } else {
            val delta = (position - prevPointerPosition) / ctx.scale()
            ctx.onMoveJunction?.invoke(draggingJuncId, delta.toModelOffset())
        }
        ctx.shortcutManager()?.eat(event, ShortcutActionId.FLOW_MOVE_POINT) ?: event.changes.forEach { it.consume() }
    }

    val draggingWp = interactionState.draggingWaypoint
    if (draggingWp != null) {
        var boardPos = (position - ctx.offset()) / ctx.scale()
        val isCtrl = event.keyboardModifiers.isCtrlPressed || interactionState.isCtrlModifierPressed
        if (isCtrl) {
            boardPos = boardPos.snapToGrid()
        }
        ctx.onMoveWaypoint?.invoke(draggingWp.first, draggingWp.second, boardPos.toModelOffset())
        ctx.shortcutManager()?.eat(event, ShortcutActionId.FLOW_MOVE_POINT) ?: event.changes.forEach { it.consume() }
    }

    val draggingSeg = interactionState.draggingSegment
    if (draggingSeg != null) {
        val delta = (position - prevPointerPosition) / ctx.scale()
        ctx.onMoveSegment?.invoke(draggingSeg.connection, draggingSeg.segmentIndex, delta.toModelOffset())
        ctx.shortcutManager()?.eat(event, ShortcutActionId.FLOW_MOVE_POINT) ?: event.changes.forEach { it.consume() }
    }
}

private fun handleWireSnappingOnMove(
    position: Offset,
    isOverNode: Boolean,
    ctx: BoardPointerGestureContext
) {
    val interactionState = ctx.interactionState
    if (ctx.isDrawingConnection() || interactionState.isDrawingStructuredConnection) {
        val closestJuncForSnap = if (!isOverNode) {
            ConnectionHitTester.findClosestJunction(
                position = position,
                junctions = ctx.junctions(),
                scale = ctx.scale(),
                offset = ctx.offset(),
                hitRadius = 24f * ctx.scale()
            )
        } else null
        if (closestJuncForSnap != null) {
            interactionState.hoveredJunctionId = closestJuncForSnap.id
            interactionState.snappedWirePoint = closestJuncForSnap.position.toComposeOffset()
            interactionState.snappedWireConnection = null
            interactionState.snappedWireSegmentIndex = null
        } else {
            val connProj = if (!isOverNode) {
                ConnectionHitTester.findClosestConnectionWithProjection(
                    position = position,
                    connections = ctx.connections(),
                    getPortBoardPosition = ctx.getPortBoardPosition,
                    scale = ctx.scale(),
                    offset = ctx.offset(),
                    initialMinDistance = 24f * ctx.scale(),
                    junctions = ctx.junctions(),
                    curveStyle = ctx.curveStyle(),
                    roundness = ctx.roundness(),
                    stepMode = ctx.orthogonalStepMode(),
                    orthogonalPortLead = ctx.orthogonalPortLead(),
                    nodes = ctx.nodes()
                )
            } else null
            if (connProj != null) {
                interactionState.snappedWirePoint = connProj.projectedPoint
                interactionState.snappedWireConnection = connProj.connection
                interactionState.snappedWireSegmentIndex = connProj.segmentIndex
                interactionState.hoveredJunctionId = null
            } else {
                interactionState.clearSnapping()
            }
        }
    } else {
        interactionState.clearSnapping()
    }
}

private fun handleDrawingConnectionOnMove(
    event: PointerEvent,
    position: Offset,
    ctx: BoardPointerGestureContext
) {
    val interactionState = ctx.interactionState
    if (ctx.isDrawingConnection()) {
        var boardPos = interactionState.snappedWirePoint ?: ((position - ctx.offset()) / ctx.scale())
        val startNodeId = ctx.connectionStartNodeId()
        val startPortId = ctx.connectionStartPortId()
        val startBoardPos = if (startNodeId != null && startPortId != null) {
            ctx.getPortBoardPosition(startNodeId, startPortId, ctx.connectionStartIsOutput())
        } else null
        if (startBoardPos != null && interactionState.snappedWirePoint == null) {
            if (event.keyboardModifiers.isShiftPressed || interactionState.isShiftModifierPressed) {
                boardPos = SplineMathUtils.snapToStraightAngle(startBoardPos, boardPos)
            } else if (event.keyboardModifiers.isCtrlPressed || interactionState.isCtrlModifierPressed) {
                boardPos = boardPos.snapToGrid()
            }
        }
        ctx.onConnectionDrag(boardPos)
    }

    if (interactionState.isDrawingStructuredConnection) {
        var livePos = interactionState.snappedWirePoint ?: ((position - ctx.offset()) / ctx.scale())
        val lastPoint = if (interactionState.structuredConnectionPoints.isNotEmpty()) {
            interactionState.structuredConnectionPoints.last()
        } else {
            val startBoardPos = if (interactionState.structuredConnectionSourceJunctionId != null) {
                ctx.junctions().find { it.id == interactionState.structuredConnectionSourceJunctionId }?.position?.toComposeOffset()
            } else if (interactionState.structuredConnectionStartNodeId != null && interactionState.structuredConnectionStartPortId != null) {
                ctx.getPortBoardPosition(interactionState.structuredConnectionStartNodeId!!, interactionState.structuredConnectionStartPortId!!, interactionState.structuredConnectionStartIsOutput)
            } else null
            startBoardPos ?: livePos
        }
        if (interactionState.snappedWirePoint == null) {
            if (event.keyboardModifiers.isShiftPressed || interactionState.isShiftModifierPressed) {
                livePos = SplineMathUtils.snapToStraightAngle(lastPoint, livePos)
            } else if (event.keyboardModifiers.isCtrlPressed || interactionState.isCtrlModifierPressed) {
                livePos = livePos.snapToGrid()
            }
        }
        ctx.onConnectionDrag(livePos)
        interactionState.structuredConnectionLivePos = livePos
        if (!event.buttons.isSecondaryPressed && !event.buttons.isTertiaryPressed) {
            event.changes.forEach { it.consume() }
        }
    }
}

private fun handleHoverDetectionOnMove(
    position: Offset,
    isOverNode: Boolean,
    ctx: BoardPointerGestureContext
) {
    val interactionState = ctx.interactionState
    if (isOverNode) {
        interactionState.hoveredJunctionId = null
        interactionState.clearHoveredWaypoint()
        interactionState.clearHoveredMidpoint()
        interactionState.clearHoveredConnection()
    } else {
        val closestJunc = ConnectionHitTester.findClosestJunction(
            position = position,
            junctions = ctx.junctions(),
            scale = ctx.scale(),
            offset = ctx.offset(),
            hitRadius = maxOf(20f, 20f * ctx.scale())
        )
        interactionState.hoveredJunctionId = closestJunc?.id

        val closestWp = ConnectionHitTester.findClosestWaypoint(
            position = position,
            connections = ctx.connections(),
            scale = ctx.scale(),
            offset = ctx.offset(),
            hitRadius = maxOf(20f, 20f * ctx.scale())
        )
        interactionState.hoveredWaypoint = closestWp?.let { Pair(it.first, it.second) }

        val junctionMap = ctx.junctions().associate { it.id to it.position.toComposeOffset() }
        val closestMid = ConnectionHitTester.findClosestMidpoint(
            position = position,
            connections = ctx.connections(),
            getPortBoardPosition = ctx.getPortBoardPosition,
            junctionMap = junctionMap,
            scale = ctx.scale(),
            offset = ctx.offset(),
            curveStyle = ctx.curveStyle(),
            roundness = ctx.roundness(),
            stepMode = ctx.orthogonalStepMode(),
            orthogonalPortLead = ctx.orthogonalPortLead(),
            nodes = ctx.nodes()
        )
        interactionState.hoveredMidpoint = closestMid?.let { Pair(it.first, it.second) }

        var bestConnection: Connection? = null
        var isHoveringPort = false
        val portHoverRadius = 20f

        ctx.nodes().forEach { node ->
            node.inputs.forEach { port ->
                val portBoardPos = ctx.getPortBoardPosition(node.id, port.id, false) ?: return@forEach
                val portScreenPos = (portBoardPos * ctx.scale()) + ctx.offset()
                if ((position - portScreenPos).getDistance() < portHoverRadius) {
                    isHoveringPort = true
                }
            }
            node.outputs.forEach { port ->
                val portBoardPos = ctx.getPortBoardPosition(node.id, port.id, true) ?: return@forEach
                val portScreenPos = (portBoardPos * ctx.scale()) + ctx.offset()
                if ((position - portScreenPos).getDistance() < portHoverRadius) {
                    isHoveringPort = true
                }
            }
        }

        if (!isHoveringPort && closestJunc == null && closestWp == null) {
            bestConnection = ConnectionHitTester.findClosestConnection(
                position = position,
                connections = ctx.connections(),
                getPortBoardPosition = ctx.getPortBoardPosition,
                scale = ctx.scale(),
                offset = ctx.offset(),
                junctions = ctx.junctions(),
                curveStyle = ctx.curveStyle(),
                roundness = ctx.roundness(),
                stepMode = ctx.orthogonalStepMode(),
                orthogonalPortLead = ctx.orthogonalPortLead(),
                nodes = ctx.nodes()
            )
        }

        if (bestConnection != null && interactionState.isCtrlModifierPressed) {
            val sourcePortBoardPos = ctx.getPortBoardPosition(bestConnection.sourceNodeId, bestConnection.sourcePortId, true)
            val targetPortBoardPos = ctx.getPortBoardPosition(bestConnection.targetNodeId, bestConnection.targetPortId, false)
            if (sourcePortBoardPos != null && targetPortBoardPos != null) {
                val startPos = (sourcePortBoardPos * ctx.scale()) + ctx.offset()
                val endPos = (targetPortBoardPos * ctx.scale()) + ctx.offset()
                interactionState.hoveredConnectionIsSource = ConnectionHitTester.determineCloserEnd(position, startPos, endPos)
            } else {
                interactionState.hoveredConnectionIsSource = null
            }
        } else {
            interactionState.hoveredConnectionIsSource = null
        }
        interactionState.hoveredConnection = bestConnection
    }
}

private fun handlePointerMove(
    event: PointerEvent,
    position: Offset,
    ctx: BoardPointerGestureContext
) {
    val interactionState = ctx.interactionState
    val session = ctx.session
    val prevPointerPosition = if (interactionState.lastPointerPosition == Offset.Zero) position else interactionState.lastPointerPosition
    interactionState.lastPointerPosition = position
    interactionState.isCtrlModifierPressed = event.keyboardModifiers.isCtrlPressed
    interactionState.isShiftModifierPressed = event.keyboardModifiers.isShiftPressed
    interactionState.isAltModifierPressed = event.keyboardModifiers.isAltPressed

    if (event.buttons.isSecondaryPressed && session.rightClickStartPos != null) {
        session.rightClickLastPos = position
        if (!session.rightClickDidDrag && (position - session.rightClickStartPos!!).getDistance() > 6f) {
            session.rightClickDidDrag = true
        }
    } else {
        session.middleClickLastPos = null
    }

    handleActiveDragsOnMove(event, position, prevPointerPosition, ctx)

    val collapsedGroupNodeIds = ctx.groups().filter { it.isCollapsed }.flatMap { it.nodeIds }.toSet()
    val isOverNode = isPointerOverAnyNode(
        screenPos = position,
        nodes = ctx.nodes(),
        nodeSizes = ctx.nodeSizes(),
        scale = ctx.scale(),
        offset = ctx.offset(),
        defaultNodeWidthPx = ctx.defaultNodeWidthPx(),
        densityValue = ctx.density().density,
        collapsedGroupNodeIds = collapsedGroupNodeIds,
        hoveredNodeId = interactionState.hoveredNodeId
    )

    handleWireSnappingOnMove(position, isOverNode, ctx)
    handleDrawingConnectionOnMove(event, position, ctx)
    handleHoverDetectionOnMove(position, isOverNode, ctx)
}

private fun handlePointerExit(ctx: BoardPointerGestureContext) {
    val interactionState = ctx.interactionState
    interactionState.clearHoveredConnection()
    interactionState.hoveredJunctionId = null
    interactionState.clearHoveredWaypoint()
    interactionState.clearHoveredMidpoint()
    interactionState.draggingWaypoint = null
    interactionState.draggingJunctionId = null
    interactionState.pendingMidpoint = null
    interactionState.pendingMidpointWasAltPressed = false
    interactionState.clearSnapping()
    ctx.session.resetRightClick()
    ctx.session.resetJunctionDrag()
    ctx.session.middleClickLastPos = null
}

private fun handleStructuredConnectionPress(
    event: PointerEvent,
    position: Offset,
    ctx: BoardPointerGestureContext
) {
    val interactionState = ctx.interactionState
    if (interactionState.snappedWirePoint != null && interactionState.snappedWireConnection != null) {
        val snappedConn = interactionState.snappedWireConnection!!
        val snappedPt = interactionState.snappedWirePoint!!.toModelOffset()
        val isOutput = interactionState.structuredConnectionStartIsOutput
        val srcNodeId = if (isOutput) interactionState.structuredConnectionStartNodeId else null
        val srcPortId = if (isOutput) interactionState.structuredConnectionStartPortId else null
        val srcJuncId = if (isOutput) interactionState.structuredConnectionSourceJunctionId else null
        val tgtNodeId = if (!isOutput) interactionState.structuredConnectionStartNodeId else null
        val tgtPortId = if (!isOutput) interactionState.structuredConnectionStartPortId else null
        val tgtJuncId = if (!isOutput) interactionState.structuredConnectionSourceJunctionId else null
        val interPts = interactionState.structuredConnectionPoints.map { it.toModelOffset() }

        ctx.onSplitConnectionAndConnect?.invoke(
            snappedConn,
            snappedPt,
            srcNodeId,
            srcPortId,
            srcJuncId,
            tgtNodeId,
            tgtPortId,
            tgtJuncId,
            interPts
        )
        interactionState.resetStructuredConnection()
        interactionState.clearSnapping()
        event.changes.forEach { it.consume() }
    } else {
        val targetJuncId = interactionState.hoveredJunctionId
        if (targetJuncId != null && targetJuncId != interactionState.structuredConnectionSourceJunctionId) {
            val finalSourceNodeId = if (interactionState.structuredConnectionStartIsOutput) (interactionState.structuredConnectionStartNodeId ?: -1L) else -1L
            val finalSourcePortId = if (interactionState.structuredConnectionStartIsOutput) (interactionState.structuredConnectionStartPortId ?: "") else ""
            val finalTargetNodeId = if (!interactionState.structuredConnectionStartIsOutput) (interactionState.structuredConnectionStartNodeId ?: -1L) else -1L
            val finalTargetPortId = if (!interactionState.structuredConnectionStartIsOutput) (interactionState.structuredConnectionStartPortId ?: "") else ""
            val finalSrcJuncId = if (!interactionState.structuredConnectionStartIsOutput) targetJuncId else interactionState.structuredConnectionSourceJunctionId
            val finalTgtJuncId = if (interactionState.structuredConnectionStartIsOutput) targetJuncId else null

            ctx.onFinalizeStructuredConnection?.invoke(
                finalSourceNodeId,
                finalSourcePortId,
                finalSrcJuncId,
                finalTargetNodeId,
                finalTargetPortId,
                interactionState.structuredConnectionPoints.map { it.toModelOffset() },
                finalTgtJuncId
            )
            interactionState.resetStructuredConnection()
            event.changes.forEach { it.consume() }
        } else {
            var clickedPortNodeId: Long? = null
            var clickedPortId: String? = null
            ctx.nodes().forEach { node ->
                val portsToCheck = if (interactionState.structuredConnectionStartIsOutput) node.inputs else node.outputs
                portsToCheck.forEach { port ->
                    val portBoardPos = ctx.getPortBoardPosition(node.id, port.id, !interactionState.structuredConnectionStartIsOutput) ?: return@forEach
                    val portScreenPos = (portBoardPos * ctx.scale()) + ctx.offset()
                    if ((position - portScreenPos).getDistance() < 32f) {
                        clickedPortNodeId = node.id
                        clickedPortId = port.id
                    }
                }
            }

            val resolvedPortNodeId = clickedPortNodeId ?: ctx.highlightedNodeId()
            val resolvedPortId = clickedPortId ?: ctx.highlightedPortId()

            if (resolvedPortNodeId != null && resolvedPortId != null) {
                val finalTargetNodeId = if (interactionState.structuredConnectionStartIsOutput) resolvedPortNodeId else (interactionState.structuredConnectionStartNodeId ?: -1L)
                val finalTargetPortId = if (interactionState.structuredConnectionStartIsOutput) resolvedPortId else (interactionState.structuredConnectionStartPortId ?: "")
                val finalSourceNodeId = if (interactionState.structuredConnectionStartIsOutput) (interactionState.structuredConnectionStartNodeId ?: -1L) else resolvedPortNodeId
                val finalSourcePortId = if (interactionState.structuredConnectionStartIsOutput) (interactionState.structuredConnectionStartPortId ?: "") else resolvedPortId

                ctx.onFinalizeStructuredConnection?.invoke(
                    finalSourceNodeId,
                    finalSourcePortId,
                    interactionState.structuredConnectionSourceJunctionId,
                    finalTargetNodeId,
                    finalTargetPortId,
                    interactionState.structuredConnectionPoints.map { it.toModelOffset() },
                    null
                )
                interactionState.resetStructuredConnection()
                ctx.onResetDrawingConnection?.invoke()
                event.changes.forEach { it.consume() }
            } else {
                val startBoardPos = if (interactionState.structuredConnectionSourceJunctionId != null) {
                    ctx.junctions().find { it.id == interactionState.structuredConnectionSourceJunctionId }?.position?.toComposeOffset()
                } else if (interactionState.structuredConnectionStartNodeId != null && interactionState.structuredConnectionStartPortId != null) {
                    ctx.getPortBoardPosition(interactionState.structuredConnectionStartNodeId!!, interactionState.structuredConnectionStartPortId!!, interactionState.structuredConnectionStartIsOutput)
                } else null

                val lastPoint = if (interactionState.structuredConnectionPoints.isNotEmpty()) {
                    interactionState.structuredConnectionPoints.last()
                } else {
                    startBoardPos ?: ((position - ctx.offset()) / ctx.scale())
                }

                var committedPos = (position - ctx.offset()) / ctx.scale()
                if (interactionState.isShiftModifierPressed || event.keyboardModifiers.isShiftPressed) {
                    committedPos = SplineMathUtils.snapToStraightAngle(lastPoint, committedPos)
                } else if (interactionState.isCtrlModifierPressed || event.keyboardModifiers.isCtrlPressed) {
                    committedPos = committedPos.snapToGrid()
                }
                interactionState.structuredConnectionPoints = (interactionState.structuredConnectionPoints + committedPos).toMutableList()
                event.changes.forEach { it.consume() }
            }
        }
    }
}

private fun handleElementPress(
    event: PointerEvent,
    position: Offset,
    ctx: BoardPointerGestureContext
) {
    val interactionState = ctx.interactionState
    val session = ctx.session
    interactionState.lastPointerPosition = position
    val isAlt = event.keyboardModifiers.isAltPressed || interactionState.isAltModifierPressed
    val isShift = event.keyboardModifiers.isShiftPressed || interactionState.isShiftModifierPressed
    val isCtrl = event.keyboardModifiers.isCtrlPressed || interactionState.isCtrlModifierPressed

    val hitJunc = ConnectionHitTester.findClosestJunction(
        position = position,
        junctions = ctx.junctions(),
        scale = ctx.scale(),
        offset = ctx.offset(),
        hitRadius = maxOf(20f, 20f * ctx.scale())
    )
    val hitWp = ConnectionHitTester.findClosestWaypoint(
        position = position,
        connections = ctx.connections(),
        scale = ctx.scale(),
        offset = ctx.offset(),
        hitRadius = maxOf(20f, 20f * ctx.scale())
    )

    val distJunc = hitJunc?.let {
        val screenPos = (it.position.toComposeOffset() * ctx.scale()) + ctx.offset()
        (position - screenPos).getDistance()
    } ?: Float.MAX_VALUE

    val distWp = hitWp?.let {
        (position - it.third).getDistance()
    } ?: Float.MAX_VALUE

    if (distWp < distJunc && hitWp != null) {
        val wp = hitWp
        if (ctx.isEyedropperActive() && ctx.onSampleColor != null) {
            ctx.onSampleColor.invoke(wp.first.color ?: "#808080")
            event.changes.forEach { it.consume() }
        } else if (ctx.isPaintToolActive() && ctx.onPaintConnection != null) {
            ctx.onPaintConnection.invoke(wp.first)
            event.changes.forEach { it.consume() }
        } else if (ctx.isWashToolActive() && ctx.onWashConnection != null) {
            ctx.onWashConnection.invoke(wp.first)
            event.changes.forEach { it.consume() }
        } else {
            val actId = if (isShift) ShortcutActionId.FLOW_DELETE_SELECTED else ShortcutActionId.FLOW_MOVE_POINT
            if (isShift) {
                ctx.onDeleteWaypoint?.invoke(wp.first, wp.second)
                interactionState.hoveredWaypoint = null
            } else {
                if (!isCtrl) {
                    ctx.onSelectPoints?.invoke(emptySet())
                    ctx.onSelectNodes?.invoke(emptySet())
                    ctx.onSelectGroups?.invoke(emptySet())
                    ctx.onSelectLabels?.invoke(emptySet())
                    interactionState.selectedConnection = wp.first
                }
                interactionState.startDraggingWaypoint(wp.first, wp.second)
                interactionState.lastPointerPosition = position
            }
            ctx.shortcutManager()?.eat(event, actId) ?: event.changes.forEach { it.consume() }
        }
    } else if (hitJunc != null) {
        val juncId = hitJunc.id
        if (ctx.isEyedropperActive() || ctx.isPaintToolActive() || ctx.isWashToolActive()) {
            val connectedConns = ctx.connections().filter { it.sourceJunctionId == juncId || it.targetJunctionId == juncId }
            if (ctx.isEyedropperActive() && ctx.onSampleColor != null) {
                val sample = hitJunc.color ?: connectedConns.firstOrNull { it.color != null }?.color ?: "#808080"
                ctx.onSampleColor.invoke(sample)
                event.changes.forEach { it.consume() }
            } else if (ctx.isPaintToolActive() && ctx.onPaintJunction != null) {
                ctx.onPaintJunction.invoke(juncId)
                event.changes.forEach { it.consume() }
            } else if (ctx.isWashToolActive() && ctx.onWashJunction != null) {
                ctx.onWashJunction.invoke(juncId)
                event.changes.forEach { it.consume() }
            }
        } else if (isAlt) {
            interactionState.startConnectingWire(
                sourceNodeId = null,
                sourcePortId = null,
                isOutput = true,
                sourceJunctionId = juncId,
                initialWaypoints = emptyList(),
                livePos = (position - ctx.offset()) / ctx.scale(),
                isStructured = true
            )
            ctx.shortcutManager()?.eat(event, ShortcutActionId.FLOW_CREATE_RAMIFICATION) ?: event.changes.forEach { it.consume() }
        } else if (isShift) {
            ctx.onDeleteJunction?.invoke(juncId)
            interactionState.hoveredJunctionId = null
            ctx.shortcutManager()?.eat(event, ShortcutActionId.FLOW_DELETE_SELECTED) ?: event.changes.forEach { it.consume() }
        } else {
            val selectedPointIds = ctx.selectedPointIds()
            val isSelected = juncId in selectedPointIds
            if (isCtrl) {
                val newSelectedPointIds = if (isSelected) {
                    selectedPointIds - juncId
                } else {
                    selectedPointIds + juncId
                }
                ctx.onSelectPoints?.invoke(newSelectedPointIds)
                session.junctionDragStartPointPositions = ctx.junctions().filter { it.id in newSelectedPointIds }.associate { it.id to it.position }
                session.junctionDragStartNodePositions = ctx.nodes().filter { it.id in ctx.selectedNodeIds() }.associate { it.id to it.position }
                session.junctionDragStartGroupPositions = ctx.groups().filter { it.id in ctx.selectedGroupIds() }.associate { it.id to it.position }
                session.junctionDragStartLabelPositions = ctx.labels().filter { it.id in ctx.selectedLabelIds() }.associate { it.id to it.position }
            } else if (!isSelected) {
                ctx.onSelectPoints?.invoke(setOf(juncId))
                ctx.onSelectNodes?.invoke(emptySet())
                ctx.onSelectGroups?.invoke(emptySet())
                ctx.onSelectLabels?.invoke(emptySet())
                session.junctionDragStartPointPositions = mapOf(juncId to hitJunc.position)
                session.junctionDragStartNodePositions = emptyMap()
                session.junctionDragStartGroupPositions = emptyMap()
                session.junctionDragStartLabelPositions = emptyMap()
            } else {
                session.junctionDragStartPointPositions = ctx.junctions().filter { it.id in selectedPointIds }.associate { it.id to it.position }
                session.junctionDragStartNodePositions = ctx.nodes().filter { it.id in ctx.selectedNodeIds() }.associate { it.id to it.position }
                session.junctionDragStartGroupPositions = ctx.groups().filter { it.id in ctx.selectedGroupIds() }.associate { it.id to it.position }
                session.junctionDragStartLabelPositions = ctx.labels().filter { it.id in ctx.selectedLabelIds() }.associate { it.id to it.position }
            }
            interactionState.selectedJunctionId = juncId
            interactionState.startDraggingJunction(juncId, position)
            interactionState.lastPointerPosition = position
            session.junctionDragStartPointerPosition = position
            session.junctionLastDispatchedBoardPos = hitJunc.position.toComposeOffset()
            ctx.shortcutManager()?.eat(event, ShortcutActionId.FLOW_MOVE_POINT) ?: event.changes.forEach { it.consume() }
        }
    } else {
        val connProj = ConnectionHitTester.findClosestConnectionWithProjection(
            position = position,
            connections = ctx.connections(),
            getPortBoardPosition = ctx.getPortBoardPosition,
            scale = ctx.scale(),
            offset = ctx.offset(),
            junctions = ctx.junctions(),
            curveStyle = ctx.curveStyle(),
            roundness = ctx.roundness(),
            stepMode = ctx.orthogonalStepMode(),
            orthogonalPortLead = ctx.orthogonalPortLead(),
            nodes = ctx.nodes()
        )
        if (connProj != null) {
            if (ctx.isEyedropperActive() && ctx.onSampleColor != null) {
                ctx.onSampleColor.invoke(connProj.connection.color ?: "#808080")
                event.changes.forEach { it.consume() }
            } else if (ctx.isPaintToolActive() && ctx.onPaintConnection != null) {
                ctx.onPaintConnection.invoke(connProj.connection)
                event.changes.forEach { it.consume() }
            } else if (ctx.isWashToolActive() && ctx.onWashConnection != null) {
                ctx.onWashConnection.invoke(connProj.connection)
                event.changes.forEach { it.consume() }
            } else if (isShift) {
                if (connProj.connection.waypoints.isNotEmpty() && ctx.onDeleteConnectionSegment != null) {
                    ctx.onDeleteConnectionSegment.invoke(connProj.connection, connProj.segmentIndex)
                } else {
                    ctx.onDeleteConnection(connProj.connection)
                }
                interactionState.clearHoveredConnection()
                if (interactionState.selectedConnection == connProj.connection) {
                    interactionState.selectedConnection = null
                }
                event.changes.forEach { it.consume() }
            } else if (isAlt) {
                val segInfo = DraggingSegmentInfo(
                    connection = connProj.connection,
                    segmentIndex = connProj.segmentIndex
                )
                interactionState.startDraggingSegment(segInfo)
                session.segmentDragStartPos = position
                interactionState.lastPointerPosition = position
                ctx.shortcutManager()?.eat(event, ShortcutActionId.FLOW_MOVE_POINT) ?: event.changes.forEach { it.consume() }
            } else {
                val newJuncId = (ctx.junctions().maxOfOrNull { it.id } ?: 0L) + 1L
                ctx.onAddJunctionAndBranch?.invoke(connProj.connection, connProj.projectedPoint, connProj.segmentIndex)
                interactionState.selectedJunctionId = newJuncId
                ctx.onSelectPoints?.invoke(setOf(newJuncId))
                ctx.onSelectNodes?.invoke(emptySet())
                ctx.onSelectGroups?.invoke(emptySet())
                ctx.onSelectLabels?.invoke(emptySet())
                interactionState.startDraggingJunction(newJuncId, position)
                interactionState.lastPointerPosition = position
                session.junctionDragStartPointerPosition = position
                session.junctionDragStartPointPositions = mapOf(newJuncId to connProj.projectedPoint.toModelOffset())
                session.junctionDragStartNodePositions = emptyMap()
                session.junctionDragStartGroupPositions = emptyMap()
                session.junctionDragStartLabelPositions = emptyMap()
                session.junctionLastDispatchedBoardPos = connProj.projectedPoint
                ctx.shortcutManager()?.eat(event, ShortcutActionId.FLOW_MOVE_POINT) ?: event.changes.forEach { it.consume() }
            }
        }
    }
}

private fun handlePointerPress(
    event: PointerEvent,
    position: Offset,
    ctx: BoardPointerGestureContext
) {
    val interactionState = ctx.interactionState
    val session = ctx.session
    if (event.buttons.isSecondaryPressed) {
        val pos = event.changes.firstOrNull()?.position ?: position
        session.rightClickStartPos = pos
        session.rightClickLastPos = pos
        session.rightClickDidDrag = false
    } else if (event.buttons.isTertiaryPressed) {
        val pos = event.changes.firstOrNull()?.position ?: position
        session.middleClickLastPos = pos
    } else if (interactionState.isDrawingStructuredConnection && event.buttons.isPrimaryPressed) {
        handleStructuredConnectionPress(event, position, ctx)
    } else if (event.buttons.isPrimaryPressed) {
        val isConsumed = event.changes.any { it.isConsumed }
        val collapsedGroupNodeIds = ctx.groups().filter { it.isCollapsed }.flatMap { it.nodeIds }.toSet()
        val isOverNode = isPointerOverAnyNode(
            screenPos = position,
            nodes = ctx.nodes(),
            nodeSizes = ctx.nodeSizes(),
            scale = ctx.scale(),
            offset = ctx.offset(),
            defaultNodeWidthPx = ctx.defaultNodeWidthPx(),
            densityValue = ctx.density().density,
            collapsedGroupNodeIds = collapsedGroupNodeIds,
            hoveredNodeId = interactionState.hoveredNodeId
        )

        if (isConsumed || isOverNode) {
            if (isOverNode) {
                event.changes.forEach { it.consume() }
            }
        } else {
            handleElementPress(event, position, ctx)
        }
    } else if (event.keyboardModifiers.isCtrlPressed) {
        val isConsumed = event.changes.any { it.isConsumed }
        val collapsedGroupNodeIds = ctx.groups().filter { it.isCollapsed }.flatMap { it.nodeIds }.toSet()
        val isOverNode = isPointerOverAnyNode(
            screenPos = position,
            nodes = ctx.nodes(),
            nodeSizes = ctx.nodeSizes(),
            scale = ctx.scale(),
            offset = ctx.offset(),
            defaultNodeWidthPx = ctx.defaultNodeWidthPx(),
            densityValue = ctx.density().density,
            collapsedGroupNodeIds = collapsedGroupNodeIds,
            hoveredNodeId = interactionState.hoveredNodeId
        )
        if (!isConsumed && !isOverNode) {
            interactionState.hoveredConnection?.let { conn ->
                val isSrc = interactionState.hoveredConnectionIsSource ?: false
                val boardPos = (position - ctx.offset()) / ctx.scale()
                ctx.onDetachConnection(conn, isSrc, boardPos)
                event.changes.forEach { it.consume() }
            }
        }
    }
}

private fun handlePointerRelease(
    event: PointerEvent,
    position: Offset,
    ctx: BoardPointerGestureContext
) {
    val interactionState = ctx.interactionState
    val session = ctx.session
    if (session.rightClickStartPos != null) {
        if (!session.rightClickDidDrag) {
            if (interactionState.isDrawingStructuredConnection) {
                interactionState.resetStructuredConnection()
                interactionState.clearSnapping()
                event.changes.forEach { it.consume() }
            } else if (ctx.isDrawingConnection()) {
                interactionState.clearSnapping()
                ctx.onConnectionDrop(false)
                event.changes.forEach { it.consume() }
            }
        }
        session.resetRightClick()
    }
    session.middleClickLastPos = null

    if (interactionState.draggingSegment != null) {
        val seg = interactionState.draggingSegment!!
        val totalDelta = if (session.segmentDragStartPos != null) {
            (position - session.segmentDragStartPos!!) / ctx.scale()
        } else Offset.Zero
        ctx.onEndMoveSegment?.invoke(seg.connection, seg.segmentIndex, totalDelta.toModelOffset())
        interactionState.draggingSegment = null
        session.segmentDragStartPos = null
    }

    if (interactionState.draggingJunctionId != null) {
        val isCtrl = event.keyboardModifiers.isCtrlPressed || interactionState.isCtrlModifierPressed
        val pointMoves = session.junctionDragStartPointPositions.mapValues { (id, startPos) ->
            val currentPos = ctx.junctions().find { it.id == id }?.position ?: startPos
            startPos to (if (isCtrl) currentPos.snapToGrid() else currentPos)
        }.filter { it.value.first != it.value.second }

        val nodeMoves = session.junctionDragStartNodePositions.mapValues { (id, startPos) ->
            startPos to (ctx.nodes().find { it.id == id }?.position ?: startPos)
        }.filter { it.value.first != it.value.second }

        val groupMoves = session.junctionDragStartGroupPositions.mapValues { (id, startPos) ->
            startPos to (ctx.groups().find { it.id == id }?.position ?: startPos)
        }.filter { it.value.first != it.value.second }

        val labelMoves = session.junctionDragStartLabelPositions.mapValues { (id, startPos) ->
            startPos to (ctx.labels().find { it.id == id }?.position ?: startPos)
        }.filter { it.value.first != it.value.second }

        if (pointMoves.isNotEmpty() || nodeMoves.isNotEmpty() || groupMoves.isNotEmpty() || labelMoves.isNotEmpty()) {
            ctx.onEndMoveJunction?.invoke(pointMoves, nodeMoves, groupMoves, labelMoves)
        }

        session.resetJunctionDrag()
        interactionState.draggingJunctionId = null
    }

    interactionState.draggingWaypoint = null

    if (ctx.isDrawingConnection() && !event.buttons.isPrimaryPressed) {
        if (interactionState.snappedWirePoint != null && interactionState.snappedWireConnection != null) {
            val snappedConn = interactionState.snappedWireConnection!!
            val snappedPt = interactionState.snappedWirePoint!!.toModelOffset()
            val isOutput = ctx.connectionStartIsOutput()
            val srcNodeId = if (isOutput) ctx.connectionStartNodeId() else null
            val srcPortId = if (isOutput) ctx.connectionStartPortId() else null
            val tgtNodeId = if (!isOutput) ctx.connectionStartNodeId() else null
            val tgtPortId = if (!isOutput) ctx.connectionStartPortId() else null

            ctx.onSplitConnectionAndConnect?.invoke(
                snappedConn,
                snappedPt,
                srcNodeId,
                srcPortId,
                null,
                tgtNodeId,
                tgtPortId,
                null,
                emptyList()
            )
            interactionState.clearSnapping()
            ctx.onResetDrawingConnection?.invoke()
        } else {
            ctx.onConnectionDrop(event.keyboardModifiers.isShiftPressed)
        }
    }
    interactionState.clearSnapping()
}

@Composable
fun Modifier.boardPointerEventGesture(
    interactionState: BoardInteractionState,
    isDrawingConnection: Boolean,
    scale: Float,
    offset: Offset,
    nodes: List<Node>,
    connections: List<Connection>,
    getPortBoardPosition: (Long, String, Boolean) -> Offset?,
    onZoom: (Float, Offset, Boolean) -> Unit,
    onConnectionDrag: (Offset) -> Unit,
    onConnectionDrop: (Boolean) -> Unit,
    onDeleteConnection: (Connection) -> Unit,
    onDetachConnection: (Connection, Boolean, Offset) -> Unit,
    junctions: List<FlowJunction> = emptyList(),
    onMoveJunction: ((Long, org.wip.plugintoolkit.features.flows.model.Offset) -> Unit)? = null,
    onEndMoveJunction: ((Map<Long, Pair<org.wip.plugintoolkit.features.flows.model.Offset, org.wip.plugintoolkit.features.flows.model.Offset>>, Map<Long, Pair<org.wip.plugintoolkit.features.flows.model.Offset, org.wip.plugintoolkit.features.flows.model.Offset>>, Map<Long, Pair<org.wip.plugintoolkit.features.flows.model.Offset, org.wip.plugintoolkit.features.flows.model.Offset>>, Map<Long, Pair<org.wip.plugintoolkit.features.flows.model.Offset, org.wip.plugintoolkit.features.flows.model.Offset>>) -> Unit)? = null,
    onDeleteJunction: ((Long) -> Unit)? = null,
    onAddWaypoint: ((Connection, Offset) -> Unit)? = null,
    onMoveWaypoint: ((Connection, Int, org.wip.plugintoolkit.features.flows.model.Offset) -> Unit)? = null,
    onDeleteWaypoint: ((Connection, Int) -> Unit)? = null,
    onInsertWaypoint: ((Connection, Int, org.wip.plugintoolkit.features.flows.model.Offset) -> Unit)? = null,
    onFinalizeStructuredConnection: ((Long?, String?, Long?, Long, String, List<org.wip.plugintoolkit.features.flows.model.Offset>, Long?) -> Unit)? = null,
    connectionStartNodeId: Long? = null,
    connectionStartPortId: String? = null,
    connectionStartIsOutput: Boolean = true,
    curveStyle: ConnectionCurveStyle = ConnectionCurveStyle.Bezier,
    roundness: Float = 0.5f,
    orthogonalStepMode: OrthogonalStepMode = OrthogonalStepMode.Auto,
    orthogonalPortLead: Boolean = false,
    isAdvancedConnectionMode: Boolean = false,
    onAddJunctionAndBranch: ((Connection, Offset, Int) -> Unit)? = null,
    onDeleteConnectionSegment: ((Connection, Int) -> Unit)? = null,
    selectedPointIds: Set<Long> = emptySet(),
    selectedNodeIds: Set<Long> = emptySet(),
    selectedGroupIds: Set<Long> = emptySet(),
    selectedLabelIds: Set<Long> = emptySet(),
    groups: List<FlowGroup> = emptyList(),
    labels: List<FlowLabel> = emptyList(),
    onSelectPoints: ((Set<Long>) -> Unit)? = null,
    onSplitConnectionAndConnect: ((Connection, org.wip.plugintoolkit.features.flows.model.Offset, Long?, String?, Long?, Long?, String?, Long?, List<org.wip.plugintoolkit.features.flows.model.Offset>) -> Unit)? = null,
    onResetDrawingConnection: (() -> Unit)? = null,
    shortcutManager: ShortcutManager? = null,
    highlightedNodeId: Long? = null,
    highlightedPortId: String? = null,
    onSelectNodes: ((Set<Long>) -> Unit)? = null,
    onSelectGroups: ((Set<Long>) -> Unit)? = null,
    onSelectLabels: ((Set<Long>) -> Unit)? = null,
    isPaintToolActive: Boolean = false,
    isWashToolActive: Boolean = false,
    isEyedropperActive: Boolean = false,
    onPaintConnection: ((Connection) -> Unit)? = null,
    onWashConnection: ((Connection) -> Unit)? = null,
    onPaintJunction: ((Long) -> Unit)? = null,
    onWashJunction: ((Long) -> Unit)? = null,
    onSampleColor: ((String) -> Unit)? = null,
    onMoveSegment: ((Connection, Int, org.wip.plugintoolkit.features.flows.model.Offset) -> Unit)? = null,
    onEndMoveSegment: ((Connection, Int, org.wip.plugintoolkit.features.flows.model.Offset) -> Unit)? = null,
    nodeSizes: Map<Long, IntSize> = emptyMap(),
    density: Density? = null,
    defaultNodeWidthPx: Float = 0f
): Modifier {
    val currentOnSelectNodes by rememberUpdatedState(onSelectNodes)
    val currentOnSelectGroups by rememberUpdatedState(onSelectGroups)
    val currentOnSelectLabels by rememberUpdatedState(onSelectLabels)
    val currentNodeSizes by rememberUpdatedState(nodeSizes)
    val currentDensity by rememberUpdatedState(density ?: Density(1f))
    val currentDefaultNodeWidthPx by rememberUpdatedState(defaultNodeWidthPx)
    val currentIsPaintToolActive by rememberUpdatedState(isPaintToolActive)
    val currentIsWashToolActive by rememberUpdatedState(isWashToolActive)
    val currentIsEyedropperActive by rememberUpdatedState(isEyedropperActive)
    val currentOnPaintConnection by rememberUpdatedState(onPaintConnection)
    val currentOnWashConnection by rememberUpdatedState(onWashConnection)
    val currentOnPaintJunction by rememberUpdatedState(onPaintJunction)
    val currentOnWashJunction by rememberUpdatedState(onWashJunction)
    val currentOnSampleColor by rememberUpdatedState(onSampleColor)
    val currentOnMoveSegment by rememberUpdatedState(onMoveSegment)
    val currentOnEndMoveSegment by rememberUpdatedState(onEndMoveSegment)
    val currentIsDrawingConnection by rememberUpdatedState(isDrawingConnection)
    val currentHighlightedNodeId by rememberUpdatedState(highlightedNodeId)
    val currentHighlightedPortId by rememberUpdatedState(highlightedPortId)
    val currentScale by rememberUpdatedState(scale)
    val currentOffset by rememberUpdatedState(offset)
    val currentNodes by rememberUpdatedState(nodes)
    val currentConnections by rememberUpdatedState(connections)
    val currentGetPortBoardPosition by rememberUpdatedState(getPortBoardPosition)
    val currentOnZoom by rememberUpdatedState(onZoom)
    val currentOnConnectionDrag by rememberUpdatedState(onConnectionDrag)
    val currentOnConnectionDrop by rememberUpdatedState(onConnectionDrop)
    val currentOnDeleteConnection by rememberUpdatedState(onDeleteConnection)
    val currentOnDetachConnection by rememberUpdatedState(onDetachConnection)
    val currentJunctions by rememberUpdatedState(junctions)
    val currentOnMoveJunction by rememberUpdatedState(onMoveJunction)
    val currentOnEndMoveJunction by rememberUpdatedState(onEndMoveJunction)
    val currentOnDeleteJunction by rememberUpdatedState(onDeleteJunction)
    val currentOnAddWaypoint by rememberUpdatedState(onAddWaypoint)
    val currentOnMoveWaypoint by rememberUpdatedState(onMoveWaypoint)
    val currentOnDeleteWaypoint by rememberUpdatedState(onDeleteWaypoint)
    val currentOnDeleteConnectionSegment by rememberUpdatedState(onDeleteConnectionSegment)
    val currentOnInsertWaypoint by rememberUpdatedState(onInsertWaypoint)
    val currentOnFinalizeStructuredConnection by rememberUpdatedState(onFinalizeStructuredConnection)
    val currentConnectionStartNodeId by rememberUpdatedState(connectionStartNodeId)
    val currentConnectionStartPortId by rememberUpdatedState(connectionStartPortId)
    val currentConnectionStartIsOutput by rememberUpdatedState(connectionStartIsOutput)
    val currentCurveStyle by rememberUpdatedState(curveStyle)
    val currentRoundness by rememberUpdatedState(roundness)
    val currentOrthogonalStepMode by rememberUpdatedState(orthogonalStepMode)
    val currentOrthogonalPortLead by rememberUpdatedState(orthogonalPortLead)
    val currentIsAdvancedConnectionMode by rememberUpdatedState(isAdvancedConnectionMode)
    val currentOnAddJunctionAndBranch by rememberUpdatedState(onAddJunctionAndBranch)
    val currentSelectedPointIds by rememberUpdatedState(selectedPointIds)
    val currentSelectedNodeIds by rememberUpdatedState(selectedNodeIds)
    val currentSelectedGroupIds by rememberUpdatedState(selectedGroupIds)
    val currentSelectedLabelIds by rememberUpdatedState(selectedLabelIds)
    val currentGroups by rememberUpdatedState(groups)
    val currentLabels by rememberUpdatedState(labels)
    val currentOnSelectPoints by rememberUpdatedState(onSelectPoints)
    val currentOnSplitConnectionAndConnect by rememberUpdatedState(onSplitConnectionAndConnect)
    val currentOnResetDrawingConnection by rememberUpdatedState(onResetDrawingConnection)
    val currentShortcutManager by rememberUpdatedState(shortcutManager)

    val session = remember { BoardGestureSessionState() }

    val ctx = remember(session, interactionState) {
        BoardPointerGestureContext(
            interactionState = interactionState,
            session = session,
            isDrawingConnection = { currentIsDrawingConnection },
            scale = { currentScale },
            offset = { currentOffset },
            nodes = { currentNodes },
            connections = { currentConnections },
            junctions = { currentJunctions },
            groups = { currentGroups },
            labels = { currentLabels },
            nodeSizes = { currentNodeSizes },
            density = { currentDensity },
            defaultNodeWidthPx = { currentDefaultNodeWidthPx },
            getPortBoardPosition = currentGetPortBoardPosition,
            onZoom = { delta, pos, shift -> currentOnZoom(delta, pos, shift) },
            onConnectionDrag = { pos -> currentOnConnectionDrag(pos) },
            onConnectionDrop = { shift -> currentOnConnectionDrop(shift) },
            onDeleteConnection = { conn -> currentOnDeleteConnection(conn) },
            onDetachConnection = { conn, isSrc, pos -> currentOnDetachConnection(conn, isSrc, pos) },
            onMoveJunction = currentOnMoveJunction,
            onEndMoveJunction = currentOnEndMoveJunction,
            onDeleteJunction = currentOnDeleteJunction,
            onAddWaypoint = currentOnAddWaypoint,
            onMoveWaypoint = currentOnMoveWaypoint,
            onDeleteWaypoint = currentOnDeleteWaypoint,
            onInsertWaypoint = currentOnInsertWaypoint,
            onFinalizeStructuredConnection = currentOnFinalizeStructuredConnection,
            connectionStartNodeId = { currentConnectionStartNodeId },
            connectionStartPortId = { currentConnectionStartPortId },
            connectionStartIsOutput = { currentConnectionStartIsOutput },
            curveStyle = { currentCurveStyle },
            roundness = { currentRoundness },
            orthogonalStepMode = { currentOrthogonalStepMode },
            orthogonalPortLead = { currentOrthogonalPortLead },
            isAdvancedConnectionMode = { currentIsAdvancedConnectionMode },
            onAddJunctionAndBranch = currentOnAddJunctionAndBranch,
            onDeleteConnectionSegment = currentOnDeleteConnectionSegment,
            selectedPointIds = { currentSelectedPointIds },
            selectedNodeIds = { currentSelectedNodeIds },
            selectedGroupIds = { currentSelectedGroupIds },
            selectedLabelIds = { currentSelectedLabelIds },
            onSelectPoints = currentOnSelectPoints,
            onSplitConnectionAndConnect = currentOnSplitConnectionAndConnect,
            onResetDrawingConnection = currentOnResetDrawingConnection,
            shortcutManager = { currentShortcutManager },
            highlightedNodeId = { currentHighlightedNodeId },
            highlightedPortId = { currentHighlightedPortId },
            onSelectNodes = currentOnSelectNodes,
            onSelectGroups = currentOnSelectGroups,
            onSelectLabels = currentOnSelectLabels,
            isPaintToolActive = { currentIsPaintToolActive },
            isWashToolActive = { currentIsWashToolActive },
            isEyedropperActive = { currentIsEyedropperActive },
            onPaintConnection = currentOnPaintConnection,
            onWashConnection = currentOnWashConnection,
            onPaintJunction = currentOnPaintJunction,
            onWashJunction = currentOnWashJunction,
            onSampleColor = currentOnSampleColor,
            onMoveSegment = currentOnMoveSegment,
            onEndMoveSegment = currentOnEndMoveSegment
        )
    }

    return this.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: continue
                val position = change.position
                when (event.type) {
                    PointerEventType.Scroll -> handlePointerScroll(event, change, position, ctx)
                    PointerEventType.Move -> handlePointerMove(event, position, ctx)
                    PointerEventType.Exit -> handlePointerExit(ctx)
                    PointerEventType.Press -> handlePointerPress(event, position, ctx)
                    PointerEventType.Release -> handlePointerRelease(event, position, ctx)
                }
            }
        }
    }
}
