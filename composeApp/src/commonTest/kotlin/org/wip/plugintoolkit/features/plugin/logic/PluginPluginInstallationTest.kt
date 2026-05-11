package org.wip.plugintoolkit.features.plugin.logic

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.core.loomDispatcher
import org.wip.plugintoolkit.features.repository.logic.RepoManager
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.core.utils.FileSystem
import org.wip.plugintoolkit.features.job.logic.JobManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginPluginInstallationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private class FakeFileSystem : FileSystem {
        val files = mutableMapOf<String, ByteArray>()
        val dirs = mutableSetOf<String>()
        val zips = mutableMapOf<String, Map<String, String>>() // zipPath -> { fileName -> content }

        override fun exists(path: String): Boolean = files.containsKey(path) || dirs.contains(path)
        override fun mkdirs(path: String): Boolean { dirs.add(path); return true }
        override fun copyFile(source: String, destination: String) { files[destination] = files[source] ?: byteArrayOf() }
        override fun deleteDirectory(path: String): Boolean { 
            files.keys.removeIf { it.startsWith(path) }
            dirs.removeIf { it.startsWith(path) }
            return true 
        }
        override fun readFileFromZip(zipPath: String, fileName: String): String? = zips[zipPath]?.get(fileName)
        override fun listFiles(path: String): List<String> = files.keys.filter { it.startsWith(path) }.map { it.substringAfterLast("/") }
        override fun saveFile(path: String, content: ByteArray) { files[path] = content }
        override fun readFile(path: String): String? = files[path]?.decodeToString()
        override fun writeFile(path: String, content: String) { files[path] = content.encodeToByteArray() }
    }

    private class FakeSettingsPersistence : SettingsPersistence {
        var settings = AppSettings()
        override fun load(): AppSettings = settings
        override fun save(settings: AppSettings) { this.settings = settings }
        override fun getSettingsDir(): String = ""
        override fun getJobsDir(): String = ""
        override fun openLogFolder() {}
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
        val registry = PluginRegistry(settingsRepo, backgroundScope, loomDispatcher)
        val jobManager = JobManager(backgroundScope, 1)
        val repoManager = RepoManager(settingsRepo, client, json, backgroundScope)
        val lifecycleManager = PluginLifecycleManager(registry, jobManager, settingsRepo, fileSystem)
        val installer = PluginInstaller(registry, repoManager, lifecycleManager, settingsRepo, jobManager, client, fileSystem)

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
}
