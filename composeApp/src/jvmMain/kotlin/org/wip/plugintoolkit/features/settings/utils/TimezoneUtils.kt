package org.wip.plugintoolkit.features.settings.utils

import java.time.ZoneId

actual object TimezoneUtils {
    actual fun getAvailableZoneIds(): List<String> = ZoneId.getAvailableZoneIds().toList().sorted()
    actual fun getSystemDefaultId(): String = ZoneId.systemDefault().id
}
