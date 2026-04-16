package com.wip.cmp_desktop_test

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cmp_desktop_test.composeapp.generated.resources.*
import cmp_desktop_test.composeapp.generated.resources.Res
import com.wip.cmp_desktop_test.ui.components.NavigationSidebar
import com.wip.cmp_desktop_test.ui.components.SidebarElement
import com.wip.cmp_desktop_test.ui.components.SidebarSectionData
import com.wip.cmp_desktop_test.ui.screens.BoardScreen
import com.wip.cmp_desktop_test.ui.screens.MainScreen
import com.wip.cmp_desktop_test.ui.screens.Screen
import com.wip.cmp_desktop_test.ui.screens.SettingsScreen

@Composable
fun App() {
    MaterialTheme {
        var isNavbarCollapsed by remember { mutableStateOf(false) }

        // Nav3-style developer-owned backstack — no external library needed
        val backStack = remember { mutableStateListOf<Screen>(Screen.Main) }
        val currentScreen: Screen = backStack.lastOrNull() ?: Screen.Main

        val sections = listOf(
            SidebarSectionData(
                title = Res.string.section_application,
                elements = listOf(
                    SidebarElement(
                        id = Screen.Main,
                        icon = Icons.Default.Home,
                        title = Res.string.nav_main
                    ),
                    SidebarElement(
                        id = Screen.Board,
                        icon = Icons.Default.Edit,
                        title = Res.string.nav_board
                    )
                )
            )
        )

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Animated sidebar footprint width
                val layoutSidebarWidth by animateDpAsState(
                    targetValue = if (isNavbarCollapsed) 80.dp else 250.dp,
                    animationSpec = tween(durationMillis = 200)
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    // Reserve physical space for the sidebar
                    Spacer(modifier = Modifier.width(layoutSidebarWidth))

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        // Render the current top of the backstack
                        key(currentScreen) {
                            when (currentScreen) {
                                Screen.Main -> MainScreen()
                                Screen.Board -> BoardScreen()
                                Screen.Settings -> SettingsScreen()
                            }
                        }
                    }
                }

                // Sidebar floats over / beside the content
                NavigationSidebar(
                    sections = sections,
                    currentScreen = currentScreen,
                    onScreenSelected = { route ->
                        if (currentScreen != route) {
                            // singleTop: remove existing occurrence then push
                            backStack.remove(route)
                            backStack.add(route as Screen)
                        }
                    },
                    isNavbarCollapsed = isNavbarCollapsed,
                    onToggleNavbar = { isNavbarCollapsed = !isNavbarCollapsed },
                    modifier = Modifier.fillMaxHeight()
                )
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun AppPreview() {
    App()
}
