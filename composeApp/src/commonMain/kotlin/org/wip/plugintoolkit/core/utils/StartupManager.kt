package org.wip.plugintoolkit.core.utils

expect object StartupManager {
    suspend fun setLaunchAtStartup(enabled: Boolean, minimized: Boolean = true)
    suspend fun isLaunchAtStartupEnabled(): Boolean
}
