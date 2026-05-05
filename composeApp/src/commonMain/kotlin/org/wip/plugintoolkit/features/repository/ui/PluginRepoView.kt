package org.wip.plugintoolkit.features.repository.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.features.repository.viewmodel.PluginRepoViewModel
import org.wip.plugintoolkit.shared.components.settings.ExpressiveMenu
import org.wip.plugintoolkit.shared.components.settings.SettingsGroup
import org.wip.plugintoolkit.shared.components.settings.SettingsItem
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
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun PluginRepoView(
    viewModel: PluginRepoViewModel = koinInject()
) {
    val repositories by viewModel.repositories.collectAsState()
    val conflicts by viewModel.conflicts.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Add Repository Section
        SettingsGroup(title = stringResource(Res.string.repo_add_title)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = viewModel.repoUrlInput,
                    onValueChange = { viewModel.repoUrlInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(Res.string.repo_url_placeholder)) },
                    singleLine = true,
                    label = { Text(stringResource(Res.string.repo_url_label)) },
                    shape = MaterialTheme.shapes.medium
                )
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { viewModel.addRepository() },
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(Res.string.repo_add_button))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Conflicts Section
        if (conflicts.isNotEmpty()) {
            SettingsGroup(title = stringResource(Res.string.repo_conflicts_title)) {
                conflicts.forEach { (pkg, repos) ->
                    SettingsItem(
                        title = pkg,
                        subtitle = stringResource(Res.string.repo_conflicts_subtitle),
                        icon = Icons.Default.Warning,
                        control = {
                            ExpressiveMenu(
                                options = repos,
                                selectedOption = viewModel.getSelectedRepoForPackage(pkg, repos),
                                onOptionSelected = { viewModel.setPackageSource(pkg, it.url) },
                                labelProvider = { it.name }
                            )
                        }
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Repository List Section
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                stringResource(Res.string.repo_managed_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(
                onClick = { viewModel.refreshAll() },
                enabled = !isRefreshing,
                shape = MaterialTheme.shapes.small
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(Res.string.repo_refresh_all))
            }
        }

        if (repositories.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Inventory,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outlineVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(Res.string.repo_empty_list),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            val pluginsMap by viewModel.plugins.collectAsState()

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                repositories.forEach { repo ->
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(repo.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    repo.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Row {
                                IconButton(onClick = { viewModel.refreshRepository(repo.url) }) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = stringResource(Res.string.action_refresh)
                                    )
                                }
                                IconButton(onClick = { viewModel.removeRepository(repo.url) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(Res.string.action_remove),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    val repoPlugins = pluginsMap[repo.url] ?: emptyList()
                    items(repoPlugins) { plugin ->
                        val installedVersion = viewModel.getInstalledVersion(plugin.pkg)
                        val subtitle = if (installedVersion != null) {
                            stringResource(
                                Res.string.plugin_version_pkg_installed_format,
                                plugin.version,
                                installedVersion,
                                plugin.pkg
                            )
                        } else {
                            stringResource(Res.string.plugin_version_pkg_format, plugin.version, plugin.pkg)
                        }

                        SettingsItem(
                            title = plugin.name,
                            subtitle = subtitle,
                            icon = Icons.Default.Extension,
                            control = {
                                val isInstalled = viewModel.isInstalled(plugin.pkg)
                                if (isInstalled) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            stringResource(Res.string.plugin_installed_label),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                } else {
                                    Button(onClick = { viewModel.installPlugin(plugin) }) {
                                        Text(stringResource(Res.string.action_install))
                                    }
                                }
                            }
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}
