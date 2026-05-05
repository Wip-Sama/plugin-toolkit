package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.repository.logic.RepoManager
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.PluginUnplugBehavior
import org.wip.plugintoolkit.api.ExecutionContext
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.api.ProgressReporter
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
        
        // Observe jobs to mark plugins as validated
        scope.launch {
            jobManager.jobs.collect { jobs ->
                jobs.forEach { job ->
                    if (job.status == JobStatus.Completed) {
                        if (job.type == JobType.Validation || job.type == JobType.Setup) {
                            markAsValidated(job.pluginId)
                        }
                    }
                }
            }
        }
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
                    json.decodeFromString<PluginManifest>(content)
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
                description = description,
                isValidated = false
            )

            addInstalledPlugin(newPlugin)
            Logger.i { "Successfully installed local plugin: ${newPlugin.pkg} with JAR: $jarFileName" }

            // Auto-setup if enabled
            if (newPlugin.isEnabled) {
                enqueueSetupJob(newPlugin.pkg)
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

            // Auto-setup if enabled
            if (newPlugin.isEnabled) {
                enqueueSetupJob(newPlugin.pkg)
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

    suspend fun loadPlugin(pkg: String): Result<Unit> {
        val plugin =
            _installedPlugins.value.find { it.pkg == pkg } ?: return Result.failure(Exception("Plugin not found"))
        if (!plugin.isEnabled) return Result.success(Unit)

        // Find the JAR in the folder
        val jarFileName = plugin.jarFileName ?: (plugin.pkg.substringAfterLast(".") + ".jar")
        val jarFile = plugin.installPath + "/" + jarFileName

        Logger.d { "Loading plugin JAR: $jarFile" }
        val result = PluginLoader.loadPlugin(jarFile)
        return if (result.isSuccess) {
            val entry = result.getOrThrow()
            
            // Initialize the plugin with its context
            val initResult = entry.initialize(createExecutionContext(pkg))
            if (initResult.isFailure) {
                val error = initResult.exceptionOrNull() ?: Exception("Initialization failed")
                Logger.e(error) { "Failed to initialize plugin: $pkg" }
                return Result.failure(error)
            }

            if (plugin.isValidated) {
                _loadedPlugins.value += pkg
                Logger.i { "Successfully loaded and activated plugin: $pkg" }
            } else {
                Logger.i { "Plugin $pkg loaded in loader but not yet validated/activated" }
            }
            Result.success(Unit)
        } else {
            val error = result.exceptionOrNull() ?: Exception("Unknown error")
            Logger.e(error) { "Failed to load plugin: $pkg" }
            Result.failure(error)
        }
    }

    fun unloadPlugin(pkg: String) {
        Logger.d { "Unloading plugin: $pkg" }
        val plugin = _installedPlugins.value.find { it.pkg == pkg } ?: run {
            Logger.w { "Cannot unload plugin $pkg: not found in installed list" }
            return
        }
        val jarFileName = plugin.jarFileName ?: (plugin.pkg.substringAfterLast(".") + ".jar")
        val jarFile = plugin.installPath + "/" + jarFileName
        Logger.d { "Requesting PluginLoader to unload JAR: $jarFile" }
        PluginLoader.unloadPlugin(jarFile)
        _loadedPlugins.value -= pkg
    }

    fun reloadPlugin(pkg: String) {
        scope.launch {
            unloadPlugin(pkg)
            loadPlugin(pkg)
        }
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
                            val manifest: PluginManifest = json.decodeFromString(content)
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
            newList.filter { it.isEnabled && it.isValidated && !_loadedPlugins.value.contains(it.pkg) }.forEach {
                scope.launch { loadPlugin(it.pkg) }
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

        if (enabled) {
            val plugin = updated.find { it.pkg == pkg }
            if (plugin != null) {
                if (plugin.isValidated) loadPlugin(pkg) else enqueueSetupJob(pkg)
            }
        } else {
            unloadPlugin(pkg)
        }
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
        //TODO: Always valid for now
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

    fun createExecutionContext(pkg: String, jobId: String? = null): ExecutionContext {
        val plugin = _installedPlugins.value.find { it.pkg == pkg }
        val installPath = plugin?.installPath ?: ""
        val jarFullPath = plugin?.let { "${it.installPath}/${it.jarFileName}" }

        return ExecutionContext(
            logger = jobManager.getPluginLogger(pkg, jobId),
            progress = object : ProgressReporter {
                override fun report(progress: Float) {
                    if (jobId != null) jobManager.updateJobProgress(jobId, progress)
                }
            },
            fileSystem = DefaultPluginFileSystem(installPath, jarFullPath),
            cacheFileSystem = DefaultPluginFileSystem.createCacheOnly(installPath)
        )
    }

    suspend fun validatePluginInJob(pkg: String): Result<Unit> {
        val plugin = _installedPlugins.value.find { it.pkg == pkg } ?: return Result.failure(Exception("Plugin not found"))
        
        // Ensure it's loaded in PluginLoader
        val loadResult = loadPlugin(pkg)
        if (loadResult.isFailure) return Result.failure(loadResult.exceptionOrNull()!!)
        
        val job = BackgroundJob(
            id = "val_$pkg",
            name = "Validation: ${plugin.name}",
            type = JobType.Validation,
            pluginId = pkg,
            capabilityName = "validate",
            keepResult = false
        )
        jobManager.enqueueJob(job)
        return Result.success(Unit)
    }

    suspend fun validatePlugin(pkg: String): Result<Unit> {
        //TODO: This is the direct call, but we should prefer validatePluginInJob for UI visibility
        val plugin = PluginLoader.getPluginById(pkg) ?: return Result.failure(Exception("Plugin not loaded"))
        return plugin.validate(createExecutionContext(pkg))
    }

    suspend fun enqueueSetupJob(pkg: String) {
        val plugin = _installedPlugins.value.find { it.pkg == pkg } ?: return
        
        // Ensure it's loaded in PluginLoader
        val loadResult = loadPlugin(pkg)
        if (loadResult.isFailure) {
            Logger.e { "Failed to load plugin $pkg for setup: ${loadResult.exceptionOrNull()?.message}" }
            return
        }
        
        val job = BackgroundJob(
            id = "setup_$pkg",
            name = "Setup: ${plugin.name}",
            type = JobType.Setup,
            pluginId = pkg,
            capabilityName = "setup",
            keepResult = false
        )
        jobManager.enqueueJob(job)
    }

    private fun markAsValidated(pkg: String) {
        val plugin = _installedPlugins.value.find { it.pkg == pkg } ?: return
        if (plugin.isValidated) return

        Logger.i { "Marking plugin $pkg as validated and activating" }
        val updated = _installedPlugins.value.map {
            if (it.pkg == pkg) it.copy(isValidated = true) else it
        }
        _installedPlugins.value = updated
        saveToSettings(updated)
        
        // Now that it's validated, load it (which will add it to _loadedPlugins)
        scope.launch { loadPlugin(pkg) }
    }
}
