package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.core.model.localized
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.shared.components.sidebar.NavigationSidebar
import org.wip.plugintoolkit.shared.components.sidebar.SidebarElement
import org.wip.plugintoolkit.shared.components.sidebar.SidebarSectionData
import plugintoolkit.composeapp.generated.resources.Res
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
    Box(
        modifier = modifier
            .width(ToolkitTheme.dimensions.sidebarExpandedWidth)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
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
                val pluginsElements = loadedPlugins.map { plugin ->
                    val manifest = plugin.getManifest()
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
                    canCollapse = false
                )
            } else {
                val plugin = loadedPlugins.find { it.getManifest().plugin.id == pluginId }
                if (plugin != null) {
                    val manifest = plugin.getManifest()
                    val capabilityElements = manifest.capabilities.map { capability ->
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
                                title = "Capabilities".localized,
                                elements = capabilityElements
                            )
                        ),
                        currentScreen = selectedCapability,
                        onScreenSelected = { capability -> capability?.let(onCapabilitySelected) },
                        isNavbarCollapsed = false,
                        onToggleNavbar = {},
                        canCollapse = false,
                        headerContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = ToolkitTheme.spacing.mediumSmall)
                            ) {
                                TextButton(
                                    onClick = onBackToPlugins,
                                    contentPadding = PaddingValues(horizontal = ToolkitTheme.spacing.small, vertical = ToolkitTheme.spacing.extraSmall)
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                                    Text("Back", style = MaterialTheme.typography.labelMedium)
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "v${manifest.plugin.version}",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                     )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
