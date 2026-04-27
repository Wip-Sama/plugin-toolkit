package com.wip.kpm_cpm_wotoolkit.features.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wip.kpm_cpm_wotoolkit.features.plugin.model.InstalledModule
import com.wip.kpm_cpm_wotoolkit.features.plugin.viewmodel.ModuleManagerViewModel
import com.wip.kpm_cpm_wotoolkit.shared.components.settings.SettingsGroup
import com.wip.kpm_cpm_wotoolkit.shared.components.settings.SettingsItem
import kpm_cpm_wotoolkit.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun ModuleManagerView(
    viewModel: ModuleManagerViewModel = koinInject()
) {
    val modules by viewModel.sortedModules.collectAsState()
    val loadedModules by viewModel.loadedModules.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { viewModel.refreshList() }) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(Res.string.module_refresh_list))
            }
            OutlinedButton(onClick = { viewModel.reloadAll() }) {
                Icon(Icons.Default.Replay, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(Res.string.module_reload_all))
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { viewModel.installLocal() }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(Res.string.module_install_local))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Module List
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(modules) { module ->
                ModuleCard(
                    module = module,
                    isLoaded = loadedModules.contains(module.pkg),
                    hasUpdate = viewModel.getUpdate(module.pkg) != null,
                    onToggle = { viewModel.toggleEnabled(module.pkg, it) },
                    onAction = { action ->
                        when (action) {
                            ModuleAction.Uninstall -> viewModel.uninstall(module.pkg)
                            ModuleAction.Reload -> viewModel.reload(module.pkg)
                            ModuleAction.Update -> viewModel.updateModule(module.pkg)
                            else -> {} // Stubs for Changelog, Settings, Validate
                        }
                    }
                )
            }
        }

        // Managed Folders Section
        SettingsGroup(title = stringResource(Res.string.module_managed_folders)) {
            val folders = viewModel.managedFolders.collectAsState().value
            folders.forEach { folder ->
                SettingsItem(
                    title = folder,
                    icon = Icons.Default.Folder,
                    control = {
                        IconButton(onClick = { viewModel.removeManagedFolder(folder) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                )
            }
            SettingsItem(
                title = stringResource(Res.string.module_add_folder),
                icon = Icons.Default.CreateNewFolder,
                onClick = { viewModel.addManagedFolder() }
            )
        }
    }
}

@Composable
fun ModuleCard(
    module: InstalledModule,
    isLoaded: Boolean,
    hasUpdate: Boolean,
    onToggle: (Boolean) -> Unit,
    onAction: (ModuleAction) -> Unit
) {
    val statusColor = if (isLoaded) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outline
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(if (isLoaded) Modifier.border(2.dp, statusColor, MaterialTheme.shapes.medium) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (isLoaded) statusColor.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(module.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (isLoaded) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = statusColor.copy(alpha = 0.2f),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                stringResource(Res.string.module_loaded),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor
                            )
                        }
                    }
                }
                Text("v${module.version} • ${module.pkg}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasUpdate) {
                    Button(
                        onClick = { onAction(ModuleAction.Update) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(stringResource(Res.string.module_update))
                    }
                }

                Switch(checked = module.isEnabled, onCheckedChange = onToggle)

                var expanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.module_reload)) },
                            onClick = { onAction(ModuleAction.Reload); expanded = false },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.module_validate)) },
                            onClick = { onAction(ModuleAction.Validate); expanded = false },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.module_changelog)) },
                            onClick = { onAction(ModuleAction.Changelog); expanded = false },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.module_settings)) },
                            onClick = { onAction(ModuleAction.Settings); expanded = false },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.module_uninstall), color = MaterialTheme.colorScheme.error) },
                            onClick = { onAction(ModuleAction.Uninstall); expanded = false },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                        )
                    }
                }
            }
        }
    }
}

enum class ModuleAction {
    Uninstall, Reload, Validate, Update, Changelog, Settings
}
