package org.wip.plugintoolkit.features.settings.logic

import org.wip.plugintoolkit.features.settings.model.AppSettings

expect class SettingsRepository() {
    fun loadSettings(): AppSettings
    fun saveSettings(settings: AppSettings)
    fun openLogFolder()
    fun getSettingsDir(): String
    fun getJobsDir(): String
}
