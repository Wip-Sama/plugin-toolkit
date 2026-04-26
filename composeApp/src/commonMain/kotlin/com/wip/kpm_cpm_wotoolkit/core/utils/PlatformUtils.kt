package com.wip.kpm_cpm_wotoolkit.core.utils

import androidx.compose.ui.graphics.Color

expect object PlatformUtils {
    val isWindows: Boolean
    val isLinux: Boolean
    fun getSystemAccentColor(): Color?
}
