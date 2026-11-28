package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.material3.AppBarRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.wip.plugintoolkit.shared.components.AdaptiveAppBarOverflowButton
import org.wip.plugintoolkit.shared.components.AdaptiveConstrainedContent
import org.wip.plugintoolkit.shared.components.AdaptivePaneHeader
import org.wip.plugintoolkit.shared.components.ToolkitAdaptiveSupportingWorkspace
import org.wip.plugintoolkit.shared.components.adaptiveOuterHorizontalPadding
import org.wip.plugintoolkit.shared.components.adaptiveWorkspacePadding
import org.wip.plugintoolkit.shared.components.rememberAdaptivePaneScaffoldDirective
import org.wip.plugintoolkit.shared.components.rememberSupportingPaneExpansionState
import org.wip.plugintoolkit.core.utils.PlatformVerticalScrollbar
import androidx.compose.foundation.layout.fillMaxHeight
import plugintoolkit.composeapp.generated.resources.pane_collapse_sidebar
import plugintoolkit.composeapp.generated.resources.pane_expand_sidebar
import plugintoolkit.composeapp.generated.resources.plugin_folders_toggle
import plugintoolkit.composeapp.generated.resources.plugin_manager_subtitle
import plugintoolkit.composeapp.generated.resources.plugin_manager_title

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wip.plugintoolkit.api.PluginAction
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.VersionUtils
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.plugin.model.PluginManagerChipFilter
import org.wip.plugintoolkit.features.plugin.model.PluginManagerSortMode
import org.wip.plugintoolkit.features.plugin.ui.components.PluginManagerFilterChipsRow
import org.wip.plugintoolkit.features.plugin.ui.components.PluginManagerSortDropdownChip
import org.wip.plugintoolkit.features.plugin.viewmodel.AlternateRepoUpdate
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginActivityInfo
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginManagerViewModel
import org.wip.plugintoolkit.shared.components.ToolkitCard
import org.wip.plugintoolkit.shared.components.ToolkitButtonGroup
import org.wip.plugintoolkit.shared.components.ToolkitChip
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import org.wip.plugintoolkit.shared.components.settings.SettingsGroup
import org.wip.plugintoolkit.shared.components.settings.SettingsItem
import org.wip.plugintoolkit.shared.components.settings.getGroupedShape
import org.wip.plugintoolkit.shared.components.tooltip
import org.wip.plugintoolkit.shared.components.verticalFadingEdges
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_more_actions
import plugintoolkit.composeapp.generated.resources.action_remove
import plugintoolkit.composeapp.generated.resources.plugin_search_placeholder
import plugintoolkit.composeapp.generated.resources.plugin_no_search_results
import plugintoolkit.composeapp.generated.resources.plugin_no_plugins_installed
import plugintoolkit.composeapp.generated.resources.plugin_add_folder
import plugintoolkit.composeapp.generated.resources.plugin_action_refresh_all_locks
import plugintoolkit.composeapp.generated.resources.plugin_action_refresh_locks
import plugintoolkit.composeapp.generated.resources.plugin_refresh_list_tooltip
import plugintoolkit.composeapp.generated.resources.plugin_rescan_tooltip
import plugintoolkit.composeapp.generated.resources.plugin_reload_all_tooltip
import plugintoolkit.composeapp.generated.resources.plugin_refresh_all_locks_tooltip
import plugintoolkit.composeapp.generated.resources.plugin_install_remote_tooltip
import plugintoolkit.composeapp.generated.resources.plugin_install_local_tooltip
import plugintoolkit.composeapp.generated.resources.plugin_settings_tooltip
import plugintoolkit.composeapp.generated.resources.plugin_broken
import plugintoolkit.composeapp.generated.resources.plugin_changelog
import plugintoolkit.composeapp.generated.resources.plugin_default_folder_label
import plugintoolkit.composeapp.generated.resources.plugin_default_tag
import plugintoolkit.composeapp.generated.resources.plugin_install_local
import plugintoolkit.composeapp.generated.resources.plugin_install_remote
import plugintoolkit.composeapp.generated.resources.plugin_loaded
import plugintoolkit.composeapp.generated.resources.plugin_managed_folders
import plugintoolkit.composeapp.generated.resources.plugin_open_folder
import plugintoolkit.composeapp.generated.resources.plugin_refresh_list
import plugintoolkit.composeapp.generated.resources.plugin_reload
import plugintoolkit.composeapp.generated.resources.plugin_reload_all
import plugintoolkit.composeapp.generated.resources.plugin_rerun_setup
import plugintoolkit.composeapp.generated.resources.plugin_rescan
import plugintoolkit.composeapp.generated.resources.plugin_setting_up
import plugintoolkit.composeapp.generated.resources.plugin_settings
import plugintoolkit.composeapp.generated.resources.plugin_state_activating
import plugintoolkit.composeapp.generated.resources.plugin_state_active
import plugintoolkit.composeapp.generated.resources.plugin_state_deactivating
import plugintoolkit.composeapp.generated.resources.plugin_state_disabled
import plugintoolkit.composeapp.generated.resources.plugin_state_setting_up
import plugintoolkit.composeapp.generated.resources.plugin_state_setting_up_percent
import plugintoolkit.composeapp.generated.resources.plugin_state_updating
import plugintoolkit.composeapp.generated.resources.plugin_state_validating
import plugintoolkit.composeapp.generated.resources.plugin_step_setup
import plugintoolkit.composeapp.generated.resources.plugin_step_update
import plugintoolkit.composeapp.generated.resources.plugin_step_validation
import plugintoolkit.composeapp.generated.resources.plugin_uninstall
import plugintoolkit.composeapp.generated.resources.plugin_update
import plugintoolkit.composeapp.generated.resources.plugin_update_local
import plugintoolkit.composeapp.generated.resources.plugin_validate
import plugintoolkit.composeapp.generated.resources.plugin_validated
import plugintoolkit.composeapp.generated.resources.plugin_status_validation_failed
import plugintoolkit.composeapp.generated.resources.plugin_validation_pending
import plugintoolkit.composeapp.generated.resources.plugin_version_pkg_format
import plugintoolkit.composeapp.generated.resources.plugin_newer_version_in_other_repo
import plugintoolkit.composeapp.generated.resources.plugin_switch_repo_action

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun PluginManagerView(
    viewModel: PluginManagerViewModel = koinInject(),
    initialPluginId: String? = null,
    initialScrollToSetting: String? = null,
    onOpenPlugin: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val plugins by viewModel.sortedPlugins.collectAsState()
    val loadedPlugins by viewModel.loadedPlugins.collectAsState()
    val isReady by viewModel.isRegistryReady.collectAsState()
    val settingsPkg by viewModel.settingsPkg.collectAsState()
    val activities by viewModel.pluginActivities.collectAsState()
    val activeInstallationJobs by viewModel.activePluginInstallationJobs.collectAsState()
    val alternateRepoUpdates by viewModel.alternateRepoUpdates.collectAsState()
    val lockedPlugins by viewModel.lockedPlugins.collectAsState()

    val lazyListState = rememberLazyListState()

    var activeScrollToSetting by remember { mutableStateOf(initialScrollToSetting) }

    LaunchedEffect(initialPluginId, initialScrollToSetting) {
        if (initialPluginId != null) {
            activeScrollToSetting = initialScrollToSetting
            viewModel.openSettings(initialPluginId)
        }
    }

    if (settingsPkg != null) {
        PluginSettingsDialog(
            pkg = settingsPkg!!,
            scrollToSetting = activeScrollToSetting,
            onDismiss = {
                viewModel.closeSettings()
                activeScrollToSetting = null
            }
        )
    }

    val showRemoteInstall by viewModel.showRemoteInstall.collectAsState()
    if (showRemoteInstall) {
        val availablePlugins by viewModel.availableRemotePlugins.collectAsState()
        val installedPlugins by viewModel.installedPlugins.collectAsState()

        RemotePluginInstallDialog(
            availablePlugins = availablePlugins,
            installedPackageNames = installedPlugins.map { it.pkg }.toSet(),
            activeJobs = activeInstallationJobs,
            onInstall = { viewModel.installRemote(it) },
            onDismiss = { viewModel.closeRemoteInstall() }
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var chipFilter by remember { mutableStateOf(PluginManagerChipFilter.All) }
    var sortMode by remember { mutableStateOf(PluginManagerSortMode.NameAsc) }

    val filteredPlugins = remember(plugins, searchQuery, chipFilter, sortMode, alternateRepoUpdates) {
        plugins
            .distinctBy { it.pkg }
            .filter { plugin ->
            val matchesSearch = searchQuery.isBlank() ||
                plugin.name.contains(searchQuery, ignoreCase = true) ||
                plugin.pkg.contains(searchQuery, ignoreCase = true) ||
                (plugin.description?.contains(searchQuery, ignoreCase = true) == true)

            val hasUpdate = viewModel.getUpdate(plugin.pkg) != null || alternateRepoUpdates[plugin.pkg] != null
            val matchesFilter = when (chipFilter) {
                PluginManagerChipFilter.All -> true
                PluginManagerChipFilter.Enabled -> plugin.isEnabled
                PluginManagerChipFilter.Disabled -> !plugin.isEnabled
                PluginManagerChipFilter.UpdateAvailable -> hasUpdate
                PluginManagerChipFilter.RequiresSetup -> !plugin.isValidated
            }

            matchesSearch && matchesFilter
        }.let { list ->
            when (sortMode) {
                PluginManagerSortMode.NameAsc -> list.sortedBy { it.name.lowercase() }
                PluginManagerSortMode.NameDesc -> list.sortedByDescending { it.name.lowercase() }
                PluginManagerSortMode.StatusEnabledFirst -> list.sortedWith(
                    compareByDescending<InstalledPlugin> { it.isEnabled }.thenBy { it.name.lowercase() }
                )
                PluginManagerSortMode.LatestVersion -> list.sortedWith { a, b ->
                    VersionUtils.compare(b.version, a.version)
                }
            }
        }
    }

    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(50)
        runCatching { searchFocusRequester.requestFocus() }
    }

    val scope = rememberCoroutineScope()
    val settingsRepository: org.wip.plugintoolkit.features.settings.logic.SettingsRepository = koinInject()
    val appSettings by settingsRepository.settings.collectAsState()
    var showFoldersPane by remember(appSettings.appearance.compactPanelsByDefault) {
        mutableStateOf(!appSettings.appearance.compactPanelsByDefault)
    }

    ToolkitAdaptiveSupportingWorkspace(
        showSupportingPane = showFoldersPane,
        onShowSupportingPaneChange = { showFoldersPane = it },
        collapseTooltip = stringResource(Res.string.pane_collapse_sidebar),
        expandTooltip = stringResource(Res.string.pane_expand_sidebar),
        applyWorkspacePadding = true,
        modifier = modifier.fillMaxSize(),
        mainPane = {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            horizontal = adaptiveOuterHorizontalPadding(),
                            vertical = ToolkitTheme.spacing.medium
                        )
                        .onPreviewKeyEvent { event ->
                            if (event.isCtrlPressed && event.key == Key.F && event.type == KeyEventType.KeyDown) {
                                searchFocusRequester.requestFocus()
                                true
                            } else false
                        }
                ) {
                    if (!isReady) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(ToolkitTheme.spacing.extraSmall),
                            contentAlignment = Alignment.Center
                        ) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    val foldersTooltip = stringResource(Res.string.plugin_folders_toggle)
                    val refreshListLabel = stringResource(Res.string.plugin_refresh_list)
                    val rescanLabel = stringResource(Res.string.plugin_rescan)
                    val reloadAllLabel = stringResource(Res.string.plugin_reload_all)
                    val refreshLocksLabel = stringResource(Res.string.plugin_action_refresh_all_locks)
                    val installRemoteLabel = stringResource(Res.string.plugin_install_remote)
                    val installLocalLabel = stringResource(Res.string.plugin_install_local)
                    AdaptivePaneHeader(
                        title = stringResource(Res.string.plugin_manager_title),
                        subtitle = stringResource(Res.string.plugin_manager_subtitle, plugins.distinctBy { it.pkg }.size),
                        actions = {
                            AppBarRow(
                                maxItemCount = 3,
                                overflowIndicator = { menuState ->
                                    AdaptiveAppBarOverflowButton(menuState = menuState)
                                }
                            ) {
                                clickableItem(
                                    onClick = { viewModel.openRemoteInstall() },
                                    enabled = isReady,
                                    icon = { Icon(Icons.Default.Add, contentDescription = installRemoteLabel) },
                                    label = installRemoteLabel
                                )
                                clickableItem(
                                    onClick = { viewModel.installLocal() },
                                    enabled = isReady,
                                    icon = { Icon(Icons.Default.Folder, contentDescription = installLocalLabel) },
                                    label = installLocalLabel
                                )
                                toggleableItem(
                                    checked = showFoldersPane,
                                    onCheckedChange = { showFoldersPane = it },
                                    icon = { Icon(Icons.Default.Folder, contentDescription = foldersTooltip) },
                                    label = foldersTooltip
                                )
                                clickableItem(
                                    onClick = { viewModel.refreshList() },
                                    enabled = isReady,
                                    icon = { Icon(Icons.Default.Refresh, contentDescription = refreshListLabel) },
                                    label = refreshListLabel
                                )
                                clickableItem(
                                    onClick = { viewModel.rescan() },
                                    enabled = isReady,
                                    icon = { Icon(Icons.Default.Folder, contentDescription = rescanLabel) },
                                    label = rescanLabel
                                )
                                clickableItem(
                                    onClick = { viewModel.reloadAll() },
                                    enabled = isReady,
                                    icon = { Icon(Icons.Default.Replay, contentDescription = reloadAllLabel) },
                                    label = reloadAllLabel
                                )
                                clickableItem(
                                    onClick = { viewModel.refreshAllLocks() },
                                    enabled = isReady,
                                    icon = { Icon(Icons.Default.Lock, contentDescription = refreshLocksLabel) },
                                    label = refreshLocksLabel
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

                    // Search & Filter Row
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                    ) {
                        ToolkitTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                            placeholder = {
                                Text(
                                    stringResource(Res.string.plugin_search_placeholder),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                            itemVerticalAlignment = Alignment.CenterVertically
                        ) {
                            PluginManagerFilterChipsRow(
                                selectedFilter = chipFilter,
                                onFilterSelected = { chipFilter = it },
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            PluginManagerSortDropdownChip(
                                sortMode = sortMode,
                                onSortModeChange = { sortMode = it }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

                    // Plugin List
                    if (filteredPlugins.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = ToolkitTheme.spacing.extraLarge),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotEmpty() || chipFilter != PluginManagerChipFilter.All) {
                                    stringResource(Res.string.plugin_no_search_results)
                                } else {
                                    stringResource(Res.string.plugin_no_plugins_installed)
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier
                                    .verticalFadingEdges(
                                        lazyListState = lazyListState,
                                        topFadeLength = ToolkitTheme.spacing.medium,
                                        bottomFadeLength = ToolkitTheme.spacing.medium
                                    )
                                    .fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = ToolkitTheme.spacing.small,
                                    bottom = ToolkitTheme.spacing.small,
                                    end = ToolkitTheme.dimensions.sduiScrollbarEndPadding
                                ),
                                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
                            ) {
                                items(filteredPlugins, key = { it.pkg }) { plugin ->
                                    val hasUpdate = remember(plugin.pkg, plugin.version) {
                                        viewModel.getUpdate(plugin.pkg) != null
                                    }
                                    val customActions = remember(plugin.pkg, plugin.version) {
                                        viewModel.getActions(plugin.pkg)
                                    }
                                    val activity = activities[plugin.pkg]
                                    val alternateUpdate = alternateRepoUpdates[plugin.pkg]
                                    PluginCard(
                                        plugin = plugin,
                                        isLoaded = loadedPlugins.contains(plugin.pkg),
                                        hasUpdate = hasUpdate,
                                        alternateUpdate = alternateUpdate,
                                        customActions = customActions,
                                        enabled = isReady,
                                        activity = activity,
                                        isLockedByJob = lockedPlugins.contains(plugin.pkg),
                                        onToggle = { if (isReady) viewModel.toggleEnabled(plugin.pkg, it) },
                                        onSwitchRepo = { viewModel.promptSwitchRepo(it) },
                                        onAction = { action ->
                                            if (isReady) {
                                                when (action) {
                                                    PluginStatusAction.Uninstall -> viewModel.uninstall(plugin.pkg)
                                                    PluginStatusAction.Reload -> viewModel.reload(plugin.pkg)
                                                    PluginStatusAction.Update -> viewModel.updatePlugin(plugin.pkg)
                                                    PluginStatusAction.Validate -> viewModel.validatePlugin(plugin.pkg)
                                                    PluginStatusAction.RerunSetup -> viewModel.rerunSetup(plugin.pkg)
                                                    PluginStatusAction.Changelog -> viewModel.showChangelog(plugin.pkg)
                                                    PluginStatusAction.Settings -> viewModel.openSettings(plugin.pkg)
                                                    PluginStatusAction.RefreshLocks -> viewModel.refreshLocks(plugin.pkg)
                                                    PluginStatusAction.OpenFolder -> viewModel.openFolder(plugin.pkg)
                                                    is PluginStatusAction.Custom -> viewModel.runAction(plugin.pkg, action.name)
                                                }
                                            }
                                        },
                                        onClick = { onOpenPlugin(plugin.pkg) }
                                    )
                                }
                            }
                            PlatformVerticalScrollbar(
                                lazyListState = lazyListState,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            },
            supportingPane = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ToolkitTheme.spacing.medium)
                ) {
                    // Managed Folders Section
                    SettingsGroup(
                        title = stringResource(Res.string.plugin_managed_folders),
                        collapsible = false
                    ) {
                        val folders = viewModel.managedFolders.collectAsState().value
                        val defaultFolder = viewModel.defaultPluginFolder
                        val totalItems = folders.size + 1

                        folders.forEachIndexed { index, folder ->
                            val isDefault = folder == defaultFolder
                            SettingsItem(
                                title = folder,
                                subtitle = if (isDefault) stringResource(Res.string.plugin_default_folder_label) else null,
                                icon = Icons.Default.Folder,
                                shape = getGroupedShape(index, totalItems),
                                control = {
                                    if (isDefault) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = MaterialTheme.shapes.extraSmall,
                                            modifier = Modifier.padding(end = ToolkitTheme.spacing.small)
                                        ) {
                                            Text(
                                                stringResource(Res.string.plugin_default_tag),
                                                modifier = Modifier.padding(
                                                    horizontal = ToolkitTheme.spacing.extraSmall + ToolkitTheme.dimensions.buttonGroupGap,
                                                    vertical = ToolkitTheme.spacing.extraSmall / 2
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        IconButton(onClick = { viewModel.removeManagedFolder(folder) }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(Res.string.action_remove),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            )
                        }
                        SettingsItem(
                            title = stringResource(Res.string.plugin_add_folder),
                            icon = Icons.Default.CreateNewFolder,
                            onClick = { viewModel.addManagedFolder() },
                            shape = getGroupedShape(folders.size, totalItems)
                        )
                    }
                }
            }
        )
}
