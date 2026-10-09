package org.wip.plugintoolkit.features.shortcuts.engine

import org.wip.plugintoolkit.core.model.localized
import org.wip.plugintoolkit.features.shortcuts.model.KeyChord
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutActionId
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutCommand
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutKey
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutScope
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_copy
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_copy_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_delete_selected
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_delete_selected_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_escape
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_escape_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_export_image
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_export_image_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_eyedropper
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_eyedropper_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_paint_tool
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_paint_tool_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_paste
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_paste_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_redo
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_redo_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_structured_mode
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_structured_mode_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_undo
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_undo_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_wash_tool
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_wash_tool_desc

/**
 * Standard catalog of discrete keyboard accelerator commands across the application.
 */
object DefaultShortcutCommands {

    val commands: List<ShortcutCommand> = listOf(
        ShortcutCommand(
            id = ShortcutActionId.FLOW_UNDO,
            title = Res.string.shortcut_action_flow_undo.localized,
            description = Res.string.shortcut_action_flow_undo_desc.localized,
            scope = ShortcutScope.FeatureCanvas,
            defaultChords = listOf(
                KeyChord.primary(ShortcutKey.Z)
            )
        ),
        ShortcutCommand(
            id = ShortcutActionId.FLOW_REDO,
            title = Res.string.shortcut_action_flow_redo.localized,
            description = Res.string.shortcut_action_flow_redo_desc.localized,
            scope = ShortcutScope.FeatureCanvas,
            defaultChords = listOf(
                KeyChord.primary(ShortcutKey.Y),
                KeyChord.primary(ShortcutKey.Z, shift = true)
            )
        ),
        ShortcutCommand(
            id = ShortcutActionId.FLOW_DELETE_SELECTED,
            title = Res.string.shortcut_action_flow_delete_selected.localized,
            description = Res.string.shortcut_action_flow_delete_selected_desc.localized,
            scope = ShortcutScope.FeatureCanvas,
            defaultChords = listOf(
                KeyChord(ShortcutKey.Delete),
                KeyChord(ShortcutKey.Backspace)
            )
        ),
        ShortcutCommand(
            id = ShortcutActionId.FLOW_COPY,
            title = Res.string.shortcut_action_flow_copy.localized,
            description = Res.string.shortcut_action_flow_copy_desc.localized,
            scope = ShortcutScope.FeatureCanvas,
            defaultChords = listOf(
                KeyChord.primary(ShortcutKey.C)
            )
        ),
        ShortcutCommand(
            id = ShortcutActionId.FLOW_PASTE,
            title = Res.string.shortcut_action_flow_paste.localized,
            description = Res.string.shortcut_action_flow_paste_desc.localized,
            scope = ShortcutScope.FeatureCanvas,
            defaultChords = listOf(
                KeyChord.primary(ShortcutKey.V)
            )
        ),
        ShortcutCommand(
            id = ShortcutActionId.FLOW_ESCAPE,
            title = Res.string.shortcut_action_flow_escape.localized,
            description = Res.string.shortcut_action_flow_escape_desc.localized,
            scope = ShortcutScope.FeatureCanvas,
            defaultChords = listOf(
                KeyChord(ShortcutKey.Escape)
            )
        ),
        ShortcutCommand(
            id = ShortcutActionId.FLOW_EXPORT_IMAGE,
            title = Res.string.shortcut_action_flow_export_image.localized,
            description = Res.string.shortcut_action_flow_export_image_desc.localized,
            scope = ShortcutScope.FeatureCanvas,
            defaultChords = listOf(
                KeyChord.primary(ShortcutKey.E, shift = true)
            )
        ),
        ShortcutCommand(
            id = ShortcutActionId.FLOW_STRUCTURED_MODE,
            title = Res.string.shortcut_action_flow_structured_mode.localized,
            description = Res.string.shortcut_action_flow_structured_mode_desc.localized,
            scope = ShortcutScope.FeatureCanvas,
            defaultChords = listOf(
                KeyChord(ShortcutKey.M)
            )
        ),
        ShortcutCommand(
            id = ShortcutActionId.FLOW_PAINT_TOOL,
            title = Res.string.shortcut_action_flow_paint_tool.localized,
            description = Res.string.shortcut_action_flow_paint_tool_desc.localized,
            scope = ShortcutScope.FeatureCanvas,
            defaultChords = listOf(
                KeyChord(ShortcutKey.P),
                KeyChord(ShortcutKey.B)
            )
        ),
        ShortcutCommand(
            id = ShortcutActionId.FLOW_WASH_TOOL,
            title = Res.string.shortcut_action_flow_wash_tool.localized,
            description = Res.string.shortcut_action_flow_wash_tool_desc.localized,
            scope = ShortcutScope.FeatureCanvas,
            defaultChords = listOf(
                KeyChord(ShortcutKey.W)
            )
        ),
        ShortcutCommand(
            id = ShortcutActionId.FLOW_EYEDROPPER,
            title = Res.string.shortcut_action_flow_eyedropper.localized,
            description = Res.string.shortcut_action_flow_eyedropper_desc.localized,
            scope = ShortcutScope.FeatureCanvas,
            defaultChords = listOf(
                KeyChord(ShortcutKey.I)
            )
        )
    )

    private val commandMap: Map<String, ShortcutCommand> = commands.associateBy { it.id }

    fun findCommand(id: String): ShortcutCommand? = commandMap[id]
}
