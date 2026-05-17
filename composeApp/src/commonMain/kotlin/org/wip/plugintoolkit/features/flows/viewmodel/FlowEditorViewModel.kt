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
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.api.isCompatibleWith
import org.wip.plugintoolkit.api.isSemanticTypeCompatible
import org.wip.plugintoolkit.core.KeepTrack
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.notification.NotificationType
import org.wip.plugintoolkit.features.flows.model.*
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.plugin.logic.PluginRegistry
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.api.PluginEntry
import kotlin.math.roundToInt

data class ValidationError(
    val sourceNodeId: Long,
    val sourcePortId: String,
    val targetNodeId: Long,
    val targetPortId: String,
    val message: String
)

data class FlowEditorState(
    val flow: Flow = Flow(""),
    val offset: Offset = Offset.Zero,
    val scale: Float = 1f,
    val nextId: Long = 0L,
    val hasUnsavedChanges: Boolean = false,
    val draggedNodeId: Long? = null,
    val currentDragOffset: Offset = Offset.Zero,
    val ghostPosition: Offset? = null,
    val flows: List<Flow> = emptyList(),
    val inferredTypes: Map<Pair<Long, String>, DataType> = emptyMap(),
    val validationErrors: List<ValidationError> = emptyList()
)

class FlowEditorViewModel(
    private val initialFlowName: String,
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

    private val _state = MutableStateFlow(FlowEditorState())
    val state: StateFlow<FlowEditorState> = _state.asStateFlow()

    init {
        loadFlow()
    }

    private val gridSize = 50f
    private val zoomLevels = listOf(0.1f, 0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f, 5.0f)

    private fun getFlowPath(appDataDir: String, flowName: String): Path {
        val safeName = flowName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return Path("$appDataDir/flows/$safeName.json")
    }

    private fun loadFlow() {
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
                        // Backup/rename the legacy file so we don't migrate again
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

                // Now list all flows in directory to populate the subflows list
                val allFlows = mutableListOf<Flow>()
                SystemFileSystem.list(flowsDir).forEach { file ->
                    if (file.name.endsWith(".json")) {
                        try {
                            val content = SystemFileSystem.source(file).buffered().use { it.readString() }
                            val flow = json.decodeFromString<Flow>(content)
                            allFlows.add(flow)
                        } catch (e: Exception) {
                            Logger.e(e) { "Failed to parse flow file: ${file.name}" }
                        }
                    }
                }

                // Determine active flow to load
                var activeFlowName = initialFlowName
                if (activeFlowName.isBlank()) {
                    activeFlowName = allFlows.firstOrNull()?.name ?: "Default Flow"
                }

                var activeFlow = allFlows.find { it.name == activeFlowName }
                if (activeFlow == null) {
                    activeFlow = Flow(activeFlowName)
                    // Save the new flow immediately in background
                    val newFile = getFlowPath(appDataDir, activeFlowName)
                    val content = json.encodeToString(Flow.serializer(), activeFlow)
                    SystemFileSystem.sink(newFile).buffered().use { it.writeString(content) }
                    allFlows.add(activeFlow)
                }

                val maxNodeId = activeFlow.nodes.maxOfOrNull { it.id } ?: -1L

                withContext(Dispatchers.Main) {
                    _state.value = FlowEditorState(
                        flow = activeFlow,
                        nextId = maxNodeId + 1,
                        flows = allFlows,
                        hasUnsavedChanges = false
                    )
                    runTypeInference()
                }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to load flow" }
                val defaultFlow = Flow("Default Flow")
                withContext(Dispatchers.Main) {
                    _state.value = FlowEditorState(
                        flow = defaultFlow,
                        flows = listOf(defaultFlow),
                        hasUnsavedChanges = false
                    )
                    resolvedNotificationService?.notify(
                        title = "Load Flow Failed",
                        message = "Failed to load flow: ${e.message}",
                        type = NotificationType.Error
                    )
                }
            }
        }
    }

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
                val (inputs, outputs) = if (targetFlow != null) {
                    getSubflowPorts(targetFlow)
                } else {
                    Pair(emptyList(), emptyList())
                }
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
            is FlowEvent.Save -> handleSave()
            is FlowEvent.UpdateInputPortValue -> handleUpdateInputPortValue(event.nodeId, event.portId, event.value)
            is FlowEvent.UpdateBoundaryNode -> handleUpdateBoundaryNode(event.nodeId, event.portName, event.dataType, event.semanticType)
            is FlowEvent.BringToFront -> handleBringToFront(event.nodeId)
            else -> {}
        }
    }

    private fun handleAddNode(node: Node) {
        _state.update { currentState ->
            currentState.copy(
                flow = currentState.flow.copy(nodes = currentState.flow.nodes + node),
                nextId = currentState.nextId + 1,
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
    }

    private fun handleMoveNode(id: Long, delta: Offset, snap: Boolean, showGhost: Boolean) {
        _state.update { currentState ->
            val newOffset = currentState.currentDragOffset + delta
            val node = currentState.flow.nodes.find { it.id == id } ?: return@update currentState
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
            val node = currentState.flow.nodes.find { it.id == id } ?: return@update currentState
            val finalPosition = (node.position + currentState.currentDragOffset).snapToGrid()
            
            currentState.copy(
                flow = currentState.flow.copy(nodes = currentState.flow.nodes.map { 
                    if (it.id == id) it.copyWithPosition(finalPosition) 
                    else it 
                }),
                hasUnsavedChanges = true,
                draggedNodeId = null,
                currentDragOffset = Offset.Zero,
                ghostPosition = null
            )
        }
    }

    private fun handleDeleteNode(id: Long) {
        _state.update { currentState ->
            currentState.copy(
                flow = currentState.flow.copy(
                    nodes = currentState.flow.nodes.filter { it.id != id },
                    connections = currentState.flow.connections.filter { it.sourceNodeId != id && it.targetNodeId != id }
                ),
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
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
        if (sourceNodeId == targetNodeId) return

        _state.update { currentState ->
            val sourceNode = currentState.flow.nodes.find { it.id == sourceNodeId }
            val targetNode = currentState.flow.nodes.find { it.id == targetNodeId }
            
            if (sourceNode == null || targetNode == null) return@update currentState

            val sourcePort = sourceNode.outputs.find { it.id == sourcePortId }
            val targetPort = targetNode.inputs.find { it.id == targetPortId }

            if (sourcePort == null || targetPort == null) return@update currentState

            if (!sourcePort.dataType.isCompatibleWith(targetPort.dataType) || 
                !isSemanticTypeCompatible(sourcePort.semanticType, targetPort.semanticType)) {
                return@update currentState
            }

            val filteredConnections = currentState.flow.connections.filterNot { 
                it.targetNodeId == targetNodeId && it.targetPortId == targetPortId 
            }

            if (wouldCreateCycle(sourceNodeId, targetNodeId, filteredConnections)) {
                resolvedNotificationService?.toast("Cannot connect: Connecting these ports would create a loop (Directed Cyclic Graph). Enforcing Directed Acyclic Graph (DAG).")
                return@update currentState
            }

            val newConnection = Connection(sourceNodeId, sourcePortId, targetNodeId, targetPortId)
            
            currentState.copy(
                flow = currentState.flow.copy(connections = filteredConnections + newConnection),
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
    }

    private fun handleDeleteConnection(connection: Connection) {
        _state.update { currentState ->
            currentState.copy(
                flow = currentState.flow.copy(connections = currentState.flow.connections.filter { it != connection }),
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
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
            currentState.copy(
                flow = currentState.flow.copy(nodes = emptyList(), connections = emptyList()),
                offset = Offset.Zero,
                scale = 1f,
                hasUnsavedChanges = true
            )
        }
    }

    private fun handleExpandSubFlow(nodeId: Long) {
        _state.update { currentState ->
            val subFlowNode = currentState.flow.nodes.find { it.id == nodeId } as? Node.SubFlowNode ?: return@update currentState
            val targetFlow = currentState.flows.find { it.name == subFlowNode.flowName } ?: return@update currentState

            val baseOffset = subFlowNode.position
            
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

            val incomingConnections = currentState.flow.connections.filter { it.targetNodeId == nodeId }
            val outgoingConnections = currentState.flow.connections.filter { it.sourceNodeId == nodeId }

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

            val updatedNodes = currentState.flow.nodes.filter { it.id != nodeId } + newNodes
            val externalConnections = currentState.flow.connections.filter { it.sourceNodeId != nodeId && it.targetNodeId != nodeId }
            val updatedConnections = externalConnections + newConnections + mappedIncomingConnections + mappedOutgoingConnections

            currentState.copy(
                flow = currentState.flow.copy(nodes = updatedNodes, connections = updatedConnections),
                nextId = nextId,
                hasUnsavedChanges = true
            )
        }
    }

    private fun handleSave() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val appDataDir = resolvedSettingsPersistence.getSettingsDir()
                val dir = Path("$appDataDir/flows")
                if (!SystemFileSystem.exists(dir)) {
                    SystemFileSystem.createDirectories(dir)
                }
                val flow = _state.value.flow
                val file = getFlowPath(appDataDir, flow.name)

                val content = json.encodeToString(Flow.serializer(), flow)
                SystemFileSystem.sink(file).buffered().use { it.writeString(content) }

                withContext(Dispatchers.Main) {
                    _state.update { it.copy(hasUnsavedChanges = false) }
                }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to save flow" }
                withContext(Dispatchers.Main) {
                    resolvedNotificationService?.notify(
                        title = "Save Flow Failed",
                        message = "Failed to save flow: ${e.message}",
                        type = NotificationType.Error
                    )
                }
            }
        }
    }

    private fun handleUpdateInputPortValue(nodeId: Long, portId: String, value: Any?) {
        _state.update { currentState ->
            val updatedNodes = currentState.flow.nodes.map { node ->
                if (node.id == nodeId) {
                    node.copyWithUpdatedInput(portId, value)
                } else node
            }
            currentState.copy(
                flow = currentState.flow.copy(nodes = updatedNodes),
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
    }

    private fun handleUpdateBoundaryNode(nodeId: Long, portName: String, dataType: DataType, semanticType: String?) {
        _state.update { currentState ->
            val oldNode = currentState.flow.nodes.find { it.id == nodeId }
            val oldPortId = when (oldNode) {
                is Node.FlowInputNode -> oldNode.outputs.firstOrNull()?.id
                is Node.FlowOutputNode -> oldNode.inputs.firstOrNull()?.id
                else -> null
            }
            val newPortId = portName.lowercase().replace(Regex("[^a-z0-9_]"), "_").ifBlank { 
                if (oldNode is Node.FlowInputNode) "input_data" else "output_data" 
            }

            val updatedNodes = currentState.flow.nodes.map { node ->
                if (node.id == nodeId) {
                    when (node) {
                        is Node.FlowInputNode -> {
                            node.copy(
                                outputs = listOf(
                                    OutputPort(
                                        id = newPortId,
                                        name = portName,
                                        dataType = dataType,
                                        semanticType = semanticType
                                    )
                                )
                            )
                        }
                        is Node.FlowOutputNode -> {
                            node.copy(
                                inputs = listOf(
                                    InputPort(
                                        id = newPortId,
                                        name = portName,
                                        dataType = dataType,
                                        semanticType = semanticType
                                    )
                                )
                            )
                        }
                        else -> node
                    }
                } else node
            }

            val updatedConnections = if (oldPortId != null && oldPortId != newPortId) {
                currentState.flow.connections.map { conn ->
                    if (conn.sourceNodeId == nodeId && conn.sourcePortId == oldPortId) {
                        conn.copy(sourcePortId = newPortId)
                    } else if (conn.targetNodeId == nodeId && conn.targetPortId == oldPortId) {
                        conn.copy(targetPortId = newPortId)
                    } else {
                        conn
                    }
                }
            } else {
                currentState.flow.connections
            }

            currentState.copy(
                flow = currentState.flow.copy(nodes = updatedNodes, connections = updatedConnections),
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
    }

    private fun handleBringToFront(nodeId: Long) {
        _state.update { currentState ->
            val node = currentState.flow.nodes.find { it.id == nodeId } ?: return@update currentState
            currentState.copy(
                flow = currentState.flow.copy(nodes = currentState.flow.nodes.filter { it.id != nodeId } + node)
            )
        }
    }

    private fun Offset.snapToGrid(): Offset {
        return Offset(
            (x / gridSize).roundToInt() * gridSize,
            (y / gridSize).roundToInt() * gridSize
        )
    }

    private fun runTypeInference() {
        val flow = _state.value.flow
        val inferred = mutableMapOf<Pair<Long, String>, DataType>()
        
        // Initialize with declared types
        flow.nodes.forEach { node ->
            node.inputs.forEach { port ->
                inferred[Pair(node.id, port.id)] = port.dataType
            }
            node.outputs.forEach { port ->
                inferred[Pair(node.id, port.id)] = port.dataType
            }
        }
        
        // Fixed-point iteration to propagate types
        var changed = true
        var iteration = 0
        val maxIterations = 10
        
        while (changed && iteration < maxIterations) {
            changed = false
            iteration++
            
            flow.connections.forEach { conn ->
                val srcKey = Pair(conn.sourceNodeId, conn.sourcePortId)
                val tgtKey = Pair(conn.targetNodeId, conn.targetPortId)
                
                val srcType = inferred[srcKey]
                val tgtType = inferred[tgtKey]
                
                if (srcType != null && tgtType != null) {
                    // Propagate specific types backwards to wildcard ANY sources
                    if (srcType is DataType.Primitive && srcType.primitiveType == PrimitiveType.ANY &&
                        !(tgtType is DataType.Primitive && tgtType.primitiveType == PrimitiveType.ANY)) {
                        inferred[srcKey] = tgtType
                        changed = true
                    }
                    // Propagate specific types forwards to wildcard ANY targets
                    if (tgtType is DataType.Primitive && tgtType.primitiveType == PrimitiveType.ANY &&
                        !(srcType is DataType.Primitive && srcType.primitiveType == PrimitiveType.ANY)) {
                        inferred[tgtKey] = srcType
                        changed = true
                    }
                }
            }
        }
        
        // Now compute validation errors using inferred types!
        val errors = mutableListOf<ValidationError>()
        flow.connections.forEach { conn ->
            val srcType = inferred[Pair(conn.sourceNodeId, conn.sourcePortId)]
            val tgtType = inferred[Pair(conn.targetNodeId, conn.targetPortId)]
            
            if (srcType != null && tgtType != null) {
                // If they are not compatible, generate an error!
                if (!srcType.isCompatibleWith(tgtType)) {
                    errors.add(
                        ValidationError(
                            sourceNodeId = conn.sourceNodeId,
                            sourcePortId = conn.sourcePortId,
                            targetNodeId = conn.targetNodeId,
                            targetPortId = conn.targetPortId,
                            message = "Type mismatch: ${srcType.format()} is not compatible with ${tgtType.format()}"
                        )
                    )
                }
            }
        }
        
        _state.update { currentState ->
            currentState.copy(
                inferredTypes = inferred,
                validationErrors = errors
            )
        }
    }

    private fun getSubflowPorts(targetFlow: Flow): Pair<List<InputPort>, List<OutputPort>> {
        val inferred = mutableMapOf<Pair<Long, String>, DataType>()
        targetFlow.nodes.forEach { node ->
            node.inputs.forEach { port -> inferred[Pair(node.id, port.id)] = port.dataType }
            node.outputs.forEach { port -> inferred[Pair(node.id, port.id)] = port.dataType }
        }
        var changed = true
        var iteration = 0
        while (changed && iteration < 10) {
            changed = false
            iteration++
            targetFlow.connections.forEach { conn ->
                val srcKey = Pair(conn.sourceNodeId, conn.sourcePortId)
                val tgtKey = Pair(conn.targetNodeId, conn.targetPortId)
                val srcType = inferred[srcKey]
                val tgtType = inferred[tgtKey]
                if (srcType != null && tgtType != null) {
                    if (srcType is DataType.Primitive && srcType.primitiveType == PrimitiveType.ANY &&
                        !(tgtType is DataType.Primitive && tgtType.primitiveType == PrimitiveType.ANY)) {
                        inferred[srcKey] = tgtType
                        changed = true
                    }
                    if (tgtType is DataType.Primitive && tgtType.primitiveType == PrimitiveType.ANY &&
                        !(srcType is DataType.Primitive && srcType.primitiveType == PrimitiveType.ANY)) {
                        inferred[tgtKey] = srcType
                        changed = true
                    }
                }
            }
        }
        
        val inputs = targetFlow.nodes.filterIsInstance<Node.FlowInputNode>().map { inputNode ->
            val port = inputNode.outputs.firstOrNull()
            val inferredType = inferred[Pair(inputNode.id, port?.id ?: "")] ?: port?.dataType ?: DataType.Primitive(PrimitiveType.ANY)
            InputPort(
                id = inputNode.id.toString(),
                name = port?.name ?: "Input Data",
                dataType = inferredType,
                semanticType = port?.semanticType
            )
        }
        
        val outputs = targetFlow.nodes.filterIsInstance<Node.FlowOutputNode>().map { outputNode ->
            val port = outputNode.inputs.firstOrNull()
            val inferredType = inferred[Pair(outputNode.id, port?.id ?: "")] ?: port?.dataType ?: DataType.Primitive(PrimitiveType.ANY)
            OutputPort(
                id = outputNode.id.toString(),
                name = port?.name ?: "Output Data",
                dataType = inferredType,
                semanticType = port?.semanticType
            )
        }
        
        return Pair(inputs, outputs)
    }

    private fun DataType.format(): String {
        return when (this) {
            is DataType.Primitive -> this.primitiveType.name.lowercase().replaceFirstChar { it.uppercase() }
            is DataType.Array -> "List<${this.items.format()}>"
            is DataType.Enum -> this.className.substringAfterLast('.')
            is DataType.Object -> this.className.substringAfterLast('.')
        }
    }
}
