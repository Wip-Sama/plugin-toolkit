package org.wip.plugintoolkit.features.plugin.logic

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.plugin.model.PluginLifecycleStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginLifecycleCoordinatorTest {

    @Test
    fun testOnJobCompletedValidationMarksValidated() = runTest {
        val registry = mockk<PluginRegistry>(relaxed = true)
        val jobManager = mockk<JobManager>(relaxed = true)
        val lifecycleManager = mockk<PluginLifecycleManager>(relaxed = true)

        val plugin = InstalledPlugin(
            pkg = "org.demo.test",
            name = "Test Plugin",
            version = "1.0.0",
            installPath = "/plugins/test"
        )
        every { registry.getPlugin("org.demo.test") } returns plugin

        val updateSlot = slot<(InstalledPlugin) -> InstalledPlugin>()
        coEvery { registry.updatePlugin("org.demo.test", capture(updateSlot)) } coAnswers {
            val updated = updateSlot.captured(plugin)
            assertEquals(true, updated.isValidated)
            assertEquals(true, updated.isSetupCompleted)
            assertEquals(PluginLifecycleStatus.VALIDATED, updated.status)
            assertEquals(null, updated.loadError)
            updated
        }

        coEvery { lifecycleManager.loadPlugin("org.demo.test") } returns Result.success(Unit)

        val coordinator = PluginLifecycleCoordinator(registry, jobManager, lifecycleManager, backgroundScope)

        val job = BackgroundJob(
            id = "val_org.demo.test",
            name = "Validation",
            type = JobType.Validation,
            pluginId = "org.demo.test",
            capabilityName = "validate"
        )

        coordinator.onLifecycleJobCompleted(job)

        coVerify { registry.updatePlugin("org.demo.test", any()) }
        coVerify { lifecycleManager.refreshLocks("org.demo.test") }
    }

    @Test
    fun testOnJobFailedValidationSetsValidationFailed() = runTest {
        val registry = mockk<PluginRegistry>(relaxed = true)
        val jobManager = mockk<JobManager>(relaxed = true)
        val lifecycleManager = mockk<PluginLifecycleManager>(relaxed = true)

        val plugin = InstalledPlugin(
            pkg = "org.demo.test",
            name = "Test Plugin",
            version = "1.0.0",
            installPath = "/plugins/test",
            isSetupCompleted = true
        )
        every { registry.getPlugin("org.demo.test") } returns plugin
        every { lifecycleManager.loadedPlugins } returns MutableStateFlow(setOf("org.demo.test"))

        val updateSlot = slot<(InstalledPlugin) -> InstalledPlugin>()
        coEvery { registry.updatePlugin("org.demo.test", capture(updateSlot)) } coAnswers {
            val updated = updateSlot.captured(plugin)
            assertFalse(updated.isValidated)
            assertEquals(PluginLifecycleStatus.VALIDATION_FAILED, updated.status)
            assertEquals("Missing required dependency", updated.loadError)
            updated
        }

        coEvery { lifecycleManager.unloadPlugin("org.demo.test") } returns Unit

        val coordinator = PluginLifecycleCoordinator(registry, jobManager, lifecycleManager, backgroundScope)

        val job = BackgroundJob(
            id = "val_org.demo.test",
            name = "Validation",
            type = JobType.Validation,
            pluginId = "org.demo.test",
            capabilityName = "validate"
        )

        coordinator.onLifecycleJobFailed(job, "Missing required dependency")

        coVerify { registry.updatePlugin("org.demo.test", any()) }
        coVerify { lifecycleManager.unloadPlugin("org.demo.test") }
    }

    @Test
    fun testOnJobFailedSetupSetsPendingSetupWithError() = runTest {
        val registry = mockk<PluginRegistry>(relaxed = true)
        val jobManager = mockk<JobManager>(relaxed = true)
        val lifecycleManager = mockk<PluginLifecycleManager>(relaxed = true)

        val plugin = InstalledPlugin(
            pkg = "org.demo.test",
            name = "Test Plugin",
            version = "1.0.0",
            installPath = "/plugins/test",
            isSetupCompleted = false
        )
        every { registry.getPlugin("org.demo.test") } returns plugin
        every { lifecycleManager.loadedPlugins } returns MutableStateFlow(emptySet())

        val updateSlot = slot<(InstalledPlugin) -> InstalledPlugin>()
        coEvery { registry.updatePlugin("org.demo.test", capture(updateSlot)) } coAnswers {
            val updated = updateSlot.captured(plugin)
            assertFalse(updated.isValidated)
            assertFalse(updated.isSetupCompleted)
            assertEquals(PluginLifecycleStatus.PENDING_SETUP, updated.status)
            assertEquals("Network timeout during setup", updated.loadError)
            updated
        }

        val coordinator = PluginLifecycleCoordinator(registry, jobManager, lifecycleManager, backgroundScope)

        val job = BackgroundJob(
            id = "setup_org.demo.test",
            name = "Setup",
            type = JobType.Setup,
            pluginId = "org.demo.test",
            capabilityName = "setup"
        )

        coordinator.onLifecycleJobFailed(job, "Network timeout during setup")

        coVerify { registry.updatePlugin("org.demo.test", any()) }
    }
}
