package org.wip.plugintoolkit.features.job.logic

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.wip.plugintoolkit.api.ProgressData
import org.wip.plugintoolkit.api.ProgressDisplayMode
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.logic.ResourceUsageTracker
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class JobProgressAndResourceTrackingTest {

    private class MockSettingsPersistence : SettingsPersistence {
        private var settings = AppSettings()
        override suspend fun load(): AppSettings = settings
        override suspend fun save(settings: AppSettings) { this.settings = settings }
        override fun getSettingsDir(): String = "test-settings-dir"
        override fun getJobsDir(): String = "test-jobs-dir"
        override fun openLogFolder() {}
        override fun openLatestLog() {}
    }

    @Test
    fun testResourceUsageTracker() {
        val tracker = ResourceUsageTracker()
        assertEquals(0L, tracker.bytesRead)
        assertEquals(0L, tracker.bytesWritten)
        assertEquals(0L, tracker.networkBytesRead)
        assertEquals(0L, tracker.networkBytesWritten)
        assertNull(tracker.averageThroughputBytesPerSec)

        tracker.recordFileRead(1024L)
        tracker.recordFileWrite(2048L)
        assertEquals(1024L, tracker.bytesRead)
        assertEquals(2048L, tracker.bytesWritten)

        tracker.recordNetworkUsage(read = 5000L, written = 500L, throughputBytesPerSec = 1_000_000L)
        tracker.recordNetworkUsage(read = 3000L, written = 300L, throughputBytesPerSec = 2_000_000L)

        assertEquals(8000L, tracker.networkBytesRead)
        assertEquals(800L, tracker.networkBytesWritten)
        assertEquals(1_500_000L, tracker.averageThroughputBytesPerSec)
    }

    @Test
    fun testJobManagerProgressUpdates() = runTest {
        val testScope = TestScope()
        val persistence = MockSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, testScope)
        val jobManager = JobManager(testScope, settingsRepo)

        val job = BackgroundJob(
            id = "progress-job-1",
            name = "Progress Job",
            type = JobType.Capability,
            pluginId = "test.plugin",
            capabilityName = "testCap"
        )
        jobManager.enqueueJob(job)

        // Primary ratio progress
        jobManager.updateJobProgress(
            "progress-job-1",
            ProgressData.ratio(12.3, 14.5, "MB", "Downloading package - 2.5 MB/s")
        )
        val p1 = jobManager.jobProgress.first()["progress-job-1"]
        assertNotNull(p1)
        assertEquals(ProgressDisplayMode.RATIO, p1.mainDisplayMode)
        assertEquals(12.3, p1.mainCurrent)
        assertEquals(14.5, p1.mainTotal)
        assertEquals("MB", p1.mainUnit)
        assertEquals("Downloading package - 2.5 MB/s", p1.mainMessage)

        // Secondary progress
        jobManager.updateJobSecondaryProgress(
            "progress-job-1",
            ProgressData.percentage(0.4f, "Rate limit backoff: 3s remaining")
        )
        val p2 = jobManager.jobProgress.first()["progress-job-1"]
        assertNotNull(p2)
        assertEquals(0.4f, p2.secondaryProgress)
        assertEquals("Rate limit backoff: 3s remaining", p2.secondaryMessage)

        // Clear secondary progress
        jobManager.clearJobSecondaryProgress("progress-job-1")
        val p3 = jobManager.jobProgress.first()["progress-job-1"]
        assertNotNull(p3)
        assertNull(p3.secondaryProgress)
        assertNull(p3.secondaryMessage)

        // Capability detailed progress
        jobManager.updateCapabilityProgress(
            "progress-job-1",
            "subTaskA",
            ProgressData.ratio(5.0, 10.0, "items", "Processing batch")
        )
        val p4 = jobManager.jobProgress.first()["progress-job-1"]
        assertNotNull(p4)
        val item = p4.capabilitiesDetailedProgress["subTaskA"]
        assertNotNull(item)
        assertEquals(0.5f, item.progress)
        assertEquals(5.0, item.current)
        assertEquals(10.0, item.total)
        assertEquals("items", item.unit)
        assertEquals("Processing batch", item.message)

        // Capability secondary progress
        jobManager.updateCapabilitySecondaryProgress(
            "progress-job-1",
            "subTaskA",
            ProgressData.indeterminate("Waiting for lock")
        )
        val p5 = jobManager.jobProgress.first()["progress-job-1"]
        assertNotNull(p5)
        val itemWithSec = p5.capabilitiesDetailedProgress["subTaskA"]
        assertNotNull(itemWithSec)
        assertEquals(ProgressDisplayMode.INDETERMINATE, itemWithSec.secondaryDisplayMode)
        assertEquals("Waiting for lock", itemWithSec.secondaryMessage)
    }

    @Test
    fun testJobManagerRecordCapabilityMetricFull() = runTest {
        val testScope = TestScope()
        val persistence = MockSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, testScope)
        val jobManager = JobManager(testScope, settingsRepo)

        jobManager.recordCapabilityMetric(
            jobId = "job-metrics-1",
            capabilityName = "cap1",
            durationMs = 1200L,
            memoryBytes = 1024L * 1024L * 20L,
            totalMemoryBytes = 1024L * 1024L * 25L,
            bytesRead = 4096L,
            bytesWritten = 8192L,
            networkBytesRead = 50000L,
            networkBytesWritten = 1000L,
            throughputBytesPerSec = 3_500_000L
        )

        assertEquals(1024L * 1024L * 20L, jobManager.getLivePeakMemory("job-metrics-1"))
    }
}
