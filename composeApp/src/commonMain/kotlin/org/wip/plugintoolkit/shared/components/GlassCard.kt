package org.wip.plugintoolkit.shared.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.jetbrains.compose.resources.stringResource
import plugintoolkit.composeapp.generated.resources.*

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable ColumnScope.() -> Unit
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onClick
        )
    } else Modifier

    val finalModifier = modifier
        .animateContentSize()
        .clip(MaterialTheme.shapes.large)
        .then(clickableModifier)
        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.glassBackground))
        .border(
            width = ToolkitTheme.dimensions.borderUnselected,
            brush = Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.outline.copy(alpha = ToolkitTheme.opacity.borderLow),
                    MaterialTheme.colorScheme.outline.copy(alpha = ToolkitTheme.opacity.cardBackground)
                )
            ),
            shape = MaterialTheme.shapes.large
        )

    Column(
        modifier = finalModifier
            .padding(ToolkitTheme.spacing.medium),
        content = content
    )
}

@Preview
@Composable
private fun GlassCardPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(ToolkitTheme.spacing.medium)) {
            GlassCard {
                Text(stringResource(Res.string.preview_glasscard_title), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(Res.string.preview_glasscard_subtitle), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

