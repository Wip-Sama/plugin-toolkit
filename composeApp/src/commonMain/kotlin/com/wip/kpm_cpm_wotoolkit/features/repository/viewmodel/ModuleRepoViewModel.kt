package com.wip.kpm_cpm_wotoolkit.features.repository.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wip.kpm_cpm_wotoolkit.core.notification.NotificationService
import com.wip.kpm_cpm_wotoolkit.features.repository.logic.AddRepoResult
import com.wip.kpm_cpm_wotoolkit.features.repository.logic.RepoManager
import com.wip.kpm_cpm_wotoolkit.features.repository.model.ExtensionModule
import com.wip.kpm_cpm_wotoolkit.features.repository.model.ExtensionRepo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ModuleRepoViewModel(
    private val repoManager: RepoManager,
    private val moduleManager: com.wip.kpm_cpm_wotoolkit.features.plugin.logic.ModuleManager,
    private val notificationService: NotificationService,
    private val dialogService: com.wip.kpm_cpm_wotoolkit.core.ui.DialogService,
    private val settingsRepository: com.wip.kpm_cpm_wotoolkit.features.settings.logic.SettingsRepository
) : ViewModel() {
    
    val modules = repoManager.modules
    
    var repoUrlInput by mutableStateOf("")

    val repositories = repoManager.repositories
    val isRefreshing = repoManager.isRefreshing

    // pkg -> List of repos offering it
    val conflicts: StateFlow<Map<String, List<ExtensionRepo>>> = repoManager.modules.map { modulesMap ->
        val pkgToRepos = mutableMapOf<String, MutableList<String>>()
        modulesMap.forEach { (url, modules) ->
            modules.forEach { module ->
                pkgToRepos.getOrPut(module.pkg) { mutableListOf() }.add(url)
            }
        }
        
        val repoUrlsWithConflicts = pkgToRepos.filter { it.value.size > 1 }
        val repos = repositories.value.associateBy { it.url }
        
        repoUrlsWithConflicts.mapValues { (_, urls) ->
            urls.mapNotNull { repos[it] }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun addRepository() {
        val url = repoUrlInput.trim()
        if (url.isEmpty()) return
        
        viewModelScope.launch {
            when (val result = repoManager.addRepository(url)) {
                is AddRepoResult.Success -> {
                    repoUrlInput = ""
                    notificationService.toast("Repository added successfully")
                }
                is AddRepoResult.AlreadyAdded -> {
                    notificationService.toast("Repository already added")
                }
                is AddRepoResult.Error -> {
                    notificationService.toast("Error adding repository: ${result.message}")
                }
            }
        }
    }

    fun removeRepository(url: String) {
        repoManager.removeRepository(url)
        notificationService.toast("Repository removed")
    }

    fun refreshRepository(url: String) {
        viewModelScope.launch {
            repoManager.refreshRepository(url)
            notificationService.toast("Repository refreshed")
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            repoManager.refreshAll()
            notificationService.toast("All repositories refreshed")
        }
    }

    fun setPackageSource(pkg: String, repoUrl: String) {
        repoManager.setPackageSourceOverride(pkg, repoUrl)
        notificationService.toast("Source for $pkg updated")
    }

    fun getSelectedRepoForPackage(pkg: String, repos: List<ExtensionRepo>): ExtensionRepo {
        val override = repoManager.repositories.value.find { it.url == repoManager.getPackageSourceOverride(pkg) }
        return override ?: repos.first()
    }

    fun installModule(module: ExtensionModule) {
        pickInstallLocation { target ->
            viewModelScope.launch {
                val result = moduleManager.installRemote(module, target)
                if (result.isSuccess) {
                    notificationService.toast("Module ${module.name} installed")
                } else {
                    notificationService.toast("Failed to install ${module.name}: ${result.exceptionOrNull()?.message}")
                }
            }
        }
    }

    private fun pickInstallLocation(onSelected: (String) -> Unit) {
        val folders = settingsRepository.loadSettings().extensions.moduleFolders
        if (folders.isEmpty()) {
            val defaultPath = settingsRepository.getSettingsDir() + "/" + com.wip.kpm_cpm_wotoolkit.core.KeepTrack.MODULES_DIR_NAME
            onSelected(defaultPath)
            return
        }
        dialogService.showLocationPicker("Choose Install Location", folders, onSelected)
    }

    fun isInstalled(pkg: String): Boolean {
        return moduleManager.installedModules.value.any { it.pkg == pkg }
    }

    fun getInstalledVersion(pkg: String): String? {
        return moduleManager.installedModules.value.find { it.pkg == pkg }?.version
    }
}
