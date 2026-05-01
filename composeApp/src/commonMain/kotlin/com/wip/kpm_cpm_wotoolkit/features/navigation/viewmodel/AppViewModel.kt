package com.wip.kpm_cpm_wotoolkit.features.navigation.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Settings
import androidx.lifecycle.ViewModel
import com.wip.kpm_cpm_wotoolkit.core.model.localized
import com.wip.kpm_cpm_wotoolkit.features.navigation.model.Screen
import com.wip.kpm_cpm_wotoolkit.shared.components.sidebar.SidebarElement
import com.wip.kpm_cpm_wotoolkit.shared.components.sidebar.SidebarSectionData
import kpm_cpm_wotoolkit.composeapp.generated.resources.Res
import kpm_cpm_wotoolkit.composeapp.generated.resources.nav_board
import kpm_cpm_wotoolkit.composeapp.generated.resources.nav_jobs
import kpm_cpm_wotoolkit.composeapp.generated.resources.nav_main
import kpm_cpm_wotoolkit.composeapp.generated.resources.nav_plugins
import kpm_cpm_wotoolkit.composeapp.generated.resources.section_direct_execution
import kpm_cpm_wotoolkit.composeapp.generated.resources.section_flows
import kpm_cpm_wotoolkit.composeapp.generated.resources.section_plugins
import kpm_cpm_wotoolkit.composeapp.generated.resources.section_plugins_repositories
import kpm_cpm_wotoolkit.composeapp.generated.resources.settings

class AppViewModel : ViewModel() {
    val sections = listOf(
        SidebarSectionData(
            title = null, elements = listOf(
                SidebarElement(
                    id = Screen.Main, icon = Icons.Default.Home, title = Res.string.nav_main.localized
                )
            )
        ), SidebarSectionData(
            title = Res.string.section_direct_execution.localized, elements = listOf(
                SidebarElement(
                    id = Screen.Plugins, icon = Icons.Default.Extension, title = Res.string.nav_plugins.localized
                )
            )
        ), SidebarSectionData(
            title = Res.string.section_plugins.localized, elements = listOf(
                SidebarElement(
                    id = Screen.PluginManager,
                    icon = Icons.Default.Inventory,
                    title = Res.string.section_plugins.localized
                ), SidebarElement(
                    id = Screen.PluginRepo,
                    icon = Icons.Default.Inventory,
                    title = Res.string.section_plugins_repositories.localized
                )
            )
        ), SidebarSectionData(
            title = Res.string.section_flows.localized, elements = listOf(
                SidebarElement(
                    id = Screen.Board, icon = Icons.Default.Edit, title = Res.string.nav_board.localized
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
