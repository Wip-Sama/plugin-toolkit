package org.wip.plugintoolkit.shared.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.preview_glasscard_subtitle
import plugintoolkit.composeapp.generated.resources.preview_glasscard_title

/**
 * Standard Material 3 Expressive Card for the Toolkit design system.
 * Uses semantic container colors (surfaceContainer), expressive shapes,
 * and subtle tonal elevation instead of non-native glassmorphism.
 */
@Composable
fun ToolkitCard(
    modifier: Modifier = Modifier,
    shape: Shape = ToolkitTheme.shapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    elevation: CardElevation = CardDefaults.cardElevation(defaultElevation = ToolkitTheme.dimensions.cardElevation),
    contentPadding: PaddingValues = PaddingValues(ToolkitTheme.spacing.md),
    animateContentSize: Boolean = false,
    onClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (animateContentSize) {
        modifier.clip(shape).animateContentSize(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing))
    } else {
        modifier.clip(shape)
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = cardModifier,
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            border = border,
            elevation = elevation,
            interactionSource = interactionSource
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(
            modifier = cardModifier,
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            border = border,
            elevation = elevation
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

@Preview
@Composable
private fun ToolkitCardPreview() {
    AppTheme(appearance = AppearanceSettings()) {
        Box(modifier = Modifier.padding(ToolkitTheme.spacing.medium)) {
            ToolkitCard {
                Text(stringResource(Res.string.preview_glasscard_title), style = ToolkitTheme.typography.bodyLarge)
                Text(stringResource(Res.string.preview_glasscard_subtitle), style = ToolkitTheme.typography.bodySmall)
            }
        }
    }
}
