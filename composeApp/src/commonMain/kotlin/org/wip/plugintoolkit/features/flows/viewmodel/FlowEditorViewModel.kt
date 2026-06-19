package org.wip.plugintoolkit.features.flows.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.mp.KoinPlatform.getKoin
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PluginEntry
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.api.isCompatibleWith
import org.wip.plugintoolkit.api.isSemanticTypeCompatible
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.FlowUnpacker
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.model.PortConstraints
import org.wip.plugintoolkit.features.flows.model.SubflowPortMapping
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.plugin.logic.PluginRegistry
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import kotlin.math.roundToInt

data class ValidationError(
    val sourceNodeId: Long,
    val sourcePortId: String,
    val targetNodeId: Long,
    val targetPortId: String,
    val message: String
)

enum class ReadOnlyReason {
    Running,
    UsedInOtherFlows
}

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
    val inferredSemanticTypes: Map<Pair<Long, String>, List<SemanticType>> = emptyMap(),
    val validationErrors: List<ValidationError> = emptyList(),
    val selectedNodeIds: Set<Long> = emptySet(),
    val isReadOnly: Boolean = false,
    val readOnlyReasons: List<ReadOnlyReason> = emptyList()
)

class FlowEditorViewModel(
    private val initialFlowName: String,
    private val flowRepository: org.wip.plugintoolkit.features.flows.logic.FlowRepository,
    private val settingsPersistence: SettingsPersistence? = null,
    private val notificationService: NotificationService? = null,
    private val pluginRegistry: PluginRegistry? = null,
    private val activeFlowEditorTracker: ActiveFlowEditorTracker? = null,
    private val settingsRepository: org.wip.plugintoolkit.features.settings.logic.SettingsRepository? = null
) : ViewModel() {

    private val resolvedSettingsRepository: org.wip.plugintoolkit.features.settings.logic.SettingsRepository? by lazy {
        settingsRepository ?: try {
            getKoin().get()
        } catch (e: Exception) {
            null
        }
    }

    private val resolvedActiveFlowEditorTracker: ActiveFlowEditorTracker by lazy {
        activeFlowEditorTracker ?: try {
            getKoin().get()
        } catch (e: Exception) {
            ActiveFlowEditorTracker()
        }
    }

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

    private val undoStack = mutableListOf<Flow>()
    private val redoStack = mutableListOf<Flow>()

    private fun saveToHistory() {
        val currentFlow = _state.value.flow
        if (undoStack.isEmpty() || undoStack.last() != currentFlow) {
            undoStack.add(currentFlow.copy())
            redoStack.clear()
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val previousFlow = undoStack.removeAt(undoStack.size - 1)
            val currentFlow = _state.value.flow
            redoStack.add(currentFlow.copy())

            _state.update { currentState ->
                currentState.copy(
                    flow = previousFlow,
                    hasUnsavedChanges = true
                )
            }
            runTypeInference()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val nextFlow = redoStack.removeAt(redoStack.size - 1)
            val currentFlow = _state.value.flow
            undoStack.add(currentFlow.copy())

            _state.update { currentState ->
                currentState.copy(
                    flow = nextFlow,
                    hasUnsavedChanges = true
                )
            }
            runTypeInference()
        }
    }

    init {
        viewModelScope.launch {
            _state.collect { currentState ->
                resolvedActiveFlowEditorTracker.setHasUnsavedChanges(currentState.hasUnsavedChanges)
            }
        }
        viewModelScope.launch {
            try {
                val jobManager = getKoin().get<JobManager>()
                jobManager.jobs.collect {
                    updateReadOnlyState()
                }
            } catch (e: Exception) {
                // Ignore if Koin or JobManager not set
            }
        }
        loadFlow()
    }

    private val gridSize = 50f
    private val zoomLevels = listOf(0.1f, 0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f, 5.0f)

    private fun loadFlow() {
        viewModelScope.launch {
            flowRepository.flows.collect { allFlows ->
                val activeFlowName = initialFlowName
                val activeFlow: Flow = if (activeFlowName.isBlank()) {
                    Flow("")
                } else {
                    allFlows.find { it.name == activeFlowName } ?: Flow(activeFlowName)
                }

                val activeFlowWithSyncedSubflows = syncSubflowNodes(activeFlow, allFlows)
                val maxNodeId = activeFlowWithSyncedSubflows.nodes.maxOfOrNull { it.id } ?: -1L

                _state.update { currentState ->
                    currentState.copy(
                        flow = activeFlowWithSyncedSubflows,
                        nextId = maxNodeId + 1,
                        flows = allFlows,
                        hasUnsavedChanges = false
                    )
                }
                updateReadOnlyState()
                runTypeInference()
            }
        }
    }

    var bypassReadOnlyForTesting: Boolean = false

    fun onEvent(event: FlowEvent) {
        if (_state.value.isReadOnly && !bypassReadOnlyForTesting) {
            when (event) {
                is FlowEvent.AddCapabilityNode,
                is FlowEvent.AddSystemNode,
                is FlowEvent.AddFlowInputNode,
                is FlowEvent.AddFlowOutputNode,
                is FlowEvent.AddSubFlowNode,
                is FlowEvent.ExpandSubFlow,
                is FlowEvent.MoveNode,
                is FlowEvent.EndMoveNode,
                is FlowEvent.DeleteNode,
                is FlowEvent.ConnectPorts,
                is FlowEvent.AutoConvertAndConnect,
                is FlowEvent.DeleteConnection,
                is FlowEvent.ResetBoard,
                is FlowEvent.Save,
                is FlowEvent.SaveAs,
                is FlowEvent.UpdateInputPortValue,
                is FlowEvent.UpdateBoundaryNode,
                is FlowEvent.DeleteSelectedNodes -> {
                    resolvedNotificationService?.toast("Cannot modify the flow because it is currently running or used as a subflow in other flows.")
                    return
                }

                else -> {}
            }
        }

        when (event) {
            is FlowEvent.AddCapabilityNode -> handleAddNode(
                Node.CapabilityNode(
                    id = _state.value.nextId,
                    position = event.position.snapToGrid(),
                    pluginInfo = event.pluginInfo,
                    capability = event.capability,
                    inputs = event.capability.parameters?.map { (key, meta) ->
                        InputPort(
                            id = key,
                            name = key,
                            description = meta.description,
                            dataType = meta.type,
                            semanticTypes = meta.semanticTypes,
                            defaultValue = meta.defaultValue,
                            constraints = meta.constraints?.let {
                                PortConstraints(regex = it.regex)
                            }
                        )
                    } ?: emptyList(),
                    outputs = event.capability.outputs?.map { out ->
                        OutputPort(
                            id = out.name,
                            name = out.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                            description = out.description,
                            dataType = out.type,
                            semanticTypes = out.semanticTypes
                        )
                    } ?: listOf(
                        OutputPort(
                            id = "result",
                            name = "Result",
                            description = "Capability Result",
                            dataType = event.capability.returnType,
                            semanticTypes = event.capability.semanticTypes
                        )
                    )
                ),
                density = event.density
            )

            is FlowEvent.AddSystemNode -> {
                val inputs = SystemNodesRegistry.getInputs(event.systemAction)
                val outputs = SystemNodesRegistry.getOutputs(event.systemAction)
                handleAddNode(
                    Node.SystemNode(
                        id = _state.value.nextId,
                        position = event.position.snapToGrid(),
                        title = event.systemAction.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                        systemAction = event.systemAction,
                        inputs = inputs,
                        outputs = outputs
                    ),
                    density = event.density
                )
            }

            is FlowEvent.AddFlowInputNode -> handleAddNode(
                Node.FlowInputNode(
                    id = _state.value.nextId,
                    position = event.position.snapToGrid(),
                    outputs = listOf(OutputPort("input_data", "Input Data", DataType.Primitive(PrimitiveType.ANY)))
                ),
                density = event.density
            )

            is FlowEvent.AddFlowOutputNode -> handleAddNode(
                Node.FlowOutputNode(
                    id = _state.value.nextId,
                    position = event.position.snapToGrid(),
                    inputs = listOf(InputPort("output_data", "Output Data", DataType.Primitive(PrimitiveType.ANY)))
                ),
                density = event.density
            )

            is FlowEvent.AddSubFlowNode -> {
                if (wouldCreateNestedFlowCycle(_state.value.flow.name, event.flowName, _state.value.flows)) {
                    Logger.w { "Failed to add subflow node: adding subflow '${event.flowName}' to '${_state.value.flow.name}' would create a nested cycle." }
                    resolvedNotificationService?.toast("Cannot add subflow: Adding this subflow would create a cyclic dependency between flows.")
                    return
                }

                val targetFlow = _state.value.flows.find { it.name == event.flowName }
                val (inputs, outputs) = if (targetFlow != null) {
                    getSubflowPorts(targetFlow)
                } else {
                    Pair(emptyList(), emptyList())
                }

                val inputMappings = targetFlow?.nodes?.filterIsInstance<Node.FlowInputNode>()?.map { inputNode ->
                    SubflowPortMapping(
                        portId = "input_${inputNode.id}",
                        boundaryNodeId = inputNode.id
                    )
                } ?: emptyList()

                val outputMappings = targetFlow?.nodes?.filterIsInstance<Node.FlowOutputNode>()?.map { outputNode ->
                    SubflowPortMapping(
                        portId = "output_${outputNode.id}",
                        boundaryNodeId = outputNode.id
                    )
                } ?: emptyList()

                handleAddNode(
                    Node.SubFlowNode(
                        id = _state.value.nextId,
                        position = event.position.snapToGrid(),
                        flowName = event.flowName,
                        inputs = inputs,
                        outputs = outputs,
                        inputMappings = inputMappings,
                        outputMappings = outputMappings
                    ),
                    density = event.density
                )
            }

            is FlowEvent.ExpandSubFlow -> handleExpandSubFlow(event.nodeId)
            is FlowEvent.MoveNode -> handleMoveNode(event.id, event.delta, event.snap, event.showGhost)
            is FlowEvent.EndMoveNode -> handleEndMoveNode(event.id, event.density)
            is FlowEvent.DeleteNode -> handleDeleteNode(event.id)
            is FlowEvent.ConnectPorts -> handleConnectPorts(
                event.sourceNodeId,
                event.sourcePortId,
                event.targetNodeId,
                event.targetPortId
            )

            is FlowEvent.AutoConvertAndConnect -> handleAutoConvertAndConnect(
                event.sourceNodeId,
                event.sourcePortId,
                event.targetNodeId,
                event.targetPortId
            )

            is FlowEvent.DeleteConnection -> handleDeleteConnection(event.connection)
            is FlowEvent.Pan -> handlePan(event.delta)
            is FlowEvent.Zoom -> handleZoom(event.delta, event.focusPosition)
            is FlowEvent.SetZoom -> handleSetZoom(event.scale)
            is FlowEvent.ResetBoard -> handleResetBoard()
            is FlowEvent.Save -> handleSave()
            is FlowEvent.SaveAs -> handleSaveAs(event.name)
            is FlowEvent.UpdateInputPortValue -> handleUpdateInputPortValue(event.nodeId, event.portId, event.value)
            is FlowEvent.UpdateBoundaryNode -> handleUpdateBoundaryNode(
                event.nodeId,
                event.portName,
                event.dataType,
                event.semanticTypes,
                event.constraints,
                event.isList
            )

            is FlowEvent.UpdateSystemNodeSettings -> handleUpdateSystemNodeSettings(
                event.nodeId,
                event.portId,
                event.semanticTypes,
                event.inputPortId,
                event.extensions
            )

            is FlowEvent.BringToFront -> handleBringToFront(event.nodeId)
            is FlowEvent.SelectNodes -> {
                _state.update { currentState ->
                    currentState.copy(selectedNodeIds = event.ids)
                }
            }

            is FlowEvent.ClearSelection -> {
                _state.update { currentState ->
                    currentState.copy(selectedNodeIds = emptySet())
                }
            }

            is FlowEvent.DeleteSelectedNodes -> handleDeleteSelectedNodes()
            is FlowEvent.ToggleNodeCollapse -> handleToggleNodeCollapse(event.nodeId)
            is FlowEvent.ToggleNodeInputsCollapse -> handleToggleNodeInputsCollapse(event.nodeId)
            is FlowEvent.ToggleNodeOutputsCollapse -> handleToggleNodeOutputsCollapse(event.nodeId)
            is FlowEvent.UpdateConnectionOrder -> handleUpdateConnectionOrder(event.connection, event.newOrderIndex)
            is FlowEvent.MoveConnectionFirst -> handleMoveConnectionFirst(event.connection)
            is FlowEvent.MoveConnectionLast -> handleMoveConnectionLast(event.connection)
            else -> {}
        }

        if (_state.value.hasUnsavedChanges && event !is FlowEvent.Save && event !is FlowEvent.SaveAs) {
            if (resolvedSettingsRepository?.settings?.value?.flows?.autosave == true) {
                handleSave()
            }
        }
    }

    private fun handleAddNode(node: Node, density: Float) {
        saveToHistory()
        _state.update { currentState ->
            val intersection =
                findCloseConnection(node, currentState.flow.nodes, currentState.flow.connections, density)

            val newConnections = if (intersection != null) {
                val (connection, ports) = intersection
                val (compatibleInput, compatibleOutput) = ports
                currentState.flow.connections.filter { it != connection } +
                        Connection(connection.sourceNodeId, connection.sourcePortId, node.id, compatibleInput.id) +
                        Connection(node.id, compatibleOutput.id, connection.targetNodeId, connection.targetPortId)
            } else {
                currentState.flow.connections
            }

            val newFlow = currentState.flow.copy(
                nodes = currentState.flow.nodes + node,
                connections = newConnections
            )
            currentState.copy(
                flow = newFlow,
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

    private fun handleEndMoveNode(id: Long, density: Float) {
        saveToHistory()
        _state.update { currentState ->
            val node = currentState.flow.nodes.find { it.id == id } ?: return@update currentState
            val finalOffset = currentState.currentDragOffset

            val isSelectedGroupMove = currentState.selectedNodeIds.contains(id)
            val nodesToMove = if (isSelectedGroupMove) currentState.selectedNodeIds else setOf(id)

            val newPositions = currentState.flow.nodes.associate {
                it.id to if (nodesToMove.contains(it.id)) (it.position + finalOffset).snapToGrid() else it.position
            }

            val updatedNodes = currentState.flow.nodes.map { n ->
                val newPos = newPositions[n.id]!!
                if (newPos != n.position) n.copyWithPosition(newPos) else n
            }

            var newConnections = currentState.flow.connections

            if (nodesToMove.size == 1) {
                val movedNode = updatedNodes.find { it.id == id }!!
                val intersection = findCloseConnection(
                    movedNode,
                    currentState.flow.nodes.filter { it.id != id },
                    currentState.flow.connections.filter { it.sourceNodeId != id && it.targetNodeId != id },
                    density
                )

                if (intersection != null) {
                    val (connection, ports) = intersection
                    val (compatibleInput, compatibleOutput) = ports
                    newConnections = currentState.flow.connections.filter { it != connection } +
                            Connection(connection.sourceNodeId, connection.sourcePortId, id, compatibleInput.id) +
                            Connection(id, compatibleOutput.id, connection.targetNodeId, connection.targetPortId)
                }
            }

            val newFlow = currentState.flow.copy(nodes = updatedNodes, connections = newConnections)

            currentState.copy(
                flow = newFlow,
                hasUnsavedChanges = true,
                draggedNodeId = null,
                currentDragOffset = Offset.Zero,
                ghostPosition = null
            )
        }
        runTypeInference()
    }

    private fun handleDeleteNode(id: Long) {
        saveToHistory()
        _state.update { currentState ->
            val newFlow = currentState.flow.copy(
                nodes = currentState.flow.nodes.filter { it.id != id },
                connections = currentState.flow.connections.filter { it.sourceNodeId != id && it.targetNodeId != id }
            )
            currentState.copy(
                flow = newFlow,
                selectedNodeIds = currentState.selectedNodeIds.filter { it != id }.toSet(),
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
    }

    private fun handleDeleteSelectedNodes() {
        val selectedIds = _state.value.selectedNodeIds
        if (selectedIds.isEmpty()) return

        saveToHistory()
        _state.update { currentState ->
            val newFlow = currentState.flow.copy(
                nodes = currentState.flow.nodes.filter { !selectedIds.contains(it.id) },
                connections = currentState.flow.connections.filter {
                    !selectedIds.contains(it.sourceNodeId) && !selectedIds.contains(it.targetNodeId)
                }
            )
            currentState.copy(
                flow = newFlow,
                selectedNodeIds = emptySet(),
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
    }

    private fun wouldCreateCycle(sourceNodeId: Long, targetNodeId: Long, connections: List<Connection>): Boolean {
        if (sourceNodeId == targetNodeId) return true

        val adjacencyList =
            connections.groupBy { it.sourceNodeId }.mapValues { entry -> entry.value.map { it.targetNodeId } }
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

    private fun hasCycle(connections: List<Connection>): Boolean {
        val adjacencyList =
            connections.groupBy { it.sourceNodeId }.mapValues { entry -> entry.value.map { it.targetNodeId } }
        val visited = mutableSetOf<Long>()
        val visiting = mutableSetOf<Long>()

        fun dfs(node: Long): Boolean {
            if (node in visiting) return true
            if (node in visited) return false

            visiting.add(node)
            val neighbors = adjacencyList[node] ?: emptyList()
            for (neighbor in neighbors) {
                if (dfs(neighbor)) return true
            }
            visiting.remove(node)
            visited.add(node)
            return false
        }

        for (node in adjacencyList.keys) {
            if (dfs(node)) return true
        }
        return false
    }

    private fun wouldCreateNestedFlowCycle(
        currentFlowName: String,
        targetFlowName: String,
        allFlows: List<Flow>
    ): Boolean {
        Logger.d { "Checking for nested flow cycle. Current: '$currentFlowName', Target: '$targetFlowName'" }
        if (currentFlowName == targetFlowName) {
            Logger.w { "Self-cycle detected! Current flow '$currentFlowName' matches target flow '$targetFlowName'." }
            return true
        }

        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()

        queue.add(targetFlowName)
        visited.add(targetFlowName)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            Logger.d { "Visiting flow '$current' in dependency graph check" }
            if (current == currentFlowName) {
                Logger.w { "Nested flow cycle detected! Flow '$currentFlowName' is reachable from '$targetFlowName'." }
                return true
            }
            val flow = allFlows.find { it.name == current } ?: continue
            val subflows = flow.nodes.filterIsInstance<Node.SubFlowNode>().map { it.flowName }
            Logger.d { "Flow '$current' contains subflows: $subflows" }
            for (subflow in subflows) {
                if (subflow !in visited) {
                    visited.add(subflow)
                    queue.add(subflow)
                }
            }
        }
        Logger.d { "No nested flow cycle detected. Adding '$targetFlowName' to '$currentFlowName' is safe." }
        return false
    }

    private fun handleConnectPorts(sourceNodeId: Long, sourcePortId: String, targetNodeId: Long, targetPortId: String) {
        if (sourceNodeId == targetNodeId) return

        saveToHistory()
        _state.update { currentState ->
            val sourceNode = currentState.flow.nodes.find { it.id == sourceNodeId }
            val targetNode = currentState.flow.nodes.find { it.id == targetNodeId }

            if (sourceNode == null || targetNode == null) return@update currentState

            val sourcePort = sourceNode.outputs.find { it.id == sourcePortId }
            val targetPort = targetNode.inputs.find { it.id == targetPortId }

            if (sourcePort == null || targetPort == null) return@update currentState

            if (!sourcePort.dataType.isCompatibleWith(targetPort.dataType) ||
                !isSemanticTypeCompatible(sourcePort.semanticTypes, targetPort.semanticTypes)
            ) {
                return@update currentState
            }

            val isList = targetPort.dataType is DataType.Array
            val filteredConnections = if (isList) {
                currentState.flow.connections
            } else {
                currentState.flow.connections.filterNot {
                    it.targetNodeId == targetNodeId && it.targetPortId == targetPortId
                }
            }

            if (filteredConnections.any { it.sourceNodeId == sourceNodeId && it.sourcePortId == sourcePortId && it.targetNodeId == targetNodeId && it.targetPortId == targetPortId }) {
                return@update currentState // Already connected exactly
            }

            if (wouldCreateCycle(sourceNodeId, targetNodeId, filteredConnections)) {
                resolvedNotificationService?.toast("Cannot connect: Connecting these ports would create a loop (Directed Cyclic Graph). Enforcing Directed Acyclic Graph (DAG).")
                return@update currentState
            }

            val orderIndex = if (isList) {
                filteredConnections.count { it.targetNodeId == targetNodeId && it.targetPortId == targetPortId }
            } else null

            val newConnection = Connection(sourceNodeId, sourcePortId, targetNodeId, targetPortId, orderIndex)

            val newFlow = currentState.flow.copy(connections = filteredConnections + newConnection)
            currentState.copy(
                flow = newFlow,
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
    }

    private fun handleAutoConvertAndConnect(
        sourceNodeId: Long,
        sourcePortId: String,
        targetNodeId: Long,
        targetPortId: String
    ) {
        if (sourceNodeId == targetNodeId) return

        saveToHistory()
        _state.update { currentState ->
            val sourceNode = currentState.flow.nodes.find { it.id == sourceNodeId }
            val targetNode = currentState.flow.nodes.find { it.id == targetNodeId }

            if (sourceNode == null || targetNode == null) return@update currentState

            val sourcePort = sourceNode.outputs.find { it.id == sourcePortId }
            val targetPort = targetNode.inputs.find { it.id == targetPortId }

            if (sourcePort == null || targetPort == null) return@update currentState

            val midPosition = Offset(
                (sourceNode.position.x + targetNode.position.x) / 2f,
                (sourceNode.position.y + targetNode.position.y) / 2f
            ).snapToGrid()

            val convertNode = Node.SystemNode(
                id = currentState.nextId,
                position = midPosition,
                title = "Convert",
                systemAction = "convert",
                inputs = SystemNodesRegistry.getInputs("convert"),
                outputs = SystemNodesRegistry.getOutputs("convert")
            )

            val conn1 = Connection(sourceNodeId, sourcePortId, convertNode.id, "input_data")
            val conn2 = Connection(convertNode.id, "output_data", targetNodeId, targetPortId)

            val filteredConnections = currentState.flow.connections.filterNot {
                it.targetNodeId == targetNodeId && it.targetPortId == targetPortId
            }

            val newFlow = currentState.flow.copy(
                nodes = currentState.flow.nodes + convertNode,
                connections = filteredConnections + conn1 + conn2
            )
            currentState.copy(
                flow = newFlow,
                nextId = currentState.nextId + 1,
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
    }

    private fun handleDeleteConnection(connection: Connection) {
        saveToHistory()
        _state.update { currentState ->
            val remainingConnections = currentState.flow.connections.filter { it != connection }.map { conn ->
                if (conn.targetNodeId == connection.targetNodeId && conn.targetPortId == connection.targetPortId && (conn.orderIndex
                        ?: 0) > (connection.orderIndex ?: 0)
                ) {
                    conn.copy(orderIndex = (conn.orderIndex ?: 0) - 1)
                } else {
                    conn
                }
            }
            currentState.copy(
                flow = currentState.flow.copy(connections = remainingConnections),
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
            val newFlow = currentState.flow.copy(nodes = emptyList(), connections = emptyList())
            currentState.copy(
                flow = newFlow,
                offset = Offset.Zero,
                scale = 1f,
                hasUnsavedChanges = true
            )
        }
    }

    private fun handleExpandSubFlow(nodeId: Long) {
        _state.update { currentState ->
            val subFlowNode =
                currentState.flow.nodes.find { it.id == nodeId } as? Node.SubFlowNode ?: return@update currentState
            val targetFlow = currentState.flows.find { it.name == subFlowNode.flowName } ?: return@update currentState

            val unpackedFlow = FlowUnpacker.unpackSubflowInFlow(currentState.flow, nodeId, targetFlow)
            if (FlowUnpacker.hasCycle(unpackedFlow.connections)) {
                resolvedNotificationService?.toast("Cannot expand subflow: Expansion would introduce a cycle. Enforcing Directed Acyclic Graph (DAG).")
                return@update currentState
            }

            val nextId = (unpackedFlow.nodes.maxOfOrNull { it.id } ?: -1L) + 1

            currentState.copy(
                flow = unpackedFlow,
                nextId = nextId,
                hasUnsavedChanges = true
            )
        }
    }

    fun updateReadOnlyState() {
        val flowName = _state.value.flow.name
        if (flowName.isBlank()) return

        val isRunning = try {
            val jobManager = getKoin().get<JobManager>()
            jobManager.jobs.value.any { job ->
                job.type == JobType.Flow &&
                        job.capabilityName == flowName &&
                        (job.status == JobStatus.Running || job.status == JobStatus.Queued)
            }
        } catch (e: Exception) {
            false
        }

        val isUsedInOtherFlows = _state.value.flows.filter { it.name != flowName }.any { parentFlow ->
            parentFlow.nodes.any { it is Node.SubFlowNode && it.flowName == flowName }
        }

        val reasons = mutableListOf<ReadOnlyReason>()
        if (isRunning) reasons.add(ReadOnlyReason.Running)
        if (isUsedInOtherFlows) reasons.add(ReadOnlyReason.UsedInOtherFlows)

        _state.update { currentState ->
            currentState.copy(
                isReadOnly = isRunning || isUsedInOtherFlows,
                readOnlyReasons = reasons
            )
        }
    }

    private fun handleSaveAs(name: String) {
        if (name.isBlank()) return
        val newFlow = _state.value.flow.copy(name = name)

        flowRepository.saveFlow(newFlow)
        _state.update {
            it.copy(
                flow = newFlow,
                hasUnsavedChanges = false
            )
        }
    }

    private fun handleSave() {
        val flowToSave = _state.value.flow
        if (flowToSave.name.isBlank()) return

        flowRepository.saveFlow(flowToSave)
        _state.update { it.copy(hasUnsavedChanges = false) }
    }

    private fun handleUpdateInputPortValue(nodeId: Long, portId: String, value: Any?) {
        saveToHistory()
        _state.update { currentState ->
            val updatedNodes = currentState.flow.nodes.map { node ->
                if (node.id == nodeId) {
                    node.copyWithUpdatedInput(
                        portId,
                        org.wip.plugintoolkit.features.flows.model.NodeSerializationUtils.anyToJsonElement(value)
                    )
                } else node
            }
            val newFlow = currentState.flow.copy(nodes = updatedNodes)
            currentState.copy(
                flow = newFlow,
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
    }

    private fun handleUpdateBoundaryNode(
        nodeId: Long,
        portName: String,
        dataType: DataType,
        semanticTypes: List<SemanticType>,
        constraints: org.wip.plugintoolkit.features.flows.model.PortConstraints?,
        isList: Boolean
    ) {
        saveToHistory()
        _state.update { currentState ->
            val updatedNodes = currentState.flow.nodes.map { node ->
                if (node.id == nodeId) {
                    when (node) {
                        is Node.FlowInputNode -> {
                            val finalDataType = if (isList) DataType.Array(dataType) else dataType
                            val port = node.outputs.first().copy(
                                name = portName,
                                dataType = finalDataType,
                                semanticTypes = semanticTypes
                            )
                            node.copy(
                                outputs = listOf(port),
                                constraints = constraints,
                                isList = isList
                            )
                        }

                        is Node.FlowOutputNode -> {
                            val finalDataType = if (isList) DataType.Array(dataType) else dataType
                            val port = node.inputs.first().copy(
                                name = portName,
                                dataType = finalDataType,
                                semanticTypes = semanticTypes
                            )
                            node.copy(inputs = listOf(port))
                        }

                        else -> node
                    }
                } else node
            }
            val newFlow = currentState.flow.copy(nodes = updatedNodes)
            currentState.copy(
                flow = newFlow,
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
    }

    private fun handleUpdateSystemNodeSettings(
        nodeId: Long,
        portId: String,
        semanticTypes: List<SemanticType>,
        inputPortId: String?,
        extensions: List<String>?
    ) {
        saveToHistory()
        _state.update { currentState ->
            val updatedNodes = currentState.flow.nodes.map { node ->
                if (node.id == nodeId && node is Node.SystemNode) {
                    val updatedOutputs = node.outputs.map { port ->
                        if (port.id == portId) {
                            port.copy(semanticTypes = semanticTypes)
                        } else port
                    }
                    val updatedInputs = if (inputPortId != null) {
                        node.inputs.map { port ->
                            if (port.id == inputPortId) {
                                val currentConstraints =
                                    port.constraints ?: org.wip.plugintoolkit.features.flows.model.PortConstraints()
                                port.copy(constraints = currentConstraints.copy(extensions = extensions))
                            } else port
                        }
                    } else node.inputs

                    node.copy(
                        outputs = updatedOutputs,
                        inputs = updatedInputs
                    )
                } else node
            }
            val newFlow = currentState.flow.copy(nodes = updatedNodes)
            currentState.copy(
                flow = newFlow,
                nextId = currentState.nextId + 1,
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
    }

    private fun handleBringToFront(nodeId: Long) {
        _state.update { currentState ->
            val node = currentState.flow.nodes.find { it.id == nodeId } ?: return@update currentState
            val isAlreadySelected = currentState.selectedNodeIds.contains(nodeId)
            val newSelection = if (isAlreadySelected) {
                currentState.selectedNodeIds
            } else {
                setOf(nodeId)
            }
            currentState.copy(
                flow = currentState.flow.copy(nodes = currentState.flow.nodes.filter { it.id != nodeId } + node),
                selectedNodeIds = newSelection
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
        val inferredSemantic = mutableMapOf<Pair<Long, String>, List<SemanticType>>()

        // Initialize with declared types and semantic types
        flow.nodes.forEach { node ->
            node.inputs.forEach { port ->
                inferred[Pair(node.id, port.id)] = port.dataType
                inferredSemantic[Pair(node.id, port.id)] = port.semanticTypes
            }
            node.outputs.forEach { port ->
                inferred[Pair(node.id, port.id)] = port.dataType
                inferredSemantic[Pair(node.id, port.id)] = port.semanticTypes
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
                        !(tgtType is DataType.Primitive && tgtType.primitiveType == PrimitiveType.ANY)
                    ) {
                        inferred[srcKey] = tgtType
                        changed = true
                    }
                    // Propagate specific types forwards to wildcard ANY targets
                    if (tgtType is DataType.Primitive && tgtType.primitiveType == PrimitiveType.ANY &&
                        !(srcType is DataType.Primitive && srcType.primitiveType == PrimitiveType.ANY)
                    ) {
                        inferred[tgtKey] = srcType
                        changed = true
                    }
                }

                // Propagate semantic types
                val srcSemantic = inferredSemantic[srcKey].orEmpty()
                val tgtSemantic = inferredSemantic[tgtKey].orEmpty()
                if (srcSemantic.isNotEmpty() && tgtSemantic.isEmpty()) {
                    inferredSemantic[tgtKey] = srcSemantic
                    changed = true
                } else if (srcSemantic.isEmpty() && tgtSemantic.isNotEmpty()) {
                    inferredSemantic[srcKey] = tgtSemantic
                    changed = true
                }
            }

            // Custom propagation for special system nodes
            flow.nodes.forEach { node ->
                if (node is Node.SystemNode) {
                    if (SystemNodesRegistry.propagateTypes(node, inferred)) {
                        changed = true
                    }
                    if (SystemNodesRegistry.propagateSemanticTypes(node, inferredSemantic)) {
                        changed = true
                    }
                }
            }
        }

        // Now compute validation errors using inferred types and semantic types!
        val errors = mutableListOf<ValidationError>()
        flow.connections.forEach { conn ->
            val srcType = inferred[Pair(conn.sourceNodeId, conn.sourcePortId)]
            val tgtType = inferred[Pair(conn.targetNodeId, conn.targetPortId)]
            val srcSemantic = inferredSemantic[Pair(conn.sourceNodeId, conn.sourcePortId)].orEmpty()
            val tgtSemantic = inferredSemantic[Pair(conn.targetNodeId, conn.targetPortId)].orEmpty()

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
                } else if (!isSemanticTypeCompatible(srcSemantic, tgtSemantic)) {
                    errors.add(
                        ValidationError(
                            sourceNodeId = conn.sourceNodeId,
                            sourcePortId = conn.sourcePortId,
                            targetNodeId = conn.targetNodeId,
                            targetPortId = conn.targetPortId,
                            message = "Semantic type mismatch: '${srcSemantic.joinToString { it.canonicalId }}' is not compatible with '${tgtSemantic.joinToString { it.canonicalId }}'"
                        )
                    )
                }
            }
        }

        // Check input ports regex validation
        flow.nodes.forEach { node ->
            node.inputs.forEach { input ->
                val regexStr = input.constraints?.regex
                if (!regexStr.isNullOrEmpty()) {
                    val rawValue = input.value ?: input.defaultValue
                    val strValue = when (rawValue) {
                        is kotlinx.serialization.json.JsonPrimitive -> {
                            if (rawValue.isString) rawValue.content else rawValue.toString()
                        }

                        null -> null
                        else -> rawValue.toString()
                    }
                    if (!strValue.isNullOrEmpty()) {
                        try {
                            val regex = Regex(regexStr)
                            val isArray = input.dataType is DataType.Array
                            val items = if (isArray) {
                                strValue.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            } else {
                                listOf(strValue)
                            }
                            for (item in items) {
                                if (!regex.matches(item)) {
                                    errors.add(
                                        ValidationError(
                                            sourceNodeId = node.id,
                                            sourcePortId = input.id,
                                            targetNodeId = node.id,
                                            targetPortId = input.id,
                                            message = if (isArray) "Item '$item' does not match pattern '$regexStr'" else "Value does not match pattern '$regexStr'"
                                        )
                                    )
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            errors.add(
                                ValidationError(
                                    sourceNodeId = node.id,
                                    sourcePortId = input.id,
                                    targetNodeId = node.id,
                                    targetPortId = input.id,
                                    message = "Invalid regex pattern: ${e.message}"
                                )
                            )
                        }
                    }
                }
            }
        }

        _state.update { currentState ->
            currentState.copy(
                inferredTypes = inferred,
                inferredSemanticTypes = inferredSemantic,
                validationErrors = errors
            )
        }
    }

    private fun getSubflowPorts(targetFlow: Flow): Pair<List<InputPort>, List<OutputPort>> {
        val inferred = mutableMapOf<Pair<Long, String>, DataType>()
        val inferredSemantic = mutableMapOf<Pair<Long, String>, List<SemanticType>>()
        targetFlow.nodes.forEach { node ->
            node.inputs.forEach { port ->
                inferred[Pair(node.id, port.id)] = port.dataType
                inferredSemantic[Pair(node.id, port.id)] = port.semanticTypes
            }
            node.outputs.forEach { port ->
                inferred[Pair(node.id, port.id)] = port.dataType
                inferredSemantic[Pair(node.id, port.id)] = port.semanticTypes
            }
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
                        !(tgtType is DataType.Primitive && tgtType.primitiveType == PrimitiveType.ANY)
                    ) {
                        inferred[srcKey] = tgtType
                        changed = true
                    }
                    if (tgtType is DataType.Primitive && tgtType.primitiveType == PrimitiveType.ANY &&
                        !(srcType is DataType.Primitive && srcType.primitiveType == PrimitiveType.ANY)
                    ) {
                        inferred[tgtKey] = srcType
                        changed = true
                    }
                }

                val srcSemantic = inferredSemantic[srcKey].orEmpty()
                val tgtSemantic = inferredSemantic[tgtKey].orEmpty()
                if (srcSemantic.isNotEmpty() && tgtSemantic.isEmpty()) {
                    inferredSemantic[tgtKey] = srcSemantic
                    changed = true
                } else if (srcSemantic.isEmpty() && tgtSemantic.isNotEmpty()) {
                    inferredSemantic[srcKey] = tgtSemantic
                    changed = true
                }
            }

            targetFlow.nodes.forEach { node ->
                if (node is Node.SystemNode) {
                    if (SystemNodesRegistry.propagateTypes(node, inferred)) {
                        changed = true
                    }
                    if (SystemNodesRegistry.propagateSemanticTypes(node, inferredSemantic)) {
                        changed = true
                    }
                }
            }
        }

        val inputs = targetFlow.nodes.filterIsInstance<Node.FlowInputNode>().map { inputNode ->
            val port = inputNode.outputs.firstOrNull()
            val inferredType =
                inferred[Pair(inputNode.id, port?.id ?: "")] ?: port?.dataType ?: DataType.Primitive(PrimitiveType.ANY)
            val inferredSem = inferredSemantic[Pair(inputNode.id, port?.id ?: "")] ?: port?.semanticTypes ?: emptyList()
            InputPort(
                id = "input_${inputNode.id}",
                name = port?.name ?: "Input Data",
                dataType = inferredType,
                semanticTypes = inferredSem
            )
        }

        val outputs = targetFlow.nodes.filterIsInstance<Node.FlowOutputNode>().map { outputNode ->
            val port = outputNode.inputs.firstOrNull()
            val inferredType =
                inferred[Pair(outputNode.id, port?.id ?: "")] ?: port?.dataType ?: DataType.Primitive(PrimitiveType.ANY)
            val inferredSem =
                inferredSemantic[Pair(outputNode.id, port?.id ?: "")] ?: port?.semanticTypes ?: emptyList()
            OutputPort(
                id = "output_${outputNode.id}",
                name = port?.name ?: "Output Data",
                dataType = inferredType,
                semanticTypes = inferredSem
            )
        }

        return Pair(inputs, outputs)
    }

    private fun DataType.format(): String {
        return when (this) {
            is DataType.Primitive -> this.primitiveType.name.lowercase().replaceFirstChar { it.uppercase() }
            is DataType.Array -> "List<${this.items.format()}>"
            is DataType.MapType -> "Map<String, ${this.valueType.format()}>"
            is DataType.Enum -> this.className.substringAfterLast('.')
            is DataType.Object -> this.className.substringAfterLast('.')
        }
    }

    private fun syncSubflowNodes(flow: Flow, allFlows: List<Flow>): Flow {
        val updatedNodes = flow.nodes.map { node ->
            if (node is Node.SubFlowNode) {
                val targetFlow = allFlows.find { it.name == node.flowName }
                if (targetFlow != null) {
                    val (newInputs, newOutputs) = getSubflowPorts(targetFlow)

                    val newInputMappings = targetFlow.nodes.filterIsInstance<Node.FlowInputNode>().map { inputNode ->
                        SubflowPortMapping(
                            portId = "input_${inputNode.id}",
                            boundaryNodeId = inputNode.id
                        )
                    }

                    val newOutputMappings = targetFlow.nodes.filterIsInstance<Node.FlowOutputNode>().map { outputNode ->
                        SubflowPortMapping(
                            portId = "output_${outputNode.id}",
                            boundaryNodeId = outputNode.id
                        )
                    }

                    node.copy(
                        inputs = newInputs,
                        outputs = newOutputs,
                        inputMappings = newInputMappings,
                        outputMappings = newOutputMappings
                    )
                } else {
                    node
                }
            } else {
                node
            }
        }
        return flow.copy(nodes = updatedNodes)
    }

    private fun getPortRelativeOffset(node: Node, portId: String, density: Float): Offset {
        val headerHeight = 48f
        val bodyTopPadding = 12f
        val rowHeight = 48f
        val spacing = 12f
        val dividerHeight = 0.5f

        val isInput = node.inputs.any { it.id == portId }
        val isOutput = node.outputs.any { it.id == portId }

        val xDp = if (isInput) 19f else 281f

        var yDp = headerHeight + bodyTopPadding
        val numInputs = node.inputs.size

        if (isInput) {
            val index = node.inputs.indexOfFirst { it.id == portId }
            if (index != -1) {
                yDp += index * (rowHeight + spacing) + (rowHeight / 2f)
            }
        } else if (isOutput) {
            val index = node.outputs.indexOfFirst { it.id == portId }
            if (index != -1) {
                var precedingHeight = numInputs * rowHeight
                var numGaps = numInputs
                if (numInputs > 0) {
                    precedingHeight += dividerHeight
                    numGaps += 1
                }
                yDp += precedingHeight + (numGaps + index) * spacing + index * rowHeight + (rowHeight / 2f)
            }
        }

        return Offset(xDp * density, yDp * density)
    }

    private fun getDistanceToBezier(p: Offset, start: Offset, end: Offset): Float {
        val controlPointOffset = kotlin.math.abs(end.x - start.x) / 2f
        val c1 = Offset(start.x + controlPointOffset, start.y)
        val c2 = Offset(end.x - controlPointOffset, end.y)

        var minDistance = Float.MAX_VALUE
        val steps = 30
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val mt = 1f - t
            val bt = start * (mt * mt * mt) +
                    c1 * (3f * mt * mt * t) +
                    c2 * (3f * mt * t * t) +
                    end * (t * t * t)
            val dist = (p - bt).getDistance()
            if (dist < minDistance) {
                minDistance = dist
            }
        }
        return minDistance
    }

    private fun isNodeCompatibleWithConnection(
        node: Node,
        sourceNode: Node,
        sourcePortId: String,
        targetNode: Node,
        targetPortId: String
    ): Pair<InputPort, OutputPort>? {
        val sourcePort = sourceNode.outputs.find { it.id == sourcePortId } ?: return null
        val targetPort = targetNode.inputs.find { it.id == targetPortId } ?: return null

        val compatibleInput = node.inputs.firstOrNull { input ->
            sourcePort.dataType.isCompatibleWith(input.dataType) &&
                    isSemanticTypeCompatible(sourcePort.semanticTypes, input.semanticTypes)
        } ?: return null

        val compatibleOutput = node.outputs.firstOrNull { output ->
            output.dataType.isCompatibleWith(targetPort.dataType) &&
                    isSemanticTypeCompatible(output.semanticTypes, targetPort.semanticTypes)
        } ?: return null

        return Pair(compatibleInput, compatibleOutput)
    }

    private fun findCloseConnection(
        node: Node,
        nodes: List<Node>,
        connections: List<Connection>,
        density: Float
    ): Pair<Connection, Pair<InputPort, OutputPort>>? {
        val nodeWidth = 300f * density
        val nodeHeight = 180f * density
        val nodeCenter = node.position + Offset(nodeWidth / 2f, nodeHeight / 2f)

        var closestConnection: Connection? = null
        var closestPorts: Pair<InputPort, OutputPort>? = null
        var minDistance = 60f * density

        connections.forEach { connection ->
            val sourceNode = nodes.find { it.id == connection.sourceNodeId } ?: return@forEach
            val targetNode = nodes.find { it.id == connection.targetNodeId } ?: return@forEach

            val sourcePortBoardPos =
                sourceNode.position + getPortRelativeOffset(sourceNode, connection.sourcePortId, density)
            val targetPortBoardPos =
                targetNode.position + getPortRelativeOffset(targetNode, connection.targetPortId, density)

            val dist = getDistanceToBezier(nodeCenter, sourcePortBoardPos, targetPortBoardPos)
            if (dist < minDistance) {
                val ports = isNodeCompatibleWithConnection(
                    node,
                    sourceNode,
                    connection.sourcePortId,
                    targetNode,
                    connection.targetPortId
                )
                if (ports != null) {
                    minDistance = dist
                    closestConnection = connection
                    closestPorts = ports
                }
            }
        }

        val conn = closestConnection
        val pts = closestPorts
        if (conn != null && pts != null) {
            return Pair(conn, pts)
        }
        return null
    }

    private fun handleToggleNodeCollapse(nodeId: Long) {
        saveToHistory()
        _state.update { currentState ->
            val updatedNodes = currentState.flow.nodes.map { node ->
                if (node.id == nodeId) {
                    val newState = !node.isCollapsed
                    node.copyWithCollapsedState(newState)
                        .copyWithInputsCollapsedState(newState)
                        .copyWithOutputsCollapsedState(newState)
                } else node
            }
            currentState.copy(flow = currentState.flow.copy(nodes = updatedNodes), hasUnsavedChanges = true)
        }
    }

    private fun handleToggleNodeInputsCollapse(nodeId: Long) {
        saveToHistory()
        _state.update { currentState ->
            val updatedNodes = currentState.flow.nodes.map { node ->
                if (node.id == nodeId) node.copyWithInputsCollapsedState(!node.isInputsCollapsed) else node
            }
            currentState.copy(flow = currentState.flow.copy(nodes = updatedNodes), hasUnsavedChanges = true)
        }
    }

    private fun handleToggleNodeOutputsCollapse(nodeId: Long) {
        saveToHistory()
        _state.update { currentState ->
            val updatedNodes = currentState.flow.nodes.map { node ->
                if (node.id == nodeId) node.copyWithOutputsCollapsedState(!node.isOutputsCollapsed) else node
            }
            currentState.copy(flow = currentState.flow.copy(nodes = updatedNodes), hasUnsavedChanges = true)
        }
    }

    private fun handleUpdateConnectionOrder(connection: Connection, newOrderIndex: Int) {
        saveToHistory()
        _state.update { currentState ->
            val targetConnections = currentState.flow.connections.filter {
                it.targetNodeId == connection.targetNodeId && it.targetPortId == connection.targetPortId
            }.sortedBy { it.orderIndex ?: 0 }.toMutableList()

            val currentIdx = targetConnections.indexOfFirst { it == connection }
            if (currentIdx == -1 || currentIdx == newOrderIndex || newOrderIndex < 0 || newOrderIndex >= targetConnections.size) return@update currentState

            val conn = targetConnections.removeAt(currentIdx)
            targetConnections.add(newOrderIndex, conn)

            // Reassign indices
            val remapped = targetConnections.mapIndexed { index, c -> c.copy(orderIndex = index) }

            val newConnections = currentState.flow.connections.map { existing ->
                remapped.find { it.sourceNodeId == existing.sourceNodeId && it.sourcePortId == existing.sourcePortId && it.targetNodeId == existing.targetNodeId && it.targetPortId == existing.targetPortId }
                    ?: existing
            }

            currentState.copy(flow = currentState.flow.copy(connections = newConnections), hasUnsavedChanges = true)
        }
    }

    private fun handleMoveConnectionFirst(connection: Connection) {
        handleUpdateConnectionOrder(connection, 0)
    }

    private fun handleMoveConnectionLast(connection: Connection) {
        val targetCount =
            _state.value.flow.connections.count { it.targetNodeId == connection.targetNodeId && it.targetPortId == connection.targetPortId }
        handleUpdateConnectionOrder(connection, targetCount - 1)
    }
}
