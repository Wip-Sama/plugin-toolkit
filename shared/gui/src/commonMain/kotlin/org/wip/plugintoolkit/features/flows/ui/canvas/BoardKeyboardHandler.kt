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
import org.wip.plugintoolkit.features.shortcuts.logic.ShortcutManager
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutActionId
import org.wip.plugintoolkit.features.shortcuts.ui.shortcutHandler

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
    onLeaveStructuredConnectionAtLastPoint: ((Long?, String?, Long?, List<Offset>) -> Unit)? = null,
    shortcutManager: ShortcutManager? = null
): Modifier = this
    .onKeyEvent { keyEvent ->
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
        false
    }
    .shortcutHandler(shortcutManager) {
        onKey(ShortcutActionId.FLOW_UNDO, enabled = !isReadOnly) {
            onUndo()
        }

        onKey(ShortcutActionId.FLOW_REDO, enabled = !isReadOnly) {
            onRedo()
        }

        onKey(
            ShortcutActionId.FLOW_DELETE_SELECTED,
            enabled = !isReadOnly && selectedNodeIds.isNotEmpty()
        ) {
            onDeleteSelectedNodes()
        }

        onKey(
            ShortcutActionId.FLOW_PAINT_TOOL,
            enabled = !isReadOnly && onTogglePaintTool != null
        ) {
            onTogglePaintTool?.invoke()
        }

        onKey(
            ShortcutActionId.FLOW_WASH_TOOL,
            enabled = !isReadOnly && onToggleWashTool != null
        ) {
            onToggleWashTool?.invoke()
        }

        onKey(
            ShortcutActionId.FLOW_STRUCTURED_MODE,
            enabled = !isReadOnly && onToggleStructuredConnectionMode != null
        ) {
            onToggleStructuredConnectionMode?.invoke()
        }

        onRawKey { keyEvent ->
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
                        } else false
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

                    else -> false
                }
            } else false
        }
    }

