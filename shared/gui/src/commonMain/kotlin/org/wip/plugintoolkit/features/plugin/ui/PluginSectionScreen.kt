package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wip.plugintoolkit.core.theme.LocalCompactPanelsByDefault
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginViewModel
import org.wip.plugintoolkit.shared.components.AdaptiveBackNavigationButton
import org.wip.plugintoolkit.shared.components.ToolkitAdaptiveListDetailWorkspace
import org.wip.plugintoolkit.shared.components.sidebar.SequelSidebarHysteresis
import org.wip.plugintoolkit.shared.components.sidebar.SequelSidebarMode
import org.wip.plugintoolkit.shared.components.sidebar.isLargeAdaptiveWidth
import org.wip.plugintoolkit.shared.components.sidebar.resolveSequelExpanded
import org.wip.plugintoolkit.shared.components.sidebar.toggleSequelSidebarMode
import org.wip.plugintoolkit.ui.desktopNavTransitionSpec
import org.wip.plugintoolkit.ui.opaqueNavEntry
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.plugin_not_found_unloaded
import plugintoolkit.composeapp.generated.resources.plugin_selection_hint

@Serializable
sealed interface PluginNavKey : NavKey {
    @Serializable
    data object PluginList : PluginNavKey

    @Serializable
    data class PluginDetail(val id: String, val scrollToSetting: String? = null) : PluginNavKey
}

val PluginNavConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(PluginNavKey.PluginList::class, PluginNavKey.PluginList.serializer())
            subclass(PluginNavKey.PluginDetail::class, PluginNavKey.PluginDetail.serializer())
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun PluginSectionScreen(
    initialPluginId: String? = null,
    initialScrollToSetting: String? = null,
    viewModel: PluginViewModel = koinInject()
) {
    val startDestination = if (initialPluginId != null) {
        PluginNavKey.PluginDetail(initialPluginId, initialScrollToSetting)
    } else {
        PluginNavKey.PluginList
    }

    val backStack = rememberNavBackStack(PluginNavConfig, startDestination)
    val currentKey = backStack.lastOrNull() ?: PluginNavKey.PluginList
    val loadedPlugins = viewModel.loadedPlugins
    val selectedPluginId = (currentKey as? PluginNavKey.PluginDetail)?.id

    val scope = rememberCoroutineScope()
    val compactPanelsByDefault = LocalCompactPanelsByDefault.current
    var sequelMode by remember(compactPanelsByDefault) {
        mutableStateOf(
            if (compactPanelsByDefault) SequelSidebarMode.ForcedCompact else SequelSidebarMode.Auto
        )
    }
    var dragSequelOverride by remember { mutableStateOf<Boolean?>(null) }
    val isLargeWidth = isLargeAdaptiveWidth()
    val targetSequelExpanded = resolveSequelExpanded(sequelMode, isLargeWidth) &&
        (selectedPluginId != null || sequelMode == SequelSidebarMode.ForcedExpanded)
    val isSequelExpanded = dragSequelOverride ?: targetSequelExpanded
    val listPaneWidth = if (targetSequelExpanded) {
        ToolkitTheme.dimensions.floatingSidebarSplitTotalWidth
    } else {
        ToolkitTheme.dimensions.floatingSidebarDefaultWidth
    }
    val detailSurfaceColor = MaterialTheme.colorScheme.surfaceContainerLowest

    ToolkitAdaptiveListDetailWorkspace<String>(
        listPaneWidth = listPaneWidth,
        onLiveListPaneWidthChanged = { draggedWidth ->
            val nextSplit = SequelSidebarHysteresis.resolveFromDraggedWidth(
                isCurrentlySplit = isSequelExpanded,
                draggedWidth = draggedWidth
            )
            if (nextSplit != isSequelExpanded) {
                dragSequelOverride = nextSplit
            }
        },
        listPane = { navigator ->
            DirectExecutionSidebar(
                loadedPlugins = loadedPlugins,
                selectedPluginId = selectedPluginId,
                onPluginSelected = { id ->
                    dragSequelOverride = null
                    val plugin = loadedPlugins.find { it.getManifest().getOrNull()?.plugin?.id == id }
                    if (plugin != null && viewModel.selectedPlugin != plugin) {
                        viewModel.selectPlugin(plugin)
                    }
                    if ((currentKey as? PluginNavKey.PluginDetail)?.id != id) {
                        backStack.add(PluginNavKey.PluginDetail(id))
                    }
                },
                onBackToPlugins = {
                    dragSequelOverride = null
                    if (backStack.any { it is PluginNavKey.PluginList }) {
                        while (backStack.lastOrNull() !is PluginNavKey.PluginList) {
                            backStack.removeLast()
                        }
                    } else {
                        backStack.add(PluginNavKey.PluginList)
                    }
                },
                selectedCapability = viewModel.selectedCapability,
                onCapabilitySelected = { capability ->
                    viewModel.selectCapability(capability)
                    scope.launch {
                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, capability.name)
                    }
                },
                isSequelExpanded = isSequelExpanded,
                onToggleSequelMode = {
                    dragSequelOverride = null
                    sequelMode = toggleSequelSidebarMode(isSequelExpanded)
                },
                fillPaneWidth = true
            )
        },
        detailPane = { navigator ->
            Box(modifier = Modifier.fillMaxSize()) {
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.fillMaxSize(),
                    onBack = { if (backStack.size > 1) backStack.removeLast() },
                    transitionSpec = desktopNavTransitionSpec(),
                    popTransitionSpec = desktopNavTransitionSpec()
                ) { key ->
                    when (key) {
                        is PluginNavKey.PluginList -> opaqueNavEntry(key, containerColor = detailSurfaceColor) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(Res.string.plugin_selection_hint),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is PluginNavKey.PluginDetail -> opaqueNavEntry(key, containerColor = detailSurfaceColor) {
                            val plugin = loadedPlugins.find { it.getManifest().getOrThrow().plugin.id == key.id }
                            if (plugin != null) {
                                LaunchedEffect(key.id) {
                                    if (viewModel.selectedPlugin != plugin) {
                                        viewModel.selectPlugin(plugin)
                                    }
                                }
                                PluginContent(
                                    viewModel = viewModel,
                                    scrollToSetting = key.scrollToSetting
                                )
                            } else {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        stringResource(Res.string.plugin_not_found_unloaded),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        else -> opaqueNavEntry(key, containerColor = detailSurfaceColor) { }
                    }
                }

                if (navigator.canNavigateBack()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(ToolkitTheme.spacing.small)
                    ) {
                        AdaptiveBackNavigationButton(
                            onClick = { scope.launch { navigator.navigateBack() } }
                        )
                    }
                }
            }
        }
    )
}
