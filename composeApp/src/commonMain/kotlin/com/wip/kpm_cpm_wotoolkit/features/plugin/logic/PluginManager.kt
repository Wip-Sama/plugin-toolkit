package com.wip.kpm_cpm_wotoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import com.wip.kpm_cpm_wotoolkit.core.utils.PlatformUtils
import com.wip.kpm_cpm_wotoolkit.features.job.logic.JobManager
import com.wip.kpm_cpm_wotoolkit.features.job.model.JobStatus
import com.wip.kpm_cpm_wotoolkit.features.plugin.model.InstalledPlugin
import com.wip.kpm_cpm_wotoolkit.features.repository.logic.RepoManager
import com.wip.kpm_cpm_wotoolkit.features.repository.model.ExtensionPlugin
import com.wip.kpm_cpm_wotoolkit.features.settings.logic.SettingsRepository
import com.wip.kpm_cpm_wotoolkit.features.settings.model.PluginUnplugBehavior
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PluginManager(
    private val settingsRepository: SettingsRepository,
    private val repoManager: RepoManager,
    private val jobManager: JobManager
) {
    private val _installedPlugins = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val installedPlugins: StateFlow<List<InstalledPlugin>> = _installedPlugins.asStateFlow()

    private val _loadedPlugins = MutableStateFlow<Set<String>>(emptySet()) // set of pkg
    val loadedPlugins: StateFlow<Set<String>> = _loadedPlugins.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        val settings = settingsRepository.loadSettings()
        _installedPlugins.value = settings.extensions.installedPlugins
        Logger.i { "Initializing PluginManager with ${_installedPlugins.value.size} installed plugins" }
        // Load plugins will happen in main.kt
    }

    suspend fun installLocal(filePath: String, targetFolderPath: String): Result<Unit> {
        Logger.i { "Installing local plugin from: $filePath" }
        return try {
            // Ensure target folder exists
            PlatformUtils.mkdirs(targetFolderPath)

            // For local, we expect a .jar. We should ideally parse it for pkg.
            // Try to read manifest.json from the JAR (check root and META-INF)
            val manifest = (PlatformUtils.readFileFromZip(filePath, "manifest.json")
                ?: PlatformUtils.readFileFromZip(filePath, "META-INF/manifest.json"))?.let { content ->
                try {
                    Logger.d { "Found manifest content: $content" }
                    val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                    json.decodeFromString<com.wip.plugin.api.PluginManifest>(content)
                } catch (e: Exception) {
                    Logger.w { "Failed to parse manifest from $filePath: ${e.message}" }
                    null
                }
            }

            if (manifest == null) {
                Logger.w { "No manifest.json found in $filePath (checked root and META-INF/)" }
            }

            val pkg =
                manifest?.plugin?.id ?: filePath.replace('\\', '/').substringAfterLast("/").substringBeforeLast(".")
            val name = manifest?.plugin?.name ?: pkg
            val version = manifest?.plugin?.version ?: "1.0.0"
            val description = manifest?.plugin?.description

            Logger.i { "Determined plugin ID: $pkg, Name: $name, Version: $version" }
            val pluginDir = "$targetFolderPath/$pkg"
            PlatformUtils.mkdirs(pluginDir)

            val jarFileName = filePath.replace('\\', '/').substringAfterLast("/")
            val dest = "$pluginDir/$jarFileName"
            PlatformUtils.copyFile(filePath, dest)

            Logger.d { "Copied local JAR to: $dest" }
            val newPlugin = InstalledPlugin(
                pkg = pkg,
                name = name,
                version = version,
                installPath = pluginDir,
                jarFileName = jarFileName,
                description = description
            )

            addInstalledPlugin(newPlugin)
            Logger.i { "Successfully installed local plugin: ${newPlugin.pkg} with JAR: $jarFileName" }

            // Auto-load if enabled
            if (newPlugin.isEnabled) {
                setupPluginInBackground(newPlugin.pkg)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to install local plugin: $filePath" }
            Result.failure(e)
        }
    }

    suspend fun installRemote(plugin: ExtensionPlugin, targetFolderPath: String): Result<Unit> {
        Logger.i { "Installing remote plugin: ${plugin.pkg} from ${plugin.repoUrl}" }
        return try {
            PlatformUtils.mkdirs(targetFolderPath)
            val pluginDir = "$targetFolderPath/${plugin.pkg}"
            PlatformUtils.mkdirs(pluginDir)

            // Base URL for the plugin folder
            val repoUrl = plugin.repoUrl ?: return Result.failure(Exception("Missing repo URL"))
            val pluginsFolder = repoUrl.substringBeforeLast("/") + "/plugins"
            val baseUrl = "$pluginsFolder/${plugin.pkg}"

            // 1. Download Plugin File
            val pluginFileUrl = "$baseUrl/${plugin.fileName}"
            val destFile = "$pluginDir/${plugin.fileName}"

            PlatformUtils.downloadFile(pluginFileUrl, destFile).onSuccess {
                if (!verifySignature(destFile)) {
                    return Result.failure(Exception("Invalid signature for ${plugin.fileName}"))
                }

                if (plugin.fileName.endsWith(".zip")) {
                    PlatformUtils.unzip(destFile, pluginDir, 100 * 1024 * 1024).onFailure { return Result.failure(it) }
                }
            }.onFailure { return Result.failure(it) }

            // 2. Download Icon (optional)
            // Try common extensions
            listOf("icon.png", "icon.webp", "icon.svg", "icon.jpg").forEach { iconName ->
                PlatformUtils.downloadFile("$baseUrl/$iconName", "$pluginDir/$iconName")
            }

            // 3. Download Changelog (optional)
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
            addInstalledPlugin(newPlugin)
            Logger.i { "Successfully installed remote plugin: ${newPlugin.pkg} with JAR: ${plugin.fileName}" }

            // Auto-load if enabled
            if (newPlugin.isEnabled) {
                setupPluginInBackground(newPlugin.pkg)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to install remote plugin: ${plugin.pkg}" }
            Result.failure(e)
        }
    }

    suspend fun uninstall(pkg: String): Result<Unit> {
        Logger.i { "Uninstalling plugin: $pkg" }
        val plugin = _installedPlugins.value.find { it.pkg == pkg } ?: run {
            Logger.w { "Plugin $pkg not found for uninstallation" }
            return Result.failure(Exception("Plugin not found"))
        }

        // Safety check for running jobs
        val runningJobs = jobManager.jobs.value.filter { it.pluginId == pkg && it.status == JobStatus.Running }
        if (runningJobs.isNotEmpty()) {
            val settings = settingsRepository.loadSettings()
            if (settings.extensions.pluginUnplugBehavior == PluginUnplugBehavior.Block) {
                Logger.w { "Cannot uninstall plugin $pkg: ${runningJobs.size} jobs are still running" }
                return Result.failure(Exception("Cannot uninstall while ${runningJobs.size} jobs are running. Stop them first or change unplug behavior in settings."))
            } else {
                Logger.i { "Stopping ${runningJobs.size} jobs before uninstalling plugin $pkg" }
                runningJobs.forEach { jobManager.cancelJob(it.id) }
            }
        }

        unloadPlugin(pkg)
        PlatformUtils.deleteDirectory(plugin.installPath)
        val updated = _installedPlugins.value.filter { it.pkg != pkg }
        _installedPlugins.value = updated
        saveToSettings(updated)
        Logger.i { "Successfully uninstalled plugin: $pkg" }
        return Result.success(Unit)
    }

    fun loadPlugin(pkg: String): Result<Unit> {
        val plugin =
            _installedPlugins.value.find { it.pkg == pkg } ?: return Result.failure(Exception("Plugin not found"))
        if (!plugin.isEnabled) return Result.success(Unit)

        // Find the JAR in the folder
        val jarFileName = plugin.jarFileName ?: (plugin.pkg.substringAfterLast(".") + ".jar")
        val jarFile = plugin.installPath + "/" + jarFileName

        Logger.d { "Loading plugin JAR: $jarFile" }
        val result = PluginLoader.loadPlugin(jarFile)
        return if (result.isSuccess) {
            _loadedPlugins.value = _loadedPlugins.value + pkg
            Logger.i { "Successfully loaded plugin: $pkg" }
            Result.success(Unit)
        } else {
            val error = result.exceptionOrNull() ?: Exception("Unknown error")
            Logger.e(error) { "Failed to load plugin: $pkg" }
            Result.failure(error)
        }
    }

    fun unloadPlugin(pkg: String) {
        Logger.d { "Unloading plugin: $pkg" }
        val plugin = _installedPlugins.value.find { it.pkg == pkg } ?: return
        val jarFile = plugin.installPath + "/" + (plugin.pkg.substringAfterLast(".") + ".jar")
        PluginLoader.unloadPlugin(jarFile)
        _loadedPlugins.value = _loadedPlugins.value - pkg
    }

    fun reloadPlugin(pkg: String) {
        unloadPlugin(pkg)
        loadPlugin(pkg)
    }

    fun reloadAll() {
        _installedPlugins.value.forEach {
            reloadPlugin(it.pkg)
        }
    }

    fun refreshInstalledPlugins() {
        val folders = settingsRepository.loadSettings().extensions.pluginFolders
        val currentPlugins = _installedPlugins.value.associateBy { it.pkg }.toMutableMap()
        var changed = false

        folders.forEach { folderPath ->
            PlatformUtils.listDirectories(folderPath).forEach { dirPath ->
                val manifestPath = "$dirPath/manifest.json"
                if (PlatformUtils.exists(manifestPath)) {
                    val content = PlatformUtils.readFile(manifestPath)
                    if (content != null) {
                        try {
                            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                            val manifest: com.wip.plugin.api.PluginManifest = json.decodeFromString(content)
                            val pkg = manifest.plugin.id
                            if (!currentPlugins.containsKey(pkg)) {
                                val newPlugin = InstalledPlugin(
                                    pkg = pkg,
                                    name = manifest.plugin.name,
                                    version = manifest.plugin.version,
                                    installPath = dirPath
                                )
                                currentPlugins[pkg] = newPlugin
                                changed = true
                            }
                        } catch (e: Exception) {
                            Logger.e(e) { "Failed to parse manifest at: $manifestPath" }
                            // Skip invalid manifest
                        }
                    }
                }
            }
        }

        if (changed) {
            val newList = currentPlugins.values.toList()
            _installedPlugins.value = newList
            saveToSettings(newList)
            newList.filter { it.isEnabled && !_loadedPlugins.value.contains(it.pkg) }.forEach {
                loadPlugin(it.pkg)
            }
        }
    }

    suspend fun setEnabled(pkg: String, enabled: Boolean): Result<Unit> {
        Logger.i { "Setting plugin $pkg enabled: $enabled" }

        if (!enabled) {
            // Safety check for running jobs when disabling
            val runningJobs = jobManager.jobs.value.filter { it.pluginId == pkg && it.status == JobStatus.Running }
            if (runningJobs.isNotEmpty()) {
                val settings = settingsRepository.loadSettings()
                if (settings.extensions.pluginUnplugBehavior == PluginUnplugBehavior.Block) {
                    Logger.w { "Cannot disable plugin $pkg: ${runningJobs.size} jobs are still running" }
                    return Result.failure(Exception("Cannot disable while ${runningJobs.size} jobs are running. Stop them first or change unplug behavior in settings."))
                } else {
                    Logger.i { "Stopping ${runningJobs.size} jobs before disabling plugin $pkg" }
                    runningJobs.forEach { jobManager.cancelJob(it.id) }
                }
            }
        }

        val updated = _installedPlugins.value.map {
            if (it.pkg == pkg) it.copy(isEnabled = enabled) else it
        }
        _installedPlugins.value = updated
        saveToSettings(updated)

        if (enabled) loadPlugin(pkg) else unloadPlugin(pkg)
        return Result.success(Unit)
    }

    private fun addInstalledPlugin(plugin: InstalledPlugin) {
        val updated = _installedPlugins.value.filter { it.pkg != plugin.pkg } + plugin
        _installedPlugins.value = updated
        saveToSettings(updated)
    }

    private fun saveToSettings(plugins: List<InstalledPlugin>) {
        val settings = settingsRepository.loadSettings()
        settingsRepository.saveSettings(
            settings.copy(
                extensions = settings.extensions.copy(installedPlugins = plugins)
            )
        )
    }

    private fun verifySignature(path: String): Boolean {
        // STUB: Always valid for now
        return true
    }

    fun getUpdate(pkg: String): ExtensionPlugin? {
        // Find in all repos
        return repoManager.plugins.value.values.flatten().find { it.pkg == pkg }
            ?.let { remote ->
                val installed = _installedPlugins.value.find { it.pkg == pkg }
                if (installed != null && isNewer(remote.version, installed.version)) {
                    remote
                } else null
            }
    }

    suspend fun fetchRemoteChangelog(pkg: String): String? {
        val remote = repoManager.plugins.value.values.flatten().find { it.pkg == pkg } ?: return null
        val repoUrl = remote.repoUrl ?: return null
        val pluginsFolder = repoUrl.substringBeforeLast("/") + "/plugins"
        val baseUrl = "$pluginsFolder/${remote.pkg}"
        return repoManager.fetchText("$baseUrl/changelog.txt") ?: repoManager.fetchText("$baseUrl/changelog.txt")
    }

    private fun isNewer(v1: String, v2: String): Boolean {
        // Simple semantic version comparison
        val s1 = v1.split(".").mapNotNull { it.toIntOrNull() }
        val s2 = v2.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until minOf(s1.size, s2.size)) {
            if (s1[i] > s2[i]) return true
            if (s1[i] < s2[i]) return false
        }
        return s1.size > s2.size
    }

    suspend fun updateLocal(pkg: String, newJarPath: String): Result<Unit> {
        val plugin =
            _installedPlugins.value.find { it.pkg == pkg } ?: return Result.failure(Exception("Plugin not found"))
        unloadPlugin(pkg)
        // Install over existing path
        return installLocal(newJarPath, plugin.installPath.substringBeforeLast("/"))
    }

    suspend fun updateRemote(pkg: String): Result<Unit> {
        val update = getUpdate(pkg) ?: return Result.failure(Exception("No update available"))
        val plugin =
            _installedPlugins.value.find { it.pkg == pkg } ?: return Result.failure(Exception("Plugin not found"))
        unloadPlugin(pkg)
        return installRemote(update, plugin.installPath.substringBeforeLast("/"))
    }

    private fun createExecutionContext(pkg: String): com.wip.plugin.api.ExecutionContext {
        val installPath = PluginLoader.getPluginInstallPath(pkg) ?: ""
        val jarFullPath = PluginLoader.getPluginJarPath(pkg)

        return com.wip.plugin.api.ExecutionContext(
            logger = object : com.wip.plugin.api.PluginLogger {
                override fun verbose(message: String) { Logger.v { "[$pkg] $message" } }
                override fun debug(message: String) { Logger.d { "[$pkg] $message" } }
                override fun info(message: String) { Logger.i { "[$pkg] $message" } }
                override fun warn(message: String) { Logger.w { "[$pkg] $message" } }
                override fun error(message: String, throwable: Throwable?) { Logger.e(throwable) { "[$pkg] $message" } }
            },
            progress = object : com.wip.plugin.api.ProgressReporter {
                override fun report(progress: Float) { /* no-op for setup/validate */ }
            },
            fileSystem = DefaultPluginFileSystem(installPath, jarFullPath)
        )
    }

    suspend fun validatePlugin(pkg: String): Result<Unit> {
        val plugin = PluginLoader.getPluginById(pkg) ?: return Result.failure(Exception("Plugin not loaded"))
        return plugin.validate(createExecutionContext(pkg))
    }

    suspend fun performSetup(pkg: String): Result<Unit> {
        val plugin = PluginLoader.getPluginById(pkg) ?: return Result.failure(Exception("Plugin not loaded"))
        return plugin.performSetup(createExecutionContext(pkg))
    }

    private fun setupPluginInBackground(pkg: String) {
        scope.launch {
            val plugin = _installedPlugins.value.find { it.pkg == pkg } ?: return@launch
            val jarFileName = plugin.jarFileName ?: (plugin.pkg.substringAfterLast(".") + ".jar")
            val jarFile = plugin.installPath + "/" + jarFileName

            Logger.d { "Loading plugin JAR for background setup: $jarFile" }
            val result = PluginLoader.loadPlugin(jarFile)
            if (result.isSuccess) {
                performSetup(pkg).onFailure { e ->
                    Logger.e(e) { "Failed to perform setup for plugin: $pkg" }
                }
                _loadedPlugins.value = _loadedPlugins.value + pkg
                Logger.i { "Successfully completed setup and loaded plugin: $pkg" }
            } else {
                val error = result.exceptionOrNull() ?: Exception("Unknown error")
                Logger.e(error) { "Failed to load plugin for setup: $pkg" }
            }
        }
    }
}
