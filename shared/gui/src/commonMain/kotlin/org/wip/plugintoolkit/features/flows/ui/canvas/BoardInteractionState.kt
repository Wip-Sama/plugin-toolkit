package org.wip.plugintoolkit.features.flows.ui.canvas

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.features.flows.model.Connection

@Stable
class BoardInteractionState {
    var selectedConnection: Connection? by mutableStateOf(null)
    var hoveredConnection: Connection? by mutableStateOf(null)
    var hoveredConnectionIsSource: Boolean? by mutableStateOf(null)
    var isCtrlModifierPressed: Boolean by mutableStateOf(false)
    var lastPointerPosition: Offset by mutableStateOf(Offset.Zero)
    var selectionStart: Offset? by mutableStateOf(null)
    var selectionEnd: Offset? by mutableStateOf(null)
    var hoveredNodeId: Long? by mutableStateOf(null)

    fun clearHoveredConnection() {
        hoveredConnection = null
        hoveredConnectionIsSource = null
    }

    fun clearHoveredNode() {
        hoveredNodeId = null
    }

    fun clearSelectionBox() {
        selectionStart = null
        selectionEnd = null
    }

    fun clearAllInteractions() {
        selectedConnection = null
        clearHoveredConnection()
        clearHoveredNode()
        clearSelectionBox()
    }
}
