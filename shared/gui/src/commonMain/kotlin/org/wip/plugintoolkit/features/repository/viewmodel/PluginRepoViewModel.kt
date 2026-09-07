package org.wip.plugintoolkit.features.repository.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.mp.KoinPlatform
import io.ktor.http.encodeURLPathPart
import org.wip.plugintoolkit.core.SystemConfig
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.core.utils.VersionUtils
import org.wip.plugintoolkit.features.plugin.utils.PluginCompatibilityUtils
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.viewmodel.FlowViewModel
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.repository.logic.AddRepoResult
import org.wip.plugintoolkit.features.repository.logic.RepoManager
import org.wip.plugintoolkit.features.repository.model.ExtensionFlow
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin
import org.wip.plugintoolkit.features.repository.model.ExtensionRepo
import org.wip.plugintoolkit.features.repository.model.RepoValidationResult
import plugintoolkit.composeapp.generated.resources.dialog_select_plugin
import plugintoolkit.composeapp.generated.resources.plugin_choose_install_location
import plugintoolkit.composeapp.generated.resources.repo_add_error
import plugintoolkit.composeapp.generated.resources.repo_add_success
import plugintoolkit.composeapp.generated.resources.repo_all_refreshed
import plugintoolkit.composeapp.generated.resources.repo_already_added
import plugintoolkit.composeapp.generated.resources.repo_link_copied
import plugintoolkit.composeapp.generated.resources.repo_refreshed
import plugintoolkit.composeapp.generated.resources.repo_removed
import plugintoolkit.composeapp.generated.resources.plugin_changelog
import plugintoolkit.composeapp.generated.resources.plugin_changelog_not_found_remote
import plugintoolkit.composeapp.generated.resources.repo_source_updated

class PluginRepoViewModel(
    private val repoManager: RepoManager,
    private val pluginManager: org.wip.plugintoolkit.features.plugin.logic.PluginManager,
    private val flowViewModel: FlowViewModel,
    private val notificationService: NotificationService,
    private val dialogService: org.wip.plugintoolkit.core.ui.DialogService,
    private val settingsRepository: org.wip.plugintoolkit.features.settings.logic.SettingsRepository,
    private val jobManager: JobManager,
    private val appConfig: SystemConfig
) : ViewModel() {

    private val ResStrings = plugintoolkit.composeapp.generated.resources.Res.string
    private suspend fun getString(resource: org.jetbrains.compose.resources.StringResource, vararg args: Any) =
        org.jetbrains.compose.resources.getString(resource, *args)

    val plugins = repoManager.plugins
    val flows = repoManager.flows

    val installedPlugins = pluginManager.installedPlugins
    val flowState = flowViewModel.state

    var repoUrlInput by mutableStateOf("")

    val repositories = repoManager.repositories
    val isRefreshing = repoManager.isRefreshing

    // Repo Filtering & Sorting State
    var repoTypeTab by mutableStateOf(RepoTypeTab.All)
    var repoSearchQuery by mutableStateOf("")
    var repoStatusFilter by mutableStateOf(RepoStatusFilter.All)

    val totalRepoCount: StateFlow<Int> = repositories.map { it.size }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val remoteRepoCount: StateFlow<Int> = repositories.map { it.count { r -> r.isRemote } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val localRepoCount: StateFlow<Int> = repositories.map { it.count { r -> r.isLocal } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    // Plugin Controls Toolbar State
    var pluginSearchQuery by mutableStateOf("")
    var pluginSortMode by mutableStateOf(PluginSortMode.NameAsc)
    var pluginChipFilter by mutableStateOf(PluginChipFilter.All)
    var pluginTagFilter by mutableStateOf<String?>(null)

    // Add Repository Dialog State
    var addRepoDialogState by mutableStateOf(AddRepoDialogState())
        private set

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

    val packageSourceOverrides: StateFlow<Map<String, String>> = settingsRepository.settings
        .map { it.extensions.packageSourceOverrides }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val activePluginInstallationJobs: StateFlow<Map<String, Float>> = jobManager.jobProgress
        .combine(jobManager.jobs) { progressMap, jobs ->
            jobs.filter {
                (it.type == JobType.Setup || it.type == JobType.Update || it.type == JobType.Validation) &&
                        (it.status == JobStatus.Running || it.status == JobStatus.Queued)
            }
                .associate { it.pluginId to (progressMap[it.id]?.mainProgress ?: 0f) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun filterRepositories(repos: List<ExtensionRepo>): List<ExtensionRepo> {
        return repos.filter { repo ->
            val matchesType = when (repoTypeTab) {
                RepoTypeTab.All -> true
                RepoTypeTab.Remote -> repo.isRemote
                RepoTypeTab.Local -> repo.isLocal
            }
            val matchesSearch = repoSearchQuery.isBlank() ||
                    repo.name.contains(repoSearchQuery, ignoreCase = true) ||
                    repo.url.contains(repoSearchQuery, ignoreCase = true)
            matchesType && matchesSearch
        }
    }

    fun filterAndSortPlugins(pluginsList: List<ExtensionPlugin>): List<ExtensionPlugin> {
        val filtered = pluginsList.filter { plugin ->
            val installedPlugin = installedPlugins.value.find { it.pkg == plugin.pkg }
            val isInstalled = installedPlugin != null
            val hasUpdate = installedPlugin != null && VersionUtils.compare(plugin.version, installedPlugin.version) > 0
            val (isCompatible, _) = PluginCompatibilityUtils.checkCompatibility(plugin)

            val matchesSearch = pluginSearchQuery.isBlank() ||
                    plugin.name.contains(pluginSearchQuery, ignoreCase = true) ||
                    (plugin.description ?: "").contains(pluginSearchQuery, ignoreCase = true) ||
                    plugin.pkg.contains(pluginSearchQuery, ignoreCase = true)

            val matchesChip = when (pluginChipFilter) {
                PluginChipFilter.All -> true
                PluginChipFilter.Installed -> isInstalled
                PluginChipFilter.UpdateAvailable -> hasUpdate
                PluginChipFilter.NotInstalled -> !isInstalled
                PluginChipFilter.Incompatible -> !isCompatible
            }

            val matchesTag = pluginTagFilter == null ||
                    (plugin.manifest?.plugin?.id?.contains(pluginTagFilter!!, ignoreCase = true) == true) ||
                    plugin.name.contains(pluginTagFilter!!, ignoreCase = true) ||
                    (plugin.description ?: "").contains(pluginTagFilter!!, ignoreCase = true)

            matchesSearch && matchesChip && matchesTag
        }

        return when (pluginSortMode) {
            PluginSortMode.NameAsc -> filtered.sortedBy { it.name.lowercase() }
            PluginSortMode.NameDesc -> filtered.sortedByDescending { it.name.lowercase() }
            PluginSortMode.StatusInstalledFirst -> filtered.sortedByDescending { isInstalled(it.pkg) }
            PluginSortMode.LatestVersion -> filtered.sortedWith { a, b -> VersionUtils.compare(b.version, a.version) }
        }
    }

    fun filterAndSortFlows(flowsList: List<ExtensionFlow>): List<ExtensionFlow> {
        val currentFlowState = flowState.value
        val filtered = flowsList.filter { flow ->
            val isInstalled = currentFlowState.flows.any { it.name == flow.name }
            val (isCompatible, _) = PluginCompatibilityUtils.checkCompatibility(flow)

            val matchesSearch = pluginSearchQuery.isBlank() ||
                    flow.name.contains(pluginSearchQuery, ignoreCase = true) ||
                    (flow.description ?: "").contains(pluginSearchQuery, ignoreCase = true) ||
                    flow.fileName.contains(pluginSearchQuery, ignoreCase = true)

            val matchesChip = when (pluginChipFilter) {
                PluginChipFilter.All -> true
                PluginChipFilter.Installed -> isInstalled
                PluginChipFilter.UpdateAvailable -> false
                PluginChipFilter.NotInstalled -> !isInstalled
                PluginChipFilter.Incompatible -> !isCompatible
            }

            matchesSearch && matchesChip
        }

        return when (pluginSortMode) {
            PluginSortMode.NameAsc -> filtered.sortedBy { it.name.lowercase() }
            PluginSortMode.NameDesc -> filtered.sortedByDescending { it.name.lowercase() }
            PluginSortMode.StatusInstalledFirst -> filtered.sortedByDescending { flow -> currentFlowState.flows.any { it.name == flow.name } }
            PluginSortMode.LatestVersion -> filtered.sortedWith { a, b -> VersionUtils.compare(b.version, a.version) }
        }
    }

    fun openAddRepoDialog(isLocal: Boolean = false) {
        addRepoDialogState = AddRepoDialogState(
            isOpen = true,
            isLocalMode = isLocal,
            targetInput = "",
            validationResult = RepoValidationResult.Idle
        )
    }

    fun closeAddRepoDialog() {
        addRepoDialogState = AddRepoDialogState(isOpen = false)
    }

    fun onAddRepoTargetChange(input: String) {
        addRepoDialogState = addRepoDialogState.copy(
            targetInput = input,
            validationResult = RepoValidationResult.Idle
        )
    }

    fun onAddRepoModeChange(isLocal: Boolean) {
        addRepoDialogState = addRepoDialogState.copy(
            isLocalMode = isLocal,
            targetInput = "",
            validationResult = RepoValidationResult.Idle
        )
    }

    fun validateAddRepoTarget() {
        val target = addRepoDialogState.targetInput.trim()
        if (target.isEmpty()) return

        addRepoDialogState = addRepoDialogState.copy(validationResult = RepoValidationResult.Checking)
        viewModelScope.launch {
            val result = repoManager.validateRepository(target)
            addRepoDialogState = addRepoDialogState.copy(validationResult = result)
        }
    }

    fun browseLocalIndexFile() {
        viewModelScope.launch {
            val pickedPath = PlatformUtils.pickFile(
                title = getString(ResStrings.dialog_select_plugin),
                allowedExtensions = listOf("json")
            )
            if (pickedPath != null) {
                addRepoDialogState = addRepoDialogState.copy(
                    targetInput = pickedPath,
                    validationResult = RepoValidationResult.Checking
                )
                val result = repoManager.validateRepository(pickedPath)
                addRepoDialogState = addRepoDialogState.copy(validationResult = result)
            }
        }
    }

    fun confirmAddRepository() {
        val target = addRepoDialogState.targetInput.trim()
        if (target.isEmpty()) return

        viewModelScope.launch {
            when (val result = repoManager.addRepository(target)) {
                is AddRepoResult.Success -> {
                    closeAddRepoDialog()
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

    fun openLocalFolder(path: String) {
        val folderPath = if (path.endsWith(".json", ignoreCase = true)) {
            path.replace('\\', '/').substringBeforeLast('/')
        } else {
            path
        }
        PlatformUtils.openFolder(folderPath)
    }

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
                pluginManager.enqueueRemoteInstall(plugin, target)
            }
        }
    }

    fun cancelPluginInstall(pkg: String) {
        viewModelScope.launch {
            val job =
                jobManager.jobs.value.find { it.type == JobType.PluginInstallation && it.status == JobStatus.Running && it.pluginId == pkg }
            if (job != null) {
                jobManager.cancelJob(job.id, force = true)
            }
        }
    }

    private fun pickInstallLocation(onSelected: (String) -> Unit) {
        val defaultPath = settingsRepository.getSettingsDir() + "/" + appConfig.PLUGINS_DIR_NAME
        val savedFolders = settingsRepository.loadSettings().extensions.pluginFolders
        val allFolders = (listOf(defaultPath) + savedFolders).distinct()

        if (allFolders.size == 1) {
            onSelected(allFolders.first())
            return
        }

        viewModelScope.launch {
            dialogService.showLocationPicker(
                getString(ResStrings.plugin_choose_install_location),
                allFolders,
                onSelected
            )
        }
    }

    fun isInstalled(pkg: String): Boolean {
        return pluginManager.installedPlugins.value.any { it.pkg == pkg }
    }

    fun getInstalledVersion(pkg: String): String? {
        return pluginManager.installedPlugins.value.find { it.pkg == pkg }?.version
    }

    fun copyRepositoryLink(url: String) {
        viewModelScope.launch {
            notificationService.toast(getString(ResStrings.repo_link_copied))
        }
    }

    fun isFlowInstalled(name: String): Boolean {
        return flowViewModel.state.value.flows.any { it.name == name }
    }

    fun getInstalledFlowVersion(name: String): String? {
        return flowViewModel.state.value.flows.find { it.name == name }?.version
    }

    fun getFlowUpdate(flow: ExtensionFlow): Boolean {
        val installed = getInstalledFlowVersion(flow.name) ?: return false
        return org.wip.plugintoolkit.core.utils.VersionUtils.compare(flow.version, installed) > 0
    }

    fun installFlow(flow: ExtensionFlow) {
        viewModelScope.launch {
            try {
                val repoUrl = flow.repoUrl ?: return@launch
                val repositories = repoManager.repositories.value
                val repo = repositories.find { it.url == repoUrl }
                val flowsFolder = repoUrl.substringBeforeLast("/") + "/" + (repo?.flowsFolder ?: "flows")
                val flowFileUrl = "$flowsFolder/${flow.fileName.encodeURLPathPart()}"

                val bytes = repoManager.fetchBytes(flowFileUrl) ?: run {
                    notificationService.toast("Failed to download flow file")
                    return@launch
                }

                // Transitive subflow dependency resolution (only if json)
                if (flow.fileName.endsWith(".json")) {
                    try {
                        val flowContent = bytes.decodeToString()
                        val parsedFlow = KoinPlatform.getKoin().get<Json>()
                            .decodeFromString<Flow>(flowContent)

                        val subflows =
                            parsedFlow.nodes.filterIsInstance<Node.SubFlowNode>()
                        for (subflow in subflows) {
                            val name = subflow.flowName
                            if (!isFlowInstalled(name)) {
                                // Search for matching flow in repos
                                val matchingRemote = repoManager.flows.value.values.flatten().find { it.name == name }
                                if (matchingRemote != null) {
                                    Logger.i { "Dependency subflow '$name' found in repo. Installing recursively." }
                                    installFlowRecursive(matchingRemote)
                                } else {
                                    Logger.w { "Dependency subflow '$name' not found in repositories." }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Logger.e(e) { "Failed to parse downloaded flow to resolve transitive dependencies" }
                    }
                }

                flowViewModel.importFlowFromBytes(bytes, flow.fileName)
            } catch (e: Exception) {
                Logger.e(e) { "Failed to install flow" }
                notificationService.toast("Error installing flow: ${e.message}")
            }
        }
    }

    private suspend fun installFlowRecursive(flow: ExtensionFlow) {
        val repoUrl = flow.repoUrl ?: return
        val repositories = repoManager.repositories.value
        val repo = repositories.find { it.url == repoUrl }
        val flowsFolder = repoUrl.substringBeforeLast("/") + "/" + (repo?.flowsFolder ?: "flows")
        val flowFileUrl = "$flowsFolder/${flow.fileName.encodeURLPathPart()}"

        val bytes = repoManager.fetchBytes(flowFileUrl) ?: return

        if (flow.fileName.endsWith(".json")) {
            try {
                val flowContent = bytes.decodeToString()
                val parsedFlow = KoinPlatform.getKoin().get<Json>()
                    .decodeFromString<Flow>(flowContent)

                val subflows =
                    parsedFlow.nodes.filterIsInstance<Node.SubFlowNode>()
                for (subflow in subflows) {
                    val name = subflow.flowName
                    if (!isFlowInstalled(name)) {
                        val matchingRemote = repoManager.flows.value.values.flatten().find { it.name == name }
                        if (matchingRemote != null) {
                            installFlowRecursive(matchingRemote)
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to parse dependency flow recursively" }
            }
        }

        flowViewModel.importFlowFromBytes(bytes, flow.fileName)
    }

    fun showChangelog(plugin: ExtensionPlugin) {
        viewModelScope.launch {
            val installed = installedPlugins.value.find { it.pkg == plugin.pkg }
            var content: String? = null
            if (installed != null) {
                val jarFileName = installed.jarFileName ?: (installed.pkg.substringAfterLast(".") + ".jar")
                val jarPath = installed.installPath + "/" + jarFileName
                content = PlatformUtils.readFileFromZip(jarPath, "resources/changelog.md")
                    ?: PlatformUtils.readFileFromZip(jarPath, "changelog.md")
            }
            if (content == null) {
                content = pluginManager.fetchRemoteChangelog(plugin.pkg)
            }
            val name = plugin.name.ifBlank { plugin.pkg }
            if (content != null) {
                val versions = org.wip.plugintoolkit.api.utils.ChangelogParser.parse(content).releases
                dialogService.showChangelog(name, versions)
            } else {
                val errorMsg = getString(ResStrings.plugin_changelog_not_found_remote, name)
                dialogService.showConfirmation(getString(ResStrings.plugin_changelog), errorMsg) {}
            }
        }
    }
}
