package com.wip.kpm_cpm_wotoolkit.features.settings.logic

import com.wip.kpm_cpm_wotoolkit.features.settings.model.AppSettings
import kotlinx.serialization.json.Json
import java.io.File

actual class SettingsRepository actual constructor() {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val settingsDir = File(System.getProperty("user.home"), ".kpm_cpm_wotoolkit")
    private val settingsFile = File(settingsDir, "settings.json")

    actual fun loadSettings(): AppSettings {
        return try {
            if (settingsFile.exists()) {
                val content = settingsFile.readText()
                json.decodeFromString<AppSettings>(content)
            } else {
                AppSettings()
            }
        } catch (e: Exception) {
            println("Error loading settings: ${e.message}")
            AppSettings()
        }
    }

    actual fun saveSettings(settings: AppSettings) {
        try {
            if (!settingsDir.exists()) {
                settingsDir.mkdirs()
            }
            val content = json.encodeToString(AppSettings.serializer(), settings)
            settingsFile.writeText(content)
        } catch (e: Exception) {
            println("Error saving settings: ${e.message}")
        }
    }
}
