package com.wip.kpm_cpm_wotoolkit.features.plugin.logic

import com.wip.kpm_cpm_wotoolkit.core.utils.PlatformUtils
import com.wip.kpm_cpm_wotoolkit.features.plugin.model.*
import com.wip.kpm_cpm_wotoolkit.features.repository.logic.RepoManager
import com.wip.kpm_cpm_wotoolkit.features.repository.model.ExtensionModule
import com.wip.kpm_cpm_wotoolkit.features.settings.logic.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import co.touchlab.kermit.Logger

class ModuleManager(
    private val settingsRepository: SettingsRepository,
    private val repoManager: RepoManager
) {
    private val _installedModules = MutableStateFlow<List<InstalledModule>>(emptyList())
    val installedModules: StateFlow<List<InstalledModule>> = _installedModules.asStateFlow()

    private val _loadedModules = MutableStateFlow<Set<String>>(emptySet()) // set of pkg
    val loadedModules: StateFlow<Set<String>> = _loadedModules.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        val settings = settingsRepository.loadSettings()
        _installedModules.value = settings.extensions.installedModules
        Logger.i { "Initializing ModuleManager with ${_installedModules.value.size} installed modules" }
        // Load modules will happen in main.kt
    }

    suspend fun installLocal(filePath: String, targetFolderPath: String): Result<Unit> {
        Logger.i { "Installing local module from: $filePath" }
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
            
            val pkg = manifest?.module?.id ?: filePath.replace('\\', '/').substringAfterLast("/").substringBeforeLast(".")
            val name = manifest?.module?.name ?: pkg
            val version = manifest?.module?.version ?: "1.0.0"
            val description = manifest?.module?.description

            Logger.i { "Determined module ID: $pkg, Name: $name, Version: $version" }
            val moduleDir = "$targetFolderPath/$pkg"
            PlatformUtils.mkdirs(moduleDir)

            val jarFileName = filePath.replace('\\', '/').substringAfterLast("/")
            val dest = "$moduleDir/$jarFileName"
            PlatformUtils.copyFile(filePath, dest)
            
            Logger.d { "Copied local JAR to: $dest" }
            val newModule = InstalledModule(
                pkg = pkg,
                name = name,
                version = version,
                installPath = moduleDir,
                jarFileName = jarFileName,
                description = description
            )

            addInstalledModule(newModule)
            Logger.i { "Successfully installed local module: ${newModule.pkg} with JAR: $jarFileName" }
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to install local module: $filePath" }
            Result.failure(e)
        }
    }

    suspend fun installRemote(module: ExtensionModule, targetFolderPath: String): Result<Unit> {
        Logger.i { "Installing remote module: ${module.pkg} from ${module.repoUrl}" }
        return try {
            PlatformUtils.mkdirs(targetFolderPath)
            val moduleDir = "$targetFolderPath/${module.pkg}"
            PlatformUtils.mkdirs(moduleDir)

            // Base URL for the module folder
            val repoUrl = module.repoUrl ?: return Result.failure(Exception("Missing repo URL"))
            val modulesFolder = repoUrl.substringBeforeLast("/") + "/modules"
            val baseUrl = "$modulesFolder/${module.pkg}"

            // 1. Download Module File
            val moduleFileUrl = "$baseUrl/${module.fileName}"
            val destFile = "$moduleDir/${module.fileName}"
            
            PlatformUtils.downloadFile(moduleFileUrl, destFile).onSuccess {
                if (!verifySignature(destFile)) {
                    return Result.failure(Exception("Invalid signature for ${module.fileName}"))
                }

                if (module.fileName.endsWith(".zip")) {
                    PlatformUtils.unzip(destFile, moduleDir, 100 * 1024 * 1024).onFailure { return Result.failure(it) }
                }
            }.onFailure { return Result.failure(it) }

            // 2. Download Icon (optional)
            // Try common extensions
            listOf("icon.png", "icon.webp", "icon.svg", "icon.jpg").forEach { iconName ->
                PlatformUtils.downloadFile("$baseUrl/$iconName", "$moduleDir/$iconName")
            }

            // 3. Download Changelog (optional)
            PlatformUtils.downloadFile("$baseUrl/changelog.chlog", "$moduleDir/changelog.chlog")

            val newModule = InstalledModule(
                pkg = module.pkg,
                name = module.name,
                version = module.version,
                installPath = moduleDir,
                repoUrl = repoUrl,
                jarFileName = module.fileName,
                description = module.description
            )
            addInstalledModule(newModule)
            Logger.i { "Successfully installed remote module: ${newModule.pkg} with JAR: ${module.fileName}" }
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to install remote module: ${module.pkg}" }
            Result.failure(e)
        }
    }

    fun uninstall(pkg: String) {
        Logger.i { "Uninstalling module: $pkg" }
        val module = _installedModules.value.find { it.pkg == pkg } ?: run {
            Logger.w { "Module $pkg not found for uninstallation" }
            return
        }
        unloadModule(pkg)
        PlatformUtils.deleteDirectory(module.installPath)
        val updated = _installedModules.value.filter { it.pkg != pkg }
        _installedModules.value = updated
        saveToSettings(updated)
        Logger.i { "Successfully uninstalled module: $pkg" }
    }

    fun loadModule(pkg: String): Result<Unit> {
        val module = _installedModules.value.find { it.pkg == pkg } ?: return Result.failure(Exception("Module not found"))
        if (!module.isEnabled) return Result.success(Unit)

        // Find the JAR in the folder
        val jarFileName = module.jarFileName ?: (module.pkg.substringAfterLast(".") + ".jar")
        val jarFile = module.installPath + "/" + jarFileName
        
        Logger.d { "Loading module JAR: $jarFile" }
        val result = ModuleLoader.loadPlugin(jarFile)
        return if (result.isSuccess) {
            _loadedModules.value = _loadedModules.value + pkg
            Logger.i { "Successfully loaded module: $pkg" }
            Result.success(Unit)
        } else {
            val error = result.exceptionOrNull() ?: Exception("Unknown error")
            Logger.e(error) { "Failed to load module: $pkg" }
            Result.failure(error)
        }
    }

    fun unloadModule(pkg: String) {
        Logger.d { "Unloading module: $pkg" }
        val module = _installedModules.value.find { it.pkg == pkg } ?: return
        val jarFile = module.installPath + "/" + (module.pkg.substringAfterLast(".") + ".jar")
        ModuleLoader.unloadPlugin(jarFile)
        _loadedModules.value = _loadedModules.value - pkg
    }

    fun reloadModule(pkg: String) {
        unloadModule(pkg)
        loadModule(pkg)
    }

    fun reloadAll() {
        _installedModules.value.forEach { 
            reloadModule(it.pkg)
        }
    }

    fun refreshInstalledModules() {
        val folders = settingsRepository.loadSettings().extensions.moduleFolders
        val currentModules = _installedModules.value.associateBy { it.pkg }.toMutableMap()
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
                            val pkg = manifest.module.id
                            if (!currentModules.containsKey(pkg)) {
                                val newModule = InstalledModule(
                                    pkg = pkg,
                                    name = manifest.module.name,
                                    version = manifest.module.version,
                                    installPath = dirPath
                                )
                                currentModules[pkg] = newModule
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
            val newList = currentModules.values.toList()
            _installedModules.value = newList
            saveToSettings(newList)
        }
    }

    fun setEnabled(pkg: String, enabled: Boolean) {
        Logger.i { "Setting module $pkg enabled: $enabled" }
        val updated = _installedModules.value.map {
            if (it.pkg == pkg) it.copy(isEnabled = enabled) else it
        }
        _installedModules.value = updated
        saveToSettings(updated)
        
        if (enabled) loadModule(pkg) else unloadModule(pkg)
    }

    private fun addInstalledModule(module: InstalledModule) {
        val updated = _installedModules.value.filter { it.pkg != module.pkg } + module
        _installedModules.value = updated
        saveToSettings(updated)
    }

    private fun saveToSettings(modules: List<InstalledModule>) {
        val settings = settingsRepository.loadSettings()
        settingsRepository.saveSettings(settings.copy(
            extensions = settings.extensions.copy(installedModules = modules)
        ))
    }

    private fun verifySignature(path: String): Boolean {
        // STUB: Always valid for now
        return true
    }

    fun getUpdate(pkg: String): ExtensionModule? {
        // Find in all repos
        return repoManager.modules.value.values.flatten().find { it.pkg == pkg }
            ?.let { remote ->
                val installed = _installedModules.value.find { it.pkg == pkg }
                if (installed != null && isNewer(remote.version, installed.version)) {
                    remote
                } else null
            }
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
}
