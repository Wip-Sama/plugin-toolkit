package org.wip.plugintoolkit.features.job.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class ScheduledJobTest {
    private val template = BackgroundJob(
        id = "job", name = "Example", type = JobType.Capability,
        pluginId = "plugin", capabilityName = "run"
    )

    @Test
    fun `due schedules advance from actual execution time`() {
        val dueAt = Instant.fromEpochMilliseconds(1_000)
        val now = Instant.fromEpochMilliseconds(5_000)
        val schedule = ScheduledJob("schedule", template, 10, dueAt)

        assertTrue(schedule.isDue(now))
        val advanced = schedule.afterRun(now)
        assertEquals(now, advanced.lastRunAt)
        assertEquals(Instant.fromEpochMilliseconds(605_000), advanced.nextRunAt)
        assertFalse(advanced.isDue(now))
    }

    @Test
    fun `disabled schedules never become due`() {
        val schedule = ScheduledJob("schedule", template, 10, Instant.fromEpochMilliseconds(0), enabled = false)
        assertFalse(schedule.isDue(Instant.fromEpochMilliseconds(5_000)))
    }
}
