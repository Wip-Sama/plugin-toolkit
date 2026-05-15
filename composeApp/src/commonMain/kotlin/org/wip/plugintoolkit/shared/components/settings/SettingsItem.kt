package org.wip.plugintoolkit.shared.components.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.features.settings.utils.LocalSettingsSearchQuery
import org.wip.plugintoolkit.core.theme.ToolkitTheme

@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    extraContent: @Composable (() -> Unit)? = null,
    control: @Composable (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor by animateColorAsState(
        targetValue = if (isHovered) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else Color.Transparent, animationSpec = tween(200)
    )

    val searchQuery = LocalSettingsSearchQuery.current
    val matchesQuery = remember(searchQuery, title, subtitle) {
        if (searchQuery.isBlank()) true
        else {
            title.contains(searchQuery, ignoreCase = true) || (subtitle?.contains(searchQuery, ignoreCase = true)
                ?: false)
        }
    }

    AnimatedVisibility(
        visible = matchesQuery, enter = fadeIn(animationSpec = tween(300)), exit = fadeOut(animationSpec = tween(300))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(ToolkitTheme.spacing.mediumSmall))
                .background(backgroundColor)
                .hoverable(interactionSource, enabled = enabled)
                .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(ToolkitTheme.spacing.small), verticalAlignment = Alignment.CenterVertically
        ) {
            val alpha = if (enabled) 1f else 0.5f
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconLarge).padding(end = ToolkitTheme.spacing.medium)
                    )
                } else {
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                        )
                    }
                    if (extraContent != null) {
                        extraContent()
                    }
                }

                if (control != null) {
                    Box(modifier = Modifier.padding(start = ToolkitTheme.spacing.medium)) {
                        control()
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun SettingsItemPreview() {
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            SettingsItem(
                title = "Dark Mode",
                subtitle = "Toggle dark theme application wide",
                icon = Icons.Default.Settings,
                control = { Switch(checked = true, onCheckedChange = {}) })
        }
    }
}

