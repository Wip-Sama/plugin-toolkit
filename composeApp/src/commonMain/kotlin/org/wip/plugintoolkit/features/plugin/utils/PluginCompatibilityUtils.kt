package org.wip.plugintoolkit.features.plugin.utils

import org.wip.plugintoolkit.AppConfig
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.core.utils.VersionUtils
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin

object PluginCompatibilityUtils {
    fun checkCompatibility(manifest: PluginManifest): Pair<Boolean, String?> {
        val target = manifest.requirements.targetAppVersion ?: "1.0.0"
        val current = AppConfig.VERSION
        val min = AppConfig.MIN_COMPATIBLE_PLUGIN_VERSION

        if (VersionUtils.compare(target, current) > 0) {
            return false to "Plugin requires a newer app version (targeted for $target)"
        }
        if (VersionUtils.compare(target, min) < 0) {
            return false to "Plugin is obsolete (targeted for $target, min supported $min)"
        }

        val supportedOs = manifest.plugin.supportedOs
        if (supportedOs.isNotEmpty()) {
            val currentOs = when {
                PlatformUtils.isWindows -> org.wip.plugintoolkit.api.OS.WINDOWS
                PlatformUtils.isLinux -> org.wip.plugintoolkit.api.OS.LINUX
                PlatformUtils.isMac -> org.wip.plugintoolkit.api.OS.MACOS
                else -> null
            }
            if (currentOs == null || currentOs !in supportedOs) {
                return false to "Plugin does not support the current operating system (supported: ${supportedOs.joinToString { it.name }})"
            }
        }

        return true to null
    }

    fun checkCompatibility(plugin: ExtensionPlugin): Pair<Boolean, String?> {
        if (plugin.manifest != null) {
            return checkCompatibility(plugin.manifest)
        }

        // Fallback for ExtensionPlugin without a manifest
        val target = plugin.minAppVersion ?: return true to null
        val current = AppConfig.VERSION
        val min = AppConfig.MIN_COMPATIBLE_PLUGIN_VERSION

        if (VersionUtils.compare(target, current) > 0) {
            return false to "Plugin requires a newer app version (targeted for $target)"
        }
        if (VersionUtils.compare(target, min) < 0) {
            return false to "Plugin is obsolete (targeted for $target, min supported $min)"
        }
        
        return true to null
    }
}
