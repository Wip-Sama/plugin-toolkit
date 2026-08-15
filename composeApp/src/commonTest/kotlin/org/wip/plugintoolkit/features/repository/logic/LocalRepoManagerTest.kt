package org.wip.plugintoolkit.features.repository.logic

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.core.utils.FileSystem
import org.wip.plugintoolkit.features.repository.model.RepoValidationResult
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalRepoManagerTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
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

    private class FakeFileSystem : FileSystem {
        val files = mutableMapOf<String, String>()
        val byteFiles = mutableMapOf<String, ByteArray>()
        val directories = mutableSetOf<String>()

        fun normalize(path: String): String = path.replace('\\', '/')

        override fun exists(path: String): Boolean {
            val norm = normalize(path)
            return files.containsKey(norm) || byteFiles.containsKey(norm) || directories.contains(norm)
        }

        override fun mkdirs(path: String): Boolean {
            directories.add(normalize(path))
            return true
        }

        override fun copyFile(source: String, destination: String) {
            val src = normalize(source)
            val dst = normalize(destination)
            if (files.containsKey(src)) files[dst] = files[src]!!
            if (byteFiles.containsKey(src)) byteFiles[dst] = byteFiles[src]!!
        }

        override fun deleteDirectory(path: String): Boolean {
            val norm = normalize(path)
            files.keys.removeIf { it.startsWith(norm) }
            byteFiles.keys.removeIf { it.startsWith(norm) }
            directories.removeIf { it.startsWith(norm) }
            return true
        }

        override fun readFileFromZip(zipPath: String, fileName: String): String? = null
        override fun listFiles(path: String): List<String> = emptyList()

        override fun saveFile(path: String, content: ByteArray) {
            byteFiles[normalize(path)] = content
        }

        override fun readFile(path: String): String? = files[normalize(path)]

        override fun writeFile(path: String, content: String) {
            files[normalize(path)] = content
        }
    }

    @Test
    fun testAddLocalRepositoryWithManifestsAndFlows() = runTest {
        val fakeFs = FakeFileSystem()
        val repoDir = "/home/user/my-repo"
        fakeFs.mkdirs(repoDir)

        val indexJsonPath = "$repoDir/index.json"
        fakeFs.writeFile(
            indexJsonPath,
            """
            {
                "name": "Local Dev Repo",
                "schemaVersion": 1,
                "pluginsFolder": "plugins",
                "flowsFolder": "flows",
                "plugins": [
                    {
                        "name": "Local Plugin",
                        "pkg": "org.local.sample",
                        "version": "2.0.0",
                        "fileName": "sample.jar"
                    }
                ],
                "flows": [
                    {
                        "name": "Local Flow",
                        "fileName": "local_flow.json",
                        "version": "1.0.0",
                        "description": "Local offline flow"
                    }
                ]
            }
            """.trimIndent()
        )

        val manifestPath = "$repoDir/plugins/org.local.sample/manifest.json"
        fakeFs.mkdirs("$repoDir/plugins/org.local.sample")
        fakeFs.writeFile(
            manifestPath,
            """
            {
                "manifestVersion": "1.0",
                "plugin": {
                    "id": "org.local.sample",
                    "name": "Local Plugin Ext",
                    "version": "2.0.0",
                    "description": "Rich description from local manifest"
                },
                "requirements": {
                    "minMemoryMb": 512,
                    "minExecutionTimeMs": 1000
                }
            }
            """.trimIndent()
        )

        val client = HttpClient(MockEngine { respondError(HttpStatusCode.NotFound) })
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        settingsRepo.isLoaded.first { it }
        val repoManager = RepoManager(
            settingsRepository = settingsRepo,
            client = client,
            jsonConfig = json,
            scope = backgroundScope,
            fileSystem = fakeFs
        )
        testScheduler.advanceUntilIdle()

        // Validation test
        val validation = repoManager.validateRepository(indexJsonPath)
        assertTrue(validation is RepoValidationResult.Valid)
        assertEquals("Local Dev Repo", (validation as RepoValidationResult.Valid).name)
        assertEquals(1, validation.pluginCount)
        assertEquals(1, validation.flowCount)

        // Add repo test
        val result = repoManager.addRepository(indexJsonPath)
        testScheduler.advanceUntilIdle()
        assertEquals(AddRepoResult.Success, result)

        val repos = repoManager.repositories.value
        assertEquals(1, repos.size)
        assertEquals("Local Dev Repo", repos[0].name)
        assertTrue(repos[0].isLocal)

        val plugins = repoManager.plugins.value[indexJsonPath]
        assertTrue(plugins != null)
        assertEquals(1, plugins.size)
        assertEquals("org.local.sample", plugins[0].pkg)
        assertEquals("Local Plugin Ext", plugins[0].manifest?.plugin?.name)
        assertEquals("Rich description from local manifest", plugins[0].manifest?.plugin?.description)

        val flows = repoManager.flows.value[indexJsonPath]
        assertTrue(flows != null)
        assertEquals(1, flows.size)
        assertEquals("Local Flow", flows[0].name)
    }

    @Test
    fun testValidateRepositoryFailures() = runTest {
        val fakeFs = FakeFileSystem()
        val client = HttpClient(MockEngine { respondError(HttpStatusCode.NotFound) })
        val persistence = FakeSettingsPersistence()
        val settingsRepo = SettingsRepository(persistence, backgroundScope)
        val repoManager = RepoManager(
            settingsRepository = settingsRepo,
            client = client,
            jsonConfig = json,
            scope = backgroundScope,
            fileSystem = fakeFs
        )

        // 1. Missing local file
        val missingResult = repoManager.validateRepository("/path/does/not/exist/index.json")
        assertTrue(missingResult is RepoValidationResult.Invalid)

        // 2. Corrupt JSON file
        val corruptPath = "/repo/corrupt.json"
        fakeFs.mkdirs("/repo")
        fakeFs.writeFile(corruptPath, "{ not valid json ")

        val corruptResult = repoManager.validateRepository(corruptPath)
        assertTrue(corruptResult is RepoValidationResult.Invalid)

        // 3. Blank target
        val blankResult = repoManager.validateRepository("   ")
        assertTrue(blankResult is RepoValidationResult.Invalid)
    }
}
