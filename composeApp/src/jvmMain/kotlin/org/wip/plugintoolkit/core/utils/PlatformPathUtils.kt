package org.wip.plugintoolkit.core.utils

import org.wip.plugintoolkit.core.KeepTrack

object PlatformPathUtils {
    fun getAppDataDir(): String {
        val appData = System.getenv("APPDATA")
        return if (PlatformUtils.isWindows && appData != null) {
            "$appData/${KeepTrack.APP_DATA_DIR_NAME}"
        } else {
            "${System.getProperty("user.home")}/${KeepTrack.LEGACY_SETTINGS_DIR_NAME}"
        }
    }
}
