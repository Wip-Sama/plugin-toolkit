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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.viewmodel.FlowState
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.repository.model.ExtensionFlow
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin
import org.wip.plugintoolkit.features.repository.model.ExtensionRepo
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.repo_conflicts_title
import plugintoolkit.composeapp.generated.resources.repo_no_flows_found
import plugintoolkit.composeapp.generated.resources.repo_no_plugins_found
import plugintoolkit.composeapp.generated.resources.repo_search_flows_placeholder
import plugintoolkit.composeapp.generated.resources.repo_search_plugins_placeholder
import plugintoolkit.composeapp.generated.resources.repo_share_link_desc
import plugintoolkit.composeapp.generated.resources.repo_tab_flows
import plugintoolkit.composeapp.generated.resources.repo_tab_plugins
import plugintoolkit.composeapp.generated.resources.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginRepoDetails(
    currentRepo: ExtensionRepo,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    plugins: List<ExtensionPlugin>,
    flows: List<ExtensionFlow>,
    installedPlugins: List<InstalledPlugin>,
    flowState: FlowState,
    conflicts: Map<String, List<ExtensionRepo>>,
    isRefreshing: Boolean,
    activeJobs: Map<String, Float>,
    clipboard: androidx.compose.ui.platform.Clipboard,
    onInstallPlugin: (ExtensionPlugin) -> Unit,
    onCancelPlugin: (String) -> Unit,
    onInstallFlow: (ExtensionFlow) -> Unit,
    onSetPackageSource: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    Column(modifier = modifier.fillMaxHeight()) {
        // Repo Details Header
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = ToolkitTheme.dimensions.borderUnselected,
            shadowElevation = ToolkitTheme.dimensions.cardElevation,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(ToolkitTheme.spacing.large)) {
                Text(
                    text = currentRepo.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentRepo.url,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                    IconButton(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    org.wip.plugintoolkit.core.utils.PlatformUtils.clipEntryOf(
                                        currentRepo.url
                                    )
                                )
                            }
                        },
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconLarge)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = stringResource(Res.string.repo_share_link_desc),
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val activeConflicts = conflicts.filter { (_, repos) -> repos.any { it.url == currentRepo.url } }
                if (activeConflicts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
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

                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.large))

                // Tabs
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
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
            }
        }

        // Search Bar
        Box(modifier = Modifier.padding(ToolkitTheme.spacing.medium)) {
            ToolkitTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        stringResource(
                            if (selectedTab == 0) Res.string.repo_search_plugins_placeholder
                            else Res.string.repo_search_flows_placeholder
                        )
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
        }

        // Content
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (selectedTab == 0) {
                // Plugins List
                val filteredPlugins = plugins.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                            (it.description ?: "").contains(searchQuery, ignoreCase = true) ||
                            it.pkg.contains(searchQuery, ignoreCase = true)
                }

                if (filteredPlugins.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(Res.string.repo_no_plugins_found),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = ToolkitTheme.spacing.medium,
                            end = ToolkitTheme.spacing.medium,
                            bottom = ToolkitTheme.spacing.medium
                        ),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                    ) {
                        items(filteredPlugins) { plugin ->
                            PluginListItem(
                                plugin = plugin,
                                currentRepo = currentRepo,
                                installedPlugins = installedPlugins,
                                isRefreshing = isRefreshing,
                                activeJobs = activeJobs,
                                onSetPackageSource = onSetPackageSource,
                                conflicts = conflicts,
                                onInstall = onInstallPlugin,
                                onCancel = onCancelPlugin
                            )
                        }
                    }
                }
            } else {
                // Flows List
                val filteredFlows = flows.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                            (it.description ?: "").contains(searchQuery, ignoreCase = true) ||
                            it.fileName.contains(searchQuery, ignoreCase = true)
                }

                if (filteredFlows.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(Res.string.repo_no_flows_found),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = ToolkitTheme.spacing.medium,
                            end = ToolkitTheme.spacing.medium,
                            bottom = ToolkitTheme.spacing.medium
                        ),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                    ) {
                        items(filteredFlows) { flow ->
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
