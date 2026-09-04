package org.wip.plugintoolkit.core

/**
 * Central registry of system locations and parameters used by the application.
 * This file serves as a reference for all persistent data and system integration points.
 */
interface SystemConfig {
    val isPortable: Boolean
    val APP_DATA_DIR_NAME: String
    val LEGACY_SETTINGS_DIR_NAME: String
    val SETTINGS_FILE_NAME: String
    val FLOWS_FILE_NAME: String
    val LOGS_DIR_NAME: String
    val PLUGINS_DIR_NAME: String
    val JOBS_DIR_NAME: String
    val CACHE_DIR_NAME: String
    val LOCK_FILE_NAME: String
    val INSTALLED_PLUGINS_FILE_NAME: String
    val STARTUP_APP_NAME: String
    val STARTUP_FLAG_BACKGROUND: String
    val WINDOWS_STARTUP_REGISTRY_PATH: String?
    val LINUX_AUTOSTART_DIR: String?
    val LINUX_DESKTOP_FILENAME: String?

    fun getAppDataDir(): String
    fun getCacheDir(systemManaged: Boolean = false): String
}

open class DefaultSystemConfig : SystemConfig {
    override val isPortable: Boolean = false
    override val APP_DATA_DIR_NAME = "PluginToolkit"
    override val LEGACY_SETTINGS_DIR_NAME = ".plugintoolkit"
    override val SETTINGS_FILE_NAME = "settings.json"
    override val FLOWS_FILE_NAME = "flows.json"
    override val LOGS_DIR_NAME = "logs"
    override val PLUGINS_DIR_NAME = "plugins"
    override val JOBS_DIR_NAME = "jobs"
    override val CACHE_DIR_NAME = "cache"
    override val LOCK_FILE_NAME = ".lock"
    override val INSTALLED_PLUGINS_FILE_NAME = "installed_plugins.json"
    override val STARTUP_APP_NAME = "PluginToolkit"
    override val STARTUP_FLAG_BACKGROUND = "--background"
    override val WINDOWS_STARTUP_REGISTRY_PATH: String? = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    override val LINUX_AUTOSTART_DIR: String? = ".config/autostart"
    override val LINUX_DESKTOP_FILENAME: String? = "${STARTUP_APP_NAME.lowercase()}.desktop"

    override fun getAppDataDir(): String {
        val appData = System.getenv("APPDATA")
        val osName = System.getProperty("os.name", "").lowercase()
        val isWindows = osName.contains("win")
        return if (isWindows && appData != null) {
            "$appData/$APP_DATA_DIR_NAME"
        } else {
            "${System.getProperty("user.home")}/$LEGACY_SETTINGS_DIR_NAME"
        }
    }

    override fun getCacheDir(systemManaged: Boolean): String {
        val osName = System.getProperty("os.name", "").lowercase()
        val isWindows = osName.contains("win")
        return if (systemManaged) {
            if (isWindows) {
                val localAppData = System.getenv("LOCALAPPDATA") ?: System.getenv("APPDATA")
                if (localAppData != null) "$localAppData/$APP_DATA_DIR_NAME/$CACHE_DIR_NAME"
                else "${getAppDataDir()}/$CACHE_DIR_NAME"
            } else {
                "${System.getProperty("user.home")}/.cache/$APP_DATA_DIR_NAME"
            }
        } else {
            "${getAppDataDir()}/$CACHE_DIR_NAME"
        }
    }
}

/**
 * System configuration used for portable installations.
 * Keeps all data, settings, logs, and cache within the portable directory and avoids modifying host registry or autostart.
 */
class PortableSystemConfig(
    val baseDir: String = System.getProperty("user.dir")
) : DefaultSystemConfig() {
    override val isPortable: Boolean = true
    override val WINDOWS_STARTUP_REGISTRY_PATH: String? = null
    override val LINUX_AUTOSTART_DIR: String? = null
    override val LINUX_DESKTOP_FILENAME: String? = null

    override fun getAppDataDir(): String {
        val normalizedBase = baseDir.replace('\\', '/').removeSuffix("/")
        return "$normalizedBase/data"
    }

    override fun getCacheDir(systemManaged: Boolean): String {
        return if (systemManaged) {
            super.getCacheDir(systemManaged = true)
        } else {
            "${getAppDataDir()}/$CACHE_DIR_NAME"
        }
    }
}
