package org.wip.plugintoolkit

import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopAppTest {

    @Test
    fun testVersionMatchesConfig() {
        assertTrue(AppConfig.VERSION.isNotEmpty())
    }

    @Test
    fun testDetectSystemConfig() {
        val config = detectSystemConfig()
        assertTrue(config.STARTUP_APP_NAME.isNotEmpty())
        assertTrue(config.STARTUP_FLAG_SAFE_MODE == "--safe-mode")
        assertTrue(config.STARTUP_FLAG_NO_SETTINGS == "--no-settings")
        assertTrue(config.STARTUP_FLAG_NO_PLUGINS == "--no-plugins")
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
