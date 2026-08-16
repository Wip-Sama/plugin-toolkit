package org.wip.plugintoolkit.features.flows.ui.canvas

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

fun Modifier.boardKeyboardHandler(
    interactionState: BoardInteractionState,
    scale: Float,
    offset: Offset,
    getPortBoardPosition: (Long, String, Boolean) -> Offset?,
    selectedNodeIds: Set<Long>,
    onDeleteSelectedNodes: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCopy: () -> Unit,
    onPaste: (Offset) -> Unit
): Modifier = this.onKeyEvent { keyEvent ->
    val newCtrlPressed = keyEvent.isCtrlPressed
    if (interactionState.isCtrlModifierPressed != newCtrlPressed) {
        interactionState.isCtrlModifierPressed = newCtrlPressed
        val hoveredConn = interactionState.hoveredConnection
        if (hoveredConn != null && newCtrlPressed) {
            val sourcePortBoardPos = getPortBoardPosition(hoveredConn.sourceNodeId, hoveredConn.sourcePortId, true)
            val targetPortBoardPos = getPortBoardPosition(hoveredConn.targetNodeId, hoveredConn.targetPortId, false)
            if (sourcePortBoardPos != null && targetPortBoardPos != null) {
                val startPos = (sourcePortBoardPos * scale) + offset
                val endPos = (targetPortBoardPos * scale) + offset
                interactionState.hoveredConnectionIsSource = ConnectionHitTester.determineCloserEnd(
                    interactionState.lastPointerPosition,
                    startPos,
                    endPos
                )
            }
        } else {
            interactionState.hoveredConnectionIsSource = null
        }
    }

    if (keyEvent.type == KeyEventType.KeyDown) {
        when {
            keyEvent.key == Key.Delete || keyEvent.key == Key.Backspace -> {
                if (selectedNodeIds.isNotEmpty()) {
                    onDeleteSelectedNodes()
                    true
                } else {
                    false
                }
            }

            keyEvent.isCtrlPressed && keyEvent.key == Key.Z -> {
                onUndo()
                true
            }

            keyEvent.isCtrlPressed && keyEvent.key == Key.Y -> {
                onRedo()
                true
            }

            keyEvent.isCtrlPressed && keyEvent.key == Key.C -> {
                onCopy()
                true
            }

            keyEvent.isCtrlPressed && keyEvent.key == Key.V -> {
                val boardPos = (interactionState.lastPointerPosition - offset) / scale
                onPaste(boardPos)
                true
            }

            else -> false
        }
    } else {
        false
    }
}
