package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.theme.ToolkitTheme

enum class ToolkitChipStyle {
    Filled,
    Tinted,
    Outlined
}

@Composable
fun ToolkitChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    style: ToolkitChipStyle = ToolkitChipStyle.Tinted,
    shape: Shape = CircleShape,
    fontWeight: FontWeight = FontWeight.SemiBold
) {
    val backgroundColor = when (style) {
        ToolkitChipStyle.Filled -> containerColor
        ToolkitChipStyle.Tinted -> containerColor.copy(alpha = ToolkitTheme.opacity.divider)
        ToolkitChipStyle.Outlined -> containerColor.copy(alpha = ToolkitTheme.opacity.textFieldContainer)
    }

    val border = if (style == ToolkitChipStyle.Outlined) {
        BorderStroke(ToolkitTheme.dimensions.borderUnselected, containerColor.copy(alpha = ToolkitTheme.opacity.divider))
    } else {
        null
    }

    Surface(
        color = backgroundColor,
        shape = shape,
        border = border,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = ToolkitTheme.spacing.small,
                vertical = ToolkitTheme.spacing.extraSmall
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
        ) {
            icon?.invoke()
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = fontWeight
            )
        }
    }
}
