package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import org.wip.plugintoolkit.AppConfig
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.core.KeepTrack
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.core.utils.VersionUtils
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin

/**
 * Handles discovery of plugins by scanning managed folders on the filesystem.
 */
class PluginScanner(
    private val registry: PluginRegistry
) {
    private val json = kotlinx.serialization.json.Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').removeSuffix("/")
    }

    private fun checkCompatibility(manifest: PluginManifest): Pair<Boolean, String?> {
        val target = manifest.requirements.targetAppVersion ?: return true to null
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

    /**
     * Rescans all managed folders for JAR files and updates the registry state.
     * This is a "deep" scan that inspects JAR manifests.
     */
    suspend fun rescanManagedFolders() {
        val folders = registry.getManagedFolders()
        val allUpdatedPlugins = mutableListOf<InstalledPlugin>()
        var globalChanged = false

        folders.forEach { folderPath ->
            val normalizedFolder = normalizePath(folderPath)
            val trackingFile = "$normalizedFolder/${KeepTrack.INSTALLED_PLUGINS_FILE_NAME}"
            
            val existingContent = PlatformUtils.readFile(trackingFile)
            val existingPlugins = try {
                if (existingContent != null) json.decodeFromString<List<InstalledPlugin>>(existingContent) else emptyList()
            } catch (e: Exception) {
                emptyList()
            }.associateBy { it.pkg }

            val folderPlugins = mutableListOf<InstalledPlugin>()
            var folderChanged = false

            Logger.i { "Rescanning managed folder: $normalizedFolder" }
            PlatformUtils.listDirectories(normalizedFolder).forEach { dirPath ->
                val normalizedDir = normalizePath(dirPath)
                val folderName = normalizedDir.substringAfterLast("/")

                val jarFiles = PlatformUtils.listFiles(normalizedDir).filter { it.endsWith(".jar") }
                jarFiles.forEach { jarPath ->
                    val normalizedJarPath = normalizePath(jarPath)
                    val jarFileName = normalizedJarPath.substringAfterLast("/")

                    var manifestContent = PlatformUtils.readFileFromZip(normalizedJarPath, "manifest.json")
                        ?: PlatformUtils.readFileFromZip(normalizedJarPath, "META-INF/manifest.json")

                    if (manifestContent != null) {
                        try {
                            val manifest = json.decodeFromString<PluginManifest>(manifestContent)
                            val pkg = manifest.plugin.id

                            // We only accept the plugin if the folder name matches the package ID
                            if (folderName == pkg) {
                                val (isCompatible, compError) = checkCompatibility(manifest)
                                val existing = existingPlugins[pkg]
                                val updatedPlugin = if (existing != null) {
                                    // Update metadata but preserve existing user state (isEnabled, isValidated)
                                    existing.copy(
                                        name = manifest.plugin.name,
                                        version = manifest.plugin.version,
                                        installPath = normalizedDir,
                                        jarFileName = jarFileName,
                                        description = manifest.plugin.description,
                                        isCompatible = isCompatible,
                                        compatibilityError = compError
                                    )
                                } else {
                                    folderChanged = true
                                    InstalledPlugin(
                                        pkg = pkg,
                                        name = manifest.plugin.name,
                                        version = manifest.plugin.version,
                                        installPath = normalizedDir,
                                        jarFileName = jarFileName,
                                        description = manifest.plugin.description,
                                        isCompatible = isCompatible,
                                        compatibilityError = compError
                                    )
                                }
                                folderPlugins.add(updatedPlugin)
                            } else {
                                Logger.w { "Skipping JAR in $normalizedDir: Folder name '$folderName' does not match plugin ID '$pkg'" }
                            }
                        } catch (t: Throwable) {
                            Logger.e(t) { "Failed to parse manifest from $normalizedJarPath" }
                        }
                    }
                }
            }

            // Detect deletions
            if (folderPlugins.size != existingPlugins.size) {
                folderChanged = true
            }

            // Sync with disk if changed
            if (folderChanged || !PlatformUtils.exists(trackingFile)) {
                Logger.i { "Updating tracking file: $trackingFile with ${folderPlugins.size} plugins" }
                PlatformUtils.writeFile(trackingFile, json.encodeToString(folderPlugins))
                globalChanged = true
            }
            allUpdatedPlugins.addAll(folderPlugins)
        }

        // Sync with registry if anything changed globally
        if (globalChanged || allUpdatedPlugins.size != registry.installedPlugins.value.size) {
            registry.updatePlugins { allUpdatedPlugins }
        }
    }

    /**
     * Quickly refreshes the registry from the installed_plugins.json files without scanning JARs.
     */
    suspend fun refreshInstalledPlugins() {
        registry.loadFromManagedFolders()
    }
}
