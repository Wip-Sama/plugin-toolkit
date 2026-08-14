package org.wip.plugintoolkit.core.utils

import org.wip.plugintoolkit.core.SystemConfig
import org.koin.core.component.KoinComponent

object PlatformPathUtils : KoinComponent {
    private val injectedAppConfig: SystemConfig by lazy { getKoin().get() }

    fun getAppDataDir(appConfig: SystemConfig = injectedAppConfig): String {
        val appData = System.getenv("APPDATA")
        return if (PlatformUtils.isWindows && appData != null) {
            "$appData/${appConfig.APP_DATA_DIR_NAME}"
        } else {
            "${System.getProperty("user.home")}/${appConfig.LEGACY_SETTINGS_DIR_NAME}"
        }
    }
}
