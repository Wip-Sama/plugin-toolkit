package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginSignalManager
import org.wip.plugintoolkit.api.PluginAction
import org.wip.plugintoolkit.api.PluginSignal
import org.wip.plugintoolkit.api.ProgressReporter
import org.wip.plugintoolkit.api.PluginLogger
import org.wip.plugintoolkit.api.ExecutionContext
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.core.KeepTrack
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.plugin.model.PluginSettingsStore
import org.wip.plugintoolkit.features.repository.logic.RepoManager
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.PluginUnplugBehavior
import kotlin.time.Clock

class PluginManager(
    private val settingsRepository: SettingsRepository,
    private val repoManager: RepoManager,
    private val jobManager: JobManager
) {
    private val _installedPlugins = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val installedPlugins: StateFlow<List<InstalledPlugin>> = _installedPlugins.asStateFlow()

    private val json = kotlinx.serialization.json.Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val defaultPluginFolder = settingsRepository.getSettingsDir() + "/" + KeepTrack.PLUGINS_DIR_NAME

    private val _loadedPlugins = MutableStateFlow<Set<String>>(emptySet()) // set of pkg
    val loadedPlugins: StateFlow<Set<String>> = _loadedPlugins.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        PlatformUtils.mkdirs(defaultPluginFolder)
        loadFromManagedFolders()
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
        } catch (t: Throwable) {
            Logger.e(t) { "Failed to install local plugin: $filePath" }
            Result.failure(Exception(t.message, t))
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
        } catch (t: Throwable) {
            Logger.e(t) { "Failed to install remote plugin: ${plugin.pkg}" }
            Result.failure(Exception(t.message, t))
        }
    }

    suspend fun uninstall(pkg: String): Result<Unit> {
        Logger.i { "Uninstalling plugin: $pkg" }
        val plugin = _installedPlugins.value.find { it.pkg == pkg } ?: run {
            Logger.w { "Plugin $pkg not found for uninstallation" }
            return Result.failure(Exception("Plugin not found"))
        }

        // Safety check for running jobs
        ensureSafeToUnload(listOf(pkg)).onFailure { return Result.failure(it) }

        unloadPlugin(pkg)
        PlatformUtils.deleteDirectory(plugin.installPath)
        val updated = _installedPlugins.value.filter { it.pkg != pkg }
        _installedPlugins.value = updated
        saveToManagedFolders(updated)
        Logger.i { "Successfully uninstalled plugin: $pkg" }
        return Result.success(Unit)
    }

    private suspend fun ensureSafeToUnload(pkgs: List<String>): Result<Unit> {
        val runningJobs = jobManager.jobs.value.filter { it.pluginId in pkgs && it.status == JobStatus.Running }
        if (runningJobs.isNotEmpty()) {
            val settings = settingsRepository.loadSettings()
            if (settings.extensions.pluginUnplugBehavior == PluginUnplugBehavior.Block) {
                val msg =
                    "Cannot proceed: ${runningJobs.size} jobs are still running for plugins: ${pkgs.joinToString()}"
                Logger.w { msg }
                return Result.failure(Exception(msg))
            } else {
                Logger.i { "Stopping ${runningJobs.size} jobs before unloading plugins: ${pkgs.joinToString()}" }
                runningJobs.forEach { jobManager.cancelJob(it.id) }
            }
        }
        return Result.success(Unit)
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').removeSuffix("/")
    }

    private fun getManagedFolders(): List<String> {
        val savedFolders = settingsRepository.loadSettings().extensions.pluginFolders
        return (listOf(defaultPluginFolder) + savedFolders).distinct()
    }

    private fun loadFromManagedFolders() {
        val allPlugins = mutableListOf<InstalledPlugin>()
        getManagedFolders().forEach { folderPath ->
            val file = "${normalizePath(folderPath)}/${KeepTrack.INSTALLED_PLUGINS_FILE_NAME}"
            val content = PlatformUtils.readFile(file)
            if (content != null) {
                try {
                    val folderPlugins = json.decodeFromString<List<InstalledPlugin>>(content)
                    allPlugins.addAll(folderPlugins)
                } catch (t: Throwable) {
                    Logger.e(t) { "Failed to parse $file" }
                }
            }
        }
        _installedPlugins.update { allPlugins }
    }

    private fun saveToManagedFolders(plugins: List<InstalledPlugin>) {
        val folders = getManagedFolders()
        folders.forEach { folderPath ->
            val normalizedFolder = normalizePath(folderPath)
            val folderPlugins = plugins.filter { normalizePath(it.installPath).startsWith(normalizedFolder) }
            val file = "$normalizedFolder/${KeepTrack.INSTALLED_PLUGINS_FILE_NAME}"

            PlatformUtils.writeFile(file, json.encodeToString(folderPlugins))
        }
    }

    suspend fun removeManagedFolder(folderPath: String): Result<Unit> {
        if (normalizePath(folderPath) == normalizePath(defaultPluginFolder))
            return Result.failure(Exception("Cannot remove default plugins folder"))

        val normalizedFolder = normalizePath(folderPath)
        val pluginsInFolder =
            _installedPlugins.value.filter { normalizePath(it.installPath).startsWith(normalizedFolder) }
        val pkgs = pluginsInFolder.map { it.pkg }

        ensureSafeToUnload(pkgs).onFailure { return Result.failure(it) }

        // Unload all plugins in that folder
        pkgs.forEach { unloadPlugin(it) }

        // Remove folder from settings
        val settings = settingsRepository.loadSettings()
        val updatedFolders = settings.extensions.pluginFolders.filter { normalizePath(it) != normalizedFolder }
        settingsRepository.saveSettings(
            settings.copy(
                extensions = settings.extensions.copy(pluginFolders = updatedFolders)
            )
        )

        // Update memory state
        _installedPlugins.update { current -> current.filter { !pkgs.contains(it.pkg) } }

        Logger.i { "Removed managed folder: $folderPath. ${pkgs.size} plugins unmanaged." }
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

            try {
                // Ensure the manifest can be read before proceeding
                entry.getManifest()
                
                // Initialize the plugin with its context
                val initResult = entry.initialize(createExecutionContext(pkg))
                if (initResult.isFailure) {
                    val error = initResult.exceptionOrNull() ?: Exception("Initialization failed")
                    Logger.e(error) { "Failed to initialize plugin: $pkg" }
                    updateLoadError(pkg, error.message ?: "Initialization failed")
                    return Result.failure(error)
                }

                if (plugin.isValidated) {
                    _loadedPlugins.update { it + pkg }
                    Logger.i { "Successfully loaded and activated plugin: $pkg (Total loaded: ${_loadedPlugins.value.size})" }
                    updateLoadError(pkg, null) // Clear error on success
                } else {
                    Logger.i { "Plugin $pkg loaded in loader but not yet validated/activated" }
                }
                Result.success(Unit)
            } catch (t: Throwable) {
                val errorMsg = "Fatal error during plugin initialization: ${t.message}"
                Logger.e(t) { errorMsg }
                updateLoadError(pkg, errorMsg)
                Result.failure(Exception(errorMsg, t))
            }
        } else {
            val error = result.exceptionOrNull() ?: Exception("Unknown error")
            val errorMsg = "Failed to load plugin: $pkg. ${error.message}"
            Logger.e(error) { errorMsg }
            updateLoadError(pkg, errorMsg)
            Result.failure(error)
        }
    }

    private fun updateLoadError(pkg: String, error: String?) {
        _installedPlugins.update { current ->
            current.map {
                if (it.pkg == pkg) {
                    it.copy(
                        loadError = error,
                        // If there's a fatal load error, the plugin is no longer considered validated
                        isValidated = if (error != null) false else it.isValidated
                    )
                } else it
            }
        }
        saveToManagedFolders(_installedPlugins.value)
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
        _loadedPlugins.update { it - pkg }
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
        val folders = getManagedFolders()
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
            }

            allUpdatedPlugins.addAll(existingPlugins)
        }

        if (allUpdatedPlugins.size != _installedPlugins.value.size) {
            _installedPlugins.update { allUpdatedPlugins }
            allUpdatedPlugins.filter { it.isEnabled && it.isValidated && !_loadedPlugins.value.contains(it.pkg) }
                .forEach {
                    scope.launch { loadPlugin(it.pkg) }
                }
        }
    }

    fun rescanManagedFolders() {
        val folders = getManagedFolders()
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

                // 1. Look for JAR files in the directory
                val jarFiles = PlatformUtils.listFiles(normalizedDir).filter { it.endsWith(".jar") }

                jarFiles.forEach { jarPath ->
                    val normalizedJarPath = normalizePath(jarPath)
                    val jarFileName = normalizedJarPath.substringAfterLast("/")

                    // 2. Try to extract manifest from JAR
                    var manifestContent = PlatformUtils.readFileFromZip(normalizedJarPath, "manifest.json")
                    if (manifestContent == null) {
                        manifestContent = PlatformUtils.readFileFromZip(normalizedJarPath, "META-INF/manifest.json")
                    }

                    if (manifestContent != null) {
                        try {
                            val manifest = json.decodeFromString<PluginManifest>(manifestContent)
                            val pkg = manifest.plugin.id

                            // 3. Check if parent folder name corresponds to plugin id
                            if (folderName == pkg) {
                                Logger.i { "Found valid plugin $pkg in $normalizedDir" }

                                val existing = existingPlugins[pkg]
                                val updatedPlugin = if (existing != null) {
                                    // Update metadata but preserve state
                                    existing.copy(
                                        name = manifest.plugin.name,
                                        version = manifest.plugin.version,
                                        installPath = normalizedDir,
                                        jarFileName = jarFileName,
                                        description = manifest.plugin.description
                                    )
                                } else {
                                    folderChanged = true
                                    InstalledPlugin(
                                        pkg = pkg,
                                        name = manifest.plugin.name,
                                        version = manifest.plugin.version,
                                        installPath = normalizedDir,
                                        jarFileName = jarFileName,
                                        description = manifest.plugin.description
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

            // Check if any plugins were removed from disk
            if (folderPlugins.size != existingPlugins.size) {
                folderChanged = true
            }

            // Always write if changed OR if the file is missing
            if (folderChanged || !PlatformUtils.exists(trackingFile)) {
                Logger.i { "Updating tracking file: $trackingFile with ${folderPlugins.size} plugins found during rescan" }
                PlatformUtils.writeFile(trackingFile, json.encodeToString(folderPlugins))
                globalChanged = true
            }
            allUpdatedPlugins.addAll(folderPlugins)
        }

        if (globalChanged || allUpdatedPlugins.size != _installedPlugins.value.size) {
            _installedPlugins.update { allUpdatedPlugins }
            allUpdatedPlugins.filter { it.isEnabled && it.isValidated && !_loadedPlugins.value.contains(it.pkg) }
                .forEach {
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
        _installedPlugins.update { updated }
        saveToManagedFolders(updated)

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
        _installedPlugins.update { current ->
            current.filter { it.pkg != plugin.pkg } + plugin
        }
        saveToManagedFolders(_installedPlugins.value)
    }

    // Removed saveToSettings(plugins: List<InstalledPlugin>) as it is replaced by saveToManagedFolders

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

        ensureSafeToUnload(listOf(pkg)).onFailure { return Result.failure(it) }

        unloadPlugin(pkg)
        // Install over existing path
        return installLocal(newJarPath, plugin.installPath.substringBeforeLast("/"))
    }

    suspend fun updateRemote(pkg: String): Result<Unit> {
        val update = getUpdate(pkg) ?: return Result.failure(Exception("No update available"))
        val plugin =
            _installedPlugins.value.find { it.pkg == pkg } ?: return Result.failure(Exception("Plugin not found"))

        ensureSafeToUnload(listOf(pkg)).onFailure { return Result.failure(it) }

        unloadPlugin(pkg)
        return installRemote(update, plugin.installPath.substringBeforeLast("/"))
    }

    private fun getSettingsFile(installPath: String): String {
        return "$installPath/settings.json"
    }

    fun loadPluginSettings(pkg: String): PluginSettingsStore {
        val plugin = _installedPlugins.value.find { it.pkg == pkg } ?: return PluginSettingsStore()
        val settingsFile = getSettingsFile(plugin.installPath)
        val content = PlatformUtils.readFile(settingsFile)
        return if (content != null) {
            try {
                json.decodeFromString<PluginSettingsStore>(content)
            } catch (e: Exception) {
                Logger.e(e) { "Failed to parse settings for $pkg" }
                PluginSettingsStore()
            }
        } else {
            PluginSettingsStore()
        }
    }

    fun savePluginSettings(pkg: String, store: PluginSettingsStore) {
        val plugin = _installedPlugins.value.find { it.pkg == pkg } ?: return
        val settingsFile = getSettingsFile(plugin.installPath)
        try {
            PlatformUtils.writeFile(settingsFile, json.encodeToString(store))
        } catch (t: Throwable) {
            Logger.e(t) { "Failed to save settings for $pkg" }
        }
    }

    fun createPluginContext(pkg: String, jobId: String? = null): PluginContext {
        val plugin = _installedPlugins.value.find { it.pkg == pkg }
        val installPath = plugin?.installPath ?: ""
        val jarFullPath = plugin?.let { "${it.installPath}/${it.jarFileName}" }

        // Load settings and merge with manifest defaults
        val storedSettings = loadPluginSettings(pkg)
        
        val manifest = PluginLoader.getPluginById(pkg)?.getManifest()
        
        val mergedSettings = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
        
        // 1. Start with manifest defaults
        manifest?.settings?.forEach { (key, meta) ->
            meta.defaultValue?.let { mergedSettings[key] = it }
        }
        
        // 2. Override with stored user settings
        mergedSettings.putAll(storedSettings.settings)

        val pluginLogger = jobManager.getPluginLogger(pkg, jobId)
        val progressReporter = object : ProgressReporter {
            override fun report(progress: Float) {
                if (jobId != null) jobManager.updateJobProgress(jobId, progress)
            }
        }

        return DefaultPluginContext(
            logger = pluginLogger,
            progress = progressReporter,
            fileSystem = DefaultPluginFileSystem(installPath, jarFullPath),
            cacheFileSystem = DefaultPluginFileSystem.createCacheOnly(installPath),
            settings = mergedSettings
        )
    }

    // Compatibility method
    fun createExecutionContext(pkg: String, jobId: String? = null): ExecutionContext {
        val context = createPluginContext(pkg, jobId)
        return object : ExecutionContext, PluginContext by context {
            override val signals: PluginSignalManager = context.signals
        }
    }

    suspend fun validatePluginInJob(pkg: String): Result<Unit> {
        val plugin =
            _installedPlugins.value.find { it.pkg == pkg } ?: return Result.failure(Exception("Plugin not found"))

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
        return plugin.validate(createPluginContext(pkg))
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

    suspend fun runAction(pkg: String, action: PluginAction) {
        val plugin = _installedPlugins.value.find { it.pkg == pkg } ?: return
        
        Logger.i { "Enqueuing custom action: ${action.name} (function: ${action.functionName}) for plugin: $pkg" }

        // Ensure it's loaded in PluginLoader
        val loadResult = loadPlugin(pkg)
        if (loadResult.isFailure) {
            Logger.e { "Failed to load plugin $pkg for action ${action.name}: ${loadResult.exceptionOrNull()?.message}" }
            return
        }

        val job = BackgroundJob(
            id = "action_${pkg}_${action.functionName}_${Clock.System.now().toEpochMilliseconds()}",
            name = "Action: ${action.name} (${plugin.name})",
            type = JobType.PluginAction,
            pluginId = pkg,
            capabilityName = action.functionName,
            keepResult = false
        )
        jobManager.enqueueJob(job)
    }

    private fun markAsValidated(pkg: String) {
        val plugin = _installedPlugins.value.find { it.pkg == pkg } ?: return
        if (plugin.isValidated) return

        Logger.i { "Marking plugin $pkg as validated and activating" }
        _installedPlugins.update { current ->
            current.map { if (it.pkg == pkg) it.copy(isValidated = true) else it }
        }
        saveToManagedFolders(_installedPlugins.value)

        // Now that it's validated, load it (which will add it to _loadedPlugins)
        scope.launch { loadPlugin(pkg) }
    }
}

class DefaultPluginSignalManager : PluginSignalManager {
    private val signalHandlers = mutableListOf<suspend (PluginSignal) -> Unit>()

    override fun onSignal(handler: suspend (PluginSignal) -> Unit) {
        signalHandlers.add(handler)
    }

    override suspend fun sendSignal(signal: PluginSignal) {
        signalHandlers.forEach { it(signal) }
    }
}

class DefaultPluginContext(
    override val logger: PluginLogger,
    override val progress: ProgressReporter,
    override val fileSystem: org.wip.plugintoolkit.api.PluginFileSystem,
    override val cacheFileSystem: org.wip.plugintoolkit.api.PluginFileSystem,
    override val settings: Map<String, kotlinx.serialization.json.JsonElement>,
    override val signals: PluginSignalManager = DefaultPluginSignalManager()
) : PluginContext
