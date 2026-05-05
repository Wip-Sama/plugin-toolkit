package org.wip.plugintoolkit.core.utils

expect object StartupManager {
    fun setLaunchAtStartup(enabled: Boolean, minimized: Boolean = true)
    fun isLaunchAtStartupEnabled(): Boolean
}
