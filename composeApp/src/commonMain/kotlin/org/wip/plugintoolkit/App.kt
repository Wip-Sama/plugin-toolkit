package org.wip.plugintoolkit

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.navigation3.runtime.rememberNavBackStack
import org.koin.compose.koinInject
import org.wip.plugintoolkit.core.model.localized
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.core.ui.DialogService
import org.wip.plugintoolkit.core.ui.LocalLanguage
import org.wip.plugintoolkit.core.utils.PlatformLocalization
import org.wip.plugintoolkit.features.flows.viewmodel.ActiveFlowEditorTracker
import org.wip.plugintoolkit.features.flows.viewmodel.FlowViewModel
import org.wip.plugintoolkit.features.job.ui.JobBadge
import org.wip.plugintoolkit.features.navigation.model.Screen
import org.wip.plugintoolkit.features.navigation.model.ScreenNavConfig
import org.wip.plugintoolkit.features.navigation.viewmodel.AppViewModel
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginViewModel
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsViewModel
import org.wip.plugintoolkit.shared.components.TooltipProvider
import org.wip.plugintoolkit.shared.components.sidebar.SidebarElement
import org.wip.plugintoolkit.ui.AppNavigation
import org.wip.plugintoolkit.ui.AppScaffold
import org.wip.plugintoolkit.ui.AppUpdateDialogs

@Composable
fun App(
    viewModel: SettingsViewModel = koinInject(),
    pluginViewModel: PluginViewModel = koinInject(),
    appViewModel: AppViewModel = koinInject(),
    notificationService: NotificationService = koinInject(),
    dialogService: DialogService = koinInject(),
    flowViewModel: FlowViewModel = koinInject(),
    activeFlowEditorTracker: ActiveFlowEditorTracker = koinInject()
) {
    val settings by viewModel.settings.collectAsState()
    val languageCode by viewModel.currentLanguageCode.collectAsState()
    val isLoaded by viewModel.isLoaded.collectAsState()

    // Immediately set/update default locale during composition to avoid race conditions
    // where stringResource() evaluates before LaunchedEffect runs.
    PlatformLocalization.setApplicationLanguage(languageCode)

    // Provide the current language via CompositionLocal so all UI elements can react to language changes
    // This preserves navigation state while allowing string resources to update
    CompositionLocalProvider(LocalLanguage provides languageCode) {
        AppContentImpl(
            settings = settings,
            viewModel = viewModel,
            pluginViewModel = pluginViewModel,
            appViewModel = appViewModel,
            notificationService = notificationService,
            dialogService = dialogService,
            flowViewModel = flowViewModel,
            activeFlowEditorTracker = activeFlowEditorTracker
        )
    }
}


@Composable
private fun AppContentImpl(
    settings: AppSettings,
    viewModel: SettingsViewModel,
    pluginViewModel: PluginViewModel,
    appViewModel: AppViewModel,
    notificationService: NotificationService,
    dialogService: DialogService,
    flowViewModel: FlowViewModel,
    activeFlowEditorTracker: ActiveFlowEditorTracker
) {
    // Read from LocalLanguage to make this composable recompose when language changes
    val currentLanguage = LocalLanguage.current

    val general = settings.general
    val density = LocalDensity.current
    val customDensity = remember(density, general.scaling) {
        Density(
            density = density.density * general.scaling,
            fontScale = density.fontScale * general.scaling
        )
    }

    CompositionLocalProvider(LocalDensity provides customDensity) {
        AppTheme(appearance = settings.appearance) {
            TooltipProvider {
                val backStack = rememberNavBackStack(ScreenNavConfig, Screen.Main)
                val currentScreen: Screen = (backStack.lastOrNull() ?: Screen.Main) as Screen
    
                val baseSections = appViewModel.sections
                val sections = remember(baseSections, currentScreen) {
                    val isEditingFlow = currentScreen is Screen.FlowEditor
                    val newSections = baseSections.toMutableList()
                    val mgtIndex = newSections.indexOfFirst { it.title == "Management".localized }
                    if (mgtIndex != -1) {
                        val mgtSection = newSections[mgtIndex]
                        val newElements = mgtSection.elements.toMutableList()
                        val flowManagerIndex = newElements.indexOfFirst { it.id == Screen.FlowManager }
                        val insertIndex = if (flowManagerIndex >= 0) flowManagerIndex + 1 else newElements.size
    
                        val editorId = currentScreen as? Screen.FlowEditor ?: Screen.FlowEditor("")
    
                        val editorElement = SidebarElement(
                            id = editorId as Screen,
                            icon = Icons.Default.Edit,
                            title = "Editor".localized,
                            isVisible = isEditingFlow
                        )
                        newElements.add(insertIndex, editorElement)
                        newSections[mgtIndex] = mgtSection.copy(elements = newElements)
                    }
                    newSections
                }
                val bottomSections = remember(appViewModel.bottomSections) {
                    appViewModel.bottomSections.map { section ->
                        section.copy(elements = section.elements.map { element ->
                            if (element.id == Screen.JobDashboard) {
                                element.copy(trailingContent = { JobBadge(it) })
                            } else element
                        })
                    }
                }
    
                val hasUnsavedChanges by activeFlowEditorTracker.hasUnsavedChanges.collectAsState()
    
                // Render UI normally without destroying the composition tree.
                // Recomposition is triggered reactively since the screens and localized strings
                // read from LocalLanguage / trigger recomposition.
                AppScaffold(
                    settings = settings,
                    sections = sections,
                    bottomSections = bottomSections,
                    currentScreen = currentScreen,
                    onScreenSelected = { screen ->
                        if (currentScreen != screen) {
                            if (currentScreen is Screen.FlowEditor && hasUnsavedChanges) {
                                dialogService.showConfirmation(
                                    title = "Unsaved Changes",
                                    message = "All the unsaved data will be lost. Are you sure you want to exit?",
                                    onConfirm = {
                                        activeFlowEditorTracker.setHasUnsavedChanges(false)
                                        backStack.clear()
                                        backStack.add(screen)
                                    }
                                )
                            } else {
                                backStack.clear()
                                backStack.add(screen)
                            }
                        }
                    },
                    notificationService = notificationService,
                    dialogService = dialogService
                ) {
                    AppNavigation(
                        backStack = backStack,
                        currentScreen = currentScreen,
                        activeFlowEditorTracker = activeFlowEditorTracker,
                        dialogService = dialogService,
                        notificationService = notificationService,
                        flowViewModel = flowViewModel,
                        settingsViewModel = viewModel
                    )
                }
    
                AppUpdateDialogs(viewModel = viewModel)
            }
        }
    }
}
