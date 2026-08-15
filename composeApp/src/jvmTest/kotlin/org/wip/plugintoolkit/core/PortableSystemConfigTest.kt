package org.wip.plugintoolkit.core

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.wip.plugintoolkit.core.update.UpdateService
import org.wip.plugintoolkit.core.utils.StartupManager
import org.wip.plugintoolkit.features.settings.definitions.systemDefinitions
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.CacheManagementMode
import org.wip.plugintoolkit.features.settings.model.SettingDefinition
import org.wip.plugintoolkit.features.settings.utils.SettingsRegistry
import org.wip.plugintoolkit.features.settings.utils.build
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsViewModel
import io.mockk.mockk
import java.io.File

class PortableSystemConfigTest {

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun testPortableSystemConfigProperties() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "portable_test_dir")
        val config = PortableSystemConfig(baseDir = tempDir.absolutePath)

        assertTrue(config.isPortable)
        assertNull(config.WINDOWS_STARTUP_REGISTRY_PATH)
        assertNull(config.LINUX_AUTOSTART_DIR)
        assertNull(config.LINUX_DESKTOP_FILENAME)

        val normalizedExpectedData = tempDir.absolutePath.replace('\\', '/').removeSuffix("/") + "/data"
        assertEquals(normalizedExpectedData, config.getAppDataDir())

        // App-managed cache
        assertEquals("$normalizedExpectedData/cache", config.getCacheDir(systemManaged = false))
    }

    @Test
    fun testDefaultSystemConfigProperties() {
        val config = DefaultSystemConfig()

        assertFalse(config.isPortable)
        assertEquals("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run", config.WINDOWS_STARTUP_REGISTRY_PATH)
        assertEquals(".config/autostart", config.LINUX_AUTOSTART_DIR)
        assertEquals("plugintoolkit.desktop", config.LINUX_DESKTOP_FILENAME)
    }

    @Test
    fun testUpdateServiceDisabledInPortableMode() = runBlocking {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "portable_test_dir")
        val portableConfig = PortableSystemConfig(baseDir = tempDir.absolutePath)
        val updateService = UpdateService(appConfig = portableConfig)

        val updateResult = updateService.checkForUpdates()
        assertNull(updateResult)
    }

    @Test
    fun testStartupManagerDisabledInPortableMode() = runBlocking {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "portable_test_dir")
        val portableConfig = PortableSystemConfig(baseDir = tempDir.absolutePath)

        startKoin {
            modules(module {
                single<SystemConfig> { portableConfig }
            })
        }

        StartupManager.setLaunchAtStartup(enabled = true, minimized = true)
        assertFalse(StartupManager.isLaunchAtStartupEnabled())
    }

    @Test
    fun testSettingsRegistryPortableDefinitions() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "portable_test_dir")
        val portableConfig = PortableSystemConfig(baseDir = tempDir.absolutePath)
        val mockViewModel = mockk<SettingsViewModel>(relaxed = true)

        val registry = SettingsRegistry.build {
            systemDefinitions(mockViewModel, portableConfig)
        }

        val settings = AppSettings()
        val allDefinitions = registry.definitions.value

        // Startup setting should be disabled
        val startupDef = allDefinitions.find { it.id == "general.launchAtStartup" }
        assertTrue(startupDef != null)
        assertFalse(startupDef!!.enabled(settings))

        // Minimized startup setting should be disabled
        val minStartupDef = allDefinitions.find { it.id == "general.launchMinimizedAtStartup" }
        assertTrue(minStartupDef != null)
        assertFalse(minStartupDef!!.enabled(settings))

        // Cache management setting should be present
        val cacheDef = allDefinitions.find { it.id == "general.cacheManagement" }
        assertTrue(cacheDef != null)
        assertTrue(cacheDef is SettingDefinition.DropdownSetting<*>)

        // Auto update setting should be omitted
        val autoUpdateDef = allDefinitions.find { it.id == "autoUpdate.enabled" }
        assertNull(autoUpdateDef)
    }

    @Test
    fun testSettingsRegistryDefaultDefinitions() {
        val defaultConfig = DefaultSystemConfig()
        val mockViewModel = mockk<SettingsViewModel>(relaxed = true)

        val registry = SettingsRegistry.build {
            systemDefinitions(mockViewModel, defaultConfig)
        }

        val settings = AppSettings()
        val allDefinitions = registry.definitions.value

        // Startup setting should be enabled
        val startupDef = allDefinitions.find { it.id == "general.launchAtStartup" }
        assertTrue(startupDef != null)
        assertTrue(startupDef!!.enabled(settings))

        // Auto update setting should be present
        val autoUpdateDef = allDefinitions.find { it.id == "autoUpdate.enabled" }
        assertTrue(autoUpdateDef != null)
    }

    @Test
    fun testPortableNoHostLeakage() = runBlocking {
        val sandboxDir = File(System.getProperty("java.io.tmpdir"), "portable_leakage_test_" + System.currentTimeMillis())
        sandboxDir.mkdirs()
        try {
            val portableConfig = PortableSystemConfig(baseDir = sandboxDir.absolutePath)

            startKoin {
                modules(module {
                    single<SystemConfig> { portableConfig }
                })
            }

            val persistence = org.wip.plugintoolkit.features.settings.logic.JvmSettingsPersistence()
            val customSettings = AppSettings(
                general = org.wip.plugintoolkit.features.settings.model.GeneralSettings(
                    scaling = 1.5f,
                    closeToTray = true
                )
            )
            persistence.save(customSettings)

            // Verify written file is in sandbox
            val settingsFile = File(sandboxDir, "data/settings.json")
            assertTrue("settings.json must exist in sandbox/data", settingsFile.exists())

            // Verify persistence loads the saved settings from sandbox
            val loaded = persistence.load()
            assertEquals(1.5f, loaded.general.scaling)
            assertTrue(loaded.general.closeToTray)

            // Verify getJobsDir is in sandbox
            val jobsDir = File(persistence.getJobsDir())
            assertTrue(jobsDir.absolutePath.startsWith(sandboxDir.absolutePath))
            assertTrue(jobsDir.exists())
        } finally {
            sandboxDir.deleteRecursively()
        }
    }
}
