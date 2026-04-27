package com.wip.kpm_cpm_wotoolkit

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.wip.kpm_cpm_wotoolkit.features.navigation.model.Screen
import com.wip.kpm_cpm_wotoolkit.features.navigation.model.ScreenNavConfig
import kpm_cpm_wotoolkit.composeapp.generated.resources.*
import kpm_cpm_wotoolkit.composeapp.generated.resources.Res
import com.wip.kpm_cpm_wotoolkit.shared.components.sidebar.NavigationSidebar
import com.wip.kpm_cpm_wotoolkit.shared.components.sidebar.SidebarElement
import com.wip.kpm_cpm_wotoolkit.shared.components.sidebar.SidebarSectionData
import com.wip.kpm_cpm_wotoolkit.features.board.ui.BoardScreen
import com.wip.kpm_cpm_wotoolkit.features.plugin.ui.MainScreen
import com.wip.kpm_cpm_wotoolkit.features.settings.ui.SettingsScreen
import com.wip.kpm_cpm_wotoolkit.features.settings.model.AppLanguage
import com.wip.kpm_cpm_wotoolkit.features.settings.viewmodel.SettingsViewModel
import com.wip.kpm_cpm_wotoolkit.core.theme.AppTheme
import com.wip.kpm_cpm_wotoolkit.core.utils.PlatformLocalization
import com.wip.kpm_cpm_wotoolkit.core.notification.NotificationService
import com.wip.kpm_cpm_wotoolkit.shared.components.ToastHost
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.mp.KoinPlatform.getKoin
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject

@Composable
fun App(viewModel: SettingsViewModel = koinInject()) {
    val languageCode by viewModel.currentLanguageCode.collectAsState()
    

    LaunchedEffect(languageCode) {
        PlatformLocalization.setApplicationLanguage(languageCode)
    }

    AppContent(viewModel)
}

@Composable
private fun AppContent(viewModel: SettingsViewModel) {
    val general = viewModel.settings.general
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

            val bottomSections = listOf(
                SidebarSectionData(
                    title = null,
                    elements = listOf(
                        SidebarElement(
                            id = Screen.Settings,
                            icon = Icons.Default.Settings,
                            title = Res.string.settings
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
                        title = Res.string.app_name,
                        bodySections = sections,
                        bottomSections = bottomSections,
                        currentScreen = currentScreen,
                        onScreenSelected = { screen ->
                            backStack.clear()
                            if (backStack.lastOrNull() != screen) {
                                backStack.add(screen)
                            }
                        },
                        isNavbarCollapsed = isNavbarCollapsed,
                        onToggleNavbar = { isNavbarCollapsed = !isNavbarCollapsed },
                        modifier = Modifier.fillMaxHeight()
                    )

                    val notificationService = koinInject<NotificationService>()
                    ToastHost(
                        notificationService = notificationService,
                        settings = viewModel.settings.notifications
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun AppPreview() {
    App()
}

