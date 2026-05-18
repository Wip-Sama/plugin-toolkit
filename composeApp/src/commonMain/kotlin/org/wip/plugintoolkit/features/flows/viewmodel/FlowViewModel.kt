package org.wip.plugintoolkit.features.flows.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import org.koin.mp.KoinPlatform.getKoin
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.core.KeepTrack
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.notification.NotificationType
import org.wip.plugintoolkit.features.flows.model.*
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.plugin.logic.PluginRegistry
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.api.PluginEntry
import kotlin.time.Clock
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType

data class FlowState(
    val flows: List<Flow> = emptyList(),
    val selectedFlowId: String? = null
) {
    val currentFlow: Flow? get() = flows.find { it.name == selectedFlowId } ?: flows.firstOrNull()
}

sealed interface FlowEvent {
    data class AddCapabilityNode(val pluginInfo: PluginInfo, val capability: Capability, val position: Offset, val density: Float = 1f) : FlowEvent
    data class AddSystemNode(val systemAction: String, val position: Offset, val density: Float = 1f) : FlowEvent
    data class AddFlowInputNode(val position: Offset, val density: Float = 1f) : FlowEvent
    data class AddFlowOutputNode(val position: Offset, val density: Float = 1f) : FlowEvent
    data class AddSubFlowNode(val flowName: String, val position: Offset, val density: Float = 1f) : FlowEvent
    
    data class MoveNode(val id: Long, val delta: Offset, val snap: Boolean = false, val showGhost: Boolean = false) : FlowEvent
    data class EndMoveNode(val id: Long, val density: Float = 1f) : FlowEvent
    data class DeleteNode(val id: Long) : FlowEvent
    
    data class ConnectPorts(val sourceNodeId: Long, val sourcePortId: String, val targetNodeId: Long, val targetPortId: String) : FlowEvent
    data class AutoConvertAndConnect(val sourceNodeId: Long, val sourcePortId: String, val targetNodeId: Long, val targetPortId: String) : FlowEvent
    data class DeleteConnection(val connection: Connection) : FlowEvent
    
    data class Pan(val delta: Offset) : FlowEvent
    data class Zoom(val delta: Float, val focusPosition: Offset) : FlowEvent
    data class SetZoom(val scale: Float) : FlowEvent
    data object ResetBoard : FlowEvent

    // Flow Management
    data class SelectFlow(val flowName: String) : FlowEvent
    data class CreateFlow(val name: String) : FlowEvent
    data class RenameFlow(val oldName: String, val newName: String) : FlowEvent
    data class DeleteFlow(val name: String) : FlowEvent
    data class ExpandSubFlow(val nodeId: Long) : FlowEvent
    
    data object Save : FlowEvent
    data class SaveAs(val name: String) : FlowEvent
    data class UpdateInputPortValue(val nodeId: Long, val portId: String, val value: Any?) : FlowEvent
    data class UpdateBoundaryNode(val nodeId: Long, val portName: String, val dataType: DataType, val semanticType: String?) : FlowEvent
    data class BringToFront(val nodeId: Long) : FlowEvent

    // Selection
    data class SelectNodes(val ids: Set<Long>) : FlowEvent
    data object ClearSelection : FlowEvent
    data object DeleteSelectedNodes : FlowEvent
}

class FlowViewModel(
    private val settingsPersistence: SettingsPersistence? = null,
    private val notificationService: NotificationService? = null,
    private val pluginRegistry: PluginRegistry? = null
) : ViewModel() {
    
    private val resolvedSettingsPersistence: SettingsPersistence by lazy {
        settingsPersistence ?: getKoin().get()
    }

    private val resolvedNotificationService: NotificationService? by lazy {
        notificationService ?: try {
            getKoin().get()
        } catch (e: Exception) {
            null
        }
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _state = MutableStateFlow(FlowState())
    val state: StateFlow<FlowState> = _state.asStateFlow()

    init {
        reloadFlows()
    }

    private fun getFlowPath(appDataDir: String, flowName: String): Path {
        val safeName = flowName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return Path("$appDataDir/flows/$safeName.json")
    }

    fun reloadFlows() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val appDataDir = resolvedSettingsPersistence.getSettingsDir()
                val flowsDir = Path("$appDataDir/flows")
                
                // Ensure flows subdirectory exists
                if (!SystemFileSystem.exists(flowsDir)) {
                    SystemFileSystem.createDirectories(flowsDir)
                }

                // Check for legacy migration first
                val legacyFile = Path("$appDataDir/${KeepTrack.FLOWS_FILE_NAME}")
                if (SystemFileSystem.exists(legacyFile)) {
                    try {
                        val legacyContent = SystemFileSystem.source(legacyFile).buffered().use { it.readString() }
                        if (legacyContent.isNotBlank()) {
                            val loadedFlows = json.decodeFromString<List<Flow>>(legacyContent)
                            loadedFlows.forEach { flow ->
                                val targetFile = getFlowPath(appDataDir, flow.name)
                                if (!SystemFileSystem.exists(targetFile)) {
                                    val flowContent = json.encodeToString(Flow.serializer(), flow)
                                    SystemFileSystem.sink(targetFile).buffered().use { it.writeString(flowContent) }
                                }
                            }
                        }
                        val backupFile = Path("$appDataDir/${KeepTrack.FLOWS_FILE_NAME}.bak")
                        if (SystemFileSystem.exists(backupFile)) {
                            SystemFileSystem.delete(backupFile)
                        }
                        SystemFileSystem.source(legacyFile).buffered().use { source ->
                            SystemFileSystem.sink(backupFile).buffered().use { sink ->
                                val data = source.readString()
                                sink.writeString(data)
                            }
                        }
                        SystemFileSystem.delete(legacyFile)
                        Logger.i { "Legacy flows.json successfully migrated and backed up" }
                    } catch (e: Exception) {
                        Logger.e(e) { "Migration failed" }
                    }
                }

                // List flows in directory
                val loadedFlows = mutableListOf<Flow>()
                SystemFileSystem.list(flowsDir).forEach { file ->
                    if (file.name.endsWith(".json")) {
                        try {
                            val content = SystemFileSystem.source(file).buffered().use { it.readString() }
                            val flow = json.decodeFromString<Flow>(content)
                            loadedFlows.add(flow)
                        } catch (e: Exception) {
                            Logger.e(e) { "Failed to parse flow file: ${file.name}" }
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    _state.update { currentState ->
                        currentState.copy(
                            flows = loadedFlows,
                            selectedFlowId = currentState.selectedFlowId ?: loadedFlows.firstOrNull()?.name
                        )
                    }
                }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to reload flows" }
            }
        }
    }

    fun onEvent(event: FlowEvent) {
        when (event) {
            is FlowEvent.SelectFlow -> _state.update { it.copy(selectedFlowId = event.flowName) }
            is FlowEvent.CreateFlow -> handleCreateFlow(event.name)
            is FlowEvent.RenameFlow -> handleRenameFlow(event.oldName, event.newName)
            is FlowEvent.DeleteFlow -> handleDeleteFlow(event.name)
            else -> {}
        }
    }

    private fun handleCreateFlow(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newFlow = Flow(name)
                val appDataDir = resolvedSettingsPersistence.getSettingsDir()
                val targetFile = getFlowPath(appDataDir, newFlow.name)
                val flowContent = json.encodeToString(Flow.serializer(), newFlow)
                SystemFileSystem.sink(targetFile).buffered().use { it.writeString(flowContent) }
                
                reloadFlows()
            } catch (e: Exception) {
                Logger.e(e) { "Failed to create flow: $name" }
            }
        }
    }

    private fun handleRenameFlow(oldName: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val appDataDir = resolvedSettingsPersistence.getSettingsDir()
                val oldFile = getFlowPath(appDataDir, oldName)
                val newFile = getFlowPath(appDataDir, newName)
                
                if (SystemFileSystem.exists(oldFile)) {
                    val content = SystemFileSystem.source(oldFile).buffered().use { it.readString() }
                    val parsed = json.decodeFromString<Flow>(content)
                    val updatedFlow = parsed.copy(name = newName)
                    val newContent = json.encodeToString(Flow.serializer(), updatedFlow)
                    SystemFileSystem.sink(newFile).buffered().use { it.writeString(newContent) }
                    SystemFileSystem.delete(oldFile)
                }
                
                reloadFlows()
            } catch (e: Exception) {
                Logger.e(e) { "Failed to rename flow" }
            }
        }
    }

    fun isFlowRunning(flowName: String): Boolean {
        return try {
            val jobManager = getKoin().get<JobManager>()
            jobManager.jobs.value.any { job ->
                job.type == JobType.Flow &&
                job.capabilityName == flowName &&
                (job.status == JobStatus.Running || job.status == JobStatus.Queued)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun handleDeleteFlow(name: String) {
        if (isFlowRunning(name)) {
            viewModelScope.launch(Dispatchers.Main) {
                resolvedNotificationService?.toast("Cannot delete flow '$name' because it is currently running or queued.")
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val appDataDir = resolvedSettingsPersistence.getSettingsDir()
                val file = getFlowPath(appDataDir, name)
                
                val currentFlows = _state.value.flows
                val deletedFlow = currentFlows.find { it.name == name }
                
                if (deletedFlow != null) {
                    currentFlows.filter { it.name != name }.forEach { parentFlow ->
                        var updatedFlow = parentFlow
                        var foundSubflowNode: Node.SubFlowNode?
                        do {
                            foundSubflowNode = updatedFlow.nodes.find { it is Node.SubFlowNode && it.flowName == name } as? Node.SubFlowNode
                            if (foundSubflowNode != null) {
                                updatedFlow = FlowUnpacker.unpackSubflowInFlow(updatedFlow, foundSubflowNode.id, deletedFlow)
                            }
                        } while (foundSubflowNode != null)
                        
                        if (updatedFlow != parentFlow) {
                            val targetFile = getFlowPath(appDataDir, updatedFlow.name)
                            val flowContent = json.encodeToString(Flow.serializer(), updatedFlow)
                            SystemFileSystem.sink(targetFile).buffered().use { it.writeString(flowContent) }
                        }
                    }
                }

                if (SystemFileSystem.exists(file)) {
                    SystemFileSystem.delete(file)
                }
                
                reloadFlows()
            } catch (e: Exception) {
                Logger.e(e) { "Failed to delete flow: $name" }
            }
        }
    }

    fun executeFlow(flow: Flow, parameterValues: Map<String, String>) {
        viewModelScope.launch {
            try {
                val jobManager = getKoin().get<JobManager>()
                val jobId = "flow-${flow.name.replace(" ", "_")}-${Clock.System.now().toEpochMilliseconds()}"
                
                val params = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
                parameterValues.forEach { (key, value) ->
                    params[key] = kotlinx.serialization.json.JsonPrimitive(value)
                }

                val job = BackgroundJob(
                    id = jobId,
                    name = "Flow: ${flow.name}",
                    type = JobType.Flow,
                    status = JobStatus.Queued,
                    pluginId = "system",
                    capabilityName = flow.name,
                    parameters = params,
                    keepResult = true
                )

                jobManager.enqueueJob(job)
                resolvedNotificationService?.notify(
                    title = "Flow Execution Started",
                    message = "Flow '${flow.name}' is now queued for execution.",
                    type = NotificationType.Info
                )
            } catch (e: Exception) {
                Logger.e(e) { "Failed to enqueue flow execution" }
                resolvedNotificationService?.notify(
                    title = "Flow Execution Failed",
                    message = "Could not execute flow: ${e.message}",
                    type = NotificationType.Error
                )
            }
        }
    }
}
