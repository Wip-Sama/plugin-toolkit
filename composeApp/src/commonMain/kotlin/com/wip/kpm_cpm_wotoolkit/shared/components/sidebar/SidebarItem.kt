package com.wip.kpm_cpm_wotoolkit.shared.components.sidebar

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource

@Composable
fun <T> SidebarItem(
    element: SidebarElement<out T>,
    isSelected: Boolean,
    onClick: () -> Unit,
    isExpanded: Boolean = true,
    position: SidebarItemPosition = SidebarItemPosition.StandAlone
) {
    val backgroundColor = if (isSelected) 
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f) 
    else 
        Color.Transparent
        
    val contentColor = if (isSelected) 
        MaterialTheme.colorScheme.primary 
    else 
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        
    val iconBackgroundColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        
    val iconTintColor = if (isSelected) 
        MaterialTheme.colorScheme.onPrimaryContainer 
    else 
        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)

    val targetTopStart = if (position == SidebarItemPosition.Start || position == SidebarItemPosition.StandAlone) 16.dp else 4.dp
    val targetBottomStart = if (position == SidebarItemPosition.End || position == SidebarItemPosition.StandAlone) 16.dp else 4.dp

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
            .padding(bottom = 2.dp)
            .clip(animatedShape)
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(if (isExpanded) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isExpanded) Arrangement.Start else Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBackgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = element.icon,
                contentDescription = stringResource(element.title),
                tint = iconTintColor,
                modifier = Modifier.size(22.dp)
            )
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(element.title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
            Spacer(modifier = Modifier.weight(1f))
            element.trailingContent(true)
        } else {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                element.trailingContent(false)
            }
        }
    }
}
