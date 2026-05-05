package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.plugin.model.InstalledPlugin
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginManagerViewModel
import org.wip.plugintoolkit.shared.components.settings.SettingsGroup
import org.wip.plugintoolkit.shared.components.settings.SettingsItem
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_more_actions
import plugintoolkit.composeapp.generated.resources.action_remove
import plugintoolkit.composeapp.generated.resources.plugin_add_folder
import plugintoolkit.composeapp.generated.resources.plugin_changelog
import plugintoolkit.composeapp.generated.resources.plugin_default_folder_label
import plugintoolkit.composeapp.generated.resources.plugin_default_tag
import plugintoolkit.composeapp.generated.resources.plugin_install_local
import plugintoolkit.composeapp.generated.resources.plugin_loaded
import plugintoolkit.composeapp.generated.resources.plugin_managed_folders
import plugintoolkit.composeapp.generated.resources.plugin_refresh_list
import plugintoolkit.composeapp.generated.resources.plugin_reload
import plugintoolkit.composeapp.generated.resources.plugin_reload_all
import plugintoolkit.composeapp.generated.resources.plugin_settings
import plugintoolkit.composeapp.generated.resources.plugin_uninstall
import plugintoolkit.composeapp.generated.resources.plugin_update
import plugintoolkit.composeapp.generated.resources.plugin_update_local
import plugintoolkit.composeapp.generated.resources.plugin_validate
import plugintoolkit.composeapp.generated.resources.plugin_version_pkg_format
import plugintoolkit.composeapp.generated.resources.plugin_validated
import plugintoolkit.composeapp.generated.resources.plugin_validation_pending
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun PluginManagerView(
    viewModel: PluginManagerViewModel = koinInject()
) {
    val plugins by viewModel.sortedPlugins.collectAsState()
    val loadedPlugins by viewModel.loadedPlugins.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(ToolkitTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
        ) {
            Button(onClick = { viewModel.refreshList() }) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                Text(stringResource(Res.string.plugin_refresh_list))
            }
            OutlinedButton(onClick = { viewModel.reloadAll() }) {
                Icon(Icons.Default.Replay, contentDescription = null)
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                Text(stringResource(Res.string.plugin_reload_all))
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { viewModel.installLocal() }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                Text(stringResource(Res.string.plugin_install_local))
            }
        }

        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

        // Plugin List
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = ToolkitTheme.spacing.medium, vertical = ToolkitTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.mediumSmall)
        ) {
            items(plugins) { plugin ->
                PluginCard(
                    plugin = plugin,
                    isLoaded = loadedPlugins.contains(plugin.pkg),
                    hasUpdate = viewModel.getUpdate(plugin.pkg) != null,
                    onToggle = { viewModel.toggleEnabled(plugin.pkg, it) },
                    onAction = { action ->
                        when (action) {
                            PluginAction.Uninstall -> viewModel.uninstall(plugin.pkg)
                            PluginAction.Reload -> viewModel.reload(plugin.pkg)
                            PluginAction.Update -> viewModel.updatePlugin(plugin.pkg)
                            PluginAction.Validate -> viewModel.validatePlugin(plugin.pkg)
                            PluginAction.Changelog -> viewModel.showChangelog(plugin.pkg)
                            PluginAction.Settings -> viewModel.openSettings(plugin.pkg)
                        }
                    }
                )
            }
        }

        // Managed Folders Section
        SettingsGroup(
            title = stringResource(Res.string.plugin_managed_folders),
            collapsible = true,
            initialExpanded = false
        ) {
            val folders = viewModel.managedFolders.collectAsState().value
            val defaultFolder = viewModel.defaultPluginFolder

            folders.forEach { folder ->
                val isDefault = folder == defaultFolder
                SettingsItem(
                    title = folder,
                    subtitle = if (isDefault) stringResource(Res.string.plugin_default_folder_label) else null,
                    icon = Icons.Default.Folder,
                    control = {
                        if (isDefault) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Text(
                                    stringResource(Res.string.plugin_default_tag),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            IconButton(onClick = { viewModel.removeManagedFolder(folder) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(Res.string.action_remove),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                )
            }
            SettingsItem(
                title = stringResource(Res.string.plugin_add_folder),
                icon = Icons.Default.CreateNewFolder,
                onClick = { viewModel.addManagedFolder() }
            )
        }
    }
}

@Composable
fun PluginCard(
    plugin: InstalledPlugin,
    isLoaded: Boolean,
    hasUpdate: Boolean,
    onToggle: (Boolean) -> Unit,
    onAction: (PluginAction) -> Unit
) {
    val statusColor = if (isLoaded) ToolkitTheme.colors.success else if (plugin.isValidated) ToolkitTheme.colors.validated else MaterialTheme.colorScheme.outline

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .then(
                if (isLoaded) Modifier.border(
                    ToolkitTheme.dimensions.cardElevation,
                    statusColor,
                    MaterialTheme.shapes.medium
                ) else Modifier
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isLoaded) statusColor.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(ToolkitTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Placeholder
            Box(
                modifier = Modifier
                    .size(ToolkitTheme.dimensions.pluginIcon)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Extension,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(plugin.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (isLoaded) {
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                        StatusBadge(stringResource(Res.string.plugin_loaded), ToolkitTheme.colors.success)
                    }
                    if (plugin.isValidated) {
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                        StatusBadge(stringResource(Res.string.plugin_validated), ToolkitTheme.colors.validated)
                    } else {
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                        StatusBadge(stringResource(Res.string.plugin_validation_pending), MaterialTheme.colorScheme.error)
                    }
                }
                Text(
                    stringResource(Res.string.plugin_version_pkg_format, plugin.version, plugin.pkg),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasUpdate) {
                    Button(
                        onClick = { onAction(PluginAction.Update) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.padding(end = ToolkitTheme.spacing.small)
                    ) {
                        Text(stringResource(Res.string.plugin_update))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onAction(PluginAction.Update) },
                        modifier = Modifier.padding(end = ToolkitTheme.spacing.small)
                    ) {
                        Text(stringResource(Res.string.plugin_update_local))
                    }
                }

                Switch(checked = plugin.isEnabled, onCheckedChange = onToggle)

                var expanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { expanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(Res.string.action_more_actions)
                        )
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.plugin_reload)) },
                            onClick = { onAction(PluginAction.Reload); expanded = false },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.plugin_validate)) },
                            onClick = { onAction(PluginAction.Validate); expanded = false },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.plugin_changelog)) },
                            onClick = { onAction(PluginAction.Changelog); expanded = false },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.plugin_settings)) },
                            onClick = { onAction(PluginAction.Settings); expanded = false },
                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(Res.string.plugin_uninstall),
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = { onAction(PluginAction.Uninstall); expanded = false },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
@Composable
private fun StatusBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.2f), MaterialTheme.shapes.extraSmall)
    ) {
        Text(
            text,
            modifier = Modifier.padding(
                horizontal = ToolkitTheme.spacing.extraSmall,
                vertical = ToolkitTheme.spacing.extraSmall / 2
            ),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

enum class PluginAction {
    Uninstall, Reload, Validate, Update, Changelog, Settings
}
