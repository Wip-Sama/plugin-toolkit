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
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.wip.cmp_desktop_test.ui.screens.ScreenNavConfig
import cmp_desktop_test.composeapp.generated.resources.*
import cmp_desktop_test.composeapp.generated.resources.Res
import com.wip.cmp_desktop_test.ui.components.NavigationSidebar
import com.wip.cmp_desktop_test.ui.components.SidebarElement
import com.wip.cmp_desktop_test.ui.components.SidebarSectionData
import com.wip.cmp_desktop_test.ui.screens.BoardScreen
import com.wip.cmp_desktop_test.ui.screens.MainScreen
import com.wip.cmp_desktop_test.ui.screens.Screen
import com.wip.cmp_desktop_test.ui.screens.SettingsScreen

import com.wip.cmp_desktop_test.settings.SettingsViewModel
import com.wip.cmp_desktop_test.ui.theme.AppTheme
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun App(viewModel: SettingsViewModel = viewModel { SettingsViewModel() }) {
    AppTheme(theme = viewModel.settings.appearance.theme) {
        var isNavbarCollapsed by remember { mutableStateOf(false) }

        // Nav3: library-managed backstack with SavedStateConfiguration for JVM polymorphic serialization
        val backStack = rememberNavBackStack(ScreenNavConfig, Screen.Main)
        val currentScreen: Screen = (backStack.lastOrNull() ?: Screen.Main) as Screen

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

                    // Nav3 NavDisplay: observes the backstack and renders the top entry
                    NavDisplay(
                        backStack = backStack,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onBack = { if (backStack.size > 1) backStack.removeLast() }
                    ) { key ->
                        when (key) {
                            is Screen.Main     -> NavEntry(key) { MainScreen() }
                            is Screen.Board    -> NavEntry(key) { BoardScreen() }
                            is Screen.Settings -> NavEntry(key) { SettingsScreen(viewModel = viewModel) }
                            else               -> NavEntry(key) { }
                        }
                    }
                }

                // Sidebar floats over / beside the content
                NavigationSidebar(
                    sections = sections,
                    currentScreen = currentScreen,
                    onScreenSelected = { route ->
                        val screen = route as Screen
                        if (backStack.lastOrNull() != screen) {
                            backStack.removeAll { it == screen }
                            backStack.add(screen)
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
