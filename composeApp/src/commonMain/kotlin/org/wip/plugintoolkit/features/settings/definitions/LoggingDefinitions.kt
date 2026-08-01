package org.wip.plugintoolkit.features.settings.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.LogLevel
import org.wip.plugintoolkit.features.settings.model.LoggingSettings
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey
import org.wip.plugintoolkit.features.settings.utils.SettingText
import org.wip.plugintoolkit.features.settings.utils.SettingsRegistryBuilder
import org.wip.plugintoolkit.features.settings.utils.bindGroup
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsViewModel
import plugintoolkit.composeapp.generated.resources.*

/**
 * Registers application logging severity and retention policy settings.
 */
fun SettingsRegistryBuilder.loggingDefinitions(viewModel: SettingsViewModel) {
    nav(SettingNavKey.SystemSettings) {
        section(Res.string.section_logging) {
            bindGroup(AppSettings::logging, { copy(logging = it) }) {
                dropdown(
                    LoggingSettings::level,
                    Res.string.setting_log_level,
                    Icons.Default.List,
                    options = LogLevel.entries,
                    subtitle = SettingText.Resource(Res.string.setting_log_level_subtitle),
                    labelProvider = { it.name }
                ) { copy(level = it) }

                numeric(
                    LoggingSettings::logsToKeep,
                    Res.string.setting_logs_to_keep,
                    Icons.Default.History,
                    range = 1..30,
                    subtitle = SettingText.Resource(Res.string.setting_logs_to_keep_subtitle)
                ) { copy(logsToKeep = it) }

                switch(
                    LoggingSettings::compressOldLogs,
                    Res.string.setting_compress_old_logs,
                    Icons.Default.Compress,
                    subtitle = SettingText.Resource(Res.string.setting_compress_old_logs_subtitle)
                ) { copy(compressOldLogs = it) }

                numeric(
                    LoggingSettings::compressedLogsToKeep,
                    Res.string.setting_compressed_logs_to_keep,
                    Icons.Default.Archive,
                    range = 1..90,
                    subtitle = SettingText.Resource(Res.string.setting_compressed_logs_to_keep_subtitle),
                    enabled = { it.logging.compressOldLogs }
                ) { copy(compressedLogsToKeep = it) }
            }

            SettingAction(
                id = "logging.openFolder",
                title = Res.string.setting_open_log_folder,
                subtitle = SettingText.Resource(Res.string.setting_open_log_folder_subtitle),
                icon = Icons.Default.FolderOpen,
                onClick = { viewModel.openLogFolder() }
            )

            SettingAction(
                id = "logging.openLatest",
                title = Res.string.setting_open_latest_log,
                subtitle = SettingText.Resource(Res.string.setting_open_latest_log_subtitle),
                icon = Icons.Default.Description,
                onClick = { viewModel.openLatestLog() }
            )
        }
    }
}
