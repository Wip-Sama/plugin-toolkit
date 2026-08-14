package org.wip.plugintoolkit.features.job.logic

import kotlinx.coroutines.test.runTest
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.job.model.ScheduledJob
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppSettings
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ScheduleRepositoryTest {
    private class TempPersistence(private val root: Path) : SettingsPersistence {
        override suspend fun load(): AppSettings = AppSettings()
        override suspend fun save(settings: AppSettings) = Unit
        override fun getSettingsDir(): String = root.toString()
        override fun getJobsDir(): String = root.resolve("jobs").toString()
        override fun openLogFolder() = Unit
        override fun openLatestLog() = Unit
    }

    private val template = BackgroundJob(
        id = "job",
        name = "Example",
        type = JobType.Capability,
        pluginId = "plugin",
        capabilityName = "run"
    )

    @Test
    fun `corrupt primary recovers the last known-good backup`() = runTest {
        withTempPersistence { persistence, root ->
            val repository = ScheduleRepository(persistence)
            val first = listOf(ScheduledJob("first", template, 10, Instant.fromEpochMilliseconds(1_000)))
            val second = listOf(ScheduledJob("second", template, 20, Instant.fromEpochMilliseconds(2_000)))

            assertTrue(repository.save(first).isSuccess)
            assertTrue(repository.save(second).isSuccess)
            Files.writeString(root.resolve("jobs/schedules.json"), "{")

            assertEquals(first, repository.load().getOrThrow())
        }
    }

    @Test
    fun `scheduler persists advancement before exposing a due job`() = runTest {
        withTempPersistence { persistence, _ ->
            val settings = SettingsRepository(persistence, backgroundScope)
            testScheduler.advanceUntilIdle()
            val manager = JobManager(backgroundScope, settings)
            val schedule = manager.scheduleJob(template, intervalMinutes = 1)!!
            val dueNow = schedule.nextRunAt + 1.minutes

            manager.runDueSchedules(dueNow)

            val persisted = ScheduleRepository(persistence).load().getOrThrow().single()
            assertEquals(dueNow, persisted.lastRunAt)
            assertEquals(dueNow + 1.minutes, persisted.nextRunAt)
            assertTrue(manager.history.value.any { it.jobId.startsWith("job-") && it.event == "Enqueued" })
        }
    }

    @Test
    fun `only capability and flow jobs can be scheduled and ids are unique`() = runTest {
        withTempPersistence { persistence, _ ->
            val settings = SettingsRepository(persistence, backgroundScope)
            testScheduler.advanceUntilIdle()
            val manager = JobManager(backgroundScope, settings)

            val first = manager.scheduleJob(template, 5)!!
            val second = manager.scheduleJob(template, 5)!!
            val setup = template.copy(id = "setup", type = JobType.Setup)

            assertNotEquals(first.id, second.id)
            assertNull(manager.scheduleJob(setup, 5))
            assertEquals(2, manager.schedules.value.size)
        }
    }

    @Test
    fun `unsupported persisted schedules are removed before scheduler startup`() = runTest {
        withTempPersistence { persistence, _ ->
            val unsafe = ScheduledJob(
                id = "setup-schedule",
                jobTemplate = template.copy(type = JobType.Setup),
                intervalMinutes = 5,
                nextRunAt = Instant.fromEpochMilliseconds(0)
            )
            ScheduleRepository(persistence).save(listOf(unsafe)).getOrThrow()
            val settings = SettingsRepository(persistence, backgroundScope)
            testScheduler.advanceUntilIdle()
            val manager = JobManager(backgroundScope, settings)

            assertTrue(manager.startScheduler())

            assertTrue(manager.schedules.value.isEmpty())
            assertTrue(ScheduleRepository(persistence).load().getOrThrow().isEmpty())
        }
    }

    private suspend fun withTempPersistence(
        block: suspend (TempPersistence, Path) -> Unit
    ) {
        val root = Files.createTempDirectory("plugin-toolkit-schedules-")
        try {
            block(TempPersistence(root), root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
