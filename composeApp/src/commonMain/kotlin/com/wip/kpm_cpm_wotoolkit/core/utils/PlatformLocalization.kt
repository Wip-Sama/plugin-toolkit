package com.wip.kpm_cpm_wotoolkit.core.utils

expect object PlatformLocalization {
    fun getSystemLanguage(): String
    fun setApplicationLanguage(languageCode: String)
}
