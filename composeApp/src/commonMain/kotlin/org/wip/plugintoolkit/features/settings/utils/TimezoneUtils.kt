package org.wip.plugintoolkit.features.settings.utils

expect object TimezoneUtils {
    fun getAvailableZoneIds(): List<String>
    fun getSystemDefaultId(): String
}
