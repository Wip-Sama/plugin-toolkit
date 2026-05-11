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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wip.plugintoolkit.api.PluginAction
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
import plugintoolkit.composeapp.generated.resources.plugin_broken
import plugintoolkit.composeapp.generated.resources.plugin_loaded
import plugintoolkit.composeapp.generated.resources.plugin_managed_folders
import plugintoolkit.composeapp.generated.resources.plugin_refresh_list
import plugintoolkit.composeapp.generated.resources.plugin_reload
import plugintoolkit.composeapp.generated.resources.plugin_reload_all
import plugintoolkit.composeapp.generated.resources.plugin_rescan
import plugintoolkit.composeapp.generated.resources.plugin_settings
import plugintoolkit.composeapp.generated.resources.plugin_uninstall
import plugintoolkit.composeapp.generated.resources.plugin_update
import plugintoolkit.composeapp.generated.resources.plugin_update_local
import plugintoolkit.composeapp.generated.resources.plugin_validate
import plugintoolkit.composeapp.generated.resources.plugin_validated
import plugintoolkit.composeapp.generated.resources.plugin_validation_pending
import plugintoolkit.composeapp.generated.resources.plugin_version_pkg_format

@Composable
fun PluginManagerView(
    viewModel: PluginManagerViewModel = koinInject()
) {
    val plugins by viewModel.sortedPlugins.collectAsState()
    val loadedPlugins by viewModel.loadedPlugins.collectAsState()
    val isReady by viewModel.isRegistryReady.collectAsState()
    val settingsPkg by viewModel.settingsPkg.collectAsState()

    if (settingsPkg != null) {
        PluginSettingsDialog(
            pkg = settingsPkg!!,
            onDismiss = { viewModel.closeSettings() }
        )
    }

    val showRemoteInstall by viewModel.showRemoteInstall.collectAsState()
    if (showRemoteInstall) {
        val availablePlugins by viewModel.availableRemotePlugins.collectAsState()
        val installedPlugins by viewModel.installedPlugins.collectAsState()
        val activeJobs by viewModel.activePluginInstallationJobs.collectAsState()
        
        RemotePluginInstallDialog(
            availablePlugins = availablePlugins,
            installedPackageNames = installedPlugins.map { it.pkg }.toSet(),
            activeJobs = activeJobs,
            onInstall = { viewModel.installRemote(it) },
            onDismiss = { viewModel.closeRemoteInstall() }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isReady) {
            Box(
                modifier = Modifier.fillMaxWidth().height(ToolkitTheme.spacing.extraSmall),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        // Toolbar
        Row(
            modifier = Modifier.fillMaxWidth().padding(ToolkitTheme.spacing.small),
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
        ) {
            Button(onClick = { viewModel.refreshList() }, enabled = isReady) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                Text(stringResource(Res.string.plugin_refresh_list))
            }
            OutlinedButton(onClick = { viewModel.rescan() }, enabled = isReady) {
                Icon(Icons.Default.Folder, contentDescription = null)
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                Text(stringResource(Res.string.plugin_rescan))
            }
            OutlinedButton(onClick = { viewModel.reloadAll() }, enabled = isReady) {
                Icon(Icons.Default.Replay, contentDescription = null)
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                Text(stringResource(Res.string.plugin_reload_all))
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = { viewModel.openRemoteInstall() }, enabled = isReady) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                Text("Install Remote")
            }
            Button(onClick = { viewModel.installLocal() }, enabled = isReady) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                Text(stringResource(Res.string.plugin_install_local))
            }
        }

        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

        // Plugin List
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(
                horizontal = ToolkitTheme.spacing.medium,
                vertical = ToolkitTheme.spacing.small
            ),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.mediumSmall)
        ) {
            items(plugins) { plugin ->
                PluginCard(
                    plugin = plugin,
                    isLoaded = loadedPlugins.contains(plugin.pkg),
                    hasUpdate = viewModel.getUpdate(plugin.pkg) != null,
                    customActions = viewModel.getActions(plugin.pkg),
                    enabled = isReady,
                    onToggle = { if (isReady) viewModel.toggleEnabled(plugin.pkg, it) },
                    onAction = { action ->
                        if (isReady) {
                            when (action) {
                                PluginStatusAction.Uninstall -> viewModel.uninstall(plugin.pkg)
                                PluginStatusAction.Reload -> viewModel.reload(plugin.pkg)
                                PluginStatusAction.Update -> viewModel.updatePlugin(plugin.pkg)
                                PluginStatusAction.Validate -> viewModel.validatePlugin(plugin.pkg)
                                PluginStatusAction.RerunSetup -> viewModel.rerunSetup(plugin.pkg)
                                PluginStatusAction.Changelog -> viewModel.showChangelog(plugin.pkg)
                                PluginStatusAction.Settings -> viewModel.openSettings(plugin.pkg)
                                is PluginStatusAction.Custom -> viewModel.runAction(plugin.pkg, action.name)
                            }
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
                                modifier = Modifier.padding(end = ToolkitTheme.spacing.small)
                            ) {
                                Text(
                                    stringResource(Res.string.plugin_default_tag),
                                    modifier = Modifier.padding(
                                        horizontal = ToolkitTheme.spacing.extraSmall + 2.dp,
                                        vertical = ToolkitTheme.spacing.extraSmall / 2
                                    ),
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
    customActions: List<PluginAction>,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit,
    onAction: (PluginStatusAction) -> Unit
) {
    val statusColor = if (plugin.loadError != null) MaterialTheme.colorScheme.error
    else if (plugin.requiredAction != null) ToolkitTheme.colors.warning
    else if (isLoaded) ToolkitTheme.colors.success
    else if (plugin.isValidated) ToolkitTheme.colors.validated
    else MaterialTheme.colorScheme.outline

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
            containerColor = if (plugin.loadError != null) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
            else if (isLoaded) statusColor.copy(alpha = 0.05f)
            else MaterialTheme.colorScheme.surface
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
                    if (plugin.loadError != null) {
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                        StatusBadge(stringResource(Res.string.plugin_broken), MaterialTheme.colorScheme.error)
                    } else if (isLoaded) {
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                        StatusBadge(stringResource(Res.string.plugin_loaded), ToolkitTheme.colors.success)
                    }

                    if (plugin.requiredAction != null) {
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                        val badgeText = if (plugin.requiredAction == "CONFIGURE_SETTINGS") "Setup Required" else "Action Required"
                        StatusBadge(badgeText, ToolkitTheme.colors.warning)
                    }
                    
                    if (plugin.loadError == null) {
                        if (plugin.isValidated) {
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                            StatusBadge(stringResource(Res.string.plugin_validated), ToolkitTheme.colors.validated)
                        } else {
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                            StatusBadge(
                                stringResource(Res.string.plugin_validation_pending),
                                MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                Text(
                    stringResource(Res.string.plugin_version_pkg_format, plugin.version, plugin.pkg),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (plugin.loadError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (plugin.loadError != null) {
                    Text(
                        plugin.loadError,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = ToolkitTheme.spacing.extraSmall)
                    )
                }
            }

            // Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (plugin.requiredAction != null) {
                    val action = customActions.find { it.functionName == plugin.requiredAction }
                    Button(
                        onClick = { 
                            if (plugin.requiredAction == "CONFIGURE_SETTINGS") {
                                onAction(PluginStatusAction.Settings)
                            } else {
                                onAction(PluginStatusAction.Custom(plugin.requiredAction!!)) 
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ToolkitTheme.colors.warning),
                        modifier = Modifier.padding(end = ToolkitTheme.spacing.small)
                    ) {
                        Text(if (plugin.requiredAction == "CONFIGURE_SETTINGS") "Configure" else (action?.name ?: "Fix Issue"))
                    }
                }

                if (hasUpdate) {
                    Button(
                        onClick = { onAction(PluginStatusAction.Update) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.padding(end = ToolkitTheme.spacing.small),
                        enabled = enabled
                    ) {
                        Text(stringResource(Res.string.plugin_update))
                    }
                } else {
                    OutlinedButton(
                        onClick = { onAction(PluginStatusAction.Update) },
                        modifier = Modifier.padding(end = ToolkitTheme.spacing.small),
                        enabled = enabled
                    ) {
                        Text(stringResource(Res.string.plugin_update_local))
                    }
                }

                Switch(checked = plugin.isEnabled, onCheckedChange = onToggle, enabled = enabled)

                var expanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { expanded = true }, enabled = enabled) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(Res.string.action_more_actions)
                        )
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.plugin_reload)) },
                            onClick = { onAction(PluginStatusAction.Reload); expanded = false },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.plugin_validate)) },
                            onClick = { onAction(PluginStatusAction.Validate); expanded = false },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Rerun Setup") },
                            onClick = { onAction(PluginStatusAction.RerunSetup); expanded = false },
                            leadingIcon = { Icon(Icons.Default.Replay, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.plugin_changelog)) },
                            onClick = { onAction(PluginStatusAction.Changelog); expanded = false },
                            leadingIcon = { Icon(Icons.Default.History, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.plugin_settings)) },
                            onClick = { onAction(PluginStatusAction.Settings); expanded = false },
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
                            onClick = { onAction(PluginStatusAction.Uninstall); expanded = false },
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

sealed class PluginStatusAction {
    object Uninstall : PluginStatusAction()
    object Reload : PluginStatusAction()
    object Validate : PluginStatusAction()
    object Update : PluginStatusAction()
    object RerunSetup : PluginStatusAction()
    object Changelog : PluginStatusAction()
    object Settings : PluginStatusAction()
    data class Custom(val name: String) : PluginStatusAction()
}
