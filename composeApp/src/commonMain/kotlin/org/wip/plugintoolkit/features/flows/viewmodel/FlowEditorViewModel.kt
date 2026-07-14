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


class FlowEditorViewModel(
    private val initialFlowName: String,
    private val flowRepository: org.wip.plugintoolkit.features.flows.logic.FlowRepository,
    private val settingsPersistence: SettingsPersistence? = null,
    private val notificationService: NotificationService? = null,
    private val pluginRegistry: PluginRegistry? = null,
    private val activeFlowEditorTracker: ActiveFlowEditorTracker? = null,
    private val settingsRepository: org.wip.plugintoolkit.features.settings.logic.SettingsRepository? = null
) : ViewModel() {

    companion object {
        private var clipboardNodes: List<Node> = emptyList()
        private var clipboardConnections: List<Connection> = emptyList()
    }

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


    private val nodeManager = FlowNodeManager()
    private val connectionManager = FlowConnectionManager(
        notificationService = resolvedNotificationService,
        viewModelScope = viewModelScope,
        onEvent = ::onEvent
    )

    private val undoStack = mutableListOf<Flow>()
    private val redoStack = mutableListOf<Flow>()

    private var clipboardNodes: List<Node> = emptyList()
    private var clipboardConnections: List<Connection> = emptyList()

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

        var shouldSaveHistory = false
        var shouldRunTypeInference = false
        val currentState = _state.value
        var newState = currentState

        when (event) {
            is FlowEvent.AddCapabilityNode -> {
                shouldSaveHistory = true
                shouldRunTypeInference = true
                val node = Node.CapabilityNode(
                    id = currentState.nextId,
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
            }

            is FlowEvent.AddSystemNode -> {
                shouldSaveHistory = true
                shouldRunTypeInference = true
                val inputs = SystemNodesRegistry.getInputs(event.systemAction)
                val outputs = SystemNodesRegistry.getOutputs(event.systemAction)
                val node = Node.SystemNode(
                    id = currentState.nextId,
                    position = event.position.snapToGrid(),
                    title = event.systemAction.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                    systemAction = event.systemAction,
                    inputs = inputs,
                    outputs = outputs
                )
                newState = nodeManager.handleAddNode(currentState, node, event.density)
            }

            is FlowEvent.AddFlowInputNode -> {
                shouldSaveHistory = true
                shouldRunTypeInference = true
                val node = Node.FlowInputNode(
                    id = currentState.nextId,
                    position = event.position.snapToGrid(),
                    outputs = listOf(OutputPort("input_data", "Input Data", DataType.Primitive(PrimitiveType.ANY)))
                )
                newState = nodeManager.handleAddNode(currentState, node, event.density)
            }

            is FlowEvent.AddFlowOutputNode -> {
                shouldSaveHistory = true
                shouldRunTypeInference = true
                val node = Node.FlowOutputNode(
                    id = currentState.nextId,
                    position = event.position.snapToGrid(),
                    inputs = listOf(InputPort("output_data", "Output Data", DataType.Primitive(PrimitiveType.ANY)))
                )
                newState = nodeManager.handleAddNode(currentState, node, event.density)
            }

            is FlowEvent.AddSubFlowNode -> {
                if (org.wip.plugintoolkit.features.flows.logic.FlowCycleDetector.wouldCreateNestedFlowCycle(
                        currentState.flow.name,
                        event.flowName,
                        currentState.flows
                    )
                ) {
                    Logger.w { "Failed to add subflow node: adding subflow '${event.flowName}' to '${currentState.flow.name}' would create a nested cycle." }
                    resolvedNotificationService?.toast("Cannot add subflow: Adding this subflow would create a cyclic dependency between flows.")
                    return
                }

                shouldSaveHistory = true
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
                    position = event.position.snapToGrid(),
                    flowName = event.flowName,
                    inputs = inputs,
                    outputs = outputs,
                    inputMappings = inputMappings,
                    outputMappings = outputMappings
                )
                newState = nodeManager.handleAddNode(currentState, node, event.density)
            }

            is FlowEvent.ExpandSubFlow -> handleExpandSubFlow(event.nodeId)
            is FlowEvent.MoveNode -> {
                newState = nodeManager.handleMoveNode(currentState, event.id, event.delta, event.snap, event.showGhost)
            }

            is FlowEvent.EndMoveNode -> {
                shouldSaveHistory = true
                shouldRunTypeInference = true
                newState = nodeManager.handleEndMoveNode(currentState, event.id, event.density)
            }

            is FlowEvent.DeleteNode -> {
                shouldSaveHistory = true
                shouldRunTypeInference = true
                newState = nodeManager.handleDeleteNode(currentState, event.id)
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
                    shouldSaveHistory = true
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
                    
                    val offsetDelta = Offset(event.position.x - centerX, event.position.y - centerY)
                    
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
                }
            }

            is FlowEvent.ConnectPorts -> {
                shouldSaveHistory = true
                shouldRunTypeInference = true
                newState = connectionManager.handleConnectPorts(
                    currentState,
                    event.sourceNodeId,
                    event.sourcePortId,
                    event.targetNodeId,
                    event.targetPortId
                )
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
                shouldSaveHistory = true
                shouldRunTypeInference = true
                newState = connectionManager.handleAutoConvertAndConnect(
                    currentState,
                    event.sourceNodeId,
                    event.sourcePortId,
                    event.targetNodeId,
                    event.targetPortId
                )
            }

            is FlowEvent.DeleteConnection -> {
                shouldSaveHistory = true
                shouldRunTypeInference = true
                newState = connectionManager.handleDeleteConnection(currentState, event.connection)
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

            is FlowEvent.UpdateBoundaryNode -> {
                shouldSaveHistory = true
                shouldRunTypeInference = true
                newState = nodeManager.handleUpdateBoundaryNode(
                    currentState,
                    event.nodeId,
                    event.portName,
                    event.dataType,
                    event.semanticTypes,
                    event.constraints,
                    event.isList,
                    event.isRequired
                )
            }

            is FlowEvent.UpdateSystemNodeSettings -> {
                shouldSaveHistory = true
                shouldRunTypeInference = true
                newState = nodeManager.handleUpdateSystemNodeSettings(
                    currentState,
                    event.nodeId,
                    event.portId,
                    event.semanticTypes,
                    event.inputPortId,
                    event.extensions
                )
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
                shouldSaveHistory = true
                shouldRunTypeInference = true
                newState = nodeManager.handleDeleteSelectedNodes(currentState)
            }

            is FlowEvent.CopySelectedNodes -> {
                val selectedIds = currentState.selectedNodeIds
                if (selectedIds.isNotEmpty()) {
                    clipboardNodes = currentState.flow.nodes.filter { it.id in selectedIds }
                    clipboardConnections = currentState.flow.connections.filter {
                        it.sourceNodeId in selectedIds && it.targetNodeId in selectedIds
                    }
                }
            }

            is FlowEvent.PasteNodes -> {
                if (clipboardNodes.isNotEmpty()) {
                    shouldSaveHistory = true
                    shouldRunTypeInference = true
                    
                    var minX = Float.MAX_VALUE
                    var maxX = Float.MIN_VALUE
                    var minY = Float.MAX_VALUE
                    var maxY = Float.MIN_VALUE
                    
                    clipboardNodes.forEach {
                        if (it.position.x < minX) minX = it.position.x
                        if (it.position.x > maxX) maxX = it.position.x
                        if (it.position.y < minY) minY = it.position.y
                        if (it.position.y > maxY) maxY = it.position.y
                    }
                    
                    val centerX = (minX + maxX) / 2
                    val centerY = (minY + maxY) / 2
                    val offsetDelta = event.position - Offset(centerX, centerY)
                    val snappedOffsetDelta = offsetDelta.snapToGrid()
                    
                    val idMap = mutableMapOf<Long, Long>()
                    var currentNextId = currentState.nextId
                    
                    val newNodes = clipboardNodes.map { oldNode ->
                        val newId = currentNextId++
                        idMap[oldNode.id] = newId
                        oldNode.copyWithId(newId).copyWithPosition(oldNode.position + snappedOffsetDelta)
                    }
                    
                    val newConnections = clipboardConnections.mapNotNull { oldConn ->
                        val newSource = idMap[oldConn.sourceNodeId]
                        val newTarget = idMap[oldConn.targetNodeId]
                        if (newSource != null && newTarget != null) {
                            oldConn.copy(sourceNodeId = newSource, targetNodeId = newTarget)
                        } else null
                    }
                    
                    newState = currentState.copy(
                        flow = currentState.flow.copy(
                            nodes = currentState.flow.nodes + newNodes,
                            connections = currentState.flow.connections + newConnections
                        ),
                        nextId = currentNextId,
                        selectedNodeIds = newNodes.map { it.id }.toSet()
                    )
                }
            }

            is FlowEvent.ToggleNodeCollapse -> {
                shouldSaveHistory = true
                newState = nodeManager.handleToggleNodeCollapse(currentState, event.nodeId)
            }

            is FlowEvent.ToggleNodeInputsCollapse -> {
                shouldSaveHistory = true
                newState = nodeManager.handleToggleNodeInputsCollapse(currentState, event.nodeId)
            }

            is FlowEvent.ToggleNodeOutputsCollapse -> {
                shouldSaveHistory = true
                newState = nodeManager.handleToggleNodeOutputsCollapse(currentState, event.nodeId)
            }

            is FlowEvent.UpdateConnectionOrder -> {
                shouldSaveHistory = true
                newState = connectionManager.handleUpdateConnectionOrder(
                    currentState,
                    event.connection,
                    event.newOrderIndex
                )
            }

            is FlowEvent.MoveConnectionFirst -> {
                shouldSaveHistory = true
                newState = connectionManager.handleMoveConnectionFirst(currentState, event.connection)
            }

            is FlowEvent.MoveConnectionLast -> {
                shouldSaveHistory = true
                newState = connectionManager.handleMoveConnectionLast(currentState, event.connection)
            }

            else -> {}
        }

        if (shouldSaveHistory && newState !== currentState) {
            saveToHistory()
        }

        if (newState !== currentState) {
            _state.value = newState
        }

        if (shouldRunTypeInference) {
            runTypeInference()
        }

        if (_state.value.hasUnsavedChanges && event !is FlowEvent.Save && event !is FlowEvent.SaveAs) {
            if (resolvedSettingsRepository?.settings?.value?.flows?.autosave == true) {
                handleSave()
            }
        }
    }


    private fun handlePan(delta: Offset) {
        _state.update { currentState ->
            currentState.copy(offset = currentState.offset + delta)
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

            if (newScale == currentZoom) return@update currentState

            val boardFocus = (focusPosition - currentState.offset) / currentZoom
            val newOffset = focusPosition - boardFocus * newScale

            currentState.copy(scale = newScale, offset = newOffset)
        }
    }

    private fun handleSetZoom(scale: Float) {
        _state.update { currentState ->
            currentState.copy(scale = scale.coerceIn(0.1f, 5.0f))
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


    private fun runTypeInference() {
        val result = org.wip.plugintoolkit.features.flows.logic.FlowTypeInference.runTypeInference(_state.value.flow)
        _state.update { currentState ->
            currentState.copy(
                inferredTypes = result.inferredTypes,
                inferredSemanticTypes = result.inferredSemanticTypes,
                validationErrors = result.validationErrors
            )
        }
    }

    private fun getSubflowPorts(targetFlow: Flow): Pair<List<InputPort>, List<OutputPort>> {
        return org.wip.plugintoolkit.features.flows.logic.FlowTypeInference.getSubflowPorts(targetFlow)
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

internal fun Offset.snapToGrid(gridSize: Float = 50f): Offset {
    return Offset(
        (x / gridSize).roundToInt() * gridSize,
        (y / gridSize).roundToInt() * gridSize
    )
}
