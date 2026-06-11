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
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import org.koin.mp.KoinPlatform.getKoin
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.core.KeepTrack
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.notification.NotificationType
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.FlowUnpacker
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.logic.PluginRegistry
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.flows.model.PortConstraints
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
    data class UpdateBoundaryNode(val nodeId: Long, val portName: String, val dataType: DataType, val semanticTypes: List<SemanticType>, val constraints: org.wip.plugintoolkit.features.flows.model.PortConstraints? = null, val isList: Boolean = false) : FlowEvent
    data class UpdateSystemNodeSettings(val nodeId: Long, val portId: String, val semanticTypes: List<SemanticType>, val inputPortId: String? = null, val extensions: List<String>? = null) : FlowEvent
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
    data class ResolveImportConflicts(val resolutions: Map<String, ConflictResolutionAction>, val customNames: Map<String, String> = emptyMap()) : FlowEvent
    data object CancelImport : FlowEvent
    data class ExportFlow(val flowName: String) : FlowEvent
}

class FlowViewModel(
    private val settingsPersistence: SettingsPersistence? = null,
    private val notificationService: NotificationService? = null,
    private val pluginRegistry: PluginRegistry? = null
) : ViewModel() {
    var saveResults by mutableStateOf(true)
    
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
            is FlowEvent.TriggerImport -> handleTriggerImport()
            is FlowEvent.ResolveImportConflicts -> handleResolveImportConflicts(event.resolutions, event.customNames)
            is FlowEvent.CancelImport -> handleCancelImport()
            is FlowEvent.ExportFlow -> handleExportFlow(event.flowName)
            else -> {}
        }
    }

    private fun handleExportFlow(flowName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val flow = _state.value.flows.find { it.name == flowName } ?: return@launch
                
                // Get all transitively dependent subflows
                val dependencies = getTransitiveSubflowDependencies(flow, _state.value.flows)
                val allExportedFlows = listOf(flow) + dependencies
                
                // Serialize each flow to JSON
                val entries = allExportedFlows.associate { it.name + ".json" to json.encodeToString(Flow.serializer(), it) }
                
                // Zip entries
                val zipBytes = PlatformUtils.zipEntries(entries)
                
                // Prompt user to save the file
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
                // Pick a .zip file
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
                
                // Find clashes with local flow names
                val localFlowNames = _state.value.flows.map { it.name }.toSet()
                val conflicts = importedFlows.map { it.name }.filter { it in localFlowNames }
                
                if (conflicts.isEmpty()) {
                    // No conflicts, write all directly
                    saveImportedFlows(importedFlows)
                    withContext(Dispatchers.Main) {
                        resolvedNotificationService?.toast("Imported ${importedFlows.size} flow(s) successfully.")
                    }
                } else {
                    // Show conflicts dialog
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
                    saveImportedFlows(importedFlows)
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

    private suspend fun saveImportedFlows(flows: List<Flow>) {
        val appDataDir = resolvedSettingsPersistence.getSettingsDir()
        flows.forEach { flow ->
            val targetFile = getFlowPath(appDataDir, flow.name)
            val flowContent = json.encodeToString(Flow.serializer(), flow)
            SystemFileSystem.sink(targetFile).buffered().use { it.writeString(flowContent) }
        }
        reloadFlows()
    }

    private fun handleResolveImportConflicts(resolutions: Map<String, ConflictResolutionAction>, customNames: Map<String, String>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val localFlows = _state.value.flows
                val localFlowNames = localFlows.map { it.name }.toMutableSet()
                
                // Start with pending imported flows
                var importedFlows = _state.value.pendingImportedFlows.toMutableList()
                
                // Track renames we perform: oldName -> newName
                val renamedFlows = mutableMapOf<String, String>()
                
                // Process each resolution
                resolutions.forEach { (clashingName, action) ->
                    val flowIndex = importedFlows.indexOfFirst { it.name == clashingName }
                    if (flowIndex != -1) {
                        val flow = importedFlows[flowIndex]
                        when (action) {
                            ConflictResolutionAction.KEEP_LOCAL -> {
                                // Discard the imported flow
                                importedFlows.removeAt(flowIndex)
                            }
                            ConflictResolutionAction.KEEP_IMPORTED -> {
                                // Keep the imported flow as is (it will overwrite the local one)
                                // Nothing special to do here, it will just overwrite the file
                            }
                            ConflictResolutionAction.RENAME -> {
                                val customName = customNames[clashingName]
                                val baseRename = if (!customName.isNullOrBlank()) customName else flow.name
                                
                                // Generate a unique name
                                val existingNames = localFlowNames + importedFlows.map { it.name }.toSet()
                                val newName = generateUniqueFlowName(baseRename, existingNames)
                                
                                // Update name in list
                                importedFlows[flowIndex] = flow.copy(name = newName)
                                renamedFlows[flow.name] = newName
                                localFlowNames.add(newName)
                            }
                        }
                    }
                }
                
                // If we renamed any flows, we MUST update any subflow nodes referencing the old names
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
                
                // Save remaining imported flows
                saveImportedFlows(importedFlows)
                
                // Clear conflict states
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
