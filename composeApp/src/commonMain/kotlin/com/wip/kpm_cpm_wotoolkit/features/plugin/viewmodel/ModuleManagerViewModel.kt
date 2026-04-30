package com.wip.kpm_cpm_wotoolkit.features.plugin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wip.kpm_cpm_wotoolkit.core.ui.DialogService
import com.wip.kpm_cpm_wotoolkit.core.utils.PlatformUtils
import com.wip.kpm_cpm_wotoolkit.features.plugin.logic.ModuleManager
import com.wip.kpm_cpm_wotoolkit.features.plugin.model.InstallationSource
import com.wip.kpm_cpm_wotoolkit.features.plugin.model.InstalledModule
import com.wip.kpm_cpm_wotoolkit.features.repository.model.ExtensionModule
import com.wip.kpm_cpm_wotoolkit.features.settings.logic.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.wip.kpm_cpm_wotoolkit.core.KeepTrack

class ModuleManagerViewModel(
    private val moduleManager: ModuleManager,
    private val dialogService: DialogService,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val installedModules = moduleManager.installedModules
    val loadedModules = moduleManager.loadedModules
    
    private val _managedFolders = MutableStateFlow<List<String>>(emptyList())
    val managedFolders: StateFlow<List<String>> = _managedFolders.asStateFlow()

    init {
        _managedFolders.value = settingsRepository.loadSettings().extensions.moduleFolders
    }

    val sortedModules: StateFlow<List<InstalledModule>> = combine(
        installedModules,
        moduleManager.loadedModules // to trigger refresh if needed
    ) { modules, _ ->
        modules.sortedWith(
            compareByDescending<InstalledModule> { moduleManager.getUpdate(it.pkg) != null }
                .thenBy { it.name }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun installLocal() {
        viewModelScope.launch {
            val filePath = PlatformUtils.pickFile()
            if (filePath != null) {
                pickInstallLocation { target ->
                    viewModelScope.launch {
                        moduleManager.installLocal(filePath, target)
                    }
                }
            }
        }
    }

    fun pickInstallLocation(onSelected: (String) -> Unit) {
        val folders = settingsRepository.loadSettings().extensions.moduleFolders
        if (folders.isEmpty()) {
            val defaultPath = settingsRepository.getSettingsDir() + "/" + KeepTrack.MODULES_DIR_NAME
            onSelected(defaultPath)
            return
        }
        dialogService.showLocationPicker("Choose Install Location", folders, onSelected)
    }

    fun addManagedFolder() {
        viewModelScope.launch {
            val folder = PlatformUtils.pickFolder()
            if (folder != null) {
                val settings = settingsRepository.loadSettings()
                val updated = settings.extensions.moduleFolders + folder
                settingsRepository.saveSettings(settings.copy(
                    extensions = settings.extensions.copy(moduleFolders = updated)
                ))
                _managedFolders.value = updated
            }
        }
    }

    fun removeManagedFolder(folder: String) {
        val settings = settingsRepository.loadSettings()
        val updated = settings.extensions.moduleFolders - folder
        settingsRepository.saveSettings(settings.copy(
            extensions = settings.extensions.copy(moduleFolders = updated)
        ))
        _managedFolders.value = updated
    }

    fun reloadAll() {
        moduleManager.reloadAll()
    }

    fun refreshList() {
        moduleManager.refreshInstalledModules()
    }

    fun toggleEnabled(pkg: String, enabled: Boolean) {
        moduleManager.setEnabled(pkg, enabled)
    }

    fun uninstall(pkg: String) {
        dialogService.showConfirmation(
            "Uninstall Module",
            "Are you sure you want to uninstall $pkg?",
            onConfirm = { moduleManager.uninstall(pkg) }
        )
    }

    fun reload(pkg: String) {
        moduleManager.reloadModule(pkg)
    }

    fun updateModule(pkg: String) {
        viewModelScope.launch {
            if (moduleManager.getUpdate(pkg) != null) {
                moduleManager.updateRemote(pkg)
            } else {
                val newPath = PlatformUtils.pickFile()
                if (newPath != null) {
                    moduleManager.updateLocal(pkg, newPath)
                }
            }
        }
    }

    fun validateModule(pkg: String) {
        viewModelScope.launch {
            val result = moduleManager.validateModule(pkg)
            val msg = if (result.isSuccess) "Module validated successfully." else "Validation failed: ${result.exceptionOrNull()?.message}"
            dialogService.showConfirmation("Validation Result", msg, {})
        }
    }

    fun showChangelog(pkg: String) {
        viewModelScope.launch {
            val module = moduleManager.installedModules.value.find { it.pkg == pkg }
            
            var content: String? = null
            
            if (module != null) {
                // Fetch from JAR resources
                val jarFileName = module.jarFileName ?: (module.pkg.substringAfterLast(".") + ".jar")
                val jarPath = module.installPath + "/" + jarFileName
                content = PlatformUtils.readFileFromZip(jarPath, "resources/changelog.txt")
                if (content == null) {
                    content = PlatformUtils.readFileFromZip(jarPath, "resources/changelog.txt")
                }
            } else {
                // Fetch from remote repository
                content = moduleManager.fetchRemoteChangelog(pkg)
            }
            
            val name = module?.name ?: pkg
            
            if (content != null) {
                val versions = com.wip.kpm_cpm_wotoolkit.features.plugin.logic.ChangelogParser.parse(content)
                dialogService.showChangelog(name, versions)
            } else {
                val errorMsg = if (module != null) "No changelog found in JAR resources for $name" else "No remote changelog found for $name"
                dialogService.showConfirmation("Changelog", errorMsg, {})
            }
        }
    }

    fun openSettings(pkg: String) {
        dialogService.showConfirmation("Settings", "Settings editing to be implemented in a dedicated UI.", {})
    }
    
    fun getUpdate(pkg: String) = moduleManager.getUpdate(pkg)
}
