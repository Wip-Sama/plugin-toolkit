package org.wip.plugintoolkit.core.utils

import org.wip.plugintoolkit.core.SystemConfig
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object PlatformPathUtils : KoinComponent {
    private val appConfig: SystemConfig by inject()

    fun getAppDataDir(): String = appConfig.getAppDataDir()

    fun getCacheDir(systemManaged: Boolean = false): String = appConfig.getCacheDir(systemManaged)
}
