package org.wip.plugintoolkit.features.plugin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.getString
import org.wip.plugintoolkit.core.KeepTrack
import org.wip.plugintoolkit.core.ui.DialogService
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.repository.logic.RepoManager
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.plugin_action_blocked
import plugintoolkit.composeapp.generated.resources.plugin_changelog
import plugintoolkit.composeapp.generated.resources.plugin_changelog_not_found_jar
import plugintoolkit.composeapp.generated.resources.plugin_changelog_not_found_remote
import plugintoolkit.composeapp.generated.resources.plugin_choose_install_location
import plugintoolkit.composeapp.generated.resources.plugin_state_change_error
import plugintoolkit.composeapp.generated.resources.plugin_uninstall_blocked
import plugintoolkit.composeapp.generated.resources.plugin_uninstall_confirmation
import plugintoolkit.composeapp.generated.resources.plugin_uninstall_error
import plugintoolkit.composeapp.generated.resources.plugin_uninstall_title
import plugintoolkit.composeapp.generated.resources.plugin_validated_success
import plugintoolkit.composeapp.generated.resources.plugin_validation_failed
import plugintoolkit.composeapp.generated.resources.plugin_validation_result

class PluginManagerViewModel(
    private val pluginManager: PluginManager,
    private val dialogService: DialogService,
    private val settingsRepository: SettingsRepository,
    private val repoManager: RepoManager,
    private val jobManager: JobManager
) : ViewModel() {

    val installedPlugins = pluginManager.installedPlugins
    val loadedPlugins = pluginManager.loadedPlugins

    val defaultPluginFolder = settingsRepository.getSettingsDir() + "/" + KeepTrack.PLUGINS_DIR_NAME

    val managedFolders: StateFlow<List<String>> = settingsRepository.settings
        .map { settings ->
            (listOf(defaultPluginFolder) + settings.extensions.pluginFolders).distinct()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(defaultPluginFolder))

    private val _settingsPkg = MutableStateFlow<String?>(null)
    val settingsPkg: StateFlow<String?> = _settingsPkg.asStateFlow()

    private val _showRemoteInstall = MutableStateFlow(false)
    val showRemoteInstall: StateFlow<Boolean> = _showRemoteInstall.asStateFlow()

    val availableRemotePlugins: StateFlow<List<ExtensionPlugin>> = repoManager.plugins
        .map { it.values.flatten() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activePluginInstallationJobs: StateFlow<Map<String, Float>> = jobManager.jobProgress
        .combine(jobManager.jobs) { progressMap, jobs ->
            jobs.filter { it.type == JobType.PluginInstallation && it.status == JobStatus.Running }
                .associate { it.pluginId to (progressMap[it.id] ?: 0f) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    
    private val autoOpenedSettings = mutableSetOf<String>()

    init {
        PlatformUtils.mkdirs(defaultPluginFolder)

        viewModelScope.launch {
            installedPlugins.collect { plugins ->
                plugins.forEach { plugin ->
                    if (plugin.requiredAction == "CONFIGURE_SETTINGS" && !autoOpenedSettings.contains(plugin.pkg)) {
                        autoOpenedSettings.add(plugin.pkg)
                        dialogService.showConfirmation(
                            title = "Configuration Required",
                            message = "Plugin ${plugin.name} requires configuration. Would you like to configure it now?",
                            onConfirm = { openSettings(plugin.pkg) }
                        )
                    } else if (plugin.requiredAction != "CONFIGURE_SETTINGS") {
                        autoOpenedSettings.remove(plugin.pkg)
                    }
                }
            }
        }
    }

    val sortedPlugins: StateFlow<List<InstalledPlugin>> = combine(
        installedPlugins,
        pluginManager.loadedPlugins // to trigger refresh if needed
    ) { plugins, _ ->
        plugins.sortedWith(
            compareByDescending<InstalledPlugin> { pluginManager.getUpdate(it.pkg) != null }
                .thenBy { it.name }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun installLocal() {
        viewModelScope.launch {
            val filePath = PlatformUtils.pickFile()
            if (filePath != null) {
                pickInstallLocation { target ->
                    viewModelScope.launch {
                        pluginManager.installLocal(filePath, target)
                    }
                }
            }
        }
    }

    fun openRemoteInstall() {
        _showRemoteInstall.value = true
    }

    fun closeRemoteInstall() {
        _showRemoteInstall.value = false
    }

    fun installRemote(plugin: ExtensionPlugin) {
        viewModelScope.launch {
            pickInstallLocation { target ->
                viewModelScope.launch {
                    pluginManager.enqueueRemoteInstall(plugin, target)
                }
            }
        }
    }

    fun pickInstallLocation(onSelected: (String) -> Unit) {
        viewModelScope.launch {
            val savedFolders = settingsRepository.loadSettings().extensions.pluginFolders
            val allFolders = (listOf(defaultPluginFolder) + savedFolders).distinct()

            if (allFolders.size == 1) {
                onSelected(allFolders.first())
                return@launch
            }

            dialogService.showLocationPicker(
                getString(Res.string.plugin_choose_install_location),
                allFolders,
                onSelected
            )
        }
    }

    fun addManagedFolder() {
        viewModelScope.launch {
            val folder = PlatformUtils.pickFolder()
            if (folder != null) {
                if (folder == defaultPluginFolder) return@launch

                settingsRepository.updateSettings { settings ->
                    if (settings.extensions.pluginFolders.contains(folder)) settings
                    else {
                        val updated = settings.extensions.pluginFolders + folder
                        settings.copy(
                            extensions = settings.extensions.copy(pluginFolders = updated)
                        )
                    }
                }
            }
        }
    }

    fun removeManagedFolder(folder: String) {
        viewModelScope.launch {
            // Already handled in PluginManager, but we can prevent the call here too
            val result = pluginManager.removeManagedFolder(folder)
            result.onFailure { error ->
                dialogService.showWarning(
                    getString(Res.string.plugin_action_blocked),
                    error.message ?: getString(Res.string.plugin_state_change_error),
                    onConfirm = {}
                )
            }
        }
    }

    fun reloadAll() {
        pluginManager.reloadAll()
    }

    fun refreshList() {
        pluginManager.refreshInstalledPlugins()
    }

    fun rescan() {
        viewModelScope.launch {
            pluginManager.rescanManagedFolders()
        }
    }

    fun toggleEnabled(pkg: String, enabled: Boolean) {
        viewModelScope.launch {
            val result = pluginManager.setEnabled(pkg, enabled)
            result.onFailure { error ->
                dialogService.showWarning(
                    getString(Res.string.plugin_action_blocked),
                    error.message ?: getString(Res.string.plugin_state_change_error),
                    onConfirm = {}
                )
            }
        }
    }

    fun uninstall(pkg: String) {
        viewModelScope.launch {
            val msg = getString(Res.string.plugin_uninstall_confirmation, pkg)
            val title = getString(Res.string.plugin_uninstall_title)
            dialogService.showConfirmation(
                title,
                msg,
                onConfirm = {
                    viewModelScope.launch {
                        val result = pluginManager.uninstall(pkg)
                        result.onFailure { error ->
                            dialogService.showWarning(
                                getString(Res.string.plugin_uninstall_blocked),
                                error.message ?: getString(Res.string.plugin_uninstall_error),
                                onConfirm = {}
                            )
                        }
                    }
                }
            )
        }
    }

    fun reload(pkg: String) {
        pluginManager.reloadPlugin(pkg)
    }

    fun updatePlugin(pkg: String) {
        viewModelScope.launch {
            if (pluginManager.getUpdate(pkg) != null) {
                pluginManager.updateRemote(pkg)
            } else {
                val newPath = PlatformUtils.pickFile()
                if (newPath != null) {
                    pluginManager.updateLocal(pkg, newPath)
                }
            }
        }
    }

    fun validatePlugin(pkg: String) {
        viewModelScope.launch {
            val result = pluginManager.validatePlugin(pkg)
            val msg = if (result.isSuccess) {
                getString(Res.string.plugin_validated_success)
            } else {
                getString(Res.string.plugin_validation_failed, result.exceptionOrNull()?.message ?: "Unknown error")
            }
            dialogService.showConfirmation(getString(Res.string.plugin_validation_result), msg) {}
        }
    }

    fun rerunSetup(pkg: String) {
        viewModelScope.launch {
            val plugin = pluginManager.installedPlugins.value.find { it.pkg == pkg } ?: return@launch
            if (plugin.isValidated) {
                dialogService.showConfirmation(
                    "Rerun Setup",
                    "The plugin is already set up. If you rerun the setup, all previous configuration and files will be wiped. Continue?",
                    onConfirm = {
                        viewModelScope.launch {
                            pluginManager.rerunSetup(pkg)
                        }
                    }
                )
            } else {
                pluginManager.rerunSetup(pkg)
            }
        }
    }

    fun showChangelog(pkg: String) {
        viewModelScope.launch {
            val plugin = pluginManager.installedPlugins.value.find { it.pkg == pkg }

            var content: String? = null

            if (plugin != null) {
                // Fetch from JAR resources
                val jarFileName = plugin.jarFileName ?: (plugin.pkg.substringAfterLast(".") + ".jar")
                val jarPath = plugin.installPath + "/" + jarFileName
                content = PlatformUtils.readFileFromZip(jarPath, "resources/changelog.md")
                if (content == null) {
                    content = PlatformUtils.readFileFromZip(jarPath, "changelog.md")
                }
            } else {
                // Fetch from remote repository
                content = pluginManager.fetchRemoteChangelog(pkg)
            }

            val name = plugin?.name ?: pkg

            if (content != null) {
                val versions = org.wip.plugintoolkit.api.utils.ChangelogParser.parse(content).releases
                dialogService.showChangelog(name, versions)
            } else {
                val errorMsg = if (plugin != null) {
                    getString(Res.string.plugin_changelog_not_found_jar, name)
                } else {
                    getString(Res.string.plugin_changelog_not_found_remote, name)
                }
                dialogService.showConfirmation(getString(Res.string.plugin_changelog), errorMsg) {}
            }
        }
    }

    fun openSettings(pkg: String) {
        _settingsPkg.value = pkg
    }

    fun closeSettings() {
        _settingsPkg.value?.let { autoOpenedSettings.remove(it) }
        _settingsPkg.value = null
    }

    fun getUpdate(pkg: String) = pluginManager.getUpdate(pkg)
    
    fun fixIssue(pkg: String) {
        openSettings(pkg)
    }

    fun getActions(pkg: String) = try {
        PluginLoader.getPluginById(pkg)?.getManifest()?.actions ?: emptyList()
    } catch (t: Throwable) {
        co.touchlab.kermit.Logger.e(t) { "Failed to get actions for $pkg" }
        emptyList()
    }

    fun runAction(pkg: String, actionName: String) {
        viewModelScope.launch {
            val manifest = PluginLoader.getPluginById(pkg)?.getManifest()
            val action = manifest?.actions?.find { it.name == actionName || it.functionName == actionName }
            if (action != null) {
                pluginManager.runAction(pkg, action)
            } else {
                co.touchlab.kermit.Logger.e { "Action $actionName not found for plugin $pkg" }
            }
        }
    }
}
