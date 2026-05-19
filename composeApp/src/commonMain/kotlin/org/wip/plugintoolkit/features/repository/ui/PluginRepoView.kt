package org.wip.plugintoolkit.features.repository.ui

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.repository.model.ExtensionRepo
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin
import org.wip.plugintoolkit.features.repository.model.ExtensionFlow
import org.wip.plugintoolkit.features.repository.viewmodel.PluginRepoViewModel
import org.wip.plugintoolkit.shared.components.settings.ExpressiveMenu
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_install
import plugintoolkit.composeapp.generated.resources.action_refresh
import plugintoolkit.composeapp.generated.resources.action_remove
import plugintoolkit.composeapp.generated.resources.plugin_installed_label
import plugintoolkit.composeapp.generated.resources.plugin_version_pkg_format
import plugintoolkit.composeapp.generated.resources.plugin_version_pkg_installed_format
import plugintoolkit.composeapp.generated.resources.repo_add_button
import plugintoolkit.composeapp.generated.resources.repo_add_title
import plugintoolkit.composeapp.generated.resources.repo_conflicts_subtitle
import plugintoolkit.composeapp.generated.resources.repo_conflicts_title
import plugintoolkit.composeapp.generated.resources.repo_empty_list
import plugintoolkit.composeapp.generated.resources.repo_managed_title
import plugintoolkit.composeapp.generated.resources.repo_refresh_all
import plugintoolkit.composeapp.generated.resources.repo_url_label
import plugintoolkit.composeapp.generated.resources.repo_url_placeholder

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
                .width(340.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
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
                OutlinedTextField(
                    value = viewModel.repoUrlInput,
                    onValueChange = { viewModel.repoUrlInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(Res.string.repo_url_placeholder), style = MaterialTheme.typography.bodyMedium) },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.repo_url_label)) },
                    shape = MaterialTheme.shapes.medium,
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                FilledIconButton(
                    onClick = { viewModel.addRepository() },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.size(52.dp)
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
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedRepo = repo },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        border = if (isSelected) {
                            CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                            )
                        } else null,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(
                            modifier = Modifier.padding(ToolkitTheme.spacing.medium)
                        ) {
                            Text(
                                text = repo.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = repo.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                val clipboardManager = LocalClipboardManager.current
                                IconButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(repo.url))
                                        viewModel.copyRepositoryLink(repo.url)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Share,
                                        contentDescription = "Share repository link",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.refreshRepository(repo.url) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = stringResource(Res.string.action_refresh),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.removeRepository(repo.url) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(Res.string.action_remove),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
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
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                Text(stringResource(Res.string.repo_refresh_all))
            }
        }

        // Divider
        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

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
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.padding(bottom = ToolkitTheme.spacing.medium)
                        ) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp).padding(ToolkitTheme.spacing.medium),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            text = "No Repository Selected",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                        Text(
                            text = "Select a repository from the left panel, or add a new repository URL at the top to explore its plugins and flows.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.width(420.dp)
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
                                style = MaterialTheme.typography.headlineMedium,
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
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
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
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
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
                    TabRow(
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
                                    "Plugins (${repoPlugins.size})",
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1; searchQuery = "" },
                            text = {
                                Text(
                                    "Flows (${repoFlows.size})",
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

                    // Search input within current list
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                if (selectedTab == 0) "Search plugins in this repository..." else "Search flows in this repository...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium
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
                                Text("No plugins found matching search query.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                items(filteredPlugins) { plugin ->
                                    val installedVersion = viewModel.getInstalledVersion(plugin.pkg)
                                    val isInstalled = viewModel.isInstalled(plugin.pkg)
                                    val activeJobs by viewModel.activePluginInstallationJobs.collectAsState()
                                    val progress = activeJobs[plugin.pkg]
                                    val hasUpdate = installedVersion != null && org.wip.plugintoolkit.core.utils.VersionUtils.compare(plugin.version, installedVersion) > 0

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.outlinedCardColors()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(ToolkitTheme.spacing.medium),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Plugin Icon
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.size(54.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.Extension,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(28.dp)
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
                                                                "Wrong Signature",
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onErrorContainer
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = "Package: ${plugin.pkg}  •  Version: ${plugin.version}",
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
                                                        modifier = Modifier.padding(top = 4.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))

                                            // Control
                                            if (progress != null) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                                                    CircularProgressIndicator(
                                                        progress = { progress },
                                                        modifier = Modifier.size(24.dp),
                                                        strokeWidth = 3.dp
                                                    )
                                                }
                                            } else if (isInstalled) {
                                                if (hasUpdate) {
                                                    Button(
                                                        onClick = { viewModel.installPlugin(plugin) },
                                                        shape = MaterialTheme.shapes.medium
                                                    ) {
                                                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Update to v${plugin.version}")
                                                    }
                                                } else {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        shape = MaterialTheme.shapes.medium
                                                    ) {
                                                        Text(
                                                            stringResource(Res.string.plugin_installed_label) + " (v$installedVersion)",
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
                                Text("No flows found matching search query.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium),
                                contentPadding = PaddingValues(bottom = 24.dp)
                            ) {
                                items(filteredFlows) { flow ->
                                    val isInstalled = viewModel.isFlowInstalled(flow.name)
                                    val installedVersion = viewModel.getInstalledFlowVersion(flow.name)
                                    val hasUpdate = viewModel.getFlowUpdate(flow)

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.outlinedCardColors()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(ToolkitTheme.spacing.medium),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Flow Icon
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.size(54.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.PlayCircle,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(28.dp)
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
                                                                "Wrong Signature",
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onErrorContainer
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = "FileName: ${flow.fileName}  •  Version: ${flow.version}",
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
                                                        modifier = Modifier.padding(top = 4.dp)
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
                                                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("Update to v${flow.version}")
                                                    }
                                                } else {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        shape = MaterialTheme.shapes.medium
                                                    ) {
                                                        Text(
                                                            "Installed (v$installedVersion)",
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
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
                                                    Text("Install")
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
