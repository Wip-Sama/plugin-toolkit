package org.wip.plugintoolkit.features.flows.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.koin.mp.KoinPlatform.getKoin
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.notification.NotificationType
import org.wip.plugintoolkit.core.utils.PlatformUtils
import java.util.Base64
import org.wip.plugintoolkit.features.flows.logic.FlowRepository
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.logic.PluginRegistry
import kotlin.time.Clock

enum class ConflictResolutionAction {
    RENAME, KEEP_LOCAL, KEEP_IMPORTED
}

data class FlowState(
    val flows: List<Flow> = emptyList(),
    val selectedFlowId: String? = null,
    val importConflicts: List<String> = emptyList(),
    val pendingImportedFlows: List<Flow> = emptyList()
) {
    val currentFlow: Flow? get() = flows.find { it.name == selectedFlowId } ?: flows.firstOrNull()
}

sealed interface FlowEvent {
    data class AddCapabilityNode(
        val pluginInfo: PluginInfo,
        val capability: Capability,
        val position: Offset,
        val density: Float = 1f
    ) : FlowEvent

    data class AddSystemNode(val systemAction: String, val position: Offset, val density: Float = 1f) : FlowEvent
    data class AddFlowInputNode(val position: Offset, val density: Float = 1f) : FlowEvent
    data class AddFlowOutputNode(val position: Offset, val density: Float = 1f) : FlowEvent
    data class AddSubFlowNode(val flowName: String, val position: Offset, val density: Float = 1f) : FlowEvent

    data class MoveNode(val id: Long, val delta: Offset, val snap: Boolean = false, val showGhost: Boolean = false) :
        FlowEvent

    data class EndMoveNode(val id: Long, val density: Float = 1f) : FlowEvent
    data class DeleteNode(val id: Long) : FlowEvent
    data object CopySelectedNodes : FlowEvent
    data class PasteNodes(val position: Offset) : FlowEvent

    data class ConnectPorts(
        val sourceNodeId: Long,
        val sourcePortId: String,
        val targetNodeId: Long,
        val targetPortId: String
    ) : FlowEvent

    data class TryConnectPorts(
        val sourceNodeId: Long,
        val sourcePortId: String,
        val targetNodeId: Long,
        val targetPortId: String,
        val isShiftPressed: Boolean
    ) : FlowEvent

    data object CancelPendingConnection : FlowEvent

    data class AutoConvertAndConnect(
        val sourceNodeId: Long,
        val sourcePortId: String,
        val targetNodeId: Long,
        val targetPortId: String
    ) : FlowEvent

    data class DeleteConnection(val connection: Connection) : FlowEvent

    data class Pan(val delta: Offset) : FlowEvent
    data class Zoom(val delta: Float, val focusPosition: Offset, val isShiftPressed: Boolean = false) : FlowEvent
    data class SetZoom(val scale: Float) : FlowEvent
    data object ResetBoard : FlowEvent

    // Flow Management
    data class SelectFlow(val flowName: String) : FlowEvent
    data class CreateFlow(val name: String) : FlowEvent
    data class RenameFlow(val oldName: String, val newName: String) : FlowEvent
    data class DeleteFlow(val name: String) : FlowEvent
    data class ShareFlowToClipboard(val flowName: String, val onCopyReady: (String) -> Unit) : FlowEvent
    
    data class UpdateFlowMetadata(
        val flowName: String,
        val newVersion: String,
        val newDescription: String?
    ) : FlowEvent
    data class TriggerImportFromClipboard(val base64String: String) : FlowEvent
    data class ExpandSubFlow(val nodeId: Long) : FlowEvent

    data object Save : FlowEvent
    data class SaveAs(val name: String) : FlowEvent
    data class UpdateInputPortValue(val nodeId: Long, val portId: String, val value: Any?) : FlowEvent
    data class UpdateBoundaryNode(
        val nodeId: Long,
        val portName: String,
        val dataType: DataType,
        val semanticTypes: List<SemanticType>,
        val constraints: org.wip.plugintoolkit.features.flows.model.PortConstraints? = null,
        val isList: Boolean = false,
        val isRequired: Boolean = true
    ) : FlowEvent

    data class UpdateSystemNodeSettings(
        val nodeId: Long,
        val portId: String,
        val semanticTypes: List<SemanticType>,
        val inputPortId: String? = null,
        val extensions: List<String>? = null
    ) : FlowEvent

    data class BringToFront(val nodeId: Long) : FlowEvent

    // Selection
    data class SelectNodes(val ids: Set<Long>) : FlowEvent
    data object ClearSelection : FlowEvent
    data object DeleteSelectedNodes : FlowEvent

    // Collapse & Order
    data class ToggleNodeCollapse(val nodeId: Long) : FlowEvent
    data class ToggleNodeInputsCollapse(val nodeId: Long) : FlowEvent
    data class ToggleNodeOutputsCollapse(val nodeId: Long) : FlowEvent
    data class UpdateConnectionOrder(val connection: Connection, val newOrderIndex: Int) : FlowEvent
    data class MoveConnectionFirst(val connection: Connection) : FlowEvent
    data class MoveConnectionLast(val connection: Connection) : FlowEvent

    // Import/Export Flow
    data object TriggerImport : FlowEvent
    data class ResolveImportConflicts(
        val resolutions: Map<String, ConflictResolutionAction>,
        val customNames: Map<String, String> = emptyMap()
    ) : FlowEvent

    data object CancelImport : FlowEvent
    data class ExportFlow(val flowName: String) : FlowEvent
}

class FlowViewModel(
    private val flowRepository: FlowRepository,
    private val notificationService: NotificationService? = null,
    private val pluginRegistry: PluginRegistry? = null
) : ViewModel() {
    var saveResults by mutableStateOf(true)

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
        viewModelScope.launch {
            flowRepository.flows.collect { repositoryFlows ->
                _state.update { currentState ->
                    currentState.copy(
                        flows = repositoryFlows,
                        selectedFlowId = currentState.selectedFlowId ?: repositoryFlows.firstOrNull()?.name
                    )
                }
            }
        }
    }

    fun triggerMigrationsForUpdatedPlugin(pluginId: String) {
        flowRepository.triggerMigrationsForUpdatedPlugin(pluginId)
    }

    fun onEvent(event: FlowEvent) {
        when (event) {
            is FlowEvent.SelectFlow -> _state.update { it.copy(selectedFlowId = event.flowName) }
            is FlowEvent.CreateFlow -> flowRepository.saveFlow(Flow(event.name))
            is FlowEvent.RenameFlow -> flowRepository.renameFlow(event.oldName, event.newName)
            is FlowEvent.DeleteFlow -> handleDeleteFlow(event.name)
            is FlowEvent.UpdateFlowMetadata -> flowRepository.updateFlowMetadata(event.flowName, event.newVersion, event.newDescription)
            is FlowEvent.TriggerImport -> handleTriggerImport()
            is FlowEvent.ResolveImportConflicts -> handleResolveImportConflicts(event.resolutions, event.customNames)
            is FlowEvent.CancelImport -> handleCancelImport()
            is FlowEvent.ExportFlow -> handleExportFlow(event.flowName)
            is FlowEvent.ShareFlowToClipboard -> handleShareFlowToClipboard(event.flowName, event.onCopyReady)
            is FlowEvent.TriggerImportFromClipboard -> handleTriggerImportFromClipboard(event.base64String)
            else -> {}
        }
    }

    private fun handleExportFlow(flowName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val flow = _state.value.flows.find { it.name == flowName } ?: return@launch

                val dependencies = getTransitiveSubflowDependencies(flow, _state.value.flows)
                val allExportedFlows = listOf(flow) + dependencies

                val entries =
                    allExportedFlows.associate { it.name + ".json" to json.encodeToString(Flow.serializer(), it) }
                val zipBytes = PlatformUtils.zipEntries(entries)

                withContext(Dispatchers.Main) {
                    val savedPath = PlatformUtils.saveFile(
                        baseName = flow.name,
                        extension = "zip",
                        bytes = zipBytes
                    )
                    if (savedPath != null) {
                        resolvedNotificationService?.toast("Flow exported successfully to $savedPath")
                    }
                }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to export flow: $flowName" }
                withContext(Dispatchers.Main) {
                    resolvedNotificationService?.toast("Failed to export flow: ${e.message}")
                }
            }
        }
    }

    private fun handleShareFlowToClipboard(flowName: String, onCopyReady: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val flow = _state.value.flows.find { it.name == flowName } ?: return@launch

                val dependencies = getTransitiveSubflowDependencies(flow, _state.value.flows)
                val allExportedFlows = listOf(flow) + dependencies

                val entries =
                    allExportedFlows.associate { it.name + ".json" to json.encodeToString(Flow.serializer(), it) }
                val zipBytes = PlatformUtils.zipEntries(entries)
                val base64String = Base64.getEncoder().encodeToString(zipBytes)

                withContext(Dispatchers.Main) {
                    onCopyReady(base64String)
                    resolvedNotificationService?.toast("Flow copied to clipboard successfully!")
                }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to share flow to clipboard $flowName" }
                withContext(Dispatchers.Main) {
                    resolvedNotificationService?.toast("Failed to copy flow: ${e.message}")
                }
            }
        }
    }

    private fun getTransitiveSubflowDependencies(flow: Flow, allFlows: List<Flow>): List<Flow> {
        val dependencies = mutableSetOf<Flow>()
        val toVisit = mutableListOf(flow)
        val visited = mutableSetOf<String>()

        while (toVisit.isNotEmpty()) {
            val current = toVisit.removeAt(0)
            if (current.name in visited) continue
            visited.add(current.name)
            if (current != flow) {
                dependencies.add(current)
            }

            val subflowNames = current.nodes
                .filterIsInstance<Node.SubFlowNode>()
                .map { it.flowName }

            subflowNames.forEach { subflowName ->
                val depFlow = allFlows.find { it.name == subflowName }
                if (depFlow != null && depFlow.name !in visited) {
                    toVisit.add(depFlow)
                }
            }
        }
        return dependencies.toList()
    }

    private fun handleTriggerImport() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pickedPath = PlatformUtils.pickFile(
                    title = "Import Flow Archive",
                    allowedExtensions = listOf("zip")
                ) ?: return@launch

                val bytes = PlatformUtils.readBytes(pickedPath) ?: return@launch
                val entries = PlatformUtils.unzipEntries(bytes)

                val importedFlows = mutableListOf<Flow>()
                entries.forEach { (name, content) ->
                    if (name.endsWith(".json")) {
                        try {
                            val flow = json.decodeFromString<Flow>(content)
                            importedFlows.add(flow)
                        } catch (e: Exception) {
                            Logger.e(e) { "Failed to parse imported flow entry: $name" }
                        }
                    }
                }

                if (importedFlows.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        resolvedNotificationService?.toast("No valid flows found in the selected archive.")
                    }
                    return@launch
                }

                val localFlowNames = _state.value.flows.map { it.name }.toSet()
                val conflicts = importedFlows.map { it.name }.filter { it in localFlowNames }

                if (conflicts.isEmpty()) {
                    importedFlows.forEach { flowRepository.saveFlow(it) }
                    withContext(Dispatchers.Main) {
                        resolvedNotificationService?.toast("Imported ${importedFlows.size} flow(s) successfully.")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _state.update { currentState ->
                            currentState.copy(
                                importConflicts = conflicts,
                                pendingImportedFlows = importedFlows
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to import flows" }
                withContext(Dispatchers.Main) {
                    resolvedNotificationService?.toast("Failed to import flows: ${e.message}")
                }
            }
        }
    }

    private fun handleTriggerImportFromClipboard(base64String: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = Base64.getDecoder().decode(base64String)
                val importedFlows = importFlowFromBytes(bytes, "clipboard.zip")
                if (importedFlows.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        resolvedNotificationService?.toast("No valid flows found in clipboard data.")
                    }
                }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to decode clipboard data for flow import" }
                withContext(Dispatchers.Main) {
                    resolvedNotificationService?.toast("Clipboard does not contain valid flow data.")
                }
            }
        }
    }

    fun importFlowFromBytes(bytes: ByteArray, fileName: String): List<Flow> {
        val importedFlows = mutableListOf<Flow>()
        try {
            if (fileName.endsWith(".zip")) {
                val entries = PlatformUtils.unzipEntries(bytes)
                entries.forEach { (name, content) ->
                    if (name.endsWith(".json")) {
                        try {
                            val flow = json.decodeFromString<Flow>(content)
                            importedFlows.add(flow)
                        } catch (e: Exception) {
                            Logger.e(e) { "Failed to parse imported flow entry: $name" }
                        }
                    }
                }
            } else if (fileName.endsWith(".json")) {
                try {
                    val content = bytes.decodeToString()
                    val flow = json.decodeFromString<Flow>(content)
                    importedFlows.add(flow)
                } catch (e: Exception) {
                    Logger.e(e) { "Failed to parse imported flow: $fileName" }
                }
            }

            if (importedFlows.isEmpty()) {
                viewModelScope.launch(Dispatchers.Main) {
                    resolvedNotificationService?.toast("No valid flows found in the file.")
                }
                return emptyList()
            }

            viewModelScope.launch(Dispatchers.IO) {
                val localFlowNames = _state.value.flows.map { it.name }.toSet()
                val conflicts = importedFlows.map { it.name }.filter { it in localFlowNames }

                if (conflicts.isEmpty()) {
                    importedFlows.forEach { flowRepository.saveFlow(it) }
                    withContext(Dispatchers.Main) {
                        resolvedNotificationService?.toast("Imported ${importedFlows.size} flow(s) successfully.")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _state.update { currentState ->
                            currentState.copy(
                                importConflicts = conflicts,
                                pendingImportedFlows = importedFlows
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to import downloaded flow" }
            viewModelScope.launch(Dispatchers.Main) {
                resolvedNotificationService?.toast("Failed to import downloaded flow: ${e.message}")
            }
        }
        return importedFlows
    }

    private fun handleResolveImportConflicts(
        resolutions: Map<String, ConflictResolutionAction>,
        customNames: Map<String, String>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val localFlows = _state.value.flows
                val localFlowNames = localFlows.map { it.name }.toMutableSet()
                var importedFlows = _state.value.pendingImportedFlows.toMutableList()
                val renamedFlows = mutableMapOf<String, String>()

                resolutions.forEach { (clashingName, action) ->
                    val flowIndex = importedFlows.indexOfFirst { it.name == clashingName }
                    if (flowIndex != -1) {
                        val flow = importedFlows[flowIndex]
                        when (action) {
                            ConflictResolutionAction.KEEP_LOCAL -> {
                                importedFlows.removeAt(flowIndex)
                            }

                            ConflictResolutionAction.KEEP_IMPORTED -> {}
                            ConflictResolutionAction.RENAME -> {
                                val customName = customNames[clashingName]
                                val baseRename = if (!customName.isNullOrBlank()) customName else flow.name
                                val existingNames = localFlowNames + importedFlows.map { it.name }.toSet()
                                val newName = generateUniqueFlowName(baseRename, existingNames)

                                importedFlows[flowIndex] = flow.copy(name = newName)
                                renamedFlows[flow.name] = newName
                                localFlowNames.add(newName)
                            }
                        }
                    }
                }

                if (renamedFlows.isNotEmpty()) {
                    importedFlows = importedFlows.map { flow ->
                        val updatedNodes = flow.nodes.map { node ->
                            if (node is Node.SubFlowNode && renamedFlows.containsKey(node.flowName)) {
                                node.copy(flowName = renamedFlows[node.flowName]!!)
                            } else {
                                node
                            }
                        }
                        flow.copy(nodes = updatedNodes)
                    }.toMutableList()
                }

                importedFlows.forEach { flowRepository.saveFlow(it) }

                withContext(Dispatchers.Main) {
                    _state.update { currentState ->
                        currentState.copy(
                            importConflicts = emptyList(),
                            pendingImportedFlows = emptyList()
                        )
                    }
                    resolvedNotificationService?.toast("Imported flow(s) resolved and saved.")
                }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to resolve import conflicts" }
                withContext(Dispatchers.Main) {
                    resolvedNotificationService?.toast("Failed to resolve conflicts: ${e.message}")
                }
            }
        }
    }

    private fun handleCancelImport() {
        _state.update { currentState ->
            currentState.copy(
                importConflicts = emptyList(),
                pendingImportedFlows = emptyList()
            )
        }
    }

    private fun generateUniqueFlowName(baseName: String, existingNames: Set<String>): String {
        if (baseName !in existingNames) return baseName
        var candidate = "$baseName (Imported)"
        if (candidate !in existingNames) return candidate
        var counter = 2
        while (candidate in existingNames) {
            candidate = "$baseName (Imported $counter)"
            counter++
        }
        return candidate
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
        flowRepository.deleteFlow(name)
    }

    fun executeFlow(flow: Flow, parameterValues: Map<String, String>) {
        viewModelScope.launch {
            try {
                val jobManager = getKoin().get<JobManager>()
                val jobId = "flow-${flow.name.replace(" ", "_")}-${Clock.System.now().toEpochMilliseconds()}"

                val params = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
                parameterValues.forEach { (key, value) ->
                    val parts = key.split("_", limit = 2)
                    val nodeId = parts[0].toLongOrNull()
                    val portId = parts.getOrNull(1)
                    val node = flow.nodes.find { it.id == nodeId }

                    val dataType = when (node) {
                        is Node.FlowInputNode -> {
                            val outPort = node.outputs.firstOrNull()
                            if (outPort != null) {
                                flow.getInferredDataTypeForOutput(node.id, outPort.id, outPort.dataType)
                            } else {
                                DataType.Primitive(org.wip.plugintoolkit.api.PrimitiveType.ANY)
                            }
                        }

                        is Node.SystemNode -> {
                            val targetPort =
                                if (portId != null) node.inputs.find { it.id == portId } else node.inputs.firstOrNull()
                            targetPort?.dataType
                                ?: DataType.Primitive(org.wip.plugintoolkit.api.PrimitiveType.ANY)
                        }

                        else -> DataType.Primitive(org.wip.plugintoolkit.api.PrimitiveType.ANY)
                    }

                    params[key] =
                        org.wip.plugintoolkit.features.plugin.utils.SettingsUtils.stringToJson(value, dataType)
                }

                val job = BackgroundJob(
                    id = jobId,
                    name = "Flow: ${flow.name}",
                    type = JobType.Flow,
                    status = JobStatus.Queued,
                    pluginId = "system",
                    capabilityName = flow.name,
                    parameters = params,
                    keepResult = saveResults,
                    isPausable = true
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
