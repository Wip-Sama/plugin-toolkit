package org.wip.plugintoolkit.features.settings.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.VerifiedUser
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.ui.DialogService
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
fun SettingsRegistryBuilder.pluginDefinitions(dialogService: DialogService? = null) {
    val effectiveDialogService = dialogService ?: run {
        org.koin.core.context.GlobalContext.getOrNull()?.get<DialogService>()
    }

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

                switch(
                    ExtensionSettings::pluginSettingsInPlace,
                    Res.string.setting_plugin_settings_in_place,
                    Icons.Default.OpenInNew,
                    subtitle = SettingText.Resource(Res.string.setting_plugin_settings_in_place_subtitle),
                    onBeforeChange = { isEnabling, onProceed ->
                        if (isEnabling && effectiveDialogService != null) {
                            effectiveDialogService.showConfirmation(
                                title = "Warning: In-Place Settings",
                                message = "Opening settings in-place may cause some components to not update their unlocked states until reloaded. Are you sure you want to enable this mode?",
                                onConfirm = onProceed
                            )
                        } else {
                            onProceed()
                        }
                    }
                ) { copy(pluginSettingsInPlace = it) }

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
