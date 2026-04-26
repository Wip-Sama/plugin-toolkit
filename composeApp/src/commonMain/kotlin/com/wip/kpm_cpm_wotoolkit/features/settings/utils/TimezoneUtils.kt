package com.wip.kpm_cpm_wotoolkit.features.settings.utils

expect object TimezoneUtils {
    fun getAvailableZoneIds(): List<String>
    fun getSystemDefaultId(): String
}
