package org.wip.plugintoolkit.features.shortcuts.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.model.resolve
import org.wip.plugintoolkit.core.model.resolveNonComposable
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.ui.DialogService
import org.wip.plugintoolkit.core.utils.FileUtils
import org.wip.plugintoolkit.features.controls.model.CanvasControlScheme
import org.wip.plugintoolkit.features.settings.utils.LocalSettingsSearchQuery
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsViewModel
import org.wip.plugintoolkit.features.shortcuts.logic.ShortcutManager
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutAction
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutActionId
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutPriorityMode
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutSituation
import org.wip.plugintoolkit.shared.components.settings.ExpressiveMenu
import org.wip.plugintoolkit.shared.components.settings.SettingsGroup
import org.wip.plugintoolkit.shared.components.settings.SettingsItem
import org.wip.plugintoolkit.shared.components.settings.SettingsSlider
import org.wip.plugintoolkit.shared.components.settings.SettingsSwitch
import org.wip.plugintoolkit.shared.components.settings.getGroupedShape
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.canvas_controls_box_select
import plugintoolkit.composeapp.generated.resources.canvas_controls_box_select_desc
import plugintoolkit.composeapp.generated.resources.canvas_controls_pan_binding
import plugintoolkit.composeapp.generated.resources.canvas_controls_pan_binding_desc
import plugintoolkit.composeapp.generated.resources.canvas_controls_reset
import plugintoolkit.composeapp.generated.resources.canvas_controls_reset_desc
import plugintoolkit.composeapp.generated.resources.canvas_controls_title
import plugintoolkit.composeapp.generated.resources.canvas_controls_toggle_select
import plugintoolkit.composeapp.generated.resources.canvas_controls_toggle_select_desc
import plugintoolkit.composeapp.generated.resources.canvas_controls_zoom_invert
import plugintoolkit.composeapp.generated.resources.canvas_controls_zoom_invert_desc
import plugintoolkit.composeapp.generated.resources.canvas_controls_zoom_require_ctrl
import plugintoolkit.composeapp.generated.resources.canvas_controls_zoom_require_ctrl_desc
import plugintoolkit.composeapp.generated.resources.canvas_controls_zoom_sensitivity
import plugintoolkit.composeapp.generated.resources.canvas_controls_zoom_sensitivity_desc
import plugintoolkit.composeapp.generated.resources.canvas_controls_zoom_wheel
import plugintoolkit.composeapp.generated.resources.canvas_controls_zoom_wheel_desc
import plugintoolkit.composeapp.generated.resources.setting_shortcut_priority_system_subtitle
import plugintoolkit.composeapp.generated.resources.setting_shortcut_priority_system_title
import plugintoolkit.composeapp.generated.resources.shortcut_priority_mode_static
import plugintoolkit.composeapp.generated.resources.shortcut_priority_mode_zindex
import plugintoolkit.composeapp.generated.resources.shortcuts_conflicts_detected
import plugintoolkit.composeapp.generated.resources.shortcuts_reset
import plugintoolkit.composeapp.generated.resources.shortcuts_section_keyboard
import plugintoolkit.composeapp.generated.resources.shortcuts_section_priority
import plugintoolkit.composeapp.generated.resources.shortcuts_title

import org.wip.plugintoolkit.shared.components.verticalFadingEdges

private val canvasPointerActionIds = setOf(
    ShortcutActionId.FLOW_PAN_CANVAS,
    ShortcutActionId.FLOW_ZOOM_CANVAS,
    ShortcutActionId.FLOW_SELECT_NODE,
    ShortcutActionId.FLOW_TOGGLE_SELECTION,
    ShortcutActionId.FLOW_BOX_SELECT,
    ShortcutActionId.FLOW_MOVE_NODE,
    ShortcutActionId.FLOW_CONNECT_PORT,
    ShortcutActionId.FLOW_DETACH_CONNECTION
)

/**
 * Standardized Settings view for shortcuts and gestures.
 * Adheres strictly to the standard SettingsGroup/SettingsItem visuals and utilizes
 * the global sidebar search query via [LocalSettingsSearchQuery].
 */
@Composable
fun ShortcutsSettingsView(
    shortcutManager: ShortcutManager,
    settingsViewModel: SettingsViewModel,
    dialogService: DialogService? = null,
    modifier: Modifier = Modifier
) {
    val settings by settingsViewModel.settings.collectAsState()
    val searchQuery = LocalSettingsSearchQuery.current
    var actionToRemap by remember { mutableStateOf<ShortcutAction?>(null) }

    val allConflicts = remember(settings.shortcuts) {
        shortcutManager.findConflicts().filter { conflict ->
            conflict.action1.id !in canvasPointerActionIds && conflict.action2.id !in canvasPointerActionIds
        }
    }

    val showCanvasControls = remember(searchQuery) {
        if (searchQuery.isBlank()) true
        else {
            val q = searchQuery.trim().lowercase()
            listOf("canvas", "mouse", "pan", "zoom", "wheel", "ctrl", "scroll", "box", "select", "toggle", "gestures", "controls").any {
                it.contains(q) || q.contains(it)
            }
        }
    }

    val showPrioritySection = remember(searchQuery) {
        if (searchQuery.isBlank()) true
        else {
            val q = searchQuery.trim().lowercase()
            listOf("priority", "zindex", "elevation", "mode").any {
                it.contains(q) || q.contains(it)
            }
        }
    }

    // Filter actions according to search query and prune canvas pointer actions from discrete keyboard shortcuts
    val filteredActions = remember(searchQuery, settings.shortcuts) {
        if (searchQuery.isBlank()) {
            shortcutManager.allActions.filter { it.id !in canvasPointerActionIds }
        } else {
            val query = searchQuery.trim()
            shortcutManager.allActions.filter { action ->
                val title = action.title.resolveNonComposable()
                val desc = action.description.resolveNonComposable()
                val effectiveTriggers = shortcutManager.getEffectiveTriggers(action.id)

                title.contains(query, ignoreCase = true) ||
                        desc.contains(query, ignoreCase = true) ||
                        action.situation.displayLabel.contains(query, ignoreCase = true) ||
                        effectiveTriggers.any { it.format().contains(query, ignoreCase = true) }
            }
        }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalFadingEdges(
                scrollState = scrollState,
                topFadeLength = ToolkitTheme.spacing.medium,
                bottomFadeLength = ToolkitTheme.spacing.medium
            )
            .verticalScroll(scrollState)
    ) {
        // Global Conflict Banner
        AnimatedVisibility(
            visible = allConflicts.isNotEmpty(),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                shape = ToolkitTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
                border = BorderStroke(
                    ToolkitTheme.dimensions.borderThin,
                    MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(ToolkitTheme.spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                    )
                    Text(
                        text = stringResource(Res.string.shortcuts_conflicts_detected, allConflicts.size),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // ── Canvas & Mouse Controls ─────────────────────────────────────
        if (showCanvasControls) {
            SettingsGroup(title = stringResource(Res.string.canvas_controls_title)) {
                // Item 0: Panning
                SettingsItem(
                    title = stringResource(Res.string.canvas_controls_pan_binding),
                    subtitle = stringResource(Res.string.canvas_controls_pan_binding_desc),
                    icon = Icons.Default.PanTool,
                    shape = getGroupedShape(0, 8),
                    control = {
                        Surface(
                            shape = ToolkitTheme.shapes.pill,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "[ Right Drag / Middle Drag ]",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(
                                    horizontal = ToolkitTheme.spacing.small,
                                    vertical = ToolkitTheme.spacing.extraExtraSmall
                                )
                            )
                        }
                    }
                )

                // Item 1: Wheel Zoom
                SettingsItem(
                    title = stringResource(Res.string.canvas_controls_zoom_wheel),
                    subtitle = stringResource(Res.string.canvas_controls_zoom_wheel_desc),
                    icon = Icons.Default.ZoomIn,
                    shape = getGroupedShape(1, 8),
                    control = {
                        SettingsSwitch(
                            checked = settings.controls.zoomWithWheel,
                            onCheckedChange = { enabled ->
                                settingsViewModel.updateSettings { current ->
                                    current.copy(controls = current.controls.copy(zoomWithWheel = enabled))
                                }
                            }
                        )
                    }
                )

                // Item 2: Require Ctrl / Cmd
                SettingsItem(
                    title = stringResource(Res.string.canvas_controls_zoom_require_ctrl),
                    subtitle = stringResource(Res.string.canvas_controls_zoom_require_ctrl_desc),
                    icon = Icons.Default.ZoomIn,
                    shape = getGroupedShape(2, 8),
                    control = {
                        SettingsSwitch(
                            checked = settings.controls.zoomRequiresCtrl,
                            enabled = settings.controls.zoomWithWheel,
                            onCheckedChange = { req ->
                                settingsViewModel.updateSettings { current ->
                                    current.copy(controls = current.controls.copy(zoomRequiresCtrl = req))
                                }
                            }
                        )
                    }
                )

                // Item 3: Invert Zoom Direction
                SettingsItem(
                    title = stringResource(Res.string.canvas_controls_zoom_invert),
                    subtitle = stringResource(Res.string.canvas_controls_zoom_invert_desc),
                    icon = Icons.Default.Refresh,
                    shape = getGroupedShape(3, 8),
                    control = {
                        SettingsSwitch(
                            checked = settings.controls.invertZoomDirection,
                            enabled = settings.controls.zoomWithWheel,
                            onCheckedChange = { invert ->
                                settingsViewModel.updateSettings { current ->
                                    current.copy(controls = current.controls.copy(invertZoomDirection = invert))
                                }
                            }
                        )
                    }
                )

                // Item 4: Zoom Sensitivity
                val formattedZoom = "${(settings.controls.zoomSensitivity * 10f).roundToInt() / 10f}x"
                SettingsItem(
                    title = stringResource(Res.string.canvas_controls_zoom_sensitivity, formattedZoom),
                    subtitle = stringResource(Res.string.canvas_controls_zoom_sensitivity_desc),
                    icon = Icons.Default.Timeline,
                    shape = getGroupedShape(4, 8),
                    control = {
                        SettingsSlider(
                            value = settings.controls.zoomSensitivity,
                            onValueChange = { newSens ->
                                val rounded = (newSens * 10f).roundToInt() / 10f
                                settingsViewModel.updateSettings { current ->
                                    current.copy(controls = current.controls.copy(zoomSensitivity = rounded))
                                }
                            },
                            valueRange = 0.2f..3.0f,
                            steps = 0,
                            enabled = settings.controls.zoomWithWheel
                        )
                    }
                )

                // Item 5: Box Selection
                SettingsItem(
                    title = stringResource(Res.string.canvas_controls_box_select),
                    subtitle = stringResource(Res.string.canvas_controls_box_select_desc),
                    icon = Icons.Default.CropSquare,
                    shape = getGroupedShape(5, 8),
                    control = {
                        Surface(
                            shape = ToolkitTheme.shapes.pill,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = "[ ${settings.controls.boxSelectBinding.button.displayName} Drag ]",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(
                                    horizontal = ToolkitTheme.spacing.small,
                                    vertical = ToolkitTheme.spacing.extraExtraSmall
                                )
                            )
                        }
                    }
                )

                // Item 6: Toggle Selection (Multi-Select)
                SettingsItem(
                    title = stringResource(Res.string.canvas_controls_toggle_select),
                    subtitle = stringResource(Res.string.canvas_controls_toggle_select_desc),
                    icon = Icons.Default.TouchApp,
                    shape = getGroupedShape(6, 8),
                    control = {
                        Surface(
                            shape = ToolkitTheme.shapes.pill,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            val modifierName = if (FileUtils.isMac) "Cmd" else "Ctrl"
                            Text(
                                text = "[ $modifierName + ${settings.controls.toggleSelectBinding.button.displayName} ]",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(
                                    horizontal = ToolkitTheme.spacing.small,
                                    vertical = ToolkitTheme.spacing.extraExtraSmall
                                )
                            )
                        }
                    }
                )

                // Item 7: Reset Canvas Controls
                SettingsItem(
                    title = stringResource(Res.string.canvas_controls_reset),
                    subtitle = stringResource(Res.string.canvas_controls_reset_desc),
                    icon = Icons.Default.Restore,
                    shape = getGroupedShape(7, 8),
                    control = {
                        OutlinedButton(
                            onClick = {
                                settingsViewModel.updateSettings { current ->
                                    current.copy(controls = CanvasControlScheme())
                                }
                            },
                            contentPadding = PaddingValues(
                                horizontal = ToolkitTheme.spacing.small,
                                vertical = ToolkitTheme.spacing.extraExtraSmall
                            ),
                            shape = ToolkitTheme.shapes.pill
                        ) {
                            Text(
                                text = stringResource(Res.string.shortcuts_reset),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            }
        }

        // ── Priority & Elevation System ──────────────────────────────────
        if (showPrioritySection) {
            SettingsGroup(title = stringResource(Res.string.shortcuts_section_priority)) {
                SettingsItem(
                    title = stringResource(Res.string.setting_shortcut_priority_system_title),
                    subtitle = stringResource(Res.string.setting_shortcut_priority_system_subtitle),
                    icon = Icons.Default.Layers,
                    shape = getGroupedShape(0, 1),
                    control = {
                        ExpressiveMenu(
                            options = ShortcutPriorityMode.entries,
                            selectedOption = settings.shortcuts.priorityMode,
                            onOptionSelected = { newMode ->
                                settingsViewModel.updateSettings { current ->
                                    current.copy(shortcuts = current.shortcuts.copy(priorityMode = newMode))
                                }
                            },
                            labelProvider = { mode ->
                                when (mode) {
                                    ShortcutPriorityMode.ZIndexAndPriority -> stringResource(Res.string.shortcut_priority_mode_zindex)
                                    ShortcutPriorityMode.PriorityOnly -> stringResource(Res.string.shortcut_priority_mode_static)
                                }
                            }
                        )
                    }
                )
            }
        }

        // ── Keyboard Shortcuts ───────────────────────────────────────────
        if (filteredActions.isNotEmpty()) {
            SettingsGroup(title = stringResource(Res.string.shortcuts_section_keyboard)) {
                filteredActions.forEachIndexed { index, action ->
                    val isCustomized = shortcutManager.isCustomized(action.id)
                    val hasConflict = allConflicts.any { it.action1.id == action.id || it.action2.id == action.id }
                    val effectivePri = shortcutManager.getEffectiveActionPriority(action.id).toInt()
                    val relPri = shortcutManager.getEffectiveRelativePriority(action.id)
                    val relStr = if (relPri >= 0) "+$relPri" else "$relPri"

                    SettingsItem(
                        title = action.title.resolve(),
                        subtitle = "${action.situation.displayLabel} • ${action.description.resolve()} • Priority: $effectivePri (relative $relStr)",
                        icon = getActionIcon(action.id, action.situation),
                        shape = getGroupedShape(index, filteredActions.size),
                        control = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                            ) {
                                if (hasConflict) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                    )
                                }

                                OutlinedButton(
                                    onClick = { actionToRemap = action },
                                    contentPadding = PaddingValues(
                                        horizontal = ToolkitTheme.spacing.small,
                                        vertical = ToolkitTheme.spacing.extraExtraSmall
                                    ),
                                    shape = ToolkitTheme.shapes.pill
                                ) {
                                    Text(
                                        text = shortcutManager.formatEffectiveTriggers(action.id),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (isCustomized) {
                                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                                        IconButton(
                                            onClick = {
                                                shortcutManager.resetBinding(action.id)
                                                shortcutManager.resetRelativePriority(action.id)
                                            },
                                            modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = stringResource(Res.string.shortcuts_reset),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(ToolkitTheme.spacing.large))
    }

    // Interactive Remap Dialog
    actionToRemap?.let { action ->
        RemapShortcutDialog(
            action = action,
            currentTriggers = shortcutManager.getEffectiveTriggers(action.id),
            shortcutManager = shortcutManager,
            onDismiss = { actionToRemap = null },
            onSave = { newTriggers, newRelPriority ->
                shortcutManager.updateBindings(action.id, newTriggers)
                shortcutManager.updateRelativePriority(action.id, newRelPriority)
                actionToRemap = null
            },
            onReset = {
                shortcutManager.resetBinding(action.id)
                shortcutManager.resetRelativePriority(action.id)
                actionToRemap = null
            }
        )
    }
}

private fun getActionIcon(actionId: String, situation: ShortcutSituation): ImageVector {
    return when (actionId) {
        ShortcutActionId.FLOW_PAN_CANVAS -> Icons.Default.PanTool
        ShortcutActionId.FLOW_ZOOM_CANVAS -> Icons.Default.ZoomIn
        ShortcutActionId.FLOW_SELECT_NODE -> Icons.Default.TouchApp
        ShortcutActionId.FLOW_TOGGLE_SELECTION -> Icons.Default.TouchApp
        ShortcutActionId.FLOW_BOX_SELECT -> Icons.Default.CropSquare
        ShortcutActionId.FLOW_MOVE_NODE -> Icons.Default.PanTool
        ShortcutActionId.FLOW_DELETE_SELECTED -> Icons.Default.Delete
        ShortcutActionId.FLOW_CONNECT_PORT -> Icons.Default.Power
        ShortcutActionId.FLOW_DETACH_CONNECTION -> Icons.Default.AltRoute
        ShortcutActionId.FLOW_BRANCH_WIRE -> Icons.Default.AccountTree
        ShortcutActionId.FLOW_MOVE_POINT -> Icons.Default.Timeline
        ShortcutActionId.FLOW_CREATE_RAMIFICATION -> Icons.Default.AccountTree
        ShortcutActionId.FLOW_PAINT_TOOL -> Icons.Default.FormatPaint
        ShortcutActionId.FLOW_UNDO -> Icons.Default.Undo
        ShortcutActionId.FLOW_REDO -> Icons.Default.Redo
        ShortcutActionId.FLOW_COPY -> Icons.Default.ContentCopy
        ShortcutActionId.FLOW_PASTE -> Icons.Default.ContentCopy
        ShortcutActionId.FLOW_ESCAPE -> Icons.Default.Close
        ShortcutActionId.JOB_FORCE_CANCEL -> Icons.Default.Terminal
        ShortcutActionId.SETTINGS_SCALE_2X_STEP -> Icons.Default.Settings
        else -> when (situation) {
            ShortcutSituation.FlowBoard, ShortcutSituation.FlowConnectionPoint,
            ShortcutSituation.FlowSelection, ShortcutSituation.FlowNode -> Icons.Default.Timeline
            ShortcutSituation.JobTerminal -> Icons.Default.Terminal
            ShortcutSituation.Settings -> Icons.Default.Settings
            else -> Icons.Default.Keyboard
        }
    }
}
