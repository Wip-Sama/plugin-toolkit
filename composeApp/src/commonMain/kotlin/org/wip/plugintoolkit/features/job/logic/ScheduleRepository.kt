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

    suspend fun load(): Result<List<ScheduledJob>> = withContext(Dispatchers.IO) {
        val primary = file()
        val backup = Path("$primary.bak")
        if (!SystemFileSystem.exists(primary) && !SystemFileSystem.exists(backup)) {
            return@withContext Result.success(emptyList())
        }

        val primaryResult = runCatching { read(primary) }
        if (primaryResult.isSuccess) return@withContext primaryResult

        Logger.e(primaryResult.exceptionOrNull()) { "Failed to load schedules; trying backup" }
        if (!SystemFileSystem.exists(backup)) return@withContext primaryResult

        runCatching { read(backup) }
            .onSuccess { Logger.w { "Recovered schedules from backup" } }
            .onFailure { Logger.e(it) { "Failed to load schedule backup" } }
    }

    suspend fun save(schedules: List<ScheduledJob>): Result<Unit> = withContext(Dispatchers.IO) {
        val primary = file()
        val temporary = Path("$primary.tmp")
        val backup = Path("$primary.bak")
        val backupTemporary = Path("$primary.bak.tmp")

        runCatching {
            write(temporary, schedules)

            // Never replace a known-good backup with a corrupt/partial primary.
            if (SystemFileSystem.exists(primary)) {
                runCatching { read(primary) }.getOrNull()?.let { previous ->
                    write(backupTemporary, previous)
                    SystemFileSystem.atomicMove(backupTemporary, backup)
                }
            }

            SystemFileSystem.atomicMove(temporary, primary)
        }.onFailure { Logger.e(it) { "Failed to save schedules atomically" } }
            .also {
                runCatching { if (SystemFileSystem.exists(temporary)) SystemFileSystem.delete(temporary) }
                runCatching { if (SystemFileSystem.exists(backupTemporary)) SystemFileSystem.delete(backupTemporary) }
            }
    }

    private fun read(path: Path): List<ScheduledJob> {
        if (!SystemFileSystem.exists(path)) error("Schedule file does not exist: $path")
        val content = SystemFileSystem.source(path).buffered().use { it.readString() }
        check(content.isNotBlank()) { "Schedule file is empty or partially written: $path" }
        return json.decodeFromString(content)
    }

    private fun write(path: Path, schedules: List<ScheduledJob>) {
        SystemFileSystem.sink(path).buffered().use { it.writeString(json.encodeToString(schedules)) }
    }
}
