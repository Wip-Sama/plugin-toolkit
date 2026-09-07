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
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.features.flows.history.AddNodeCommand
import org.wip.plugintoolkit.features.flows.history.CompositeCommand
import org.wip.plugintoolkit.features.flows.history.ConnectPortsCommand
import org.wip.plugintoolkit.features.flows.history.DeleteNodesCommand
import org.wip.plugintoolkit.features.flows.history.DisconnectPortsCommand
import org.wip.plugintoolkit.features.flows.history.FlowCommand
import org.wip.plugintoolkit.features.flows.history.FlowHistoryManager
import org.wip.plugintoolkit.features.flows.history.MoveNodesCommand
import org.wip.plugintoolkit.features.flows.history.UpdateBoundaryNodeCommand
import org.wip.plugintoolkit.features.flows.history.UpdateConnectionOrderCommand
import org.wip.plugintoolkit.features.flows.history.UpdateInputPortDefaultCommand
import org.wip.plugintoolkit.features.flows.history.UpdateInputPortValueCommand
import org.wip.plugintoolkit.features.flows.history.UpdateNodeCommand
import org.wip.plugintoolkit.features.flows.history.UpdateSystemNodeSettingsCommand
import org.wip.plugintoolkit.features.flows.logic.FlowCycleDetector
import org.wip.plugintoolkit.features.flows.logic.FlowRepository
import org.wip.plugintoolkit.features.flows.logic.FlowTypeInference
import org.wip.plugintoolkit.features.flows.logic.SystemNodesRegistry
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.FlowUnpacker
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset as ModelOffset
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.model.PortConstraints
import org.wip.plugintoolkit.features.flows.model.SubflowPortMapping
import org.wip.plugintoolkit.features.flows.ui.plus
import org.wip.plugintoolkit.features.flows.ui.snapToGrid
import org.wip.plugintoolkit.features.flows.ui.toModelOffset
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.plugin.logic.PluginRegistry
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository

class FlowEditorViewModel(
    private val initialFlowName: String,
    private val flowRepository: FlowRepository,
    private val settingsPersistence: SettingsPersistence? = null,
    private val notificationService: NotificationService? = null,
    private val pluginRegistry: PluginRegistry? = null,
    private val activeFlowEditorTracker: ActiveFlowEditorTracker? = null,
    private val settingsRepository: SettingsRepository? = null
) : ViewModel() {

    companion object {
        private var clipboardNodes: List<Node> = emptyList()
        private var clipboardConnections: List<Connection> = emptyList()
    }

    private val resolvedSettingsRepository: SettingsRepository? by lazy {
        settingsRepository ?: try {
            getKoin().get()
        } catch (e: Exception) {
            null
        }
    }

    val isAutoSaveEnabled: Boolean
        get() = resolvedSettingsRepository?.settings?.value?.flows?.autosave == true

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

    private val resolvedPluginManager: org.wip.plugintoolkit.features.plugin.logic.PluginManager? by lazy {
        try {
            getKoin().get()
        } catch (e: Exception) {
            null
        }
    }

    val plugins: StateFlow<List<PluginEntry>> = resolvedPluginRegistry?.let { registry ->
        val loadedFlow = resolvedPluginManager?.loadedPlugins ?: kotlinx.coroutines.flow.flowOf(emptySet())
        kotlinx.coroutines.flow.combine(registry.installedPlugins, loadedFlow) { installed, _ ->
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

    private val nodeManager = FlowNodeManager()
    private val connectionManager = FlowConnectionManager(
        notificationService = resolvedNotificationService,
        viewModelScope = viewModelScope,
        onEvent = ::onEvent
    )

    val historyManager = FlowHistoryManager(maxStackSize = 100)
    val canUndo: StateFlow<Boolean> = historyManager.canUndo
    val canRedo: StateFlow<Boolean> = historyManager.canRedo

    fun undo() {
        if (_state.value.isReadOnly && !bypassReadOnlyForTesting) return
        val revertedState = historyManager.undo(_state.value)
        if (revertedState != null) {
            _state.value = revertedState
            runTypeInference()
        }
    }

    fun redo() {
        if (_state.value.isReadOnly && !bypassReadOnlyForTesting) return
        val redoneState = historyManager.redo(_state.value)
        if (redoneState != null) {
            _state.value = redoneState
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
                historyManager.clear()
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
                is FlowEvent.UpdateInputPortDefault,
                is FlowEvent.UpdateBoundaryNode,
                is FlowEvent.DeleteSelectedNodes,
                is FlowEvent.TryConnectPorts,
                is FlowEvent.PasteNodes,
                is FlowEvent.MoveConnectionFirst,
                is FlowEvent.MoveConnectionLast,
                is FlowEvent.UpdateConnectionOrder,
                is FlowEvent.UpdateSystemNodeSettings,
                is FlowEvent.ToggleNodeCollapse,
                is FlowEvent.ToggleNodeInputsCollapse,
                is FlowEvent.ToggleNodeOutputsCollapse,
                is FlowEvent.BringToFront -> {
                    resolvedNotificationService?.toast("Cannot modify the flow because it is currently running or used as a subflow in other flows.")
                    return
                }

                else -> {}
            }
        }

        var pendingCommand: FlowCommand? = null
        var shouldRunTypeInference = false
        val currentState = _state.value
        var newState = currentState

        when (event) {
            is FlowEvent.AddCapabilityNode -> {
                shouldRunTypeInference = true
                val node = Node.CapabilityNode(
                    id = currentState.nextId,
                    position = event.position.toModelOffset().snapToGrid(),
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
                    outputs = (event.capability.outputs?.map { out ->
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
                    )) + (event.capability.parameters?.filter { it.value.role == org.wip.plugintoolkit.api.ParameterRole.OUTPUT_LOCATION }
                        ?.map { (key, meta) ->
                            OutputPort(
                                id = key,
                                name = key.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                                description = meta.description,
                                dataType = meta.type,
                                semanticTypes = meta.semanticTypes
                            )
                        } ?: emptyList())
                )
                newState = nodeManager.handleAddNode(currentState, node, event.density)
                pendingCommand = AddNodeCommand(node)
            }

            is FlowEvent.AddSystemNode -> {
                shouldRunTypeInference = true
                val inputs = SystemNodesRegistry.getInputs(event.systemAction)
                val outputs = SystemNodesRegistry.getOutputs(event.systemAction)
                val node = Node.SystemNode(
                    id = currentState.nextId,
                    position = event.position.toModelOffset().snapToGrid(),
                    title = event.systemAction.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                    systemAction = event.systemAction,
                    inputs = inputs,
                    outputs = outputs
                )
                newState = nodeManager.handleAddNode(currentState, node, event.density)
                pendingCommand = AddNodeCommand(node)
            }

            is FlowEvent.AddFlowInputNode -> {
                shouldRunTypeInference = true
                val node = Node.FlowInputNode(
                    id = currentState.nextId,
                    position = event.position.toModelOffset().snapToGrid(),
                    outputs = listOf(OutputPort("input_data", "Input Data", DataType.Primitive(PrimitiveType.ANY)))
                )
                newState = nodeManager.handleAddNode(currentState, node, event.density)
                pendingCommand = AddNodeCommand(node)
            }

            is FlowEvent.AddFlowOutputNode -> {
                shouldRunTypeInference = true
                val node = Node.FlowOutputNode(
                    id = currentState.nextId,
                    position = event.position.toModelOffset().snapToGrid(),
                    inputs = listOf(InputPort("output_data", "Output Data", DataType.Primitive(PrimitiveType.ANY)))
                )
                newState = nodeManager.handleAddNode(currentState, node, event.density)
                pendingCommand = AddNodeCommand(node)
            }

            is FlowEvent.AddSubFlowNode -> {
                if (FlowCycleDetector.wouldCreateNestedFlowCycle(
                        currentState.flow.name,
                        event.flowName,
                        currentState.flows
                    )
                ) {
                    Logger.w { "Failed to add subflow node: adding subflow '${event.flowName}' to '${currentState.flow.name}' would create a nested cycle." }
                    resolvedNotificationService?.toast("Cannot add subflow: Adding this subflow would create a cyclic dependency between flows.")
                    return
                }

                shouldRunTypeInference = true
                val targetFlow = currentState.flows.find { it.name == event.flowName }
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

                val node = Node.SubFlowNode(
                    id = currentState.nextId,
                    position = event.position.toModelOffset().snapToGrid(),
                    flowName = event.flowName,
                    inputs = inputs,
                    outputs = outputs,
                    inputMappings = inputMappings,
                    outputMappings = outputMappings
                )
                newState = nodeManager.handleAddNode(currentState, node, event.density)
                pendingCommand = AddNodeCommand(node)
            }

            is FlowEvent.ExpandSubFlow -> handleExpandSubFlow(event.nodeId)
            is FlowEvent.MoveNode -> {
                newState = nodeManager.handleMoveNode(currentState, event.id, event.delta, event.snap, event.showGhost)
            }

            is FlowEvent.EndMoveNode -> {
                shouldRunTypeInference = true
                newState = nodeManager.handleEndMoveNode(currentState, event.id, event.density)
                val isSelectedGroupMove = currentState.selectedNodeIds.contains(event.id)
                val nodesToMove = if (isSelectedGroupMove) currentState.selectedNodeIds else setOf(event.id)
                val moves = mutableMapOf<Long, Pair<ModelOffset, ModelOffset>>()
                for (nodeId in nodesToMove) {
                    val oldPos = currentState.flow.nodes.find { it.id == nodeId }?.position
                    val newPos = newState.flow.nodes.find { it.id == nodeId }?.position
                    if (oldPos != null && newPos != null && oldPos != newPos) {
                        moves[nodeId] = oldPos to newPos
                    }
                }
                if (moves.isNotEmpty()) {
                    pendingCommand = MoveNodesCommand(moves)
                }
            }

            is FlowEvent.DeleteNode -> {
                shouldRunTypeInference = true
                val deletedNode = currentState.flow.nodes.find { it.id == event.id }
                val cascadeConns = currentState.flow.connections.filter { it.sourceNodeId == event.id || it.targetNodeId == event.id }
                newState = nodeManager.handleDeleteNode(currentState, event.id)
                if (deletedNode != null) {
                    pendingCommand = DeleteNodesCommand(listOf(deletedNode), cascadeConns)
                }
            }
            is FlowEvent.CopySelectedNodes -> {
                val selected = currentState.selectedNodeIds
                if (selected.isNotEmpty()) {
                    clipboardNodes = currentState.flow.nodes.filter { it.id in selected }
                    clipboardConnections = currentState.flow.connections.filter { 
                        it.sourceNodeId in selected && it.targetNodeId in selected 
                    }
                }
            }
            is FlowEvent.PasteNodes -> {
                if (clipboardNodes.isNotEmpty()) {
                    shouldRunTypeInference = true
                    
                    var nextId = currentState.nextId
                    val idMapping = mutableMapOf<Long, Long>()
                    
                    val newNodes = clipboardNodes.map { node ->
                        val newId = nextId++
                        idMapping[node.id] = newId
                        node.copyWithId(newId)
                    }
                    
                    // Calculate bounding box center to offset nodes to cursor position
                    val minX = newNodes.minOfOrNull { it.position.x } ?: 0f
                    val minY = newNodes.minOfOrNull { it.position.y } ?: 0f
                    val maxX = newNodes.maxOfOrNull { it.position.x } ?: 0f
                    val maxY = newNodes.maxOfOrNull { it.position.y } ?: 0f
                    val centerX = minX + (maxX - minX) / 2f
                    val centerY = minY + (maxY - minY) / 2f
                    
                    val offsetDelta = ModelOffset(event.position.x - centerX, event.position.y - centerY)
                    
                    val positionedNodes = newNodes.map { node ->
                        node.copyWithPosition((node.position + offsetDelta).snapToGrid())
                    }
                    
                    val newConnections = clipboardConnections.mapNotNull { conn ->
                        val newSourceId = idMapping[conn.sourceNodeId]
                        val newTargetId = idMapping[conn.targetNodeId]
                        if (newSourceId != null && newTargetId != null) {
                            conn.copy(
                                sourceNodeId = newSourceId,
                                targetNodeId = newTargetId
                            )
                        } else null
                    }
                    
                    newState = currentState.copy(
                        nextId = nextId,
                        flow = currentState.flow.copy(
                            nodes = currentState.flow.nodes + positionedNodes,
                            connections = currentState.flow.connections + newConnections
                        ),
                        selectedNodeIds = positionedNodes.map { it.id }.toSet()
                    )
                    val addNodeCommands = positionedNodes.map { AddNodeCommand(it) }
                    val addConnCommands = newConnections.map { ConnectPortsCommand(it) }
                    pendingCommand = CompositeCommand("Paste ${positionedNodes.size} node(s)", addNodeCommands + addConnCommands)
                }
            }

            is FlowEvent.ConnectPorts -> {
                shouldRunTypeInference = true
                newState = connectionManager.handleConnectPorts(
                    currentState,
                    event.sourceNodeId,
                    event.sourcePortId,
                    event.targetNodeId,
                    event.targetPortId
                )
                if (newState !== currentState) {
                    val addedConn = newState.flow.connections.firstOrNull { it !in currentState.flow.connections }
                    val removedConns = currentState.flow.connections.filter { it !in newState.flow.connections }
                    if (addedConn != null) {
                        pendingCommand = ConnectPortsCommand(addedConn, removedConns)
                    }
                }
            }

            is FlowEvent.TryConnectPorts -> {
                newState = connectionManager.handleTryConnectPorts(
                    currentState,
                    event.sourceNodeId,
                    event.sourcePortId,
                    event.targetNodeId,
                    event.targetPortId,
                    event.isShiftPressed
                )
            }

            is FlowEvent.CancelPendingConnection -> {
                newState = currentState.copy(pendingConnection = null)
            }

            is FlowEvent.AutoConvertAndConnect -> {
                shouldRunTypeInference = true
                newState = connectionManager.handleAutoConvertAndConnect(
                    currentState,
                    event.sourceNodeId,
                    event.sourcePortId,
                    event.targetNodeId,
                    event.targetPortId
                )
                if (newState !== currentState) {
                    val addedNode = newState.flow.nodes.firstOrNull { it !in currentState.flow.nodes }
                    val addedConns = newState.flow.connections.filter { it !in currentState.flow.connections }
                    val removedConns = currentState.flow.connections.filter { it !in newState.flow.connections }
                    val commands = mutableListOf<FlowCommand>()
                    if (addedNode != null) {
                        commands.add(AddNodeCommand(addedNode))
                    }
                    addedConns.forEach { conn ->
                        commands.add(ConnectPortsCommand(conn, if (conn == addedConns.last()) removedConns else emptyList()))
                    }
                    pendingCommand = CompositeCommand("Auto convert and connect", commands)
                }
            }

            is FlowEvent.DeleteConnection -> {
                shouldRunTypeInference = true
                newState = connectionManager.handleDeleteConnection(currentState, event.connection)
                if (newState !== currentState) {
                    pendingCommand = DisconnectPortsCommand(event.connection)
                }
            }

            is FlowEvent.Pan -> handlePan(event.delta)
            is FlowEvent.Zoom -> handleZoom(event.delta, event.focusPosition, event.isShiftPressed)
            is FlowEvent.SetZoom -> handleSetZoom(event.scale)
            is FlowEvent.ResetBoard -> handleResetBoard()
            is FlowEvent.Save -> handleSave()
            is FlowEvent.SaveAs -> handleSaveAs(event.name)
            is FlowEvent.UpdateInputPortValue -> {
                // Throttled: no history save for every keystroke
                shouldRunTypeInference = true
                newState = nodeManager.handleUpdateInputPortValue(
                    currentState,
                    event.nodeId,
                    event.portId,
                    event.value
                )
            }

            is FlowEvent.UpdateInputPortDefault -> {
                shouldRunTypeInference = true
                val oldNode = currentState.flow.nodes.find { it.id == event.nodeId }
                val oldDefault = (oldNode as? Node.FlowInputNode)?.defaultValue
                    ?: oldNode?.inputs?.find { it.id == event.portId }?.defaultValue
                newState = nodeManager.handleUpdateInputPortDefault(
                    currentState,
                    event.nodeId,
                    event.portId,
                    event.defaultValue
                )
                if (oldDefault != event.defaultValue) {
                    pendingCommand = UpdateInputPortDefaultCommand(event.nodeId, event.portId, oldDefault, event.defaultValue)
                }
            }

            is FlowEvent.UpdateBoundaryNode -> {
                shouldRunTypeInference = true
                val oldNode = currentState.flow.nodes.find { it.id == event.nodeId }
                newState = nodeManager.handleUpdateBoundaryNode(
                    currentState,
                    event.nodeId,
                    event.portName,
                    event.dataType,
                    event.semanticTypes,
                    event.constraints,
                    event.isList,
                    event.isRequired,
                    event.defaultValue
                )
                val newNode = newState.flow.nodes.find { it.id == event.nodeId }
                if (oldNode != null && newNode != null && oldNode != newNode) {
                    pendingCommand = UpdateBoundaryNodeCommand(event.nodeId, oldNode, newNode)
                }
            }

            is FlowEvent.UpdateSystemNodeSettings -> {
                shouldRunTypeInference = true
                val oldNode = currentState.flow.nodes.find { it.id == event.nodeId }
                newState = nodeManager.handleUpdateSystemNodeSettings(
                    currentState,
                    event.nodeId,
                    event.portId,
                    event.semanticTypes,
                    event.inputPortId,
                    event.extensions
                )
                val newNode = newState.flow.nodes.find { it.id == event.nodeId }
                if (oldNode != null && newNode != null && oldNode != newNode) {
                    pendingCommand = UpdateSystemNodeSettingsCommand(event.nodeId, oldNode, newNode)
                }
            }

            is FlowEvent.BringToFront -> {
                newState = nodeManager.handleBringToFront(currentState, event.nodeId)
            }

            is FlowEvent.SelectNodes -> {
                newState = currentState.copy(selectedNodeIds = event.ids)
            }

            is FlowEvent.ClearSelection -> {
                newState = currentState.copy(selectedNodeIds = emptySet())
            }

            is FlowEvent.DeleteSelectedNodes -> {
                shouldRunTypeInference = true
                val deletedNodes = currentState.flow.nodes.filter { it.id in currentState.selectedNodeIds }
                val cascadeConns = currentState.flow.connections.filter {
                    it.sourceNodeId in currentState.selectedNodeIds || it.targetNodeId in currentState.selectedNodeIds
                }
                newState = nodeManager.handleDeleteSelectedNodes(currentState)
                if (deletedNodes.isNotEmpty()) {
                    pendingCommand = DeleteNodesCommand(deletedNodes, cascadeConns)
                }
            }

            is FlowEvent.ToggleNodeCollapse -> {
                val oldNode = currentState.flow.nodes.find { it.id == event.nodeId }
                newState = nodeManager.handleToggleNodeCollapse(currentState, event.nodeId)
                val newNode = newState.flow.nodes.find { it.id == event.nodeId }
                if (oldNode != null && newNode != null && oldNode != newNode) {
                    pendingCommand = UpdateNodeCommand(event.nodeId, oldNode, newNode, "Toggle node collapse")
                }
            }

            is FlowEvent.ToggleNodeInputsCollapse -> {
                val oldNode = currentState.flow.nodes.find { it.id == event.nodeId }
                newState = nodeManager.handleToggleNodeInputsCollapse(currentState, event.nodeId)
                val newNode = newState.flow.nodes.find { it.id == event.nodeId }
                if (oldNode != null && newNode != null && oldNode != newNode) {
                    pendingCommand = UpdateNodeCommand(event.nodeId, oldNode, newNode, "Toggle node inputs collapse")
                }
            }

            is FlowEvent.ToggleNodeOutputsCollapse -> {
                val oldNode = currentState.flow.nodes.find { it.id == event.nodeId }
                newState = nodeManager.handleToggleNodeOutputsCollapse(currentState, event.nodeId)
                val newNode = newState.flow.nodes.find { it.id == event.nodeId }
                if (oldNode != null && newNode != null && oldNode != newNode) {
                    pendingCommand = UpdateNodeCommand(event.nodeId, oldNode, newNode, "Toggle node outputs collapse")
                }
            }

            is FlowEvent.UpdateConnectionOrder -> {
                newState = connectionManager.handleUpdateConnectionOrder(
                    currentState,
                    event.connection,
                    event.newOrderIndex
                )
                if (newState !== currentState && newState.flow.connections != currentState.flow.connections) {
                    pendingCommand = UpdateConnectionOrderCommand(currentState.flow.connections, newState.flow.connections)
                }
            }

            is FlowEvent.MoveConnectionFirst -> {
                newState = connectionManager.handleMoveConnectionFirst(currentState, event.connection)
                if (newState !== currentState && newState.flow.connections != currentState.flow.connections) {
                    pendingCommand = UpdateConnectionOrderCommand(currentState.flow.connections, newState.flow.connections)
                }
            }

            is FlowEvent.MoveConnectionLast -> {
                newState = connectionManager.handleMoveConnectionLast(currentState, event.connection)
                if (newState !== currentState && newState.flow.connections != currentState.flow.connections) {
                    pendingCommand = UpdateConnectionOrderCommand(currentState.flow.connections, newState.flow.connections)
                }
            }

            else -> {}
        }

        if (pendingCommand != null) {
            historyManager.recordExecutedCommand(pendingCommand)
        }

        if (newState !== currentState) {
            _state.value = newState
        }

        if (shouldRunTypeInference) {
            runTypeInference()
        }

        if (_state.value.hasUnsavedChanges && (!_state.value.isReadOnly || bypassReadOnlyForTesting) && event !is FlowEvent.Save && event !is FlowEvent.SaveAs) {
            if (resolvedSettingsRepository?.settings?.value?.flows?.autosave == true) {
                handleSave()
            }
        }
    }


    private fun handlePan(delta: Offset) {
        _state.update { currentState ->
            nodeManager.handlePan(currentState, delta)
        }
    }

    private fun handleZoom(delta: Float, focusPosition: Offset, isShiftPressed: Boolean) {
        _state.update { currentState ->
            val currentZoom = currentState.scale
            val factor = if (isShiftPressed) {
                if (delta > 0) 1.20f else 1f / 1.20f
            } else {
                if (delta > 0) 1.05f else 1f / 1.05f
            }
            val newScale = (currentZoom * factor).coerceIn(0.1f, 5.0f)

            // Zoom centered on mouse pointer
            val panDelta = (focusPosition - currentState.offset) * (1f - newScale / currentZoom)
            currentState.copy(
                scale = newScale,
                offset = currentState.offset + panDelta
            )
        }
    }

    private fun handleSetZoom(scale: Float) {
        _state.update { currentState ->
            val newScale = scale.coerceIn(0.1f, 5.0f)
            currentState.copy(scale = newScale)
        }
    }

    fun zoomIn() {
        val currentZoom = _state.value.scale
        val nextZoom = zoomLevels.firstOrNull { it > currentZoom + 0.01f } ?: zoomLevels.last()
        handleSetZoom(nextZoom)
    }

    fun zoomOut() {
        val currentZoom = _state.value.scale
        val nextZoom = zoomLevels.lastOrNull { it < currentZoom - 0.01f } ?: zoomLevels.first()
        handleSetZoom(nextZoom)
    }

    fun resetZoom() {
        handleSetZoom(1.0f)
    }

    private fun handleResetBoard() {
        val currentState = _state.value
        if (currentState.flow.nodes.isNotEmpty() || currentState.flow.connections.isNotEmpty()) {
            historyManager.recordExecutedCommand(
                DeleteNodesCommand(currentState.flow.nodes, currentState.flow.connections)
            )
        }
        _state.update { curr ->
            val newFlow = curr.flow.copy(nodes = emptyList(), connections = emptyList())
            curr.copy(
                flow = newFlow,
                offset = Offset.Zero,
                scale = 1f,
                hasUnsavedChanges = true
            )
        }
    }

    private fun handleExpandSubFlow(nodeId: Long) {
        val currentState = _state.value
        val subFlowNode =
            currentState.flow.nodes.find { it.id == nodeId } as? Node.SubFlowNode ?: return
        val targetFlow = currentState.flows.find { it.name == subFlowNode.flowName } ?: return

        val unpackedFlow = FlowUnpacker.unpackSubflowInFlow(currentState.flow, nodeId, targetFlow)
        if (FlowUnpacker.hasCycle(unpackedFlow.connections)) {
            resolvedNotificationService?.toast("Cannot expand subflow: Expansion would introduce a cycle. Enforcing Directed Acyclic Graph (DAG).")
            return
        }

        val deletedConns = currentState.flow.connections.filter { it !in unpackedFlow.connections }
        val addedNodes = unpackedFlow.nodes.filter { it.id != nodeId && it !in currentState.flow.nodes }
        val addedConns = unpackedFlow.connections.filter { it !in currentState.flow.connections }
        val commands = mutableListOf<FlowCommand>()
        commands.add(DeleteNodesCommand(listOf(subFlowNode), deletedConns))
        addedNodes.forEach { commands.add(AddNodeCommand(it)) }
        addedConns.forEach { commands.add(ConnectPortsCommand(it)) }
        historyManager.recordExecutedCommand(CompositeCommand("Expand subflow '${subFlowNode.flowName}'", commands))

        val nextId = (unpackedFlow.nodes.maxOfOrNull { it.id } ?: -1L) + 1

        _state.update { curr ->
            curr.copy(
                flow = unpackedFlow,
                nextId = nextId,
                hasUnsavedChanges = true
            )
        }
        runTypeInference()
    }


    internal    fun updateReadOnlyState() {
        val currentState = _state.value
        val currentFlowName = currentState.flow.name

        if (currentFlowName.isBlank()) {
            _state.update { it.copy(isReadOnly = false, readOnlyReasons = emptyList()) }
            return
        }

        var isRunning = false
        try {
            val jobManager = getKoin().get<JobManager>()
            isRunning = jobManager.jobs.value.any {
                it.type == JobType.Flow && (it.capabilityName == currentFlowName || it.pluginId == currentFlowName) && (it.status == JobStatus.Running || it.status == JobStatus.Queued)
            }
        } catch (e: Exception) {
            // Ignore
        }

        val isUsedAsSubflow = currentState.flows.any { otherFlow ->
            otherFlow.name != currentFlowName && otherFlow.nodes.filterIsInstance<Node.SubFlowNode>()
                .any { it.flowName == currentFlowName }
        }

        val reasons = mutableListOf<ReadOnlyReason>()
        if (isRunning) reasons.add(ReadOnlyReason.Running)
        if (isUsedAsSubflow) reasons.add(ReadOnlyReason.UsedInOtherFlows)

        _state.update {
            it.copy(
                isReadOnly = reasons.isNotEmpty(),
                readOnlyReasons = reasons
            )
        }
    }

    private fun handleSave() {
        if (_state.value.isReadOnly && !bypassReadOnlyForTesting) return
        val flowToSave = _state.value.flow
        if (flowToSave.name.isBlank()) return

        flowRepository.saveFlow(flowToSave)
        _state.update { it.copy(hasUnsavedChanges = false) }
    }

    private fun handleSaveAs(newName: String) {
        if (_state.value.isReadOnly && !bypassReadOnlyForTesting) return
        if (newName.isBlank()) return
        val flowToSave = _state.value.flow.copy(name = newName)

        flowRepository.saveFlow(flowToSave)
        _state.update { it.copy(hasUnsavedChanges = false) }
    }

    private fun runTypeInference() {
        val result = FlowTypeInference.runTypeInference(_state.value.flow)
        _state.update { currentState ->
            currentState.copy(
                inferredTypes = result.inferredTypes,
                inferredSemanticTypes = result.inferredSemanticTypes,
                validationErrors = result.validationErrors
            )
        }
    }

    private fun getSubflowPorts(targetFlow: Flow): Pair<List<InputPort>, List<OutputPort>> {
        return FlowTypeInference.getSubflowPorts(targetFlow)
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

}
