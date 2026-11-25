package org.wip.plugintoolkit.features.repository.ui.components

import androidx.compose.material3.AppBarRow
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import org.wip.plugintoolkit.shared.components.AdaptiveAppBarOverflowButton
import org.wip.plugintoolkit.shared.components.AdaptivePaneHeader
import org.wip.plugintoolkit.shared.components.adaptiveOuterHorizontalPadding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.ButtonGroup
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
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.flows.viewmodel.FlowState
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.repository.model.ExtensionFlow
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin
import org.wip.plugintoolkit.features.repository.model.ExtensionRepo
import org.wip.plugintoolkit.features.repository.model.PluginInstallationJobState
import org.wip.plugintoolkit.features.repository.viewmodel.PluginChipFilter
import org.wip.plugintoolkit.features.repository.viewmodel.PluginSortMode
import org.wip.plugintoolkit.shared.components.ToolkitButtonGroup
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import org.wip.plugintoolkit.shared.components.settings.ExpressiveMenu
import org.wip.plugintoolkit.shared.components.tooltip
import org.wip.plugintoolkit.shared.components.verticalFadingEdges
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_refresh
import plugintoolkit.composeapp.generated.resources.plugin_open_folder
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
    packageSourceOverrides: Map<String, String> = emptyMap(),
    pluginsMap: Map<String, List<ExtensionPlugin>> = emptyMap(),
    isRefreshing: Boolean,
    activeJobs: Map<String, PluginInstallationJobState>,
    clipboard: Clipboard,
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
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = ToolkitTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = adaptiveOuterHorizontalPadding())
        ) {
            Column(
                modifier = Modifier.padding(bottom = ToolkitTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
            ) {
                val activeConflicts = conflicts.filter { (_, repos) -> repos.any { it.url == currentRepo.url } }
                AdaptivePaneHeader(
                    title = currentRepo.name,
                    subtitle = if (activeConflicts.isNotEmpty()) {
                        stringResource(Res.string.plugin_repo_multiple_warning)
                    } else null,
                    actions = {
                        key(currentRepo.url, currentRepo.isLocal) {
                            val refreshLabel = stringResource(Res.string.action_refresh)
                            val openFolderLabel = stringResource(Res.string.plugin_open_folder)
                            AppBarRow(
                                maxItemCount = 2,
                                overflowIndicator = { menuState ->
                                    AdaptiveAppBarOverflowButton(menuState = menuState)
                                }
                            ) {
                                clickableItem(
                                    onClick = { onRefreshRepo(currentRepo) },
                                    enabled = !isRefreshing,
                                    icon = {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = refreshLabel,
                                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                        )
                                    },
                                    label = refreshLabel
                                )
                                if (currentRepo.isLocal) {
                                    clickableItem(
                                        onClick = { onOpenLocalFolder(currentRepo.url) },
                                        icon = {
                                            Icon(
                                                Icons.Default.FolderOpen,
                                                contentDescription = openFolderLabel,
                                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                            )
                                        },
                                        label = openFolderLabel
                                    )
                                }
                            }
                        }
                    }
                )
                Column(
                    modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.large),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
                ) {

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
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        PlatformUtils.clipEntryOf(currentRepo.url)
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
//                val activeConflicts = conflicts.filter { (_, repos) -> repos.any { it.url == currentRepo.url } }
//                if (activeConflicts.isNotEmpty()) {
//                    Card(
//                        colors = CardDefaults.cardColors(
//                            containerColor = MaterialTheme.colorScheme.errorContainer,
//                            contentColor = MaterialTheme.colorScheme.onErrorContainer
//                        ),
//                        shape = MaterialTheme.shapes.medium
//                    ) {
//                        Row(
//                            modifier = Modifier.padding(ToolkitTheme.spacing.medium),
//                            verticalAlignment = Alignment.CenterVertically
//                        ) {
//                            Icon(Icons.Default.Warning, contentDescription = null)
//                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))
//                            Column {
//                                Text(
//                                    stringResource(Res.string.repo_conflicts_title, activeConflicts.size),
//                                    fontWeight = FontWeight.Bold
//                                )
//                                Text(
//                                    stringResource(Res.string.plugin_repo_multiple_warning),
//                                    style = MaterialTheme.typography.bodySmall
//                                )
//                            }
//                        }
//                    }
//                }
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
                text = {
                    Text(
                        text = stringResource(Res.string.repo_tab_plugins, plugins.size),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                text = {
                    Text(
                        text = stringResource(Res.string.repo_tab_flows, flows.size),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                PluginFilterChipsRow(
                    selectedFilter = chipFilter,
                    onFilterSelected = onChipFilterChange,
                    modifier = Modifier.weight(1f, fill = false)
                )

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
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
                    ) {
                        items(plugins.distinctBy { it.pkg }, key = { it.pkg }) { plugin ->
                            PluginListItem(
                                plugin = plugin,
                                currentRepo = currentRepo,
                                installedPlugins = installedPlugins,
                                isRefreshing = isRefreshing,
                                activeJobs = activeJobs,
                                onSetPackageSource = onSetPackageSource,
                                conflicts = conflicts,
                                packageSourceOverrides = packageSourceOverrides,
                                pluginsMap = pluginsMap,
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
                                topFadeLength = ToolkitTheme.spacing.medium,
                                bottomFadeLength = ToolkitTheme.spacing.medium
                            )
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
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
