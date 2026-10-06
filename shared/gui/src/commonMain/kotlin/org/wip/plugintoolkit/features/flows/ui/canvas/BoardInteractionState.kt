package org.wip.plugintoolkit.features.flows.ui.canvas

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.features.flows.model.Connection

data class StructuredConnectionStartInfo(
    val nodeId: Long? = null,
    val portId: String? = null,
    val isOutput: Boolean = true,
    val sourceJunctionId: Long? = null,
    val initialWaypoints: List<Offset> = emptyList(),
    val livePos: Offset? = null
)

data class DraggingSegmentInfo(
    val connection: Connection,
    val segmentIndex: Int,
    val startNodeId: Long? = null,
    val startJunctionId: Long? = null,
    val startWaypointIndex: Int? = null,
    val endNodeId: Long? = null,
    val endJunctionId: Long? = null,
    val endWaypointIndex: Int? = null
)

/**
 * Mutually-exclusive canvas input states forming the finite state machine (FSM).
 */
sealed interface CanvasInputMode {
    data object Idle : CanvasInputMode

    data class Panning(
        val startPointer: Offset = Offset.Zero
    ) : CanvasInputMode

    data class MarqueeSelecting(
        val start: Offset,
        val current: Offset
    ) : CanvasInputMode

    data class ConnectingWire(
        val sourceNodeId: Long? = null,
        val sourcePortId: String? = null,
        val isOutput: Boolean = true,
        val sourceJunctionId: Long? = null,
        val intermediatePoints: List<Offset> = emptyList(),
        val livePos: Offset = Offset.Zero,
        val isStructured: Boolean = true
    ) : CanvasInputMode

    data class DraggingJunction(
        val junctionId: Long,
        val startPointer: Offset = Offset.Zero
    ) : CanvasInputMode

    data class DraggingWaypoint(
        val connection: Connection,
        val waypointIndex: Int
    ) : CanvasInputMode

    data class DraggingSegment(
        val segmentInfo: DraggingSegmentInfo
    ) : CanvasInputMode
}

@Stable
class BoardInteractionState {
    var inputMode: CanvasInputMode by mutableStateOf(CanvasInputMode.Idle)

    // Selection & Hover states
    var selectedConnection: Connection? by mutableStateOf(null)
    var hoveredConnection: Connection? by mutableStateOf(null)
    var hoveredConnectionIsSource: Boolean? by mutableStateOf(null)
    var isCtrlModifierPressed: Boolean by mutableStateOf(false)
    var isShiftModifierPressed: Boolean by mutableStateOf(false)
    var isAltModifierPressed: Boolean by mutableStateOf(false)
    var lastPointerPosition: Offset by mutableStateOf(Offset.Zero)
    var hoveredNodeId: Long? by mutableStateOf(null)
    var hoveredJunctionId: Long? by mutableStateOf(null)
    var selectedJunctionId: Long? by mutableStateOf(null)
    var hoveredWaypoint: Pair<Connection, Int>? by mutableStateOf(null)
    var hoveredMidpoint: Pair<Connection, Int>? by mutableStateOf(null)
    var pendingMidpoint: Pair<Connection, Int>? by mutableStateOf(null)
    var pendingMidpointPressPos: Offset by mutableStateOf(Offset.Zero)
    var pendingMidpointWasAltPressed: Boolean by mutableStateOf(false)

    // Dynamic line snapping during connection drag
    var snappedWirePoint: Offset? by mutableStateOf(null)
    var snappedWireConnection: Connection? by mutableStateOf(null)
    var snappedWireSegmentIndex: Int? by mutableStateOf(null)

    // Private backing storage for marquee & structured session compatibility
    private var _selectionStart: Offset? by mutableStateOf(null)
    private var _selectionEnd: Offset? by mutableStateOf(null)
    private var _draggingJunctionId: Long? by mutableStateOf(null)
    private var _draggingWaypoint: Pair<Connection, Int>? by mutableStateOf(null)
    private var _draggingSegment: DraggingSegmentInfo? by mutableStateOf(null)
    private var _structuredStartNodeId: Long? by mutableStateOf(null)
    private var _structuredStartPortId: String? by mutableStateOf(null)
    private var _structuredStartIsOutput: Boolean by mutableStateOf(true)
    private var _structuredSourceJunctionId: Long? by mutableStateOf(null)
    private var _structuredPoints: MutableList<Offset> by mutableStateOf(mutableListOf())
    private var _structuredLivePos: Offset by mutableStateOf(Offset.Zero)

    // --- FSM Transition Methods ---

    fun startPanning(startPointer: Offset = Offset.Zero) {
        inputMode = CanvasInputMode.Panning(startPointer)
    }

    fun startMarquee(start: Offset) {
        _selectionStart = start
        _selectionEnd = start
        inputMode = CanvasInputMode.MarqueeSelecting(start, start)
    }

    fun updateMarquee(current: Offset) {
        _selectionEnd = current
        val start = _selectionStart ?: current
        inputMode = CanvasInputMode.MarqueeSelecting(start, current)
    }

    fun clearSelectionBox() {
        _selectionStart = null
        _selectionEnd = null
        if (inputMode is CanvasInputMode.MarqueeSelecting) {
            inputMode = CanvasInputMode.Idle
        }
    }

    fun startConnectingWire(
        sourceNodeId: Long? = null,
        sourcePortId: String? = null,
        isOutput: Boolean = true,
        sourceJunctionId: Long? = null,
        initialWaypoints: List<Offset> = emptyList(),
        livePos: Offset = Offset.Zero,
        isStructured: Boolean = true
    ) {
        _structuredStartNodeId = sourceNodeId
        _structuredStartPortId = sourcePortId
        _structuredStartIsOutput = isOutput
        _structuredSourceJunctionId = sourceJunctionId
        _structuredPoints = initialWaypoints.toMutableList()
        _structuredLivePos = livePos
        inputMode = CanvasInputMode.ConnectingWire(
            sourceNodeId = sourceNodeId,
            sourcePortId = sourcePortId,
            isOutput = isOutput,
            sourceJunctionId = sourceJunctionId,
            intermediatePoints = initialWaypoints,
            livePos = livePos,
            isStructured = isStructured
        )
    }

    fun updateConnectingWire(livePos: Offset, waypoints: List<Offset>? = null) {
        _structuredLivePos = livePos
        if (waypoints != null) {
            _structuredPoints = waypoints.toMutableList()
        }
        val current = inputMode as? CanvasInputMode.ConnectingWire
        if (current != null) {
            inputMode = current.copy(
                livePos = livePos,
                intermediatePoints = waypoints ?: current.intermediatePoints
            )
        }
    }

    fun resetStructuredConnection() {
        _structuredStartNodeId = null
        _structuredStartPortId = null
        _structuredStartIsOutput = true
        _structuredSourceJunctionId = null
        _structuredPoints = mutableListOf()
        _structuredLivePos = Offset.Zero
        if (inputMode is CanvasInputMode.ConnectingWire) {
            inputMode = CanvasInputMode.Idle
        }
    }

    fun startDraggingJunction(junctionId: Long, startPointer: Offset = lastPointerPosition) {
        _draggingJunctionId = junctionId
        inputMode = CanvasInputMode.DraggingJunction(junctionId, startPointer)
    }

    fun startDraggingWaypoint(connection: Connection, waypointIndex: Int) {
        _draggingWaypoint = Pair(connection, waypointIndex)
        inputMode = CanvasInputMode.DraggingWaypoint(connection, waypointIndex)
    }

    fun startDraggingSegment(segmentInfo: DraggingSegmentInfo) {
        _draggingSegment = segmentInfo
        inputMode = CanvasInputMode.DraggingSegment(segmentInfo)
    }

    fun resetToIdle() {
        _selectionStart = null
        _selectionEnd = null
        _draggingJunctionId = null
        _draggingWaypoint = null
        _draggingSegment = null
        resetStructuredConnection()
        inputMode = CanvasInputMode.Idle
    }

    // --- Backward-Compatible Properties ---

    var selectionStart: Offset?
        get() = (inputMode as? CanvasInputMode.MarqueeSelecting)?.start
        set(value) {
            _selectionStart = value
            val end = _selectionEnd
            if (value != null && end != null) {
                inputMode = CanvasInputMode.MarqueeSelecting(value, end)
            } else if (value != null) {
                inputMode = CanvasInputMode.MarqueeSelecting(value, value)
            } else if (inputMode is CanvasInputMode.MarqueeSelecting) {
                inputMode = CanvasInputMode.Idle
            }
        }

    var selectionEnd: Offset?
        get() = (inputMode as? CanvasInputMode.MarqueeSelecting)?.current
        set(value) {
            _selectionEnd = value
            val start = _selectionStart
            if (start != null && value != null) {
                inputMode = CanvasInputMode.MarqueeSelecting(start, value)
            } else if (value != null) {
                inputMode = CanvasInputMode.MarqueeSelecting(value, value)
            } else if (inputMode is CanvasInputMode.MarqueeSelecting) {
                inputMode = CanvasInputMode.Idle
            }
        }

    var isDrawingStructuredConnection: Boolean
        get() = inputMode is CanvasInputMode.ConnectingWire && (inputMode as CanvasInputMode.ConnectingWire).isStructured
        set(value) {
            if (value) {
                if (inputMode !is CanvasInputMode.ConnectingWire) {
                    startConnectingWire(
                        sourceNodeId = _structuredStartNodeId,
                        sourcePortId = _structuredStartPortId,
                        isOutput = _structuredStartIsOutput,
                        sourceJunctionId = _structuredSourceJunctionId,
                        initialWaypoints = _structuredPoints,
                        livePos = _structuredLivePos,
                        isStructured = true
                    )
                }
            } else {
                if (inputMode is CanvasInputMode.ConnectingWire) {
                    resetStructuredConnection()
                }
            }
        }

    var structuredConnectionStartNodeId: Long?
        get() = (inputMode as? CanvasInputMode.ConnectingWire)?.sourceNodeId
        set(value) {
            _structuredStartNodeId = value
            val current = inputMode as? CanvasInputMode.ConnectingWire
            if (current != null) {
                inputMode = current.copy(sourceNodeId = value)
            }
        }

    var structuredConnectionStartPortId: String?
        get() = (inputMode as? CanvasInputMode.ConnectingWire)?.sourcePortId
        set(value) {
            _structuredStartPortId = value
            val current = inputMode as? CanvasInputMode.ConnectingWire
            if (current != null) {
                inputMode = current.copy(sourcePortId = value)
            }
        }

    var structuredConnectionStartIsOutput: Boolean
        get() = (inputMode as? CanvasInputMode.ConnectingWire)?.isOutput ?: _structuredStartIsOutput
        set(value) {
            _structuredStartIsOutput = value
            val current = inputMode as? CanvasInputMode.ConnectingWire
            if (current != null) {
                inputMode = current.copy(isOutput = value)
            }
        }

    var structuredConnectionSourceJunctionId: Long?
        get() = (inputMode as? CanvasInputMode.ConnectingWire)?.sourceJunctionId
        set(value) {
            _structuredSourceJunctionId = value
            val current = inputMode as? CanvasInputMode.ConnectingWire
            if (current != null) {
                inputMode = current.copy(sourceJunctionId = value)
            }
        }

    var structuredConnectionPoints: MutableList<Offset>
        get() = ((inputMode as? CanvasInputMode.ConnectingWire)?.intermediatePoints?.toMutableList()) ?: mutableListOf()
        set(value) {
            _structuredPoints = value
            val current = inputMode as? CanvasInputMode.ConnectingWire
            if (current != null) {
                inputMode = current.copy(intermediatePoints = value)
            }
        }

    var structuredConnectionLivePos: Offset
        get() = (inputMode as? CanvasInputMode.ConnectingWire)?.livePos ?: Offset.Zero
        set(value) {
            _structuredLivePos = value
            val current = inputMode as? CanvasInputMode.ConnectingWire
            if (current != null) {
                inputMode = current.copy(livePos = value)
            }
        }

    var draggingJunctionId: Long?
        get() = (inputMode as? CanvasInputMode.DraggingJunction)?.junctionId
        set(value) {
            _draggingJunctionId = value
            if (value != null) {
                inputMode = CanvasInputMode.DraggingJunction(value, lastPointerPosition)
            } else if (inputMode is CanvasInputMode.DraggingJunction) {
                inputMode = CanvasInputMode.Idle
            }
        }

    var draggingWaypoint: Pair<Connection, Int>?
        get() = (inputMode as? CanvasInputMode.DraggingWaypoint)?.let { Pair(it.connection, it.waypointIndex) }
        set(value) {
            _draggingWaypoint = value
            if (value != null) {
                inputMode = CanvasInputMode.DraggingWaypoint(value.first, value.second)
            } else if (inputMode is CanvasInputMode.DraggingWaypoint) {
                inputMode = CanvasInputMode.Idle
            }
        }

    var draggingSegment: DraggingSegmentInfo?
        get() = (inputMode as? CanvasInputMode.DraggingSegment)?.segmentInfo
        set(value) {
            _draggingSegment = value
            if (value != null) {
                inputMode = CanvasInputMode.DraggingSegment(value)
            } else if (inputMode is CanvasInputMode.DraggingSegment) {
                inputMode = CanvasInputMode.Idle
            }
        }

    // Unified point aliases
    var hoveredPointId: Long?
        get() = hoveredJunctionId
        set(value) { hoveredJunctionId = value }

    var selectedPointId: Long?
        get() = selectedJunctionId
        set(value) { selectedJunctionId = value }

    var draggingPointId: Long?
        get() = draggingJunctionId
        set(value) { draggingJunctionId = value }

    fun clearHoveredConnection() {
        hoveredConnection = null
        hoveredConnectionIsSource = null
    }

    fun clearHoveredNode() {
        hoveredNodeId = null
    }

    fun clearHoveredJunction() {
        hoveredJunctionId = null
    }

    fun clearHoveredWaypoint() {
        hoveredWaypoint = null
    }

    fun clearHoveredMidpoint() {
        hoveredMidpoint = null
    }

    fun clearSnapping() {
        snappedWirePoint = null
        snappedWireConnection = null
        snappedWireSegmentIndex = null
    }

    fun clearAllInteractions() {
        selectedConnection = null
        selectedJunctionId = null
        pendingMidpoint = null
        pendingMidpointPressPos = Offset.Zero
        pendingMidpointWasAltPressed = false
        clearHoveredConnection()
        clearHoveredNode()
        clearHoveredJunction()
        clearHoveredWaypoint()
        clearHoveredMidpoint()
        clearSnapping()
        resetToIdle()
    }
}
