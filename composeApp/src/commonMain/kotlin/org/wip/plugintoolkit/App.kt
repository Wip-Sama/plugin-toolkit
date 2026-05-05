package org.wip.plugintoolkit

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.wip.plugintoolkit.core.model.localized
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.ui.DialogHost
import org.wip.plugintoolkit.core.ui.DialogService
import org.wip.plugintoolkit.core.utils.PlatformLocalization
import org.wip.plugintoolkit.features.board.ui.BoardScreen
import org.wip.plugintoolkit.features.job.ui.JobBadge
import org.wip.plugintoolkit.features.job.ui.JobDashboard
import org.wip.plugintoolkit.features.landingPage.ui.LandingPage
import org.wip.plugintoolkit.features.navigation.model.Screen
import org.wip.plugintoolkit.features.navigation.model.ScreenNavConfig
import org.wip.plugintoolkit.features.navigation.viewmodel.AppViewModel
import org.wip.plugintoolkit.features.plugin.ui.PluginManagerView
import org.wip.plugintoolkit.features.plugin.ui.PluginSectionScreen
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginViewModel
import org.wip.plugintoolkit.features.repository.ui.PluginRepoView
import org.wip.plugintoolkit.features.settings.ui.SettingsScreen
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsViewModel
import org.wip.plugintoolkit.shared.components.ToastHost
import org.wip.plugintoolkit.shared.components.sidebar.NavigationSidebar
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.app_name
import org.koin.compose.koinInject
import org.wip.plugintoolkit.features.settings.ui.UpdateDialog
import org.wip.plugintoolkit.features.settings.ui.UpdateProgressOverlay

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

    LaunchedEffect(Unit) {
        val settings = viewModel.settings
        if (settings.autoUpdate.enabled && settings.autoUpdate.checkOnStartup) {
            viewModel.checkForUpdates()
        }
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
                        targetValue = if (isNavbarCollapsed) ToolkitTheme.dimensions.sidebarCollapsedWidth else ToolkitTheme.dimensions.sidebarExpandedWidth,
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
                                is Screen.Main -> NavEntry(key) { LandingPage() }
                                is Screen.Board -> NavEntry(key) { BoardScreen() }
                                is Screen.Settings -> NavEntry(key) { SettingsScreen(viewModel = viewModel) }
                                is Screen.JobDashboard -> NavEntry(key) { JobDashboard() }
                                is Screen.Plugins -> NavEntry(key) { PluginSectionScreen() }
                                is Screen.Plugin -> NavEntry(key) { PluginSectionScreen(initialPluginId = key.id) }
                                is Screen.PluginManager -> NavEntry(key) { PluginManagerView() }
                                is Screen.PluginRepo -> NavEntry(key) { PluginRepoView() }
                                else -> NavEntry(key) { }
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

                    // Update Handling UI (Global)
                    val availableUpdate = viewModel.availableUpdate
                    val isDownloading = viewModel.isDownloadingUpdate
                    val downloadProgress by viewModel.downloadProgress.collectAsState()

                    if (availableUpdate != null) {
                        UpdateDialog(
                            updateInfo = availableUpdate,
                            onDownload = { viewModel.downloadAndInstallUpdate() },
                            onDismiss = { viewModel.dismissUpdate() }
                        )
                    }

                    if (isDownloading) {
                        UpdateProgressOverlay(progress = downloadProgress)
                    }
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

