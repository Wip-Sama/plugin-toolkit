package com.wip.kpm_cpm_wotoolkit.core.utils

expect object StartupManager {
    fun setLaunchAtStartup(enabled: Boolean, minimized: Boolean = true)
    fun isLaunchAtStartupEnabled(): Boolean
}
