package org.wip.plugintoolkit.features.repository.logic

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class RepoParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
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
    fun testAddRepositoryParsing() = runTest {
        val mockEngine = MockEngine { request ->
            when (request.url.toString()) {
                "https://example.com/repo/index.json" -> {
                    respond(
                        content = """
                            {
                                "name": "Test Repo",
                                "pluginsFolder": "custom-plugins",
                                "plugins": [
                                    {
                                        "name": "Test Plugin",
                                        "fileName": "test.jar",
                                        "pkg": "org.test.plugin",
                                        "version": "1.0.0"
                                    }
                                ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                "https://example.com/repo/custom-plugins/org.test.plugin/manifest.json" -> {
                    respond(
                        content = """
                            {
                                "manifestVersion": "1.0",
                                "plugin": {
                                    "id": "org.test.plugin",
                                    "name": "Test Plugin",
                                    "version": "1.0.0",
                                    "description": "A test plugin"
                                },
                                "requirements": {
                                    "minMemoryMb": 512,
                                    "minExecutionTimeMs": 1000
                                }
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence)
        val repoManager = RepoManager(settingsRepo, client, json)

        val result = repoManager.addRepository("https://example.com/repo/index.json")
        
        assertEquals(AddRepoResult.Success, result)
        
        val repos = repoManager.repositories.value
        assertEquals(1, repos.size)
        assertEquals("Test Repo", repos[0].name)
        assertEquals("custom-plugins", repos[0].pluginsFolder)

        val plugins = repoManager.plugins.value["https://example.com/repo/index.json"]
        assertTrue(plugins != null)
        assertEquals(1, plugins.size)
        assertEquals("org.test.plugin", plugins[0].pkg)
        assertEquals("Test Plugin", plugins[0].manifest?.plugin?.name)
        assertEquals(512, plugins[0].manifest?.requirements?.minMemoryMb)
    }

    @Test
    fun testMalformedManifestParsing() = runTest {
        val mockEngine = MockEngine { request ->
            when (request.url.toString()) {
                "https://example.com/repo/index.json" -> {
                    respond(
                        content = """
                            {
                                "name": "Test Repo",
                                "plugins": [
                                    {
                                        "name": "Broken Plugin",
                                        "fileName": "broken.jar",
                                        "pkg": "org.broken.plugin",
                                        "version": "1.0.0"
                                    }
                                ]
                            }
                        """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                "https://example.com/repo/plugins/org.broken.plugin/manifest.json" -> {
                    respond(
                        content = "{ \"invalid\": \"json\" }", // Missing required fields for PluginManifest
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                else -> respondError(HttpStatusCode.NotFound)
            }
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(json)
            }
        }

        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence)
        val repoManager = RepoManager(settingsRepo, client, json)

        repoManager.addRepository("https://example.com/repo/index.json")

        val plugins = repoManager.plugins.value["https://example.com/repo/index.json"]
        assertTrue(plugins != null)
        assertEquals(1, plugins.size)
        assertEquals("org.broken.plugin", plugins[0].pkg)
        assertNull(plugins[0].manifest, "Manifest should be null for invalid JSON")
    }
}
