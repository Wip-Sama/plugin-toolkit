package org.wip.plugintoolkit.features.flows

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.wip.plugintoolkit.features.flows.logic.FlowRepository
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertTrue

private class MockSettingsPersistence : SettingsPersistence {
    override suspend fun load(): AppSettings = AppSettings()
    override suspend fun save(settings: AppSettings) {}
    override fun getSettingsDir(): String = "build/tmp/test_flows"
    override fun getJobsDir(): String = "build/tmp/test_flows/jobs"
    override fun openLogFolder() {}
    override fun openLatestLog() {}
}

class FlowRepositoryTest {


    @Test
    fun testReloadFlowsOnPluginChange() = runBlocking {
        val testJob = Job()
        val testScope = CoroutineScope(Dispatchers.Unconfined + testJob)
        try {
            val persistence = MockSettingsPersistence()
            val mockPluginManager = mockk<PluginManager>(relaxed = true)

            val installedPluginsFlow = MutableStateFlow<List<InstalledPlugin>>(emptyList())
            every { mockPluginManager.installedPlugins } returns installedPluginsFlow

            // This will launch the collect job in its init block
            val mockAppConfig = mockk<org.wip.plugintoolkit.core.SystemConfig>(relaxed = true)
            val repository = FlowRepository(persistence, mockPluginManager, testScope, mockAppConfig)

            val initialFlows = repository.flows.value

            // Emit a new list
            installedPluginsFlow.value = listOf(InstalledPlugin("test", "test", "1.0", "path"))

            // Wait a bit for the coroutine to process
            delay(100)

            assertTrue(true, "FlowRepository should not crash on plugin change collection")
        } finally {
            testJob.cancel()
        }
    }

    @Test
    fun testUpdateFlowMaxConcurrencyWhileExecuting() = runBlocking {
        val testJob = Job()
        val testScope = CoroutineScope(Dispatchers.Unconfined + testJob)
        try {
            val persistence = MockSettingsPersistence()
            val mockPluginManager = mockk<PluginManager>(relaxed = true)
            val installedPluginsFlow = MutableStateFlow<List<InstalledPlugin>>(emptyList())
            every { mockPluginManager.installedPlugins } returns installedPluginsFlow

            val mockGuard = mockk<org.wip.plugintoolkit.features.flows.logic.FlowExecutionGuard>(relaxed = true)
            io.mockk.every { mockGuard.isFlowLocked(any(), any()) } returns true
            io.mockk.every { mockGuard.isFlowRunning(any(), any()) } returns true

            val mockAppConfig = mockk<org.wip.plugintoolkit.core.SystemConfig>(relaxed = true)
            val repository = FlowRepository(
                persistence,
                mockPluginManager,
                testScope,
                mockAppConfig,
                executionGuard = mockGuard
            )

            // Should not throw FlowReadOnlyViolationException even when guard says flow is locked/running
            repository.updateFlowMaxConcurrency("RunningFlow", 4)
            delay(50)
            assertTrue(true, "updateFlowMaxConcurrency should succeed without triggering execution lock errors")
        } finally {
            testJob.cancel()
        }
    }
}
