package org.wip.plugintoolkit.core

import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.logic.SandboxCleanupManager
import org.wip.plugintoolkit.features.job.logic.SystemNodeExecutorRegistry
import org.wip.plugintoolkit.features.plugin.logic.PluginLifecycleCoordinator
import org.wip.plugintoolkit.features.plugin.logic.PluginLifecycleManager
import org.wip.plugintoolkit.features.plugin.logic.PluginLockProvider
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.logic.PluginRegistry
import org.wip.plugintoolkit.features.plugin.model.PluginSettingsStore
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StandaloneKoinResolutionTest : KoinTest {

    private val testPluginId = "org.test.standalone"

    @BeforeTest
    fun setUp() {
        startKoin {
            modules(
                coroutineModule,
                createStandaloneLogicModule(),
                module {
                    single<SystemConfig> { DefaultSystemConfig() }
                }
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun testStandaloneLogicModuleResolvesCoreServices() {
        val jobManager: JobManager = get()
        val pluginManager: PluginManager = get()
        val pluginRegistry: PluginRegistry = get()
        val settingsRepository: SettingsRepository = get()
        val notificationService: NotificationService = get()
        val lifecycleManager: PluginLifecycleManager = get()
        val coordinator: PluginLifecycleCoordinator = get()
        val lockProvider: PluginLockProvider = get()
        val systemNodeExecutorRegistry: SystemNodeExecutorRegistry = get()
        val sandboxCleanupManager: SandboxCleanupManager = get()

        assertNotNull(jobManager)
        assertNotNull(pluginManager)
        assertNotNull(pluginRegistry)
        assertNotNull(settingsRepository)
        assertNotNull(notificationService)
        assertNotNull(lifecycleManager)
        assertNotNull(coordinator)
        assertNotNull(lockProvider)
        assertNotNull(systemNodeExecutorRegistry)
        assertNotNull(sandboxCleanupManager)
    }

    @Test
    fun testStandalonePluginManagerSafeExecutionWithoutMarketplace() = runTest {
        val pluginManager: PluginManager = get()

        // Calling marketplace-dependent methods safely returns empty or failure without crashing
        assertTrue(pluginManager.installedPlugins.value.isEmpty())
        assertNull(pluginManager.getUpdate(testPluginId))
        assertNull(pluginManager.fetchRemoteChangelog(testPluginId))
        assertEquals(emptyList(), pluginManager.getManagedFolders())

        val updateResult = pluginManager.updateLocal(testPluginId, "/dummy/path.jar")
        assertTrue(updateResult.isFailure)
        assertTrue(updateResult.exceptionOrNull() is UnsupportedOperationException)

        // Plugin settings operations work independently
        val store = pluginManager.loadPluginSettings(testPluginId)
        assertNotNull(store)
        pluginManager.savePluginSettings(testPluginId, PluginSettingsStore())
    }
}
