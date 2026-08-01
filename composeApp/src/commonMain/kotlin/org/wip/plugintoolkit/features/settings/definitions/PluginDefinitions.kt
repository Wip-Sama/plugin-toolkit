package org.wip.plugintoolkit.features.settings.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.VerifiedUser
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.ExtensionSettings
import org.wip.plugintoolkit.features.settings.model.FileAccessMode
import org.wip.plugintoolkit.features.settings.model.PluginUnplugBehavior
import org.wip.plugintoolkit.features.settings.ui.ConfigureDirectoriesControl
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey
import org.wip.plugintoolkit.features.settings.utils.SettingText
import org.wip.plugintoolkit.features.settings.utils.SettingsRegistryBuilder
import org.wip.plugintoolkit.features.settings.utils.bindGroup
import plugintoolkit.composeapp.generated.resources.*

/**
 * Registers extension and plugin system behavior and security settings.
 */
fun SettingsRegistryBuilder.pluginDefinitions() {
    nav(SettingNavKey.SystemSettings) {
        section(Res.string.section_plugins) {
            bindGroup(AppSettings::extensions, { copy(extensions = it) }) {
                dropdown(
                    ExtensionSettings::pluginUnplugBehavior,
                    Res.string.setting_plugin_unplug_behavior,
                    Icons.Default.Cable,
                    options = PluginUnplugBehavior.entries,
                    subtitle = SettingText.Resource(Res.string.setting_plugin_unplug_behavior_subtitle),
                    labelProvider = {
                        when (it) {
                            PluginUnplugBehavior.Block -> stringResource(Res.string.setting_plugin_unplug_behavior_block)
                            PluginUnplugBehavior.StopJobs -> stringResource(Res.string.setting_plugin_unplug_behavior_stop)
                        }
                    }
                ) { copy(pluginUnplugBehavior = it) }

                switch(
                    ExtensionSettings::strictSignatureChecking,
                    Res.string.setting_strict_signature_checking,
                    Icons.Default.VerifiedUser,
                    subtitle = SettingText.Resource(Res.string.setting_strict_signature_checking_subtitle)
                ) { copy(strictSignatureChecking = it) }

                dropdown(
                    ExtensionSettings::fileAccessMode,
                    Res.string.setting_file_access_mode,
                    Icons.Default.FolderSpecial,
                    options = FileAccessMode.entries,
                    subtitle = SettingText.Resource(Res.string.setting_file_access_mode_subtitle),
                    labelProvider = { it.name }
                ) { copy(fileAccessMode = it) }
            }

            SettingCustom(
                id = "extensions.configureFileAccessDirectories",
                title = Res.string.setting_configure_directories,
                subtitle = SettingText.Resource(Res.string.setting_configure_directories_subtitle),
                icon = Icons.Default.FolderOpen,
                enabled = { it.extensions.fileAccessMode != FileAccessMode.Unrestricted },
                control = { settings, onUpdate ->
                    ConfigureDirectoriesControl(settings, onUpdate)
                }
            )
        }
    }
}
