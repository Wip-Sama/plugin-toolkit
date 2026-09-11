package org.wip.plugintoolkit.features.flows.ui.canvas

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
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
    onPaste: (Offset) -> Unit,
    isReadOnly: Boolean = false,
    onTogglePaintTool: (() -> Unit)? = null,
    onToggleWashTool: (() -> Unit)? = null,
    onToggleStructuredConnectionMode: (() -> Unit)? = null,
    onLeaveStructuredConnectionAtLastPoint: ((Long?, String?, Long?, List<Offset>) -> Unit)? = null
): Modifier = this.onKeyEvent { keyEvent ->
    val newCtrlPressed = keyEvent.isCtrlPressed
    val newShiftPressed = keyEvent.isShiftPressed
    interactionState.isShiftModifierPressed = newShiftPressed
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
            keyEvent.key == Key.Escape -> {
                if (interactionState.isDrawingStructuredConnection) {
                    if (interactionState.structuredConnectionPoints.isNotEmpty()) {
                        onLeaveStructuredConnectionAtLastPoint?.invoke(
                            interactionState.structuredConnectionStartNodeId,
                            interactionState.structuredConnectionStartPortId,
                            interactionState.structuredConnectionSourceJunctionId,
                            interactionState.structuredConnectionPoints.toList()
                        )
                    }
                    interactionState.resetStructuredConnection()
                    true
                } else {
                    false
                }
            }

            keyEvent.key == Key.Delete || keyEvent.key == Key.Backspace -> {
                if (selectedNodeIds.isNotEmpty() && !isReadOnly) {
                    onDeleteSelectedNodes()
                    true
                } else {
                    false
                }
            }

            keyEvent.isCtrlPressed && keyEvent.key == Key.Z -> {
                if (!isReadOnly) onUndo()
                true
            }

            keyEvent.isCtrlPressed && keyEvent.key == Key.Y -> {
                if (!isReadOnly) onRedo()
                true
            }

            keyEvent.isCtrlPressed && keyEvent.key == Key.C -> {
                onCopy()
                true
            }

            keyEvent.isCtrlPressed && keyEvent.key == Key.V -> {
                if (!isReadOnly) {
                    val boardPos = (interactionState.lastPointerPosition - offset) / scale
                    onPaste(boardPos)
                }
                true
            }

            !keyEvent.isCtrlPressed && (keyEvent.key == Key.P || keyEvent.key == Key.B) -> {
                if (!isReadOnly && onTogglePaintTool != null) {
                    onTogglePaintTool()
                    true
                } else {
                    false
                }
            }

            !keyEvent.isCtrlPressed && keyEvent.key == Key.W -> {
                if (!isReadOnly && onToggleWashTool != null) {
                    onToggleWashTool()
                    true
                } else {
                    false
                }
            }

            !keyEvent.isCtrlPressed && keyEvent.key == Key.M -> {
                if (!isReadOnly && onToggleStructuredConnectionMode != null) {
                    onToggleStructuredConnectionMode()
                    true
                } else {
                    false
                }
            }

            else -> false
        }
    } else {
        false
    }
}
