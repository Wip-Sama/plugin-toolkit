package org.wip.plugintoolkit.features.settings.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Window
import org.wip.plugintoolkit.core.utils.StartupManager
import org.wip.plugintoolkit.features.settings.model.*
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey
import org.wip.plugintoolkit.features.settings.utils.*
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsViewModel
import plugintoolkit.composeapp.generated.resources.*

fun SettingsRegistryBuilder.systemDefinitions(viewModel: SettingsViewModel) {
    nav(SettingNavKey.SystemSettings) {
        // ── System ───────────────────────────────────────────────────
        section(SettingText.Resource(Res.string.section_system)) {
            SettingSwitch(
                p1 = AppSettings::general,
                p2 = GeneralSettings::launchAtStartup,
                title = SettingText.Resource(Res.string.setting_launch_at_startup),
                subtitle = SettingText.Raw("Automatically start the application when you log in"),
                icon = Icons.Default.Launch,
                sideEffect = { s ->
                    StartupManager.setLaunchAtStartup(s.general.launchAtStartup, s.general.launchMinimizedAtStartup)
                },
                setValue = { s, v -> s.copy(general = s.general.copy(launchAtStartup = v)) }
            )

            SettingSwitch(
                p1 = AppSettings::general,
                p2 = GeneralSettings::launchMinimizedAtStartup,
                title = SettingText.Resource(Res.string.setting_launch_minimized_at_startup),
                subtitle = SettingText.Resource(Res.string.setting_launch_minimized_at_startup_subtitle),
                icon = Icons.Default.Minimize,
                enabled = { it.general.launchAtStartup },
                sideEffect = { s ->
                    StartupManager.setLaunchAtStartup(s.general.launchAtStartup, s.general.launchMinimizedAtStartup)
                },
                setValue = { s, v -> s.copy(general = s.general.copy(launchMinimizedAtStartup = v)) }
            )

            SettingDropdown(
                p1 = AppSettings::general,
                p2 = GeneralSettings::windowStartMode,
                title = SettingText.Resource(Res.string.setting_window_start_mode),
                subtitle = SettingText.Raw("Select how the window should appear when opened"),
                icon = Icons.Default.Window,
                options = WindowStartMode.entries,
                labelProvider = { it.name },
                setValue = { s, v -> s.copy(general = s.general.copy(windowStartMode = v)) }
            )

            SettingSwitch(
                p1 = AppSettings::general,
                p2 = GeneralSettings::closeToTray,
                title = SettingText.Resource(Res.string.setting_close_to_tray),
                subtitle = SettingText.Raw("Closing the main window will minimize it to the system tray"),
                icon = Icons.Default.Close,
                setValue = { s, v -> s.copy(general = s.general.copy(closeToTray = v)) }
            )
        }

        // ── Auto Update ──────────────────────────────────────────────
        section(SettingText.Raw("Updates")) {
            SettingSwitch(
                p1 = AppSettings::autoUpdate,
                p2 = AutoUpdateSettings::enabled,
                title = SettingText.Raw("Auto Update"),
                subtitle = SettingText.Raw("Automatically check for and download application updates"),
                icon = Icons.Default.SystemUpdate,
                setValue = { s, v -> s.copy(autoUpdate = s.autoUpdate.copy(enabled = v)) }
            )

            SettingCustom(
                id = "system.checkForUpdates",
                title = SettingText.Raw("Check for Updates"),
                subtitle = SettingText.Raw("Manually check for available application updates"),
                icon = Icons.Default.Refresh,
                control = { _, _ ->
                    org.wip.plugintoolkit.features.settings.ui.CheckForUpdatesControl(viewModel)
                }
            )
        }
    }
}
