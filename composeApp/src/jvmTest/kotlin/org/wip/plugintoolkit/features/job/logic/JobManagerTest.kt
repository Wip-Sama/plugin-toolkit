package org.wip.plugintoolkit.features.job.logic

import kotlinx.coroutines.test.runTest
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JobManagerTest {

    private class FakeSettingsPersistence : SettingsPersistence {
        var settings = AppSettings()
        override fun load(): AppSettings = settings
        override fun save(settings: AppSettings) { this.settings = settings }
        override fun getSettingsDir(): String = "/tmp"
        override fun getJobsDir(): String = "/tmp/jobs"
        override fun openLogFolder() {}
        override fun openLatestLog() {}
    }

    @Test
    fun testKeepResultFilteringOnEnqueue() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val jobManager = JobManager(backgroundScope, settingsRepo)

        // 1. Enqueue and complete a job with keepResult = true
        val job1 = BackgroundJob(
            id = "job-1",
            name = "Job 1",
            type = JobType.Capability,
            pluginId = "test-plugin",
            capabilityName = "test-cap",
            keepResult = true
        )
        jobManager.enqueueJob(job1)
        
        // Claim the job and run it, then complete it
        val claimed1 = jobManager.waitForNextJob()
        assertEquals("job-1", claimed1.id)
        val completed1 = jobManager.tryCompleteJob("job-1", "result-1")
        assertTrue(completed1)
        
        // Verify it is in endedJobs
        assertEquals(1, jobManager.endedJobs.value.size)
        assertEquals("job-1", jobManager.endedJobs.value[0].id)

        // 2. Enqueue and complete a job with keepResult = false
        val job2 = BackgroundJob(
            id = "job-2",
            name = "Job 2",
            type = JobType.Capability,
            pluginId = "test-plugin",
            capabilityName = "test-cap",
            keepResult = false
        )
        jobManager.enqueueJob(job2)
        
        // Verify job1 (keepResult = true) was NOT deleted when job2 was enqueued!
        assertEquals(1, jobManager.endedJobs.value.size)
        assertEquals("job-1", jobManager.endedJobs.value[0].id)

        // Claim and complete job2
        val claimed2 = jobManager.waitForNextJob()
        assertEquals("job-2", claimed2.id)
        val completed2 = jobManager.tryCompleteJob("job-2", "result-2")
        assertTrue(completed2)

        // Verify both are now in endedJobs
        assertEquals(2, jobManager.endedJobs.value.size)
        assertEquals("job-2", jobManager.endedJobs.value[0].id)
        assertEquals("job-1", jobManager.endedJobs.value[1].id)

        // 3. Enqueue job3 (keepResult = true)
        val job3 = BackgroundJob(
            id = "job-3",
            name = "Job 3",
            type = JobType.Capability,
            pluginId = "test-plugin",
            capabilityName = "test-cap",
            keepResult = true
        )
        jobManager.enqueueJob(job3)

        // Verify that job2 (keepResult = false) WAS deleted, but job1 (keepResult = true) WAS NOT!
        assertEquals(1, jobManager.endedJobs.value.size)
        assertEquals("job-1", jobManager.endedJobs.value[0].id)
    }
}
