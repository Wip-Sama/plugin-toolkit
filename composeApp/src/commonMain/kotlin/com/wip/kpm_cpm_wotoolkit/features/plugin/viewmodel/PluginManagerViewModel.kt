package com.wip.kpm_cpm_wotoolkit.features.plugin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wip.kpm_cpm_wotoolkit.core.KeepTrack
import com.wip.kpm_cpm_wotoolkit.core.ui.DialogService
import com.wip.kpm_cpm_wotoolkit.core.utils.PlatformUtils
import com.wip.kpm_cpm_wotoolkit.features.plugin.logic.PluginManager
import com.wip.kpm_cpm_wotoolkit.features.plugin.model.InstalledPlugin
import com.wip.kpm_cpm_wotoolkit.features.settings.logic.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kpm_cpm_wotoolkit.composeapp.generated.resources.Res
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_action_blocked
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_changelog
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_changelog_not_found_jar
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_changelog_not_found_remote
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_choose_install_location
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_settings
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_state_change_error
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_uninstall_blocked
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_uninstall_confirmation
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_uninstall_error
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_uninstall_title
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_validated_success
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_validation_failed
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_validation_result
import org.jetbrains.compose.resources.getString

class PluginManagerViewModel(
    private val pluginManager: PluginManager,
    private val dialogService: DialogService,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val installedPlugins = pluginManager.installedPlugins
    val loadedPlugins = pluginManager.loadedPlugins

    val defaultPluginFolder = settingsRepository.getSettingsDir() + "/" + KeepTrack.PLUGINS_DIR_NAME

    private val _managedFolders = MutableStateFlow<List<String>>(emptyList())
    val managedFolders: StateFlow<List<String>> = _managedFolders.asStateFlow()

    init {
        PlatformUtils.mkdirs(defaultPluginFolder)
        refreshManagedFolders()
    }

    private fun refreshManagedFolders() {
        val savedFolders = settingsRepository.loadSettings().extensions.pluginFolders
        _managedFolders.value = (listOf(defaultPluginFolder) + savedFolders).distinct()
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

    fun pickInstallLocation(onSelected: (String) -> Unit) {
        viewModelScope.launch {
            val savedFolders = settingsRepository.loadSettings().extensions.pluginFolders
            val allFolders = (listOf(defaultPluginFolder) + savedFolders).distinct()
            
            if (allFolders.size == 1) {
                onSelected(allFolders.first())
                return@launch
            }
            
            dialogService.showLocationPicker(getString(Res.string.plugin_choose_install_location), allFolders, onSelected)
        }
    }

    fun addManagedFolder() {
        viewModelScope.launch {
            val folder = PlatformUtils.pickFolder()
            if (folder != null) {
                if (folder == defaultPluginFolder) return@launch

                val settings = settingsRepository.loadSettings()
                if (settings.extensions.pluginFolders.contains(folder)) return@launch

                val updated = settings.extensions.pluginFolders + folder
                settingsRepository.saveSettings(
                    settings.copy(
                        extensions = settings.extensions.copy(pluginFolders = updated)
                    )
                )
                refreshManagedFolders()
            }
        }
    }

    fun removeManagedFolder(folder: String) {
        if (folder == defaultPluginFolder) return

        val settings = settingsRepository.loadSettings()
        val updated = settings.extensions.pluginFolders - folder
        settingsRepository.saveSettings(
            settings.copy(
                extensions = settings.extensions.copy(pluginFolders = updated)
            )
        )
        refreshManagedFolders()
    }

    fun reloadAll() {
        pluginManager.reloadAll()
    }

    fun refreshList() {
        pluginManager.refreshInstalledPlugins()
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
            dialogService.showConfirmation(getString(Res.string.plugin_validation_result), msg, {})
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
                content = PlatformUtils.readFileFromZip(jarPath, "resources/changelog.txt")
                if (content == null) {
                    content = PlatformUtils.readFileFromZip(jarPath, "changelog.txt")
                }
            } else {
                // Fetch from remote repository
                content = pluginManager.fetchRemoteChangelog(pkg)
            }

            val name = plugin?.name ?: pkg

            if (content != null) {
                val versions = com.wip.kpm_cpm_wotoolkit.features.plugin.logic.ChangelogParser.parse(content)
                dialogService.showChangelog(name, versions)
            } else {
                val errorMsg = if (plugin != null) {
                    getString(Res.string.plugin_changelog_not_found_jar, name)
                } else {
                    getString(Res.string.plugin_changelog_not_found_remote, name)
                }
                dialogService.showConfirmation(getString(Res.string.plugin_changelog), errorMsg, {})
            }
        }
    }

    fun openSettings(pkg: String) {
        viewModelScope.launch {
            dialogService.showConfirmation(
                getString(Res.string.plugin_settings),
                "Settings editing to be implemented in a dedicated UI.",
                {}
            )
        }
    }

    fun getUpdate(pkg: String) = pluginManager.getUpdate(pkg)
}
