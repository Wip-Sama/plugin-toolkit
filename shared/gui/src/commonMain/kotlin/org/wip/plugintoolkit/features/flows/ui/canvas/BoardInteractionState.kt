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

@Stable
class BoardInteractionState {
    var selectedConnection: Connection? by mutableStateOf(null)
    var hoveredConnection: Connection? by mutableStateOf(null)
    var hoveredConnectionIsSource: Boolean? by mutableStateOf(null)
    var isCtrlModifierPressed: Boolean by mutableStateOf(false)
    var isShiftModifierPressed: Boolean by mutableStateOf(false)
    var isAltModifierPressed: Boolean by mutableStateOf(false)
    var lastPointerPosition: Offset by mutableStateOf(Offset.Zero)
    var selectionStart: Offset? by mutableStateOf(null)
    var selectionEnd: Offset? by mutableStateOf(null)
    var hoveredNodeId: Long? by mutableStateOf(null)
    var hoveredJunctionId: Long? by mutableStateOf(null)
    var selectedJunctionId: Long? by mutableStateOf(null)
    var draggingJunctionId: Long? by mutableStateOf(null)
    var hoveredWaypoint: Pair<Connection, Int>? by mutableStateOf(null)
    var draggingWaypoint: Pair<Connection, Int>? by mutableStateOf(null)
    var hoveredMidpoint: Pair<Connection, Int>? by mutableStateOf(null)
    var pendingMidpoint: Pair<Connection, Int>? by mutableStateOf(null)
    var pendingMidpointPressPos: Offset by mutableStateOf(Offset.Zero)
    var pendingMidpointWasAltPressed: Boolean by mutableStateOf(false)

    // Structured / Multi-point connection drawing session
    var isDrawingStructuredConnection: Boolean by mutableStateOf(false)
    var structuredConnectionStartNodeId: Long? by mutableStateOf(null)
    var structuredConnectionStartPortId: String? by mutableStateOf(null)
    var structuredConnectionStartIsOutput: Boolean by mutableStateOf(true)
    var structuredConnectionSourceJunctionId: Long? by mutableStateOf(null)
    var structuredConnectionPoints: MutableList<Offset> by mutableStateOf(mutableListOf())
    var structuredConnectionLivePos: Offset by mutableStateOf(Offset.Zero)

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

    fun resetStructuredConnection() {
        isDrawingStructuredConnection = false
        structuredConnectionStartNodeId = null
        structuredConnectionStartPortId = null
        structuredConnectionStartIsOutput = true
        structuredConnectionSourceJunctionId = null
        structuredConnectionPoints = mutableListOf()
        structuredConnectionLivePos = Offset.Zero
    }

    fun clearSelectionBox() {
        selectionStart = null
        selectionEnd = null
    }

    fun clearAllInteractions() {
        selectedConnection = null
        selectedJunctionId = null
        draggingJunctionId = null
        draggingWaypoint = null
        pendingMidpoint = null
        pendingMidpointPressPos = Offset.Zero
        pendingMidpointWasAltPressed = false
        clearHoveredConnection()
        clearHoveredNode()
        clearHoveredJunction()
        clearHoveredWaypoint()
        clearHoveredMidpoint()
        clearSelectionBox()
        resetStructuredConnection()
    }
}
