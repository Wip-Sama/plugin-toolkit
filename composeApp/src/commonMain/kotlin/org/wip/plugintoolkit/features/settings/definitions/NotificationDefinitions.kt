package org.wip.plugintoolkit.features.settings.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Timer
import org.wip.plugintoolkit.features.settings.model.*
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey
import org.wip.plugintoolkit.features.settings.utils.*
import org.wip.plugintoolkit.features.settings.viewmodel.NotificationViewModel
import plugintoolkit.composeapp.generated.resources.*
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ChatBubble
import org.wip.plugintoolkit.core.notification.NotificationType

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
                    Row {
                        IconButton(onClick = { viewModel.testSystemNotification(NotificationType.Info) }) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Info",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { viewModel.testSystemNotification(NotificationType.Warning) }) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = androidx.compose.ui.graphics.Color(0xFFFFA500)
                            )
                        }
                        IconButton(onClick = { viewModel.testSystemNotification(NotificationType.Error) }) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = "Error",
                                tint = MaterialTheme.colorScheme.error
                            )
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
