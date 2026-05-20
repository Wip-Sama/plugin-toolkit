package org.wip.plugintoolkit.features.repository.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import org.wip.plugintoolkit.core.utils.PlatformUtils
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.shared.components.GlassCard
import org.wip.plugintoolkit.features.repository.model.ExtensionRepo
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin
import org.wip.plugintoolkit.features.repository.model.ExtensionFlow
import org.wip.plugintoolkit.features.repository.viewmodel.PluginRepoViewModel
import org.wip.plugintoolkit.shared.components.settings.ExpressiveMenu
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import plugintoolkit.composeapp.generated.resources.*

@Composable
fun PluginRepoView(
    viewModel: PluginRepoViewModel = koinInject()
) {
    val repositories by viewModel.repositories.collectAsState()
    val conflicts by viewModel.conflicts.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pluginsMap by viewModel.plugins.collectAsState()
    val flowsMap by viewModel.flows.collectAsState()

    var selectedRepo by remember { mutableStateOf<ExtensionRepo?>(null) }
    var selectedTab by remember { mutableStateOf(0) } // 0: Plugins, 1: Flows
    var searchQuery by remember { mutableStateOf("") }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

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
        // LEFT COLUMN - Repositories Sidebar
        Column(
            modifier = Modifier
                .width(ToolkitTheme.dimensions.repositorySidebarWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.sidebarBackground))
                .padding(ToolkitTheme.spacing.medium)
        ) {
            Text(
                text = stringResource(Res.string.repo_managed_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = ToolkitTheme.spacing.small)
            )

            // Add Repository Input & Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolkitTextField(
                    value = viewModel.repoUrlInput,
                    onValueChange = { viewModel.repoUrlInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(Res.string.repo_url_placeholder), style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                FilledIconButton(
                    onClick = { viewModel.addRepository() },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(ToolkitTheme.dimensions.textFieldHeight)
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.repo_add_button))
                }
            }

            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

            // Repository List
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
            ) {
                items(repositories) { repo ->
                    val isSelected = selectedRepo?.url == repo.url
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { selectedRepo = repo },
                        colors = CardDefaults.outlinedCardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = ToolkitTheme.opacity.cardBackground)
                            } else {
                                Color.Transparent
                            }
                        ),
                        border = BorderStroke(
                            width = if (isSelected) ToolkitTheme.dimensions.borderSelected else ToolkitTheme.dimensions.borderUnselected,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.sidebarBackground)
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = ToolkitTheme.spacing.medium, vertical = ToolkitTheme.spacing.small),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = repo.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                               )
                                Text(
                                    text = repo.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = ToolkitTheme.opacity.secondaryText),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            clipboard.setClipEntry(PlatformUtils.clipEntryOf(repo.url))
                                        }
                                        viewModel.copyRepositoryLink(repo.url)
                                    },
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconLarge)
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = stringResource(Res.string.repo_share_link_desc),
                                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.refreshRepository(repo.url) },
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconLarge)
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = stringResource(Res.string.action_refresh),
                                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.removeRepository(repo.url) },
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconLarge)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(Res.string.action_remove),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))

            // Refresh All Button
            Button(
                onClick = { viewModel.refreshAll() },
                enabled = !isRefreshing,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall),
                        strokeWidth = ToolkitTheme.dimensions.progressIndicatorStroke,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall))
                }
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                Text(stringResource(Res.string.repo_refresh_all))
            }
        }

        // Divider
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider))

        // RIGHT COLUMN - Repository Inspector / Details View
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(ToolkitTheme.spacing.large)
        ) {
            val currentRepo = selectedRepo
            if (currentRepo == null) {
                // Empty state when no repository is selected or empty list
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(ToolkitTheme.spacing.extraLarge)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.sidebarBackground),
                            modifier = Modifier.padding(bottom = ToolkitTheme.spacing.medium)
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(ToolkitTheme.dimensions.emptyStateIconSize).padding(ToolkitTheme.spacing.medium),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = ToolkitTheme.opacity.disabled)
                            )
                        }
                        Text(
                            text = stringResource(Res.string.repo_no_repo_selected),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                        Text(
                            text = stringResource(Res.string.repo_no_repo_selected_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.width(ToolkitTheme.dimensions.emptyStateTextWidth)
                        )
                    }
                }
            } else {
                // Details Panel for Selected Repository
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = currentRepo.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = currentRepo.url,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

                    // Conflicts Banner (Global, shown if package matches selected repo)
                    val activeConflicts = conflicts.filter { (_, repos) -> repos.any { it.url == currentRepo.url } }
                    if (activeConflicts.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = ToolkitTheme.opacity.divider)
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().padding(bottom = ToolkitTheme.spacing.medium)
                        ) {
                            Column(modifier = Modifier.padding(ToolkitTheme.spacing.medium)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                                    Text(
                                        text = stringResource(Res.string.repo_conflicts_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                                activeConflicts.forEach { (pkg, repos) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = ToolkitTheme.spacing.extraSmall),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = pkg,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        ExpressiveMenu(
                                            options = repos,
                                            selectedOption = viewModel.getSelectedRepoForPackage(pkg, repos),
                                            onOptionSelected = { viewModel.setPackageSource(pkg, it.url) },
                                            labelProvider = { it.name }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    val repoPlugins = pluginsMap[currentRepo.url] ?: emptyList()
                    val repoFlows = flowsMap[currentRepo.url] ?: emptyList()

                    // Tab Selection (Plugins vs Flows)
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0; searchQuery = "" },
                            text = {
                                Text(
                                    stringResource(Res.string.repo_tab_plugins, repoPlugins.size),
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1; searchQuery = "" },
                            text = {
                                Text(
                                    stringResource(Res.string.repo_tab_flows, repoFlows.size),
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

                    // Search input within current list
                    ToolkitTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                if (selectedTab == 0) stringResource(Res.string.repo_search_plugins_placeholder) else stringResource(Res.string.repo_search_flows_placeholder),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

                    // Content List
                    if (selectedTab == 0) {
                        // PLUGINS LIST
                        val filteredPlugins = repoPlugins.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                                    it.pkg.contains(searchQuery, ignoreCase = true) ||
                                    (it.description?.contains(searchQuery, ignoreCase = true) ?: false)
                        }

                        if (filteredPlugins.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(Res.string.repo_no_plugins_found), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium),
                                contentPadding = PaddingValues(bottom = ToolkitTheme.spacing.large)
                            ) {
                                items(filteredPlugins) { plugin ->
                                    val installedVersion = viewModel.getInstalledVersion(plugin.pkg)
                                    val isInstalled = viewModel.isInstalled(plugin.pkg)
                                    val activeJobs by viewModel.activePluginInstallationJobs.collectAsState()
                                    val progress = activeJobs[plugin.pkg]
                                    val hasUpdate = installedVersion != null && org.wip.plugintoolkit.core.utils.VersionUtils.compare(plugin.version, installedVersion) > 0

                                    GlassCard(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(ToolkitTheme.spacing.medium),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Plugin Icon
                                            Surface(
                                                shape = MaterialTheme.shapes.medium,
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.size(ToolkitTheme.dimensions.listIconSize)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.Extension,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(ToolkitTheme.dimensions.listIconContentSize)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))

                                            // Plugin Information
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = plugin.name,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    if (plugin.isSignatureValid == false) {
                                                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                                                        Surface(
                                                            color = MaterialTheme.colorScheme.errorContainer,
                                                            shape = MaterialTheme.shapes.extraSmall
                                                        ) {
                                                            Text(
                                                                stringResource(Res.string.repo_wrong_signature),
                                                                modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.badgeHorizontal, vertical = ToolkitTheme.spacing.badgeVertical),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onErrorContainer
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = stringResource(Res.string.repo_plugin_pkg_version_format, plugin.pkg, plugin.version),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                                if (!plugin.description.isNullOrEmpty()) {
                                                    Text(
                                                        text = plugin.description,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(top = ToolkitTheme.spacing.extraSmall)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))

                                            // Control
                                            if (progress != null) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ToolkitTheme.dimensions.progressBoxSize)) {
                                                    CircularProgressIndicator(
                                                        progress = { progress },
                                                        modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium),
                                                        strokeWidth = ToolkitTheme.dimensions.progressIndicatorStrokeMedium
                                                    )
                                                }
                                            } else if (isInstalled) {
                                                if (hasUpdate) {
                                                    Button(
                                                        onClick = { viewModel.installPlugin(plugin) },
                                                        shape = MaterialTheme.shapes.medium
                                                    ) {
                                                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall))
                                                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                                                        Text(stringResource(Res.string.repo_action_update_version, plugin.version))
                                                    }
                                                } else {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        shape = MaterialTheme.shapes.medium
                                                    ) {
                                                        Text(
                                                            stringResource(Res.string.repo_plugin_installed_version_format, installedVersion ?: ""),
                                                            modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.mediumSmall, vertical = ToolkitTheme.spacing.badgeHorizontal),
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            } else {
                                                Button(
                                                    onClick = { viewModel.installPlugin(plugin) },
                                                    shape = MaterialTheme.shapes.medium
                                                ) {
                                                    Text(stringResource(Res.string.action_install))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // FLOWS LIST
                        val filteredFlows = repoFlows.filter {
                            it.name.contains(searchQuery, ignoreCase = true) ||
                                    (it.description?.contains(searchQuery, ignoreCase = true) ?: false)
                        }

                        if (filteredFlows.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(Res.string.repo_no_flows_found), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium),
                                contentPadding = PaddingValues(bottom = ToolkitTheme.spacing.large)
                            ) {
                                items(filteredFlows) { flow ->
                                    val isInstalled = viewModel.isFlowInstalled(flow.name)
                                    val installedVersion = viewModel.getInstalledFlowVersion(flow.name)
                                    val hasUpdate = viewModel.getFlowUpdate(flow)

                                    GlassCard(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(ToolkitTheme.spacing.medium),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Flow Icon
                                            Surface(
                                                shape = MaterialTheme.shapes.medium,
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.size(ToolkitTheme.dimensions.listIconSize)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.PlayCircle,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(ToolkitTheme.dimensions.listIconContentSize)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))

                                            // Flow Information
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = flow.name,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    if (flow.isSignatureValid == false) {
                                                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                                                        Surface(
                                                            color = MaterialTheme.colorScheme.errorContainer,
                                                            shape = MaterialTheme.shapes.extraSmall
                                                        ) {
                                                            Text(
                                                                stringResource(Res.string.repo_wrong_signature),
                                                                modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.badgeHorizontal, vertical = ToolkitTheme.spacing.badgeVertical),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onErrorContainer
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = stringResource(Res.string.repo_flow_filename_version_format, flow.fileName, flow.version),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                                if (!flow.description.isNullOrEmpty()) {
                                                    Text(
                                                        text = flow.description,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.padding(top = ToolkitTheme.spacing.extraSmall)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))

                                            // Control
                                            if (isInstalled) {
                                                if (hasUpdate) {
                                                    Button(
                                                        onClick = { viewModel.installFlow(flow) },
                                                        shape = MaterialTheme.shapes.medium
                                                    ) {
                                                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall))
                                                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                                                        Text(stringResource(Res.string.repo_action_update_version, flow.version))
                                                    }
                                                } else {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        shape = MaterialTheme.shapes.medium
                                                    ) {
                                                        Text(
                                                            stringResource(Res.string.repo_plugin_installed_version_format, installedVersion ?: ""),
                                                            modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.mediumSmall, vertical = ToolkitTheme.spacing.badgeHorizontal),
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            } else {
                                                Button(
                                                    onClick = { viewModel.installFlow(flow) },
                                                    shape = MaterialTheme.shapes.medium
                                                ) {
                                                    Text(stringResource(Res.string.action_install))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
