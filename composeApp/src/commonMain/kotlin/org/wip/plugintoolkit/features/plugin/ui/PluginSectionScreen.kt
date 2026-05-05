package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginViewModel
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.plugin_not_found_unloaded
import plugintoolkit.composeapp.generated.resources.plugin_selection_hint

@Serializable
sealed interface PluginNavKey : NavKey {
    @Serializable
    data object PluginList : PluginNavKey

    @Serializable
    data class PluginDetail(val id: String) : PluginNavKey
}

val PluginNavConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(PluginNavKey.PluginList::class, PluginNavKey.PluginList.serializer())
            subclass(PluginNavKey.PluginDetail::class, PluginNavKey.PluginDetail.serializer())
        }
    }
}

@Composable
fun PluginSectionScreen(
    initialPluginId: String? = null,
    viewModel: PluginViewModel = koinInject()
) {
    val startDestination = if (initialPluginId != null) {
        PluginNavKey.PluginDetail(initialPluginId)
    } else {
        PluginNavKey.PluginList
    }

    val backStack = rememberNavBackStack(PluginNavConfig, startDestination)
    val currentKey = backStack.lastOrNull() ?: PluginNavKey.PluginList
    val loadedPlugins = viewModel.loadedPlugins

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Nested Navigation Sidebar for Plugins
        DirectExecutionSidebar(
            loadedPlugins = loadedPlugins,
            selectedPluginId = (currentKey as? PluginNavKey.PluginDetail)?.id,
            onPluginSelected = { id ->
                if ((currentKey as? PluginNavKey.PluginDetail)?.id != id) {
                    backStack.add(PluginNavKey.PluginDetail(id))
                }
            },
            onBackToPlugins = {
                if (backStack.any { it is PluginNavKey.PluginList }) {
                    while (backStack.lastOrNull() !is PluginNavKey.PluginList) {
                        backStack.removeLast()
                    }
                } else {
                    backStack.add(PluginNavKey.PluginList)
                }
            },
            selectedCapability = viewModel.selectedCapability,
            onCapabilitySelected = { viewModel.selectCapability(it) }
        )

        // Detail Content Area
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                onBack = { if (backStack.size > 1) backStack.removeLast() }
            ) { key ->
                when (key) {
                    is PluginNavKey.PluginList -> NavEntry(key) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(Res.string.plugin_selection_hint),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    is PluginNavKey.PluginDetail -> NavEntry(key) {
                        val plugin = loadedPlugins.find { it.getManifest().plugin.id == key.id }
                        if (plugin != null) {
                            LaunchedEffect(key.id) {
                                viewModel.selectPlugin(plugin)
                            }
                            PluginContent(viewModel = viewModel)
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(Res.string.plugin_not_found_unloaded),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }

                    else -> NavEntry(key) { }
                }
            }
        }
    }
}
