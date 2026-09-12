package org.wip.plugintoolkit.features.shortcuts.logic

import org.wip.plugintoolkit.core.model.localized
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutAction
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutActionId
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutGesture
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutInputMode
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutKey
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutPointerButton
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutPriorities
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutSituation
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutTrigger
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_branch_wire
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_branch_wire_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_connect_port
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_connect_port_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_delete_selected
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_delete_selected_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_detach_connection
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_detach_connection_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_eyedropper
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_eyedropper_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_move_node
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_move_node_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_move_point
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_move_point_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_paint_tool
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_paint_tool_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_pan
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_pan_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_ramification
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_ramification_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_redo
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_redo_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_select_node
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_select_node_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_selection
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_selection_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_structured_mode
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_structured_mode_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_undo
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_undo_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_wash_tool
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_wash_tool_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_zoom
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_zoom_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_job_force_cancel
import plugintoolkit.composeapp.generated.resources.shortcut_action_job_force_cancel_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_scale_step
import plugintoolkit.composeapp.generated.resources.shortcut_action_scale_step_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_skip_confirmation
import plugintoolkit.composeapp.generated.resources.shortcut_action_skip_confirmation_desc

/**
 * Built-in default catalog defining all recognized shortcut actions across the toolkit.
 */
object DefaultShortcutCatalog {

    val actions: List<ShortcutAction> = listOf(
        // ── Global ────────────────────────────────────────────────────────
        ShortcutAction(
            id = ShortcutActionId.SKIP_CONFIRMATION,
            title = Res.string.shortcut_action_skip_confirmation.localized,
            description = Res.string.shortcut_action_skip_confirmation_desc.localized,
            situation = ShortcutSituation.Global,
            defaultTriggers = listOf(
                ShortcutTrigger(
                    isShift = true,
                    pointerButton = ShortcutPointerButton.Left,
                    gesture = ShortcutGesture.Click
                )
            ),
            relativePriority = ShortcutPriorities.Relative.HIGHEST
        ),

        // ── Flow Editor Board ─────────────────────────────────────────────
        ShortcutAction(
            id = ShortcutActionId.FLOW_PAN_CANVAS,
            title = Res.string.shortcut_action_flow_pan.localized,
            description = Res.string.shortcut_action_flow_pan_desc.localized,
            situation = ShortcutSituation.FlowBoard,
            defaultTriggers = listOf(
                ShortcutTrigger(
                    pointerButton = ShortcutPointerButton.Right,
                    gesture = ShortcutGesture.Drag
                ),
                ShortcutTrigger(
                    pointerButton = ShortcutPointerButton.Middle,
                    gesture = ShortcutGesture.Drag
                )
            ),
            relativePriority = ShortcutPriorities.Relative.HIGH
        ),
        ShortcutAction(
            id = ShortcutActionId.FLOW_ZOOM_CANVAS,
            title = Res.string.shortcut_action_flow_zoom.localized,
            description = Res.string.shortcut_action_flow_zoom_desc.localized,
            situation = ShortcutSituation.FlowBoard,
            defaultTriggers = listOf(
                ShortcutTrigger(
                    isCtrl = true,
                    gesture = ShortcutGesture.Wheel
                )
            ),
            relativePriority = ShortcutPriorities.Relative.HIGH
        ),
        ShortcutAction(
            id = ShortcutActionId.FLOW_SELECT_NODE,
            title = Res.string.shortcut_action_flow_select_node.localized,
            description = Res.string.shortcut_action_flow_select_node_desc.localized,
            situation = ShortcutSituation.FlowBoard,
            defaultTriggers = listOf(
                ShortcutTrigger(
                    pointerButton = ShortcutPointerButton.Left,
                    gesture = ShortcutGesture.Click
                )
            ),
            relativePriority = ShortcutPriorities.Relative.HIGHEST
        ),
        ShortcutAction(
            id = ShortcutActionId.FLOW_BOX_SELECT,
            title = Res.string.shortcut_action_flow_selection.localized,
            description = Res.string.shortcut_action_flow_selection_desc.localized,
            situation = ShortcutSituation.FlowBoard,
            defaultTriggers = listOf(
                ShortcutTrigger(
                    pointerButton = ShortcutPointerButton.Left,
                    gesture = ShortcutGesture.Drag
                )
            ),
            relativePriority = ShortcutPriorities.Relative.NORMAL
        ),
        ShortcutAction(
            id = ShortcutActionId.FLOW_STRUCTURED_MODE,
            title = Res.string.shortcut_action_flow_structured_mode.localized,
            description = Res.string.shortcut_action_flow_structured_mode_desc.localized,
            situation = ShortcutSituation.FlowBoard,
            defaultTriggers = listOf(
                ShortcutTrigger(
                    key = ShortcutKey.M
                )
            )
        ),
        ShortcutAction(
            id = ShortcutActionId.FLOW_PAINT_TOOL,
            title = Res.string.shortcut_action_flow_paint_tool.localized,
            description = Res.string.shortcut_action_flow_paint_tool_desc.localized,
            situation = ShortcutSituation.FlowBoard,
            defaultTriggers = listOf(
                ShortcutTrigger(
                    key = ShortcutKey.P
                ),
                ShortcutTrigger(
                    key = ShortcutKey.B
                )
            )
        ),
        ShortcutAction(
            id = ShortcutActionId.FLOW_WASH_TOOL,
            title = Res.string.shortcut_action_flow_wash_tool.localized,
            description = Res.string.shortcut_action_flow_wash_tool_desc.localized,
            situation = ShortcutSituation.FlowBoard,
            defaultTriggers = listOf(
                ShortcutTrigger(
                    key = ShortcutKey.W
                )
            )
        ),
        ShortcutAction(
            id = ShortcutActionId.FLOW_EYEDROPPER,
            title = Res.string.shortcut_action_flow_eyedropper.localized,
            description = Res.string.shortcut_action_flow_eyedropper_desc.localized,
            situation = ShortcutSituation.FlowBoard,
            defaultTriggers = listOf(
                ShortcutTrigger(
                    key = ShortcutKey.I
                )
            )
        ),
        ShortcutAction(
            id = ShortcutActionId.FLOW_UNDO,
            title = Res.string.shortcut_action_flow_undo.localized,
            description = Res.string.shortcut_action_flow_undo_desc.localized,
            situation = ShortcutSituation.FlowBoard,
            defaultTriggers = listOf(
                ShortcutTrigger(
                    isCtrl = true,
                    key = ShortcutKey.Z
                )
            )
        ),
        ShortcutAction(
            id = ShortcutActionId.FLOW_REDO,
            title = Res.string.shortcut_action_flow_redo.localized,
            description = Res.string.shortcut_action_flow_redo_desc.localized,
            situation = ShortcutSituation.FlowBoard,
            defaultTriggers = listOf(
                ShortcutTrigger(
                    isCtrl = true,
                    key = ShortcutKey.Y
                ),
                ShortcutTrigger(
                    isCtrl = true,
                    isShift = true,
                    key = ShortcutKey.Z
                )
            )
        ),

        // ── Flow Selection ────────────────────────────────────────────────
        ShortcutAction(
            id = ShortcutActionId.FLOW_MOVE_NODE,
            title = Res.string.shortcut_action_flow_move_node.localized,
            description = Res.string.shortcut_action_flow_move_node_desc.localized,
            situation = ShortcutSituation.FlowSelection,
            defaultTriggers = listOf(
                ShortcutTrigger(
                    pointerButton = ShortcutPointerButton.Left,
                    gesture = ShortcutGesture.Drag
                )
            ),
            relativePriority = ShortcutPriorities.Relative.HIGH
        ),
        ShortcutAction(
            id = ShortcutActionId.FLOW_DELETE_SELECTED,
            title = Res.string.shortcut_action_flow_delete_selected.localized,
            description = Res.string.shortcut_action_flow_delete_selected_desc.localized,
            situation = ShortcutSituation.FlowSelection,
            defaultTriggers = listOf(
                ShortcutTrigger(
                    key = ShortcutKey.Delete
                ),
                ShortcutTrigger(
                    key = ShortcutKey.Backspace
                )
            ),
            relativePriority = ShortcutPriorities.Relative.HIGHEST
        ),

        // ── Flow Node ─────────────────────────────────────────────────────
        ShortcutAction(
            id = ShortcutActionId.FLOW_CONNECT_PORT,
            title = Res.string.shortcut_action_flow_connect_port.localized,
            description = Res.string.shortcut_action_flow_connect_port_desc.localized,
            situation = ShortcutSituation.FlowNode,
            defaultTriggers = listOf(
                ShortcutTrigger(
                    pointerButton = ShortcutPointerButton.Left,
                    gesture = ShortcutGesture.Drag
                )
            ),
            relativePriority = ShortcutPriorities.Relative.HIGHEST
        ),

        // ── Flow Wire ─────────────────────────────────────────────────────
        ShortcutAction(
            id = ShortcutActionId.FLOW_DETACH_CONNECTION,
            title = Res.string.shortcut_action_flow_detach_connection.localized,
            description = Res.string.shortcut_action_flow_detach_connection_desc.localized,
            situation = ShortcutSituation.FlowWire,
            defaultTriggers = listOf(
                ShortcutTrigger(
                    isCtrl = true,
                    pointerButton = ShortcutPointerButton.Left,
                    gesture = ShortcutGesture.Drag
                )
            ),
            relativePriority = ShortcutPriorities.Relative.NORMAL
        ),
        ShortcutAction(
            id = ShortcutActionId.FLOW_BRANCH_WIRE,
            title = Res.string.shortcut_action_flow_branch_wire.localized,
            description = Res.string.shortcut_action_flow_branch_wire_desc.localized,
            situation = ShortcutSituation.FlowWire,
            defaultTriggers = listOf(
                ShortcutTrigger(
                    pointerButton = ShortcutPointerButton.Left,
                    gesture = ShortcutGesture.DoubleClick
                ),
                ShortcutTrigger(
                    isAlt = true,
                    pointerButton = ShortcutPointerButton.Left,
                    gesture = ShortcutGesture.Click
                ),
                ShortcutTrigger(
                    isShift = true,
                    pointerButton = ShortcutPointerButton.Left,
                    gesture = ShortcutGesture.Drag
                )
            ),
            relativePriority = ShortcutPriorities.Relative.NORMAL
        ),

        // ── Flow Connection Point ─────────────────────────────────────────
        ShortcutAction(
            id = ShortcutActionId.FLOW_MOVE_POINT,
            title = Res.string.shortcut_action_flow_move_point.localized,
            description = Res.string.shortcut_action_flow_move_point_desc.localized,
            situation = ShortcutSituation.FlowConnectionPoint,
            defaultTriggers = listOf(
                ShortcutTrigger(
                    pointerButton = ShortcutPointerButton.Left,
                    gesture = ShortcutGesture.Drag
                )
            ),
            relativePriority = ShortcutPriorities.Relative.HIGH
        ),
        ShortcutAction(
            id = ShortcutActionId.FLOW_CREATE_RAMIFICATION,
            title = Res.string.shortcut_action_flow_ramification.localized,
            description = Res.string.shortcut_action_flow_ramification_desc.localized,
            situation = ShortcutSituation.FlowConnectionPoint,
            defaultTriggers = listOf(
                ShortcutTrigger(
                    isAlt = true,
                    pointerButton = ShortcutPointerButton.Left,
                    gesture = ShortcutGesture.Drag
                )
            ),
            relativePriority = ShortcutPriorities.Relative.HIGH
        ),

        // ── Job Terminal ──────────────────────────────────────────────────
        ShortcutAction(
            id = ShortcutActionId.JOB_FORCE_CANCEL,
            title = Res.string.shortcut_action_job_force_cancel.localized,
            description = Res.string.shortcut_action_job_force_cancel_desc.localized,
            situation = ShortcutSituation.JobTerminal,
            defaultTriggers = listOf(
                ShortcutTrigger(
                    isShift = true,
                    pointerButton = ShortcutPointerButton.Left,
                    gesture = ShortcutGesture.Click
                )
            )
        ),

        // ── Settings ──────────────────────────────────────────────────────
        ShortcutAction(
            id = ShortcutActionId.SETTINGS_SCALE_2X_STEP,
            title = Res.string.shortcut_action_scale_step.localized,
            description = Res.string.shortcut_action_scale_step_desc.localized,
            situation = ShortcutSituation.Settings,
            defaultTriggers = listOf(
                ShortcutTrigger(
                    isShift = true,
                    pointerButton = ShortcutPointerButton.Left,
                    gesture = ShortcutGesture.Click
                )
            )
        )
    )

    private val actionMap: Map<String, ShortcutAction> = actions.associateBy { it.id }

    fun findAction(id: String): ShortcutAction? = actionMap[id]
}
