package org.wip.plugintoolkit

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.wip.plugintoolkit.core.PortableSystemConfig
import org.wip.plugintoolkit.core.SystemConfig
import org.wip.plugintoolkit.core.model.LocalizedString
import org.wip.plugintoolkit.core.notification.NotificationEvent
import org.wip.plugintoolkit.core.notification.NotificationRecord
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.notification.NotificationType
import org.wip.plugintoolkit.core.utils.RealFileSystem
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.plugin.logic.PluginInstaller
import org.wip.plugintoolkit.features.plugin.logic.PluginLifecycleCoordinator
import org.wip.plugintoolkit.features.plugin.logic.PluginLifecycleManager
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.logic.PluginRegistry
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.repository.logic.RepoManager
import org.wip.plugintoolkit.features.settings.logic.JvmSettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DevBootstrapTest {

    private class FakeNotificationService : NotificationService {
        override val events: SharedFlow<NotificationEvent> = MutableSharedFlow()
        override val history: StateFlow<List<NotificationRecord>> = MutableStateFlow(emptyList())
        override fun notify(title: String, message: String, type: NotificationType) = Unit
        override fun toast(message: String, isNotification: Boolean) = Unit
        override fun toast(message: LocalizedString, isNotification: Boolean) = Unit
        override fun clearHistory() = Unit
        override fun removeHistoryItem(id: String) = Unit
    }

    @AfterTest
    fun tearDown() {
        System.clearProperty(DevBootstrap.PROP_DEV_PLUGINS)
        System.clearProperty(DevBootstrap.PROP_DEV_REPO)
        stopKoin()
    }

    @Test
    fun testResolveCliOptionValuesAndDevPaths() {
        val sandbox = Files.createTempDirectory("dev_bootstrap_paths").toFile()
        try {
            val jarA = File(sandbox, "a.jar").apply { writeText("jarA") }
            val jarB = File(sandbox, "b.jar").apply { writeText("jarB") }
            val notJar = File(sandbox, "ignore.txt").apply { writeText("txt") }
            val indexFile = File(sandbox, "index.json").apply { writeText("{}") }

            val cliJars = DevBootstrap.resolveDevPluginJars(
                arrayOf(
                    "--dev-plugin=${jarA.absolutePath}",
                    "--dev-plugin",
                    jarB.absolutePath,
                    "--dev-plugin=${notJar.absolutePath}",
                    "--dev-plugin=${jarA.absolutePath}"
                )
            )
            assertEquals(listOf(jarA.canonicalPath, jarB.canonicalPath), cliJars.map { it.canonicalPath })

            System.setProperty(DevBootstrap.PROP_DEV_PLUGINS, "${jarA.absolutePath}${File.pathSeparator}${jarB.absolutePath}")
            val propJars = DevBootstrap.resolveDevPluginJars(emptyArray())
            assertEquals(2, propJars.size)

            System.setProperty(DevBootstrap.PROP_DEV_REPO, indexFile.absolutePath)
            assertEquals(
                indexFile.canonicalPath.replace('\\', '/'),
                DevBootstrap.resolveDevRepoPath(emptyArray())
            )
            assertNull(DevBootstrap.resolveDevRepoPath(arrayOf("--dev-repo=${File(sandbox, "missing.json").absolutePath}")))
        } finally {
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun testRegisterDevRepoInPortableSandbox() = runTest {
        val sandbox = Files.createTempDirectory("dev_repo_sandbox").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val portableConfig = PortableSystemConfig(sandbox.absolutePath)
            startKoin {
                modules(
                    module {
                        single<SystemConfig> { portableConfig }
                        single<NotificationService> { FakeNotificationService() }
                    }
                )
            }

            val repoDir = File(sandbox, "repo").apply { mkdirs() }
            val minimalDir = File(repoDir, "plugins/org.wip.minimal").apply { mkdirs() }
            File(minimalDir, "manifest.json").writeText(sampleManifestJson("org.wip.minimal", "Minimal Example Plugin", "1.0.0"))
            val indexFile = File(repoDir, "index.json")
            indexFile.writeText(
                """
                {
                  "name": "Local Dev Examples Repository",
                  "url": "${indexFile.absolutePath.replace('\\', '/')}",
                  "schemaVersion": 1,
                  "pluginsFolder": "plugins",
                  "flowsFolder": "flows",
                  "plugins": [
                    {
                      "name": "Minimal Example Plugin",
                      "fileName": "minimalExample-1.0.0.jar",
                      "pkg": "org.wip.minimal",
                      "version": "1.0.0"
                    }
                  ],
                  "flows": []
                }
                """.trimIndent()
            )

            val settingsRepo = SettingsRepository(JvmSettingsPersistence(), scope)
            val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            val client = HttpClient(MockEngine) { expectSuccess = false; engine { addHandler { respondOk("") } } }
            val repoManager = RepoManager(settingsRepo, client, json, scope, RealFileSystem())

            DevBootstrap.registerDevRepo(indexFile.absolutePath, settingsRepo, repoManager)
            assertEquals(1, repoManager.repositories.value.size)
            assertEquals("Local Dev Examples Repository", repoManager.repositories.value.first().name)
            assertEquals(1, repoManager.plugins.value.values.flatten().size)

            // Calling again should not duplicate the repository
            DevBootstrap.registerDevRepo(indexFile.absolutePath, settingsRepo, repoManager)
            assertEquals(1, repoManager.repositories.value.size)
        } finally {
            scope.cancel()
            sandbox.deleteRecursively()
        }
    }

    @Test
    fun testSyncDevPluginsInstallsSkipsUnchangedAndUpdatesModified() = runTest {
        val sandbox = Files.createTempDirectory("dev_plugins_sandbox").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val portableConfig = PortableSystemConfig(sandbox.absolutePath)
            startKoin {
                modules(
                    module {
                        single<SystemConfig> { portableConfig }
                        single<NotificationService> { FakeNotificationService() }
                    }
                )
            }

            val fileSystem = RealFileSystem()
            val settingsRepo = SettingsRepository(JvmSettingsPersistence(), scope)
            val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            val client = HttpClient(MockEngine) { expectSuccess = false; engine { addHandler { respondOk("") } } }
            val repoManager = RepoManager(settingsRepo, client, json, scope, fileSystem)
            val registry = PluginRegistry(settingsRepo, scope, Dispatchers.IO, portableConfig)
            registry.initialize()
            val jobManager = JobManager(scope, settingsRepo)
            val lifecycleManager = PluginLifecycleManager(registry, jobManager, settingsRepo, fileSystem)
            val coordinator = PluginLifecycleCoordinator(registry, jobManager, lifecycleManager, scope)
            val installer = PluginInstaller(registry, repoManager, lifecycleManager, settingsRepo, jobManager, client, fileSystem)
            val pluginManager = PluginManager(
                repoManager = repoManager,
                registry = registry,
                installer = installer,
                lifecycleManager = lifecycleManager,
                scanner = null,
                coordinator = coordinator,
                folderManager = null,
                scope = scope
            )

            val sourceDir = File(sandbox, "jars").apply { mkdirs() }
            val minimalJar = createFakePluginJar(
                File(sourceDir, "minimalExample.jar"),
                sampleManifestJson("org.wip.minimal", "Minimal Example Plugin", "1.0.0")
            )

            val handledFirst = DevBootstrap.syncDevPlugins(listOf(minimalJar), registry, pluginManager)
            assertEquals(setOf("org.wip.minimal"), handledFirst)
            val installed = registry.getPlugin("org.wip.minimal")
            assertNotNull(installed)
            assertTrue(File(installed.installPath, "minimalExample.jar").exists())

            // Re-running with identical JAR should be a no-op
            val handledSecond = DevBootstrap.syncDevPlugins(listOf(minimalJar), registry, pluginManager)
            assertTrue(handledSecond.isEmpty())

            // Updating the JAR content should trigger reinstall
            createFakePluginJar(
                minimalJar,
                sampleManifestJson("org.wip.minimal", "Minimal Example Plugin", "1.0.1")
            )
            val handledThird = DevBootstrap.syncDevPlugins(listOf(minimalJar), registry, pluginManager)
            assertEquals(setOf("org.wip.minimal"), handledThird)
            assertEquals("1.0.1", registry.getPlugin("org.wip.minimal")?.version)

            // Verify required settings seeding for org.wip.complete when CONFIGURE_SETTINGS is requested
            val completeJar = createFakePluginJar(
                File(sourceDir, "completeExample.jar"),
                sampleCompleteManifestWithRequiredSettings()
            )
            val handledComplete = DevBootstrap.syncDevPlugins(listOf(completeJar), registry, pluginManager)
            assertEquals(setOf("org.wip.complete"), handledComplete)
            val completeSettings = pluginManager.loadPluginSettings("org.wip.complete")
            assertEquals(JsonPrimitive("user_demo"), completeSettings.settings["userId"])
            assertEquals(JsonPrimitive("secret-12345"), completeSettings.settings["secretToken"])
            assertFalse(registry.getPlugin("org.wip.complete")?.requiredAction == "CONFIGURE_SETTINGS")
        } finally {
            scope.cancel()
            sandbox.deleteRecursively()
        }
    }

    private fun createFakePluginJar(target: File, manifestJson: String): File {
        ZipOutputStream(target.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/manifest.json"))
            zip.write(manifestJson.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return target
    }

    private fun sampleManifestJson(id: String, name: String, version: String): String =
        """
        {
          "manifestVersion": "1.0",
          "plugin": {
            "id": "$id",
            "name": "$name",
            "version": "$version",
            "description": "Test plugin",
            "supportedOs": ["WINDOWS", "LINUX", "MACOS"]
          },
          "requirements": {
            "minMemoryMb": 64,
            "minExecutionTimeMs": 10
          },
          "capabilities": [],
          "actions": [],
          "settings": {},
          "hasUpdateHandler": false,
          "hasSetupHandler": false,
          "hasMigrations": false
        }
        """.trimIndent()

    private fun sampleCompleteManifestWithRequiredSettings(): String =
        """
        {
          "manifestVersion": "1.0",
          "plugin": {
            "id": "org.wip.complete",
            "name": "Complete Example Plugin",
            "version": "2.2.0",
            "description": "Test complete plugin",
            "supportedOs": ["WINDOWS", "LINUX", "MACOS"]
          },
          "requirements": {
            "minMemoryMb": 64,
            "minExecutionTimeMs": 10
          },
          "capabilities": [],
          "actions": [],
          "settings": {
            "secretToken": {
              "defaultValue": null,
              "description": "Secret",
              "type": { "primitiveType": "STRING" },
              "required": true,
              "secret": true
            },
            "userId": {
              "defaultValue": null,
              "description": "User ID",
              "type": { "primitiveType": "STRING" },
              "required": true,
              "secret": false
            }
          },
          "hasUpdateHandler": false,
          "hasSetupHandler": false,
          "hasMigrations": false
        }
        """.trimIndent()
}
