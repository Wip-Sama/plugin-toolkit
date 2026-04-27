package com.wip.kpm_cpm_wotoolkit.features.repository.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wip.kpm_cpm_wotoolkit.features.repository.viewmodel.ModuleRepoViewModel
import com.wip.kpm_cpm_wotoolkit.shared.components.settings.ExpressiveMenu
import com.wip.kpm_cpm_wotoolkit.shared.components.settings.SettingsGroup
import com.wip.kpm_cpm_wotoolkit.shared.components.settings.SettingsItem
import kpm_cpm_wotoolkit.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun ModuleRepoView(
    viewModel: ModuleRepoViewModel = koinInject()
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
                    placeholder = { Text("https://.../index.json") },
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
            val modulesMap by viewModel.modules.collectAsState()
            
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
                                Text(repo.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            }
                            Row {
                                IconButton(onClick = { viewModel.refreshRepository(repo.url) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                                }
                                IconButton(onClick = { viewModel.removeRepository(repo.url) }) {
                                    Icon(
                                        Icons.Default.Delete, 
                                        contentDescription = "Remove", 
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    val repoModules = modulesMap[repo.url] ?: emptyList()
                    items(repoModules) { module ->
                        val installedVersion = viewModel.getInstalledVersion(module.pkg)
                        val subtitle = if (installedVersion != null) {
                            "v${module.version} • Installed: v$installedVersion • ${module.pkg}"
                        } else {
                            "v${module.version} • ${module.pkg}"
                        }
                        
                        SettingsItem(
                            title = module.name,
                            subtitle = subtitle,
                            icon = Icons.Default.Extension,
                            control = {
                                val isInstalled = viewModel.isInstalled(module.pkg)
                                if (isInstalled) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            "Installed", 
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                } else {
                                    Button(onClick = { viewModel.installModule(module) }) {
                                        Text("Install")
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
