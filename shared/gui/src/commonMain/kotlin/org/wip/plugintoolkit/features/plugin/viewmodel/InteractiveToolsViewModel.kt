package org.wip.plugintoolkit.features.plugin.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.CapabilityContext
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.sdui.PluginUiSessionHandle
import org.wip.plugintoolkit.features.plugin.sdui.PluginUiSessionManager

/**
 * Represents an interactive tool pair: the hosting plugin entry and the interactive capability.
 */
data class InteractiveToolItem(
    val plugin: PluginEntry,
    val capability: Capability
)

/**
 * ViewModel managing the Interactive Tools runner section.
 *
 * Discovers plugins exposing interactive SDUI tools, handles interactive session
 * initialization via [PluginUiSessionManager], and enforces full UI lifecycle teardown.
 */
class InteractiveToolsViewModel(
    private val pluginManager: PluginManager,
    private val uiSessionManager: PluginUiSessionManager?,
    private val notificationService: NotificationService? = null
) : ViewModel() {

    private val _interactiveTools = MutableStateFlow<List<InteractiveToolItem>>(emptyList())
    val interactiveTools: StateFlow<List<InteractiveToolItem>> = _interactiveTools.asStateFlow()

    private val _selectedTool = MutableStateFlow<InteractiveToolItem?>(null)
    val selectedTool: StateFlow<InteractiveToolItem?> = _selectedTool.asStateFlow()

    private val _activeSessionHandle = MutableStateFlow<PluginUiSessionHandle?>(null)
    val activeSessionHandle: StateFlow<PluginUiSessionHandle?> = _activeSessionHandle.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        refreshInteractiveTools()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Refreshes the list of available interactive tools across all loaded plugins.
     */
    fun refreshInteractiveTools() {
        val plugins = PluginLoader.getPlugins()
        val tools = mutableListOf<InteractiveToolItem>()
        for (plugin in plugins) {
            val manifest = plugin.getManifest().getOrNull() ?: continue
            val interactiveCaps = manifest.capabilities.filter {
                it.isInteractive || it.context == CapabilityContext.INTERACTIVE_ONLY
            }
            if (interactiveCaps.isNotEmpty()) {
                interactiveCaps.forEach { cap ->
                    tools.add(InteractiveToolItem(plugin, cap))
                }
            } else if (plugin.getInteractiveProcessor() != null) {
                val fallbackCap = Capability(
                    name = manifest.plugin.id,
                    description = manifest.plugin.description,
                    returnType = org.wip.plugintoolkit.api.DataType.Primitive(org.wip.plugintoolkit.api.PrimitiveType.STRING),
                    isInteractive = true,
                    context = CapabilityContext.INTERACTIVE_ONLY
                )
                tools.add(InteractiveToolItem(plugin, fallbackCap))
            }
        }
        _interactiveTools.value = tools

        // If currently selected tool is no longer available, reset selection
        val current = _selectedTool.value
        if (current != null && tools.none { it.plugin.getManifest().getOrNull()?.plugin?.id == current.plugin.getManifest().getOrNull()?.plugin?.id && it.capability.name == current.capability.name }) {
            closeSession()
            _selectedTool.value = null
        }
    }

    /**
     * Selects a tool and opens a live SDUI session.
     */
    fun selectTool(tool: InteractiveToolItem) {
        val current = _selectedTool.value
        if (current?.plugin?.getManifest()?.getOrNull()?.plugin?.id == tool.plugin.getManifest().getOrNull()?.plugin?.id &&
            current?.capability?.name == tool.capability.name &&
            _activeSessionHandle.value?.isActive == true
        ) {
            return
        }
        _selectedTool.value = tool
        startSession(tool)
    }

    /**
     * Starts an interactive session for the given tool item.
     */
    fun startSession(tool: InteractiveToolItem) {
        closeSession()
        val processor = tool.plugin.getInteractiveProcessor()?.getOrNull()
        if (processor == null) {
            notificationService?.toast("Plugin does not provide an InteractiveUiProcessor")
            return
        }
        if (uiSessionManager == null) {
            notificationService?.toast("UI Session Manager is not available")
            return
        }

        val manifest = tool.plugin.getManifest().getOrNull() ?: return
        val context = pluginManager.createPluginContext(
            pkg = manifest.plugin.id,
            capabilityName = tool.capability.name,
            manifest = manifest
        )

        val handle = uiSessionManager.startSession(
            pluginId = manifest.plugin.id,
            capabilityName = tool.capability.name,
            context = context,
            processor = processor
        )
        _activeSessionHandle.value = handle
    }

    /**
     * Restarts the current session with fresh initial state.
     */
    fun restartSession() {
        val tool = _selectedTool.value ?: return
        startSession(tool)
    }

    /**
     * Closes the active session handle and cancels background coroutines.
     */
    fun closeSession() {
        _activeSessionHandle.value?.let { handle ->
            if (handle.isActive) {
                handle.cancel("Session closed by user")
            }
        }
        _activeSessionHandle.value = null
    }

    override fun onCleared() {
        super.onCleared()
        closeSession()
    }
}
