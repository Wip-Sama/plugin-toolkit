package org.wip.plugintoolkit.features.flows.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import plugintoolkit.composeapp.generated.resources.*

@Composable
fun FlowControlsInfoCard(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(min = ToolkitTheme.dimensions.nodeWidth, max = ToolkitTheme.dimensions.nodeWidth)
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
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
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

            // Shortcut list
            Column(
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
            ) {
                ShortcutRow(
                    badgeText = stringResource(Res.string.flow_info_pan_label),
                    description = stringResource(Res.string.flow_info_pan_desc)
                )
                ShortcutRow(
                    badgeText = stringResource(Res.string.flow_info_zoom_label),
                    description = stringResource(Res.string.flow_info_zoom_desc)
                )
                ShortcutRow(
                    badgeText = stringResource(Res.string.flow_info_select_label),
                    description = stringResource(Res.string.flow_info_select_desc)
                )
                ShortcutRow(
                    badgeText = stringResource(Res.string.flow_info_box_select_label),
                    description = stringResource(Res.string.flow_info_box_select_desc)
                )
                ShortcutRow(
                    badgeText = stringResource(Res.string.flow_info_move_label),
                    description = stringResource(Res.string.flow_info_move_desc)
                )
                ShortcutRow(
                    badgeText = stringResource(Res.string.flow_info_connect_label),
                    description = stringResource(Res.string.flow_info_connect_desc)
                )
                ShortcutRow(
                    badgeText = stringResource(Res.string.flow_info_detach_label),
                    description = stringResource(Res.string.flow_info_detach_desc)
                )
                ShortcutRow(
                    badgeText = stringResource(Res.string.flow_info_order_label),
                    description = stringResource(Res.string.flow_info_order_desc)
                )
                ShortcutRow(
                    badgeText = stringResource(Res.string.flow_info_hover_label),
                    description = stringResource(Res.string.flow_info_hover_desc)
                )
                ShortcutRow(
                    badgeText = stringResource(Res.string.flow_info_delete_label),
                    description = stringResource(Res.string.flow_info_delete_desc)
                )
                ShortcutRow(
                    badgeText = stringResource(Res.string.flow_info_undo_redo_label),
                    description = stringResource(Res.string.flow_info_undo_redo_desc)
                )
            }
        }
    }
}

@Composable
private fun ShortcutRow(
    badgeText: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            shape = ToolkitTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            border = androidx.compose.foundation.BorderStroke(
                width = ToolkitTheme.dimensions.borderThin,
                color = MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    horizontal = ToolkitTheme.spacing.extraSmall,
                    vertical = ToolkitTheme.spacing.extraExtraSmall
                )
            )
        }

        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}
