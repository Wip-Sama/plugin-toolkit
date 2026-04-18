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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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
import com.wip.cmp_desktop_test.ui.screens.settings.SettingsScreen
import com.wip.cmp_desktop_test.settings.AppLanguage
import com.wip.cmp_desktop_test.settings.SettingsViewModel
import com.wip.cmp_desktop_test.ui.theme.AppTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun App(viewModel: SettingsViewModel = viewModel { SettingsViewModel() }) {
    val general = viewModel.settings.general
    val localization = viewModel.settings.localization
    
    // 1. Language Control
    val languageCode = if (localization.useSystemLanguage) {
        val systemLang = System.getProperty("user.language")
        if (systemLang == "it") "it" else "en"
    } else {
        when (localization.language) {
            AppLanguage.Italian -> "it"
            AppLanguage.English -> "en"
        }
    }

    LaunchedEffect(languageCode) {
        Locale.setDefault(Locale.forLanguageTag(languageCode))
    }

    AppContent(viewModel, general)
}

@Composable
private fun AppContent(viewModel: SettingsViewModel, general: com.wip.cmp_desktop_test.settings.GeneralSettings) {
    // 3. GUI Scaling
    val density = LocalDensity.current
    val customDensity = remember(density, general.scaling) {
        Density(
            density = density.density * general.scaling,
            fontScale = density.fontScale * general.scaling
        )
    }

    CompositionLocalProvider(LocalDensity provides customDensity) {
        AppTheme(appearance = viewModel.settings.appearance) {
            var isNavbarCollapsed by remember { mutableStateOf(false) }

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
                    val layoutSidebarWidth by animateDpAsState(
                        targetValue = if (isNavbarCollapsed) 80.dp else 250.dp,
                        animationSpec = tween(durationMillis = 200)
                    )

                    Row(modifier = Modifier.fillMaxSize()) {
                        Spacer(modifier = Modifier.width(layoutSidebarWidth))

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
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun AppPreview() {
    App()
}
