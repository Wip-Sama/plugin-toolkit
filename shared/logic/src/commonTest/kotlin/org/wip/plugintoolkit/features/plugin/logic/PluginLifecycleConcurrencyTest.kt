package org.wip.plugintoolkit.features.plugin.logic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.wip.plugintoolkit.core.loomDispatcher
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PluginLifecycleConcurrencyTest {

    private class FakeSettingsPersistence : SettingsPersistence {
        var settings = AppSettings()
        override suspend fun load(): AppSettings = settings
        override suspend fun save(settings: AppSettings) {
            this.settings = settings
        }

        override fun getSettingsDir(): String = "/tmp/test_settings"
        override fun getJobsDir(): String = "/tmp/test_jobs"
        override fun openLogFolder() {}
        override fun openLatestLog() {}
    }

    @Test
    fun testPluginRegistryConcurrentUpdates() = runTest {
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val mockAppConfig = io.mockk.mockk<org.wip.plugintoolkit.core.SystemConfig>(relaxed = true)
        val registry = PluginRegistry(settingsRepo, backgroundScope, loomDispatcher, mockAppConfig)

        // Seed registry with initial plugins
        val initialPlugins = (1..5).map { i ->
            InstalledPlugin(
                pkg = "org.test.plugin$i",
                name = "Plugin $i",
                version = "1.0.0",
                installPath = "/tmp/plugin$i",
                isEnabled = true
            )
        }
        initialPlugins.forEach { registry.addOrUpdatePlugin(it) }

        // Perform 20 rapid concurrent updates
        val deferreds = (1..20).map { i ->
            async {
                registry.updatePlugin("org.test.plugin${(i % 5) + 1}") {
                    it.copy(version = "1.0.$i")
                }
            }
        }
        deferreds.awaitAll()

        // Verify all 5 plugins are still registered and valid
        assertEquals(5, registry.installedPlugins.value.size)
        initialPlugins.forEach { plugin ->
            assertNotNull(registry.getPlugin(plugin.pkg))
        }
    }
}
