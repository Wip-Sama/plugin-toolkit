package com.wip.kpm_cpm_wotoolkit.features.settings.model

import com.wip.kpm_cpm_wotoolkit.features.repository.model.ExtensionRepo
import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
        val appearance: AppearanceSettings = AppearanceSettings(),
        val localization: LocalizationSettings = LocalizationSettings(),
        val general: GeneralSettings = GeneralSettings(),
        val logging: LoggingSettings = LoggingSettings(),
        val notifications: NotificationSettings = NotificationSettings(),
        val extensions: ExtensionSettings = ExtensionSettings()
)

@Serializable
data class AppearanceSettings(
        val theme: AppTheme = AppTheme.System,
        val accentColor: Long = 0xFF6200EE, // Default purple
        val followSystemAccent: Boolean = true
)

@Serializable
enum class AppTheme {
    System,
    Light,
    Dark,
    Amoled
}

@Serializable
data class LocalizationSettings(
        val language: AppLanguage = AppLanguage.English,
        val timezone: String = "UTC",
        val useSystemTimezone: Boolean = true,
        val useSystemLanguage: Boolean = true
)

@Serializable
enum class AppLanguage(val label: String) {
    Italian("Italiano"),
    English("English")
}

@Serializable
data class GeneralSettings(
        val scaling: Float = 1.0f,
        val animationsEnabled: Boolean = true,
        val launchAtStartup: Boolean = false,
        val launchMinimizedAtStartup: Boolean = true,
        val startMinimized: Boolean = false,
        val closeToTray: Boolean = false
)

@Serializable
data class LoggingSettings(
    val level: LogLevel = LogLevel.Info,
    val logsToKeep: Int = 7,
    val compressOldLogs: Boolean = true,
    val compressedLogsToKeep: Int = 14
)

@Serializable
enum class LogLevel {
    Verbose,
    Debug,
    Info,
    Warn,
    Error,
    Assert
}

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

@Serializable
data class NotificationHistorySettings(
    val retentionDays: Int = 7 // Default 7 days, up to 30 (1 month)
)

@Serializable
data class ExtensionSettings(
    val repositories: List<ExtensionRepo> = emptyList(),
    val packageSourceOverrides: Map<String, String> = emptyMap() // pkg to repo url
)
