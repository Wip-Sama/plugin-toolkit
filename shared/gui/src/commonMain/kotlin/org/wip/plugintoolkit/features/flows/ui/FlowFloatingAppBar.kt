package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.colorpicker.ui.ColorPickerDialog
import org.wip.plugintoolkit.features.colorpicker.utils.toHex
import org.wip.plugintoolkit.features.flows.ui.components.FlowControlsInfoCard
import org.wip.plugintoolkit.features.settings.model.ConnectionCurveStyle
import org.wip.plugintoolkit.shared.components.LocalTooltipState
import org.wip.plugintoolkit.shared.components.ZoomControls
import org.wip.plugintoolkit.shared.components.plugin.inputs.parseColorString
import org.wip.plugintoolkit.shared.components.tooltip
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.flow_connection_style_bezier
import plugintoolkit.composeapp.generated.resources.flow_connection_style_cardinal
import plugintoolkit.composeapp.generated.resources.flow_connection_style_orthogonal
import plugintoolkit.composeapp.generated.resources.flow_connection_style_straight
import plugintoolkit.composeapp.generated.resources.flow_info_button_tooltip
import plugintoolkit.composeapp.generated.resources.flow_toolbar_add_group
import plugintoolkit.composeapp.generated.resources.flow_toolbar_add_label
import plugintoolkit.composeapp.generated.resources.flow_toolbar_advanced_connections
import plugintoolkit.composeapp.generated.resources.flow_toolbar_colors_in_flow
import plugintoolkit.composeapp.generated.resources.flow_toolbar_eyedropper
import plugintoolkit.composeapp.generated.resources.flow_toolbar_paint
import plugintoolkit.composeapp.generated.resources.flow_toolbar_palette_quick
import plugintoolkit.composeapp.generated.resources.flow_toolbar_pick_color
import plugintoolkit.composeapp.generated.resources.flow_toolbar_roundness
import plugintoolkit.composeapp.generated.resources.flow_toolbar_spline_style
import plugintoolkit.composeapp.generated.resources.flow_toolbar_wash
import kotlin.math.roundToInt

private const val DEFAULT_PAINT_COLOR = "#4CAF50"

/**
 * Material 3 Floating App Bar (Toolbar) for the Flow Editor canvas.
 * Consolidates Add Group, Add Label, Paint Tool, Wash Brush, Eyedropper, Spline Settings, Info, and Zoom Controls.
 */
@Composable
fun FlowFloatingAppBar(
    scale: Float,
    isReadOnly: Boolean,
    isPaintToolActive: Boolean,
    isWashToolActive: Boolean,
    isEyedropperActive: Boolean = false,
    isAdvancedConnectionMode: Boolean = false,
    activePaintColor: String?,
    colorsInFlow: List<String> = emptyList(),
    connectionStyle: ConnectionCurveStyle,
    connectionRoundness: Float,
    onTogglePaintTool: () -> Unit,
    onToggleWashTool: () -> Unit,
    onToggleEyedropper: () -> Unit = {},
    onToggleAdvancedConnectionMode: () -> Unit = {},
    onSelectPaintColor: (String) -> Unit,
    onAddGroup: () -> Unit,
    onAddLabel: () -> Unit,
    onChangeConnectionStyle: (ConnectionCurveStyle) -> Unit,
    onChangeConnectionRoundness: (Float) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = ToolkitTheme.dimensions
    val spacing = ToolkitTheme.spacing
    val shapes = ToolkitTheme.shapes

    var showColorPicker by remember { mutableStateOf(false) }
    var showSplineSettings by remember { mutableStateOf(false) }

    val currentColor = if (!activePaintColor.isNullOrBlank()) {
        parseColorString(activePaintColor)
    } else {
        parseColorString(DEFAULT_PAINT_COLOR)
    }

    Surface(
        modifier = modifier
            .padding(spacing.medium)
            .testTag("flow_floating_app_bar"),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = dimensions.elevationMedium,
        shadowElevation = dimensions.elevationMedium,
        border = BorderStroke(
            width = dimensions.borderUnselected,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.small, vertical = spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
        ) {
            if (!isReadOnly) {
                // 1. Add Group button
                IconButton(
                    onClick = onAddGroup,
                    modifier = Modifier
                        .size(dimensions.standardButtonHeight)
                        .testTag("toolbar_add_group")
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = stringResource(Res.string.flow_toolbar_add_group),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(dimensions.iconSmall)
                    )
                }

                // 2. Add Label button
                IconButton(
                    onClick = onAddLabel,
                    modifier = Modifier
                        .size(dimensions.standardButtonHeight)
                        .testTag("toolbar_add_label")
                ) {
                    Icon(
                        imageVector = Icons.Default.TextFields,
                        contentDescription = stringResource(Res.string.flow_toolbar_add_label),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(dimensions.iconSmall)
                    )
                }

                ToolbarDivider()

                // 3. Paint Tool toggle, Color Swatch & Eyedropper
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    IconButton(
                        onClick = onTogglePaintTool,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isPaintToolActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            contentColor = if (isPaintToolActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .size(dimensions.standardButtonHeight)
                            .testTag("toolbar_paint_tool")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = stringResource(Res.string.flow_toolbar_paint),
                            modifier = Modifier.size(dimensions.iconSmall)
                        )
                    }

                    // Color swatch indicator with hover/click Quick Palette popup
                    val tooltipState = LocalTooltipState.current
                    var colorSwatchCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                    val swatchTooltipKey = remember { Any() }

                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .onGloballyPositioned { colorSwatchCoordinates = it }
                            .clickable {
                                colorSwatchCoordinates?.let { coords ->
                                    tooltipState?.toggle(coords, swatchTooltipKey) {
                                        QuickPaletteCard(
                                            colorsInFlow = colorsInFlow,
                                            onSelectColor = { hex ->
                                                onSelectPaintColor(hex)
                                                tooltipState.dismiss()
                                            },
                                            onOpenFullPicker = {
                                                tooltipState.dismiss()
                                                showColorPicker = true
                                            }
                                        )
                                    }
                                }
                            }
                            .tooltip {
                                QuickPaletteCard(
                                    colorsInFlow = colorsInFlow,
                                    onSelectColor = { hex ->
                                        onSelectPaintColor(hex)
                                        tooltipState?.dismiss()
                                    },
                                    onOpenFullPicker = {
                                        tooltipState?.dismiss()
                                        showColorPicker = true
                                    }
                                )
                            }
                            .testTag("toolbar_color_swatch")
                    )

                    // Eyedropper Tool button
                    IconButton(
                        onClick = onToggleEyedropper,
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isEyedropperActive) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            contentColor = if (isEyedropperActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .size(dimensions.standardButtonHeight)
                            .testTag("toolbar_eyedropper_tool")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Colorize,
                            contentDescription = stringResource(Res.string.flow_toolbar_eyedropper),
                            modifier = Modifier.size(dimensions.iconSmall)
                        )
                    }
                }

                // 4. Wash Brush tool
                IconButton(
                    onClick = onToggleWashTool,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isWashToolActive) MaterialTheme.colorScheme.tertiaryContainer else Color.Transparent,
                        contentColor = if (isWashToolActive) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .size(dimensions.standardButtonHeight)
                        .testTag("toolbar_wash_tool")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(Res.string.flow_toolbar_wash),
                        modifier = Modifier.size(dimensions.iconSmall)
                    )
                }

                ToolbarDivider()

                // 5. Spline & Roundness settings button
                Box {
                    IconButton(
                        onClick = { showSplineSettings = !showSplineSettings },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (showSplineSettings) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            contentColor = if (showSplineSettings) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .size(dimensions.standardButtonHeight)
                            .testTag("toolbar_spline_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = stringResource(Res.string.flow_toolbar_spline_style),
                            modifier = Modifier.size(dimensions.iconSmall)
                        )
                    }

                    if (showSplineSettings) {
                        Popup(
                            alignment = Alignment.TopCenter,
                            offset = IntOffset(0, -260),
                            onDismissRequest = { showSplineSettings = false },
                            properties = PopupProperties(focusable = true)
                        ) {
                            SplineSettingsCard(
                                connectionStyle = connectionStyle,
                                connectionRoundness = connectionRoundness,
                                onChangeConnectionStyle = onChangeConnectionStyle,
                                onChangeConnectionRoundness = onChangeConnectionRoundness
                            )
                        }
                    }
                }

                // 5.1 Advanced Connection Mode toggle
                IconButton(
                    onClick = onToggleAdvancedConnectionMode,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isAdvancedConnectionMode) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        contentColor = if (isAdvancedConnectionMode) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier
                        .size(dimensions.standardButtonHeight)
                        .testTag("toolbar_advanced_connection_mode")
                ) {
                    Icon(
                        imageVector = Icons.Default.Hub,
                        contentDescription = stringResource(Res.string.flow_toolbar_advanced_connections),
                        modifier = Modifier.size(dimensions.iconSmall)
                    )
                }

                ToolbarDivider()
            }

            // 6. Info Button
            val tooltipState = LocalTooltipState.current
            var infoCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
            val infoTooltipKey = remember { Any() }

            Surface(
                onClick = {
                    infoCoordinates?.let { coords ->
                        tooltipState?.toggle(coords, infoTooltipKey) {
                            FlowControlsInfoCard()
                        }
                    }
                },
                shape = CircleShape,
                color = Color.Transparent,
                modifier = Modifier
                    .size(dimensions.standardButtonHeight)
                    .onGloballyPositioned { infoCoordinates = it }
                    .tooltip { FlowControlsInfoCard() }
                    .testTag("flow_controls_info_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = stringResource(Res.string.flow_info_button_tooltip),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(dimensions.iconSmall)
                    )
                }
            }

            // 7. Zoom Controls
            ZoomControls(
                scale = scale,
                onZoomIn = onZoomIn,
                onZoomOut = onZoomOut,
                modifier = Modifier.testTag("zoom_controls")
            )
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
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(24.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
    val presetColors = listOf("#4CAF50", "#2196F3", "#FF9800", "#F44336", "#9C27B0", "#00BCD4", "#E91E63", "#607D8B")

    Surface(
        shape = shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = ToolkitTheme.dimensions.elevationHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .width(240.dp)
            .padding(spacing.small)
            .testTag("quick_palette_card")
    ) {
        Column(
            modifier = Modifier.padding(spacing.small),
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
                    colorsInFlow.take(8).forEach { hex ->
                        val c = parseColorString(hex)
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .clickable { onSelectColor(hex) }
                        )
                    }
                }
            } else {
                Text(
                    text = "No custom colors in flow yet",
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
                presetColors.forEach { hex ->
                    val c = parseColorString(hex)
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { onSelectColor(hex) }
                    )
                }
            }

            TextButton(
                onClick = onOpenFullPicker,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(Res.string.flow_toolbar_pick_color), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun SplineSettingsCard(
    connectionStyle: ConnectionCurveStyle,
    connectionRoundness: Float,
    onChangeConnectionStyle: (ConnectionCurveStyle) -> Unit,
    onChangeConnectionRoundness: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = ToolkitTheme.spacing
    val shapes = ToolkitTheme.shapes

    Surface(
        shape = shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = ToolkitTheme.dimensions.elevationHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .width(260.dp)
            .padding(spacing.small)
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

            // Connection style chips
            Row(
                modifier = Modifier.padding(vertical = spacing.extraSmall),
                horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
            ) {
                FilterChip(
                    selected = connectionStyle == ConnectionCurveStyle.CardinalSpline,
                    onClick = { onChangeConnectionStyle(ConnectionCurveStyle.CardinalSpline) },
                    label = { Text(stringResource(Res.string.flow_connection_style_cardinal), style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = connectionStyle == ConnectionCurveStyle.Bezier,
                    onClick = { onChangeConnectionStyle(ConnectionCurveStyle.Bezier) },
                    label = { Text(stringResource(Res.string.flow_connection_style_bezier), style = MaterialTheme.typography.labelSmall) }
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
            ) {
                FilterChip(
                    selected = connectionStyle == ConnectionCurveStyle.Straight,
                    onClick = { onChangeConnectionStyle(ConnectionCurveStyle.Straight) },
                    label = { Text(stringResource(Res.string.flow_connection_style_straight), style = MaterialTheme.typography.labelSmall) }
                )
                FilterChip(
                    selected = connectionStyle == ConnectionCurveStyle.Orthogonal,
                    onClick = { onChangeConnectionStyle(ConnectionCurveStyle.Orthogonal) },
                    label = { Text(stringResource(Res.string.flow_connection_style_orthogonal), style = MaterialTheme.typography.labelSmall) }
                )
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

