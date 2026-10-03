package org.wip.plugintoolkit.features.job.logic

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class JobResourceSamplerTest {

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
    fun testJobResourceSamplerLifecycleAndMetrics() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val manager = JobManager(backgroundScope, settingsRepo)

        val sampler = JobResourceSampler(
            jobId = "test-job-123",
            samplingIntervalMs = 100L,
            getActivePids = { emptyList() },
            manager = manager
        )

        // Initial snapshot recorded
        assertTrue(sampler.getSamples().isNotEmpty(), "Sampler should have captured initial baseline sample")
        val initialSample = sampler.getSamples().first()
        assertTrue(initialSample.ramUsageBytes >= 0L)

        // Event-driven node transition snapshots
        val step1Sample = sampler.recordSnapshot(activeCapability = "image_processor", activeNodeId = "101")
        assertEquals("image_processor", step1Sample.activeCapability)
        assertEquals("101", step1Sample.activeNodeId)

        val step2Sample = sampler.recordSnapshot(activeCapability = "ocr_reader", activeNodeId = "102")
        assertEquals("ocr_reader", step2Sample.activeCapability)
        assertEquals("102", step2Sample.activeNodeId)

        // Finish sampler
        val finalMetrics = sampler.finish(activeCapability = "ocr_reader", activeNodeId = "102")
        assertTrue(finalMetrics.peakMemoryBytes >= 0L)
        assertTrue(finalMetrics.timeline.size >= 3, "Timeline should contain all snapshots")

        // Samples recorded in JobManager timeline
        val timelineInManager = manager.getResourceTimeline("test-job-123")
        assertTrue(timelineInManager.isNotEmpty(), "JobManager should have recorded the timeline samples")
    }
}
