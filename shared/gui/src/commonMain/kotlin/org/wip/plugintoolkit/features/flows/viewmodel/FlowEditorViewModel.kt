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
import org.wip.plugintoolkit.features.flows.history.AddGroupCommand
import org.wip.plugintoolkit.features.flows.history.AddJunctionCommand
import org.wip.plugintoolkit.features.flows.history.AddLabelCommand
import org.wip.plugintoolkit.features.flows.history.AddNodeCommand
import org.wip.plugintoolkit.features.flows.history.CompositeCommand
import org.wip.plugintoolkit.features.flows.history.ConnectPortsCommand
import org.wip.plugintoolkit.features.flows.history.DeleteGroupCommand
import org.wip.plugintoolkit.features.flows.history.DeleteJunctionCommand
import org.wip.plugintoolkit.features.flows.history.DeleteLabelCommand
import org.wip.plugintoolkit.features.flows.history.DeleteNodesCommand
import org.wip.plugintoolkit.features.flows.history.DisconnectPortsCommand
import org.wip.plugintoolkit.features.flows.history.FlowCommand
import org.wip.plugintoolkit.features.flows.history.FlowHistoryManager
import org.wip.plugintoolkit.features.flows.history.MoveBoardElementsCommand
import org.wip.plugintoolkit.features.flows.history.MoveGroupCommand
import org.wip.plugintoolkit.features.flows.history.MoveJunctionCommand
import org.wip.plugintoolkit.features.flows.history.MoveLabelCommand
import org.wip.plugintoolkit.features.flows.history.MoveNodesCommand
import org.wip.plugintoolkit.features.flows.history.PaintElementsCommand
import org.wip.plugintoolkit.features.flows.history.ResizeGroupCommand
import org.wip.plugintoolkit.features.flows.history.UpdateBoundaryNodeCommand
import org.wip.plugintoolkit.features.flows.history.UpdateConnectionOrderCommand
import org.wip.plugintoolkit.features.flows.history.UpdateGroupCommand
import org.wip.plugintoolkit.features.flows.history.UpdateInputPortDefaultCommand
import org.wip.plugintoolkit.features.flows.history.UpdateInputPortValueCommand
import org.wip.plugintoolkit.features.flows.history.UpdateLabelCommand
import org.wip.plugintoolkit.features.flows.history.UpdateNodeCommand
import org.wip.plugintoolkit.features.flows.history.UpdateWaypointsCommand
import org.wip.plugintoolkit.features.flows.history.UpdateSystemNodeSettingsCommand
import org.wip.plugintoolkit.features.flows.logic.FlowCycleDetector
import org.wip.plugintoolkit.features.flows.logic.FlowRepository
import org.wip.plugintoolkit.features.flows.logic.FlowTypeInference
import org.wip.plugintoolkit.features.flows.logic.SystemNodesRegistry
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowJunction
import org.wip.plugintoolkit.features.flows.model.FlowLabel
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
        private var clipboardGroups: List<FlowGroup> = emptyList()
        private var clipboardLabels: List<FlowLabel> = emptyList()
    }

    private val resolvedSettingsRepository: SettingsRepository? by lazy {
        settingsRepository ?: try {
            getKoin().get()
        } catch (e: Exception) {
            null
        }
    }

    val isAutoSaveEnabled: Boolean
        get() = resolvedSettingsRepository?.let { repo ->
            (repo.isLoaded.value as? Boolean ?: false) && repo.settings.value.flows.autosave
        } == true

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
        viewModelScope = viewModelScope
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
        resolvedActiveFlowEditorTracker.registerDiscardHandler {
            discardUnsavedChanges()
        }
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
                val maxPointId = activeFlowWithSyncedSubflows.junctions.maxOfOrNull { it.id } ?: -1L
                val maxId = maxOf(maxNodeId, maxPointId)

                _state.update { currentState ->
                    currentState.copy(
                        flow = activeFlowWithSyncedSubflows,
                        nextId = maxId + 1,
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
                            },
                            isAdvanced = meta.isAdvanced,
                            condition = meta.condition
                        )
                    } ?: emptyList(),
                    outputs = (event.capability.outputs?.map { out ->
                        OutputPort(
                            id = out.name,
                            name = out.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                            description = out.description,
                            dataType = out.type,
                            semanticTypes = out.semanticTypes,
                            isAdvanced = out.isAdvanced,
                            condition = out.condition
                        )
                    } ?: listOf(
                        OutputPort(
                            id = "result",
                            name = "Result",
                            description = "Capability Result",
                            dataType = event.capability.returnType,
                            semanticTypes = event.capability.semanticTypes,
                            isAdvanced = false,
                            condition = null
                        )
                    )) + (event.capability.parameters?.filter { it.value.role == org.wip.plugintoolkit.api.ParameterRole.OUTPUT_LOCATION }
                        ?.map { (key, meta) ->
                            OutputPort(
                                id = key,
                                name = key.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                                description = meta.description,
                                dataType = meta.type,
                                semanticTypes = meta.semanticTypes,
                                isAdvanced = meta.isAdvanced,
                                condition = meta.condition
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
                val finalOffset = currentState.currentDragOffset
                newState = nodeManager.handleEndMoveNode(currentState, event.id, event.density)
                val isSelectedMove = currentState.selectedNodeIds.contains(event.id) ||
                        currentState.selectedGroupIds.contains(event.id) ||
                        currentState.selectedLabelIds.contains(event.id) ||
                        currentState.selectedPointIds.contains(event.id)

                val nodesToMove = (if (isSelectedMove) currentState.selectedNodeIds else if (currentState.flow.nodes.any { it.id == event.id }) setOf(event.id) else emptySet()).toMutableSet()
                val groupsToMove = if (isSelectedMove) currentState.selectedGroupIds else if (currentState.flow.groups.any { it.id == event.id }) setOf(event.id) else emptySet()
                val labelsToMove = if (isSelectedMove) currentState.selectedLabelIds else if (currentState.flow.labels.any { it.id == event.id }) setOf(event.id) else emptySet()
                val pointsToMove = if (isSelectedMove) currentState.selectedPointIds else if (currentState.flow.junctions.any { it.id == event.id }) setOf(event.id) else emptySet()

                for (grpId in groupsToMove) {
                    currentState.flow.groups.find { it.id == grpId }?.let { nodesToMove.addAll(it.nodeIds) }
                }

                val moves = mutableMapOf<Long, Pair<ModelOffset, ModelOffset>>()
                for (nodeId in nodesToMove) {
                    val oldPos = currentState.flow.nodes.find { it.id == nodeId }?.position
                    val newPos = newState.flow.nodes.find { it.id == nodeId }?.position
                    if (oldPos != null && newPos != null && oldPos != newPos) {
                        moves[nodeId] = oldPos to newPos
                    }
                }

                // Check group containment for moved nodes:
                // Only nodes dropped inside a group become bound, and nodes dropped outside become unbound!
                val updatedGroups = newState.flow.groups.map { baseGrp ->
                    val currentlyBound = baseGrp.nodeIds.toMutableSet()
                    for (nodeId in nodesToMove) {
                        val node = newState.flow.nodes.find { it.id == nodeId }
                        if (node != null) {
                            val isInside = node.position.x >= baseGrp.position.x &&
                                    node.position.x <= baseGrp.position.x + baseGrp.size.x &&
                                    node.position.y >= baseGrp.position.y &&
                                    node.position.y <= baseGrp.position.y + baseGrp.size.y
                            if (isInside) {
                                currentlyBound.add(nodeId)
                            } else {
                                currentlyBound.remove(nodeId)
                            }
                        }
                    }
                    baseGrp.copy(nodeIds = currentlyBound.toList())
                }

                val groupMoves = mutableMapOf<Long, Pair<ModelOffset, ModelOffset>>()
                for (grpId in groupsToMove) {
                    val oldPos = currentState.flow.groups.find { it.id == grpId }?.position
                    val newPos = updatedGroups.find { it.id == grpId }?.position
                    if (oldPos != null && newPos != null && oldPos != newPos) {
                        groupMoves[grpId] = oldPos to newPos
                    }
                }

                val labelMoves = mutableMapOf<Long, Pair<ModelOffset, ModelOffset>>()
                for (lblId in labelsToMove) {
                    val oldPos = currentState.flow.labels.find { it.id == lblId }?.position
                    val newPos = newState.flow.labels.find { it.id == lblId }?.position
                    if (oldPos != null && newPos != null && oldPos != newPos) {
                        labelMoves[lblId] = oldPos to newPos
                    }
                }

                val updatedPoints = newState.flow.junctions.map { point ->
                    if (pointsToMove.contains(point.id) && finalOffset != ModelOffset.Zero) {
                        point.copy(position = point.position + finalOffset)
                    } else point
                }

                val pointMoves = mutableMapOf<Long, Pair<ModelOffset, ModelOffset>>()
                for (ptId in pointsToMove) {
                    val oldPos = currentState.flow.junctions.find { it.id == ptId }?.position
                    val newPos = updatedPoints.find { it.id == ptId }?.position
                    if (oldPos != null && newPos != null && oldPos != newPos) {
                        pointMoves[ptId] = oldPos to newPos
                    }
                }

                if (groupMoves.isNotEmpty() || labelMoves.isNotEmpty() || pointMoves.isNotEmpty()) {
                    pendingCommand = MoveBoardElementsCommand(nodeMoves = moves, groupMoves = groupMoves, labelMoves = labelMoves, pointMoves = pointMoves)
                } else if (moves.isNotEmpty()) {
                    pendingCommand = MoveNodesCommand(moves)
                }

                newState = newState.copy(
                    flow = newState.flow.copy(groups = updatedGroups, junctions = updatedPoints)
                )
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
                val selectedNodes = currentState.selectedNodeIds
                val selectedGroups = currentState.selectedGroupIds
                val selectedLabels = currentState.selectedLabelIds

                if (selectedNodes.isNotEmpty() || selectedGroups.isNotEmpty() || selectedLabels.isNotEmpty()) {
                    val groupContainedNodeIds = currentState.flow.groups
                        .filter { it.id in selectedGroups }
                        .flatMap { it.nodeIds }
                        .toSet()
                    val allNodesToCopy = selectedNodes + groupContainedNodeIds

                    clipboardNodes = currentState.flow.nodes.filter { it.id in allNodesToCopy }
                    clipboardConnections = currentState.flow.connections.filter { 
                        it.sourceNodeId in allNodesToCopy && it.targetNodeId in allNodesToCopy 
                    }
                    clipboardGroups = currentState.flow.groups.filter { it.id in selectedGroups }
                    clipboardLabels = currentState.flow.labels.filter { it.id in selectedLabels }
                }
            }
            is FlowEvent.PasteNodes -> {
                if (clipboardNodes.isNotEmpty() || clipboardGroups.isNotEmpty() || clipboardLabels.isNotEmpty()) {
                    shouldRunTypeInference = true
                    
                    var nextId = currentState.nextId
                    val idMapping = mutableMapOf<Long, Long>()
                    
                    val newNodes = clipboardNodes.map { node ->
                        val newId = nextId++
                        idMapping[node.id] = newId
                        node.copyWithId(newId)
                    }
                    
                    val offsetDelta = if (newNodes.isNotEmpty()) {
                        val minX = newNodes.minOfOrNull { it.position.x } ?: 0f
                        val minY = newNodes.minOfOrNull { it.position.y } ?: 0f
                        val maxX = newNodes.maxOfOrNull { it.position.x } ?: 0f
                        val maxY = newNodes.maxOfOrNull { it.position.y } ?: 0f
                        val centerX = minX + (maxX - minX) / 2f
                        val centerY = minY + (maxY - minY) / 2f
                        ModelOffset(event.position.x - centerX, event.position.y - centerY)
                    } else {
                        ModelOffset(30f, 30f)
                    }
                    
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

                    var nextGroupId = (currentState.flow.groups.maxOfOrNull { it.id } ?: 0L) + 1L
                    val positionedGroups = clipboardGroups.map { grp ->
                        val newGId = nextGroupId++
                        val remappedNodeIds = grp.nodeIds.mapNotNull { idMapping[it] }
                        grp.copy(
                            id = newGId,
                            position = (grp.position + offsetDelta).snapToGrid(),
                            nodeIds = remappedNodeIds
                        )
                    }

                    var nextLabelId = (currentState.flow.labels.maxOfOrNull { it.id } ?: 0L) + 1L
                    val positionedLabels = clipboardLabels.map { lbl ->
                        val newLId = nextLabelId++
                        lbl.copy(
                            id = newLId,
                            position = (lbl.position + offsetDelta).snapToGrid()
                        )
                    }
                    
                    newState = currentState.copy(
                        nextId = nextId,
                        flow = currentState.flow.copy(
                            nodes = currentState.flow.nodes + positionedNodes,
                            connections = currentState.flow.connections + newConnections,
                            groups = currentState.flow.groups + positionedGroups,
                            labels = currentState.flow.labels + positionedLabels
                        ),
                        selectedNodeIds = positionedNodes.map { it.id }.toSet(),
                        selectedGroupIds = positionedGroups.map { it.id }.toSet(),
                        selectedLabelIds = positionedLabels.map { it.id }.toSet(),
                        hasUnsavedChanges = true
                    )
                    val addNodeCommands = positionedNodes.map { AddNodeCommand(it) }
                    val addConnCommands = newConnections.map { ConnectPortsCommand(it) }
                    val addGroupCommands = positionedGroups.map { AddGroupCommand(it) }
                    val addLabelCommands = positionedLabels.map { AddLabelCommand(it) }
                    pendingCommand = CompositeCommand("Paste elements", addNodeCommands + addConnCommands + addGroupCommands + addLabelCommands)
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
                val stateBefore = currentState
                newState = connectionManager.handleTryConnectPorts(
                    currentState,
                    event.sourceNodeId,
                    event.sourcePortId,
                    event.targetNodeId,
                    event.targetPortId,
                    event.isShiftPressed
                )
                if (newState !== stateBefore && newState.flow.connections != stateBefore.flow.connections) {
                    shouldRunTypeInference = true
                    val addedNode = newState.flow.nodes.firstOrNull { it !in stateBefore.flow.nodes }
                    val addedConns = newState.flow.connections.filter { it !in stateBefore.flow.connections }
                    val removedConns = stateBefore.flow.connections.filter { it !in newState.flow.connections }

                    if (addedNode != null) {
                        val commands = mutableListOf<FlowCommand>()
                        commands.add(AddNodeCommand(addedNode))
                        addedConns.forEach { conn ->
                            commands.add(ConnectPortsCommand(conn, if (conn == addedConns.last()) removedConns else emptyList()))
                        }
                        pendingCommand = CompositeCommand("Auto convert and connect", commands)
                    } else if (addedConns.isNotEmpty()) {
                        pendingCommand = ConnectPortsCommand(addedConns.first(), removedConns)
                    }
                }
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

            is FlowEvent.SelectLabels -> {
                newState = currentState.copy(selectedLabelIds = event.ids)
            }

            is FlowEvent.SelectGroups -> {
                newState = currentState.copy(selectedGroupIds = event.ids)
            }

            is FlowEvent.SelectPoints -> {
                newState = currentState.copy(selectedPointIds = event.ids)
            }

            is FlowEvent.ClearSelection -> {
                newState = currentState.copy(
                    selectedNodeIds = emptySet(),
                    selectedLabelIds = emptySet(),
                    selectedGroupIds = emptySet(),
                    selectedPointIds = emptySet()
                )
            }

            is FlowEvent.DeleteSelectedNodes -> {
                shouldRunTypeInference = true
                val deletedNodes = currentState.flow.nodes.filter { it.id in currentState.selectedNodeIds }
                val cascadeConns = currentState.flow.connections.filter {
                    it.sourceNodeId in currentState.selectedNodeIds || it.targetNodeId in currentState.selectedNodeIds ||
                    (it.sourceJunctionId != null && it.sourceJunctionId in currentState.selectedPointIds) ||
                    (it.targetJunctionId != null && it.targetJunctionId in currentState.selectedPointIds)
                }
                val deletedLabels = currentState.flow.labels.filter { it.id in currentState.selectedLabelIds }
                val deletedGroups = currentState.flow.groups.filter { it.id in currentState.selectedGroupIds }
                val deletedPoints = currentState.flow.junctions.filter { it.id in currentState.selectedPointIds }

                val updatedFlow = currentState.flow.copy(
                    nodes = currentState.flow.nodes.filter { it.id !in currentState.selectedNodeIds },
                    connections = currentState.flow.connections.filter { it !in cascadeConns },
                    labels = currentState.flow.labels.filter { it.id !in currentState.selectedLabelIds },
                    groups = currentState.flow.groups.filter { it.id !in currentState.selectedGroupIds },
                    junctions = currentState.flow.junctions.filter { it.id !in currentState.selectedPointIds }
                ).purgeStrayPoints()

                newState = currentState.copy(
                    flow = updatedFlow,
                    selectedNodeIds = emptySet(),
                    selectedLabelIds = emptySet(),
                    selectedGroupIds = emptySet(),
                    selectedPointIds = emptySet(),
                    hasUnsavedChanges = true
                )
                val commands = mutableListOf<FlowCommand>()
                if (deletedNodes.isNotEmpty() || cascadeConns.isNotEmpty()) {
                    commands.add(DeleteNodesCommand(deletedNodes, cascadeConns))
                }
                for (lbl in deletedLabels) {
                    commands.add(DeleteLabelCommand(lbl))
                }
                for (grp in deletedGroups) {
                    commands.add(DeleteGroupCommand(grp))
                }
                for (pt in deletedPoints) {
                    commands.add(DeleteJunctionCommand(pt, cascadeConns.filter { it.sourceJunctionId == pt.id || it.targetJunctionId == pt.id }))
                }
                if (commands.isNotEmpty()) {
                    pendingCommand = CompositeCommand("Delete selected elements", commands)
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

            is FlowEvent.DiscardChanges -> discardUnsavedChanges()

            // Paint Tool, Wash Tool, Eyedropper & Advanced Connection Mode
            is FlowEvent.TogglePaintTool -> {
                newState = currentState.copy(
                    isPaintToolActive = !currentState.isPaintToolActive,
                    isWashToolActive = false,
                    isEyedropperActive = false
                )
            }

            is FlowEvent.ToggleWashTool -> {
                newState = currentState.copy(
                    isWashToolActive = !currentState.isWashToolActive,
                    isPaintToolActive = false,
                    isEyedropperActive = false
                )
            }

            is FlowEvent.ToggleEyedropper -> {
                newState = currentState.copy(
                    isEyedropperActive = !currentState.isEyedropperActive,
                    isPaintToolActive = false,
                    isWashToolActive = false
                )
            }

            is FlowEvent.SampleColor -> {
                newState = currentState.copy(
                    activePaintColor = event.color,
                    isEyedropperActive = false
                )
                resolvedNotificationService?.toast("Sampled color: ${event.color}")
            }

            is FlowEvent.ToggleAdvancedConnectionMode -> {
                newState = currentState.copy(
                    isAdvancedConnectionMode = !currentState.isAdvancedConnectionMode
                )
            }

            is FlowEvent.SetActivePaintColor -> {
                newState = currentState.copy(activePaintColor = event.color)
            }

            is FlowEvent.PaintNode -> {
                val isSelected = currentState.selectedNodeIds.contains(event.nodeId)
                val totalSelected = currentState.selectedNodeIds.size + currentState.selectedGroupIds.size + currentState.selectedLabelIds.size
                if (isSelected && totalSelected > 1) {
                    val newSt = applyPaintToSelection(currentState, currentState.activePaintColor, event.isForce)
                    newState = newSt
                    pendingCommand = PaintElementsCommand(currentState.flow, newSt.flow)
                } else {
                    val color = currentState.activePaintColor
                    val updatedNodes = currentState.flow.nodes.map {
                        if (it.id == event.nodeId) it.copyWithColor(color) else it
                    }
                    val updatedConnections = currentState.flow.connections.map { conn ->
                        if (conn.sourceNodeId == event.nodeId) {
                            if (event.isForce || conn.color == null) {
                                conn.copy(color = color)
                            } else {
                                conn
                            }
                        } else {
                            conn
                        }
                    }
                    val newFlow = currentState.flow.copy(nodes = updatedNodes, connections = updatedConnections)
                    newState = currentState.copy(flow = newFlow, hasUnsavedChanges = true)
                    pendingCommand = PaintElementsCommand(currentState.flow, newFlow)
                }
            }

            is FlowEvent.PaintConnection -> {
                val color = currentState.activePaintColor
                val updatedConnections = currentState.flow.connections.map {
                    if (it == event.connection) it.copy(color = color) else it
                }
                val newFlow = currentState.flow.copy(connections = updatedConnections)
                newState = currentState.copy(flow = newFlow, hasUnsavedChanges = true)
                pendingCommand = PaintElementsCommand(currentState.flow, newFlow)
            }

            is FlowEvent.PaintGroup -> {
                val isSelected = currentState.selectedGroupIds.contains(event.groupId)
                val totalSelected = currentState.selectedNodeIds.size + currentState.selectedGroupIds.size + currentState.selectedLabelIds.size
                if (isSelected && totalSelected > 1) {
                    val newSt = applyPaintToSelection(currentState, currentState.activePaintColor)
                    newState = newSt
                    pendingCommand = PaintElementsCommand(currentState.flow, newSt.flow)
                } else {
                    val color = currentState.activePaintColor
                    val updatedGroups = currentState.flow.groups.map {
                        if (it.id == event.groupId) it.copy(color = color) else it
                    }
                    val newFlow = currentState.flow.copy(groups = updatedGroups)
                    newState = currentState.copy(flow = newFlow, hasUnsavedChanges = true)
                    pendingCommand = PaintElementsCommand(currentState.flow, newFlow)
                }
            }

            is FlowEvent.PaintLabel -> {
                val isSelected = currentState.selectedLabelIds.contains(event.labelId)
                val totalSelected = currentState.selectedNodeIds.size + currentState.selectedGroupIds.size + currentState.selectedLabelIds.size
                if (isSelected && totalSelected > 1) {
                    val newSt = applyPaintToSelection(currentState, currentState.activePaintColor)
                    newState = newSt
                    pendingCommand = PaintElementsCommand(currentState.flow, newSt.flow)
                } else {
                    val color = currentState.activePaintColor
                    val updatedLabels = currentState.flow.labels.map {
                        if (it.id == event.labelId) it.copy(color = color) else it
                    }
                    val newFlow = currentState.flow.copy(labels = updatedLabels)
                    newState = currentState.copy(flow = newFlow, hasUnsavedChanges = true)
                    pendingCommand = PaintElementsCommand(currentState.flow, newFlow)
                }
            }

            is FlowEvent.WashNode -> {
                val isSelected = currentState.selectedNodeIds.contains(event.nodeId)
                val totalSelected = currentState.selectedNodeIds.size + currentState.selectedGroupIds.size + currentState.selectedLabelIds.size
                if (isSelected && totalSelected > 1) {
                    val newSt = applyPaintToSelection(currentState, null)
                    newState = newSt
                    pendingCommand = PaintElementsCommand(currentState.flow, newSt.flow)
                } else {
                    val updatedNodes = currentState.flow.nodes.map {
                        if (it.id == event.nodeId) it.copyWithColor(null) else it
                    }
                    val newFlow = currentState.flow.copy(nodes = updatedNodes)
                    newState = currentState.copy(flow = newFlow, hasUnsavedChanges = true)
                    pendingCommand = PaintElementsCommand(currentState.flow, newFlow)
                }
            }

            is FlowEvent.WashConnection -> {
                val updatedConnections = currentState.flow.connections.map {
                    if (it == event.connection) it.copy(color = null) else it
                }
                val newFlow = currentState.flow.copy(connections = updatedConnections)
                newState = currentState.copy(flow = newFlow, hasUnsavedChanges = true)
                pendingCommand = PaintElementsCommand(currentState.flow, newFlow)
            }

            is FlowEvent.WashGroup -> {
                val isSelected = currentState.selectedGroupIds.contains(event.groupId)
                val totalSelected = currentState.selectedNodeIds.size + currentState.selectedGroupIds.size + currentState.selectedLabelIds.size
                if (isSelected && totalSelected > 1) {
                    val newSt = applyPaintToSelection(currentState, null)
                    newState = newSt
                    pendingCommand = PaintElementsCommand(currentState.flow, newSt.flow)
                } else {
                    val updatedGroups = currentState.flow.groups.map {
                        if (it.id == event.groupId) it.copy(color = null) else it
                    }
                    val newFlow = currentState.flow.copy(groups = updatedGroups)
                    newState = currentState.copy(flow = newFlow, hasUnsavedChanges = true)
                    pendingCommand = PaintElementsCommand(currentState.flow, newFlow)
                }
            }

            is FlowEvent.WashLabel -> {
                val isSelected = currentState.selectedLabelIds.contains(event.labelId)
                val totalSelected = currentState.selectedNodeIds.size + currentState.selectedGroupIds.size + currentState.selectedLabelIds.size
                if (isSelected && totalSelected > 1) {
                    val newSt = applyPaintToSelection(currentState, null)
                    newState = newSt
                    pendingCommand = PaintElementsCommand(currentState.flow, newSt.flow)
                } else {
                    val updatedLabels = currentState.flow.labels.map {
                        if (it.id == event.labelId) it.copy(color = null) else it
                    }
                    val newFlow = currentState.flow.copy(labels = updatedLabels)
                    newState = currentState.copy(flow = newFlow, hasUnsavedChanges = true)
                    pendingCommand = PaintElementsCommand(currentState.flow, newFlow)
                }
            }

            is FlowEvent.PaintSelection -> {
                if (currentState.selectedNodeIds.isNotEmpty() || currentState.selectedGroupIds.isNotEmpty() || currentState.selectedLabelIds.isNotEmpty()) {
                    val newSt = applyPaintToSelection(currentState, currentState.activePaintColor)
                    newState = newSt
                    pendingCommand = PaintElementsCommand(currentState.flow, newSt.flow)
                }
            }

            is FlowEvent.WashSelection -> {
                if (currentState.selectedNodeIds.isNotEmpty() || currentState.selectedGroupIds.isNotEmpty() || currentState.selectedLabelIds.isNotEmpty()) {
                    val newSt = applyPaintToSelection(currentState, null)
                    newState = newSt
                    pendingCommand = PaintElementsCommand(currentState.flow, newSt.flow)
                }
            }

            // Groups
            is FlowEvent.AddGroup -> {
                val newId = (currentState.flow.groups.maxOfOrNull { it.id } ?: 0L) + 1L
                val newGroup = FlowGroup(id = newId, title = "New Group", position = event.position, size = ModelOffset(320f, 240f))
                newState = currentState.copy(
                    flow = currentState.flow.copy(groups = currentState.flow.groups + newGroup),
                    hasUnsavedChanges = true
                )
                pendingCommand = AddGroupCommand(newGroup)
            }

            is FlowEvent.UpdateGroup -> {
                val old = currentState.flow.groups.find { it.id == event.group.id }
                if (old != null) {
                    newState = currentState.copy(
                        flow = currentState.flow.copy(groups = currentState.flow.groups.map { if (it.id == event.group.id) event.group else it }),
                        hasUnsavedChanges = true
                    )
                    pendingCommand = UpdateGroupCommand(old, event.group)
                }
            }

            is FlowEvent.DeleteGroup -> {
                newState = currentState.copy(
                    flow = currentState.flow.copy(groups = currentState.flow.groups.filter { it.id != event.group.id }),
                    hasUnsavedChanges = true
                )
                pendingCommand = DeleteGroupCommand(event.group)
            }

            is FlowEvent.MoveGroup -> {
                val grp = currentState.flow.groups.find { it.id == event.groupId }
                if (grp != null) {
                    val isSelected = currentState.selectedGroupIds.contains(event.groupId)
                    val groupsToMove = if (isSelected) currentState.selectedGroupIds else setOf(event.groupId)
                    val labelsToMove = if (isSelected) currentState.selectedLabelIds else emptySet()
                    val nodeIdsToMove = (if (isSelected) currentState.selectedNodeIds else emptySet()).toMutableSet()
                    for (gId in groupsToMove) {
                        currentState.flow.groups.find { it.id == gId }?.let { nodeIdsToMove.addAll(it.nodeIds) }
                    }

                    val updatedNodes = currentState.flow.nodes.map { node ->
                        if (node.id in nodeIdsToMove) {
                            node.copyWithPosition(node.position + event.delta)
                        } else node
                    }

                    val updatedGroups = currentState.flow.groups.map { g ->
                        if (g.id in groupsToMove) {
                            g.copy(position = g.position + event.delta)
                        } else g
                    }

                    val updatedLabels = currentState.flow.labels.map { l ->
                        if (l.id in labelsToMove) {
                            l.copy(position = l.position + event.delta)
                        } else l
                    }

                    newState = currentState.copy(
                        flow = currentState.flow.copy(
                            groups = updatedGroups,
                            labels = updatedLabels,
                            nodes = updatedNodes
                        ),
                        hasUnsavedChanges = true
                    )
                    pendingCommand = MoveGroupCommand(event.groupId, grp.position, grp.position + event.delta, nodeIdsToMove)
                }
            }

            is FlowEvent.ResizeGroup -> {
                val grp = currentState.flow.groups.find { it.id == event.groupId }
                if (grp != null) {
                    val newSize = org.wip.plugintoolkit.features.flows.model.Offset(
                        (grp.size.x + event.delta.x).coerceAtLeast(150f),
                        (grp.size.y + event.delta.y).coerceAtLeast(100f)
                    )
                    val updatedGroup = grp.copy(size = newSize)
                    newState = currentState.copy(
                        flow = currentState.flow.copy(
                            groups = currentState.flow.groups.map { if (it.id == event.groupId) updatedGroup else it }
                        ),
                        hasUnsavedChanges = true
                    )
                    pendingCommand = ResizeGroupCommand(event.groupId, grp.size, newSize)
                }
            }

            // Labels
            is FlowEvent.AddLabel -> {
                val newId = (currentState.flow.labels.maxOfOrNull { it.id } ?: 0L) + 1L
                val newLabel = FlowLabel(id = newId, text = "Text Note", position = event.position)
                newState = currentState.copy(
                    flow = currentState.flow.copy(labels = currentState.flow.labels + newLabel),
                    hasUnsavedChanges = true
                )
                pendingCommand = AddLabelCommand(newLabel)
            }

            is FlowEvent.UpdateLabel -> {
                val old = currentState.flow.labels.find { it.id == event.label.id }
                if (old != null) {
                    newState = currentState.copy(
                        flow = currentState.flow.copy(labels = currentState.flow.labels.map { if (it.id == event.label.id) event.label else it }),
                        hasUnsavedChanges = true
                    )
                    pendingCommand = UpdateLabelCommand(old, event.label)
                }
            }

            is FlowEvent.DeleteLabel -> {
                newState = currentState.copy(
                    flow = currentState.flow.copy(labels = currentState.flow.labels.filter { it.id != event.label.id }),
                    hasUnsavedChanges = true
                )
                pendingCommand = DeleteLabelCommand(event.label)
            }

            is FlowEvent.MoveLabel -> {
                val lbl = currentState.flow.labels.find { it.id == event.labelId }
                if (lbl != null) {
                    val isSelected = currentState.selectedLabelIds.contains(event.labelId)
                    val labelsToMove = if (isSelected) currentState.selectedLabelIds else setOf(event.labelId)
                    val groupsToMove = if (isSelected) currentState.selectedGroupIds else emptySet()
                    val nodeIdsToMove = (if (isSelected) currentState.selectedNodeIds else emptySet()).toMutableSet()
                    for (gId in groupsToMove) {
                        currentState.flow.groups.find { it.id == gId }?.let { nodeIdsToMove.addAll(it.nodeIds) }
                    }

                    val updatedLabels = currentState.flow.labels.map { l ->
                        if (l.id in labelsToMove) {
                            l.copy(position = l.position + event.delta)
                        } else l
                    }

                    val updatedGroups = currentState.flow.groups.map { g ->
                        if (g.id in groupsToMove) {
                            g.copy(position = g.position + event.delta)
                        } else g
                    }

                    val updatedNodes = currentState.flow.nodes.map { node ->
                        if (node.id in nodeIdsToMove) {
                            node.copyWithPosition(node.position + event.delta)
                        } else node
                    }

                    newState = currentState.copy(
                        flow = currentState.flow.copy(
                            labels = updatedLabels,
                            groups = updatedGroups,
                            nodes = updatedNodes
                        ),
                        hasUnsavedChanges = true
                    )
                    pendingCommand = MoveLabelCommand(event.labelId, lbl.position, lbl.position + event.delta)
                }
            }

            // Junctions, Waypoints & Branching
            is FlowEvent.AddJunctionAndBranch -> {
                val newJunctionId = (currentState.flow.junctions.maxOfOrNull { it.id } ?: 0L) + 1L
                val junction = FlowJunction(id = newJunctionId, position = event.splitPosition, color = event.connection.color)

                val segIdx = event.segmentIndex ?: 0
                val waypointsBefore = event.connection.waypoints.take(segIdx)
                val waypointsAfter = event.connection.waypoints.drop(segIdx)

                val conn1 = event.connection.copy(
                    targetNodeId = -1L,
                    targetPortId = "",
                    targetJunctionId = newJunctionId,
                    waypoints = waypointsBefore
                )
                val conn2 = Connection(
                    sourceNodeId = -1L,
                    sourcePortId = "",
                    sourceJunctionId = newJunctionId,
                    targetNodeId = event.connection.targetNodeId,
                    targetPortId = event.connection.targetPortId,
                    targetJunctionId = event.connection.targetJunctionId,
                    waypoints = waypointsAfter,
                    isStructured = event.connection.isStructured,
                    orderIndex = event.connection.orderIndex,
                    color = event.connection.color
                )
                val branchConns = mutableListOf<Connection>()
                if (event.branchSourceNodeId != null && event.branchSourcePortId != null) {
                    branchConns.add(
                        Connection(
                            sourceNodeId = event.branchSourceNodeId,
                            sourcePortId = event.branchSourcePortId,
                            targetNodeId = -1L,
                            targetPortId = "",
                            targetJunctionId = newJunctionId,
                            isStructured = event.connection.isStructured,
                            color = event.connection.color
                        )
                    )
                } else if (event.branchTargetNodeId != null && event.branchTargetPortId != null) {
                    branchConns.add(
                        Connection(
                            sourceNodeId = -1L,
                            sourcePortId = "",
                            sourceJunctionId = newJunctionId,
                            targetNodeId = event.branchTargetNodeId,
                            targetPortId = event.branchTargetPortId,
                            isStructured = event.connection.isStructured,
                            color = event.connection.color
                        )
                    )
                }
                val createdConnections = listOf(conn1, conn2) + branchConns
                val newConnections = currentState.flow.connections.filter { it != event.connection } + createdConnections
                newState = currentState.copy(
                    flow = currentState.flow.copy(
                        junctions = currentState.flow.junctions + junction,
                        connections = newConnections
                    ),
                    hasUnsavedChanges = true
                )
                pendingCommand = AddJunctionCommand(
                    junction = junction,
                    originalConnection = event.connection,
                    createdConnections = createdConnections
                )
            }

            is FlowEvent.SplitConnectionAndConnect -> {
                val newJunctionId = (currentState.flow.junctions.maxOfOrNull { it.id } ?: 0L) + 1L
                val junction = FlowJunction(id = newJunctionId, position = event.splitPosition, color = event.connection.color)

                val conn1 = event.connection.copy(
                    targetNodeId = -1L,
                    targetPortId = "",
                    targetJunctionId = newJunctionId,
                    floatingTarget = null
                )
                val conn2 = Connection(
                    sourceNodeId = -1L,
                    sourcePortId = "",
                    sourceJunctionId = newJunctionId,
                    targetNodeId = event.connection.targetNodeId,
                    targetPortId = event.connection.targetPortId,
                    targetJunctionId = event.connection.targetJunctionId,
                    orderIndex = event.connection.orderIndex,
                    color = event.connection.color,
                    floatingTarget = event.connection.floatingTarget,
                    isStructured = event.connection.isStructured
                )

                val branchConns = mutableListOf<Connection>()
                val intermediateJunctions = mutableListOf<FlowJunction>()
                var nextJuncId = newJunctionId + 1L

                if (event.targetNodeId != null) {
                    // Branch flows FROM wire (newJunctionId) TO target node
                    var prevSrcNodeId = -1L
                    var prevSrcPortId = ""
                    var prevSrcJuncId: Long? = newJunctionId

                    for (pt in event.intermediatePoints) {
                        val interJunc = FlowJunction(id = nextJuncId, position = pt, color = event.connection.color)
                        intermediateJunctions.add(interJunc)
                        branchConns.add(
                            Connection(
                                sourceNodeId = prevSrcNodeId,
                                sourcePortId = prevSrcPortId,
                                sourceJunctionId = prevSrcJuncId,
                                targetNodeId = -1L,
                                targetPortId = "",
                                targetJunctionId = nextJuncId,
                                isStructured = true,
                                color = event.connection.color
                            )
                        )
                        prevSrcNodeId = -1L
                        prevSrcPortId = ""
                        prevSrcJuncId = nextJuncId
                        nextJuncId++
                    }

                    branchConns.add(
                        Connection(
                            sourceNodeId = prevSrcNodeId,
                            sourcePortId = prevSrcPortId,
                            sourceJunctionId = prevSrcJuncId,
                            targetNodeId = event.targetNodeId,
                            targetPortId = event.targetPortId ?: "",
                            targetJunctionId = event.targetJunctionId,
                            isStructured = event.intermediatePoints.isNotEmpty(),
                            color = event.connection.color
                        )
                    )
                } else if (event.sourceNodeId != null || event.sourceJunctionId != null) {
                    // Branch flows FROM source node/junction TO wire (newJunctionId)
                    var prevSrcNodeId = event.sourceNodeId ?: -1L
                    var prevSrcPortId = event.sourcePortId ?: ""
                    var prevSrcJuncId = event.sourceJunctionId

                    for (pt in event.intermediatePoints) {
                        val interJunc = FlowJunction(id = nextJuncId, position = pt, color = event.connection.color)
                        intermediateJunctions.add(interJunc)
                        branchConns.add(
                            Connection(
                                sourceNodeId = prevSrcNodeId,
                                sourcePortId = prevSrcPortId,
                                sourceJunctionId = prevSrcJuncId,
                                targetNodeId = -1L,
                                targetPortId = "",
                                targetJunctionId = nextJuncId,
                                isStructured = true,
                                color = event.connection.color
                            )
                        )
                        prevSrcNodeId = -1L
                        prevSrcPortId = ""
                        prevSrcJuncId = nextJuncId
                        nextJuncId++
                    }

                    branchConns.add(
                        Connection(
                            sourceNodeId = prevSrcNodeId,
                            sourcePortId = prevSrcPortId,
                            sourceJunctionId = prevSrcJuncId,
                            targetNodeId = -1L,
                            targetPortId = "",
                            targetJunctionId = newJunctionId,
                            isStructured = event.intermediatePoints.isNotEmpty(),
                            color = event.connection.color
                        )
                    )
                }

                val createdConnections = listOf(conn1, conn2) + branchConns
                val newConnections = currentState.flow.connections.filter { it != event.connection } + createdConnections
                val newJunctions = currentState.flow.junctions + junction + intermediateJunctions

                newState = currentState.copy(
                    flow = currentState.flow.copy(
                        junctions = newJunctions,
                        connections = newConnections
                    ).purgeStrayPoints(),
                    hasUnsavedChanges = true
                )
                pendingCommand = AddJunctionCommand(
                    junction = junction,
                    originalConnection = event.connection,
                    createdConnections = createdConnections
                )
                shouldRunTypeInference = true
            }

            is FlowEvent.FinalizeStructuredConnectionWithPoints -> {
                var nextJuncId = (currentState.flow.junctions.maxOfOrNull { it.id } ?: 0L) + 1L
                val newJunctions = mutableListOf<FlowJunction>()
                val segmentConns = mutableListOf<Connection>()
                var prevSrcNodeId = event.sourceNodeId ?: -1L
                var prevSrcPortId = event.sourcePortId ?: ""
                var prevSrcJuncId = event.sourceJunctionId

                for (pt in event.points) {
                    val junc = FlowJunction(id = nextJuncId, position = pt)
                    newJunctions.add(junc)
                    segmentConns.add(
                        Connection(
                            sourceNodeId = prevSrcNodeId,
                            sourcePortId = prevSrcPortId,
                            sourceJunctionId = prevSrcJuncId,
                            targetNodeId = -1L,
                            targetPortId = "",
                            targetJunctionId = nextJuncId,
                            isStructured = true
                        )
                    )
                    prevSrcNodeId = -1L
                    prevSrcPortId = ""
                    prevSrcJuncId = nextJuncId
                    nextJuncId++
                }

                segmentConns.add(
                    Connection(
                        sourceNodeId = prevSrcNodeId,
                        sourcePortId = prevSrcPortId,
                        sourceJunctionId = prevSrcJuncId,
                        targetNodeId = event.targetNodeId,
                        targetPortId = event.targetPortId,
                        targetJunctionId = event.targetJunctionId,
                        isStructured = true
                    )
                )

                newState = currentState.copy(
                    flow = currentState.flow.copy(
                        junctions = currentState.flow.junctions + newJunctions,
                        connections = currentState.flow.connections + segmentConns
                    ),
                    hasUnsavedChanges = true
                )
                shouldRunTypeInference = true
            }

            is FlowEvent.NormalizeWirePoints -> {
                val normalizedFlow = currentState.flow.normalizeWirePoints()
                if (normalizedFlow != currentState.flow) {
                    newState = currentState.copy(
                        flow = normalizedFlow,
                        hasUnsavedChanges = true
                    )
                    shouldRunTypeInference = true
                }
            }

            is FlowEvent.AddWaypoint -> {
                val conn = currentState.flow.connections.find {
                    it == event.connection || (it.sourceNodeId == event.connection.sourceNodeId && it.sourcePortId == event.connection.sourcePortId && it.targetNodeId == event.connection.targetNodeId && it.targetPortId == event.connection.targetPortId)
                }
                if (conn != null) {
                    val oldWps = conn.waypoints
                    val newWps = if (event.index != null && event.index in 0..oldWps.size) {
                        oldWps.toMutableList().apply { add(event.index, event.point) }
                    } else {
                        oldWps + event.point
                    }
                    val newConnections = currentState.flow.connections.map {
                        if (it == conn) it.copy(waypoints = newWps) else it
                    }
                    newState = currentState.copy(
                        flow = currentState.flow.copy(connections = newConnections),
                        hasUnsavedChanges = true
                    )
                    pendingCommand = UpdateWaypointsCommand(conn, oldWps, newWps)
                }
            }

            is FlowEvent.MoveWaypoint -> {
                val conn = currentState.flow.connections.find {
                    it == event.connection || (it.sourceNodeId == event.connection.sourceNodeId && it.sourcePortId == event.connection.sourcePortId && it.targetNodeId == event.connection.targetNodeId && it.targetPortId == event.connection.targetPortId)
                }
                if (conn != null && event.index in conn.waypoints.indices) {
                    val oldWps = conn.waypoints
                    val newWps = oldWps.toMutableList().apply { set(event.index, event.newPoint) }
                    val newConnections = currentState.flow.connections.map {
                        if (it == conn) it.copy(waypoints = newWps) else it
                    }
                    newState = currentState.copy(
                        flow = currentState.flow.copy(connections = newConnections),
                        hasUnsavedChanges = true
                    )
                    pendingCommand = UpdateWaypointsCommand(conn, oldWps, newWps)
                }
            }

            is FlowEvent.DeleteWaypoint -> {
                val conn = currentState.flow.connections.find {
                    it == event.connection || (it.sourceNodeId == event.connection.sourceNodeId && it.sourcePortId == event.connection.sourcePortId && it.targetNodeId == event.connection.targetNodeId && it.targetPortId == event.connection.targetPortId)
                }
                if (conn != null && event.index in conn.waypoints.indices) {
                    val oldWps = conn.waypoints
                    val newWps = oldWps.toMutableList().apply { removeAt(event.index) }
                    val newConnections = currentState.flow.connections.map {
                        if (it == conn) it.copy(waypoints = newWps) else it
                    }
                    newState = currentState.copy(
                        flow = currentState.flow.copy(connections = newConnections),
                        hasUnsavedChanges = true
                    )
                    pendingCommand = UpdateWaypointsCommand(conn, oldWps, newWps)
                }
            }

            is FlowEvent.DeleteConnectionSegment -> {
                val conn = currentState.flow.connections.find {
                    it == event.connection || (it.sourceNodeId == event.connection.sourceNodeId && it.sourcePortId == event.connection.sourcePortId && it.targetNodeId == event.connection.targetNodeId && it.targetPortId == event.connection.targetPortId)
                }
                if (conn != null) {
                    val wps = conn.waypoints
                    val count = wps.size
                    val segIdx = event.segmentIndex
                    if (count == 0) {
                        newState = connectionManager.handleDeleteConnection(currentState, conn)
                    } else if (segIdx == 0) {
                        val newJuncId = (currentState.flow.junctions.maxOfOrNull { it.id } ?: 0L) + 1L
                        val newJunc = FlowJunction(newJuncId, wps[0], conn.color)
                        val remConn = conn.copy(
                            sourceNodeId = -1L,
                            sourcePortId = "",
                            sourceJunctionId = newJuncId,
                            waypoints = wps.drop(1)
                        )
                        val newConnections = currentState.flow.connections.filter { it != conn } + remConn
                        newState = currentState.copy(
                            flow = currentState.flow.copy(
                                junctions = currentState.flow.junctions + newJunc,
                                connections = newConnections
                            ),
                            hasUnsavedChanges = true
                        )
                    } else if (segIdx >= count) {
                        val remConn = conn.copy(
                            targetNodeId = -1L,
                            targetPortId = "",
                            targetJunctionId = null,
                            floatingTarget = wps[count - 1],
                            waypoints = wps.take(count - 1)
                        )
                        val newConnections = currentState.flow.connections.filter { it != conn } + remConn
                        newState = currentState.copy(
                            flow = currentState.flow.copy(connections = newConnections),
                            hasUnsavedChanges = true
                        )
                    } else {
                        val conn1 = conn.copy(
                            targetNodeId = -1L,
                            targetPortId = "",
                            targetJunctionId = null,
                            floatingTarget = wps[segIdx - 1],
                            waypoints = wps.take(segIdx - 1)
                        )
                        val newJuncId = (currentState.flow.junctions.maxOfOrNull { it.id } ?: 0L) + 1L
                        val newJunc = FlowJunction(newJuncId, wps[segIdx], conn.color)
                        val conn2 = conn.copy(
                            sourceNodeId = -1L,
                            sourcePortId = "",
                            sourceJunctionId = newJuncId,
                            waypoints = wps.drop(segIdx + 1)
                        )
                        val newConnections = currentState.flow.connections.filter { it != conn } + conn1 + conn2
                        newState = currentState.copy(
                            flow = currentState.flow.copy(
                                junctions = currentState.flow.junctions + newJunc,
                                connections = newConnections
                            ),
                            hasUnsavedChanges = true
                        )
                    }
                }
            }

            is FlowEvent.MoveJunction -> {
                val junc = currentState.flow.junctions.find { it.id == event.junctionId }
                if (junc != null) {
                    val newPos = junc.position + event.delta
                    newState = currentState.copy(
                        flow = currentState.flow.copy(junctions = currentState.flow.junctions.map { if (it.id == event.junctionId) it.copy(position = newPos) else it }),
                        hasUnsavedChanges = true
                    )
                    pendingCommand = MoveJunctionCommand(event.junctionId, junc.position, newPos)
                }
            }

            is FlowEvent.DeleteJunction -> {
                val junc = currentState.flow.junctions.find { it.id == event.junctionId }
                if (junc != null) {
                    val cascading = currentState.flow.connections.filter { it.sourceJunctionId == event.junctionId || it.targetJunctionId == event.junctionId }
                    newState = currentState.copy(
                        flow = currentState.flow.copy(
                            junctions = currentState.flow.junctions.filter { it.id != event.junctionId },
                            connections = currentState.flow.connections - cascading.toSet()
                        ),
                        hasUnsavedChanges = true
                    )
                    pendingCommand = DeleteJunctionCommand(junc, cascading)
                }
            }

            is FlowEvent.CreateFloatingConnection -> {
                val floating = Connection(
                    sourceNodeId = event.sourceNodeId,
                    sourcePortId = event.sourcePortId,
                    targetNodeId = Connection.FLOATING_NODE_ID,
                    targetPortId = Connection.FLOATING_PORT_ID,
                    waypoints = event.waypoints,
                    sourceJunctionId = event.sourceJunctionId,
                    floatingTarget = event.floatingTarget,
                    isStructured = event.isStructured
                )
                newState = currentState.copy(
                    flow = currentState.flow.copy(connections = currentState.flow.connections + floating),
                    hasUnsavedChanges = true
                )
            }

            is FlowEvent.ConnectPortsWithWaypoints -> {
                val stateBefore = currentState
                val targetNode = currentState.flow.nodes.find { it.id == event.targetNodeId }
                val targetPort = targetNode?.inputs?.find { it.id == event.targetPortId }
                val isList = targetPort?.dataType is DataType.Array
                val removedConns = if (isList) emptyList() else currentState.flow.connections.filter {
                    it.targetNodeId == event.targetNodeId && it.targetPortId == event.targetPortId
                }

                newState = connectionManager.handleConnectPortsWithWaypoints(
                    currentState,
                    event.sourceNodeId,
                    event.sourcePortId,
                    event.sourceJunctionId,
                    event.targetNodeId,
                    event.targetPortId,
                    event.waypoints,
                    event.isStructured,
                    event.targetJunctionId
                )
                if (newState !== stateBefore && newState.flow.connections != stateBefore.flow.connections) {
                    shouldRunTypeInference = true
                    val addedConn = newState.flow.connections.lastOrNull()
                    if (addedConn != null) {
                        pendingCommand = ConnectPortsCommand(addedConn, removedConns)
                    }
                }
            }

            is FlowEvent.UpdateConnectionCurveStyle -> {
                newState = currentState.copy(connectionCurveStyle = event.style)
            }

            is FlowEvent.UpdateConnectionRoundness -> {
                newState = currentState.copy(connectionRoundness = event.roundness)
            }

            is FlowEvent.ToggleStructuredConnectionMode -> {
                newState = currentState.copy(isAdvancedConnectionMode = !currentState.isAdvancedConnectionMode)
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
            if (isAutoSaveEnabled) {
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

    fun discardUnsavedChanges() {
        val allFlows = flowRepository.flows.value
        val activeFlowName = initialFlowName
        val originalFlow = if (activeFlowName.isBlank()) {
            Flow("")
        } else {
            allFlows.find { it.name == activeFlowName } ?: Flow(activeFlowName)
        }
        val syncedFlow = syncSubflowNodes(originalFlow, allFlows)
        val maxNodeId = syncedFlow.nodes.maxOfOrNull { it.id } ?: -1L

        _state.update { currentState ->
            currentState.copy(
                flow = syncedFlow,
                nextId = maxNodeId + 1,
                flows = allFlows,
                pendingConnection = null,
                selectedNodeIds = emptySet(),
                hasUnsavedChanges = false
            )
        }
        historyManager.clear()
        resolvedActiveFlowEditorTracker.setHasUnsavedChanges(false)
        updateReadOnlyState()
        runTypeInference()
    }

    private fun applyPaintToSelection(
        currentState: FlowEditorState,
        color: String?,
        forceConnections: Boolean = false
    ): FlowEditorState {
        val selectedNodeIds = currentState.selectedNodeIds
        val selectedGroupIds = currentState.selectedGroupIds
        val selectedLabelIds = currentState.selectedLabelIds

        val updatedNodes = currentState.flow.nodes.map { node ->
            if (node.id in selectedNodeIds) node.copyWithColor(color) else node
        }
        val updatedGroups = currentState.flow.groups.map { grp ->
            if (grp.id in selectedGroupIds) grp.copy(color = color) else grp
        }
        val updatedLabels = currentState.flow.labels.map { lbl ->
            if (lbl.id in selectedLabelIds) lbl.copy(color = color) else lbl
        }
        val updatedConnections = currentState.flow.connections.map { conn ->
            if (conn.sourceNodeId in selectedNodeIds) {
                if (forceConnections || conn.color == null || color == null) {
                    conn.copy(color = color)
                } else conn
            } else conn
        }
        val newFlow = currentState.flow.copy(
            nodes = updatedNodes,
            groups = updatedGroups,
            labels = updatedLabels,
            connections = updatedConnections
        )
        return currentState.copy(flow = newFlow, hasUnsavedChanges = true)
    }

    override fun onCleared() {
        super.onCleared()
        resolvedActiveFlowEditorTracker.registerDiscardHandler(null)
    }
}
