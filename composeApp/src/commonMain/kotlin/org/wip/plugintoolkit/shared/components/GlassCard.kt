package org.wip.plugintoolkit.shared.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.theme.ToolkitTheme

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val baseModifier = modifier
        .clip(MaterialTheme.shapes.large)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.05f)
                )
            ),
            shape = MaterialTheme.shapes.large
        )

    val finalModifier = if (onClick != null) baseModifier.clickable(onClick = onClick) else baseModifier

    Column(
        modifier = finalModifier
            .animateContentSize()
            .padding(ToolkitTheme.spacing.medium),
        content = content
    )
}

@Preview
@Composable
private fun GlassCardPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            GlassCard {
                Text("This is a GlassCard", style = MaterialTheme.typography.bodyLarge)
                Text("With some content inside", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

