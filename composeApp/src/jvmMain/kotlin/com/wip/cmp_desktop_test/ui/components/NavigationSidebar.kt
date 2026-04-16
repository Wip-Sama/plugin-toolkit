package com.wip.cmp_desktop_test.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import cmp_desktop_test.composeapp.generated.resources.Res
import cmp_desktop_test.composeapp.generated.resources.*
import com.wip.cmp_desktop_test.ui.screens.Screen
import org.jetbrains.compose.resources.stringResource
import com.wip.cmp_desktop_test.ui.theme.*


@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun NavigationSidebar(
    sections: List<SidebarSectionData>,
    currentScreen: Any,
    onScreenSelected: (Any) -> Unit,
    isNavbarCollapsed: Boolean,
    onToggleNavbar: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isHovered by remember { mutableStateOf(false) }

    // If navbar is collapsed, it can temporarily expand on hover
    val isActuallyExpanded = !isNavbarCollapsed || isHovered

    val sidebarWidth by animateDpAsState(
        targetValue = if (isActuallyExpanded) 250.dp else 80.dp,
        animationSpec = tween(durationMillis = 200)
    )

    Surface(
        modifier = modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false },
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        shadowElevation = if (isHovered && isNavbarCollapsed) 8.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp)
        ) {
            // Header / toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isActuallyExpanded) Arrangement.Start else Arrangement.Center
            ) {
                IconButton(onClick = onToggleNavbar) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Toggle Sidebar"
                    )
                }

                if (isActuallyExpanded) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(Res.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }

            // Nav sections
            sections.forEach { section ->
                SidebarSection(
                    section = section,
                    currentSelection = currentScreen,
                    onItemSelected = onScreenSelected,
                    isExpanded = isActuallyExpanded
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // Settings button at the bottom
            val isSettingsSelected = currentScreen == Screen.Settings
            val settingsBg = if (isSettingsSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
            val settingsColor = if (isSettingsSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
            val settingsIconColor = if (isSettingsSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            val settingsIconBg = if (isSettingsSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)

            if (isActuallyExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(settingsBg)
                        .clickable { onScreenSelected(Screen.Settings) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(settingsIconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = settingsIconColor, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(Res.string.settings),
                        color = settingsColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(settingsBg)
                        .clickable { onScreenSelected(Screen.Settings) }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(settingsIconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = settingsIconColor, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}


@androidx.compose.ui.tooling.preview.Preview
@Composable
fun NavigationSidebarPreview() {
    MaterialTheme {
        val sections = listOf(
            SidebarSectionData(
                title = Res.string.section_privacy,
                elements = listOf(
                    SidebarElement("privacy", Icons.Default.Security, Res.string.nav_privacy)
                )
            ),
            SidebarSectionData(
                title = Res.string.section_archive,
                elements = listOf(
                    SidebarElement("archiviazione", Icons.Default.List, Res.string.nav_archive),
                    SidebarElement("backup", Icons.Default.Refresh, Res.string.nav_backup)
                )
            )
        )

        NavigationSidebar(
            sections = sections,
            currentScreen = "privacy",
            onScreenSelected = {},
            isNavbarCollapsed = false,
            onToggleNavbar = {},
            modifier = Modifier.height(600.dp)
        )
    }
}
