package com.wip.kpm_cpm_wotoolkit

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
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
import com.wip.kpm_cpm_wotoolkit.features.job.ui.JobDashboard
import com.wip.kpm_cpm_wotoolkit.features.job.ui.JobBadge
import com.wip.kpm_cpm_wotoolkit.features.settings.ui.SettingsScreen
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
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
import com.wip.kpm_cpm_wotoolkit.core.model.localized

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

            val pluginViewModel: com.wip.kpm_cpm_wotoolkit.features.plugin.viewmodel.PluginViewModel = koinInject()
            val loadedPlugins = pluginViewModel.loadedPlugins

            val backStack = rememberNavBackStack(ScreenNavConfig, Screen.Main)
            val currentScreen: Screen = (backStack.lastOrNull() ?: Screen.Main) as Screen

            val sections = listOf(
                SidebarSectionData(
                    title = null,
                    elements = listOf(
                        SidebarElement(
                            id = Screen.Main,
                            icon = Icons.Default.Home,
                            title = Res.string.nav_main.localized
                        )
                    )
                ),
                SidebarSectionData(
                    title = Res.string.section_direct_execution.localized,
                    elements = listOf(
                        SidebarElement(
                            id = Screen.Modules,
                            icon = Icons.Default.Extension,
                            title = Res.string.nav_modules.localized
                        )
                    )
                ),
                SidebarSectionData(
                    title = Res.string.section_flows.localized,
                    elements = listOf(
                        SidebarElement(
                            id = Screen.Board,
                            icon = Icons.Default.Edit,
                            title = Res.string.nav_board.localized
                        )
                    )
                )
            )

            val bottomSections = listOf(
                SidebarSectionData(
                    title = null,
                    elements = listOf(
                        SidebarElement(
                            id = Screen.JobDashboard,
                            icon = Icons.Default.PendingActions,
                            title = Res.string.nav_jobs.localized,
                            trailingContent = { JobBadge(it) }
                        ),
                        SidebarElement(
                            id = Screen.Settings,
                            icon = Icons.Default.Settings,
                            title = Res.string.settings.localized
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
                        // First Sidebar Spacer
                        Spacer(modifier = Modifier.width(layoutSidebarWidth))

                        // Second Sidebar (if module is selected)
                        // Second Sidebar (Direct Execution flow)
                        if (currentScreen is Screen.Modules || currentScreen is Screen.Module) {
                            com.wip.kpm_cpm_wotoolkit.features.plugin.ui.DirectExecutionSidebar(
                                loadedPlugins = loadedPlugins,
                                selectedModuleId = (currentScreen as? Screen.Module)?.id,
                                onModuleSelected = { id -> backStack.add(Screen.Module(id)) },
                                onBackToModules = { 
                                    if (backStack.any { it is Screen.Modules }) {
                                        while (backStack.lastOrNull() !is Screen.Modules) {
                                            backStack.removeLast()
                                        }
                                    } else {
                                        backStack.add(Screen.Modules)
                                    }
                                },
                                selectedCapability = pluginViewModel.selectedCapability,
                                onCapabilitySelected = { pluginViewModel.selectCapability(it) }
                            )
                        }

                        NavDisplay(
                            backStack = backStack,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onBack = { if (backStack.size > 1) backStack.removeLast() }
                        ) { key ->
                            when (key) {
                                is Screen.Main     -> NavEntry(key) { MainScreen() }
                                is Screen.Board    -> NavEntry(key) { BoardScreen() }
                                is Screen.Settings -> NavEntry(key) { SettingsScreen(viewModel = viewModel) }
                                is Screen.JobDashboard -> NavEntry(key) { JobDashboard() }
                                is Screen.Modules -> NavEntry(key) { 
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Select a module from the sidebar to see its capabilities", style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                                is Screen.Module   -> NavEntry(key) {
                                    val plugin = loadedPlugins.find { it.getManifest().module.id == key.id }
                                    if (plugin != null) {
                                        LaunchedEffect(key.id) {
                                            pluginViewModel.selectPlugin(plugin)
                                        }
                                        com.wip.kpm_cpm_wotoolkit.features.plugin.ui.PluginContent(viewModel = pluginViewModel)
                                    } else {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                            Text("Module not found or unloaded")
                                        }
                                    }
                                }
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

                    val notificationService = koinInject<NotificationService>()
                    val dialogService = koinInject<com.wip.kpm_cpm_wotoolkit.core.ui.DialogService>()
                    
                    ToastHost(
                        notificationService = notificationService,
                        settings = viewModel.settings.notifications
                    )
                    com.wip.kpm_cpm_wotoolkit.core.ui.DialogHost(dialogService)
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

