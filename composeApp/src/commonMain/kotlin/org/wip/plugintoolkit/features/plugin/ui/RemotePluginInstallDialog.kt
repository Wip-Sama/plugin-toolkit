package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.plugin.utils.PluginSearchUtils
import org.wip.plugintoolkit.features.repository.model.ExtensionPlugin

@Composable
fun RemotePluginInstallDialog(
    availablePlugins: List<ExtensionPlugin>,
    installedPackageNames: Set<String>,
    activeJobs: Map<String, Float>,
    onInstall: (ExtensionPlugin) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredPlugins = remember(availablePlugins, searchQuery, installedPackageNames) {
        PluginSearchUtils.filterPlugins(
            availablePlugins,
            searchQuery,
            installedPackageNames
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.85f)
                .clip(MaterialTheme.shapes.large),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ToolkitTheme.spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Install Remote Plugins",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ToolkitTheme.spacing.medium),
                    placeholder = { Text("Search plugins...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )

                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

                // List
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(ToolkitTheme.spacing.medium),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                ) {
                    items(filteredPlugins) { plugin ->
                        val isInstalled = installedPackageNames.contains(plugin.pkg)
                        val progress = activeJobs[plugin.pkg]
                        
                        RemotePluginCard(
                            plugin = plugin,
                            isInstalled = isInstalled,
                            progress = progress,
                            onInstall = { onInstall(plugin) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RemotePluginCard(
    plugin: ExtensionPlugin,
    isInstalled: Boolean,
    progress: Float?,
    onInstall: () -> Unit
) {
    val alpha = if (isInstalled) 0.6f else 1f
    var showInfo by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().alpha(alpha),
        colors = CardDefaults.cardColors(
            containerColor = if (isInstalled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        ),
        border = if (progress != null) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(modifier = Modifier.padding(ToolkitTheme.spacing.medium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
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
                        Text(
                            plugin.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (isInstalled) {
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.extraSmall
                            ) {
                                Text(
                                    "Installed",
                                    modifier = Modifier.padding(
                                        horizontal = ToolkitTheme.spacing.extraSmall + 2.dp,
                                        vertical = ToolkitTheme.spacing.extraSmall / 2
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                    Text(
                        "v${plugin.version} • ${plugin.pkg}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showInfo = !showInfo }) {
                        Icon(
                            Icons.Default.Info, 
                            contentDescription = "Info", 
                            tint = if (showInfo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (progress != null) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconLarge).padding(ToolkitTheme.spacing.extraSmall),
                            strokeWidth = 3.dp
                        )
                    } else if (!isInstalled) {
                        Button(
                            onClick = onInstall,
                            contentPadding = PaddingValues(
                                horizontal = ToolkitTheme.spacing.mediumSmall,
                                vertical = ToolkitTheme.spacing.small
                            )
                        ) {
                            Icon(
                                Icons.Default.CloudDownload, 
                                contentDescription = null, 
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall + 2.dp)
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text("Install")
                        }
                    }
                }
            }

            if (showInfo) {
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                HorizontalDivider(modifier = Modifier.alpha(0.5f))
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))

                if (plugin.description != null) {
                    Text(
                        plugin.description,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = ToolkitTheme.spacing.small),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                InfoRow("Repository", plugin.repoUrl ?: "Unknown")
                plugin.size?.let { InfoRow("Size", formatSize(it)) }
                plugin.hash?.let { InfoRow("Hash", it) }
                plugin.signature?.let { InfoRow("Signature", it) }
            }

            if (progress != null) {
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                        .height(ToolkitTheme.spacing.extraSmall)
                        .clip(MaterialTheme.shapes.extraSmall)
                )
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            "$label: ",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

fun formatSize(size: Long): String {
    val kb = size / 1024.0
    val mb = kb / 1024.0
    return if (mb >= 1.0) "%.2f MB".format(mb) else "%.2f KB".format(kb)
}