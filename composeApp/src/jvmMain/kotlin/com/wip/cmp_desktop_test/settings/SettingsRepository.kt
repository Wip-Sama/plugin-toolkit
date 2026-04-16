package com.wip.cmp_desktop_test.settings

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class SettingsRepository {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val settingsDir = File(System.getProperty("user.home"), ".cmp_desktop_test")
    private val settingsFile = File(settingsDir, "settings.json")

    fun loadSettings(): AppSettings {
        return try {
            if (settingsFile.exists()) {
                val content = settingsFile.readText()
                json.decodeFromString<AppSettings>(content)
            } else {
                AppSettings()
            }
        } catch (e: Exception) {
            println("Error loading settings: ${e.message}")
            AppSettings() // Fallback to defaults
        }
    }

    fun saveSettings(settings: AppSettings) {
        try {
            if (!settingsDir.exists()) {
                settingsDir.mkdirs()
            }
            val content = json.encodeToString(settings)
            settingsFile.writeText(content)
        } catch (e: Exception) {
            println("Error saving settings: ${e.message}")
        }
    }
}
