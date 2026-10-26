package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.plugin.sdui.PluginCustomUiCard
import org.wip.plugintoolkit.features.plugin.viewmodel.InteractiveToolItem
import org.wip.plugintoolkit.features.plugin.viewmodel.InteractiveToolsViewModel
import org.wip.plugintoolkit.shared.components.SectionHeader
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_close_session
import plugintoolkit.composeapp.generated.resources.action_launch_session
import plugintoolkit.composeapp.generated.resources.action_restart_session
import plugintoolkit.composeapp.generated.resources.interactive_session_active
import plugintoolkit.composeapp.generated.resources.interactive_session_idle
import plugintoolkit.composeapp.generated.resources.interactive_tools_empty
import plugintoolkit.composeapp.generated.resources.interactive_tools_empty_desc
import plugintoolkit.composeapp.generated.resources.interactive_tools_hint
import plugintoolkit.composeapp.generated.resources.interactive_tools_search_placeholder
import plugintoolkit.composeapp.generated.resources.interactive_tools_title

/**
 * Dedicated host screen for running interactive SDUI plugin tools.
 *
 * Provides a dedicated workspace separate from automated capabilities and visual flows,
 * offering a live bidirectional GUI viewport with session lifecycle management.
 */
@Composable
fun InteractiveToolsScreen(
    initialPluginId: String? = null,
    initialCapabilityName: String? = null,
    viewModel: InteractiveToolsViewModel = koinInject()
) {
    val tools by viewModel.interactiveTools.collectAsState()
    val selectedTool by viewModel.selectedTool.collectAsState()
    val activeSessionHandle by viewModel.activeSessionHandle.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    // Handle initial navigation target
    LaunchedEffect(initialPluginId, initialCapabilityName, tools) {
        if (initialPluginId != null && selectedTool == null && tools.isNotEmpty()) {
            val matching = tools.find {
                val manifest = it.plugin.getManifest().getOrNull()
                manifest?.plugin?.id == initialPluginId &&
                    (initialCapabilityName == null || it.capability.name == initialCapabilityName)
            } ?: tools.firstOrNull()
            matching?.let { viewModel.selectTool(it) }
        }
    }

    val filteredTools = tools.filter { tool ->
        val manifest = tool.plugin.getManifest().getOrNull()
        val pluginName = manifest?.plugin?.name ?: ""
        val toolName = tool.capability.name
        val desc = tool.capability.description ?: ""
        pluginName.contains(searchQuery, ignoreCase = true) ||
            toolName.contains(searchQuery, ignoreCase = true) ||
            desc.contains(searchQuery, ignoreCase = true)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Left Pane: Interactive Tools Sidebar
        Surface(
            modifier = Modifier
                .width(ToolkitTheme.dimensions.containerSizeLarge)
                .fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(
                width = ToolkitTheme.dimensions.borderThin,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(ToolkitTheme.spacing.medium)
            ) {
                Text(
                    text = stringResource(Res.string.interactive_tools_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = ToolkitTheme.spacing.small)
                )

                // Search field
                ToolkitTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = {
                        Text(
                            text = stringResource(Res.string.interactive_tools_search_placeholder),
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                        )
                    },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                )
                            }
                        }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = ToolkitTheme.spacing.medium),
                    singleLine = true
                )

                if (filteredTools.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (tools.isEmpty()) {
                                stringResource(Res.string.interactive_tools_empty)
                            } else {
                                stringResource(Res.string.interactive_tools_search_placeholder)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
                    ) {
                        items(filteredTools, key = { "${it.plugin.getManifest().getOrNull()?.plugin?.id}_${it.capability.name}" }) { item ->
                            val isSelected = selectedTool?.plugin?.getManifest()?.getOrNull()?.plugin?.id ==
                                item.plugin.getManifest().getOrNull()?.plugin?.id &&
                                selectedTool?.capability?.name == item.capability.name
                            val pluginManifest = item.plugin.getManifest().getOrNull()

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(ToolkitTheme.dimensions.buttonGroupInnerCorner))
                                    .clickable { viewModel.selectTool(item) },
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                shape = RoundedCornerShape(ToolkitTheme.dimensions.buttonGroupInnerCorner),
                                border = if (isSelected) {
                                    androidx.compose.foundation.BorderStroke(
                                        width = ToolkitTheme.dimensions.borderThin,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(ToolkitTheme.spacing.mediumSmall),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TouchApp,
                                        contentDescription = null,
                                        tint = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.capability.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) {
                                                MaterialTheme.colorScheme.onSecondaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                        Text(
                                            text = pluginManifest?.plugin?.name ?: item.plugin.getManifest().getOrNull()?.plugin?.id ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Right Pane: Full Interactive SDUI Workspace
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            val currentTool = selectedTool
            if (currentTool == null) {
                if (tools.isEmpty()) {
                    EmptyState(stringResource(Res.string.interactive_tools_empty))
                } else {
                    EmptyState(stringResource(Res.string.interactive_tools_hint))
                }
            } else {
                val manifest = currentTool.plugin.getManifest().getOrNull()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(ToolkitTheme.spacing.large)
                ) {
                    // Header Bar: Tool Info, Status Pill & Actions
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = ToolkitTheme.spacing.medium),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumLarge)
                            )
                            Column {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = currentTool.capability.name,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    // Status Badge
                                    val isRunning = activeSessionHandle?.isActive == true
                                    Surface(
                                        color = if (isRunning) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                        shape = RoundedCornerShape(ToolkitTheme.dimensions.buttonGroupInnerCorner)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(
                                                horizontal = ToolkitTheme.spacing.small,
                                                vertical = ToolkitTheme.spacing.extraSmall
                                            ),
                                            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(ToolkitTheme.dimensions.heightSmall)
                                                    .background(
                                                        color = if (isRunning) {
                                                            MaterialTheme.colorScheme.primary
                                                        } else {
                                                            MaterialTheme.colorScheme.outline
                                                        },
                                                        shape = CircleShape
                                                    )
                                            )
                                            Text(
                                                text = if (isRunning) {
                                                    stringResource(Res.string.interactive_session_active)
                                                } else {
                                                    stringResource(Res.string.interactive_session_idle)
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isRunning) {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }
                                }

                                if (manifest != null) {
                                    Text(
                                        text = "${manifest.plugin.name} v${manifest.plugin.version} (${manifest.plugin.id})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // Action Controls
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.restartSession() },
                                contentPadding = ButtonDefaults.TextButtonContentPadding
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                )
                                Spacer(Modifier.width(ToolkitTheme.spacing.extraSmall))
                                Text(stringResource(Res.string.action_restart_session))
                            }

                            if (activeSessionHandle?.isActive == true) {
                                IconButton(onClick = { viewModel.closeSession() }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(Res.string.action_close_session),
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider),
                        modifier = Modifier.padding(bottom = ToolkitTheme.spacing.medium)
                    )

                    // Interactive Viewport
                    val handle = activeSessionHandle
                    if (handle != null && handle.isActive) {
                        PluginCustomUiCard(
                            handle = handle,
                            modifier = Modifier
                                .fillMaxSize(),
                            onClose = { viewModel.closeSession() }
                        )
                    } else {
                        // Closed / Idle State Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = ToolkitTheme.dimensions.borderThin,
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(ToolkitTheme.dimensions.expressiveCardCornerRadius)
                                ),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(ToolkitTheme.dimensions.expressiveCardCornerRadius)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(ToolkitTheme.spacing.extraLarge),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.TouchApp,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconLarge)
                                )
                                Text(
                                    text = currentTool.capability.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = currentTool.capability.description ?: stringResource(Res.string.interactive_tools_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Button(
                                    onClick = { viewModel.startSession(currentTool) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                    )
                                    Spacer(Modifier.width(ToolkitTheme.spacing.extraSmall))
                                    Text(stringResource(Res.string.action_launch_session))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
