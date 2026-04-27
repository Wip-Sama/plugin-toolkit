package com.wip.kpm_cpm_wotoolkit.core

/**
 * Central registry of system locations and parameters used by the application.
 * This file serves as a reference for all persistent data and system integration points.
 */
object KeepTrack {
    // --- Settings Storage ---
    /** The directory in the user's home where app settings are stored. */
    const val SETTINGS_DIR_NAME = ".kpm_cpm_wotoolkit"
    
    /** The filename for the application settings. */
    const val SETTINGS_FILE_NAME = "settings.json"

    /** The directory for application logs. */
    const val LOGS_DIR_NAME = "logs"

    /** The directory for modules. */
    const val MODULES_DIR_NAME = "modules"
    
    // --- Startup Parameters ---
    /** The name used for system startup entries (Registry/Desktop file). */
    const val STARTUP_APP_NAME = "WOToolkit"
    
    /** The command-line flag used to launch the app in background mode. */
    const val STARTUP_FLAG_BACKGROUND = "--background"
    
    // --- Platform Specific Locations (Reference) ---
    
    /** Windows Registry key for autostart. */
    const val WINDOWS_STARTUP_REGISTRY_PATH = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    
    /** Linux Autostart directory relative to user home. */
    const val LINUX_AUTOSTART_DIR = ".config/autostart"
    
    /** Linux Desktop entry filename. */
    val LINUX_DESKTOP_FILENAME = "${STARTUP_APP_NAME.lowercase()}.desktop"
}
