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
 * Connection line rendering style in the Flow Editor canvas.
 */
@Serializable
enum class ConnectionCurveStyle {
    CardinalSpline,
    Bezier,
    Straight,
    Orthogonal
}

/**
 * Visual styling and theme configuration options.
 */
@Serializable
data class AppearanceSettings(
    val theme: AppTheme = AppTheme.System,
    val accentColor: Long = 0xFF6200EE, // Default purple
    val followSystemAccent: Boolean = true,
    val useAccentInTheme: Boolean = false,
    val sidebarStartMode: SidebarStartMode = SidebarStartMode.Remember,
    val isSidebarCollapsed: Boolean = false,
    val useCustomTitleBar: Boolean = true,
    val connectionStyle: ConnectionCurveStyle = ConnectionCurveStyle.CardinalSpline,
    val connectionRoundness: Float = 0.5f
)

/**
 * Sidebar initial display state options on launch.
 */
@Serializable
enum class SidebarStartMode {
    Expanded,
    Collapsed,
    Remember
}

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
 * Storage location strategy for application cache and temporary files.
 */
@Serializable
enum class CacheManagementMode {
    SystemManaged,
    ApplicationManaged
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
    val closeToTray: Boolean = false,
    val cacheManagement: CacheManagementMode = CacheManagementMode.SystemManaged,
    val singleInstanceLock: Boolean = true,
    val maxMemoryMb: Int = DEFAULT_MAX_MEMORY_MB
) {
    fun effectiveMaxMemoryMb(): Int = maxMemoryMb.coerceAtLeast(MIN_MAX_MEMORY_MB)

    companion object {
        const val MIN_MAX_MEMORY_MB: Int = 1024
        const val DEFAULT_MAX_MEMORY_MB: Int = 2048
    }
}

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
    val maxLogLineLength: Int = 1000,
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
