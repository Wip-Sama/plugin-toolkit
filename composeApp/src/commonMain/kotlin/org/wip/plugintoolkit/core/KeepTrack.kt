package org.wip.plugintoolkit.core

/**
 * Central registry of system locations and parameters used by the application.
 * This file serves as a reference for all persistent data and system integration points.
 */
interface SystemConfig {
    val APP_DATA_DIR_NAME: String
    val LEGACY_SETTINGS_DIR_NAME: String
    val SETTINGS_FILE_NAME: String
    val FLOWS_FILE_NAME: String
    val LOGS_DIR_NAME: String
    val PLUGINS_DIR_NAME: String
    val JOBS_DIR_NAME: String
    val INSTALLED_PLUGINS_FILE_NAME: String
    val STARTUP_APP_NAME: String
    val STARTUP_FLAG_BACKGROUND: String
    val WINDOWS_STARTUP_REGISTRY_PATH: String
    val LINUX_AUTOSTART_DIR: String
    val LINUX_DESKTOP_FILENAME: String
}

class DefaultSystemConfig : SystemConfig {
    override val APP_DATA_DIR_NAME = "PluginToolkit"
    override val LEGACY_SETTINGS_DIR_NAME = ".plugintoolkit"
    override val SETTINGS_FILE_NAME = "settings.json"
    override val FLOWS_FILE_NAME = "flows.json"
    override val LOGS_DIR_NAME = "logs"
    override val PLUGINS_DIR_NAME = "plugins"
    override val JOBS_DIR_NAME = "jobs"
    override val INSTALLED_PLUGINS_FILE_NAME = "installed_plugins.json"
    override val STARTUP_APP_NAME = "PluginToolkit"
    override val STARTUP_FLAG_BACKGROUND = "--background"
    override val WINDOWS_STARTUP_REGISTRY_PATH = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    override val LINUX_AUTOSTART_DIR = ".config/autostart"
    override val LINUX_DESKTOP_FILENAME = "${STARTUP_APP_NAME.lowercase()}.desktop"
}
