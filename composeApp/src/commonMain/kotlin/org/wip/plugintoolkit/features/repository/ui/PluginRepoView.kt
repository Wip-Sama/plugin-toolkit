package org.wip.plugintoolkit.features.repository.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import org.koin.compose.koinInject
import org.wip.plugintoolkit.features.repository.model.ExtensionRepo
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

    val installedPlugins by viewModel.installedPlugins.collectAsState()
    val flowState by viewModel.flowState.collectAsState()
    val activeJobs by viewModel.activePluginInstallationJobs.collectAsState()

    var selectedRepo by remember { mutableStateOf<ExtensionRepo?>(null) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Plugins, 1: Flows
    var searchQuery by remember { mutableStateOf("") }
    val clipboard = LocalClipboard.current

    // If selected repo is removed, reset selection
    LaunchedEffect(repositories) {
        if (selectedRepo != null && repositories.none { it.url == selectedRepo?.url }) {
            selectedRepo = null
        }
        if (selectedRepo == null && repositories.isNotEmpty()) {
            selectedRepo = repositories.first()
        }
    }

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PluginRepoSidebar(
            repositories = repositories,
            selectedRepo = selectedRepo,
            isRefreshing = isRefreshing,
            onRepoSelected = { selectedRepo = it },
            onRemoveRepo = { viewModel.removeRepository(it.url) },
            onCopyLink = { viewModel.copyRepositoryLink(it) },
            onRefreshRepo = { viewModel.refreshRepository(it.url) },
            onRefreshAll = { viewModel.refreshAll() },
            repoUrlInput = viewModel.repoUrlInput,
            onRepoUrlInputChange = { viewModel.repoUrlInput = it },
            onAddRepository = { viewModel.addRepository() }
        )

        selectedRepo?.let { currentRepo ->
            PluginRepoDetails(
                modifier = Modifier.weight(1f),
                currentRepo = currentRepo,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                plugins = pluginsMap[currentRepo.url] ?: emptyList(),
                flows = flowsMap[currentRepo.url] ?: emptyList(),
                installedPlugins = installedPlugins,
                flowState = flowState,
                activeJobs = activeJobs,
                conflicts = conflicts,
                isRefreshing = isRefreshing,
                clipboard = clipboard,
                onInstallPlugin = { viewModel.installPlugin(it) },
                onCancelPlugin = { viewModel.cancelPluginInstall(it) },
                onInstallFlow = { viewModel.installFlow(it) },
                onSetPackageSource = { pkg, url -> viewModel.setPackageSource(pkg, url) }
            )
        }
    }
}
