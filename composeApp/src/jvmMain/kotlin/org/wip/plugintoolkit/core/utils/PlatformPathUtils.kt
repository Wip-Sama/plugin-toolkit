package org.wip.plugintoolkit.core.utils

import org.wip.plugintoolkit.core.SystemConfig
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object PlatformPathUtils : KoinComponent {
    private val appConfig: SystemConfig by inject()

    fun getAppDataDir(): String {
        val appData = System.getenv("APPDATA")
        return if (PlatformUtils.isWindows && appData != null) {
            "$appData/${appConfig.APP_DATA_DIR_NAME}"
        } else {
            "${System.getProperty("user.home")}/${appConfig.LEGACY_SETTINGS_DIR_NAME}"
        }
    }
}
