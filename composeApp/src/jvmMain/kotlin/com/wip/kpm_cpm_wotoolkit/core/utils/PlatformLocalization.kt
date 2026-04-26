package com.wip.kpm_cpm_wotoolkit.core.utils

import java.util.Locale

actual object PlatformLocalization {
    actual fun getSystemLanguage(): String = System.getProperty("user.language") ?: "en"
    
    actual fun setApplicationLanguage(languageCode: String) {
        Locale.setDefault(Locale.forLanguageTag(languageCode))
    }
}
