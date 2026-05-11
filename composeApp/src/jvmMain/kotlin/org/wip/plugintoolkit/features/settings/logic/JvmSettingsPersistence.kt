package org.wip.plugintoolkit.features.settings.logic

import co.touchlab.kermit.Logger
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.core.KeepTrack
import org.wip.plugintoolkit.core.utils.PlatformPathUtils
import org.wip.plugintoolkit.features.settings.model.AppSettings

class JvmSettingsPersistence : SettingsPersistence {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val settingsDirPath = PlatformPathUtils.getAppDataDir()
    private val settingsDir = Path(settingsDirPath)
    private val settingsFile = Path("$settingsDirPath/${KeepTrack.SETTINGS_FILE_NAME}")

    override fun getSettingsDir(): String = settingsDirPath

    override fun getJobsDir(): String {
        val jobsDirPath = "$settingsDirPath/${KeepTrack.JOBS_DIR_NAME}"
        val jobsDir = Path(jobsDirPath)
        if (!SystemFileSystem.exists(jobsDir)) {
            SystemFileSystem.createDirectories(jobsDir)
        }
        return jobsDirPath
    }

    override fun load(): AppSettings {
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

    override fun save(settings: AppSettings) {
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

    override fun openLogFolder() {
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

    override fun openLatestLog() {
        try {
            val logDirPath = "$settingsDirPath/${KeepTrack.LOGS_DIR_NAME}"
            val dateString = java.text.SimpleDateFormat("yyyy_MM_dd", java.util.Locale.getDefault()).format(java.util.Date())
            val logFilePath = "$logDirPath/$dateString.log"
            val logFile = java.io.File(logFilePath)

            if (java.awt.Desktop.isDesktopSupported()) {
                if (logFile.exists()) {
                    java.awt.Desktop.getDesktop().open(logFile)
                } else {
                    // If today's log doesn't exist yet, just open the folder
                    openLogFolder()
                }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Error opening latest log" }
        }
    }
}
