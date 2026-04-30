package com.wip.kpm_cpm_wotoolkit

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.wip.kpm_cpm_wotoolkit.features.navigation.model.Screen
import com.wip.kpm_cpm_wotoolkit.features.navigation.model.ScreenNavConfig
import kpm_cpm_wotoolkit.composeapp.generated.resources.*
import kpm_cpm_wotoolkit.composeapp.generated.resources.Res
import com.wip.kpm_cpm_wotoolkit.shared.components.sidebar.NavigationSidebar
import com.wip.kpm_cpm_wotoolkit.features.board.ui.BoardScreen
import com.wip.kpm_cpm_wotoolkit.features.landingPage.ui.LandingPage
import com.wip.kpm_cpm_wotoolkit.features.plugin.ui.PluginSectionScreen
import com.wip.kpm_cpm_wotoolkit.features.job.ui.JobDashboard
import com.wip.kpm_cpm_wotoolkit.features.job.ui.JobBadge
import com.wip.kpm_cpm_wotoolkit.features.settings.ui.SettingsScreen
import com.wip.kpm_cpm_wotoolkit.features.plugin.ui.ModuleManagerView
import com.wip.kpm_cpm_wotoolkit.features.repository.ui.ModuleRepoView
import com.wip.kpm_cpm_wotoolkit.features.settings.viewmodel.SettingsViewModel
import com.wip.kpm_cpm_wotoolkit.core.theme.AppTheme
import com.wip.kpm_cpm_wotoolkit.core.theme.WOTheme
import com.wip.kpm_cpm_wotoolkit.core.utils.PlatformLocalization
import com.wip.kpm_cpm_wotoolkit.core.notification.NotificationService
import com.wip.kpm_cpm_wotoolkit.shared.components.ToastHost
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.koinInject
import com.wip.kpm_cpm_wotoolkit.core.model.localized
import com.wip.kpm_cpm_wotoolkit.core.ui.DialogHost
import com.wip.kpm_cpm_wotoolkit.core.ui.DialogService
import com.wip.kpm_cpm_wotoolkit.features.plugin.viewmodel.PluginViewModel
import com.wip.kpm_cpm_wotoolkit.features.navigation.viewmodel.AppViewModel

@Composable
fun App(
    viewModel: SettingsViewModel = koinInject(),
    pluginViewModel: PluginViewModel = koinInject(),
    appViewModel: AppViewModel = koinInject(),
    notificationService: NotificationService = koinInject(),
    dialogService: DialogService = koinInject()
) {
    val languageCode by viewModel.currentLanguageCode.collectAsState()
    

    LaunchedEffect(languageCode) {
        PlatformLocalization.setApplicationLanguage(languageCode)
    }

    AppContent(viewModel, pluginViewModel, appViewModel, notificationService, dialogService)
}

@Composable
private fun AppContent(
    viewModel: SettingsViewModel,
    pluginViewModel: PluginViewModel,
    appViewModel: AppViewModel,
    notificationService: NotificationService,
    dialogService: DialogService
) {
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

            val sections = appViewModel.sections
            val bottomSections = remember(appViewModel.bottomSections) {
                appViewModel.bottomSections.map { section ->
                    section.copy(elements = section.elements.map { element ->
                        if (element.id == Screen.JobDashboard) {
                            element.copy(trailingContent = { JobBadge(it) })
                        } else element
                    })
                }
            }

            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val layoutSidebarWidth by animateDpAsState(
                        targetValue = if (isNavbarCollapsed) WOTheme.dimensions.sidebarCollapsedWidth else WOTheme.dimensions.sidebarExpandedWidth,
                        animationSpec = tween(durationMillis = 200)
                    )

                    Row(modifier = Modifier.fillMaxSize()) {
                        // First Sidebar Spacer
                        Spacer(modifier = Modifier.width(layoutSidebarWidth))

                        // Content Area
                        NavDisplay(
                            backStack = backStack,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onBack = { if (backStack.size > 1) backStack.removeLast() }
                        ) { key ->
                            when (key) {
                                is Screen.Main     -> NavEntry(key) { LandingPage() }
                                is Screen.Board    -> NavEntry(key) { BoardScreen() }
                                is Screen.Settings -> NavEntry(key) { SettingsScreen(viewModel = viewModel) }
                                is Screen.JobDashboard -> NavEntry(key) { JobDashboard() }
                                is Screen.Modules -> NavEntry(key) { PluginSectionScreen() }
                                is Screen.Module   -> NavEntry(key) { PluginSectionScreen(initialModuleId = key.id) }
                                is Screen.ModuleManager -> NavEntry(key) { ModuleManagerView() }
                                is Screen.ModuleRepo -> NavEntry(key) { ModuleRepoView() }
                                else               -> NavEntry(key) { }
                            }
                        }
                    }

                    NavigationSidebar(
                        title = Res.string.app_name.localized,
                        bodySections = sections,
                        bottomSections = bottomSections,
                        currentScreen = currentScreen,
                        onScreenSelected = { screen ->
                            if (currentScreen != screen) {
                                backStack.clear()
                                backStack.add(screen)
                            }
                        },
                        isNavbarCollapsed = isNavbarCollapsed,
                        onToggleNavbar = { isNavbarCollapsed = !isNavbarCollapsed },
                        modifier = Modifier.fillMaxHeight()
                    )

                    
                    ToastHost(
                        notificationService = notificationService,
                        settings = viewModel.settings.notifications
                    )
                    DialogHost(dialogService)
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

