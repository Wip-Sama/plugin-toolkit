package org.wip.plugintoolkit.features.plugin.logic

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.core.loomDispatcher
import org.wip.plugintoolkit.core.utils.FileSystem
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.repository.logic.RepoManager
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.ExtensionSettings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PluginPluginInstallationTest {

    @AfterTest
    fun tearDown() {
        unmockkAll()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private class FakeFileSystem : FileSystem {
        val files = mutableMapOf<String, ByteArray>()
        val dirs = mutableSetOf<String>()
        val zips = mutableMapOf<String, Map<String, String>>() // zipPath -> { fileName -> content }

        override fun exists(path: String): Boolean = files.containsKey(path) || dirs.contains(path)
        override fun mkdirs(path: String): Boolean {
            dirs.add(path); return true
        }

        override fun copyFile(source: String, destination: String) {
            files[destination] = files[source] ?: byteArrayOf()
        }

        override fun deleteDirectory(path: String): Boolean {
            files.keys.removeIf { it.startsWith(path) }
            dirs.removeIf { it.startsWith(path) }
            return true
        }

        override fun readFileFromZip(zipPath: String, fileName: String): String? = zips[zipPath]?.get(fileName)
        override fun listFiles(path: String): List<String> =
            files.keys.filter { it.startsWith(path) }.map { it.substringAfterLast("/") }

        override fun saveFile(path: String, content: ByteArray) {
            files[path] = content
        }

        override fun readFile(path: String): String? = files[path]?.decodeToString()
        override fun writeFile(path: String, content: String) {
            files[path] = content.encodeToByteArray()
        }
    }

    private class FakeSettingsPersistence : SettingsPersistence {
        var settings = AppSettings()
        override suspend fun load(): AppSettings = settings
        override suspend fun save(settings: AppSettings) {
            this.settings = settings
        }

        override fun getSettingsDir(): String = ""
        override fun getJobsDir(): String = ""
        override fun openLogFolder() {}
        override fun openLatestLog() {}
    }

    @Test
    fun testRemoteInstallationAndUpdating() = runTest {
        var remoteVersion = "1.0.0"
        var remoteJarName = "test.jar"

        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url == "https://example.com/repo/index.json" -> {
                    respond(
                        content = """
                            {
                                "name": "Test Repo",
                                "plugins": [
                                    {
                                        "name": "Test Plugin",
                                        "fileName": "$remoteJarName",
                                        "pkg": "org.test.plugin",
                                        "version": "$remoteVersion"
                                    }
                                ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                url.endsWith("manifest.json") -> {
                    respond(
                        content = """
                            {
                                "manifestVersion": "1.0",
                                "plugin": { "id": "org.test.plugin", "name": "Test Plugin", "version": "$remoteVersion", "description": "" },
                                "requirements": { "minMemoryMb": 0, "minExecutionTimeMs": 0 }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }

                url.endsWith(".jar") -> {
                    respond(
                        content = if (url.contains("test_v2")) byteArrayOf(4, 5, 6) else byteArrayOf(1, 2, 3),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/java-archive")
                    )
                }

                else -> respond("")
            }
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val fileSystem = FakeFileSystem()
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val mockAppConfig = io.mockk.mockk<org.wip.plugintoolkit.core.SystemConfig>(relaxed = true)
        val registry = PluginRegistry(settingsRepo, backgroundScope, loomDispatcher, mockAppConfig)
        val jobManager = JobManager(backgroundScope, settingsRepo)
        val repoManager = RepoManager(settingsRepo, client, json, backgroundScope)
        val lifecycleManager = PluginLifecycleManager(registry, jobManager, settingsRepo, fileSystem)
        val installer =
            PluginInstaller(registry, repoManager, lifecycleManager, settingsRepo, jobManager, client, fileSystem)

        // 1. Initial Installation
        repoManager.addRepository("https://example.com/repo/index.json")
        val extensionPlugin = repoManager.plugins.value["https://example.com/repo/index.json"]!![0]

        fileSystem.zips["target/org.test.plugin/test.jar"] = mapOf(
            "manifest.json" to """{ "manifestVersion": "1.0", "plugin": { "id": "org.test.plugin", "name": "Test Plugin", "version": "1.0.0", "description": "" }, "requirements": { "minMemoryMb": 0, "minExecutionTimeMs": 0 } }"""
        )

        val installResult = installer.installRemote(extensionPlugin, "target")
        assertTrue(installResult.isSuccess)
        assertEquals("1.0.0", registry.getPlugin("org.test.plugin")?.version)

        // 2. Simulate Remote Update
        remoteVersion = "1.1.0"
        remoteJarName = "test_v2.jar"

        // Refresh repository to see the new version
        repoManager.refreshRepository("https://example.com/repo/index.json")

        val update = installer.getUpdate("org.test.plugin")
        assertTrue(update != null, "Update should be available")
        assertEquals("1.1.0", update.version)

        // Mock manifest for the new JAR
        fileSystem.zips["target/org.test.plugin/test_v2.jar"] = mapOf(
            "manifest.json" to """{ "manifestVersion": "1.0", "plugin": { "id": "org.test.plugin", "name": "Test Plugin", "version": "1.1.0", "description": "" }, "requirements": { "minMemoryMb": 0, "minExecutionTimeMs": 0 } }"""
        )

        val updateResult = installer.updateRemote("org.test.plugin")
        assertTrue(updateResult.isSuccess)
        assertEquals("1.1.0", registry.getPlugin("org.test.plugin")?.version)
        assertTrue(fileSystem.exists("target/org.test.plugin/test_v2.jar"))
    }

    @Test
    fun testStrictSignatureChecking() = runTest {
        mockkObject(PluginSecurity)
        every { PluginSecurity.verify(any(), any()) } returns false

        val persistence = FakeSettingsPersistence()
        persistence.settings = AppSettings(extensions = ExtensionSettings(strictSignatureChecking = true))

        val (installer, repoManager, registry, fileSystem) = createTestInstaller(persistence)

        val repoUrl = "https://example.com/repo/index.json"
        repoManager.addRepository(repoUrl)

        val plugin = repoManager.plugins.value[repoUrl]!![0]

        val result = installer.installRemote(plugin, "target")

        assertTrue(result.isFailure)
        assertEquals(
            "Plugin signature verification failed (strict checking enabled)",
            result.exceptionOrNull()?.message
        )
        assertFalse(fileSystem.exists("target/org.test"))
        unmockkObject(PluginSecurity)
    }

    @Test
    fun testLazySignatureChecking() = runTest {
        mockkObject(PluginSecurity)
        every { PluginSecurity.verify(any(), any()) } returns false

        val persistence = FakeSettingsPersistence()
        persistence.settings = AppSettings(extensions = ExtensionSettings(strictSignatureChecking = false))

        val (installer, repoManager, registry, fileSystem) = createTestInstaller(persistence)

        val repoUrl = "https://example.com/repo/index.json"
        repoManager.addRepository(repoUrl)

        val plugin = repoManager.plugins.value[repoUrl]!![0]

        val result = installer.installRemote(plugin, "target")

        assertTrue(result.isSuccess)
        val installed = registry.getPlugin("org.test")
        assertNotNull(installed)
        assertEquals("CONFIRM_SIGNATURE", installed.requiredAction)
        assertEquals("Invalid Signature", installed.loadError)
        assertFalse(installed.isEnabled)
        unmockkObject(PluginSecurity)
    }

    @Test
    fun testRemoteInstallationWithProgress() = runTest {
        mockkObject(PluginSecurity)
        every { PluginSecurity.verify(any(), any()) } returns true

        val (installer, repoManager, registry, fileSystem) = createTestInstaller()
        val repoUrl = "https://example.com/repo/index.json"
        repoManager.addRepository(repoUrl)
        val plugin = repoManager.plugins.value[repoUrl]!![0]

        fileSystem.zips["target/org.test/test.jar"] = mapOf(
            "manifest.json" to """{ "manifestVersion": "1.0", "plugin": { "id": "org.test", "name": "Test", "version": "1.0", "description": "" }, "requirements": { "minMemoryMb": 0, "minExecutionTimeMs": 0 } }"""
        )

        val progressUpdates = mutableListOf<Float>()
        val result = installer.installRemote(plugin, "target") { progress ->
            progressUpdates.add(progress)
        }

        assertTrue(result.isSuccess)
        assertTrue(progressUpdates.isNotEmpty(), "Progress updates should have been received")
        assertEquals(1.0f, progressUpdates.last(), "Final progress update should be 1.0f (100%)")
        assertTrue(fileSystem.exists("target/org.test/test.jar"))
        unmockkObject(PluginSecurity)
    }

    @Test
    fun testReinstallUnloadsPluginAndCleansOldJar() = runTest {
        mockkObject(PluginLoader)
        every { PluginLoader.unloadPlugin(any()) } returns Unit
        every { PluginLoader.unloadPluginById(any()) } returns Unit

        val (installer, _, registry, fileSystem) = createTestInstaller()

        // 1. Initial installation with plugin-1.0.jar
        val manifestV1 = """{ "manifestVersion": "1.0", "plugin": { "id": "org.test", "name": "Test", "version": "1.0.0", "description": "" }, "requirements": { "minMemoryMb": 0, "minExecutionTimeMs": 0 } }"""
        fileSystem.zips["source/plugin-1.0.jar"] = mapOf("manifest.json" to manifestV1)
        fileSystem.files["source/plugin-1.0.jar"] = byteArrayOf(1, 2, 3)

        val res1 = installer.installLocal("source/plugin-1.0.jar", "target")
        assertTrue(res1.isSuccess)
        assertTrue(fileSystem.exists("target/org.test/plugin-1.0.jar"))
        assertEquals("plugin-1.0.jar", registry.getPlugin("org.test")?.jarFileName)

        // 2. Reinstall with updated version and different jar filename: plugin-1.1.0.jar
        val manifestV2 = """{ "manifestVersion": "1.0", "plugin": { "id": "org.test", "name": "Test", "version": "1.1.0", "description": "" }, "requirements": { "minMemoryMb": 0, "minExecutionTimeMs": 0 } }"""
        fileSystem.zips["source/plugin-1.1.0.jar"] = mapOf("manifest.json" to manifestV2)
        fileSystem.files["source/plugin-1.1.0.jar"] = byteArrayOf(4, 5, 6)

        val res2 = installer.installLocal("source/plugin-1.1.0.jar", "target")
        assertTrue(res2.isSuccess)

        // Old jar should be removed and new jar installed
        assertFalse(fileSystem.exists("target/org.test/plugin-1.0.jar"), "Old JAR should be cleaned up")
        assertTrue(fileSystem.exists("target/org.test/plugin-1.1.0.jar"), "New JAR should be present")
        assertEquals("1.1.0", registry.getPlugin("org.test")?.version)
        assertEquals("plugin-1.1.0.jar", registry.getPlugin("org.test")?.jarFileName)
        assertFalse(registry.getPlugin("org.test")?.isValidated ?: true, "Reinstalled plugin should have isValidated = false")

        io.mockk.verify(atLeast = 1) { PluginLoader.unloadPluginById("org.test") }
        unmockkObject(PluginLoader)
    }

    private fun TestScope.createTestInstaller(
        persistence: FakeSettingsPersistence = FakeSettingsPersistence(),
        fileSystem: FakeFileSystem = FakeFileSystem()
    ): TestInstallerComponents {
        val mockEngine = MockEngine { request ->
            val url = request.url.toString()
            when {
                url.contains("index.json") -> respond(
                    content = """{ "name": "Test", "signPublicKey": "test-key", "plugins": [ { "name": "Test", "pkg": "org.test", "version": "1.0", "fileName": "test.jar" } ] }""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )

                else -> respond(byteArrayOf(1, 2, 3))
            }
        }
        val client = HttpClient(mockEngine) { install(ContentNegotiation) { json(json) } }
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val mockAppConfig = io.mockk.mockk<org.wip.plugintoolkit.core.SystemConfig>(relaxed = true)
        val registry = PluginRegistry(settingsRepo, backgroundScope, loomDispatcher, mockAppConfig)
        val jobManager = JobManager(backgroundScope, settingsRepo)
        val repoManager = RepoManager(settingsRepo, client, json, backgroundScope)
        val lifecycleManager = PluginLifecycleManager(registry, jobManager, settingsRepo, fileSystem)
        val installer =
            PluginInstaller(registry, repoManager, lifecycleManager, settingsRepo, jobManager, client, fileSystem)
        return TestInstallerComponents(installer, repoManager, registry, fileSystem)
    }

    private data class TestInstallerComponents(
        val installer: PluginInstaller,
        val repoManager: RepoManager,
        val registry: PluginRegistry,
        val fileSystem: FakeFileSystem
    )
}
