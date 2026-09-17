package org.wip.plugintoolkit.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StandaloneSystemConfigTest {

    @Test
    fun testStandaloneSystemConfigIsolation() {
        val pluginId = "com.wip.cleaner"
        val config = StandaloneSystemConfig(pluginId)

        assertFalse(config.isPortable)
        assertNull(config.WINDOWS_STARTUP_REGISTRY_PATH)
        assertNull(config.LINUX_AUTOSTART_DIR)
        assertNull(config.LINUX_DESKTOP_FILENAME)
        assertEquals("PluginToolkit-com.wip.cleaner", config.STARTUP_APP_NAME)
        assertEquals("PluginToolkit/standalone/com.wip.cleaner", config.APP_DATA_DIR_NAME)

        val appDataDir = config.getAppDataDir().replace('\\', '/')
        assertTrue(
            appDataDir.contains("PluginToolkit/standalone/com.wip.cleaner") ||
                appDataDir.contains(".plugintoolkit/standalone/com.wip.cleaner")
        )

        val cacheDir = config.getCacheDir().replace('\\', '/')
        assertTrue(
            cacheDir.contains("PluginToolkit/standalone/com.wip.cleaner/cache") ||
                cacheDir.contains(".plugintoolkit/standalone/com.wip.cleaner/cache")
        )
    }

    @Test
    fun testStandaloneSystemConfigDistinctPerPlugin() {
        val configCleaner = StandaloneSystemConfig("com.wip.cleaner")
        val configComplete = StandaloneSystemConfig("org.wip.complete")
        val configDefault = DefaultSystemConfig()

        assertNotEquals(configCleaner.getAppDataDir(), configComplete.getAppDataDir())
        assertNotEquals(configCleaner.getAppDataDir(), configDefault.getAppDataDir())
        assertNotEquals(configComplete.getAppDataDir(), configDefault.getAppDataDir())
    }

    @Test
    fun testStandaloneSystemConfigPortableMode() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "standalone_portable_test")
        val config = StandaloneSystemConfig(
            pluginId = "org.wip.complete",
            baseDir = tempDir.absolutePath
        )

        assertTrue(config.isPortable)
        assertNull(config.WINDOWS_STARTUP_REGISTRY_PATH)
        assertNull(config.LINUX_AUTOSTART_DIR)
        assertNull(config.LINUX_DESKTOP_FILENAME)

        val expectedData = tempDir.absolutePath.replace('\\', '/').removeSuffix("/") + "/data"
        assertEquals(expectedData, config.getAppDataDir())
        assertEquals("$expectedData/cache", config.getCacheDir(systemManaged = false))
    }
}
