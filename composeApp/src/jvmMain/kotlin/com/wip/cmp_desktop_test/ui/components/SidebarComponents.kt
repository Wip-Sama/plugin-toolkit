package com.wip.cmp_desktop_test.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import cmp_desktop_test.composeapp.generated.resources.Res
import cmp_desktop_test.composeapp.generated.resources.*
import com.wip.cmp_desktop_test.ui.screens.Screen
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import com.wip.cmp_desktop_test.ui.theme.*

data class SidebarElement(
    val id: Any,
    val icon: ImageVector,
    val title: StringResource
)

enum class SidebarItemPosition {
    StandAlone, Start, Middle, End
}

data class SidebarSectionData(
    val title: StringResource,
    val elements: List<SidebarElement>
)

@Composable
fun SidebarSection(
    section: SidebarSectionData,
    currentSelection: Any,
    onItemSelected: (Any) -> Unit,
    isExpanded: Boolean = true
) {
    var isSectionCollapsed by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        if (isExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { isSectionCollapsed = !isSectionCollapsed }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(section.title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip
                )
                Icon(
                    imageVector = if (isSectionCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = "Toggle Section",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        } else {
            Spacer(modifier = Modifier.height(4.dp))
        }

        section.elements.forEachIndexed { index, element ->
            val isSelected = element.id == currentSelection

            val isVisible = !isSectionCollapsed || isSelected

            val position = if (section.elements.size == 1 || (isSectionCollapsed && isSelected)) {
                SidebarItemPosition.StandAlone
            } else {
                when (index) {
                    0 -> SidebarItemPosition.Start
                    section.elements.lastIndex -> SidebarItemPosition.End
                    else -> SidebarItemPosition.Middle
                }
            }

            AnimatedVisibility(
                visible = isVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                SidebarItem(
                    element = element,
                    isSelected = isSelected,
                    onClick = { onItemSelected(element.id) },
                    isExpanded = isExpanded,
                    position = position
                )
            }
        }
    }
}

@Composable
fun SidebarItem(
    element: SidebarElement,
    isSelected: Boolean,
    onClick: () -> Unit,
    isExpanded: Boolean = true,
    position: SidebarItemPosition = SidebarItemPosition.StandAlone
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val iconBackgroundColor = if (isSelected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val iconTintColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

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
        }
    }
}
