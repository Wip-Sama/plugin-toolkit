package org.wip.plugintoolkit.core.utils

expect object PlatformLocalization {
    fun getSystemLanguage(): String
    fun setApplicationLanguage(languageCode: String)
}
