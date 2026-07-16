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
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import org.wip.plugintoolkit.core.model.LocalizedString
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.settings.utils.LocalSettingsSearchQuery
import plugintoolkit.composeapp.generated.resources.*
import org.wip.plugintoolkit.core.model.localized

@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(ToolkitTheme.spacing.mediumSmall),
    extraContent: (@Composable () -> Unit)? = null,
    control: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.disabled * ToolkitTheme.opacity.settingsItemDefault)
            isPressed && onClick != null -> MaterialTheme.colorScheme.primary.copy(alpha = ToolkitTheme.opacity.settingsItemPressed)
            isHovered -> MaterialTheme.colorScheme.primary.copy(alpha = ToolkitTheme.opacity.settingsItemHover)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.settingsItemDefault)
        },
        animationSpec = tween(200)
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
                .clip(shape)
                .background(backgroundColor)
                .hoverable(interactionSource, enabled = enabled)
                .then(
                    if (onClick != null && enabled) {
                        Modifier.clickable(
                            interactionSource = interactionSource,
                            indication = androidx.compose.foundation.LocalIndication.current,
                            onClick = onClick
                        )
                    } else Modifier
                )
                .padding(ToolkitTheme.spacing.small), verticalAlignment = Alignment.CenterVertically
        ) {
            val alpha = if (enabled) 1f else 0.5f
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .padding(end = ToolkitTheme.spacing.medium)
                            .size(ToolkitTheme.dimensions.settingsIconContainerSize)
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.sidebarBackground),
                                shape = RoundedCornerShape(ToolkitTheme.dimensions.settingsIconCornerRadius)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                            modifier = Modifier.size(ToolkitTheme.dimensions.settingsIconSize)
                        )
                    }
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

//@Preview
//@Composable
//fun SettingsItemPreview() {
//    MaterialTheme {
//        Column(modifier = Modifier.padding(ToolkitTheme.spacing.medium)) {
//            SettingsItem(
//                title = Res.string.setting_dark_mode.localized,
//                subtitle = Res.string.setting_dark_mode_desc.localized,
//                icon = Icons.Default.Settings,
//                control = { Switch(checked = true, onCheckedChange = {}) })
//        }
//    }
//}

@Composable
fun getGroupedShape(
    index: Int,
    totalCount: Int
): androidx.compose.ui.graphics.Shape {
    val outer = ToolkitTheme.dimensions.buttonGroupOuterCorner
    val inner = ToolkitTheme.dimensions.buttonGroupInnerCorner
    return remember(index, totalCount, outer, inner) {
        when {
            totalCount <= 1 -> RoundedCornerShape(outer)
            index == 0 -> RoundedCornerShape(
                topStart = outer,
                topEnd = outer,
                bottomStart = inner,
                bottomEnd = inner
            )

            index == totalCount - 1 -> RoundedCornerShape(
                topStart = inner,
                topEnd = inner,
                bottomStart = outer,
                bottomEnd = outer
            )

            else -> RoundedCornerShape(inner)
        }
    }
}

