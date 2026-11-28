package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.viewmodel.ReadOnlyReason
import org.wip.plugintoolkit.shared.components.ToolkitTooltipBox
import org.wip.plugintoolkit.shared.components.ToolkitTooltipPosition
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.flow_editor_btn_exit
import plugintoolkit.composeapp.generated.resources.flow_editor_flow_selected
import plugintoolkit.composeapp.generated.resources.flow_editor_no_flow_selected
import plugintoolkit.composeapp.generated.resources.flow_editor_read_only
import plugintoolkit.composeapp.generated.resources.flow_editor_read_only_reason
import plugintoolkit.composeapp.generated.resources.flow_editor_refresh_broken_nodes
import plugintoolkit.composeapp.generated.resources.flow_editor_save_changes
import plugintoolkit.composeapp.generated.resources.flow_export_image_tooltip
import plugintoolkit.composeapp.generated.resources.flow_readonly_reason_running
import plugintoolkit.composeapp.generated.resources.flow_readonly_reason_used_in_other

/**
 * Material 3 Expressive top floating toolbar for the Flow Editor canvas.
 *
 * Visually matches [FlowFloatingAppBar] with a full capsule [HorizontalFloatingToolbar],
 * `surfaceContainerHigh` container, vertically centered compact desktop controls, and
 * `Alignment.TopCenter` placement on the canvas.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FlowEditorTopBar(
    flowName: String,
    isReadOnly: Boolean,
    readOnlyReasons: List<ReadOnlyReason>,
    hasUnsavedChanges: Boolean,
    hasBrokenNodes: Boolean = false,
    onRefreshBrokenNodes: (() -> Unit)? = null,
    onExportImage: (() -> Unit)? = null,
    onSave: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = ToolkitTheme.dimensions
    val spacing = ToolkitTheme.spacing

    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier
            .padding(spacing.medium)
            .testTag("flow_editor_top_bar"),
        shape = FloatingToolbarDefaults.ContainerShape,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(
            toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        contentPadding = PaddingValues(
            horizontal = spacing.medium,
            vertical = spacing.extraSmall
        ),
        expandedShadowElevation = dimensions.elevationMedium
    ) {
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides dimensions.desktopTouchTargetSize
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    if (flowName.isBlank()) {
                        Text(
                            text = stringResource(Res.string.flow_editor_no_flow_selected),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(spacing.small)
                        ) {
                            Column {
                                Text(
                                    text = stringResource(Res.string.flow_editor_flow_selected),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                                Text(
                                    text = flowName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isReadOnly) {
                                @Suppress("SimplifiableCallChain")
                                val reasonString = readOnlyReasons.map { reason ->
                                    when (reason) {
                                        ReadOnlyReason.Running -> stringResource(Res.string.flow_readonly_reason_running)
                                        ReadOnlyReason.UsedInOtherFlows -> stringResource(Res.string.flow_readonly_reason_used_in_other)
                                    }
                                }.joinToString(", ")

                                val displayText = if (reasonString.isNotEmpty()) {
                                    stringResource(Res.string.flow_editor_read_only_reason, reasonString)
                                } else {
                                    stringResource(Res.string.flow_editor_read_only)
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = displayText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(
                                            horizontal = spacing.small,
                                            vertical = spacing.extraSmall
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .width(dimensions.borderThin)
                        .height(dimensions.iconMedium)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider))
                )

                if (hasBrokenNodes && !isReadOnly && onRefreshBrokenNodes != null) {
                    OutlinedButton(
                        onClick = onRefreshBrokenNodes,
                        contentPadding = PaddingValues(
                            horizontal = spacing.mediumSmall,
                            vertical = spacing.extraSmall
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .height(dimensions.toolbarZoomHeight)
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(dimensions.iconSmall)
                        )
                        Spacer(modifier = Modifier.width(spacing.extraSmall))
                        Text(
                            text = stringResource(Res.string.flow_editor_refresh_broken_nodes),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }

                Button(
                    onClick = onSave,
                    enabled = hasUnsavedChanges && !isReadOnly,
                    contentPadding = PaddingValues(
                        horizontal = spacing.medium,
                        vertical = spacing.extraSmall
                    ),
                    modifier = Modifier
                        .height(dimensions.toolbarZoomHeight)
                        .pointerHoverIcon(if (hasUnsavedChanges && !isReadOnly) PointerIcon.Hand else PointerIcon.Default)
                ) {
                    Text(
                        text = stringResource(Res.string.flow_editor_save_changes),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                if (onExportImage != null) {
                    val exportTooltip = stringResource(Res.string.flow_export_image_tooltip)
                    ToolkitTooltipBox(
                        tooltip = exportTooltip,
                        position = ToolkitTooltipPosition.Below
                    ) {
                        IconButton(
                            onClick = onExportImage,
                            modifier = Modifier
                                .size(dimensions.toolbarZoomHeight)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .testTag("topbar_export_image")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = exportTooltip,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(dimensions.iconSmall)
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onExit,
                    contentPadding = PaddingValues(
                        horizontal = spacing.medium,
                        vertical = spacing.extraSmall
                    ),
                    modifier = Modifier
                        .height(dimensions.toolbarZoomHeight)
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(
                        text = stringResource(Res.string.flow_editor_btn_exit),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}
