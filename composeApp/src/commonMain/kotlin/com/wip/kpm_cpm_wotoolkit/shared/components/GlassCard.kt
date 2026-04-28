package com.wip.kpm_cpm_wotoolkit.shared.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview

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
            .padding(16.dp),
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

