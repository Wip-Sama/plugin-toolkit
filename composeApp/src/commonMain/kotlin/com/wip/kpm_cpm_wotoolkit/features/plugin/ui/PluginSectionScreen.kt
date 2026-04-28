package com.wip.kpm_cpm_wotoolkit.features.plugin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.wip.kpm_cpm_wotoolkit.features.plugin.viewmodel.PluginViewModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.koinInject

@Serializable
sealed interface PluginNavKey : NavKey {
    @Serializable
    data object ModuleList : PluginNavKey
    @Serializable
    data class ModuleDetail(val id: String) : PluginNavKey
}

val PluginNavConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(PluginNavKey.ModuleList::class, PluginNavKey.ModuleList.serializer())
            subclass(PluginNavKey.ModuleDetail::class, PluginNavKey.ModuleDetail.serializer())
        }
    }
}

@Composable
fun PluginSectionScreen(
    initialModuleId: String? = null,
    viewModel: PluginViewModel = koinInject()
) {
    val startDestination = if (initialModuleId != null) {
        PluginNavKey.ModuleDetail(initialModuleId)
    } else {
        PluginNavKey.ModuleList
    }

    val backStack = rememberNavBackStack(PluginNavConfig, startDestination)
    val currentKey = backStack.lastOrNull() ?: PluginNavKey.ModuleList
    val loadedPlugins = viewModel.loadedPlugins

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Nested Navigation Sidebar for Modules
        DirectExecutionSidebar(
            loadedPlugins = loadedPlugins,
            selectedModuleId = (currentKey as? PluginNavKey.ModuleDetail)?.id,
            onModuleSelected = { id -> 
                if ((currentKey as? PluginNavKey.ModuleDetail)?.id != id) {
                    backStack.add(PluginNavKey.ModuleDetail(id))
                }
            },
            onBackToModules = {
                if (backStack.any { it is PluginNavKey.ModuleList }) {
                    while (backStack.lastOrNull() !is PluginNavKey.ModuleList) {
                        backStack.removeLast()
                    }
                } else {
                    backStack.add(PluginNavKey.ModuleList)
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
                    is PluginNavKey.ModuleList -> NavEntry(key) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "Select a module from the sidebar to see its capabilities",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is PluginNavKey.ModuleDetail -> NavEntry(key) {
                        val plugin = loadedPlugins.find { it.getManifest().module.id == key.id }
                        if (plugin != null) {
                            LaunchedEffect(key.id) {
                                viewModel.selectPlugin(plugin)
                            }
                            PluginContent(viewModel = viewModel)
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Module not found or unloaded", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    else -> NavEntry(key) { }
                }
            }
        }
    }
}
