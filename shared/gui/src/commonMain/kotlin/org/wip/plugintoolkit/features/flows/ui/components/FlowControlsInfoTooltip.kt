package org.wip.plugintoolkit.features.flows.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.model.resolve
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutActionId
import org.wip.plugintoolkit.features.shortcuts.ui.LocalShortcutManager
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.flow_info_cat_connections
import plugintoolkit.composeapp.generated.resources.flow_info_cat_navigation
import plugintoolkit.composeapp.generated.resources.flow_info_cat_selection
import plugintoolkit.composeapp.generated.resources.flow_info_cat_tools
import plugintoolkit.composeapp.generated.resources.flow_info_title

import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width

@Composable
fun FlowControlsInfoCard(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(ToolkitTheme.dimensions.flowControlsTooltipWidth)
            .shadow(ToolkitTheme.dimensions.elevationHigh, ToolkitTheme.shapes.medium),
        shape = ToolkitTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            width = ToolkitTheme.dimensions.borderThin,
            color = MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(ToolkitTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                )
                Text(
                    text = stringResource(Res.string.flow_info_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            HorizontalDivider(
                thickness = ToolkitTheme.dimensions.borderThin,
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Balanced 2-column layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
            ) {
                // Column 1: Navigation & Board + Tools
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
                ) {
                    InfoCategorySection(
                        title = stringResource(Res.string.flow_info_cat_navigation),
                        actionIds = listOf(
                            ShortcutActionId.FLOW_PAN_CANVAS,
                            ShortcutActionId.FLOW_ZOOM_CANVAS,
                            ShortcutActionId.FLOW_UNDO,
                            ShortcutActionId.FLOW_REDO
                        )
                    )

                    HorizontalDivider(
                        thickness = ToolkitTheme.dimensions.borderThin,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    InfoCategorySection(
                        title = stringResource(Res.string.flow_info_cat_tools),
                        actionIds = listOf(
                            ShortcutActionId.FLOW_PAINT_TOOL,
                            ShortcutActionId.FLOW_WASH_TOOL,
                            ShortcutActionId.FLOW_EYEDROPPER
                        )
                    )
                }

                VerticalDivider(
                    thickness = ToolkitTheme.dimensions.borderThin,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Column 2: Connections & Points + Selection
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
                ) {
                    InfoCategorySection(
                        title = stringResource(Res.string.flow_info_cat_connections),
                        actionIds = listOf(
                            ShortcutActionId.FLOW_CONNECT_PORT,
                            ShortcutActionId.FLOW_DETACH_CONNECTION,
                            ShortcutActionId.FLOW_BRANCH_WIRE,
                            ShortcutActionId.FLOW_MOVE_POINT,
                            ShortcutActionId.FLOW_CREATE_RAMIFICATION,
                            ShortcutActionId.FLOW_STRUCTURED_MODE
                        )
                    )

                    HorizontalDivider(
                        thickness = ToolkitTheme.dimensions.borderThin,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    InfoCategorySection(
                        title = stringResource(Res.string.flow_info_cat_selection),
                        actionIds = listOf(
                            ShortcutActionId.FLOW_SELECT_NODE,
                            ShortcutActionId.FLOW_BOX_SELECT,
                            ShortcutActionId.FLOW_MOVE_NODE,
                            ShortcutActionId.FLOW_DELETE_SELECTED
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoCategorySection(
    title: String,
    actionIds: List<String>,
    modifier: Modifier = Modifier
) {
    val shortcutManager = LocalShortcutManager.current
    val settings by (shortcutManager?.settings ?: kotlinx.coroutines.flow.MutableStateFlow(null)).collectAsState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
        ) {
            actionIds.forEach { actionId ->
                val action = remember(actionId, settings) { shortcutManager?.getAction(actionId) }
                val triggerFormatted = remember(actionId, settings) {
                    shortcutManager?.formatEffectiveTriggers(actionId) ?: "[ None ]"
                }
                val situationLabel = remember(action) {
                    action?.situation?.displayLabel?.let { "($it)" } ?: ""
                }
                val behaviorLabel = action?.title?.resolve() ?: actionId

                ShortcutItemView(
                    triggerText = triggerFormatted,
                    situationText = situationLabel,
                    behaviorText = behaviorLabel
                )
            }
        }
    }
}

@Composable
private fun ShortcutItemView(
    triggerText: String,
    situationText: String,
    behaviorText: String,
    modifier: Modifier = Modifier
) {
    val triggers = remember(triggerText) {
        triggerText.split(" / ").map { it.trim() }.filter { it.isNotEmpty() }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(0.44f)
                .padding(end = ToolkitTheme.spacing.extraSmall),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraExtraSmall)
        ) {
            Text(
                text = behaviorText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (situationText.isNotEmpty()) {
                Text(
                    text = situationText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }

        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.weight(0.56f),
            horizontalArrangement = Arrangement.End,
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraExtraSmall)
        ) {
            triggers.forEach { trig ->
                Surface(
                    shape = ToolkitTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = androidx.compose.foundation.BorderStroke(
                        width = ToolkitTheme.dimensions.borderThin,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Text(
                        text = trig,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            horizontal = ToolkitTheme.spacing.extraSmall,
                            vertical = ToolkitTheme.spacing.extraExtraSmall
                        )
                    )
                }
            }
        }
    }
}
