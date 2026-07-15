package org.wip.plugintoolkit.features.settings.definitions

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.notification.NotificationType
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.NotificationHistorySettings
import org.wip.plugintoolkit.features.settings.model.NotificationSettings
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey
import org.wip.plugintoolkit.features.settings.utils.SettingText
import org.wip.plugintoolkit.features.settings.utils.SettingsRegistryBuilder
import org.wip.plugintoolkit.features.settings.viewmodel.NotificationViewModel
import org.wip.plugintoolkit.shared.components.ToolkitButtonGroup
import org.wip.plugintoolkit.core.theme.ToolkitTheme

fun SettingsRegistryBuilder.notificationDefinitions(viewModel: NotificationViewModel) {
    nav(SettingNavKey.SystemSettings) {
        section(SettingText.Raw("Notifications")) {
            SettingSwitch(
                p1 = AppSettings::notifications,
                p2 = NotificationSettings::enableToasts,
                title = SettingText.Raw("Show In-App Toasts"),
                subtitle = SettingText.Raw("Display small popup messages within the application"),
                icon = Icons.Default.Notifications,
                setValue = { s, v -> s.copy(notifications = s.notifications.copy(enableToasts = v)) }
            )

            SettingSwitch(
                p1 = AppSettings::notifications,
                p2 = NotificationSettings::toastAutoDismiss,
                title = SettingText.Raw("Toast Auto-Dismiss"),
                subtitle = SettingText.Raw("Automatically hide toasts after a few seconds"),
                icon = Icons.Default.Timer,
                enabled = { it.notifications.enableToasts },
                setValue = { s, v -> s.copy(notifications = s.notifications.copy(toastAutoDismiss = v)) }
            )

            SettingNumeric(
                p1 = AppSettings::notifications,
                p2 = NotificationSettings::toastDismissTime,
                title = SettingText.Raw("Toast Duration (Seconds)"),
                icon = Icons.Default.Timer,
                enabled = { it.notifications.enableToasts && it.notifications.toastAutoDismiss },
                valueRange = 1..20,
                setValue = { s, v -> s.copy(notifications = s.notifications.copy(toastDismissTime = v)) }
            )

            SettingSwitch(
                p1 = AppSettings::notifications,
                p2 = NotificationSettings::enableSystemNotifications,
                title = SettingText.Raw("System Notifications"),
                subtitle = SettingText.Raw("Send notifications to the operating system notification center"),
                icon = Icons.Default.NotificationsActive,
                setValue = { s, v -> s.copy(notifications = s.notifications.copy(enableSystemNotifications = v)) }
            )

            SettingNumeric(
                p1 = AppSettings::notifications,
                p2 = NotificationSettings::history,
                p3 = NotificationHistorySettings::retentionDays,
                title = SettingText.Raw("History Retention (Days)"),
                subtitle = SettingText.Raw("How long to keep notification history (max 30 days)"),
                icon = Icons.Default.History,
                valueRange = 1..30,
                setValue = { s, v ->
                    s.copy(
                        notifications = s.notifications.copy(
                            history = s.notifications.history.copy(retentionDays = v)
                        )
                    )
                }
            )

            SettingCustom(
                id = "notifications.test",
                title = SettingText.Raw("Test Notifications"),
                subtitle = SettingText.Raw("Trigger test notifications to verify behavior"),
                icon = Icons.Default.BugReport,
                control = { _, _ ->
                    ToolkitButtonGroup {
                        item { shape, modifierSpec ->
                            FilledTonalIconButton(
                                onClick = { viewModel.testSystemNotification(NotificationType.Info) },
                                shape = shape,
                                modifier = modifierSpec.size(ToolkitTheme.dimensions.menuItem)
                            ) {
                                Icon(
                                    Icons.Default.Info,
                                    contentDescription = "Info",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        item { shape, modifierSpec ->
                            FilledTonalIconButton(
                                onClick = { viewModel.testSystemNotification(NotificationType.Warning) },
                                shape = shape,
                                modifier = modifierSpec.size(ToolkitTheme.dimensions.menuItem)
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = androidx.compose.ui.graphics.Color(0xFFFFA500),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        item { shape, modifierSpec ->
                            FilledTonalIconButton(
                                onClick = { viewModel.testSystemNotification(NotificationType.Error) },
                                shape = shape,
                                modifier = modifierSpec.size(ToolkitTheme.dimensions.menuItem)
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        item { shape, modifierSpec ->
                            FilledTonalIconButton(
                                onClick = { viewModel.testToastNotification() },
                                shape = shape,
                                modifier = modifierSpec.size(ToolkitTheme.dimensions.menuItem)
                            ) {
                                Icon(
                                    Icons.Default.ChatBubble,
                                    contentDescription = "Toast",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}
