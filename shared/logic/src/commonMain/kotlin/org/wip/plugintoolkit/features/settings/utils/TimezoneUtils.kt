package org.wip.plugintoolkit.features.settings.utils

import kotlinx.datetime.TimeZone

object TimezoneUtils {
    fun getAvailableZoneIds(): List<String> = TimeZone.availableZoneIds.toList().sorted()
    fun getSystemDefaultId(): String = TimeZone.currentSystemDefault().id
}
