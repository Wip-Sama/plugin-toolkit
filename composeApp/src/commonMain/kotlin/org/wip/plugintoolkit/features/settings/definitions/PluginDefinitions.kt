package org.wip.plugintoolkit.features.settings.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.features.settings.model.*
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey
import org.wip.plugintoolkit.features.settings.utils.*
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.setting_plugin_unplug_behavior_block
import plugintoolkit.composeapp.generated.resources.setting_plugin_unplug_behavior_stop

fun SettingsRegistryBuilder.pluginDefinitions() {
    nav(SettingNavKey.SystemSettings) {
        section(SettingText.Raw("Plugins")) {
            SettingDropdown(
                p1 = AppSettings::extensions,
                p2 = ExtensionSettings::pluginUnplugBehavior,
                title = SettingText.Raw("Plugin Unplug Behavior"),
                subtitle = SettingText.Raw("What should happen when a plugin becomes unavailable while jobs are running"),
                icon = Icons.Default.Cable,
                options = PluginUnplugBehavior.entries,
                labelProvider = {
                    when (it) {
                        PluginUnplugBehavior.Block -> stringResource(Res.string.setting_plugin_unplug_behavior_block)
                        PluginUnplugBehavior.StopJobs -> stringResource(Res.string.setting_plugin_unplug_behavior_stop)
                    }
                },
                setValue = { s, v -> s.copy(extensions = s.extensions.copy(pluginUnplugBehavior = v)) }
            )
        }
    }
}
