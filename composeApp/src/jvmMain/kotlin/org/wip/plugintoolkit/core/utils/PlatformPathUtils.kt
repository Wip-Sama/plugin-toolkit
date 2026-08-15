package org.wip.plugintoolkit.core.utils

import org.wip.plugintoolkit.core.SystemConfig
import org.koin.core.component.KoinComponent

object PlatformPathUtils : KoinComponent {
    private val injectedAppConfig: SystemConfig by lazy { getKoin().get() }

    fun getAppDataDir(appConfig: SystemConfig = injectedAppConfig): String = appConfig.getAppDataDir()

    fun getCacheDir(
        systemManaged: Boolean = false,
        appConfig: SystemConfig = injectedAppConfig
    ): String = appConfig.getCacheDir(systemManaged)
}
