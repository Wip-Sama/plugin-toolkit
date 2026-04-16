package com.wip.cmp_desktop_test.settings

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
    val useAdaptiveColor: Boolean = true
)

@Serializable
enum class AppTheme {
    System, Light, Dark, Amoled
}

@Serializable
data class LocalizationSettings(
    val language: AppLanguage = AppLanguage.English,
    val timezone: String = "GMT+1"
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
    val animationSpeed: Float = 1.0f
)
