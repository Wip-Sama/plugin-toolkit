package com.wip.kpm_cpm_wotoolkit.features.settings.logic

import com.wip.kpm_cpm_wotoolkit.features.settings.model.AppSettings
import com.wip.kpm_cpm_wotoolkit.core.KeepTrack
import kotlinx.serialization.json.Json
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.Path
import kotlinx.io.buffered
import kotlinx.io.readString
import kotlinx.io.writeString
import co.touchlab.kermit.Logger

actual class SettingsRepository actual constructor() {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val settingsDirPath = "${System.getProperty("user.home")}/${KeepTrack.SETTINGS_DIR_NAME}"
    private val settingsDir = Path(settingsDirPath)
    private val settingsFile = Path("$settingsDirPath/${KeepTrack.SETTINGS_FILE_NAME}")

    actual fun getSettingsDir(): String = settingsDirPath

    actual fun getJobsDir(): String {
        val jobsDirPath = "$settingsDirPath/${KeepTrack.JOBS_DIR_NAME}"
        val jobsDir = Path(jobsDirPath)
        if (!SystemFileSystem.exists(jobsDir)) {
            SystemFileSystem.createDirectories(jobsDir)
        }
        return jobsDirPath
    }

    actual fun loadSettings(): AppSettings {
        return try {
            if (SystemFileSystem.exists(settingsFile)) {
                val content = SystemFileSystem.source(settingsFile).buffered().use { it.readString() }
                if (content.isBlank()) {
                    AppSettings()
                } else {
                    json.decodeFromString<AppSettings>(content)
                }
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
            if (!SystemFileSystem.exists(settingsDir)) {
                SystemFileSystem.createDirectories(settingsDir)
            }
            val content = json.encodeToString(AppSettings.serializer(), settings)
            SystemFileSystem.sink(settingsFile).buffered().use { it.writeString(content) }
        } catch (e: Exception) {
            Logger.e(e) { "Error saving settings" }
        }
    }

    actual fun openLogFolder() {
        try {
            val logDirPath = "$settingsDirPath/${KeepTrack.LOGS_DIR_NAME}"
            val logDir = Path(logDirPath)
            if (!SystemFileSystem.exists(logDir)) {
                SystemFileSystem.createDirectories(logDir)
            }
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(java.io.File(logDirPath))
            }
        } catch (e: Exception) {
            Logger.e(e) { "Error opening log folder" }
        }
    }
}
