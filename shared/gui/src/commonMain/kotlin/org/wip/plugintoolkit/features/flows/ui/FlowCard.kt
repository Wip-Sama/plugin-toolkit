package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.shared.components.ToolkitButtonGroup
import org.wip.plugintoolkit.shared.components.ToolkitCard
import org.wip.plugintoolkit.shared.components.ToolkitChip
import org.wip.plugintoolkit.shared.components.ToolkitChipStyle
import org.wip.plugintoolkit.shared.components.ToolkitTooltipBox
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_copy_flow_clipboard
import plugintoolkit.composeapp.generated.resources.action_copy_flow_clipboard_tooltip
import plugintoolkit.composeapp.generated.resources.action_delete
import plugintoolkit.composeapp.generated.resources.action_edit
import plugintoolkit.composeapp.generated.resources.action_edit_metadata
import plugintoolkit.composeapp.generated.resources.action_export_flow
import plugintoolkit.composeapp.generated.resources.action_export_flow_tooltip
import plugintoolkit.composeapp.generated.resources.flow_broken_tag
import plugintoolkit.composeapp.generated.resources.flow_editor_read_only
import plugintoolkit.composeapp.generated.resources.flow_editor_read_only_reason
import plugintoolkit.composeapp.generated.resources.flow_missing_chip
import plugintoolkit.composeapp.generated.resources.flow_nodes_count
import plugintoolkit.composeapp.generated.resources.flow_not_ready_node_chip
import plugintoolkit.composeapp.generated.resources.flow_readonly_reason_running
import plugintoolkit.composeapp.generated.resources.flow_readonly_reason_used_in_other
import plugintoolkit.composeapp.generated.resources.flow_status_paused
import plugintoolkit.composeapp.generated.resources.flow_used_in_chip
import plugintoolkit.composeapp.generated.resources.flow_used_in_parents

@Composable
internal fun FlowItem(
    name: String,
    nodeCount: Int,
    parentFlows: List<String>,
    missingCapabilities: List<String>,
    notReadyNodes: List<Node>,
    isRunning: Boolean,
    isPaused: Boolean = false,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEditMetadata: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onShareClipboard: () -> Unit
) {
    val isBroken = missingCapabilities.isNotEmpty() || notReadyNodes.isNotEmpty()

    ToolkitCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stackVertically = maxWidth < ToolkitTheme.dimensions.breakpointCompact

            val actionsContent: @Composable () -> Unit = {
                ToolkitButtonGroup {
                    item { shape, modifierSpec ->
                        ToolkitTooltipBox(
                            tooltip = stringResource(Res.string.action_export_flow_tooltip),
                            modifier = modifierSpec
                        ) {
                            FilledTonalIconButton(
                                onClick = onExport,
                                shape = shape,
                                modifier = Modifier.size(ToolkitTheme.dimensions.menuItem)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = stringResource(Res.string.action_export_flow),
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                                )
                            }
                        }
                    }
                    item { shape, modifierSpec ->
                        ToolkitTooltipBox(
                            tooltip = stringResource(Res.string.action_copy_flow_clipboard_tooltip),
                            modifier = modifierSpec
                        ) {
                            FilledTonalIconButton(
                                onClick = onShareClipboard,
                                shape = shape,
                                modifier = Modifier.size(ToolkitTheme.dimensions.menuItem)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(Res.string.action_copy_flow_clipboard),
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                                )
                            }
                        }
                    }
                    item { shape, modifierSpec ->
                        val metadataLabel = stringResource(Res.string.action_edit_metadata)
                        ToolkitTooltipBox(
                            tooltip = metadataLabel,
                            modifier = modifierSpec
                        ) {
                            FilledTonalIconButton(
                                onClick = onEditMetadata,
                                shape = shape,
                                modifier = Modifier.size(ToolkitTheme.dimensions.menuItem)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = metadataLabel,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                                )
                            }
                        }
                    }
                    item { shape, modifierSpec ->
                        val editLabel = stringResource(Res.string.action_edit)
                        ToolkitTooltipBox(
                            tooltip = editLabel,
                            modifier = modifierSpec
                        ) {
                            FilledTonalIconButton(
                                onClick = onEdit,
                                shape = shape,
                                modifier = Modifier.size(ToolkitTheme.dimensions.menuItem)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = editLabel,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                                )
                            }
                        }
                    }
                    item { shape, modifierSpec ->
                        val deleteLabel = stringResource(Res.string.action_delete)
                        ToolkitTooltipBox(
                            tooltip = deleteLabel,
                            modifier = modifierSpec
                        ) {
                            FilledTonalIconButton(
                                onClick = onDelete,
                                shape = shape,
                                modifier = Modifier.size(ToolkitTheme.dimensions.menuItem)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = deleteLabel,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                                )
                            }
                        }
                    }
                }
            }

            if (stackVertically) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                ) {
                    FlowCardInfoColumn(
                        name = name,
                        nodeCount = nodeCount,
                        parentFlows = parentFlows,
                        missingCapabilities = missingCapabilities,
                        notReadyNodes = notReadyNodes,
                        isBroken = isBroken,
                        isRunning = isRunning,
                        isPaused = isPaused,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        actionsContent()
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FlowCardInfoColumn(
                        name = name,
                        nodeCount = nodeCount,
                        parentFlows = parentFlows,
                        missingCapabilities = missingCapabilities,
                        notReadyNodes = notReadyNodes,
                        isBroken = isBroken,
                        isRunning = isRunning,
                        isPaused = isPaused,
                        modifier = Modifier.weight(1f)
                    )
                    actionsContent()
                }
            }
        }
    }
}

@Composable
private fun FlowCardInfoColumn(
    name: String,
    nodeCount: Int,
    parentFlows: List<String>,
    missingCapabilities: List<String>,
    notReadyNodes: List<Node>,
    isBroken: Boolean,
    isRunning: Boolean,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
    ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (isBroken) {
                        ToolkitChip(
                            text = stringResource(Res.string.flow_broken_tag),
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                )
                            },
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            style = ToolkitChipStyle.Filled,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isRunning) {
                        ToolkitChip(
                            text = stringResource(Res.string.flow_readonly_reason_running),
                            icon = {
                                Box(
                                    modifier = Modifier
                                        .size(ToolkitTheme.spacing.extraSmall)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        )
                            },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = ToolkitChipStyle.Filled,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isPaused) {
                        ToolkitChip(
                            text = stringResource(Res.string.flow_status_paused),
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                )
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            style = ToolkitChipStyle.Filled,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (isRunning || parentFlows.isNotEmpty()) {
                        val reasons = mutableListOf<String>()
                        if (isRunning) reasons.add(stringResource(Res.string.flow_readonly_reason_running))
                        if (parentFlows.isNotEmpty()) reasons.add(stringResource(Res.string.flow_readonly_reason_used_in_other))

                        val displayText = if (reasons.isNotEmpty()) {
                            stringResource(Res.string.flow_editor_read_only_reason, reasons.joinToString(", "))
                        } else {
                            stringResource(Res.string.flow_editor_read_only)
                        }

                        ToolkitChip(
                            text = displayText,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            style = ToolkitChipStyle.Filled,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(Res.string.flow_nodes_count, nodeCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (parentFlows.isNotEmpty()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = ToolkitTheme.opacity.divider)
                        )
                        Text(
                            text = stringResource(Res.string.flow_used_in_parents, parentFlows.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Chips Flow Row (parent flows and missing capabilities)
                if (parentFlows.isNotEmpty() || missingCapabilities.isNotEmpty() || notReadyNodes.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = ToolkitTheme.spacing.extraSmall)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        parentFlows.forEach { parentName ->
                            ToolkitChip(
                                text = stringResource(Res.string.flow_used_in_chip, parentName),
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = ToolkitChipStyle.Tinted,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        missingCapabilities.forEach { capName ->
                            ToolkitChip(
                                text = stringResource(Res.string.flow_missing_chip, capName),
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.error,
                                style = ToolkitChipStyle.Outlined,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        notReadyNodes.forEach { node ->
                            ToolkitChip(
                                text = stringResource(Res.string.flow_not_ready_node_chip, node.title),
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.error,
                                style = ToolkitChipStyle.Outlined,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
