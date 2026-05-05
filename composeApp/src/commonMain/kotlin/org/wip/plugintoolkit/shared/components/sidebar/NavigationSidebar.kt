package org.wip.plugintoolkit.shared.components.sidebar

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.model.LocalizedString
import org.wip.plugintoolkit.core.model.localized
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.navigation.model.Screen
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.app_name

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun <T> NavigationSidebar(
    title: LocalizedString,
    bodySections: List<SidebarSectionData<out T>>,
    bottomSections: List<SidebarSectionData<out T>> = emptyList(),
    currentScreen: T,
    onScreenSelected: (T) -> Unit,
    isNavbarCollapsed: Boolean,
    onToggleNavbar: () -> Unit,
    canCollapse: Boolean = true,
    headerContent: @Composable () -> Unit = {},
    bottomExtraContent: @Composable ColumnScope.() -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isHovered by remember { mutableStateOf(false) }

    // If navbar is collapsed, it can temporarily expand on hover
    val isActuallyExpanded = !canCollapse || !isNavbarCollapsed || isHovered

    val sidebarWidth by animateDpAsState(
        targetValue = if (isActuallyExpanded) ToolkitTheme.dimensions.sidebarExpandedWidth else ToolkitTheme.dimensions.sidebarCollapsedWidth,
        animationSpec = tween(durationMillis = 200)
    )

    Surface(
        modifier = modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .onPointerEvent(PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(PointerEventType.Exit) { isHovered = false },
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        shadowElevation = if (isHovered && isNavbarCollapsed && canCollapse) ToolkitTheme.spacing.small else ToolkitTheme.spacing.none
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(ToolkitTheme.spacing.mediumSmall)
        ) {
            // Header / toggle (TOP SECTION)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = ToolkitTheme.spacing.medium),
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
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.mediumSmall))
                    }
                }

                if (isActuallyExpanded) {
                    Text(
                        text = title.resolve(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip
                    )
                }
            }

            if (isActuallyExpanded) {
                headerContent()
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
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                }
            }

            // Bottom sections (BOTTOM SECTION)
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                bottomExtraContent()
                bottomSections.forEach { section ->
                    SidebarSection(
                        section = section,
                        currentSelection = currentScreen,
                        onItemSelected = onScreenSelected,
                        isExpanded = isActuallyExpanded
                    )
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
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
            title = Res.string.app_name.localized,
            bodySections = listOf<SidebarSectionData<Screen>>(),
            currentScreen = Screen.Main,
            onScreenSelected = {},
            isNavbarCollapsed = false,
            onToggleNavbar = {}
        )
    }
}