package com.wip.kpm_cpm_wotoolkit.features.settings.logic

import com.wip.kpm_cpm_wotoolkit.features.settings.model.AppSettings
import com.wip.kpm_cpm_wotoolkit.core.KeepTrack
import kotlinx.serialization.json.Json
import java.io.File
import co.touchlab.kermit.Logger

actual class SettingsRepository actual constructor() {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val settingsDir = File(System.getProperty("user.home"), KeepTrack.SETTINGS_DIR_NAME)
    private val settingsFile = File(settingsDir, KeepTrack.SETTINGS_FILE_NAME)

    actual fun getSettingsDir(): String = settingsDir.absolutePath

    actual fun getJobsDir(): String {
        val jobsDir = File(settingsDir, KeepTrack.JOBS_DIR_NAME)
        if (!jobsDir.exists()) {
            jobsDir.mkdirs()
        }
        return jobsDir.absolutePath
    }

    actual fun loadSettings(): AppSettings {
        return try {
            if (settingsFile.exists()) {
                val content = settingsFile.readText()
                json.decodeFromString<AppSettings>(content)
            } else {
                AppSettings()
            }
        } catch (e: Exception) {
            Logger.e(e) { "Error loading settings" }
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
            Logger.e(e) { "Error saving settings" }
        }
    }

    actual fun openLogFolder() {
        try {
            val logDir = File(settingsDir, KeepTrack.LOGS_DIR_NAME)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(logDir)
            }
        } catch (e: Exception) {
            Logger.e(e) { "Error opening log folder" }
        }
    }
}
