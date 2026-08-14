package org.wip.plugintoolkit.features.job.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.features.job.model.ScheduledJob
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence

class ScheduleRepository(private val settingsPersistence: SettingsPersistence) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    private fun file(): Path {
        val jobsDir = Path(settingsPersistence.getJobsDir())
        if (!SystemFileSystem.exists(jobsDir)) SystemFileSystem.createDirectories(jobsDir)
        return Path("$jobsDir/schedules.json")
    }

    suspend fun load(): List<ScheduledJob> = withContext(Dispatchers.IO) {
        val file = file()
        if (!SystemFileSystem.exists(file)) return@withContext emptyList()
        runCatching<List<ScheduledJob>> {
            SystemFileSystem.source(file).buffered().use { source ->
                source.readString().takeIf { it.isNotBlank() }
                    ?.let { json.decodeFromString<List<ScheduledJob>>(it) }
                    ?: emptyList()
            }
        }.onFailure { Logger.e(it) { "Failed to load schedules" } }.getOrDefault(emptyList())
    }

    suspend fun save(schedules: List<ScheduledJob>) = withContext(Dispatchers.IO) {
        runCatching {
            SystemFileSystem.sink(file()).buffered().use { it.writeString(json.encodeToString(schedules)) }
        }.onFailure { Logger.e(it) { "Failed to save schedules" } }
    }
}
