package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.core.model.localized
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import org.wip.plugintoolkit.shared.components.sidebar.NavigationSidebar
import org.wip.plugintoolkit.shared.components.sidebar.SidebarElement
import org.wip.plugintoolkit.shared.components.sidebar.SidebarSectionData
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_back
import plugintoolkit.composeapp.generated.resources.plugin_capabilities
import plugintoolkit.composeapp.generated.resources.search_capabilities_placeholder
import plugintoolkit.composeapp.generated.resources.search_plugins_placeholder
import plugintoolkit.composeapp.generated.resources.section_loaded_plugins

@Composable
fun DirectExecutionSidebar(
    loadedPlugins: List<PluginEntry>,
    selectedPluginId: String?,
    onPluginSelected: (String) -> Unit,
    onBackToPlugins: () -> Unit,
    selectedCapability: Capability?,
    onCapabilitySelected: (Capability) -> Unit,
    modifier: Modifier = Modifier
) {
    var pluginSearchQuery by remember { mutableStateOf("") }
    var capabilitySearchQuery by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .width(ToolkitTheme.dimensions.sidebarExpandedWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(ToolkitTheme.dimensions.borderUnselected))
            .clipToBounds()
    ) {
        AnimatedContent(
            targetState = selectedPluginId,
            transitionSpec = {
                if (targetState != null) {
                    slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                } else {
                    slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                }
            }
        ) { pluginId ->
            if (pluginId == null) {
                val filteredPlugins = loadedPlugins.filter { plugin ->
                    val manifest = plugin.getManifest().getOrNull() ?: return@filter false
                    val name = manifest.plugin.name
                    val id = manifest.plugin.id
                    name.contains(pluginSearchQuery, ignoreCase = true) || id.contains(pluginSearchQuery, ignoreCase = true)
                }

                val pluginsElements = filteredPlugins.map { plugin ->
                    val manifest = plugin.getManifest().getOrThrow()
                    SidebarElement(
                        id = manifest.plugin.id,
                        icon = Icons.Default.Extension,
                        title = manifest.plugin.name.localized
                    )
                }

                NavigationSidebar(
                    title = Res.string.section_loaded_plugins.localized,
                    bodySections = listOf(SidebarSectionData(title = null, elements = pluginsElements)),
                    currentScreen = "",
                    onScreenSelected = onPluginSelected,
                    isNavbarCollapsed = false,
                    onToggleNavbar = {},
                    canCollapse = false,
                    headerContent = {
                        if (loadedPlugins.size > 5 || pluginSearchQuery.isNotEmpty()) {
                            ToolkitTextField(
                                value = pluginSearchQuery,
                                onValueChange = { pluginSearchQuery = it },
                                placeholder = {
                                    Text(
                                        stringResource(Res.string.search_plugins_placeholder),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                                    )
                                },
                                trailingIcon = if (pluginSearchQuery.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { pluginSearchQuery = "" }) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Clear",
                                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                            )
                                        }
                                    }
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = ToolkitTheme.spacing.small),
                                singleLine = true
                            )
                        }
                    }
                )
            } else {
                val plugin = loadedPlugins.find { it.getManifest().getOrNull()?.plugin?.id == pluginId }
                if (plugin != null) {
                    val manifest = plugin.getManifest().getOrThrow()

                    val filteredCapabilities = manifest.capabilities.filter { capability ->
                        capability.name.contains(capabilitySearchQuery, ignoreCase = true) ||
                            (capability.description != null && capability.description.contains(capabilitySearchQuery, ignoreCase = true))
                    }

                    val capabilityElements = filteredCapabilities.map { capability ->
                        SidebarElement(
                            id = capability,
                            icon = Icons.Default.Bolt,
                            title = capability.name.localized
                        )
                    }

                    NavigationSidebar(
                        title = manifest.plugin.name.localized,
                        bodySections = listOf(
                            SidebarSectionData(
                                title = Res.string.plugin_capabilities.localized,
                                elements = capabilityElements
                            )
                        ),
                        currentScreen = selectedCapability,
                        onScreenSelected = { capability -> capability?.let(onCapabilitySelected) },
                        isNavbarCollapsed = false,
                        onToggleNavbar = {},
                        canCollapse = false,
                        headerContent = {
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(bottom = ToolkitTheme.spacing.mediumSmall)
                                ) {
                                    TextButton(
                                        onClick = {
                                            capabilitySearchQuery = ""
                                            onBackToPlugins()
                                        },
                                        contentPadding = PaddingValues(
                                            horizontal = ToolkitTheme.spacing.small,
                                            vertical = ToolkitTheme.spacing.extraSmall
                                        )
                                    ) {
                                        Icon(
                                            Icons.Default.ArrowBack,
                                            contentDescription = stringResource(Res.string.action_back),
                                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                        )
                                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                                        Text(stringResource(Res.string.action_back), style = MaterialTheme.typography.labelMedium)
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = ToolkitTheme.opacity.divider),
                                        shape = ToolkitTheme.shapes.extraSmall
                                    ) {
                                        Text(
                                            text = "v${manifest.plugin.version}",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(
                                                horizontal = ToolkitTheme.spacing.badgeHorizontal,
                                                vertical = ToolkitTheme.spacing.badgeVertical
                                            ),
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }

                                ToolkitTextField(
                                    value = capabilitySearchQuery,
                                    onValueChange = { capabilitySearchQuery = it },
                                    placeholder = {
                                        Text(
                                            stringResource(Res.string.search_capabilities_placeholder),
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                                        )
                                    },
                                    trailingIcon = if (capabilitySearchQuery.isNotEmpty()) {
                                        {
                                            IconButton(onClick = { capabilitySearchQuery = "" }) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    contentDescription = "Clear",
                                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                                )
                                            }
                                        }
                                    } else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = ToolkitTheme.spacing.small),
                                    singleLine = true
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}
