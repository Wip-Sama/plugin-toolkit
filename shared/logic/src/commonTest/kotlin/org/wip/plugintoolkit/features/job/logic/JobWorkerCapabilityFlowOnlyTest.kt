package org.wip.plugintoolkit.features.job.logic

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.CapabilityContext
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.api.Requirements
import org.wip.plugintoolkit.core.utils.DefaultSemanticRegistry
import org.wip.plugintoolkit.core.utils.SemanticRegistry
import org.wip.plugintoolkit.features.flows.JobWorkerFlowTestBase
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.logic.PluginLifecycleCoordinator
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class JobWorkerCapabilityFlowOnlyTest : JobWorkerFlowTestBase() {

    @AfterTest
    override fun tearDownTest() {
        super.tearDownTest()
        stopKoin()
        unmockkAll()
    }

    @Test
    fun testFlowOnlyCapabilityRejectedOnDirectExecution() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.updateSettings { persistence.settings }
        val jobManager = JobManager(backgroundScope, settingsRepo)

        val mockPluginManager = mockk<PluginManager>(relaxed = true)
        val mockLifecycleCoordinator = mockk<PluginLifecycleCoordinator>(relaxed = true)

        stopKoin()
        startKoin {
            modules(module {
                single<SettingsPersistence> { persistence }
                single { settingsRepo }
                single { mockPluginManager }
                single { mockLifecycleCoordinator }
                single<SemanticRegistry> { DefaultSemanticRegistry() }
            })
        }

        mockkObject(PluginLoader)
        val mockPluginEntry = mockk<PluginEntry>(relaxed = true)
        every { PluginLoader.getPluginById("test-plugin") } returns mockPluginEntry

        val flowOnlyCap = Capability(
            name = "flow_only_cap",
            description = "A capability only available in flows",
            returnType = org.wip.plugintoolkit.api.DataType.Primitive(org.wip.plugintoolkit.api.PrimitiveType.STRING),
            context = CapabilityContext.FLOW_ONLY
        )
        val manifest = PluginManifest(
            manifestVersion = "1.0",
            plugin = PluginInfo("test-plugin", "Test Plugin", "1.0.0", "Test"),
            capabilities = listOf(flowOnlyCap),
            requirements = Requirements(128, 1000)
        )
        every { mockPluginEntry.getManifest() } returns Result.success(manifest)

        val job = BackgroundJob(
            id = "test-job-flow-only",
            name = "Direct Flow Only Job",
            type = JobType.Capability,
            pluginId = "test-plugin",
            capabilityName = "flow_only_cap"
        )

        jobManager.enqueueJob(job)
        val jobWorker = JobWorker(1, jobManager, backgroundScope)
        jobWorker.start()

        withTimeout(5000) {
            while (true) {
                val ended = jobManager.endedJobs.value.find { it.id == job.id }
                if (ended != null) break
                delay(10)
            }
        }

        val failedJob = jobManager.endedJobs.value.find { it.id == job.id }
        assertNotNull(failedJob)
        assertEquals(JobStatus.Failed, failedJob.status)
        assertEquals(
            "Capability 'flow_only_cap' is scoped only as a node in flows and cannot be executed directly.",
            failedJob.errorMessage
        )

        jobWorker.stop()
    }
}
