package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.repository.logic.RepoManager
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository

/**
 * Handles the installation and removal of plugin files.
 * Coordinates with PluginRegistry for state updates and PluginLifecycleManager for unloading.
 */
class PluginInstaller(
    private val registry: PluginRegistry,
    private val repoManager: RepoManager,
    private val lifecycleManager: PluginLifecycleManager,
    private val settingsRepository: SettingsRepository
) {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /**
     * Installs a plugin from a local JAR file.
     */
    suspend fun installLocal(filePath: String, targetFolderPath: String): Result<Unit> {
        Logger.i { "Installing local plugin from: $filePath into $targetFolderPath" }
        return try {
            PlatformUtils.mkdirs(targetFolderPath)

            // Try to read manifest from JAR
            val manifestContent = PlatformUtils.readFileFromZip(filePath, "manifest.json")
                ?: PlatformUtils.readFileFromZip(filePath, "META-INF/manifest.json")

            val manifest = manifestContent?.let {
                try {
                    json.decodeFromString<PluginManifest>(it)
                } catch (e: Exception) {
                    Logger.w { "Failed to parse manifest from zip: ${e.message}" }
                    null
                }
            }

            val pkg = manifest?.plugin?.id ?: filePath.replace('\\', '/').substringAfterLast("/").substringBeforeLast(".")
            val name = manifest?.plugin?.name ?: pkg
            val version = manifest?.plugin?.version ?: "1.0.0"
            val description = manifest?.plugin?.description

            val pluginDir = "$targetFolderPath/$pkg"
            PlatformUtils.mkdirs(pluginDir)

            val jarFileName = filePath.replace('\\', '/').substringAfterLast("/")
            val dest = "$pluginDir/$jarFileName"
            PlatformUtils.copyFile(filePath, dest)

            val newPlugin = InstalledPlugin(
                pkg = pkg,
                name = name,
                version = version,
                installPath = pluginDir,
                jarFileName = jarFileName,
                description = description,
                isValidated = false
            )

            registry.addOrUpdatePlugin(newPlugin)
            Logger.i { "Successfully installed local plugin: $pkg" }
            Result.success(Unit)
        } catch (t: Throwable) {
            Logger.e(t) { "Failed local installation: $filePath" }
            Result.failure(t)
        }
    }

    /**
     * Installs a plugin from a remote repository.
     */
    suspend fun installRemote(plugin: ExtensionPlugin, targetFolderPath: String): Result<Unit> {
        Logger.i { "Installing remote plugin: ${plugin.pkg} from ${plugin.repoUrl}" }
        return try {
            PlatformUtils.mkdirs(targetFolderPath)
            val pluginDir = "$targetFolderPath/${plugin.pkg}"
            PlatformUtils.mkdirs(pluginDir)

            val repoUrl = plugin.repoUrl ?: return Result.failure(Exception("Missing repo URL"))
            val pluginsFolder = repoUrl.substringBeforeLast("/") + "/plugins"
            val baseUrl = "$pluginsFolder/${plugin.pkg}"

            val pluginFileUrl = "$baseUrl/${plugin.fileName}"
            val destFile = "$pluginDir/${plugin.fileName}"

            PlatformUtils.downloadFile(pluginFileUrl, destFile).onFailure { return Result.failure(it) }

            // Download optional assets
            listOf("icon.png", "icon.webp", "icon.svg", "icon.jpg").forEach { 
                PlatformUtils.downloadFile("$baseUrl/$it", "$pluginDir/$it")
            }
            PlatformUtils.downloadFile("$baseUrl/changelog.txt", "$pluginDir/changelog.txt")

            val newPlugin = InstalledPlugin(
                pkg = plugin.pkg,
                name = plugin.name,
                version = plugin.version,
                installPath = pluginDir,
                repoUrl = repoUrl,
                jarFileName = plugin.fileName,
                description = plugin.description
            )
            registry.addOrUpdatePlugin(newPlugin)
            Logger.i { "Successfully installed remote plugin: ${plugin.pkg}" }
            Result.success(Unit)
        } catch (t: Throwable) {
            Logger.e(t) { "Failed remote installation: ${plugin.pkg}" }
            Result.failure(t)
        }
    }

    /**
     * Uninstalls a plugin and removes its directory.
     */
    suspend fun uninstall(pkg: String): Result<Unit> {
        Logger.i { "Uninstalling plugin: $pkg" }
        val plugin = registry.getPlugin(pkg) ?: return Result.failure(Exception("Plugin $pkg not found"))

        // Unload safely (includes job check)
        try {
            lifecycleManager.unloadPlugin(pkg)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        PlatformUtils.deleteDirectory(plugin.installPath)
        registry.removePlugin(pkg)
        Logger.i { "Successfully uninstalled plugin: $pkg" }
        return Result.success(Unit)
    }

    /**
     * Updates a local plugin by installing a new JAR over the existing path.
     */
    suspend fun updateLocal(pkg: String, newJarPath: String): Result<Unit> {
        val plugin = registry.getPlugin(pkg) ?: return Result.failure(Exception("Plugin $pkg not found"))
        try {
            lifecycleManager.unloadPlugin(pkg)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return installLocal(newJarPath, plugin.installPath.substringBeforeLast("/"))
    }

    /**
     * Updates a remote plugin.
     */
    suspend fun updateRemote(pkg: String): Result<Unit> {
        val update = getUpdate(pkg) ?: return Result.failure(Exception("No update available for $pkg"))
        val plugin = registry.getPlugin(pkg) ?: return Result.failure(Exception("Plugin $pkg not found"))
        try {
            lifecycleManager.unloadPlugin(pkg)
        } catch (e: Exception) {
            return Result.failure(e)
        }
        return installRemote(update, plugin.installPath.substringBeforeLast("/"))
    }

    /**
     * Checks if an update is available for the given plugin.
     */
    fun getUpdate(pkg: String): ExtensionPlugin? {
        return repoManager.plugins.value.values.flatten().find { it.pkg == pkg }?.let { remote ->
            val installed = registry.getPlugin(pkg)
            if (installed != null && isNewer(remote.version, installed.version)) {
                remote
            } else null
        }
    }

    private fun isNewer(v1: String, v2: String): Boolean {
        val s1 = v1.split(".").mapNotNull { it.toIntOrNull() }
        val s2 = v2.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until minOf(s1.size, s2.size)) {
            if (s1[i] > s2[i]) return true
            if (s1[i] < s2[i]) return false
        }
        return s1.size > s2.size
    }
}
