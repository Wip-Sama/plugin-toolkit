package org.wip.plugintoolkit

import org.wip.plugintoolkit.core.PortableSystemConfig
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAppTest {

    @AfterTest
    fun clearPortableProperties() {
        System.clearProperty(DevBootstrap.PROP_PORTABLE)
        System.clearProperty(DevBootstrap.PROP_PORTABLE_DIR)
        System.clearProperty(DevBootstrap.PROP_DEV_PLUGINS)
        System.clearProperty(DevBootstrap.PROP_DEV_REPO)
    }

    @Test
    fun testVersionMatchesConfig() {
        assertTrue(AppConfig.VERSION.isNotEmpty())
    }

    @Test
    fun testDetectSystemConfig() {
        val config = detectSystemConfig()
        assertTrue(config.STARTUP_APP_NAME.isNotEmpty())
        assertEquals("--safe-mode", config.STARTUP_FLAG_SAFE_MODE)
        assertEquals("--no-settings", config.STARTUP_FLAG_NO_SETTINGS)
        assertEquals("--no-plugins", config.STARTUP_FLAG_NO_PLUGINS)
        assertEquals("--portable", config.STARTUP_FLAG_PORTABLE)
        assertEquals("--portable-dir", config.STARTUP_FLAG_PORTABLE_DIR)
    }

    @Test
    fun testDetectSystemConfigPortableCliAndProperties() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "pt_portable_cli_${System.currentTimeMillis()}")
        val expectedDataDir = "${tempDir.absolutePath.replace('\\', '/').removeSuffix("/")}/data"

        val equalsConfig = detectSystemConfig(arrayOf("--portable-dir=${tempDir.absolutePath}"))
        assertTrue(equalsConfig is PortableSystemConfig)
        assertTrue(equalsConfig.isPortable)
        assertEquals(expectedDataDir, equalsConfig.getAppDataDir())

        val spacedConfig = detectSystemConfig(arrayOf("--portable-dir", tempDir.absolutePath))
        assertTrue(spacedConfig is PortableSystemConfig)
        assertEquals(expectedDataDir, spacedConfig.getAppDataDir())

        System.setProperty(DevBootstrap.PROP_PORTABLE_DIR, tempDir.absolutePath)
        val propConfig = detectSystemConfig()
        assertTrue(propConfig is PortableSystemConfig)
        assertEquals(expectedDataDir, propConfig.getAppDataDir())

        System.clearProperty(DevBootstrap.PROP_PORTABLE_DIR)
        System.setProperty(DevBootstrap.PROP_PORTABLE, "true")
        val flagPropConfig = detectSystemConfig()
        assertTrue(flagPropConfig.isPortable)

        System.clearProperty(DevBootstrap.PROP_PORTABLE)
        val cliFlagConfig = detectSystemConfig(arrayOf("--portable"))
        assertTrue(cliFlagConfig.isPortable)

        val defaultConfig = detectSystemConfig(emptyArray())
        assertFalse(defaultConfig.isPortable)
    }

    @Test
    fun testSafeModeFlagsDetection() {
        val config = detectSystemConfig()
        val safeArgs = arrayOf("--safe-mode", "--debug")
        val isSafeMode = safeArgs.contains(config.STARTUP_FLAG_SAFE_MODE) ||
                safeArgs.contains(config.STARTUP_FLAG_NO_SETTINGS) ||
                safeArgs.contains("--provisional")
        val isNoPlugins = isSafeMode || safeArgs.contains(config.STARTUP_FLAG_NO_PLUGINS)

        assertTrue(isSafeMode, "Safe mode flag should be detected")
        assertTrue(isNoPlugins, "Safe mode should also imply no-plugins auto-load")
    }

    @Test
    fun testTransientSettingsPersistence() = kotlinx.coroutines.test.runTest {
        val transientPersistence = org.wip.plugintoolkit.features.settings.logic.JvmSettingsPersistence(isTransient = true)
        val loaded = transientPersistence.load()
        kotlin.test.assertNotNull(loaded)
        // Verify saving does not throw in transient mode
        transientPersistence.save(loaded)
    }
}
