package org.wip.plugintoolkit.features.job.logic

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JobManagerTest {

    private class FakeSettingsPersistence : SettingsPersistence {
        var settings = AppSettings()
        override suspend fun load(): AppSettings = settings
        override suspend fun save(settings: AppSettings) {
            this.settings = settings
        }

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

    @Test
    fun testIsJobPendingOrRunning() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val setupJob = BackgroundJob(
            id = "setup_com.wip.ocr_ia",
            name = "Setup OCR IA",
            type = JobType.Setup,
            pluginId = "com.wip.ocr_ia",
            capabilityName = "setup",
            keepResult = false
        )

        assertEquals(false, jobManager.isJobPendingOrRunning("setup_com.wip.ocr_ia"))

        jobManager.enqueueJob(setupJob)
        assertEquals(true, jobManager.isJobPendingOrRunning("setup_com.wip.ocr_ia"))

        val claimed = jobManager.waitForNextJob()
        assertEquals("setup_com.wip.ocr_ia", claimed.id)
        assertEquals(true, jobManager.isJobPendingOrRunning("setup_com.wip.ocr_ia"))

        jobManager.tryCompleteJob("setup_com.wip.ocr_ia", "Success")
        assertEquals(false, jobManager.isJobPendingOrRunning("setup_com.wip.ocr_ia"))
    }

    @Test
    fun testEnqueueReplacesExistingFailedOrEndedJobWithSameId() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val setupJob = BackgroundJob(
            id = "setup_com.wip.cleaner",
            name = "Setup Cleaner",
            type = JobType.Setup,
            pluginId = "com.wip.cleaner",
            capabilityName = "setup",
            keepResult = false
        )

        jobManager.enqueueJob(setupJob)
        val claimed = jobManager.waitForNextJob()
        jobManager.tryFailJob(claimed.id, "Network failed")

        // Job is in endedJobs now
        assertEquals(1, jobManager.endedJobs.value.size)
        assertEquals("setup_com.wip.cleaner", jobManager.endedJobs.value[0].id)
        assertEquals(0, jobManager.jobs.value.size)

        // Re-enqueue the same job ID
        val retryJob = BackgroundJob(
            id = "setup_com.wip.cleaner",
            name = "Setup Cleaner Retry",
            type = JobType.Setup,
            pluginId = "com.wip.cleaner",
            capabilityName = "setup",
            keepResult = false
        )
        jobManager.enqueueJob(retryJob)

        assertEquals(1, jobManager.jobs.value.size)
        assertEquals("setup_com.wip.cleaner", jobManager.jobs.value[0].id)
        assertEquals(0, jobManager.endedJobs.value.size)

        val claimedRetry = jobManager.waitForNextJob()
        assertEquals("setup_com.wip.cleaner", claimedRetry.id)
    }

    @Test
    fun testJobLogsTruncateToMaxLogLines() = runTest {
        val persistence = FakeSettingsPersistence()
        persistence.settings = persistence.settings.copy(
            jobs = persistence.settings.jobs.copy(maxLogLines = 5)
        )
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.isLoaded.first { it }
        val jobManager = JobManager(backgroundScope, settingsRepo)

        for (i in 1..10) {
            jobManager.addJobLog("job-lines", "Message $i")
        }

        val logs = jobManager.jobLogs.value["job-lines"]
        assertEquals(5, logs?.size)
        assertTrue(logs?.first()?.endsWith("Message 6") == true)
        assertTrue(logs?.last()?.endsWith("Message 10") == true)
    }

    @Test
    fun testJobLogsTruncateToMaxLogLineLength() = runTest {
        val persistence = FakeSettingsPersistence()
        persistence.settings = persistence.settings.copy(
            jobs = persistence.settings.jobs.copy(maxLogLineLength = 50)
        )
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.isLoaded.first { it }
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val longMessage = "A".repeat(200)
        jobManager.addJobLog("job-long", longMessage)

        val logs = jobManager.jobLogs.value["job-long"]
        assertEquals(1, logs?.size)
        val logLine = logs?.first().orEmpty()
        assertTrue(logLine.endsWith("... [truncated]"))
        assertEquals(50 + "... [truncated]".length, logLine.length)
    }

    @Test
    fun testJobLogsMultilineMessageSplitting() = runTest {
        val persistence = FakeSettingsPersistence()
        persistence.settings = persistence.settings.copy(
            jobs = persistence.settings.jobs.copy(maxLogLines = 5, maxLogLineLength = 200)
        )
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.isLoaded.first { it }
        val jobManager = JobManager(backgroundScope, settingsRepo)

        jobManager.addJobLog("job-multi", "Line 1\nLine 2\nLine 3\nLine 4")
        val initialLogs = jobManager.jobLogs.value["job-multi"]
        assertEquals(4, initialLogs?.size)

        jobManager.addJobLog("job-multi", "Line 5\nLine 6\nLine 7")
        val prunedLogs = jobManager.jobLogs.value["job-multi"]
        assertEquals(5, prunedLogs?.size)
        assertTrue(prunedLogs?.get(0)?.contains("Line 3") == true)
        assertTrue(prunedLogs?.get(1)?.contains("Line 4") == true)
        assertTrue(prunedLogs?.get(2)?.contains("Line 5") == true)
        assertTrue(prunedLogs?.get(3)?.contains("Line 6") == true)
        assertTrue(prunedLogs?.get(4)?.contains("Line 7") == true)
    }

    @Test
    fun testJobLogsUnlimitedSettings() = runTest {
        val persistence = FakeSettingsPersistence()
        persistence.settings = persistence.settings.copy(
            jobs = persistence.settings.jobs.copy(maxLogLines = -1, maxLogLineLength = -1)
        )
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.isLoaded.first { it }
        val jobManager = JobManager(backgroundScope, settingsRepo)

        for (i in 1..25) {
            jobManager.addJobLog("job-unlimited", "Message $i")
        }
        val longMessage = "B".repeat(500)
        jobManager.addJobLog("job-unlimited", longMessage)

        val logs = jobManager.jobLogs.value["job-unlimited"]
        assertEquals(26, logs?.size)
        val lastLog = logs?.last().orEmpty()
        assertTrue(lastLog.endsWith(longMessage))
        assertTrue(!lastLog.contains("... [truncated]"))
    }

    @Test
    fun testProgressLoggingDeduplication() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val job = BackgroundJob(
            id = "progress-job",
            name = "Progress Job",
            type = JobType.Capability,
            pluginId = "test-plugin",
            capabilityName = "test-cap"
        )
        jobManager.enqueueJob(job)
        val claimed = jobManager.waitForNextJob()

        // 1. First progress update at 0.12 (12%) -> should log
        jobManager.updateJobProgress(claimed.id, 0.12f)
        var logs = jobManager.jobLogs.value[claimed.id].orEmpty()
        assertEquals(1, logs.size)
        assertTrue(logs[0].endsWith("Progress: 12%"))

        // 2. Minor change that still rounds to 12% (0.1204f) -> should be deduplicated (no new log)
        jobManager.updateJobProgress(claimed.id, 0.1204f)
        logs = jobManager.jobLogs.value[claimed.id].orEmpty()
        assertEquals(1, logs.size)

        // 3. Update to 0.125f -> 12.5% -> should log
        jobManager.updateJobProgress(claimed.id, 0.125f)
        logs = jobManager.jobLogs.value[claimed.id].orEmpty()
        assertEquals(2, logs.size)
        assertTrue(logs[1].endsWith("Progress: 12.5%"))

        // 4. Minor change that still rounds to 12.5% (0.1253f) -> deduplicated
        jobManager.updateJobProgress(claimed.id, 0.1253f)
        logs = jobManager.jobLogs.value[claimed.id].orEmpty()
        assertEquals(2, logs.size)

        // 5. Update to 1.0f (100%) -> should log
        jobManager.updateJobProgress(claimed.id, 1.0f)
        logs = jobManager.jobLogs.value[claimed.id].orEmpty()
        assertEquals(3, logs.size)
        assertTrue(logs[2].endsWith("Progress: 100%"))
    }

    @Test
    fun testExecutionMetricsPopulatedOnJobComplete() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val job = BackgroundJob(
            id = "metrics-job",
            name = "Metrics Job",
            type = JobType.Flow,
            pluginId = "test-plugin",
            capabilityName = "flow"
        )
        jobManager.enqueueJob(job)
        val claimed = jobManager.waitForNextJob()

        jobManager.recordCapabilityMetric(claimed.id, "capabilityA", 150L, 1024L)
        jobManager.recordCapabilityMetric(claimed.id, "capabilityA", 200L, 2048L)
        jobManager.recordCapabilityMetric(claimed.id, "capabilityB", 300L, 4096L)

        jobManager.tryCompleteJob(claimed.id, "Success")

        val endedJobs = jobManager.endedJobs.value
        assertEquals(1, endedJobs.size)
        val endedJob = endedJobs.first()
        val metrics = endedJob.executionMetrics
        assertNotNull(metrics)

        assertEquals(3, metrics.capabilityMetrics.size)
        assertTrue(metrics.totalDurationMs >= 0L)
        assertTrue(metrics.memoryUsageBytes != null && metrics.memoryUsageBytes >= 4096L)
        assertEquals(350L, metrics.totalDurationPerCapability["capabilityA"])
        assertEquals(2, metrics.executionCountPerCapability["capabilityA"])
        assertEquals(300L, metrics.totalDurationPerCapability["capabilityB"])
        assertEquals(1, metrics.executionCountPerCapability["capabilityB"])
    }

    @Test
    fun testExecutionMetricsPopulatedOnJobCancel() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val job = BackgroundJob(
            id = "cancel-metrics-job",
            name = "Cancel Metrics Job",
            type = JobType.Capability,
            pluginId = "test-plugin",
            capabilityName = "test-cap"
        )
        jobManager.enqueueJob(job)
        val claimed = jobManager.waitForNextJob()

        jobManager.recordCapabilityMetric(claimed.id, "test-cap", 500L, 8192L)
        jobManager.cancelJob(claimed.id)

        val endedJobs = jobManager.endedJobs.value
        assertEquals(1, endedJobs.size)
        val endedJob = endedJobs.first()
        val metrics = endedJob.executionMetrics
        assertNotNull(metrics)
        assertEquals(1, metrics.capabilityMetrics.size)
        assertEquals(500L, metrics.totalDurationPerCapability["test-cap"])
        assertTrue(metrics.memoryUsageBytes != null && metrics.memoryUsageBytes >= 8192L)
    }
}
