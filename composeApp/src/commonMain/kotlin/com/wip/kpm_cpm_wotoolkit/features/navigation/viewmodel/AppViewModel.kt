package com.wip.kpm_cpm_wotoolkit.features.navigation.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.lifecycle.ViewModel
import com.wip.kpm_cpm_wotoolkit.features.navigation.model.Screen
import com.wip.kpm_cpm_wotoolkit.shared.components.sidebar.SidebarElement
import com.wip.kpm_cpm_wotoolkit.shared.components.sidebar.SidebarSectionData
import kpm_cpm_wotoolkit.composeapp.generated.resources.*
import kpm_cpm_wotoolkit.composeapp.generated.resources.Res
import com.wip.kpm_cpm_wotoolkit.core.model.localized

class AppViewModel : ViewModel() {
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
            title = Res.string.section_modules.localized,
            elements = listOf(
                SidebarElement(
                    id = Screen.ModuleManager,
                    icon = Icons.Default.Inventory,
                    title = Res.string.section_modules.localized
                ),
                SidebarElement(
                    id = Screen.ModuleRepo,
                    icon = Icons.Default.Inventory,
                    title = Res.string.section_modules_repositories.localized
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
                    title = Res.string.nav_jobs.localized
                ),
                SidebarElement(
                    id = Screen.Settings,
                    icon = Icons.Default.Settings,
                    title = Res.string.settings.localized
                )
            )
        )
    )
}
