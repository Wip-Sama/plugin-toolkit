package org.wip.plugintoolkit.features.job.logic

import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.model.AppSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JobRepositoryRecoveryTest {

    private val testDir = Path("build/test_job_repo_${kotlin.random.Random.nextInt(100000)}")

    private val fakePersistence = object : SettingsPersistence {
        override suspend fun load(): AppSettings = AppSettings()
        override suspend fun save(settings: AppSettings) {}
        override fun getSettingsDir(): String = testDir.toString()
        override fun getJobsDir(): String = "$testDir/jobs"
        override fun openLogFolder() {}
        override fun openLatestLog() {}
    }

    @BeforeTest
    fun setup() {
        if (!SystemFileSystem.exists(testDir)) {
            SystemFileSystem.createDirectories(testDir)
        }
    }

    @AfterTest
    fun cleanup() {
        try {
            if (SystemFileSystem.exists(testDir)) {
                deleteRecursively(testDir)
            }
        } catch (_: Exception) {}
    }

    private fun deleteRecursively(path: Path) {
        if (!SystemFileSystem.exists(path)) return
        val metadata = SystemFileSystem.metadataOrNull(path) ?: return
        if (metadata.isDirectory) {
            SystemFileSystem.list(path).forEach { child ->
                deleteRecursively(child)
            }
        }
        SystemFileSystem.delete(path, mustExist = false)
    }

    @Test
    fun testRecoverJobsWithVariousStatuses() = runTest {
        val repo = JobRepository(fakePersistence)

        val jobRunning = BackgroundJob(id = "job-run", name = "Running Job", type = JobType.Flow, status = JobStatus.Running, pluginId = "system", capabilityName = "test-flow")
        val jobQueued = BackgroundJob(id = "job-queue", name = "Queued Job", type = JobType.Flow, status = JobStatus.Queued, pluginId = "system", capabilityName = "test-flow")
        val jobPaused = BackgroundJob(id = "job-paused", name = "Paused Job", type = JobType.Flow, status = JobStatus.Paused, pluginId = "system", capabilityName = "test-flow", resumeState = JsonPrimitive("state-1"))
        val jobPauseReqWithState = BackgroundJob(id = "job-pause-req-state", name = "PauseReq with State", type = JobType.Flow, status = JobStatus.PauseRequested, pluginId = "system", capabilityName = "test-flow", resumeState = JsonPrimitive("state-2"))
        val jobPauseReqWithoutState = BackgroundJob(id = "job-pause-req-no-state", name = "PauseReq without State", type = JobType.Flow, status = JobStatus.PauseRequested, pluginId = "system", capabilityName = "test-flow", resumeState = null)

        repo.saveJobs(listOf(jobRunning, jobQueued, jobPaused, jobPauseReqWithState, jobPauseReqWithoutState))

        val loaded = repo.loadJobs()
        assertEquals(5, loaded.size)

        val loadedRunning = loaded.find { it.id == "job-run" }
        assertNotNull(loadedRunning)
        assertEquals(JobStatus.Failed, loadedRunning.status)

        val loadedQueued = loaded.find { it.id == "job-queue" }
        assertNotNull(loadedQueued)
        assertEquals(JobStatus.Failed, loadedQueued.status)

        val loadedPaused = loaded.find { it.id == "job-paused" }
        assertNotNull(loadedPaused)
        assertEquals(JobStatus.Paused, loadedPaused.status)

        val loadedPauseReqWithState = loaded.find { it.id == "job-pause-req-state" }
        assertNotNull(loadedPauseReqWithState)
        assertEquals(JobStatus.Paused, loadedPauseReqWithState.status)

        val loadedPauseReqWithoutState = loaded.find { it.id == "job-pause-req-no-state" }
        assertNotNull(loadedPauseReqWithoutState)
        assertEquals(JobStatus.Failed, loadedPauseReqWithoutState.status)
    }
}
