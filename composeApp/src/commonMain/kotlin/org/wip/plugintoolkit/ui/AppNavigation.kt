package org.wip.plugintoolkit.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.ui.DialogService
import org.wip.plugintoolkit.features.flows.ui.FlowEditorView
import org.wip.plugintoolkit.features.flows.ui.FlowManagerView
import org.wip.plugintoolkit.features.flows.ui.FlowRunnerView
import org.wip.plugintoolkit.features.flows.viewmodel.ActiveFlowEditorTracker
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorViewModel
import org.wip.plugintoolkit.features.flows.viewmodel.FlowViewModel
import org.wip.plugintoolkit.features.job.ui.JobDashboard
import org.wip.plugintoolkit.features.landingPage.ui.LandingPage
import org.wip.plugintoolkit.features.navigation.model.Screen
import org.wip.plugintoolkit.features.plugin.ui.PluginManagerView
import org.wip.plugintoolkit.features.plugin.ui.PluginSectionScreen
import org.wip.plugintoolkit.features.repository.ui.PluginRepoView
import org.wip.plugintoolkit.features.settings.ui.SettingsScreen
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsViewModel
import plugintoolkit.composeapp.generated.resources.*
import org.wip.plugintoolkit.core.model.localized

@Composable
fun AppNavigation(
    backStack: NavBackStack<NavKey>,
    currentScreen: Screen,
    activeFlowEditorTracker: ActiveFlowEditorTracker,
    dialogService: DialogService,
    notificationService: NotificationService,
    flowViewModel: FlowViewModel,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val hasUnsavedChanges by activeFlowEditorTracker.hasUnsavedChanges.collectAsState()
    val dialogUnsavedChangesTitle = org.jetbrains.compose.resources.stringResource(Res.string.dialog_unsaved_changes)

    NavDisplay(
        backStack = backStack,
        modifier = modifier.fillMaxHeight(),
        onBack = {
            if (currentScreen is Screen.FlowEditor && hasUnsavedChanges) {
                dialogService.showConfirmation(
                    title = dialogUnsavedChangesTitle,
                    message = "All the unsaved data will be lost. Are you sure you want to exit?",
                    onConfirm = {
                        activeFlowEditorTracker.setHasUnsavedChanges(false)
                        if (backStack.size > 1) backStack.removeLast()
                    }
                )
            } else {
                if (backStack.size > 1) backStack.removeLast()
            }
        }
    ) { key ->
        when (key) {
            is Screen.Main -> NavEntry(key) {
                val navigate: (Screen) -> Unit = { screen ->
                    if (currentScreen != screen) {
                        if (currentScreen is Screen.FlowEditor && hasUnsavedChanges) {
                            dialogService.showConfirmation(
                                title = dialogUnsavedChangesTitle,
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
                }
                LandingPage(onNavigate = navigate)
            }

            is Screen.FlowManager -> NavEntry(key) {
                FlowManagerView(
                    viewModel = flowViewModel,
                    onEditFlow = { flowName -> backStack.add(Screen.FlowEditor(flowName)) },
                    onRunFlow = { flowName ->
                        backStack.add(Screen.FlowRunner(flowName))
                    }
                )
            }

            is Screen.FlowRunner -> NavEntry(key) {
                FlowRunnerView(viewModel = flowViewModel, initialFlowName = key.flowName)
            }

            is Screen.FlowEditor -> NavEntry(key) {
                val editorViewModel: FlowEditorViewModel =
                    koinViewModel(key = key.flowName, parameters = { parametersOf(key.flowName) })
                FlowEditorView(
                    viewModel = editorViewModel,
                    notificationService = notificationService,
                    onExit = {
                        if (hasUnsavedChanges) {
                            dialogService.showConfirmation(
                                title = dialogUnsavedChangesTitle,
                                message = "All the unsaved data will be lost. Are you sure you want to exit?",
                                onConfirm = {
                                    activeFlowEditorTracker.setHasUnsavedChanges(false)
                                    if (backStack.size > 1) {
                                        backStack.removeLast()
                                    } else {
                                        backStack.clear()
                                        backStack.add(Screen.FlowManager)
                                    }
                                }
                            )
                        } else {
                            if (backStack.size > 1) {
                                backStack.removeLast()
                            } else {
                                backStack.clear()
                                backStack.add(Screen.FlowManager)
                            }
                        }
                    }
                )
            }

            is Screen.Settings -> NavEntry(key) { SettingsScreen(viewModel = settingsViewModel) }
            is Screen.JobDashboard -> NavEntry(key) { JobDashboard() }
            is Screen.Plugins -> NavEntry(key) { PluginSectionScreen() }
            is Screen.Plugin -> NavEntry(key) { PluginSectionScreen(initialPluginId = key.id) }
            is Screen.PluginManager -> NavEntry(key) {
                PluginManagerView(onOpenPlugin = { id -> backStack.add(Screen.Plugin(id)) })
            }
            is Screen.PluginRepo -> NavEntry(key) { PluginRepoView() }
            else -> NavEntry(key) { }
        }
    }
}
