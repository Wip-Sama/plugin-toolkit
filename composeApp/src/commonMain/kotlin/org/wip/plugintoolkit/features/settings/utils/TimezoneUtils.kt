package org.wip.plugintoolkit.features.settings.utils

import java.time.ZoneId

object TimezoneUtils {
    fun getAvailableZoneIds(): List<String> = ZoneId.getAvailableZoneIds().toList().sorted()
    fun getSystemDefaultId(): String = ZoneId.systemDefault().id
}
