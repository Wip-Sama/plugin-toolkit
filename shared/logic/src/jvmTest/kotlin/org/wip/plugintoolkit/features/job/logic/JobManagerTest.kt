package org.wip.plugintoolkit.features.job.logic

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppSettings
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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

        // 3. Update to 0.125f -> still 12% -> should be deduplicated (no new log)
        jobManager.updateJobProgress(claimed.id, 0.125f)
        logs = jobManager.jobLogs.value[claimed.id].orEmpty()
        assertEquals(1, logs.size)

        // 4. Update to 0.13f -> 13% -> should log
        jobManager.updateJobProgress(claimed.id, 0.13f)
        logs = jobManager.jobLogs.value[claimed.id].orEmpty()
        assertEquals(2, logs.size)
        assertTrue(logs[1].endsWith("Progress: 13%"))

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
        assertEquals(7168L, metrics.totalMemoryUsageBytes)
        assertEquals(7168L, metrics.effectiveTotalMemoryUsageBytes)
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
        assertEquals(8192L, metrics.totalMemoryUsageBytes)
        assertEquals(8192L, metrics.effectiveTotalMemoryUsageBytes)
    }

    @Test
    fun testExecutionMetricsPopulatedOnJobFail() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val job = BackgroundJob(
            id = "fail-metrics-job",
            name = "Fail Metrics Job",
            type = JobType.Capability,
            pluginId = "test-plugin",
            capabilityName = "test-cap"
        )
        jobManager.enqueueJob(job)
        val claimed = jobManager.waitForNextJob()

        jobManager.recordCapabilityMetric(claimed.id, "test-cap", 250L, 5000L)
        jobManager.tryFailJob(claimed.id, "Error")

        val endedJobs = jobManager.endedJobs.value
        assertEquals(1, endedJobs.size)
        val endedJob = endedJobs.first()
        val metrics = endedJob.executionMetrics
        assertNotNull(metrics)
        assertEquals(1, metrics.capabilityMetrics.size)
        assertTrue(metrics.memoryUsageBytes != null && metrics.memoryUsageBytes >= 5000L)
        assertEquals(5000L, metrics.totalMemoryUsageBytes)
        assertEquals(5000L, metrics.effectiveTotalMemoryUsageBytes)
    }

    @Test
    fun testPauseRequestedStateTransitions() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val jobManager = JobManager(backgroundScope, settingsRepo)

        // 1. Job completes while PauseRequested
        val job1 = BackgroundJob(
            id = "pause-complete-job",
            name = "Job 1",
            type = JobType.Capability,
            pluginId = "test-plugin",
            capabilityName = "cap1"
        )
        jobManager.enqueueJob(job1)
        val claimed1 = jobManager.waitForNextJob()
        jobManager.pauseJob(claimed1.id)

        // Verify status is PauseRequested
        val runningJob1 = jobManager.jobs.value.find { it.id == claimed1.id }
        assertNotNull(runningJob1)
        assertEquals(JobStatus.PauseRequested, runningJob1.status)

        // Completion while PauseRequested should succeed
        val completed1 = jobManager.tryCompleteJob(claimed1.id, "Finished before pause acknowledged")
        assertTrue(completed1)
        val ended1 = jobManager.endedJobs.value.find { it.id == claimed1.id }
        assertNotNull(ended1)
        assertEquals(JobStatus.Completed, ended1.status)

        // 2. Job fails while PauseRequested
        val job2 = BackgroundJob(
            id = "pause-fail-job",
            name = "Job 2",
            type = JobType.Capability,
            pluginId = "test-plugin",
            capabilityName = "cap2"
        )
        jobManager.enqueueJob(job2)
        val claimed2 = jobManager.waitForNextJob()
        jobManager.pauseJob(claimed2.id)

        val failed2 = jobManager.tryFailJob(claimed2.id, "Error before pause acknowledged")
        assertTrue(failed2)
        val ended2 = jobManager.endedJobs.value.find { it.id == claimed2.id }
        assertNotNull(ended2)
        assertEquals(JobStatus.Failed, ended2.status)

        // 3. Job acknowledges pause -> Paused
        val job3 = BackgroundJob(
            id = "pause-acknowledged-job",
            name = "Job 3",
            type = JobType.Capability,
            pluginId = "test-plugin",
            capabilityName = "cap3"
        )
        jobManager.enqueueJob(job3)
        val claimed3 = jobManager.waitForNextJob()
        jobManager.pauseJob(claimed3.id)

        val paused3 = jobManager.tryPauseJob(claimed3.id, JsonPrimitive("state_snapshot"))
        assertTrue(paused3)
        val runningJob3 = jobManager.jobs.value.find { it.id == claimed3.id }
        assertNotNull(runningJob3)
        assertEquals(JobStatus.Paused, runningJob3.status)
        assertEquals(JsonPrimitive("state_snapshot"), runningJob3.resumeState)
    }

    @Test
    fun testMaxConcurrentJobsEnforcement() = runTest {
        val persistence = FakeSettingsPersistence()
        persistence.settings = AppSettings().copy(
            jobs = AppSettings().jobs.copy(maxConcurrentJobs = 1)
        )
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.isLoaded.first { it }
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val job1 = BackgroundJob(
            id = "concurrent-job-1",
            name = "Job 1",
            type = JobType.Capability,
            pluginId = "test-plugin",
            capabilityName = "cap1"
        )
        val job2 = BackgroundJob(
            id = "concurrent-job-2",
            name = "Job 2",
            type = JobType.Capability,
            pluginId = "test-plugin",
            capabilityName = "cap2"
        )

        jobManager.enqueueJob(job1)
        jobManager.enqueueJob(job2)

        // Claim first job
        val claimed1 = jobManager.waitForNextJob()
        assertEquals("concurrent-job-1", claimed1.id)
        assertEquals(JobStatus.Running, claimed1.status)

        // Second job is still Queued, cannot be claimed because limit = 1
        val queuedJob = jobManager.jobs.value.find { it.id == "concurrent-job-2" }
        assertNotNull(queuedJob)
        assertEquals(JobStatus.Queued, queuedJob.status)

        val claimed2Attempt = withTimeoutOrNull(200) {
            jobManager.waitForNextJob()
        }
        assertNull(claimed2Attempt, "Second job should not be claimable while first is running and limit is 1")

        // Complete job 1 -> frees slot and wakes up next job
        jobManager.tryCompleteJob("concurrent-job-1", "Done")

        // Now job 2 should be claimed
        val claimed2 = withTimeoutOrNull(1000) {
            jobManager.waitForNextJob()
        }
        assertNotNull(claimed2)
        assertEquals("concurrent-job-2", claimed2.id)
        assertEquals(JobStatus.Running, claimed2.status)

        jobManager.tryCompleteJob("concurrent-job-2", "Done")
    }

    @Test
    fun testLiveMaxConcurrentJobsIncrease() = runTest {
        val persistence = FakeSettingsPersistence()
        persistence.settings = AppSettings().copy(
            jobs = AppSettings().jobs.copy(maxConcurrentJobs = 1)
        )
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.isLoaded.first { it }
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val job1 = BackgroundJob(id = "live-inc-1", name = "Job 1", type = JobType.Capability, pluginId = "test-plugin", capabilityName = "cap1")
        val job2 = BackgroundJob(id = "live-inc-2", name = "Job 2", type = JobType.Capability, pluginId = "test-plugin", capabilityName = "cap2")

        jobManager.enqueueJob(job1)
        jobManager.enqueueJob(job2)

        val claimed1 = jobManager.waitForNextJob()
        assertEquals("live-inc-1", claimed1.id)

        // Cannot claim job2 under limit 1
        val attemptBefore = withTimeoutOrNull(200) { jobManager.waitForNextJob() }
        assertNull(attemptBefore)

        // Increase limit live to 2
        settingsRepo.updateSettings { current -> current.copy(jobs = current.jobs.copy(maxConcurrentJobs = 2)) }

        // Now job2 can be claimed while job1 is still running!
        val claimed2 = withTimeoutOrNull(1000) { jobManager.waitForNextJob() }
        assertNotNull(claimed2)
        assertEquals("live-inc-2", claimed2.id)

        val runningJobs = jobManager.jobs.value.filter { it.status == JobStatus.Running }
        assertEquals(2, runningJobs.size)

        jobManager.tryCompleteJob("live-inc-1", "Done")
        jobManager.tryCompleteJob("live-inc-2", "Done")
    }

    @Test
    fun testLiveMaxConcurrentJobsDecreaseLocksNewJobs() = runTest {
        val persistence = FakeSettingsPersistence()
        persistence.settings = AppSettings().copy(
            jobs = AppSettings().jobs.copy(maxConcurrentJobs = 2)
        )
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.isLoaded.first { it }
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val job1 = BackgroundJob(id = "live-dec-1", name = "Job 1", type = JobType.Capability, pluginId = "test-plugin", capabilityName = "cap1")
        val job2 = BackgroundJob(id = "live-dec-2", name = "Job 2", type = JobType.Capability, pluginId = "test-plugin", capabilityName = "cap2")
        val job3 = BackgroundJob(id = "live-dec-3", name = "Job 3", type = JobType.Capability, pluginId = "test-plugin", capabilityName = "cap3")

        jobManager.enqueueJob(job1)
        jobManager.enqueueJob(job2)
        jobManager.enqueueJob(job3)

        val claimed1 = jobManager.waitForNextJob()
        val claimed2 = jobManager.waitForNextJob()
        assertEquals("live-dec-1", claimed1.id)
        assertEquals("live-dec-2", claimed2.id)

        // 2 jobs are currently running. Decrease limit live to 1
        settingsRepo.updateSettings { current -> current.copy(jobs = current.jobs.copy(maxConcurrentJobs = 1)) }

        // Finish job1 -> running count becomes 1 (which equals the new limit 1)
        jobManager.tryCompleteJob("live-dec-1", "Done")

        // Since running count is 1 and maxConcurrentJobs is 1, job3 must NOT start!
        val attemptJob3 = withTimeoutOrNull(200) { jobManager.waitForNextJob() }
        assertNull(attemptJob3, "Job 3 should be locked until running jobs drop below new limit")

        // Now complete job2 -> running count becomes 0
        jobManager.tryCompleteJob("live-dec-2", "Done")

        // Now job3 can start!
        val claimed3 = withTimeoutOrNull(1000) { jobManager.waitForNextJob() }
        assertNotNull(claimed3)
        assertEquals("live-dec-3", claimed3.id)

        jobManager.tryCompleteJob("live-dec-3", "Done")
    }

    @Test
    fun testTryFailAndTryPauseSignalWaitingWorkers() = runTest {
        val persistence = FakeSettingsPersistence()
        persistence.settings = AppSettings().copy(
            jobs = AppSettings().jobs.copy(maxConcurrentJobs = 1)
        )
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.isLoaded.first { it }
        val jobManager = JobManager(backgroundScope, settingsRepo)

        // Test fail wake-up
        val job1 = BackgroundJob(id = "signal-fail-1", name = "Job 1", type = JobType.Capability, pluginId = "test-plugin", capabilityName = "cap1")
        val job2 = BackgroundJob(id = "signal-fail-2", name = "Job 2", type = JobType.Capability, pluginId = "test-plugin", capabilityName = "cap2")
        jobManager.enqueueJob(job1)
        jobManager.enqueueJob(job2)

        val claimed1 = jobManager.waitForNextJob()
        assertEquals("signal-fail-1", claimed1.id)

        // Failing job1 frees slot and wakes up job2
        jobManager.tryFailJob("signal-fail-1", "Failed deliberately")
        val claimed2 = withTimeoutOrNull(1000) { jobManager.waitForNextJob() }
        assertNotNull(claimed2)
        assertEquals("signal-fail-2", claimed2.id)

        // Test pause wake-up
        val job3 = BackgroundJob(id = "signal-pause-3", name = "Job 3", type = JobType.Capability, pluginId = "test-plugin", capabilityName = "cap3")
        jobManager.enqueueJob(job3)

        // Pausing job2 frees slot and wakes up job3
        jobManager.tryPauseJob("signal-fail-2", JsonPrimitive("paused"))
        val claimed3 = withTimeoutOrNull(1000) { jobManager.waitForNextJob() }
        assertNotNull(claimed3)
        assertEquals("signal-pause-3", claimed3.id)

        jobManager.tryCompleteJob("signal-pause-3", "Done")
    }

    @Test
    fun testPausedJobsDoNotCountTowardsMaxConcurrentJobs() = runTest {
        val persistence = FakeSettingsPersistence()
        persistence.settings = AppSettings().copy(
            jobs = AppSettings().jobs.copy(maxConcurrentJobs = 1)
        )
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.isLoaded.first { it }
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val job1 = BackgroundJob(id = "paused-test-1", name = "Job 1", type = JobType.Capability, pluginId = "test-plugin", capabilityName = "cap1")
        val job2 = BackgroundJob(id = "paused-test-2", name = "Job 2", type = JobType.Capability, pluginId = "test-plugin", capabilityName = "cap2")

        jobManager.enqueueJob(job1)
        jobManager.enqueueJob(job2)

        // Claim job 1
        val claimed1 = jobManager.waitForNextJob()
        assertEquals("paused-test-1", claimed1.id)
        assertEquals(JobStatus.Running, claimed1.status)

        // Job 2 cannot be claimed because limit = 1 and job 1 is running
        val attempt1 = withTimeoutOrNull(200) { jobManager.waitForNextJob() }
        assertNull(attempt1, "Job 2 should not run while Job 1 is running under limit 1")

        // Request pause on running job 1 -> it transitions to PauseRequested
        jobManager.pauseJob("paused-test-1")
        val job1PauseReq = jobManager.jobs.value.find { it.id == "paused-test-1" }
        assertNotNull(job1PauseReq)
        assertEquals(JobStatus.PauseRequested, job1PauseReq.status)

        // Pause-requested jobs are still running so they should count towards the max concurrent job limit until fully stopped
        val attemptWhilePauseRequested = withTimeoutOrNull(200) { jobManager.waitForNextJob() }
        assertNull(attemptWhilePauseRequested, "Job 2 should not run while Job 1 is PauseRequested under limit 1")

        // Fully stop and pause job 1 with resume state -> it becomes Paused
        jobManager.tryPauseJob("paused-test-1", JsonPrimitive("resume-state-1"))
        val job1Paused = jobManager.jobs.value.find { it.id == "paused-test-1" }
        assertNotNull(job1Paused)
        assertEquals(JobStatus.Paused, job1Paused.status)

        // Job 1 is still in jobs list, but Paused jobs do NOT count towards maxConcurrentJobs.
        // Therefore, job 2 should now be claimable!
        val claimed2 = withTimeoutOrNull(1000) { jobManager.waitForNextJob() }
        assertNotNull(claimed2, "Job 2 should be claimable after Job 1 is paused")
        assertEquals("paused-test-2", claimed2.id)
        assertEquals(JobStatus.Running, claimed2.status)

        // Resume job 1 -> it moves to Queued
        jobManager.resumeJob("paused-test-1")
        val job1Resumed = jobManager.jobs.value.find { it.id == "paused-test-1" }
        assertNotNull(job1Resumed)
        assertEquals(JobStatus.Queued, job1Resumed.status)

        // Job 1 cannot be claimed yet because job 2 is currently running under limit 1
        val attempt2 = withTimeoutOrNull(200) { jobManager.waitForNextJob() }
        assertNull(attempt2, "Job 1 should not run while Job 2 is running under limit 1")

        // Complete job 2 -> slot frees up
        jobManager.tryCompleteJob("paused-test-2", "Done")

        // Job 1 can now be claimed again
        val claimed1Again = withTimeoutOrNull(1000) { jobManager.waitForNextJob() }
        assertNotNull(claimed1Again)
        assertEquals("paused-test-1", claimed1Again.id)

        jobManager.tryCompleteJob("paused-test-1", "Done")
    }

    @Test
    fun testQueuedJobPauseImmediatelyTransitionsToPaused() = runTest {
        val persistence = FakeSettingsPersistence()
        persistence.settings = AppSettings().copy(
            jobs = AppSettings().jobs.copy(maxConcurrentJobs = 1)
        )
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.isLoaded.first { it }
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val job1 = BackgroundJob(id = "q-pause-1", name = "Job 1", type = JobType.Capability, pluginId = "test-plugin", capabilityName = "cap1")
        val job2 = BackgroundJob(id = "q-pause-2", name = "Job 2", type = JobType.Capability, pluginId = "test-plugin", capabilityName = "cap2")

        jobManager.enqueueJob(job1)
        jobManager.enqueueJob(job2)

        val claimed1 = jobManager.waitForNextJob()
        assertEquals("q-pause-1", claimed1.id)

        // Pausing queued job 2 must transition directly to JobStatus.Paused (NOT stuck in PauseRequested)
        jobManager.pauseJob("q-pause-2")
        val job2State = jobManager.jobs.value.find { it.id == "q-pause-2" }
        assertNotNull(job2State)
        assertEquals(JobStatus.Paused, job2State.status)

        // Active job IDs includes Paused jobs
        val activeIds = withTimeoutOrNull(1000) { jobManager.activeJobIds.first { it.contains("q-pause-2") } }
        assertNotNull(activeIds)
        assertTrue(activeIds.contains("q-pause-2"))
        assertTrue(jobManager.isJobPendingOrRunning("q-pause-2"))

        // Resuming queued job 2 transitions back to Queued
        jobManager.resumeJob("q-pause-2")
        val job2Resumed = jobManager.jobs.value.find { it.id == "q-pause-2" }
        assertNotNull(job2Resumed)
        assertEquals(JobStatus.Queued, job2Resumed.status)

        jobManager.tryCompleteJob("q-pause-1", "Done")

        val claimed2 = withTimeoutOrNull(1000) { jobManager.waitForNextJob() }
        assertNotNull(claimed2)
        assertEquals("q-pause-2", claimed2.id)

        jobManager.tryCompleteJob("q-pause-2", "Done")
    }

    @Test
    fun testFlowConcurrencyEnforcement() = runTest {
        val persistence = FakeSettingsPersistence()
        persistence.settings = AppSettings().copy(
            jobs = AppSettings().jobs.copy(maxConcurrentJobs = 5)
        )
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.isLoaded.first { it }
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val flowJob1 = BackgroundJob(
            id = "flow-a-1",
            name = "Flow A Run 1",
            type = JobType.Flow,
            pluginId = "flow-engine",
            capabilityName = "FlowA",
            maxConcurrentExecutions = 1
        )
        val flowJob2 = BackgroundJob(
            id = "flow-a-2",
            name = "Flow A Run 2",
            type = JobType.Flow,
            pluginId = "flow-engine",
            capabilityName = "FlowA",
            maxConcurrentExecutions = 1
        )
        val flowJobB = BackgroundJob(
            id = "flow-b-1",
            name = "Flow B Run 1",
            type = JobType.Flow,
            pluginId = "flow-engine",
            capabilityName = "FlowB",
            maxConcurrentExecutions = 2
        )

        jobManager.enqueueJob(flowJob1)
        jobManager.enqueueJob(flowJob2)
        jobManager.enqueueJob(flowJobB)

        // Claim first job: should be flow-a-1
        val claimed1 = jobManager.waitForNextJob()
        assertEquals("flow-a-1", claimed1.id)
        assertEquals(JobStatus.Running, claimed1.status)

        // Second claim: flow-a-2 cannot run because FlowA limit of 1 is reached.
        // Therefore, flow-b-1 should be claimed next!
        val claimed2 = jobManager.waitForNextJob()
        assertEquals("flow-b-1", claimed2.id)
        assertEquals(JobStatus.Running, claimed2.status)

        // Now complete flow-a-1
        jobManager.tryCompleteJob("flow-a-1", "Done")

        // Now flow-a-2 can be claimed
        val claimed3 = jobManager.waitForNextJob()
        assertEquals("flow-a-2", claimed3.id)
        assertEquals(JobStatus.Running, claimed3.status)

        jobManager.tryCompleteJob("flow-a-2", "Done")
        jobManager.tryCompleteJob("flow-b-1", "Done")
    }

    @Test
    fun testCapabilityConcurrencyEnforcement() = runTest {
        val persistence = FakeSettingsPersistence()
        persistence.settings = AppSettings().copy(
            jobs = AppSettings().jobs.copy(maxConcurrentJobs = 5)
        )
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.isLoaded.first { it }
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val capJob1 = BackgroundJob(
            id = "cap-a-1",
            name = "Cap A Run 1",
            type = JobType.Capability,
            pluginId = "pkg-1",
            capabilityName = "cap-x",
            maxConcurrentExecutions = 1
        )
        val capJob2 = BackgroundJob(
            id = "cap-a-2",
            name = "Cap A Run 2",
            type = JobType.Capability,
            pluginId = "pkg-1",
            capabilityName = "cap-x",
            maxConcurrentExecutions = 1
        )
        val capJobOther = BackgroundJob(
            id = "cap-other-1",
            name = "Cap Other Run 1",
            type = JobType.Capability,
            pluginId = "pkg-1",
            capabilityName = "cap-y",
            maxConcurrentExecutions = 2
        )

        jobManager.enqueueJob(capJob1)
        jobManager.enqueueJob(capJob2)
        jobManager.enqueueJob(capJobOther)

        val claimed1 = jobManager.waitForNextJob()
        assertEquals("cap-a-1", claimed1.id)

        // cap-a-2 skipped due to concurrency limit = 1 on cap-x, cap-other-1 claimed
        val claimed2 = jobManager.waitForNextJob()
        assertEquals("cap-other-1", claimed2.id)

        jobManager.tryCompleteJob("cap-a-1", "Result")

        // Now cap-a-2 is schedulable
        val claimed3 = jobManager.waitForNextJob()
        assertEquals("cap-a-2", claimed3.id)

        jobManager.tryCompleteJob("cap-a-2", "Result")
        jobManager.tryCompleteJob("cap-other-1", "Result")
    }

    @Test
    fun testFailedFlowReprocessResume() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.isLoaded.first { it }
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val flowJob = BackgroundJob(
            id = "failed-flow-1",
            name = "Failing Flow",
            type = JobType.Flow,
            pluginId = "flow-engine",
            capabilityName = "TestFlow"
        )
        jobManager.enqueueJob(flowJob)

        val claimed = jobManager.waitForNextJob()
        assertEquals("failed-flow-1", claimed.id)

        // Fail flow with resumeState
        val failureResumeState = JsonPrimitive("saved_node_snapshot")
        val failSuccess = jobManager.tryFailJob("failed-flow-1", "Simulation error", resumeState = failureResumeState)
        assertTrue(failSuccess)

        // Must be in endedJobs
        val endedJob = jobManager.endedJobs.value.find { it.id == "failed-flow-1" }
        assertNotNull(endedJob)
        assertEquals(JobStatus.Failed, endedJob.status)
        assertEquals(failureResumeState, endedJob.resumeState)

        // Calling resumeJob on failed flow restores it to active jobs as Queued
        jobManager.resumeJob("failed-flow-1")

        // Verify it was moved back to jobs as Queued with cleared errorMessage
        val restoredJob = jobManager.jobs.value.find { it.id == "failed-flow-1" }
        assertNotNull(restoredJob)
        assertEquals(JobStatus.Queued, restoredJob.status)
        assertNull(restoredJob.errorMessage)
        assertEquals(failureResumeState, restoredJob.resumeState)

        // Should no longer be in endedJobs
        assertNull(jobManager.endedJobs.value.find { it.id == "failed-flow-1" })

        // Should be claimable again for reprocess
        val reprocessedClaimed = jobManager.waitForNextJob()
        assertEquals("failed-flow-1", reprocessedClaimed.id)
        assertEquals(JobStatus.Running, reprocessedClaimed.status)

        jobManager.tryCompleteJob("failed-flow-1", "Success")
    }

    @Test
    fun testRestartEndedJob() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.isLoaded.first { it }
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val flowJob = BackgroundJob(
            id = "ended-flow-1",
            name = "Flow: Sample",
            type = JobType.Flow,
            pluginId = "system",
            capabilityName = "SampleFlow",
            parameters = mapOf("param1" to JsonPrimitive("value1"))
        )
        jobManager.enqueueJob(flowJob)
        val claimed = jobManager.waitForNextJob()
        jobManager.tryCompleteJob(claimed.id, "Done")

        assertNotNull(jobManager.endedJobs.value.find { it.id == "ended-flow-1" })

        val restartedId = jobManager.restartJob("ended-flow-1")
        assertNotNull(restartedId)
        assertTrue(restartedId != "ended-flow-1")

        val newJob = jobManager.jobs.value.find { it.id == restartedId }
        assertNotNull(newJob)
        assertEquals(JobStatus.Queued, newJob.status)
        assertEquals("SampleFlow", newJob.capabilityName)
        assertEquals(JsonPrimitive("value1"), newJob.parameters["param1"])
        assertNull(newJob.resumeState)
        assertNull(newJob.result)
    }
}
