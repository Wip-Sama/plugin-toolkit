package org.wip.plugintoolkit.features.repository.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.viewmodel.FlowState
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.repository.model.ExtensionFlow
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin
import org.wip.plugintoolkit.features.repository.model.ExtensionRepo
import org.wip.plugintoolkit.features.repository.viewmodel.PluginChipFilter
import org.wip.plugintoolkit.features.repository.viewmodel.PluginSortMode
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import org.wip.plugintoolkit.shared.components.settings.ExpressiveMenu
import org.wip.plugintoolkit.shared.components.verticalFadingEdges
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_refresh
import plugintoolkit.composeapp.generated.resources.repo_action_open_folder
import plugintoolkit.composeapp.generated.resources.repo_conflicts_title
import plugintoolkit.composeapp.generated.resources.repo_no_flows_found
import plugintoolkit.composeapp.generated.resources.repo_no_plugins_found
import plugintoolkit.composeapp.generated.resources.repo_search_flows_placeholder
import plugintoolkit.composeapp.generated.resources.repo_search_plugins_placeholder
import plugintoolkit.composeapp.generated.resources.repo_share_link_desc
import plugintoolkit.composeapp.generated.resources.repo_tab_flows
import plugintoolkit.composeapp.generated.resources.repo_tab_plugins
import plugintoolkit.composeapp.generated.resources.plugin_repo_multiple_warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginRepoDetails(
    currentRepo: ExtensionRepo,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    sortMode: PluginSortMode,
    onSortModeChange: (PluginSortMode) -> Unit,
    chipFilter: PluginChipFilter,
    onChipFilterChange: (PluginChipFilter) -> Unit,
    plugins: List<ExtensionPlugin>,
    flows: List<ExtensionFlow>,
    installedPlugins: List<InstalledPlugin>,
    flowState: FlowState,
    conflicts: Map<String, List<ExtensionRepo>>,
    isRefreshing: Boolean,
    activeJobs: Map<String, Float>,
    clipboard: androidx.compose.ui.platform.Clipboard,
    onRefreshRepo: (ExtensionRepo) -> Unit,
    onOpenLocalFolder: (String) -> Unit,
    onInstallPlugin: (ExtensionPlugin) -> Unit,
    onCancelPlugin: (String) -> Unit,
    onInstallFlow: (ExtensionFlow) -> Unit,
    onSetPackageSource: (String, String) -> Unit,
    onShowChangelog: (ExtensionPlugin) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val pluginsListState = rememberLazyListState()
    val flowsListState = rememberLazyListState()

    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(top = ToolkitTheme.spacing.large, bottom = ToolkitTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
    ) {
        // Repository Context Header Card
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = ToolkitTheme.shapes.large,
            tonalElevation = ToolkitTheme.dimensions.borderUnselected,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ToolkitTheme.spacing.large)
        ) {
            Column(
                modifier = Modifier.padding(ToolkitTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentRepo.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)) {
                        Button(
                            onClick = { onRefreshRepo(currentRepo) },
                            enabled = !isRefreshing,
                            shape = MaterialTheme.shapes.large,
                            colors = ButtonDefaults.filledTonalButtonColors()
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(stringResource(Res.string.action_refresh))
                        }

                        if (currentRepo.isLocal) {
                            IconButton(
                                onClick = { onOpenLocalFolder(currentRepo.url) },
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconLarge)
                            ) {
                                Icon(
                                    Icons.Default.FolderOpen,
                                    contentDescription = stringResource(Res.string.repo_action_open_folder),
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                                )
                            }
                        }
                    }
                }

                // URI Box
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = ToolkitTheme.spacing.medium,
                            vertical = ToolkitTheme.spacing.small
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (currentRepo.isLocal) Icons.Default.FolderOpen else Icons.Default.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                        )
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                        Text(
                            text = currentRepo.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        org.wip.plugintoolkit.core.utils.PlatformUtils.clipEntryOf(currentRepo.url)
                                    )
                                }
                            },
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumLarge)
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = stringResource(Res.string.repo_share_link_desc),
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Conflicts Warning Banner if any
                val activeConflicts = conflicts.filter { (_, repos) -> repos.any { it.url == currentRepo.url } }
                if (activeConflicts.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(ToolkitTheme.spacing.medium),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null)
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))
                            Column {
                                Text(
                                    stringResource(Res.string.repo_conflicts_title, activeConflicts.size),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    stringResource(Res.string.plugin_repo_multiple_warning),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        // Primary Tabs: Plugins vs Flows (touches both sides of the window!)
        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = ToolkitTheme.colors.transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                text = { Text(stringResource(Res.string.repo_tab_plugins, plugins.size)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                text = { Text(stringResource(Res.string.repo_tab_flows, flows.size)) }
            )
        }

        // Controls Toolbar: Full Width Search, Filter Chips & Sort Dropdown
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ToolkitTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
        ) {
            ToolkitTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        stringResource(
                            if (selectedTab == 0) Res.string.repo_search_plugins_placeholder
                            else Res.string.repo_search_flows_placeholder
                        ),
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PluginFilterChipsRow(
                    selectedFilter = chipFilter,
                    onFilterSelected = onChipFilterChange,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))

                SortDropdownChip(
                    sortMode = sortMode,
                    onSortModeChange = onSortModeChange
                )
            }
        }

        // List Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = ToolkitTheme.spacing.large)
        ) {
            if (selectedTab == 0) {
                if (plugins.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(Res.string.repo_no_plugins_found),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = pluginsListState,
                        modifier = Modifier
                            .verticalFadingEdges(
                                lazyListState = pluginsListState,
                                topFadeLength = ToolkitTheme.spacing.medium,
                                bottomFadeLength = ToolkitTheme.spacing.medium
                            )
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                    ) {
                        items(plugins) { plugin ->
                            PluginListItem(
                                plugin = plugin,
                                currentRepo = currentRepo,
                                installedPlugins = installedPlugins,
                                isRefreshing = isRefreshing,
                                activeJobs = activeJobs,
                                onSetPackageSource = onSetPackageSource,
                                conflicts = conflicts,
                                onInstall = onInstallPlugin,
                                onCancel = onCancelPlugin,
                                onShowChangelog = onShowChangelog
                            )
                        }
                    }
                }
            } else {
                if (flows.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(Res.string.repo_no_flows_found),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = flowsListState,
                        modifier = Modifier
                            .verticalFadingEdges(
                                lazyListState = flowsListState,
                                topFadeLength = ToolkitTheme.spacing.small,
                                bottomFadeLength = ToolkitTheme.spacing.small
                            )
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                    ) {
                        items(flows) { flow ->
                            FlowListItem(
                                flow = flow,
                                currentRepo = currentRepo,
                                flowState = flowState,
                                isRefreshing = isRefreshing,
                                onInstall = onInstallFlow
                            )
                        }
                    }
                }
            }
        }
    }
}
