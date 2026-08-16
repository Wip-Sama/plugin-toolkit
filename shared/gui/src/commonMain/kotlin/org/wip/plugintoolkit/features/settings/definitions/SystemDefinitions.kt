package org.wip.plugintoolkit.features.settings.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Window
import androidx.compose.material.icons.filled.Storage
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.SystemConfig
import org.wip.plugintoolkit.core.utils.StartupManager
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.AutoUpdateSettings
import org.wip.plugintoolkit.features.settings.model.CacheManagementMode
import org.wip.plugintoolkit.features.settings.model.FlowSettings
import org.wip.plugintoolkit.features.settings.model.GeneralSettings
import org.wip.plugintoolkit.features.settings.model.WindowStartMode
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey
import org.wip.plugintoolkit.features.settings.utils.SettingText
import org.wip.plugintoolkit.features.settings.utils.SettingsRegistryBuilder
import org.wip.plugintoolkit.features.settings.utils.bindGroup
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsViewModel
import plugintoolkit.composeapp.generated.resources.*

/**
 * Registers system startup, window management, flow editing, and software update settings.
 */
fun SettingsRegistryBuilder.systemDefinitions(viewModel: SettingsViewModel, appConfig: SystemConfig) {
    nav(SettingNavKey.SystemSettings) {
        // ── System ───────────────────────────────────────────────────
        section(Res.string.section_system) {
            bindGroup(AppSettings::general, { copy(general = it) }) {
                switch(
                    GeneralSettings::launchAtStartup,
                    Res.string.setting_launch_at_startup,
                    Icons.Default.Launch,
                    subtitle = if (appConfig.isPortable) {
                        SettingText.Resource(Res.string.setting_launch_at_startup_disabled_portable_subtitle)
                    } else {
                        SettingText.Resource(Res.string.setting_launch_at_startup_subtitle)
                    },
                    enabled = { !appConfig.isPortable },
                    sideEffect = { s ->
                        StartupManager.setLaunchAtStartup(s.general.launchAtStartup, s.general.launchMinimizedAtStartup)
                    }
                ) { copy(launchAtStartup = it) }

                switch(
                    GeneralSettings::launchMinimizedAtStartup,
                    Res.string.setting_launch_minimized_at_startup,
                    Icons.Default.Minimize,
                    subtitle = SettingText.Resource(Res.string.setting_launch_minimized_at_startup_subtitle),
                    enabled = { !appConfig.isPortable && it.general.launchAtStartup },
                    sideEffect = { s ->
                        StartupManager.setLaunchAtStartup(s.general.launchAtStartup, s.general.launchMinimizedAtStartup)
                    }
                ) { copy(launchMinimizedAtStartup = it) }

                dropdown(
                    GeneralSettings::windowStartMode,
                    Res.string.setting_window_start_mode,
                    Icons.Default.Window,
                    options = WindowStartMode.entries,
                    subtitle = SettingText.Resource(Res.string.setting_window_start_mode_subtitle),
                    labelProvider = { it.name }
                ) { copy(windowStartMode = it) }

                switch(
                    GeneralSettings::closeToTray,
                    Res.string.setting_close_to_tray,
                    Icons.Default.Close,
                    subtitle = SettingText.Resource(Res.string.setting_close_to_tray_subtitle)
                ) { copy(closeToTray = it) }

                dropdown(
                    GeneralSettings::cacheManagement,
                    Res.string.setting_cache_management,
                    Icons.Default.Storage,
                    options = CacheManagementMode.entries,
                    subtitle = SettingText.Resource(Res.string.setting_cache_management_subtitle),
                    labelProvider = { mode ->
                        when (mode) {
                            CacheManagementMode.SystemManaged -> stringResource(Res.string.cache_mode_system)
                            CacheManagementMode.ApplicationManaged -> stringResource(Res.string.cache_mode_application)
                        }
                    }
                ) { copy(cacheManagement = it) }
            }
        }

        // ── Flows ────────────────────────────────────────────────────
        section(Res.string.section_flows_header) {
            bindGroup(AppSettings::flows, { copy(flows = it) }) {
                switch(
                    FlowSettings::autosave,
                    Res.string.setting_flow_autosave,
                    Icons.Default.Save,
                    subtitle = SettingText.Resource(Res.string.setting_flow_autosave_subtitle)
                ) { copy(autosave = it) }
            }
        }

        // ── Auto Update ──────────────────────────────────────────────
        if (!appConfig.isPortable) {
            section(SettingText.Raw("Updates")) {
                bindGroup(AppSettings::autoUpdate, { copy(autoUpdate = it) }) {
                    switch(
                        AutoUpdateSettings::enabled,
                        Res.string.setting_enable_auto_update,
                        Icons.Default.SystemUpdate,
                        subtitle = SettingText.Resource(Res.string.setting_enable_auto_update_subtitle)
                    ) { copy(enabled = it) }

                    switch(
                        AutoUpdateSettings::checkOnStartup,
                        Res.string.setting_check_on_startup,
                        Icons.Default.Refresh,
                        subtitle = SettingText.Resource(Res.string.setting_check_on_startup_subtitle),
                        enabled = { it.autoUpdate.enabled }
                    ) { copy(checkOnStartup = it) }
                }

                SettingCustom(
                    id = "system.checkForUpdates",
                    title = Res.string.action_check_for_updates,
                    subtitle = SettingText.Raw("Manually check for available application updates"),
                    icon = Icons.Default.Refresh,
                    control = { _, _ ->
                        org.wip.plugintoolkit.features.settings.ui.CheckForUpdatesControl(viewModel)
                    }
                )
            }
        }
    }
}
