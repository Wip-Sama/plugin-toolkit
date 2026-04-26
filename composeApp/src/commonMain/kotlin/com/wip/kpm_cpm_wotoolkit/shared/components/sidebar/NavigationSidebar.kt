package com.wip.kpm_cpm_wotoolkit.shared.components.sidebar

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import kpm_cpm_wotoolkit.composeapp.generated.resources.Res
import kpm_cpm_wotoolkit.composeapp.generated.resources.*
import com.wip.kpm_cpm_wotoolkit.features.navigation.model.Screen
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.jetbrains.compose.resources.StringResource

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun NavigationSidebar(
    title: StringResource,
    bodySections: List<SidebarSectionData>,
    bottomSections: List<SidebarSectionData> = emptyList(),
    currentScreen: Any,
    onScreenSelected: (Any) -> Unit,
    isNavbarCollapsed: Boolean,
    onToggleNavbar: () -> Unit,
    canCollapse: Boolean = true,
    modifier: Modifier = Modifier
) {
    var isHovered by remember { mutableStateOf(false) }

    // If navbar is collapsed, it can temporarily expand on hover
    val isActuallyExpanded = !canCollapse || !isNavbarCollapsed || isHovered

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
        shadowElevation = if (isHovered && isNavbarCollapsed && canCollapse) 8.dp else 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp)
        ) {
            // Header / toggle (TOP SECTION)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (isActuallyExpanded) Arrangement.Start else Arrangement.Center
            ) {
                if (canCollapse) {
                    IconButton(onClick = onToggleNavbar) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Toggle Sidebar"
                        )
                    }
                    if (isActuallyExpanded) {
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                }

                if (isActuallyExpanded) {
                    Text(
                        text = stringResource(title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }

            // Nav sections (BODY SECTION - Scrollable)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                bodySections.forEach { section ->
                    SidebarSection(
                        section = section,
                        currentSelection = currentScreen,
                        onItemSelected = onScreenSelected,
                        isExpanded = isActuallyExpanded
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Bottom sections (BOTTOM SECTION)
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                bottomSections.forEach { section ->
                    SidebarSection(
                        section = section,
                        currentSelection = currentScreen,
                        onItemSelected = onScreenSelected,
                        isExpanded = isActuallyExpanded
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Preview
@Composable
private fun NavigationSidebarPreview() {
    MaterialTheme {
        NavigationSidebar(
            title = Res.string.app_name,
            bodySections = listOf(),
            currentScreen = Screen.Main,
            onScreenSelected = {},
            isNavbarCollapsed = false,
            onToggleNavbar = {}
        )
    }
}