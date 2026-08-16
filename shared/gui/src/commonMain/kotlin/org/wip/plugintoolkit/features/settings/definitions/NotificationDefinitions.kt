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
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.notification.NotificationType
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.NotificationHistorySettings
import org.wip.plugintoolkit.features.settings.model.NotificationSettings
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey
import org.wip.plugintoolkit.features.settings.utils.SettingText
import org.wip.plugintoolkit.features.settings.utils.SettingsRegistryBuilder
import org.wip.plugintoolkit.features.settings.utils.bindGroup
import org.wip.plugintoolkit.features.settings.viewmodel.NotificationViewModel
import org.wip.plugintoolkit.shared.components.ToolkitButtonGroup
import plugintoolkit.composeapp.generated.resources.*

/**
 * Registers notification preferences, toast durations, filters, and notification history settings.
 */
fun SettingsRegistryBuilder.notificationDefinitions(viewModel: NotificationViewModel) {
    nav(SettingNavKey.SystemSettings) {
        section(Res.string.section_notifications) {
            bindGroup(AppSettings::notifications, { copy(notifications = it) }) {
                switch(
                    NotificationSettings::enableToasts,
                    Res.string.setting_enable_toasts,
                    Icons.Default.Notifications,
                    subtitle = SettingText.Raw("Display small popup messages within the application")
                ) { copy(enableToasts = it) }

                switch(
                    NotificationSettings::toastAutoDismiss,
                    Res.string.setting_toast_auto_dismiss,
                    Icons.Default.Timer,
                    subtitle = SettingText.Raw("Automatically hide toasts after a few seconds"),
                    enabled = { it.notifications.enableToasts }
                ) { copy(toastAutoDismiss = it) }

                numeric(
                    NotificationSettings::toastDismissTime,
                    Res.string.setting_toast_dismiss_time,
                    Icons.Default.Timer,
                    range = 1..20,
                    enabled = { it.notifications.enableToasts && it.notifications.toastAutoDismiss }
                ) { copy(toastDismissTime = it) }

                switch(
                    NotificationSettings::enableSystemNotifications,
                    Res.string.setting_enable_system_notifications,
                    Icons.Default.NotificationsActive,
                    subtitle = SettingText.Raw("Send notifications to the operating system notification center")
                ) { copy(enableSystemNotifications = it) }

                switch(
                    NotificationSettings::showInfo,
                    Res.string.setting_show_info,
                    Icons.Default.Info,
                    enabled = { it.notifications.enableSystemNotifications }
                ) { copy(showInfo = it) }

                switch(
                    NotificationSettings::showWarning,
                    Res.string.setting_show_warning,
                    Icons.Default.Warning,
                    enabled = { it.notifications.enableSystemNotifications }
                ) { copy(showWarning = it) }

                switch(
                    NotificationSettings::showError,
                    Res.string.setting_show_error,
                    Icons.Default.Error,
                    enabled = { it.notifications.enableSystemNotifications }
                ) { copy(showError = it) }
            }

            SettingNumeric(
                p1 = AppSettings::notifications,
                p2 = NotificationSettings::history,
                p3 = NotificationHistorySettings::retentionDays,
                title = Res.string.setting_history_retention,
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
                                    contentDescription = stringResource(Res.string.action_info),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumLarge)
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
                                    contentDescription = stringResource(Res.string.common_warning),
                                    tint = ToolkitTheme.colors.warning,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumLarge)
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
                                    contentDescription = stringResource(Res.string.common_error),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumLarge)
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
                                    contentDescription = stringResource(Res.string.common_toast),
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}
