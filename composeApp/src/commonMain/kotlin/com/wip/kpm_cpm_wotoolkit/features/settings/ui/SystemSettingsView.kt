package com.wip.kpm_cpm_wotoolkit.features.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wip.kpm_cpm_wotoolkit.features.settings.viewmodel.SettingsViewModel
import kpm_cpm_wotoolkit.composeapp.generated.resources.Res
import kpm_cpm_wotoolkit.composeapp.generated.resources.*
import com.wip.kpm_cpm_wotoolkit.shared.components.settings.*
import org.jetbrains.compose.resources.stringResource
import com.wip.kpm_cpm_wotoolkit.core.utils.StartupManager
import com.wip.kpm_cpm_wotoolkit.features.settings.model.LogLevel

@Composable
fun SystemSettingsView(viewModel: SettingsViewModel) {
    val general = viewModel.settings.general

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SettingsGroup(title = stringResource(Res.string.section_system)) {
            
            SettingsItem(
                title = stringResource(Res.string.setting_launch_at_startup),
                subtitle = stringResource(Res.string.setting_launch_at_startup_subtitle),
                icon = Icons.Default.RocketLaunch,
                control = {
                    SettingsSwitch(
                        checked = general.launchAtStartup,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings {
                                it.copy(general = it.general.copy(launchAtStartup = checked))
                            }
                            // Apply the change immediately to the OS
                            StartupManager.setLaunchAtStartup(checked, general.launchMinimizedAtStartup)
                        }
                    )
                }
            )

            SettingsItem(
                title = stringResource(Res.string.setting_launch_minimized_at_startup),
                subtitle = stringResource(Res.string.setting_launch_minimized_at_startup_subtitle),
                icon = Icons.Default.Input,
                enabled = general.launchAtStartup,
                control = {
                    SettingsSwitch(
                        checked = general.launchMinimizedAtStartup,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings {
                                it.copy(general = it.general.copy(launchMinimizedAtStartup = checked))
                            }
                            // Update the startup command if enabled
                            if (general.launchAtStartup) {
                                StartupManager.setLaunchAtStartup(true, checked)
                            }
                        },
                    )
                }
            )

            SettingsItem(
                title = stringResource(Res.string.setting_start_minimized),
                subtitle = stringResource(Res.string.setting_start_minimized_subtitle),
                icon = Icons.Default.Minimize,
                control = {
                    SettingsSwitch(
                        checked = general.startMinimized,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings {
                                it.copy(general = it.general.copy(startMinimized = checked))
                            }
                        }
                    )
                }
            )

            SettingsItem(
                title = stringResource(Res.string.setting_close_to_tray),
                subtitle = stringResource(Res.string.setting_close_to_tray_subtitle),
                icon = Icons.Default.SettingsInputComponent, // Or any relevant icon
                control = {
                    SettingsSwitch(
                        checked = general.closeToTray,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings {
                                it.copy(general = it.general.copy(closeToTray = checked))
                            }
                        }
                    )
                }
            )
        }
        
        SettingsGroup(title = stringResource(Res.string.section_logging)) {
            val logging = viewModel.settings.logging

            SettingsItem(
                title = stringResource(Res.string.setting_log_level),
                subtitle = stringResource(Res.string.setting_log_level_subtitle),
                icon = Icons.Default.BugReport,
                control = {
                    SettingsDropdown(
                        options = LogLevel.entries.map { it.name },
                        selectedOption = logging.level.name,
                        labelProvider = { it },
                        onOptionSelected = { name ->
                            viewModel.updateSettings {
                                it.copy(logging = it.logging.copy(level = LogLevel.valueOf(name)))
                            }
                        }
                    )
                }
            )

            SettingsItem(
                title = stringResource(Res.string.setting_logs_to_keep),
                subtitle = stringResource(Res.string.setting_logs_to_keep_subtitle),
                icon = Icons.Default.CalendarToday,
                control = {
                    SettingsNumericInput(
                        value = logging.logsToKeep,
                        onValueChange = { value ->
                            viewModel.updateSettings {
                                it.copy(logging = it.logging.copy(logsToKeep = value))
                            }
                        },
                        valueRange = 1..30
                    )
                }
            )

            SettingsItem(
                title = stringResource(Res.string.setting_compress_old_logs),
                subtitle = stringResource(Res.string.setting_compress_old_logs_subtitle),
                icon = Icons.Default.Compress,
                control = {
                    SettingsSwitch(
                        checked = logging.compressOldLogs,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings {
                                it.copy(logging = it.logging.copy(compressOldLogs = checked))
                            }
                        }
                    )
                }
            )

            SettingsItem(
                title = stringResource(Res.string.setting_compressed_logs_to_keep),
                subtitle = stringResource(Res.string.setting_compressed_logs_to_keep_subtitle),
                icon = Icons.Default.Storage,
                enabled = logging.compressOldLogs,
                control = {
                    SettingsNumericInput(
                        value = logging.compressedLogsToKeep,
                        onValueChange = { value ->
                            viewModel.updateSettings {
                                it.copy(logging = it.logging.copy(compressedLogsToKeep = value))
                            }
                        },
                        valueRange = 1..100
                    )
                }
            )

            SettingsItem(
                title = stringResource(Res.string.setting_open_log_folder),
                subtitle = stringResource(Res.string.setting_open_log_folder_subtitle),
                icon = Icons.Default.FolderOpen,
                onClick = {
                    viewModel.openLogFolder()
                }
            )
        }

        SettingsGroup(title = stringResource(Res.string.section_toasts)) {
            val notifications = viewModel.settings.notifications

            SettingsItem(
                title = stringResource(Res.string.setting_enable_toasts),
                icon = Icons.Default.ChatBubble,
                control = {
                    SettingsSwitch(
                        checked = notifications.enableToasts,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings {
                                it.copy(notifications = it.notifications.copy(enableToasts = checked))
                            }
                        }
                    )
                }
            )

            SettingsItem(
                title = stringResource(Res.string.setting_toast_auto_dismiss),
                icon = Icons.Default.Timer,
                enabled = notifications.enableToasts,
                control = {
                    SettingsSwitch(
                        checked = notifications.toastAutoDismiss,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings {
                                it.copy(notifications = it.notifications.copy(toastAutoDismiss = checked))
                            }
                        }
                    )
                }
            )

            SettingsItem(
                title = stringResource(Res.string.setting_toast_dismiss_time),
                icon = Icons.Default.AvTimer,
                enabled = notifications.enableToasts && notifications.toastAutoDismiss,
                control = {
                    SettingsNumericInput(
                        value = notifications.toastDismissTime,
                        onValueChange = { value ->
                            viewModel.updateSettings {
                                it.copy(notifications = it.notifications.copy(toastDismissTime = value))
                            }
                        },
                        valueRange = 1..10
                    )
                }
            )
        }

        SettingsGroup(title = stringResource(Res.string.section_notifications)) {
            val notifications = viewModel.settings.notifications

            SettingsItem(
                title = stringResource(Res.string.setting_enable_system_notifications),
                icon = Icons.Default.Notifications,
                control = {
                    SettingsSwitch(
                        checked = notifications.enableSystemNotifications,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings {
                                it.copy(notifications = it.notifications.copy(enableSystemNotifications = checked))
                            }
                        }
                    )
                }
            )

            SettingsItem(
                title = stringResource(Res.string.setting_show_info),
                icon = Icons.Default.Info,
                enabled = notifications.enableSystemNotifications,
                control = {
                    SettingsSwitch(
                        checked = notifications.showInfo,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings {
                                it.copy(notifications = it.notifications.copy(showInfo = checked))
                            }
                        }
                    )
                }
            )

            SettingsItem(
                title = stringResource(Res.string.setting_show_warning),
                icon = Icons.Default.Warning,
                enabled = notifications.enableSystemNotifications,
                control = {
                    SettingsSwitch(
                        checked = notifications.showWarning,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings {
                                it.copy(notifications = it.notifications.copy(showWarning = checked))
                            }
                        }
                    )
                }
            )

            SettingsItem(
                title = stringResource(Res.string.setting_show_error),
                icon = Icons.Default.Error,
                enabled = notifications.enableSystemNotifications,
                control = {
                    SettingsSwitch(
                        checked = notifications.showError,
                        onCheckedChange = { checked ->
                            viewModel.updateSettings {
                                it.copy(notifications = it.notifications.copy(showError = checked))
                            }
                        }
                    )
                }
            )

            SettingsItem(
                title = stringResource(Res.string.setting_history_retention),
                icon = Icons.Default.History,
                control = {
                    SettingsNumericInput(
                        value = notifications.history.retentionDays,
                        onValueChange = { value ->
                            viewModel.updateSettings {
                                it.copy(notifications = it.notifications.copy(
                                    history = it.notifications.history.copy(retentionDays = value)
                                ))
                            }
                        },
                        valueRange = 1..30
                    )
                }
            )
            
            SettingsItem(
                title = "Test Notifications",
                subtitle = "Trigger test notifications to verify behavior",
                icon = Icons.Default.BugReport,
                control = {
                    Row {
                        IconButton(onClick = { viewModel.testSystemNotification(com.wip.kpm_cpm_wotoolkit.core.notification.NotificationType.Info) }) {
                            Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = { viewModel.testSystemNotification(com.wip.kpm_cpm_wotoolkit.core.notification.NotificationType.Warning) }) {
                            Icon(Icons.Default.Warning, contentDescription = "Warning", tint = androidx.compose.ui.graphics.Color(0xFFFFA500))
                        }
                        IconButton(onClick = { viewModel.testSystemNotification(com.wip.kpm_cpm_wotoolkit.core.notification.NotificationType.Error) }) {
                            Icon(Icons.Default.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                        }
                        IconButton(onClick = { viewModel.testToastNotification() }) {
                            Icon(Icons.Default.ChatBubble, contentDescription = "Toast")
                        }
                    }
                }
            )
        }
    }
}
