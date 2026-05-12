package org.wip.plugintoolkit.features.settings.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import org.wip.plugintoolkit.features.settings.model.*
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey
import org.wip.plugintoolkit.features.settings.utils.*
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsViewModel

fun SettingsRegistryBuilder.loggingDefinitions(viewModel: SettingsViewModel) {
    nav(SettingNavKey.SystemSettings) {
        section(SettingText.Raw("Logging")) {
            SettingDropdown(
                p1 = AppSettings::logging,
                p2 = LoggingSettings::level,
                title = SettingText.Raw("Log Level"),
                subtitle = SettingText.Raw("Minimum severity level to record in logs"),
                icon = Icons.Default.List,
                options = LogLevel.entries,
                labelProvider = { it.name },
                setValue = { s, v -> s.copy(logging = s.logging.copy(level = v)) }
            )

            SettingNumeric(
                p1 = AppSettings::logging,
                p2 = LoggingSettings::logsToKeep,
                title = SettingText.Raw("Logs to Keep"),
                subtitle = SettingText.Raw("Number of historical log files to retain on disk"),
                icon = Icons.Default.History,
                valueRange = 1..30,
                setValue = { s, v -> s.copy(logging = s.logging.copy(logsToKeep = v)) }
            )

            SettingSwitch(
                p1 = AppSettings::logging,
                p2 = LoggingSettings::compressOldLogs,
                title = SettingText.Raw("Compress Old Logs"),
                subtitle = SettingText.Raw("Reduce disk usage by zipping historical log files"),
                icon = Icons.Default.Compress,
                setValue = { s, v -> s.copy(logging = s.logging.copy(compressOldLogs = v)) }
            )

            SettingAction(
                id = "logging.openFolder",
                title = SettingText.Raw("Open Log Folder"),
                subtitle = SettingText.Raw("Open the directory containing all log files"),
                icon = Icons.Default.FolderOpen,
                onClick = { viewModel.openLogFolder() }
            )

            SettingAction(
                id = "logging.openLatest",
                title = SettingText.Raw("Open Latest Log"),
                subtitle = SettingText.Raw("Directly open the most recent log file"),
                icon = Icons.Default.Description,
                onClick = { viewModel.openLatestLog() }
            )
        }
    }
}
