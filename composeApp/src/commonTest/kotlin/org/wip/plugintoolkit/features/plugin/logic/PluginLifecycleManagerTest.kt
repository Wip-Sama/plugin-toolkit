package org.wip.plugintoolkit.features.plugin.logic

import kotlinx.coroutines.test.runTest
import org.wip.plugintoolkit.core.utils.FileSystem
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.plugin.model.PluginSettingsStore
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.model.AppSettings
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.core.loomDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class PluginLifecycleManagerTest {

    private class FakeFileSystem : FileSystem {
        val files = mutableMapOf<String, String>()
        override fun exists(path: String): Boolean = files.containsKey(path)
        override fun mkdirs(path: String): Boolean = true
        override fun copyFile(source: String, destination: String) {}
        override fun deleteDirectory(path: String): Boolean = true
        override fun readFileFromZip(zipPath: String, fileName: String): String? = null
        override fun listFiles(path: String): List<String> = emptyList()
        override fun saveFile(path: String, content: ByteArray) { files[path] = content.decodeToString() }
        override fun readFile(path: String): String? = files[path]
        override fun writeFile(path: String, content: String) { files[path] = content }
    }

    private class FakeSettingsPersistence : SettingsPersistence {
        var settings = AppSettings()
        override fun load(): AppSettings = settings
        override fun save(settings: AppSettings) { this.settings = settings }
        override fun getSettingsDir(): String = "/tmp"
        override fun getJobsDir(): String = "/tmp/jobs"
        override fun openLogFolder() {}
        override fun openLatestLog() {}
    }

    @Test
    fun testSettingsCaching() = runTest {
        val fileSystem = FakeFileSystem()
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val registry = PluginRegistry(settingsRepo, backgroundScope, loomDispatcher)
        val jobManager = JobManager(backgroundScope, settingsRepo)
        val lifecycleManager = PluginLifecycleManager(registry, jobManager, settingsRepo, fileSystem)

        val pkg = "test.plugin"
        registry.addOrUpdatePlugin(InstalledPlugin(pkg = pkg, name = "Test", version = "1.0.0", installPath = "/tmp/test.plugin"))

        val store = PluginSettingsStore(settings = mapOf("key" to JsonPrimitive("value")))
        lifecycleManager.savePluginSettings(pkg, store)

        // First load should hit disk (fake) and cache
        val loaded1 = lifecycleManager.loadPluginSettings(pkg)
        // Second load should hit cache
        val loaded2 = lifecycleManager.loadPluginSettings(pkg)

        println("Asserting loaded1 === loaded2")
        assertSame(loaded1, loaded2, "Should return cached instance")
        
        // Update settings
        val newStore = PluginSettingsStore(settings = mapOf("key" to JsonPrimitive("new-value")))
        println("Saving new settings")
        lifecycleManager.savePluginSettings(pkg, newStore)
        
        println("Loading settings again")
        val loaded3 = lifecycleManager.loadPluginSettings(pkg)
        println("Asserting loaded1 !== loaded3")
        assertNotSame(loaded1, loaded3, "Should return new instance after save")
        println("Asserting value is correct")
        assertEquals("new-value", (loaded3.settings["key"] as JsonPrimitive).content)
        println("Asserting newStore === loaded3")
        assertSame(newStore, loaded3, "Should return the same instance that was saved")
        println("Test completed successfully")
    }
}
