package org.wip.plugintoolkit.features.plugin.viewmodel

import co.touchlab.kermit.Logger
import org.wip.plugintoolkit.core.model.resolveNonComposable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.wip.plugintoolkit.core.SystemConfig
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.ui.DialogService
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.flows.viewmodel.FlowViewModel
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
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
import plugintoolkit.composeapp.generated.resources.plugin_uninstall_confirmation
import plugintoolkit.composeapp.generated.resources.plugin_uninstall_title
import plugintoolkit.composeapp.generated.resources.plugin_install_failed
import plugintoolkit.composeapp.generated.resources.plugin_installed_success
import plugintoolkit.composeapp.generated.resources.plugin_validated_success
import plugintoolkit.composeapp.generated.resources.plugin_validation_failed
import plugintoolkit.composeapp.generated.resources.plugin_validation_result
import plugintoolkit.composeapp.generated.resources.*
import org.wip.plugintoolkit.core.model.localized
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.wip.plugintoolkit.features.navigation.model.Screen


class PluginManagerViewModel(
    private val pluginManager: PluginManager,
    private val dialogService: DialogService,
    private val settingsRepository: SettingsRepository,
    repoManager: RepoManager,
    jobManager: JobManager,
    private val flowViewModel: FlowViewModel,
    appConfig: SystemConfig,
    private val notificationService: NotificationService
) : ViewModel() {

    private val _navigationEvent = MutableSharedFlow<Screen>()
    val navigationEvent: SharedFlow<Screen> = _navigationEvent.asSharedFlow()

    val installedPlugins = pluginManager.installedPlugins

    val loadedPlugins = pluginManager.loadedPlugins
    val isRegistryReady = pluginManager.isRegistryReady

    val defaultPluginFolder = settingsRepository.getSettingsDir() + "/" + appConfig.PLUGINS_DIR_NAME

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

    val loadingPlugins: StateFlow<Set<String>> = pluginManager.loadingPlugins
    val pluginLoadingSteps: StateFlow<Map<String, String>> = pluginManager.pluginLoadingSteps

    val activePluginJobs: StateFlow<Map<String, ActivePluginJobInfo>> = jobManager.jobProgress
        .combine(jobManager.jobs) { progressMap, jobs ->
            jobs.filter {
                (it.type == JobType.Setup || it.type == JobType.Update || it.type == JobType.Validation) &&
                        (it.status == JobStatus.Running || it.status == JobStatus.Queued)
            }
                .associate { job ->
                    job.pluginId to ActivePluginJobInfo(
                        jobId = job.id,
                        type = job.type,
                        status = job.status,
                        progress = progressMap[job.id]?.mainProgress ?: 0f
                    )
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val activePluginInstallationJobs: StateFlow<Map<String, Float>> = activePluginJobs
        .map { map -> map.mapValues { it.value.progress } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _togglingPlugins = MutableStateFlow<Set<String>>(emptySet())
    val togglingPlugins: StateFlow<Set<String>> = _togglingPlugins.asStateFlow()

    val pluginActivities: StateFlow<Map<String, PluginActivityInfo>> = combine(
        jobManager.jobProgress,
        jobManager.jobs,
        pluginManager.loadingPlugins,
        pluginManager.pluginLoadingSteps,
        _togglingPlugins
    ) { progressMap, jobs, loadingSet, stepsMap, togglingSet ->
        val activeJobs = jobs.filter {
            (it.type == JobType.Setup || it.type == JobType.Update || it.type == JobType.Validation) &&
                    (it.status == JobStatus.Running || it.status == JobStatus.Queued)
        }
        val result = mutableMapOf<String, PluginActivityInfo>()

        for (job in activeJobs) {
            val prog = progressMap[job.id]?.mainProgress ?: 0f
            result[job.pluginId] = PluginActivityInfo(
                type = job.type,
                step = when (job.type) {
                    JobType.Setup -> "Setting up dependencies and models"
                    JobType.Validation -> "Validating plugin capabilities"
                    JobType.Update -> "Updating plugin"
                    else -> "Processing"
                },
                progress = prog,
                isDeterminate = prog > 0f,
                isBusy = true
            )
        }

        for (pkg in loadingSet) {
            if (!result.containsKey(pkg)) {
                result[pkg] = PluginActivityInfo(
                    type = null,
                    step = stepsMap[pkg] ?: "Loading plugin...",
                    progress = 0f,
                    isDeterminate = false,
                    isBusy = true
                )
            }
        }

        for (pkg in togglingSet) {
            if (!result.containsKey(pkg)) {
                result[pkg] = PluginActivityInfo(
                    type = null,
                    step = "Updating state...",
                    progress = 0f,
                    isDeterminate = false,
                    isBusy = true
                )
            }
        }

        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Persistence flags handled via InstalledPlugin now

    private val sessionAllowedUnsignedPlugins = mutableSetOf<String>()

    init {
        PlatformUtils.mkdirs(defaultPluginFolder)

        viewModelScope.launch {
            installedPlugins
                .map { plugins ->
                    plugins.filter { plugin ->
                        (plugin.requiredAction == "CONFIGURE_SETTINGS" && !plugin.configurationPrompted) ||
                                (plugin.requiredAction == "CONFIRM_SIGNATURE" && !plugin.signaturePrompted)
                    }
                }
                .distinctUntilChanged()
                .collectLatest { unpromptedPlugins ->
                    unpromptedPlugins.forEach { plugin ->
                        if (plugin.requiredAction == "CONFIGURE_SETTINGS" && !plugin.configurationPrompted) {
                            pluginManager.updatePlugin(plugin.pkg) { it.copy(configurationPrompted = true) }
                            dialogService.showConfirmation(
                                title = Res.string.plugin_config_required.localized.resolveNonComposable(),
                                message = "Plugin ${plugin.name} requires configuration. Would you like to configure it now?",
                                onConfirm = { openSettings(plugin.pkg) }
                            )
                        } else if (plugin.requiredAction == "CONFIRM_SIGNATURE" && !plugin.signaturePrompted) {
                            val strictChecking = settingsRepository.settings.value.extensions.strictSignatureChecking
                            if (strictChecking) {
                                pluginManager.updatePlugin(plugin.pkg) { it.copy(signaturePrompted = true) }
                                dialogService.showConfirmation(
                                    title = Res.string.plugin_invalid_signature.localized.resolveNonComposable(),
                                    message = "Plugin ${plugin.name} has an invalid or missing signature. Strict signature checking is currently enabled. Would you like to open Settings to adjust signature checking?",
                                    onConfirm = { openSettings(plugin.pkg) }
                                )
                            } else {
                                if (sessionAllowedUnsignedPlugins.contains(plugin.pkg)) {
                                    pluginManager.updatePlugin(plugin.pkg) { p ->
                                        p.copy(
                                            requiredAction = null,
                                            isEnabled = true,
                                            loadError = null,
                                            isValidated = true
                                        )
                                    }
                                    pluginManager.reloadPlugin(plugin.pkg)
                                } else {
                                    pluginManager.updatePlugin(plugin.pkg) { it.copy(signaturePrompted = true) }
                                    dialogService.showConfirmation(
                                        title = Res.string.plugin_invalid_signature.localized.resolveNonComposable(),
                                        message = "Plugin ${plugin.name} has an invalid or missing signature. Do you want to allow loading it for this session?",
                                        onConfirm = {
                                            sessionAllowedUnsignedPlugins.add(plugin.pkg)
                                            viewModelScope.launch {
                                                pluginManager.updatePlugin(plugin.pkg) { p ->
                                                    p.copy(
                                                        requiredAction = null,
                                                        isEnabled = true,
                                                        loadError = null,
                                                        isValidated = true
                                                    )
                                                }
                                                pluginManager.reloadPlugin(plugin.pkg)
                                            }
                                        }
                                    )
                                }
                            }
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
                        val result = pluginManager.installLocal(filePath, target)
                        if (result.isFailure) {
                            val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                            notificationService.toast(getString(Res.string.plugin_install_failed, errorMsg))
                        } else {
                            notificationService.toast(getString(Res.string.plugin_installed_success))
                        }
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
        viewModelScope.launch {
            pluginManager.refreshInstalledPlugins()
        }
    }

    fun rescan() {
        viewModelScope.launch {
            pluginManager.rescanManagedFolders()
        }
    }

    fun refreshLocks(pkg: String) {
        viewModelScope.launch {
            try {
                pluginManager.refreshLocks(pkg)
                val plugin = installedPlugins.value.find { it.pkg == pkg }
                val name = plugin?.name ?: pkg
                notificationService.toast(getString(Res.string.plugin_locks_refreshed_single, name))
            } catch (t: Throwable) {
                Logger.e(t) { "Failed to refresh locks for $pkg" }
            }
        }
    }

    fun refreshAllLocks() {
        viewModelScope.launch {
            try {
                pluginManager.refreshAllLocks()
                notificationService.toast(getString(Res.string.plugin_locks_refreshed_all))
            } catch (t: Throwable) {
                Logger.e(t) { "Failed to refresh all plugin locks" }
            }
        }
    }

    fun toggleEnabled(pkg: String, enabled: Boolean) {
        viewModelScope.launch {
            _togglingPlugins.update { it + pkg }
            try {
                val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    pluginManager.setEnabled(pkg, enabled)
                }
                result.onFailure { error ->
                    dialogService.showWarning(
                        getString(Res.string.plugin_action_blocked),
                        error.message ?: getString(Res.string.plugin_state_change_error),
                        onConfirm = {}
                    )
                }
            } finally {
                _togglingPlugins.update { it - pkg }
            }
        }
    }

    fun uninstall(pkg: String) {
        viewModelScope.launch {
            try {
                notificationService.toast("Initiating removal of $pkg...")
                try {
                    val msg = getString(Res.string.plugin_uninstall_confirmation, pkg)
                    val title = getString(Res.string.plugin_uninstall_title)
                    dialogService.showConfirmation(
                        title,
                        msg,
                        onConfirm = {
                            viewModelScope.launch {
                                notificationService.toast("Uninstall confirmed for $pkg")
                                val result = pluginManager.uninstall(pkg)
                                result.onFailure { error ->
                                    notificationService.toast("Failed to uninstall $pkg: ${error.message}")
                                }.onSuccess {
                                    notificationService.toast("Plugin $pkg uninstalled")
                                }
                            }
                        }
                    )
                } catch (e: Throwable) {
                    // Fallback if getString fails (e.g. format args mismatch in CMP)
                    dialogService.showConfirmation(
                        "Uninstall Plugin",
                        "Are you sure you want to uninstall $pkg?",
                        onConfirm = {
                            viewModelScope.launch {
                                notificationService.toast("Uninstall confirmed for $pkg (Fallback)")
                                val result = pluginManager.uninstall(pkg)
                                result.onFailure { error ->
                                    notificationService.toast("Failed to uninstall $pkg: ${error.message}")
                                }.onSuccess {
                                    notificationService.toast("Plugin $pkg uninstalled")
                                }
                            }
                        }
                    )
                }
            } catch (t: Throwable) {
                notificationService.toast("Critical error during uninstall init: ${t.message}")
            }
        }
    }

    fun reload(pkg: String) {
        pluginManager.reloadPlugin(pkg)
    }

    fun updatePlugin(pkg: String) {
        viewModelScope.launch {
            val manifest = pluginManager.getManifest(pkg)
            val proceedWithUpdate = suspend {
                if (pluginManager.getUpdate(pkg) != null) {
                    pluginManager.updateRemote(pkg)
                } else {
                    val newPath = PlatformUtils.pickFile()
                    if (newPath != null) {
                        pluginManager.updateLocal(pkg, newPath)
                    }
                }
                flowViewModel.triggerMigrationsForUpdatedPlugin(pkg)
            }

            if (manifest != null && manifest.hasSetupHandler && !manifest.hasUpdateHandler) {
                dialogService.showConfirmation(
                    title = Res.string.plugin_update_warning.localized.resolveNonComposable(),
                    message = "This plugin requires setup but does not support migrations. Updating will wipe all current configuration and data for this plugin. Continue?",
                    onConfirm = {
                        viewModelScope.launch {
                            proceedWithUpdate()
                        }
                    }
                )
            } else {
                proceedWithUpdate()
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

            var content: String?

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
        _settingsPkg.value = null
    }

    fun openFolder(pkg: String) {
        val plugin = pluginManager.installedPlugins.value.find { it.pkg == pkg }
        plugin?.installPath?.let { path ->
            PlatformUtils.openFolder(path)
        }
    }

    fun getUpdate(pkg: String) = pluginManager.getUpdate(pkg)

    fun fixIssue(pkg: String) {
        openSettings(pkg)
    }

    fun getActions(pkg: String) = try {
        pluginManager.getManifest(pkg)?.actions ?: emptyList()
    } catch (t: Throwable) {
        co.touchlab.kermit.Logger.e(t) { "Failed to get actions for $pkg" }
        emptyList()
    }

    fun runAction(pkg: String, actionName: String) {
        viewModelScope.launch {
            val manifest = pluginManager.getManifest(pkg)
            val action = manifest?.actions?.find { it.name == actionName || it.functionName == actionName }
            if (action != null) {
                pluginManager.runAction(pkg, action)
            } else {
                co.touchlab.kermit.Logger.e { "Action $actionName not found for plugin $pkg" }
            }
        }
    }
}

data class ActivePluginJobInfo(
    val jobId: String,
    val type: JobType,
    val status: JobStatus,
    val progress: Float
)

data class PluginActivityInfo(
    val type: JobType? = null,
    val step: String? = null,
    val progress: Float = 0f,
    val isDeterminate: Boolean = false,
    val isBusy: Boolean = true
)
