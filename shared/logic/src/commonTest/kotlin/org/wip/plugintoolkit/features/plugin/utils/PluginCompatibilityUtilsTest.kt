package org.wip.plugintoolkit.features.plugin.utils

import org.wip.plugintoolkit.AppConfig
import org.wip.plugintoolkit.api.OS
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.api.Requirements
import org.wip.plugintoolkit.core.utils.FileUtils
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PluginCompatibilityUtilsTest {

    @Test
    fun testObsoletePluginManifestReturnsIncompatible() {
        val manifest = PluginManifest(
            manifestVersion = "1.0",
            plugin = PluginInfo(
                id = "com.wip.obsolete",
                name = "Obsolete Plugin",
                version = "1.0.0",
                description = "Obsolete test plugin"
            ),
            requirements = Requirements(
                minMemoryMb = 0,
                minExecutionTimeMs = 0,
                targetAppVersion = "1.7.2"
            )
        )

        val (isCompatible, error) = PluginCompatibilityUtils.checkCompatibility(manifest)
        assertFalse(isCompatible, "Plugin targeted for 1.7.2 should be incompatible when MIN_COMPATIBLE_PLUGIN_VERSION is 2.0.0")
        assertNotNull(error)
        assertTrue(error.contains("obsolete"), "Error message should mention obsolete target version")
    }

    @Test
    fun testCompatiblePluginManifestReturnsTrue() {
        val manifest = PluginManifest(
            manifestVersion = "1.0",
            plugin = PluginInfo(
                id = "com.wip.compatible",
                name = "Compatible Plugin",
                version = "1.0.0",
                description = "Compatible test plugin"
            ),
            requirements = Requirements(
                minMemoryMb = 0,
                minExecutionTimeMs = 0,
                targetAppVersion = AppConfig.VERSION
            )
        )

        val (isCompatible, error) = PluginCompatibilityUtils.checkCompatibility(manifest)
        assertTrue(isCompatible, "Plugin targeted for ${AppConfig.VERSION} should be compatible")
        assertTrue(error == null, "Error message should be null for compatible plugin")
    }

    @Test
    fun testPluginRequiringNewerAppVersionReturnsIncompatible() {
        val manifest = PluginManifest(
            manifestVersion = "1.0",
            plugin = PluginInfo(
                id = "com.wip.future",
                name = "Future Plugin",
                version = "1.0.0",
                description = "Future test plugin"
            ),
            requirements = Requirements(
                minMemoryMb = 0,
                minExecutionTimeMs = 0,
                targetAppVersion = "99.0.0"
            )
        )

        val (isCompatible, error) = PluginCompatibilityUtils.checkCompatibility(manifest)
        assertFalse(isCompatible, "Plugin requiring app version 99.0.0 should be incompatible")
        assertNotNull(error)
        assertTrue(error.contains("newer app version"), "Error message should mention requiring a newer app version")
    }

    @Test
    fun testInstalledPluginCompatibilityCheck() {
        val obsoletePlugin = InstalledPlugin(
            pkg = "com.wip.obsolete.local",
            name = "Obsolete Local Plugin",
            version = "1.0.0",
            installPath = "/fake/path",
            targetAppVersion = "1.7.2"
        )

        val (isObsoleteCompatible, obsoleteError) = PluginCompatibilityUtils.checkCompatibility(obsoletePlugin)
        assertFalse(isObsoleteCompatible, "InstalledPlugin targeted for 1.7.2 should be marked incompatible")
        assertNotNull(obsoleteError)

        val compatiblePlugin = InstalledPlugin(
            pkg = "com.wip.compatible.local",
            name = "Compatible Local Plugin",
            version = "1.0.0",
            installPath = "/fake/path",
            targetAppVersion = AppConfig.VERSION
        )

        val (isCompCompatible, compError) = PluginCompatibilityUtils.checkCompatibility(compatiblePlugin)
        assertTrue(isCompCompatible, "InstalledPlugin targeted for ${AppConfig.VERSION} should be marked compatible")
        assertTrue(compError == null)
    }

    @Test
    fun testUnsupportedOsPluginReturnsIncompatible() {
        val otherOs = if (FileUtils.isWindows) OS.LINUX else OS.WINDOWS
        val unsupportedOsManifest = PluginManifest(
            manifestVersion = "1.0",
            plugin = PluginInfo(
                id = "com.wip.unsupported_os",
                name = "Unsupported OS Plugin",
                version = "1.0.0",
                description = "Unsupported OS test plugin",
                supportedOs = listOf(otherOs)
            ),
            requirements = Requirements(
                minMemoryMb = 0,
                minExecutionTimeMs = 0,
                targetAppVersion = AppConfig.VERSION
            )
        )

        val (isCompatible, error) = PluginCompatibilityUtils.checkCompatibility(unsupportedOsManifest)
        assertFalse(isCompatible, "Plugin specifying only unsupported OS should be marked incompatible")
        assertNotNull(error)
        assertTrue(error.contains("operating system"), "Error message should mention operating system incompatibility")
    }
}
