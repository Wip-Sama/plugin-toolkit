package com.wip.kpm_cpm_wotoolkit.features.settings.utils

import java.time.ZoneId

actual object TimezoneUtils {
    actual fun getAvailableZoneIds(): List<String> = ZoneId.getAvailableZoneIds().toList().sorted()
    actual fun getSystemDefaultId(): String = ZoneId.systemDefault().id
}
