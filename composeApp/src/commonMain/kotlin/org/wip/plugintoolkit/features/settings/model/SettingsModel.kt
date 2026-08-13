/**
 * # AppSettings — How to Add and Register a New Setting
 *
 * Defines the application's persistent configuration state.
 * All settings are serialized to disk via [org.wip.plugintoolkit.features.settings.logic.SettingsRepository].
 *
 * ## How to Add a New Setting
 *
 * 1. **Add Model Field**: Add your new property to the appropriate `*Settings` data class in this file.
 * 2. **Add Localization**: Add the setting title (and optional subtitle) to `composeResources/values/strings.xml`.
 * 3. **Register UI Definition**: Open the corresponding file in `features/settings/definitions/`:
 *    ```kotlin
 *    bindGroup(AppSettings::logging, { copy(logging = it) }) {
 *        switch(LoggingSettings::myBoolProp, Res.string.setting_title, Icons.Default.Check) {
 *            copy(myBoolProp = it)
 *        }
 *    }
 *    ```
 * 4. **Auto-Rendering & Search**: The setting automatically registers in [org.wip.plugintoolkit.features.settings.utils.SettingsRegistry],
 *    renders in [org.wip.plugintoolkit.features.settings.ui.AutoSettingsView], and becomes searchable.
 *
 * @see SettingDefinition
 * @see org.wip.plugintoolkit.features.settings.utils.SettingsRegistry
 */
package org.wip.plugintoolkit.features.settings.model

import kotlinx.serialization.Serializable
import org.wip.plugintoolkit.features.repository.model.ExtensionRepo

/**
 * Root data structure aggregating all setting groups.
 */
@Serializable
data class AppSettings(
    val appearance: AppearanceSettings = AppearanceSettings(),
    val localization: LocalizationSettings = LocalizationSettings(),
    val general: GeneralSettings = GeneralSettings(),
    val logging: LoggingSettings = LoggingSettings(),
    val notifications: NotificationSettings = NotificationSettings(),
    val extensions: ExtensionSettings = ExtensionSettings(),
    val jobs: JobSettings = JobSettings(),
    val autoUpdate: AutoUpdateSettings = AutoUpdateSettings(),
    val flows: FlowSettings = FlowSettings()
)

/**
 * Visual styling and theme configuration options.
 */
@Serializable
data class AppearanceSettings(
    val theme: AppTheme = AppTheme.System,
    val accentColor: Long = 0xFF6200EE, // Default purple
    val followSystemAccent: Boolean = true
)

/**
 * Application theme mode options.
 */
@Serializable
enum class AppTheme {
    System,
    Light,
    Dark,
    Amoled
}

/**
 * Main window initial display state options on launch.
 */
@Serializable
enum class WindowStartMode {
    Normal,
    Minimized,
    Maximized,
    Fullscreen
}

/**
 * Locale and timezone settings configuration.
 */
@Serializable
data class LocalizationSettings(
    val language: AppLanguage = AppLanguage.English,
    val timezone: String = "UTC",
    val useSystemTimezone: Boolean = true,
    val useSystemLanguage: Boolean = true
)

/**
 * Supported application UI languages.
 */
@Serializable
enum class AppLanguage(val label: String) {
    Italian("Italiano"),
    English("English")
}

/**
 * General application layout, animation, and startup behavior.
 */
@Serializable
data class GeneralSettings(
    val scaling: Float = 1.0f,
    val animationsEnabled: Boolean = true,
    val launchAtStartup: Boolean = false,
    val launchMinimizedAtStartup: Boolean = true,
    val windowStartMode: WindowStartMode = WindowStartMode.Normal,
    val closeToTray: Boolean = false
)

/**
 * System logging severity levels and retention policies.
 */
@Serializable
data class LoggingSettings(
    val level: LogLevel = LogLevel.Info,
    val logsToKeep: Int = 7,
    val compressOldLogs: Boolean = true,
    val compressedLogsToKeep: Int = 14
)

/**
 * Logging severity level filter thresholds.
 */
@Serializable
enum class LogLevel {
    Verbose,
    Debug,
    Info,
    Warn,
    Error,
    Assert
}

/**
 * In-app toasts, system notification center settings, and notification filters.
 */
@Serializable
data class NotificationSettings(
    val enableToasts: Boolean = true,
    val toastAutoDismiss: Boolean = true,
    val toastDismissTime: Int = 5, // seconds
    val enableSystemNotifications: Boolean = true,
    val showInfo: Boolean = true,
    val showWarning: Boolean = true,
    val showError: Boolean = true,
    val history: NotificationHistorySettings = NotificationHistorySettings()
)

/**
 * Retention duration configuration for notification history log.
 */
@Serializable
data class NotificationHistorySettings(
    val retentionDays: Int = 7 // Default 7 days, up to 30 (1 month)
)

/**
 * Action taken when an active plugin is unplugged during execution.
 */
@Serializable
enum class PluginUnplugBehavior {
    Block,
    StopJobs
}

/**
 * Host file system access restriction mode for plugins.
 */
@Serializable
enum class FileAccessMode {
    Blacklist,
    Whitelist,
    Unrestricted
}

/**
 * Extension and plugin ecosystem security and installation settings.
 */
@Serializable
data class ExtensionSettings(
    val repositories: List<ExtensionRepo> = emptyList(),
    val packageSourceOverrides: Map<String, String> = emptyMap(), // pkg to repo url
    val pluginFolders: List<String> = emptyList(), // managed install locations
    val pluginUnplugBehavior: PluginUnplugBehavior = PluginUnplugBehavior.Block,
    val strictSignatureChecking: Boolean = true,
    val fileAccessMode: FileAccessMode = FileAccessMode.Blacklist,
    val blacklistedDirectories: List<String> = emptyList(),
    val allowedDirectories: List<String> = emptyList(),
    val pluginSettingsInPlace: Boolean = false
)

/**
 * Background job engine execution concurrency, timeouts, and retry policies.
 */
@Serializable
data class JobSettings(
    val maxConcurrentJobs: Int = 2,
    val saveHistory: Boolean = true,
    val maxHistoryLength: Int = 200,
    val maxLogLines: Int = 100,
    val maxEndedJobs: Int = 20,
    val pluginTimeoutMs: Long = 600000L,
    val enableTransientRetries: Boolean = true,
    val maxRetries: Int = 2
)

/**
 * Automatic application software update checking options.
 */
@Serializable
data class AutoUpdateSettings(
    val enabled: Boolean = true,
    val checkOnStartup: Boolean = true,
    val lastCheckTimestamp: Long = 0,
    val pendingUpdateVersion: String? = null
)

/**
 * Visual automation flow editor preferences.
 */
@Serializable
data class FlowSettings(
    val autosave: Boolean = true
)
