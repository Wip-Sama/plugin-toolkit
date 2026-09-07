package org.wip.plugintoolkit.features.repository.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import org.koin.compose.koinInject
import org.wip.plugintoolkit.features.repository.model.ExtensionRepo
import org.wip.plugintoolkit.features.repository.ui.components.AddRepoDialog
import org.wip.plugintoolkit.features.repository.ui.components.PluginRepoDetails
import org.wip.plugintoolkit.features.repository.ui.components.PluginRepoSidebar
import org.wip.plugintoolkit.features.repository.viewmodel.PluginRepoViewModel

@Composable
fun PluginRepoView(
    viewModel: PluginRepoViewModel = koinInject()
) {
    val repositories by viewModel.repositories.collectAsState()
    val conflicts by viewModel.conflicts.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pluginsMap by viewModel.plugins.collectAsState()
    val flowsMap by viewModel.flows.collectAsState()
    val packageSourceOverrides by viewModel.packageSourceOverrides.collectAsState()

    val totalRepoCount by viewModel.totalRepoCount.collectAsState()
    val remoteRepoCount by viewModel.remoteRepoCount.collectAsState()
    val localRepoCount by viewModel.localRepoCount.collectAsState()

    val installedPlugins by viewModel.installedPlugins.collectAsState()
    val flowState by viewModel.flowState.collectAsState()
    val activeJobs by viewModel.activePluginInstallationJobs.collectAsState()

    var selectedRepo by remember { mutableStateOf<ExtensionRepo?>(null) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Plugins, 1: Flows
    val clipboard = LocalClipboard.current

    val filteredRepos = viewModel.filterRepositories(repositories)

    // If selected repo is removed or not found, update selection
    LaunchedEffect(filteredRepos) {
        if (selectedRepo != null && repositories.none { it.url == selectedRepo?.url }) {
            selectedRepo = null
        }
        if (selectedRepo == null && filteredRepos.isNotEmpty()) {
            selectedRepo = filteredRepos.first()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            PluginRepoSidebar(
                repositories = filteredRepos,
                selectedRepo = selectedRepo,
                isRefreshing = isRefreshing,
                repoTypeTab = viewModel.repoTypeTab,
                totalRepoCount = totalRepoCount,
                remoteRepoCount = remoteRepoCount,
                localRepoCount = localRepoCount,
                repoSearchQuery = viewModel.repoSearchQuery,
                onRepoTypeTabChange = { viewModel.repoTypeTab = it },
                onRepoSearchQueryChange = { viewModel.repoSearchQuery = it },
                onRepoSelected = { selectedRepo = it },
                onRemoveRepo = { viewModel.removeRepository(it.url) },
                onCopyLink = { viewModel.copyRepositoryLink(it) },
                onRefreshRepo = { viewModel.refreshRepository(it.url) },
                onRefreshAll = { viewModel.refreshAll() },
                onOpenAddDialog = { viewModel.openAddRepoDialog() },
                onOpenLocalFolder = { viewModel.openLocalFolder(it) }
            )

            selectedRepo?.let { currentRepo ->
                key(currentRepo.url) {
                    val rawPlugins = pluginsMap[currentRepo.url] ?: emptyList()
                    val sortedFilteredPlugins = viewModel.filterAndSortPlugins(rawPlugins)

                    PluginRepoDetails(
                        modifier = Modifier.weight(1f),
                        currentRepo = currentRepo,
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        searchQuery = viewModel.pluginSearchQuery,
                        onSearchQueryChange = { viewModel.pluginSearchQuery = it },
                        sortMode = viewModel.pluginSortMode,
                        onSortModeChange = { viewModel.pluginSortMode = it },
                        chipFilter = viewModel.pluginChipFilter,
                        onChipFilterChange = { viewModel.pluginChipFilter = it },
                        plugins = sortedFilteredPlugins,
                        flows = viewModel.filterAndSortFlows(flowsMap[currentRepo.url] ?: emptyList()),
                        installedPlugins = installedPlugins,
                        flowState = flowState,
                        activeJobs = activeJobs,
                        conflicts = conflicts,
                        packageSourceOverrides = packageSourceOverrides,
                        pluginsMap = pluginsMap,
                        isRefreshing = isRefreshing,
                        clipboard = clipboard,
                        onRefreshRepo = { viewModel.refreshRepository(it.url) },
                        onOpenLocalFolder = { viewModel.openLocalFolder(it) },
                        onInstallPlugin = { viewModel.installPlugin(it) },
                        onCancelPlugin = { viewModel.cancelPluginInstall(it) },
                        onInstallFlow = { viewModel.installFlow(it) },
                        onSetPackageSource = { pkg, url -> viewModel.setPackageSource(pkg, url) },
                        onShowChangelog = { viewModel.showChangelog(it) }
                    )
                }
            }
        }

        // Add Repository Verification Modal Dialog
        AddRepoDialog(
            state = viewModel.addRepoDialogState,
            onTargetChange = { viewModel.onAddRepoTargetChange(it) },
            onModeChange = { viewModel.onAddRepoModeChange(it) },
            onVerify = { viewModel.validateAddRepoTarget() },
            onBrowse = { viewModel.browseLocalIndexFile() },
            onConfirm = { viewModel.confirmAddRepository() },
            onDismiss = { viewModel.closeAddRepoDialog() }
        )
    }
}
