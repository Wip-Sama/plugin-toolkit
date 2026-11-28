package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.colorpicker.ui.ColorPickerDialog
import org.wip.plugintoolkit.features.colorpicker.utils.toHex
import org.wip.plugintoolkit.features.flows.ui.components.FlowControlsInfoCard
import org.wip.plugintoolkit.features.settings.model.ConnectionCurveStyle
import org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode
import org.wip.plugintoolkit.shared.components.ToolkitConnectedToggleGroup
import org.wip.plugintoolkit.shared.components.ToolkitToggleOption
import org.wip.plugintoolkit.shared.components.ToolkitTooltipBox
import org.wip.plugintoolkit.shared.components.ToolkitTooltipPosition
import org.wip.plugintoolkit.shared.components.ZoomControls
import org.wip.plugintoolkit.shared.components.plugin.inputs.parseColorString
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.flow_connection_style_cardinal
import plugintoolkit.composeapp.generated.resources.flow_connection_style_orthogonal
import plugintoolkit.composeapp.generated.resources.flow_connection_style_straight
import plugintoolkit.composeapp.generated.resources.flow_export_image_title
import plugintoolkit.composeapp.generated.resources.flow_export_image_tooltip
import plugintoolkit.composeapp.generated.resources.flow_info_button_tooltip
import plugintoolkit.composeapp.generated.resources.flow_orthogonal_port_lead
import plugintoolkit.composeapp.generated.resources.flow_orthogonal_step_after
import plugintoolkit.composeapp.generated.resources.flow_orthogonal_step_auto
import plugintoolkit.composeapp.generated.resources.flow_orthogonal_step_before
import plugintoolkit.composeapp.generated.resources.flow_orthogonal_step_middle
import plugintoolkit.composeapp.generated.resources.flow_orthogonal_step_mode
import plugintoolkit.composeapp.generated.resources.flow_toolbar_add_group
import plugintoolkit.composeapp.generated.resources.flow_toolbar_add_label
import plugintoolkit.composeapp.generated.resources.flow_toolbar_advanced_connections
import plugintoolkit.composeapp.generated.resources.flow_toolbar_colors_in_flow
import plugintoolkit.composeapp.generated.resources.flow_toolbar_eyedropper
import plugintoolkit.composeapp.generated.resources.flow_toolbar_hide_connection_points
import plugintoolkit.composeapp.generated.resources.flow_toolbar_no_custom_colors
import plugintoolkit.composeapp.generated.resources.flow_toolbar_paint
import plugintoolkit.composeapp.generated.resources.flow_toolbar_palette_quick
import plugintoolkit.composeapp.generated.resources.flow_toolbar_pick_color
import plugintoolkit.composeapp.generated.resources.flow_toolbar_roundness
import plugintoolkit.composeapp.generated.resources.flow_toolbar_spline_style
import plugintoolkit.composeapp.generated.resources.flow_toolbar_wash
import kotlin.math.roundToInt

private const val DEFAULT_PAINT_COLOR = "#4CAF50"
private const val MAX_FLOW_SWATCHES = 8
private val QUICK_PALETTE_PRESET_COLORS = listOf(
    "#4CAF50", "#2196F3", "#FF9800", "#F44336",
    "#9C27B0", "#00BCD4", "#E91E63", "#607D8B"
)

/**
 * Material 3 Expressive floating toolbar for the Flow Editor canvas.
 *
 * Remains always expanded and bottom-centered on the canvas for 1-click desktop accessibility,
 * with vertically centered controls, shape-morphing tonal toggles, delayed tooltips, anchored
 * popovers, and a matching-height [ZoomControls] pill.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FlowFloatingAppBar(
    scale: Float,
    isReadOnly: Boolean,
    isPaintToolActive: Boolean,
    isWashToolActive: Boolean,
    isEyedropperActive: Boolean = false,
    isAdvancedConnectionMode: Boolean = false,
    hideConnectionPointsUnlessHovered: Boolean = false,
    activePaintColor: String?,
    colorsInFlow: List<String> = emptyList(),
    connectionStyle: ConnectionCurveStyle,
    connectionRoundness: Float,
    orthogonalStepMode: OrthogonalStepMode = OrthogonalStepMode.Auto,
    orthogonalPortLead: Boolean = false,
    onTogglePaintTool: () -> Unit,
    onToggleWashTool: () -> Unit,
    onToggleEyedropper: () -> Unit = {},
    onToggleAdvancedConnectionMode: () -> Unit = {},
    onToggleHideConnectionPoints: () -> Unit = {},
    onSelectPaintColor: (String) -> Unit,
    onAddGroup: () -> Unit,
    onAddLabel: () -> Unit,
    onChangeConnectionStyle: (ConnectionCurveStyle) -> Unit,
    onChangeConnectionRoundness: (Float) -> Unit,
    onChangeOrthogonalStepMode: (OrthogonalStepMode) -> Unit = {},
    onChangeOrthogonalPortLead: (Boolean) -> Unit = {},
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onExportImage: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dimensions = ToolkitTheme.dimensions
    val spacing = ToolkitTheme.spacing
    val shapes = ToolkitTheme.shapes

    var showColorPicker by remember { mutableStateOf(false) }
    var showQuickPalette by remember { mutableStateOf(false) }
    var showSplineSettings by remember { mutableStateOf(false) }
    var showControlsInfo by remember { mutableStateOf(false) }

    val abovePopoverPositionProvider = TooltipDefaults.rememberTooltipPositionProvider(
        positioning = TooltipAnchorPosition.Above,
        spacingBetweenTooltipAndAnchor = spacing.small
    )

    val currentColor = if (!activePaintColor.isNullOrBlank()) {
        parseColorString(activePaintColor)
    } else {
        parseColorString(DEFAULT_PAINT_COLOR)
    }

    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier
            .padding(spacing.medium)
            .testTag("flow_floating_app_bar"),
        shape = FloatingToolbarDefaults.ContainerShape,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
            toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        contentPadding = PaddingValues(
            horizontal = spacing.small,
            vertical = spacing.extraSmall
        ),
        expandedShadowElevation = dimensions.elevationMedium
    ) {
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides dimensions.desktopTouchTargetSize
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
            ) {
                if (!isReadOnly) {
                    // 1. Canvas authoring actions
                    ToolbarActionIconButton(
                        icon = Icons.Default.Layers,
                        tooltip = stringResource(Res.string.flow_toolbar_add_group),
                        onClick = onAddGroup,
                        testTag = "toolbar_add_group"
                    )

                    ToolbarActionIconButton(
                        icon = Icons.Default.TextFields,
                        tooltip = stringResource(Res.string.flow_toolbar_add_label),
                        onClick = onAddLabel,
                        testTag = "toolbar_add_label"
                    )

                    ToolbarDivider()

                    // 2. Drawing tools tonal cluster (Paint + Swatch + Eyedropper)
                    Surface(
                        shape = shapes.pill,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.xxs),
                            modifier = Modifier.padding(horizontal = spacing.xxs)
                        ) {
                            ToolbarToggleIconButton(
                                checked = isPaintToolActive,
                                onCheckedChange = { onTogglePaintTool() },
                                icon = Icons.Default.Palette,
                                tooltip = stringResource(Res.string.flow_toolbar_paint),
                                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                testTag = "toolbar_paint_tool"
                            )

                            Box(contentAlignment = Alignment.Center) {
                                val pickColorLabel = stringResource(Res.string.flow_toolbar_pick_color)
                                ToolkitTooltipBox(
                                    tooltip = pickColorLabel,
                                    position = ToolkitTooltipPosition.Above
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(dimensions.toolbarZoomHeight)
                                            .clip(CircleShape)
                                            .pointerHoverIcon(PointerIcon.Hand)
                                            .clickable(role = Role.Button) {
                                                showQuickPalette = !showQuickPalette
                                                showSplineSettings = false
                                                showControlsInfo = false
                                            }
                                            .semantics { contentDescription = pickColorLabel }
                                            .testTag("toolbar_color_swatch")
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(dimensions.toolbarColorSwatchSize)
                                                .clip(CircleShape)
                                                .background(currentColor)
                                                .border(
                                                    width = dimensions.borderThin,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    shape = CircleShape
                                                )
                                        )
                                    }
                                }

                                if (showQuickPalette) {
                                    Popup(
                                        popupPositionProvider = abovePopoverPositionProvider,
                                        onDismissRequest = { showQuickPalette = false },
                                        properties = PopupProperties(focusable = true)
                                    ) {
                                        QuickPaletteCard(
                                            colorsInFlow = colorsInFlow,
                                            onSelectColor = { hex ->
                                                onSelectPaintColor(hex)
                                                showQuickPalette = false
                                            },
                                            onOpenFullPicker = {
                                                showQuickPalette = false
                                                showColorPicker = true
                                            }
                                        )
                                    }
                                }
                            }

                            ToolbarToggleIconButton(
                                checked = isEyedropperActive,
                                onCheckedChange = { onToggleEyedropper() },
                                icon = Icons.Default.Colorize,
                                tooltip = stringResource(Res.string.flow_toolbar_eyedropper),
                                checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                testTag = "toolbar_eyedropper_tool"
                            )
                        }
                    }

                    // 3. Wash Brush tool
                    ToolbarToggleIconButton(
                        checked = isWashToolActive,
                        onCheckedChange = { onToggleWashTool() },
                        icon = Icons.Default.Refresh,
                        tooltip = stringResource(Res.string.flow_toolbar_wash),
                        checkedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        testTag = "toolbar_wash_tool"
                    )

                    ToolbarDivider()

                    // 4. Connection routing & display controls
                    Box(contentAlignment = Alignment.Center) {
                        ToolbarToggleIconButton(
                            checked = showSplineSettings,
                            onCheckedChange = { checked ->
                                showSplineSettings = checked
                                if (checked) {
                                    showQuickPalette = false
                                    showControlsInfo = false
                                }
                            },
                            icon = Icons.Default.Tune,
                            tooltip = stringResource(Res.string.flow_toolbar_spline_style),
                            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            testTag = "toolbar_spline_settings"
                        )

                        if (showSplineSettings) {
                            Popup(
                                popupPositionProvider = abovePopoverPositionProvider,
                                onDismissRequest = { showSplineSettings = false },
                                properties = PopupProperties(focusable = true)
                            ) {
                                SplineSettingsCard(
                                    connectionStyle = connectionStyle,
                                    connectionRoundness = connectionRoundness,
                                    orthogonalStepMode = orthogonalStepMode,
                                    orthogonalPortLead = orthogonalPortLead,
                                    onChangeConnectionStyle = onChangeConnectionStyle,
                                    onChangeConnectionRoundness = onChangeConnectionRoundness,
                                    onChangeOrthogonalStepMode = onChangeOrthogonalStepMode,
                                    onChangeOrthogonalPortLead = onChangeOrthogonalPortLead
                                )
                            }
                        }
                    }

                    ToolbarToggleIconButton(
                        checked = isAdvancedConnectionMode,
                        onCheckedChange = { onToggleAdvancedConnectionMode() },
                        icon = Icons.Default.Hub,
                        tooltip = stringResource(Res.string.flow_toolbar_advanced_connections),
                        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        testTag = "toolbar_advanced_connection_mode"
                    )

                    ToolbarToggleIconButton(
                        checked = hideConnectionPointsUnlessHovered,
                        onCheckedChange = { onToggleHideConnectionPoints() },
                        icon = if (hideConnectionPointsUnlessHovered) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        tooltip = stringResource(Res.string.flow_toolbar_hide_connection_points),
                        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        testTag = "toolbar_hide_connection_points"
                    )

                    ToolbarDivider()
                }

                // 5. Controls Info popover toggle
                Box(contentAlignment = Alignment.Center) {
                    ToolbarToggleIconButton(
                        checked = showControlsInfo,
                        onCheckedChange = { checked ->
                            showControlsInfo = checked
                            if (checked) {
                                showQuickPalette = false
                                showSplineSettings = false
                            }
                        },
                        icon = Icons.Default.Info,
                        tooltip = stringResource(Res.string.flow_info_button_tooltip),
                        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        testTag = "flow_controls_info_button"
                    )

                    if (showControlsInfo) {
                        Popup(
                            popupPositionProvider = abovePopoverPositionProvider,
                            onDismissRequest = { showControlsInfo = false },
                            properties = PopupProperties(focusable = true)
                        ) {
                            FlowControlsInfoCard()
                        }
                    }
                }

                // 6. Export Image action
                if (onExportImage != null) {
                    ToolbarActionIconButton(
                        icon = Icons.Default.Image,
                        tooltip = stringResource(Res.string.flow_export_image_tooltip),
                        contentDescription = stringResource(Res.string.flow_export_image_title),
                        onClick = onExportImage,
                        testTag = "toolbar_export_image"
                    )
                }

                // 7. Zoom Controls pill
                ZoomControls(
                    scale = scale,
                    onZoomIn = onZoomIn,
                    onZoomOut = onZoomOut,
                    modifier = Modifier.testTag("zoom_controls")
                )
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            show = showColorPicker,
            onDismissRequest = { showColorPicker = false },
            onPickedColor = { pickedColor ->
                val hex = pickedColor.toHex(hexPrefix = true, includeAlpha = false)
                onSelectPaintColor(hex)
                showColorPicker = false
            }
        )
    }
}

@Composable
private fun ToolbarActionIconButton(
    icon: ImageVector,
    tooltip: String,
    onClick: () -> Unit,
    testTag: String,
    contentDescription: String = tooltip
) {
    val dimensions = ToolkitTheme.dimensions
    ToolkitTooltipBox(tooltip = tooltip, position = ToolkitTooltipPosition.Above) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(dimensions.toolbarZoomHeight)
                .pointerHoverIcon(PointerIcon.Hand)
                .testTag(testTag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(dimensions.iconSmall)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToolbarToggleIconButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector,
    tooltip: String,
    checkedContainerColor: Color,
    checkedContentColor: Color,
    testTag: String
) {
    val dimensions = ToolkitTheme.dimensions
    ToolkitTooltipBox(tooltip = tooltip, position = ToolkitTooltipPosition.Above) {
        FilledTonalIconToggleButton(
            checked = checked,
            onCheckedChange = onCheckedChange,
            shapes = IconButtonDefaults.toggleableShapes(),
            colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = checkedContainerColor,
                checkedContentColor = checkedContentColor
            ),
            modifier = Modifier
                .size(dimensions.toolbarZoomHeight)
                .pointerHoverIcon(PointerIcon.Hand)
                .testTag(testTag)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = tooltip,
                modifier = Modifier.size(dimensions.iconSmall)
            )
        }
    }
}

@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .width(ToolkitTheme.dimensions.borderThin)
            .height(ToolkitTheme.dimensions.iconMedium)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider))
    )
}

@Composable
private fun QuickPaletteCard(
    colorsInFlow: List<String>,
    onSelectColor: (String) -> Unit,
    onOpenFullPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = ToolkitTheme.spacing
    val shapes = ToolkitTheme.shapes
    val dimensions = ToolkitTheme.dimensions

    Surface(
        shape = shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = dimensions.elevationHigh,
        border = BorderStroke(dimensions.borderThin, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .width(dimensions.toolbarQuickPaletteWidth)
            .testTag("quick_palette_card")
    ) {
        Column(
            modifier = Modifier.padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Text(
                text = stringResource(Res.string.flow_toolbar_colors_in_flow),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (colorsInFlow.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    colorsInFlow.take(MAX_FLOW_SWATCHES).forEach { hex ->
                        PaletteSwatchDot(hex = hex, onClick = { onSelectColor(hex) })
                    }
                }
            } else {
                Text(
                    text = stringResource(Res.string.flow_toolbar_no_custom_colors),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = stringResource(Res.string.flow_toolbar_palette_quick),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall),
                modifier = Modifier.fillMaxWidth()
            ) {
                QUICK_PALETTE_PRESET_COLORS.forEach { hex ->
                    PaletteSwatchDot(hex = hex, onClick = { onSelectColor(hex) })
                }
            }

            TextButton(
                onClick = onOpenFullPicker,
                modifier = Modifier
                    .align(Alignment.End)
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                Text(
                    text = stringResource(Res.string.flow_toolbar_pick_color),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun PaletteSwatchDot(
    hex: String,
    onClick: () -> Unit
) {
    val dimensions = ToolkitTheme.dimensions
    Box(
        modifier = Modifier
            .size(dimensions.toolbarColorSwatchSize)
            .clip(CircleShape)
            .background(parseColorString(hex))
            .border(dimensions.borderThin, MaterialTheme.colorScheme.outline, CircleShape)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(role = Role.Button, onClick = onClick)
    )
}

@Composable
private fun SplineSettingsCard(
    connectionStyle: ConnectionCurveStyle,
    connectionRoundness: Float,
    orthogonalStepMode: OrthogonalStepMode = OrthogonalStepMode.Auto,
    orthogonalPortLead: Boolean = false,
    onChangeConnectionStyle: (ConnectionCurveStyle) -> Unit,
    onChangeConnectionRoundness: (Float) -> Unit,
    onChangeOrthogonalStepMode: (OrthogonalStepMode) -> Unit = {},
    onChangeOrthogonalPortLead: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val spacing = ToolkitTheme.spacing
    val shapes = ToolkitTheme.shapes
    val dimensions = ToolkitTheme.dimensions

    val styleOptions = listOf(
        ToolkitToggleOption(
            key = ConnectionCurveStyle.CardinalSpline.name,
            label = stringResource(Res.string.flow_connection_style_cardinal)
        ),
        ToolkitToggleOption(
            key = ConnectionCurveStyle.Straight.name,
            label = stringResource(Res.string.flow_connection_style_straight)
        ),
        ToolkitToggleOption(
            key = ConnectionCurveStyle.Orthogonal.name,
            label = stringResource(Res.string.flow_connection_style_orthogonal)
        )
    )
    val stepModeOptions = listOf(
        ToolkitToggleOption(
            key = OrthogonalStepMode.Auto.name,
            label = stringResource(Res.string.flow_orthogonal_step_auto)
        ),
        ToolkitToggleOption(
            key = OrthogonalStepMode.Middle.name,
            label = stringResource(Res.string.flow_orthogonal_step_middle)
        ),
        ToolkitToggleOption(
            key = OrthogonalStepMode.Before.name,
            label = stringResource(Res.string.flow_orthogonal_step_before)
        ),
        ToolkitToggleOption(
            key = OrthogonalStepMode.After.name,
            label = stringResource(Res.string.flow_orthogonal_step_after)
        )
    )

    Surface(
        shape = shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = dimensions.elevationHigh,
        border = BorderStroke(dimensions.borderUnselected, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .widthIn(min = dimensions.toolbarPopoverWidth)
            .testTag("spline_settings_card")
    ) {
        Column(
            modifier = Modifier.padding(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.small)
        ) {
            Text(
                text = stringResource(Res.string.flow_toolbar_spline_style),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            ToolkitConnectedToggleGroup(
                options = styleOptions,
                selectedKey = connectionStyle.name,
                onSelect = { key ->
                    ConnectionCurveStyle.entries.find { it.name == key }?.let(onChangeConnectionStyle)
                }
            )

            if (connectionStyle == ConnectionCurveStyle.Orthogonal) {
                Spacer(modifier = Modifier.height(spacing.extraSmall))
                Text(
                    text = stringResource(Res.string.flow_orthogonal_step_mode),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                ToolkitConnectedToggleGroup(
                    options = stepModeOptions,
                    selectedKey = orthogonalStepMode.name,
                    onSelect = { key ->
                        OrthogonalStepMode.entries.find { it.name == key }?.let(onChangeOrthogonalStepMode)
                    }
                )

                if (orthogonalStepMode == OrthogonalStepMode.Auto) {
                    Spacer(modifier = Modifier.height(spacing.extraSmall))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { onChangeOrthogonalPortLead(!orthogonalPortLead) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(Res.string.flow_orthogonal_port_lead),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = orthogonalPortLead,
                            onCheckedChange = onChangeOrthogonalPortLead,
                            modifier = Modifier.testTag("orthogonal_port_lead_switch")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.extraSmall))

            Text(
                text = "${stringResource(Res.string.flow_toolbar_roundness)}: ${(connectionRoundness * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Slider(
                value = connectionRoundness,
                onValueChange = onChangeConnectionRoundness,
                valueRange = 0f..1f,
                modifier = Modifier.testTag("spline_roundness_slider")
            )
        }
    }
}
