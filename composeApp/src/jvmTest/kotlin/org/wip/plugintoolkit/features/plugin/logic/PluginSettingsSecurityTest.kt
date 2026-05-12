package org.wip.plugintoolkit.features.plugin.logic

import kotlinx.coroutines.test.runTest
import org.wip.plugintoolkit.core.utils.FileSystem
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.plugin.model.PluginSettingsStore
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.model.AppSettings
import kotlinx.serialization.json.*
import org.wip.plugintoolkit.core.loomDispatcher
import org.wip.plugintoolkit.core.utils.SecureStorage
import org.wip.plugintoolkit.api.*
import kotlin.test.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.yield

class PluginSettingsSecurityTest {

    private class FakeFileSystem : FileSystem {
        val files = mutableMapOf<String, String>()
        var manifestJson: String? = null

        override fun exists(path: String): Boolean = files.containsKey(path)
        override fun mkdirs(path: String): Boolean = true
        override fun copyFile(source: String, destination: String) {}
        override fun deleteDirectory(path: String): Boolean = true
        override fun readFileFromZip(zipPath: String, fileName: String): String? = manifestJson
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

    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Test
    fun testSecretSettingsEncryption() = runTest {
        val fileSystem = FakeFileSystem()
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val registry = PluginRegistry(settingsRepo, backgroundScope, loomDispatcher)
        val jobManager = JobManager(backgroundScope, settingsRepo)
        val lifecycleManager = PluginLifecycleManager(registry, jobManager, settingsRepo, fileSystem)

        val pkg = "test.plugin.secret"
        val tempDir = System.getProperty("java.io.tmpdir").replace("\\", "/")
        val installPath = "$tempDir/test.plugin.secret"
        
        // Setup manifest with a secret setting
        val manifest = PluginManifest(
            manifestVersion = "1.0",
            plugin = PluginInfo(id = pkg, name = "Secret Plugin", version = "1.0.0", description = "Test"),
            requirements = Requirements(minMemoryMb = 128, minExecutionTimeMs = 100),
            settings = mapOf(
                "apiKey" to SettingMetadata(
                    description = "A secret API key",
                    type = DataType.Primitive(PrimitiveType.STRING),
                    secret = true
                ),
                "publicSetting" to SettingMetadata(
                    description = "A public setting",
                    type = DataType.Primitive(PrimitiveType.STRING),
                    secret = false
                )
            )
        )
        fileSystem.manifestJson = json.encodeToString(manifest)

        // registry.initialize() is skipped because it uses real IO via PlatformUtils
        // We manually add the plugin to the registry for the test
        registry.addOrUpdatePlugin(InstalledPlugin(pkg = pkg, name = "Secret Plugin", version = "1.0.0", installPath = installPath))

        // Save settings
        val clearApiKey = "sk-1234567890abcdef"
        val publicVal = "hello-world"
        val store = PluginSettingsStore(
            settings = mapOf(
                "apiKey" to JsonPrimitive(clearApiKey),
                "publicSetting" to JsonPrimitive(publicVal)
            )
        )
        
        lifecycleManager.savePluginSettings(pkg, store)

        // Verify that the file on disk is encrypted
        val savedContent = fileSystem.readFile("$installPath/settings.json")
        assertNotNull(savedContent, "Settings file should exist")
        
        val savedStore = json.decodeFromString<PluginSettingsStore>(savedContent)
        val savedApiKey = (savedStore.settings["apiKey"] as JsonPrimitive).content
        val savedPublic = (savedStore.settings["publicSetting"] as JsonPrimitive).content

        assertNotEquals(clearApiKey, savedApiKey, "API Key should be encrypted on disk")
        assertTrue(savedApiKey.startsWith("dpapi:") || savedApiKey.startsWith("aes:"), "API Key should have encryption prefix: $savedApiKey")
        assertEquals(publicVal, savedPublic, "Public setting should NOT be encrypted")

        // Load settings back
        val loadedStore = lifecycleManager.loadPluginSettings(pkg)
        val loadedApiKey = (loadedStore.settings["apiKey"] as JsonPrimitive).content
        val loadedPublic = (loadedStore.settings["publicSetting"] as JsonPrimitive).content

        assertEquals(clearApiKey, loadedApiKey, "Loaded API Key should be decrypted")
        assertEquals(publicVal, loadedPublic, "Loaded public setting should be correct")
    }

    @Test
    fun testNonStringSecretSettings() = runTest {
        // Test that if someone tries to mark a non-string as secret, it doesn't crash but also doesn't encrypt (as per current implementation)
        val fileSystem = FakeFileSystem()
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val registry = PluginRegistry(settingsRepo, backgroundScope, loomDispatcher)
        val jobManager = JobManager(backgroundScope, settingsRepo)
        val lifecycleManager = PluginLifecycleManager(registry, jobManager, settingsRepo, fileSystem)

        val pkg = "test.plugin.secret.nonstring"
        val tempDir = System.getProperty("java.io.tmpdir").replace("\\", "/")
        val installPath = "$tempDir/test.plugin.secret.nonstring"
        
        val manifest = PluginManifest(
            manifestVersion = "1.0",
            plugin = PluginInfo(id = pkg, name = "Secret Plugin", version = "1.0.0", description = "Test"),
            requirements = Requirements(minMemoryMb = 128, minExecutionTimeMs = 100),
            settings = mapOf(
                "secretNumber" to SettingMetadata(
                    description = "A secret number",
                    type = DataType.Primitive(PrimitiveType.INT),
                    secret = true
                )
            )
        )
        fileSystem.manifestJson = json.encodeToString(manifest)

        registry.addOrUpdatePlugin(InstalledPlugin(pkg = pkg, name = "Secret Plugin", version = "1.0.0", installPath = installPath))

        val store = PluginSettingsStore(
            settings = mapOf("secretNumber" to JsonPrimitive(42))
        )
        
        lifecycleManager.savePluginSettings(pkg, store)

        val savedContent = fileSystem.readFile("$installPath/settings.json")
        assertNotNull(savedContent)
        
        val savedStore = json.decodeFromString<PluginSettingsStore>(savedContent)
        val savedNumber = (savedStore.settings["secretNumber"] as JsonPrimitive).content
        
        assertEquals("42", savedNumber, "Non-string settings are currently not encrypted even if marked secret")
    }
}
