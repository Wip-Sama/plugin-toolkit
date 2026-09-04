package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.readBytes
import io.ktor.utils.io.readAvailable
import io.ktor.http.encodeURLPathPart
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.core.utils.FileSystem
import org.wip.plugintoolkit.core.utils.VersionUtils
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.repository.logic.RepoManager
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import kotlin.time.Clock

/**
 * Handles the installation and removal of plugin files.
 * Coordinates with PluginRegistry for state updates and PluginLifecycleManager for unloading.
 */
class PluginInstaller(
    private val registry: PluginRegistry,
    private val repoManager: RepoManager,
    private val lifecycleManager: PluginLifecycleManager,
    private val settingsRepository: SettingsRepository,
    private val jobManager: JobManager,
    private val client: HttpClient,
    private val fileSystem: FileSystem
) {
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }

    /**
     * Installs a plugin from a local JAR file.
     */
    suspend fun installLocal(filePath: String, targetFolderPath: String): Result<PluginManifest?> {
        Logger.i { "Installing local plugin from: $filePath into $targetFolderPath" }
        return try {
            fileSystem.mkdirs(targetFolderPath)

            val manifest = getManifestFromJar(filePath)
            val pkg =
                manifest?.plugin?.id ?: filePath.replace('\\', '/').substringAfterLast("/").substringBeforeLast(".")
            val name = manifest?.plugin?.name ?: pkg
            val version = manifest?.plugin?.version ?: "1.0.0"
            val description = manifest?.plugin?.description
            val pluginDir = "$targetFolderPath/$pkg"

            fileSystem.mkdirs(pluginDir)

            val jarFileName = filePath.replace('\\', '/').substringAfterLast("/")
            val dest = "$pluginDir/$jarFileName"

            // Unload existing plugin if present to release file locks and in-memory caches
            try {
                lifecycleManager.unloadPlugin(pkg)
            } catch (t: Throwable) {
                Logger.w { "Could not unload plugin $pkg before install/reinstall: ${t.message}" }
            }
            PluginLoader.unloadPluginById(pkg)

            // Remove previous JAR if filename changed
            val existingPlugin = registry.getPlugin(pkg)
            if (existingPlugin?.jarFileName != null && existingPlugin.jarFileName != jarFileName) {
                val oldJarPath = "$pluginDir/${existingPlugin.jarFileName}"
                try {
                    fileSystem.deleteDirectory(oldJarPath)
                } catch (t: Throwable) {
                    Logger.w { "Could not delete old JAR $oldJarPath: ${t.message}" }
                }
            }

            fileSystem.copyFile(filePath, dest)

            val (isCompatible, compError) = manifest?.let { checkCompatibility(it) } ?: (true to null as String?)

            val newPlugin = InstalledPlugin(
                pkg = pkg,
                name = name,
                version = version,
                installPath = pluginDir,
                jarFileName = jarFileName,
                description = description,
                isValidated = false,
                isCompatible = isCompatible,
                compatibilityError = compError,
                supportedOs = manifest?.plugin?.supportedOs ?: emptyList(),
                targetAppVersion = manifest?.requirements?.targetAppVersion
            )

            registry.addOrUpdatePlugin(newPlugin)
            Logger.i { "Successfully installed local plugin: $pkg" }
            Result.success(manifest)
        } catch (t: Throwable) {
            Logger.e(t) { "Failed local installation: $filePath" }
            Result.failure(t)
        }
    }

    fun getManifestFromJar(jarPath: String): PluginManifest? {
        val manifestContent = fileSystem.readFileFromZip(jarPath, "manifest.json")
            ?: fileSystem.readFileFromZip(jarPath, "META-INF/manifest.json")

        return manifestContent?.let {
            try {
                json.decodeFromString<PluginManifest>(it)
            } catch (e: Exception) {
                Logger.w { "Failed to parse manifest from jar $jarPath: ${e.message}" }
                null
            }
        }
    }

    /**
     * Installs a plugin from a remote repository.
     */
    suspend fun installRemote(
        plugin: ExtensionPlugin,
        targetFolderPath: String,
        onProgress: ((Float) -> Unit)? = null
    ): Result<PluginManifest?> {
        Logger.i { "Installing remote plugin: ${plugin.pkg} from ${plugin.repoUrl}" }
        return try {
            fileSystem.mkdirs(targetFolderPath)
            val pluginDir = "$targetFolderPath/${plugin.pkg}"
            fileSystem.mkdirs(pluginDir)

            val repoUrl = plugin.repoUrl ?: return Result.failure(Exception("Missing repo URL"))
            val repo = repoManager.repositories.value.find { it.url == repoUrl }
            val isLocal = repo?.isLocal ?: (!repoUrl.startsWith("http://", ignoreCase = true) && !repoUrl.startsWith("https://", ignoreCase = true))
            val baseLocation = repo?.getBaseLocation() ?: (if (repoUrl.contains('/')) repoUrl.replace('\\', '/').substringBeforeLast('/') else repoUrl)
            val pluginsFolder = repo?.pluginsFolder ?: "plugins"
            val baseUrl = "$baseLocation/$pluginsFolder/${plugin.pkg}"
            val destFile = "$pluginDir/${plugin.fileName}"

            // Unload existing plugin if present to release file locks and in-memory caches
            try {
                lifecycleManager.unloadPlugin(plugin.pkg)
            } catch (t: Throwable) {
                Logger.w { "Could not unload plugin ${plugin.pkg} before remote install/reinstall: ${t.message}" }
            }
            PluginLoader.unloadPluginById(plugin.pkg)

            // Remove previous JAR if filename changed
            val existingPlugin = registry.getPlugin(plugin.pkg)
            if (existingPlugin?.jarFileName != null && existingPlugin.jarFileName != plugin.fileName) {
                val oldJarPath = "$pluginDir/${existingPlugin.jarFileName}"
                try {
                    fileSystem.deleteDirectory(oldJarPath)
                } catch (t: Throwable) {
                    Logger.w { "Could not delete old JAR $oldJarPath: ${t.message}" }
                }
            }

            if (isLocal) {
                val sourceJar = "$baseUrl/${plugin.fileName}"
                if (!fileSystem.exists(sourceJar)) {
                    return Result.failure(Exception("Source plugin file not found: $sourceJar"))
                }
                fileSystem.copyFile(sourceJar, destFile)
                onProgress?.invoke(1.0f)
                listOf("icon.png", "icon.webp", "icon.svg", "icon.jpg").forEach {
                    val assetPath = "$baseUrl/$it"
                    if (fileSystem.exists(assetPath)) {
                        fileSystem.copyFile(assetPath, "$pluginDir/$it")
                    }
                }
                val changelogPath = "$baseUrl/changelog.md"
                if (fileSystem.exists(changelogPath)) {
                    fileSystem.copyFile(changelogPath, "$pluginDir/changelog.md")
                }
            } else {
                val pluginFileUrl = "$baseUrl/${plugin.fileName.encodeURLPathPart()}"
                downloadFile(pluginFileUrl, destFile, onProgress).onFailure { return Result.failure(it) }

                // Download optional assets
                listOf("icon.png", "icon.webp", "icon.svg", "icon.jpg").forEach {
                    downloadFile("$baseUrl/$it", "$pluginDir/$it")
                }
                downloadFile("$baseUrl/changelog.md", "$pluginDir/changelog.md")
            }

            // Signature verification
            val publicKey = repo?.signPublicKey
            val strictChecking = settingsRepository.loadSettings().extensions.strictSignatureChecking

            var isSignatureValid = true
            if (publicKey != null) {
                isSignatureValid = PluginSecurity.verify(destFile, publicKey)
            } else {
                Logger.w { "No public key found for repo $repoUrl, treating signature as valid for now." }
            }

            if (!isSignatureValid) {
                Logger.w { "Signature verification failed for ${plugin.pkg}" }
                if (strictChecking) {
                    fileSystem.deleteDirectory(pluginDir)
                    return Result.failure(Exception("Plugin signature verification failed (strict checking enabled)"))
                }
            }

            val manifest = getManifestFromJar(destFile)
            val (isCompatible, compError) = manifest?.let { checkCompatibility(it) } ?: (true to null as String?)

            val newPlugin = InstalledPlugin(
                pkg = plugin.pkg,
                name = plugin.name,
                version = plugin.version,
                installPath = pluginDir,
                repoUrl = repoUrl,
                jarFileName = plugin.fileName,
                description = plugin.description,
                isEnabled = isSignatureValid,
                isValidated = false,
                isCompatible = isCompatible,
                compatibilityError = compError,
                requiredAction = if (!isSignatureValid) "CONFIRM_SIGNATURE" else null,
                loadError = if (!isSignatureValid) "Invalid Signature" else null,
                supportedOs = manifest?.plugin?.supportedOs ?: emptyList(),
                targetAppVersion = manifest?.requirements?.targetAppVersion ?: plugin.minAppVersion
            )
            registry.addOrUpdatePlugin(newPlugin)
            Logger.i { "Successfully installed remote plugin: ${plugin.pkg}" }
            Result.success(getManifestFromJar(destFile))
        } catch (t: Throwable) {
            Logger.e(t) { "Failed remote installation: ${plugin.pkg}" }
            Result.failure(t)
        }
    }

    suspend fun enqueueRemoteInstall(plugin: ExtensionPlugin, targetFolderPath: String) {
        Logger.i { "Enqueuing remote installation for: ${plugin.pkg}" }
        val job = BackgroundJob(
            id = "install_${plugin.pkg}_${Clock.System.now().toEpochMilliseconds()}",
            name = "Installing: ${plugin.name}",
            type = JobType.PluginInstallation,
            pluginId = plugin.pkg,
            capabilityName = "install",
            parameters = mapOf(
                "pluginJson" to kotlinx.serialization.json.Json.encodeToJsonElement(
                    ExtensionPlugin.serializer(),
                    plugin
                ),
                "targetFolderPath" to kotlinx.serialization.json.JsonPrimitive(targetFolderPath)
            ),
            isCancellable = true,
            isPausable = false
        )
        jobManager.enqueueJob(job)
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

        try {
            fileSystem.deleteDirectory(plugin.installPath)
        } catch (e: Exception) {
            Logger.w(e) { "Failed to fully delete plugin directory for $pkg (it may be locked by the OS), but it will be removed from registry." }
        }

        registry.removePlugin(pkg)
        Logger.i { "Successfully uninstalled plugin: $pkg" }
        return Result.success(Unit)
    }

    /**
     * Updates a local plugin by installing a new JAR over the existing path.
     */
    suspend fun updateLocal(pkg: String, newJarPath: String): Result<PluginManifest?> {
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
    suspend fun updateRemote(pkg: String): Result<PluginManifest?> {
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
     * Clears the plugin's file directory.
     */
    fun clearFiles(pkg: String): Result<Unit> {
        val plugin = registry.getPlugin(pkg) ?: return Result.failure(Exception("Plugin $pkg not found"))
        val filesPath = "${plugin.installPath}/files"
        return if (fileSystem.exists(filesPath)) {
            try {
                // We don't delete the "files" folder itself, only its content
                fileSystem.listFiles(filesPath).forEach {
                    val path = "$filesPath/$it"
                    fileSystem.deleteDirectory(path)
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.success(Unit)
        }
    }

    /**
     * Checks if an update is available for the given plugin.
     */
    fun getUpdate(pkg: String): ExtensionPlugin? {
        return repoManager.plugins.value.values.flatten().find { it.pkg == pkg }?.let { remote ->
            val installed = registry.getPlugin(pkg)
            if (installed != null && VersionUtils.compare(remote.version, installed.version) > 0) {
                remote
            } else null
        }
    }

    private fun checkCompatibility(manifest: PluginManifest): Pair<Boolean, String?> {
        return org.wip.plugintoolkit.features.plugin.utils.PluginCompatibilityUtils.checkCompatibility(manifest)
    }

    private suspend fun downloadFile(url: String, dest: String, onProgress: ((Float) -> Unit)? = null): Result<Unit> {
        return try {
            val response = client.get(url)
            if (response.status.value !in 200..299) {
                return Result.failure(Exception("Failed to download: ${response.status}"))
            }

            val contentLength = response.headers[io.ktor.http.HttpHeaders.ContentLength]?.toLong()
            val bytes = if (contentLength != null && contentLength > 0L && onProgress != null) {
                val channel = response.bodyAsChannel()
                val tempBuffer = ByteArray(8192)
                var totalRead = 0L
                val packet = kotlinx.io.Buffer()
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(tempBuffer)
                    if (read == -1) break
                    if (read > 0) {
                        totalRead += read
                        packet.write(tempBuffer, 0, read)
                        onProgress((totalRead.toFloat() / contentLength).coerceIn(0f, 1f))
                    }
                }
                packet.readByteArray()
            } else {
                response.readBytes()
            }

            fileSystem.saveFile(dest, bytes)
            onProgress?.invoke(1.0f)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
