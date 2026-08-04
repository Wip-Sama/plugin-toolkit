package org.wip.plugintoolkit.features.job.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class JobViewModelTest {

    private class FakeSettingsPersistence : SettingsPersistence {
        var settings = AppSettings()
        override suspend fun load(): AppSettings = settings
        override suspend fun save(settings: AppSettings) {
            this.settings = settings
        }

        override fun getSettingsDir(): String = System.getProperty("java.io.tmpdir")
        override fun getJobsDir(): String = System.getProperty("java.io.tmpdir") + "/jobs"
        override fun openLogFolder() {}
        override fun openLatestLog() {}
    }

    @Test
    fun testJobViewModelDerivedFlows() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val persistence = FakeSettingsPersistence()
            val settingsRepo = SettingsRepository(persistence, backgroundScope)
            testScheduler.advanceUntilIdle()
            val jobManager = JobManager(backgroundScope, settingsRepo)
            val viewModel = JobViewModel(jobManager)

            val runningJob = BackgroundJob(
                id = "job-running",
                name = "Running Job",
                type = JobType.Capability,
                status = JobStatus.Running,
                pluginId = "plugin-1",
                capabilityName = "cap-1"
            )
            val pausedJob = BackgroundJob(
                id = "job-paused",
                name = "Paused Job",
                type = JobType.Capability,
                status = JobStatus.Paused,
                pluginId = "plugin-1",
                capabilityName = "cap-1"
            )

            var runningList = emptyList<BackgroundJob>()
            var pausedList = emptyList<BackgroundJob>()

            val collectorRunning = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { viewModel.runningJobs.collect { runningList = it } }
            val collectorPaused = backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) { viewModel.pausedJobs.collect { pausedList = it } }

            jobManager.enqueueJob(runningJob)
            jobManager.enqueueJob(pausedJob)
            testScheduler.advanceUntilIdle()

            assertTrue(runningList.any { it.id == "job-running" })
            assertEquals(1, pausedList.size)
            assertEquals("job-paused", pausedList[0].id)

            collectorRunning.cancel()
            collectorPaused.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
