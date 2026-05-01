package com.wip.kpm_cpm_wotoolkit.features.repository.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wip.kpm_cpm_wotoolkit.core.KeepTrack
import com.wip.kpm_cpm_wotoolkit.core.notification.NotificationService
import com.wip.kpm_cpm_wotoolkit.features.repository.logic.AddRepoResult
import com.wip.kpm_cpm_wotoolkit.features.repository.logic.RepoManager
import com.wip.kpm_cpm_wotoolkit.features.repository.model.ExtensionPlugin
import com.wip.kpm_cpm_wotoolkit.features.repository.model.ExtensionRepo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kpm_cpm_wotoolkit.composeapp.generated.resources.plugin_choose_install_location
import kpm_cpm_wotoolkit.composeapp.generated.resources.repo_add_error
import kpm_cpm_wotoolkit.composeapp.generated.resources.repo_add_success
import kpm_cpm_wotoolkit.composeapp.generated.resources.repo_all_refreshed
import kpm_cpm_wotoolkit.composeapp.generated.resources.repo_already_added
import kpm_cpm_wotoolkit.composeapp.generated.resources.repo_install_failed
import kpm_cpm_wotoolkit.composeapp.generated.resources.repo_plugin_installed
import kpm_cpm_wotoolkit.composeapp.generated.resources.repo_refreshed
import kpm_cpm_wotoolkit.composeapp.generated.resources.repo_removed
import kpm_cpm_wotoolkit.composeapp.generated.resources.repo_source_updated

class PluginRepoViewModel(
    private val repoManager: RepoManager,
    private val pluginManager: com.wip.kpm_cpm_wotoolkit.features.plugin.logic.PluginManager,
    private val notificationService: NotificationService,
    private val dialogService: com.wip.kpm_cpm_wotoolkit.core.ui.DialogService,
    private val settingsRepository: com.wip.kpm_cpm_wotoolkit.features.settings.logic.SettingsRepository
) : ViewModel() {

    private val ResStrings = kpm_cpm_wotoolkit.composeapp.generated.resources.Res.string
    private suspend fun getString(resource: org.jetbrains.compose.resources.StringResource, vararg args: Any) =
        org.jetbrains.compose.resources.getString(resource, *args)

    val plugins = repoManager.plugins

    var repoUrlInput by mutableStateOf("")

    val repositories = repoManager.repositories
    val isRefreshing = repoManager.isRefreshing

    // pkg -> List of repos offering it
    val conflicts: StateFlow<Map<String, List<ExtensionRepo>>> = repoManager.plugins.map { pluginsMap ->
        val pkgToRepos = mutableMapOf<String, MutableList<String>>()
        pluginsMap.forEach { (url, plugins) ->
            plugins.forEach { plugin ->
                pkgToRepos.getOrPut(plugin.pkg) { mutableListOf() }.add(url)
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
                    notificationService.toast(getString(ResStrings.repo_add_success))
                }

                is AddRepoResult.AlreadyAdded -> {
                    notificationService.toast(getString(ResStrings.repo_already_added))
                }

                is AddRepoResult.Error -> {
                    notificationService.toast(getString(ResStrings.repo_add_error, result.message))
                }
            }
        }
    }

    fun removeRepository(url: String) {
        viewModelScope.launch {
            repoManager.removeRepository(url)
            notificationService.toast(getString(ResStrings.repo_removed))
        }
    }

    fun refreshRepository(url: String) {
        viewModelScope.launch {
            repoManager.refreshRepository(url)
            notificationService.toast(getString(ResStrings.repo_refreshed))
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            repoManager.refreshAll()
            notificationService.toast(getString(ResStrings.repo_all_refreshed))
        }
    }

    fun setPackageSource(pkg: String, repoUrl: String) {
        viewModelScope.launch {
            repoManager.setPackageSourceOverride(pkg, repoUrl)
            notificationService.toast(getString(ResStrings.repo_source_updated, pkg))
        }
    }

    fun getSelectedRepoForPackage(pkg: String, repos: List<ExtensionRepo>): ExtensionRepo {
        val override = repoManager.repositories.value.find { it.url == repoManager.getPackageSourceOverride(pkg) }
        return override ?: repos.first()
    }

    fun installPlugin(plugin: ExtensionPlugin) {
        pickInstallLocation { target ->
            viewModelScope.launch {
                val result = pluginManager.installRemote(plugin, target)
                if (result.isSuccess) {
                    notificationService.toast(getString(ResStrings.repo_plugin_installed, plugin.name))
                } else {
                    notificationService.toast(
                        getString(
                            ResStrings.repo_install_failed,
                            plugin.name,
                            result.exceptionOrNull()?.message ?: "Unknown error"
                        )
                    )
                }
            }
        }
    }

    private fun pickInstallLocation(onSelected: (String) -> Unit) {
        val defaultPath = settingsRepository.getSettingsDir() + "/" + KeepTrack.PLUGINS_DIR_NAME
        val savedFolders = settingsRepository.loadSettings().extensions.pluginFolders
        val allFolders = (listOf(defaultPath) + savedFolders).distinct()
        
        if (allFolders.size == 1) {
            onSelected(allFolders.first())
            return
        }
        
        viewModelScope.launch {
            dialogService.showLocationPicker(getString(ResStrings.plugin_choose_install_location), allFolders, onSelected)
        }
    }

    fun isInstalled(pkg: String): Boolean {
        return pluginManager.installedPlugins.value.any { it.pkg == pkg }
    }

    fun getInstalledVersion(pkg: String): String? {
        return pluginManager.installedPlugins.value.find { it.pkg == pkg }?.version
    }
}
