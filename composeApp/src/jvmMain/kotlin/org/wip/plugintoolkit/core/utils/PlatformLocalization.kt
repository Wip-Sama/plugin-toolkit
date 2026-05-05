package org.wip.plugintoolkit.core.utils

import java.util.Locale

actual object PlatformLocalization {
    actual fun getSystemLanguage(): String = System.getProperty("user.language") ?: "en"

    actual fun setApplicationLanguage(languageCode: String) {
        Locale.setDefault(Locale.forLanguageTag(languageCode))
    }
}
