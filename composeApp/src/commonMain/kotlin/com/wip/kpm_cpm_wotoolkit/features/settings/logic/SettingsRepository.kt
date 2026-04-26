package com.wip.kpm_cpm_wotoolkit.features.settings.logic

import com.wip.kpm_cpm_wotoolkit.features.settings.model.AppSettings

expect class SettingsRepository() {
    fun loadSettings(): AppSettings
    fun saveSettings(settings: AppSettings)
    fun openLogFolder()
}
