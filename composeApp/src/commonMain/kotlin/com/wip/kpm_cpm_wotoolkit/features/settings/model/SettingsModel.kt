package com.wip.kpm_cpm_wotoolkit.features.settings.model

import kotlinx.serialization.Serializable

@Serializable
data class AppSettings(
        val appearance: AppearanceSettings = AppearanceSettings(),
        val localization: LocalizationSettings = LocalizationSettings(),
        val general: GeneralSettings = GeneralSettings()
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
        val animationsEnabled: Boolean = true
)
