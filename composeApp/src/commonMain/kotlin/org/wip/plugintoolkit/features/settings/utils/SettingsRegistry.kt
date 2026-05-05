package org.wip.plugintoolkit.features.settings.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.utils.StartupManager
import org.wip.plugintoolkit.features.colorpicker.model.ColorPickerType
import org.wip.plugintoolkit.features.colorpicker.ui.ColorPickerDialog
import org.wip.plugintoolkit.features.settings.model.AppLanguage
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.AppTheme
import org.wip.plugintoolkit.features.settings.model.LogLevel
import org.wip.plugintoolkit.features.settings.model.PluginUnplugBehavior
import org.wip.plugintoolkit.features.settings.model.SettingDefinition
import org.wip.plugintoolkit.features.settings.model.WindowStartMode
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.section_jobs
import plugintoolkit.composeapp.generated.resources.section_localization
import plugintoolkit.composeapp.generated.resources.section_logging
import plugintoolkit.composeapp.generated.resources.section_notifications
import plugintoolkit.composeapp.generated.resources.section_plugins
import plugintoolkit.composeapp.generated.resources.section_system
import plugintoolkit.composeapp.generated.resources.section_toasts
import plugintoolkit.composeapp.generated.resources.setting_accent_color
import plugintoolkit.composeapp.generated.resources.setting_appearance
import plugintoolkit.composeapp.generated.resources.setting_close_to_tray
import plugintoolkit.composeapp.generated.resources.setting_close_to_tray_subtitle
import plugintoolkit.composeapp.generated.resources.setting_compress_old_logs
import plugintoolkit.composeapp.generated.resources.setting_compress_old_logs_subtitle
import plugintoolkit.composeapp.generated.resources.setting_compressed_logs_to_keep
import plugintoolkit.composeapp.generated.resources.setting_compressed_logs_to_keep_subtitle
import plugintoolkit.composeapp.generated.resources.setting_enable_system_notifications
import plugintoolkit.composeapp.generated.resources.setting_enable_toasts
import plugintoolkit.composeapp.generated.resources.setting_history_retention
import plugintoolkit.composeapp.generated.resources.setting_history_retention_days
import plugintoolkit.composeapp.generated.resources.setting_history_retention_days_subtitle
import plugintoolkit.composeapp.generated.resources.setting_language
import plugintoolkit.composeapp.generated.resources.setting_launch_at_startup
import plugintoolkit.composeapp.generated.resources.setting_launch_at_startup_subtitle
import plugintoolkit.composeapp.generated.resources.setting_launch_minimized_at_startup
import plugintoolkit.composeapp.generated.resources.setting_launch_minimized_at_startup_subtitle
import plugintoolkit.composeapp.generated.resources.setting_log_level
import plugintoolkit.composeapp.generated.resources.setting_log_level_subtitle
import plugintoolkit.composeapp.generated.resources.setting_logs_to_keep
import plugintoolkit.composeapp.generated.resources.setting_logs_to_keep_subtitle
import plugintoolkit.composeapp.generated.resources.setting_max_concurrent_jobs
import plugintoolkit.composeapp.generated.resources.setting_max_concurrent_jobs_subtitle
import plugintoolkit.composeapp.generated.resources.setting_open_log_folder
import plugintoolkit.composeapp.generated.resources.setting_open_log_folder_subtitle
import plugintoolkit.composeapp.generated.resources.setting_plugin_unplug_behavior
import plugintoolkit.composeapp.generated.resources.setting_plugin_unplug_behavior_block
import plugintoolkit.composeapp.generated.resources.setting_plugin_unplug_behavior_stop
import plugintoolkit.composeapp.generated.resources.setting_plugin_unplug_behavior_subtitle
import plugintoolkit.composeapp.generated.resources.setting_save_job_history
import plugintoolkit.composeapp.generated.resources.setting_save_job_history_subtitle
import plugintoolkit.composeapp.generated.resources.setting_scaling
import plugintoolkit.composeapp.generated.resources.setting_show_error
import plugintoolkit.composeapp.generated.resources.setting_show_info
import plugintoolkit.composeapp.generated.resources.setting_show_warning
import plugintoolkit.composeapp.generated.resources.setting_theme
import plugintoolkit.composeapp.generated.resources.setting_timezone
import plugintoolkit.composeapp.generated.resources.setting_toast_auto_dismiss
import plugintoolkit.composeapp.generated.resources.setting_toast_dismiss_time
import plugintoolkit.composeapp.generated.resources.setting_use_system_language
import plugintoolkit.composeapp.generated.resources.setting_use_system_language_subtitle
import plugintoolkit.composeapp.generated.resources.setting_window_start_mode
import plugintoolkit.composeapp.generated.resources.setting_window_start_mode_subtitle

/**
 * An abstraction to handle both Compose StringResources (for static UI)
 * and plain Strings (for dynamic plugin settings).
 */
sealed interface SettingText {
    data class Resource(val res: StringResource) : SettingText
    data class Raw(val text: String) : SettingText
}

@Composable
fun SettingText.resolve(): String {
    return when (this) {
        is SettingText.Resource -> stringResource(res)
        is SettingText.Raw -> text
    }
}


class SettingsRegistry {
    private val _definitions = MutableStateFlow<List<SettingDefinition>>(emptyList())
    val definitions: StateFlow<List<SettingDefinition>> = _definitions.asStateFlow()


    /** Side-effect callbacks keyed by SettingDefinition identity (hashCode) */
    private val sideEffects = mutableMapOf<Int, (AppSettings) -> Unit>()

    init {
        registerDefaultSettings()
    }

    // ── Public API ──────────────────────────────────────────────────────

    fun register(newDefinitions: List<SettingDefinition>) {
        _definitions.value += newDefinitions
    }

    fun registerWithSideEffect(definition: SettingDefinition, sideEffect: (AppSettings) -> Unit) {
        sideEffects[definition.hashCode()] = sideEffect
        register(listOf(definition))
    }

    fun getSideEffect(definition: SettingDefinition): ((AppSettings) -> Unit)? {
        return sideEffects[definition.hashCode()]
    }

    fun unregister(navKey: SettingNavKey) {
        val toRemove = _definitions.value.filter { it.navKey == navKey }
        toRemove.forEach { sideEffects.remove(it.hashCode()) }
        _definitions.value = _definitions.value.filter { it.navKey != navKey }
    }

    fun clear() {
        _definitions.value = emptyList()
        sideEffects.clear()
    }

    /** Get all definitions for a specific nav key, grouped by section */
    fun getDefinitionsForPage(navKey: SettingNavKey): Map<SettingText, List<SettingDefinition>> {
        return _definitions.value
            .filter { it.navKey == navKey }
            .groupBy { it.sectionTitle }
    }


    // ── Default settings registration ───────────────────────────────────

    private fun registerDefaultSettings() {
        val appearanceSection = SettingText.Resource(Res.string.setting_appearance)
        val localizationSection = SettingText.Resource(Res.string.section_localization)
        val systemSection = SettingText.Resource(Res.string.section_system)
        val loggingSection = SettingText.Resource(Res.string.section_logging)
        val toastsSection = SettingText.Resource(Res.string.section_toasts)
        val notificationsSection = SettingText.Resource(Res.string.section_notifications)
        val jobsSection = SettingText.Resource(Res.string.section_jobs)
        val pluginsSection = SettingText.Resource(Res.string.section_plugins)

        val definitions = listOf(
            // ── Appearance ───────────────────────────────────────────────
            SettingDefinition.DropdownSetting(
                title = SettingText.Resource(Res.string.setting_theme),
                subtitle = SettingText.Raw("Choose between System, Light, Dark or Amoled"),
                icon = Icons.Default.Brightness6,
                sectionTitle = appearanceSection,
                navKey = SettingNavKey.Appearance,
                options = AppTheme.entries,
                getValue = { it.appearance.theme },
                setValue = { s, v -> s.copy(appearance = s.appearance.copy(theme = v)) },
                labelProvider = { it.name }
            ),

            SettingDefinition.SwitchSetting(
                title = SettingText.Raw("Follow System Accent"),
                subtitle = SettingText.Raw("Automatically use the accent color from your operating system"),
                icon = Icons.Default.AutoFixHigh,
                sectionTitle = appearanceSection,
                navKey = SettingNavKey.Appearance,
                getValue = { it.appearance.followSystemAccent },
                setValue = { s, v -> s.copy(appearance = s.appearance.copy(followSystemAccent = v)) }
            ),

            SettingDefinition.CustomSetting(
                title = SettingText.Resource(Res.string.setting_accent_color),
                subtitle = SettingText.Raw("Manually select the accent color for the application"),
                icon = Icons.Default.ColorLens,
                sectionTitle = appearanceSection,
                navKey = SettingNavKey.Appearance,
                enabled = { !it.appearance.followSystemAccent },
                control = { settings, update ->
                    var showColorPicker by remember { mutableStateOf(false) }

                    ColorPickerDialog(
                        show = showColorPicker,
                        initialType = ColorPickerType.Classic(),
                        onDismissRequest = { showColorPicker = false },
                        onPickedColor = { color ->
                            update(
                                settings.copy(
                                    appearance = settings.appearance.copy(
                                        accentColor = color.toArgb().toLong()
                                    )
                                )
                            )
                            showColorPicker = false
                        }
                    )

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(settings.appearance.accentColor))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable(enabled = !settings.appearance.followSystemAccent) {
                                showColorPicker = true
                            }
                    )
                }
            ),

            SettingDefinition.SliderSetting(
                title = SettingText.Resource(Res.string.setting_scaling),
                subtitle = null,
                icon = Icons.Default.AspectRatio,
                sectionTitle = appearanceSection,
                navKey = SettingNavKey.Appearance,
                getValue = { it.general.scaling },
                setValue = { s, v -> s.copy(general = s.general.copy(scaling = v)) },
                valueRange = 0.5f..2.0f,
                steps = 5,
                subtitleProvider = { "${(it.general.scaling * 100).toInt()}%" }
            ),

            // ── Localization ─────────────────────────────────────────────
            SettingDefinition.SwitchSetting(
                title = SettingText.Resource(Res.string.setting_use_system_language),
                subtitle = SettingText.Resource(Res.string.setting_use_system_language_subtitle),
                icon = Icons.Default.Language,
                sectionTitle = localizationSection,
                navKey = SettingNavKey.Appearance,
                getValue = { it.localization.useSystemLanguage },
                setValue = { s, v -> s.copy(localization = s.localization.copy(useSystemLanguage = v)) }
            ),

            SettingDefinition.DropdownSetting(
                title = SettingText.Resource(Res.string.setting_language),
                subtitle = SettingText.Raw("Select your preferred language"),
                icon = Icons.Default.Language,
                sectionTitle = localizationSection,
                navKey = SettingNavKey.Appearance,
                enabled = { !it.localization.useSystemLanguage },
                options = AppLanguage.entries,
                getValue = { it.localization.language },
                setValue = { s, v -> s.copy(localization = s.localization.copy(language = v)) },
                labelProvider = { it.label }
            ),

            SettingDefinition.SwitchSetting(
                title = SettingText.Raw("Use System Timezone"),
                subtitle = SettingText.Raw("Automatically detect your local timezone from the system"),
                icon = Icons.Default.Map,
                sectionTitle = localizationSection,
                navKey = SettingNavKey.Appearance,
                getValue = { it.localization.useSystemTimezone },
                setValue = { s, v -> s.copy(localization = s.localization.copy(useSystemTimezone = v)) }
            ),

            SettingDefinition.DropdownSetting(
                title = SettingText.Resource(Res.string.setting_timezone),
                subtitle = SettingText.Raw("Manual timezone selection"),
                icon = Icons.Default.Schedule,
                sectionTitle = localizationSection,
                navKey = SettingNavKey.Appearance,
                enabled = { !it.localization.useSystemTimezone },
                options = TimezoneUtils.getAvailableZoneIds(),
                getValue = { s ->
                    if (s.localization.useSystemTimezone) TimezoneUtils.getSystemDefaultId()
                    else s.localization.timezone
                },
                setValue = { s, v -> s.copy(localization = s.localization.copy(timezone = v)) },
                labelProvider = { it }
            ),

            // ── System ──────────────────────────────────────────────────
            SettingDefinition.SwitchSetting(
                title = SettingText.Resource(Res.string.setting_launch_at_startup),
                subtitle = SettingText.Resource(Res.string.setting_launch_at_startup_subtitle),
                icon = Icons.Default.RocketLaunch,
                sectionTitle = systemSection,
                navKey = SettingNavKey.SystemSettings,
                getValue = { it.general.launchAtStartup },
                setValue = { s, v -> s.copy(general = s.general.copy(launchAtStartup = v)) }
            ),

            SettingDefinition.SwitchSetting(
                title = SettingText.Resource(Res.string.setting_launch_minimized_at_startup),
                subtitle = SettingText.Resource(Res.string.setting_launch_minimized_at_startup_subtitle),
                icon = Icons.Default.Input,
                sectionTitle = systemSection,
                navKey = SettingNavKey.SystemSettings,
                enabled = { it.general.launchAtStartup },
                getValue = { it.general.launchMinimizedAtStartup },
                setValue = { s, v -> s.copy(general = s.general.copy(launchMinimizedAtStartup = v)) }
            ),

            SettingDefinition.DropdownSetting(
                title = SettingText.Resource(Res.string.setting_window_start_mode),
                subtitle = SettingText.Resource(Res.string.setting_window_start_mode_subtitle),
                icon = Icons.Default.Minimize,
                sectionTitle = systemSection,
                navKey = SettingNavKey.SystemSettings,
                options = WindowStartMode.entries,
                getValue = { it.general.windowStartMode },
                setValue = { s, v -> s.copy(general = s.general.copy(windowStartMode = v)) },
                labelProvider = { it.name }
            ),

            SettingDefinition.SwitchSetting(
                title = SettingText.Resource(Res.string.setting_close_to_tray),
                subtitle = SettingText.Resource(Res.string.setting_close_to_tray_subtitle),
                icon = Icons.Default.SettingsInputComponent,
                sectionTitle = systemSection,
                navKey = SettingNavKey.SystemSettings,
                getValue = { it.general.closeToTray },
                setValue = { s, v -> s.copy(general = s.general.copy(closeToTray = v)) }
            ),

            // ── Logging ──────────────────────────────────────────────────
            SettingDefinition.DropdownSetting(
                title = SettingText.Resource(Res.string.setting_log_level),
                subtitle = SettingText.Resource(Res.string.setting_log_level_subtitle),
                icon = Icons.Default.BugReport,
                sectionTitle = loggingSection,
                navKey = SettingNavKey.SystemSettings,
                options = LogLevel.entries.map { it.name },
                getValue = { it.logging.level.name },
                setValue = { s, v -> s.copy(logging = s.logging.copy(level = LogLevel.valueOf(v))) },
                labelProvider = { it }
            ),

            SettingDefinition.NumericSetting(
                title = SettingText.Resource(Res.string.setting_logs_to_keep),
                subtitle = SettingText.Resource(Res.string.setting_logs_to_keep_subtitle),
                icon = Icons.Default.CalendarToday,
                sectionTitle = loggingSection,
                navKey = SettingNavKey.SystemSettings,
                getValue = { it.logging.logsToKeep },
                setValue = { s, v -> s.copy(logging = s.logging.copy(logsToKeep = v)) },
                valueRange = 1..30
            ),

            SettingDefinition.SwitchSetting(
                title = SettingText.Resource(Res.string.setting_compress_old_logs),
                subtitle = SettingText.Resource(Res.string.setting_compress_old_logs_subtitle),
                icon = Icons.Default.Compress,
                sectionTitle = loggingSection,
                navKey = SettingNavKey.SystemSettings,
                getValue = { it.logging.compressOldLogs },
                setValue = { s, v -> s.copy(logging = s.logging.copy(compressOldLogs = v)) }
            ),

            SettingDefinition.NumericSetting(
                title = SettingText.Resource(Res.string.setting_compressed_logs_to_keep),
                subtitle = SettingText.Resource(Res.string.setting_compressed_logs_to_keep_subtitle),
                icon = Icons.Default.Storage,
                sectionTitle = loggingSection,
                navKey = SettingNavKey.SystemSettings,
                enabled = { it.logging.compressOldLogs },
                getValue = { it.logging.compressedLogsToKeep },
                setValue = { s, v -> s.copy(logging = s.logging.copy(compressedLogsToKeep = v)) },
                valueRange = 1..100
            ),

            SettingDefinition.ActionSetting(
                title = SettingText.Resource(Res.string.setting_open_log_folder),
                subtitle = SettingText.Resource(Res.string.setting_open_log_folder_subtitle),
                icon = Icons.Default.FolderOpen,
                sectionTitle = loggingSection,
                navKey = SettingNavKey.SystemSettings,
                onClick = {} // Will be wired via side-effect at screen level
            ),

            // ── Toasts ───────────────────────────────────────────────────
            SettingDefinition.SwitchSetting(
                title = SettingText.Resource(Res.string.setting_enable_toasts),
                subtitle = null,
                icon = Icons.Default.ChatBubble,
                sectionTitle = toastsSection,
                navKey = SettingNavKey.SystemSettings,
                getValue = { it.notifications.enableToasts },
                setValue = { s, v -> s.copy(notifications = s.notifications.copy(enableToasts = v)) }
            ),

            SettingDefinition.SwitchSetting(
                title = SettingText.Resource(Res.string.setting_toast_auto_dismiss),
                subtitle = null,
                icon = Icons.Default.Timer,
                sectionTitle = toastsSection,
                navKey = SettingNavKey.SystemSettings,
                enabled = { it.notifications.enableToasts },
                getValue = { it.notifications.toastAutoDismiss },
                setValue = { s, v -> s.copy(notifications = s.notifications.copy(toastAutoDismiss = v)) }
            ),

            SettingDefinition.NumericSetting(
                title = SettingText.Resource(Res.string.setting_toast_dismiss_time),
                subtitle = null,
                icon = Icons.Default.AvTimer,
                sectionTitle = toastsSection,
                navKey = SettingNavKey.SystemSettings,
                enabled = { it.notifications.enableToasts && it.notifications.toastAutoDismiss },
                getValue = { it.notifications.toastDismissTime },
                setValue = { s, v -> s.copy(notifications = s.notifications.copy(toastDismissTime = v)) },
                valueRange = 1..10
            ),

            // ── Notifications ────────────────────────────────────────────
            SettingDefinition.SwitchSetting(
                title = SettingText.Resource(Res.string.setting_enable_system_notifications),
                subtitle = null,
                icon = Icons.Default.Notifications,
                sectionTitle = notificationsSection,
                navKey = SettingNavKey.SystemSettings,
                getValue = { it.notifications.enableSystemNotifications },
                setValue = { s, v -> s.copy(notifications = s.notifications.copy(enableSystemNotifications = v)) }
            ),

            SettingDefinition.SwitchSetting(
                title = SettingText.Resource(Res.string.setting_show_info),
                subtitle = null,
                icon = Icons.Default.Info,
                sectionTitle = notificationsSection,
                navKey = SettingNavKey.SystemSettings,
                enabled = { it.notifications.enableSystemNotifications },
                getValue = { it.notifications.showInfo },
                setValue = { s, v -> s.copy(notifications = s.notifications.copy(showInfo = v)) }
            ),

            SettingDefinition.SwitchSetting(
                title = SettingText.Resource(Res.string.setting_show_warning),
                subtitle = null,
                icon = Icons.Default.Warning,
                sectionTitle = notificationsSection,
                navKey = SettingNavKey.SystemSettings,
                enabled = { it.notifications.enableSystemNotifications },
                getValue = { it.notifications.showWarning },
                setValue = { s, v -> s.copy(notifications = s.notifications.copy(showWarning = v)) }
            ),

            SettingDefinition.SwitchSetting(
                title = SettingText.Resource(Res.string.setting_show_error),
                subtitle = null,
                icon = Icons.Default.Error,
                sectionTitle = notificationsSection,
                navKey = SettingNavKey.SystemSettings,
                enabled = { it.notifications.enableSystemNotifications },
                getValue = { it.notifications.showError },
                setValue = { s, v -> s.copy(notifications = s.notifications.copy(showError = v)) }
            ),

            SettingDefinition.NumericSetting(
                title = SettingText.Resource(Res.string.setting_history_retention),
                subtitle = null,
                icon = Icons.Default.History,
                sectionTitle = notificationsSection,
                navKey = SettingNavKey.SystemSettings,
                getValue = { it.notifications.history.retentionDays },
                setValue = { s, v ->
                    s.copy(
                        notifications = s.notifications.copy(
                            history = s.notifications.history.copy(retentionDays = v)
                        )
                    )
                },
                valueRange = 1..30
            ),

            SettingDefinition.CustomSetting(
                title = SettingText.Raw("Test Notifications"),
                subtitle = SettingText.Raw("Trigger test notifications to verify behavior"),
                icon = Icons.Default.BugReport,
                sectionTitle = notificationsSection,
                navKey = SettingNavKey.SystemSettings,
                control = { _, _ ->
                    // This control is a placeholder; it will be replaced at the screen level
                    // with the actual NotificationViewModel-dependent buttons.
                    // See AutoSettingsView for the injection point.
                }
            ),

            // ── Jobs ────────────────────────────────────────────────────
            SettingDefinition.NumericSetting(
                title = SettingText.Resource(Res.string.setting_max_concurrent_jobs),
                subtitle = SettingText.Resource(Res.string.setting_max_concurrent_jobs_subtitle),
                icon = Icons.Default.Engineering,
                sectionTitle = jobsSection,
                navKey = SettingNavKey.SystemSettings,
                getValue = { it.jobs.maxConcurrentJobs },
                setValue = { s, v -> s.copy(jobs = s.jobs.copy(maxConcurrentJobs = v)) },
                valueRange = 1..16
            ),

            SettingDefinition.SwitchSetting(
                title = SettingText.Resource(Res.string.setting_save_job_history),
                subtitle = SettingText.Resource(Res.string.setting_save_job_history_subtitle),
                icon = Icons.Default.History,
                sectionTitle = jobsSection,
                navKey = SettingNavKey.SystemSettings,
                getValue = { it.jobs.saveHistory },
                setValue = { s, v -> s.copy(jobs = s.jobs.copy(saveHistory = v)) }
            ),

            SettingDefinition.NumericSetting(
                title = SettingText.Resource(Res.string.setting_history_retention_days),
                subtitle = SettingText.Resource(Res.string.setting_history_retention_days_subtitle),
                icon = Icons.Default.EventRepeat,
                sectionTitle = jobsSection,
                navKey = SettingNavKey.SystemSettings,
                enabled = { it.jobs.saveHistory },
                getValue = { it.jobs.historyRetentionDays },
                setValue = { s, v -> s.copy(jobs = s.jobs.copy(historyRetentionDays = v)) },
                valueRange = 1..365
            ),

            // ── Plugins ─────────────────────────────────────────────────
            SettingDefinition.DropdownSetting(
                title = SettingText.Resource(Res.string.setting_plugin_unplug_behavior),
                subtitle = SettingText.Resource(Res.string.setting_plugin_unplug_behavior_subtitle),
                icon = Icons.Default.PowerOff,
                sectionTitle = pluginsSection,
                navKey = SettingNavKey.SystemSettings,
                options = PluginUnplugBehavior.entries,
                getValue = { it.extensions.pluginUnplugBehavior },
                setValue = { s, v -> s.copy(extensions = s.extensions.copy(pluginUnplugBehavior = v)) },
                labelProvider = {
                    when (it) {
                        PluginUnplugBehavior.Block -> stringResource(Res.string.setting_plugin_unplug_behavior_block)
                        PluginUnplugBehavior.StopJobs -> stringResource(Res.string.setting_plugin_unplug_behavior_stop)
                    }
                }
            )
        )

        // Register side effects for settings that need OS-level changes
        val launchAtStartup = definitions.first {
            it.title == SettingText.Resource(Res.string.setting_launch_at_startup)
        }
        sideEffects[launchAtStartup.hashCode()] = { settings ->
            StartupManager.setLaunchAtStartup(
                settings.general.launchAtStartup,
                settings.general.launchMinimizedAtStartup
            )
        }

        val launchMinimized = definitions.first {
            it.title == SettingText.Resource(Res.string.setting_launch_minimized_at_startup)
        }
        sideEffects[launchMinimized.hashCode()] = { settings ->
            if (settings.general.launchAtStartup) {
                StartupManager.setLaunchAtStartup(true, settings.general.launchMinimizedAtStartup)
            }
        }

        register(definitions)
    }
}
