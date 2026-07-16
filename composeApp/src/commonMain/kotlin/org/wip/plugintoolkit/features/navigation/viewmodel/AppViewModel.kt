package org.wip.plugintoolkit.features.navigation.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.lifecycle.ViewModel
import org.wip.plugintoolkit.core.model.localized
import org.wip.plugintoolkit.core.update.UpdateService
import org.wip.plugintoolkit.features.navigation.model.Screen
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.shared.components.sidebar.SidebarElement
import org.wip.plugintoolkit.shared.components.sidebar.SidebarSectionData
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.nav_jobs
import plugintoolkit.composeapp.generated.resources.nav_main
import plugintoolkit.composeapp.generated.resources.settings

class AppViewModel(
    settingsRepository: SettingsRepository,
    private val updateService: UpdateService
) : ViewModel() {

    init {
        val settings = settingsRepository.loadSettings()
        if (settings.autoUpdate.enabled && settings.autoUpdate.checkOnStartup) {
            // We'll let the SettingsViewModel handle the actual state for now, 
            // but we can trigger it here or through a shared state.
            // Actually, it's better if SettingsViewModel is the one holding the state.
        }
    }

    val sections = listOf(
        SidebarSectionData(
            title = null, elements = listOf(
                SidebarElement(
                    id = Screen.Main, icon = Icons.Default.Home, title = Res.string.nav_main.localized
                )
            )
        ), SidebarSectionData(
            title = "Runner".localized, elements = listOf(
                SidebarElement(
                    id = Screen.Plugins, icon = Icons.Default.PlayCircle, title = "Capabilities".localized
                ), SidebarElement(
                    id = Screen.FlowRunner(), icon = Icons.Default.PlayCircle, title = "Flows".localized
                )
            )
        ), SidebarSectionData(
            title = "Management".localized, elements = listOf(
                SidebarElement(
                    id = Screen.PluginManager, icon = Icons.Default.Inventory, title = Res.string.tab_plugin_manager.localized
                ), SidebarElement(
                    id = Screen.FlowManager, icon = Icons.Default.Dashboard, title = "Flow Manager".localized
                ), SidebarElement(
                    id = Screen.PluginRepo, icon = Icons.Default.CloudDownload, title = "Repositories".localized
                )
            )
        )
    )

    val bottomSections = listOf(
        SidebarSectionData(
            title = null, elements = listOf(
                SidebarElement(
                    id = Screen.JobDashboard, icon = Icons.Default.PendingActions, title = Res.string.nav_jobs.localized
                ), SidebarElement(
                    id = Screen.Settings, icon = Icons.Default.Settings, title = Res.string.settings.localized
                )
            )
        )
    )
}
