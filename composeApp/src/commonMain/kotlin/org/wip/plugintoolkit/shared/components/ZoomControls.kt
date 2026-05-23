package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.wip.plugintoolkit.core.theme.ToolkitTheme

@Composable
fun ZoomControls(
    scale: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = ToolkitTheme.opacity.glassBackground),
                shape = CircleShape
            )
            .padding(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
    ) {
        IconButton(onClick = onZoomOut) {
            Text("-", style = MaterialTheme.typography.titleLarge)
        }

        Text(
            text = "${(scale * 100).roundToInt()}%",
            style = MaterialTheme.typography.bodyLarge
        )

        IconButton(onClick = onZoomIn) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Preview
@Composable
private fun ZoomControlsPreview() {
    MaterialTheme {
        ZoomControls(
            scale = 1.0f,
            onZoomIn = {},
            onZoomOut = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}

