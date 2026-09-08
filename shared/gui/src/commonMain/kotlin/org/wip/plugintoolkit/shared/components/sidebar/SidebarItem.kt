package org.wip.plugintoolkit.shared.components.sidebar

import org.wip.plugintoolkit.core.model.resolve

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.shared.components.tooltip

@Composable
fun <T> SidebarItem(
    element: SidebarElement<out T>,
    isSelected: Boolean,
    onClick: () -> Unit,
    isExpanded: Boolean = true,
    position: SidebarItemPosition = SidebarItemPosition.StandAlone
) {
    val backgroundColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = ToolkitTheme.opacity.buttonBackground)
    else
        ToolkitTheme.colors.transparent

    val contentColor = if (isSelected)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.onSurface.copy(alpha = ToolkitTheme.opacity.secondaryText)

    val iconBackgroundColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.high)

    val iconTintColor = if (isSelected)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.primary.copy(alpha = ToolkitTheme.opacity.high)

    val targetTopStart =
        if (position == SidebarItemPosition.Start || position == SidebarItemPosition.StandAlone) ToolkitTheme.dimensions.cornerRadiusLarge else ToolkitTheme.dimensions.cornerRadiusExtraSmall
    val targetBottomStart =
        if (position == SidebarItemPosition.End || position == SidebarItemPosition.StandAlone) ToolkitTheme.dimensions.cornerRadiusLarge else ToolkitTheme.dimensions.cornerRadiusExtraSmall

    val topStart by animateDpAsState(targetTopStart, animationSpec = tween(300))
    val topEnd by animateDpAsState(targetTopStart, animationSpec = tween(300))
    val bottomStart by animateDpAsState(targetBottomStart, animationSpec = tween(300))
    val bottomEnd by animateDpAsState(targetBottomStart, animationSpec = tween(300))

    val animatedShape = RoundedCornerShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomStart = bottomStart,
        bottomEnd = bottomEnd
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = ToolkitTheme.spacing.badgeVertical)
            .clip(animatedShape)
            .background(backgroundColor)
            .clickable { onClick() }
            .then(if (!isExpanded) Modifier.tooltip(element.title.resolve()) else Modifier)
            .padding(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(ToolkitTheme.dimensions.settingsIconContainerSize)
                .clip(ToolkitTheme.shapes.medium)
                .background(iconBackgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = element.icon,
                contentDescription = element.title.resolve(),
                tint = iconTintColor,
                modifier = Modifier.size(ToolkitTheme.dimensions.sidebarIconSize)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(tween(150)) + expandHorizontally(tween(200)),
            exit = fadeOut(tween(100)) + shrinkHorizontally(tween(200)),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))
                Text(
                    text = element.title.resolve(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                element.trailingContent(true)
            }
        }
        if (!isExpanded) {
            Box(contentAlignment = Alignment.TopEnd) {
                element.trailingContent(false)
            }
        }
    }
}
