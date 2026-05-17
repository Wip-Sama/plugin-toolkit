package org.wip.plugintoolkit.features.flows.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
import org.wip.plugintoolkit.api.isCompatibleWith
import org.wip.plugintoolkit.api.isSemanticTypeCompatible
import org.wip.plugintoolkit.core.KeepTrack
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.notification.NotificationType
import org.wip.plugintoolkit.core.notification.NotificationEvent
import org.wip.plugintoolkit.features.flows.model.*
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import androidx.lifecycle.viewModelScope
import org.wip.plugintoolkit.features.plugin.logic.PluginRegistry
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.api.PluginEntry
import kotlin.math.roundToInt

data class FlowState(
    val flows: List<Flow> = emptyList(),
    val selectedFlowId: String? = null,
    val offset: Offset = Offset.Zero,
    val scale: Float = 1f,
    val nextId: Long = 0L,
    val hasUnsavedChanges: Boolean = false,
    val draggedNodeId: Long? = null,
    val currentDragOffset: Offset = Offset.Zero,
    val ghostPosition: Offset? = null,
    val inputValues: Map<Pair<Long, String>, Any?> = emptyMap()
) {
    val currentFlow: Flow? get() = flows.find { it.name == selectedFlowId } ?: flows.firstOrNull()
}

sealed interface FlowEvent {
    data class AddCapabilityNode(val pluginInfo: PluginInfo, val capability: Capability, val position: Offset) : FlowEvent
    data class AddSystemNode(val systemAction: String, val position: Offset) : FlowEvent
    data class AddFlowInputNode(val position: Offset) : FlowEvent
    data class AddFlowOutputNode(val position: Offset) : FlowEvent
    data class AddSubFlowNode(val flowName: String, val position: Offset) : FlowEvent
    
    data class MoveNode(val id: Long, val delta: Offset, val snap: Boolean = false, val showGhost: Boolean = false) : FlowEvent
    data class EndMoveNode(val id: Long) : FlowEvent
    data class DeleteNode(val id: Long) : FlowEvent
    
    data class ConnectPorts(val sourceNodeId: Long, val sourcePortId: String, val targetNodeId: Long, val targetPortId: String) : FlowEvent
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
    data class UpdateInputPortValue(val nodeId: Long, val portId: String, val value: Any?) : FlowEvent
    data class BringToFront(val nodeId: Long) : FlowEvent
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

    private val resolvedPluginRegistry: PluginRegistry? by lazy {
        pluginRegistry ?: try {
            getKoin().get()
        } catch (e: Exception) {
            null
        }
    }

    val plugins: StateFlow<List<PluginEntry>> = resolvedPluginRegistry?.let { registry ->
        registry.installedPlugins
            .map { installed ->
                installed.filter { it.isEnabled && it.isValidated }
                    .mapNotNull { PluginLoader.getPluginById(it.pkg) }
            }
    }?.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    ) ?: MutableStateFlow(emptyList())

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _state = MutableStateFlow(FlowState(flows = emptyList()))
    val state: StateFlow<FlowState> = _state.asStateFlow()

    init {
        loadFlows()
    }

    private val gridSize = 50f
    private val zoomLevels = listOf(0.1f, 0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f, 5.0f)

    fun onEvent(event: FlowEvent) {
        when (event) {
            is FlowEvent.AddCapabilityNode -> handleAddNode(
                Node.CapabilityNode(
                    id = _state.value.nextId,
                    position = event.position.snapToGrid(),
                    pluginInfo = event.pluginInfo,
                    capability = event.capability,
                    inputs = event.capability.parameters?.map { (key, meta) ->
                        InputPort(key, key, meta.type, meta.semanticType, meta.defaultValue)
                    } ?: emptyList(),
                    outputs = listOf(OutputPort("result", "Result", event.capability.returnType, event.capability.semanticType))
                )
            )
            is FlowEvent.AddSystemNode -> {
                val action = event.systemAction.lowercase()
                val inputs = when (action) {
                    "save" -> listOf(
                        InputPort("data", "Data", DataType.Primitive(PrimitiveType.ANY)),
                        InputPort("file_path", "File Path", DataType.Primitive(PrimitiveType.STRING), defaultValue = "output.txt")
                    )
                    "load" -> listOf(
                        InputPort("file_path", "File Path", DataType.Primitive(PrimitiveType.STRING), defaultValue = "output.txt")
                    )
                    "log" -> listOf(
                        InputPort("message", "Message", DataType.Primitive(PrimitiveType.ANY))
                    )
                    "delay" -> listOf(
                        InputPort("duration", "Duration (ms)", DataType.Primitive(PrimitiveType.INT), defaultValue = 1000),
                        InputPort("input_data", "Input", DataType.Primitive(PrimitiveType.ANY))
                    )
                    else -> emptyList()
                }
                val outputs = when (action) {
                    "save" -> listOf(
                        OutputPort("success", "Success", DataType.Primitive(PrimitiveType.BOOLEAN))
                    )
                    "load" -> listOf(
                        OutputPort("data", "Data", DataType.Primitive(PrimitiveType.ANY))
                    )
                    "log" -> listOf(
                        OutputPort("output", "Output", DataType.Primitive(PrimitiveType.ANY))
                    )
                    "delay" -> listOf(
                        OutputPort("output_data", "Output", DataType.Primitive(PrimitiveType.ANY))
                    )
                    else -> emptyList()
                }
                handleAddNode(
                    Node.SystemNode(
                        id = _state.value.nextId,
                        position = event.position.snapToGrid(),
                        title = event.systemAction.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                        systemAction = event.systemAction,
                        inputs = inputs,
                        outputs = outputs
                    )
                )
            }
            is FlowEvent.AddFlowInputNode -> handleAddNode(
                Node.FlowInputNode(
                    id = _state.value.nextId,
                    position = event.position.snapToGrid(),
                    outputs = listOf(OutputPort("input_data", "Input Data", DataType.Primitive(PrimitiveType.ANY)))
                )
            )
            is FlowEvent.AddFlowOutputNode -> handleAddNode(
                Node.FlowOutputNode(
                    id = _state.value.nextId,
                    position = event.position.snapToGrid(),
                    inputs = listOf(InputPort("output_data", "Output Data", DataType.Primitive(PrimitiveType.ANY)))
                )
            )
            is FlowEvent.AddSubFlowNode -> {
                val targetFlow = _state.value.flows.find { it.name == event.flowName }
                val inputs = targetFlow?.nodes?.filterIsInstance<Node.FlowInputNode>()?.map { inputNode ->
                    val port = inputNode.outputs.firstOrNull()
                    InputPort(
                        id = inputNode.id.toString(),
                        name = port?.name ?: "Input Data",
                        dataType = port?.dataType ?: DataType.Primitive(PrimitiveType.ANY),
                        semanticType = port?.semanticType
                    )
                } ?: emptyList()
                val outputs = targetFlow?.nodes?.filterIsInstance<Node.FlowOutputNode>()?.map { outputNode ->
                    val port = outputNode.inputs.firstOrNull()
                    OutputPort(
                        id = outputNode.id.toString(),
                        name = port?.name ?: "Output Data",
                        dataType = port?.dataType ?: DataType.Primitive(PrimitiveType.ANY),
                        semanticType = port?.semanticType
                    )
                } ?: emptyList()
                handleAddNode(
                    Node.SubFlowNode(
                        id = _state.value.nextId,
                        position = event.position.snapToGrid(),
                        flowName = event.flowName,
                        inputs = inputs,
                        outputs = outputs
                    )
                )
            }
            is FlowEvent.ExpandSubFlow -> handleExpandSubFlow(event.nodeId)
            is FlowEvent.MoveNode -> handleMoveNode(event.id, event.delta, event.snap, event.showGhost)
            is FlowEvent.EndMoveNode -> handleEndMoveNode(event.id)
            is FlowEvent.DeleteNode -> handleDeleteNode(event.id)
            is FlowEvent.ConnectPorts -> handleConnectPorts(event.sourceNodeId, event.sourcePortId, event.targetNodeId, event.targetPortId)
            is FlowEvent.DeleteConnection -> handleDeleteConnection(event.connection)
            is FlowEvent.Pan -> handlePan(event.delta)
            is FlowEvent.Zoom -> handleZoom(event.delta, event.focusPosition)
            is FlowEvent.SetZoom -> handleSetZoom(event.scale)
            is FlowEvent.ResetBoard -> handleResetBoard()
            is FlowEvent.SelectFlow -> _state.update { it.copy(selectedFlowId = event.flowName) }
            is FlowEvent.CreateFlow -> handleCreateFlow(event.name)
            is FlowEvent.RenameFlow -> handleRenameFlow(event.oldName, event.newName)
            is FlowEvent.DeleteFlow -> handleDeleteFlow(event.name)
            is FlowEvent.Save -> handleSave()
            is FlowEvent.UpdateInputPortValue -> handleUpdateInputPortValue(event.nodeId, event.portId, event.value)
            is FlowEvent.BringToFront -> handleBringToFront(event.nodeId)
        }
    }

    private fun handleAddNode(node: Node) {
        _state.update { currentState ->
            val updatedFlows = currentState.flows.map { flow ->
                if (flow.name == (currentState.selectedFlowId ?: currentState.flows.firstOrNull()?.name)) {
                    flow.copy(nodes = flow.nodes + node)
                } else flow
            }
            currentState.copy(
                flows = updatedFlows,
                nextId = currentState.nextId + 1,
                hasUnsavedChanges = true
            )
        }
    }

    private fun handleMoveNode(id: Long, delta: Offset, snap: Boolean, showGhost: Boolean) {
        _state.update { currentState ->
            val scaledDelta = delta
            val newOffset = currentState.currentDragOffset + scaledDelta
            val node = currentState.currentFlow?.nodes?.find { it.id == id } ?: return@update currentState
            val ghostToSet = if (showGhost) (node.position + newOffset).snapToGrid() else null
            
            currentState.copy(
                draggedNodeId = id,
                currentDragOffset = newOffset,
                ghostPosition = ghostToSet
            )
        }
    }

    private fun handleEndMoveNode(id: Long) {
        _state.update { currentState ->
            val currentFlow = currentState.currentFlow ?: return@update currentState
            val node = currentFlow.nodes.find { it.id == id } ?: return@update currentState
            val finalPosition = (node.position + currentState.currentDragOffset).snapToGrid()
            
            val updatedFlows = currentState.flows.map { flow ->
                if (flow.name == currentFlow.name) {
                    flow.copy(nodes = flow.nodes.map { 
                        if (it.id == id) it.copyWithPosition(finalPosition) 
                        else it 
                    })
                } else flow
            }
            currentState.copy(
                flows = updatedFlows, 
                hasUnsavedChanges = true,
                draggedNodeId = null,
                currentDragOffset = Offset.Zero,
                ghostPosition = null
            )
        }
    }

    private fun handleDeleteNode(id: Long) {
        _state.update { currentState ->
            val updatedFlows = currentState.flows.map { flow ->
                if (flow.name == (currentState.selectedFlowId ?: currentState.flows.firstOrNull()?.name)) {
                    flow.copy(
                        nodes = flow.nodes.filter { it.id != id },
                        connections = flow.connections.filter { it.sourceNodeId != id && it.targetNodeId != id }
                    )
                } else flow
            }
            currentState.copy(flows = updatedFlows, hasUnsavedChanges = true)
        }
    }

    private fun wouldCreateCycle(sourceNodeId: Long, targetNodeId: Long, connections: List<Connection>): Boolean {
        if (sourceNodeId == targetNodeId) return true

        val adjacencyList = connections.groupBy { it.sourceNodeId }.mapValues { entry -> entry.value.map { it.targetNodeId } }
        val visited = mutableSetOf<Long>()
        val queue = ArrayDeque<Long>()

        queue.add(targetNodeId)
        visited.add(targetNodeId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == sourceNodeId) {
                return true
            }
            val neighbors = adjacencyList[current] ?: emptyList()
            for (neighbor in neighbors) {
                if (neighbor !in visited) {
                    visited.add(neighbor)
                    queue.add(neighbor)
                }
            }
        }
        return false
    }

    private fun handleConnectPorts(sourceNodeId: Long, sourcePortId: String, targetNodeId: Long, targetPortId: String) {
        // Prevent connecting to self
        if (sourceNodeId == targetNodeId) return

        _state.update { currentState ->
            val currentFlow = currentState.currentFlow ?: return@update currentState
            val sourceNode = currentFlow.nodes.find { it.id == sourceNodeId }
            val targetNode = currentFlow.nodes.find { it.id == targetNodeId }
            
            if (sourceNode == null || targetNode == null) return@update currentState

            val sourcePort = sourceNode.outputs.find { it.id == sourcePortId }
            val targetPort = targetNode.inputs.find { it.id == targetPortId }

            if (sourcePort == null || targetPort == null) return@update currentState

            // Strict matching using robust compatibility layer
            if (!sourcePort.dataType.isCompatibleWith(targetPort.dataType) || 
                !isSemanticTypeCompatible(sourcePort.semanticType, targetPort.semanticType)) {
                return@update currentState
            }

            val filteredConnections = currentFlow.connections.filterNot { 
                it.targetNodeId == targetNodeId && it.targetPortId == targetPortId 
            }

            // Check if connection would create a cycle
            if (wouldCreateCycle(sourceNodeId, targetNodeId, filteredConnections)) {
                resolvedNotificationService?.toast("Cannot connect: Connecting these ports would create a loop (Directed Cyclic Graph). Enforcing Directed Acyclic Graph (DAG).")
                return@update currentState
            }

            val newConnection = Connection(sourceNodeId, sourcePortId, targetNodeId, targetPortId)
            
            val updatedFlows = currentState.flows.map { flow ->
                if (flow.name == (currentState.selectedFlowId ?: currentState.flows.firstOrNull()?.name)) {
                    flow.copy(connections = filteredConnections + newConnection)
                } else flow
            }

            currentState.copy(flows = updatedFlows, hasUnsavedChanges = true)
        }
    }

    private fun handleDeleteConnection(connection: Connection) {
        _state.update { currentState ->
            val updatedFlows = currentState.flows.map { flow ->
                if (flow.name == (currentState.selectedFlowId ?: currentState.flows.firstOrNull()?.name)) {
                    flow.copy(connections = flow.connections.filter { it != connection })
                } else flow
            }
            currentState.copy(flows = updatedFlows, hasUnsavedChanges = true)
        }
    }

    private fun handlePan(delta: Offset) {
        _state.update { currentState ->
            currentState.copy(offset = currentState.offset + delta)
        }
    }

    private fun handleZoom(delta: Float, focusPosition: Offset) {
        _state.update { currentState ->
            val currentZoom = currentState.scale
            val currentIndex = zoomLevels.indexOfFirst { it >= currentZoom }

            val newScale = if (delta > 0) {
                val newIndex = (currentIndex - 1).coerceAtLeast(0)
                zoomLevels[newIndex]
            } else {
                val newIndex =
                    (if (currentIndex == -1) zoomLevels.size - 1 else currentIndex + 1).coerceAtMost(zoomLevels.size - 1)
                zoomLevels[newIndex]
            }

            if (newScale == currentZoom) return@update currentState

            val boardFocus = (focusPosition - currentState.offset) / currentZoom
            val newOffset = focusPosition - boardFocus * newScale

            currentState.copy(scale = newScale, offset = newOffset)
        }
    }

    private fun handleSetZoom(scale: Float) {
        _state.update { currentState ->
            val newScale = zoomLevels.minByOrNull { kotlin.math.abs(it - scale) } ?: scale
            currentState.copy(scale = newScale)
        }
    }

    private fun handleResetBoard() {
        _state.update { currentState ->
            val updatedFlows = currentState.flows.map { flow ->
                if (flow.name == (currentState.selectedFlowId ?: currentState.flows.firstOrNull()?.name)) {
                    flow.copy(nodes = emptyList(), connections = emptyList())
                } else flow
            }
            currentState.copy(flows = updatedFlows, offset = Offset.Zero, scale = 1f, hasUnsavedChanges = true)
        }
    }

    private fun handleCreateFlow(name: String) {
        _state.update { it.copy(flows = it.flows + Flow(name), selectedFlowId = name, hasUnsavedChanges = true) }
    }

    private fun handleRenameFlow(oldName: String, newName: String) {
        _state.update { state ->
            state.copy(
                flows = state.flows.map { if (it.name == oldName) it.copy(name = newName) else it },
                selectedFlowId = if (state.selectedFlowId == oldName) newName else state.selectedFlowId,
                hasUnsavedChanges = true
            )
        }
    }

    private fun handleDeleteFlow(name: String) {
        _state.update { state ->
            val newFlows = state.flows.filter { it.name != name }
            state.copy(
                flows = if (newFlows.isEmpty()) listOf(Flow("Default Flow")) else newFlows,
                selectedFlowId = if (state.selectedFlowId == name) null else state.selectedFlowId,
                hasUnsavedChanges = true
            )
        }
    }

    private fun loadFlows() {
        try {
            val appDataDir = resolvedSettingsPersistence.getSettingsDir()
            val file = Path("$appDataDir/${KeepTrack.FLOWS_FILE_NAME}")
            if (SystemFileSystem.exists(file)) {
                val content = SystemFileSystem.source(file).buffered().use { it.readString() }
                if (content.isNotBlank()) {
                    val loadedFlows = json.decodeFromString<List<Flow>>(content)
                    if (loadedFlows.isNotEmpty()) {
                        val inputValues = mutableMapOf<Pair<Long, String>, Any?>()
                        loadedFlows.forEach { flow ->
                            flow.nodes.forEach { node ->
                                node.inputs.forEach { input ->
                                    if (input.value != null) {
                                        inputValues[node.id to input.id] = input.value
                                    }
                                }
                            }
                        }
                        val maxNodeId = loadedFlows.flatMap { it.nodes }.maxOfOrNull { it.id } ?: -1L
                        _state.value = FlowState(
                            flows = loadedFlows,
                            selectedFlowId = loadedFlows.first().name,
                            nextId = maxNodeId + 1,
                            inputValues = inputValues,
                            hasUnsavedChanges = false
                        )
                        return
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to load flows" }
            resolvedNotificationService?.notify(
                title = "Load Flows Failed",
                message = "Failed to load saved flows: ${e.message}. Falling back to default flow.",
                type = NotificationType.Error
            )
        }

        val defaultFlow = Flow("Default Flow")
        _state.value = FlowState(
            flows = listOf(defaultFlow),
            selectedFlowId = defaultFlow.name,
            nextId = 0L,
            hasUnsavedChanges = false
        )
    }

    private fun handleExpandSubFlow(nodeId: Long) {
        _state.update { currentState ->
            val currentFlow = currentState.currentFlow ?: return@update currentState
            val subFlowNode = currentFlow.nodes.find { it.id == nodeId } as? Node.SubFlowNode ?: return@update currentState
            val targetFlow = currentState.flows.find { it.name == subFlowNode.flowName } ?: return@update currentState

            // 1. Calculate offset for new nodes based on sub-flow node position
            val baseOffset = subFlowNode.position
            
            // 2. Clone nodes with new IDs
            var nextId = currentState.nextId
            val oldToNewId = mutableMapOf<Long, Long>()
            targetFlow.nodes.forEach { node ->
                oldToNewId[node.id] = nextId++
            }
            
            val newNodes = targetFlow.nodes.filter { 
                it !is Node.FlowInputNode && it !is Node.FlowOutputNode 
            }.map { node ->
                val newId = oldToNewId[node.id]!!
                node.copyWithPosition(node.position + baseOffset).let { 
                    when(it) {
                        is Node.CapabilityNode -> it.copy(id = newId)
                        is Node.SystemNode -> it.copy(id = newId)
                        is Node.FlowInputNode -> it.copy(id = newId)
                        is Node.FlowOutputNode -> it.copy(id = newId)
                        is Node.SubFlowNode -> it.copy(id = newId)
                    }
                }
            }

            // 3. Clone connections with new IDs (excluding connections to/from placeholder nodes)
            val newConnections = targetFlow.connections.filter { conn ->
                val sourceNode = targetFlow.nodes.find { it.id == conn.sourceNodeId }
                val targetNode = targetFlow.nodes.find { it.id == conn.targetNodeId }
                sourceNode !is Node.FlowInputNode && sourceNode !is Node.FlowOutputNode &&
                targetNode !is Node.FlowInputNode && targetNode !is Node.FlowOutputNode
            }.map { conn ->
                Connection(
                    sourceNodeId = oldToNewId[conn.sourceNodeId] ?: conn.sourceNodeId,
                    sourcePortId = conn.sourcePortId,
                    targetNodeId = oldToNewId[conn.targetNodeId] ?: conn.targetNodeId,
                    targetPortId = conn.targetPortId
                )
            }

            // 4. Map incoming/outgoing connections of the subflow node
            val incomingConnections = currentFlow.connections.filter { it.targetNodeId == nodeId }
            val outgoingConnections = currentFlow.connections.filter { it.sourceNodeId == nodeId }

            val mappedIncomingConnections = incomingConnections.flatMap { conn ->
                val oldInputNodeId = conn.targetPortId.toLongOrNull() ?: return@flatMap emptyList()
                val internalConns = targetFlow.connections.filter { it.sourceNodeId == oldInputNodeId }
                internalConns.mapNotNull { internalConn ->
                    val newTargetNodeId = oldToNewId[internalConn.targetNodeId] ?: return@mapNotNull null
                    Connection(
                        sourceNodeId = conn.sourceNodeId,
                        sourcePortId = conn.sourcePortId,
                        targetNodeId = newTargetNodeId,
                        targetPortId = internalConn.targetPortId
                    )
                }
            }

            val mappedOutgoingConnections = outgoingConnections.flatMap { conn ->
                val oldOutputNodeId = conn.sourcePortId.toLongOrNull() ?: return@flatMap emptyList()
                val internalConns = targetFlow.connections.filter { it.targetNodeId == oldOutputNodeId }
                internalConns.mapNotNull { internalConn ->
                    val newSourceNodeId = oldToNewId[internalConn.sourceNodeId] ?: return@mapNotNull null
                    Connection(
                        sourceNodeId = newSourceNodeId,
                        sourcePortId = internalConn.sourcePortId,
                        targetNodeId = conn.targetNodeId,
                        targetPortId = conn.targetPortId
                    )
                }
            }

            // 5. Update flow: remove sub-flow node and add expanded content
            val updatedNodes = currentFlow.nodes.filter { it.id != nodeId } + newNodes
            val externalConnections = currentFlow.connections.filter { it.sourceNodeId != nodeId && it.targetNodeId != nodeId }
            val updatedConnections = externalConnections + newConnections + mappedIncomingConnections + mappedOutgoingConnections

            val updatedFlows = currentState.flows.map { 
                if (it.name == currentFlow.name) it.copy(nodes = updatedNodes, connections = updatedConnections) 
                else it 
            }

            currentState.copy(flows = updatedFlows, nextId = nextId, hasUnsavedChanges = true)
        }
    }

    private fun handleSave() {
        try {
            val appDataDir = resolvedSettingsPersistence.getSettingsDir()
            val dir = Path(appDataDir)
            if (!SystemFileSystem.exists(dir)) {
                SystemFileSystem.createDirectories(dir)
            }
            val file = Path("$appDataDir/${KeepTrack.FLOWS_FILE_NAME}")

            val updatedFlows = _state.value.flows.map { flow ->
                flow.copy(
                    nodes = flow.nodes.map { node ->
                        node.copyWithUpdatedInputs(_state.value.inputValues)
                    }
                )
            }

            val content = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Flow.serializer()), updatedFlows)
            SystemFileSystem.sink(file).buffered().use { it.writeString(content) }

            _state.update { it.copy(flows = updatedFlows, hasUnsavedChanges = false) }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to save flows" }
            resolvedNotificationService?.notify(
                title = "Save Flows Failed",
                message = "Failed to save flows: ${e.message}",
                type = NotificationType.Error
            )
        }
    }

    private fun handleUpdateInputPortValue(nodeId: Long, portId: String, value: Any?) {
        _state.update { currentState ->
            currentState.copy(
                inputValues = currentState.inputValues + ((nodeId to portId) to value),
                hasUnsavedChanges = true
            )
        }
    }

    private fun handleBringToFront(nodeId: Long) {
        _state.update { currentState ->
            val updatedFlows = currentState.flows.map { flow ->
                if (flow.name == (currentState.selectedFlowId ?: currentState.flows.firstOrNull()?.name)) {
                    val node = flow.nodes.find { it.id == nodeId } ?: return@map flow
                    flow.copy(nodes = flow.nodes.filter { it.id != nodeId } + node)
                } else flow
            }
            currentState.copy(flows = updatedFlows)
        }
    }

    private fun Offset.snapToGrid(): Offset {
        return Offset(
            (x / gridSize).roundToInt() * gridSize,
            (y / gridSize).roundToInt() * gridSize
        )
    }
}
