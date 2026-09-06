package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import plugintoolkit.composeapp.generated.resources.*
import kotlin.math.roundToInt

@Composable
fun ZoomControls(
    scale: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zoomOutLabel = stringResource(Res.string.action_zoom_out)
    val zoomInLabel = stringResource(Res.string.action_zoom_in)
    val zoomLevelLabel = stringResource(Res.string.action_zoom_level, (scale * 100).roundToInt())

    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = CircleShape
            )
            .border(
                width = ToolkitTheme.dimensions.borderUnselected,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
            )
            .padding(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
    ) {
        IconButton(
            onClick = onZoomOut,
            modifier = Modifier
                .semantics { contentDescription = zoomOutLabel }
                .testTag("zoom_out_button")
        ) {
            Icon(Icons.Default.Remove, contentDescription = zoomOutLabel)
        }

        Text(
            text = "${(scale * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { contentDescription = zoomLevelLabel }
        )

        IconButton(
            onClick = onZoomIn,
            modifier = Modifier
                .semantics { contentDescription = zoomInLabel }
                .testTag("zoom_in_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = zoomInLabel)
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

