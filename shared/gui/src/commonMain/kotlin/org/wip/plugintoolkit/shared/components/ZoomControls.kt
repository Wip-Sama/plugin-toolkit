package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_zoom_in
import plugintoolkit.composeapp.generated.resources.action_zoom_level
import plugintoolkit.composeapp.generated.resources.action_zoom_out
import kotlin.math.roundToInt

/**
 * Compact Material 3 Expressive canvas zoom pill control matching floating toolbar height.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ZoomControls(
    scale: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = ToolkitTheme.dimensions
    val spacing = ToolkitTheme.spacing
    val zoomOutLabel = stringResource(Res.string.action_zoom_out)
    val zoomInLabel = stringResource(Res.string.action_zoom_in)
    val zoomLevelLabel = stringResource(Res.string.action_zoom_level, (scale * 100).roundToInt())

    Row(
        modifier = modifier
            .height(dimensions.toolbarZoomHeight)
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = CircleShape
            )
            .border(
                width = dimensions.borderUnselected,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
            )
            .padding(horizontal = spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.extraSmall)
    ) {
        ToolkitTooltipBox(tooltip = zoomOutLabel, position = ToolkitTooltipPosition.Above) {
            IconButton(
                onClick = onZoomOut,
                modifier = Modifier
                    .size(dimensions.desktopTouchTargetSize)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .semantics { contentDescription = zoomOutLabel }
                    .testTag("zoom_out_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = zoomOutLabel,
                    modifier = Modifier.size(dimensions.iconSmall)
                )
            }
        }

        Text(
            text = "${(scale * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(horizontal = spacing.xxs)
                .semantics { contentDescription = zoomLevelLabel }
        )

        ToolkitTooltipBox(tooltip = zoomInLabel, position = ToolkitTooltipPosition.Above) {
            IconButton(
                onClick = onZoomIn,
                modifier = Modifier
                    .size(dimensions.desktopTouchTargetSize)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .semantics { contentDescription = zoomInLabel }
                    .testTag("zoom_in_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = zoomInLabel,
                    modifier = Modifier.size(dimensions.iconSmall)
                )
            }
        }
    }
}

@Preview
@Composable
private fun ZoomControlsPreview() {
    AppTheme(appearance = AppearanceSettings()) {
        ZoomControls(
            scale = 1.0f,
            onZoomIn = {},
            onZoomOut = {},
            modifier = Modifier.padding(ToolkitTheme.spacing.medium)
        )
    }
}

