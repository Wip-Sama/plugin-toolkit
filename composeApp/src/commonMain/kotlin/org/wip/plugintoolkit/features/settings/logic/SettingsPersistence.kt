package org.wip.plugintoolkit.features.settings.logic

import org.wip.plugintoolkit.features.settings.model.AppSettings

interface SettingsPersistence {
    suspend fun load(): AppSettings
    suspend fun save(settings: AppSettings)
    fun getSettingsDir(): String
    fun getJobsDir(): String
    fun openLogFolder()
    fun openLatestLog()
}
