package org.wip.plugintoolkit.features.job.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.JobHandle
import org.wip.plugintoolkit.api.PluginRequest
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.logic.PluginLifecycleCoordinator
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import kotlinx.coroutines.CoroutineScope

class FlowEngine(
    private val manager: JobManager,
    private val executorRegistry: SystemNodeExecutorRegistry,
    private val pluginManager: PluginManager,
    private val lifecycleCoordinator: PluginLifecycleCoordinator,
    private val workerScope: CoroutineScope
) : KoinComponent {

    suspend fun executeFlowJob(job: BackgroundJob) {
        manager.addJobLog(job.id, "Starting flow execution for '${job.capabilityName}'...")
        manager.updateJobProgress(job.id, 0.05f)

        // 1. Resolve Settings Directory and Load Flow
        val settingsPersistence: SettingsPersistence by inject()
        val appDataDir = settingsPersistence.getSettingsDir()
        val safeName = job.capabilityName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val file = Path("$appDataDir/flows/$safeName.json")
        
        if (!SystemFileSystem.exists(file)) {
            throw Exception("Flow definition file not found: ${file.name}")
        }
        
        val flowContent = SystemFileSystem.source(file).buffered().use { it.readString() }
        val flow = Json { 
            ignoreUnknownKeys = true 
            encodeDefaults = true
        }.decodeFromString<org.wip.plugintoolkit.features.flows.model.Flow>(flowContent)

        manager.addJobLog(job.id, "Loaded flow '${flow.name}' with ${flow.nodes.size} nodes and ${flow.connections.size} connections.")

        // Cache the type inference
        val runtimeInferred = FlowTypeInferenceCache.getOrCreate(flow) {
            runRuntimeTypeInference(flow)
        }

        // Build lookup maps
        val nodesById = flow.nodes.associateBy { it.id }
        val connectionsByTarget = flow.connections.groupBy { Pair(it.targetNodeId, it.targetPortId) }
        val connectionsBySource = flow.connections.groupBy { Pair(it.sourceNodeId, it.sourcePortId) }

        val computedValues = mutableMapOf<Pair<Long, String>, Any?>()
        val flowOutputs = mutableMapOf<String, Any?>()

        // Helper to get input value
        fun getInputValue(nodeId: Long, portId: String, defaultValue: Any?): Any? {
            val conns = connectionsByTarget[Pair(nodeId, portId)]
            if (conns != null && conns.isNotEmpty()) {
                val sortedConns = conns.sortedBy { it.orderIndex ?: 0 }
                val targetNode = nodesById[nodeId]
                val targetPort = targetNode?.inputs?.find { it.id == portId }
                
                if (targetPort?.dataType is DataType.Array) {
                    val results = sortedConns.map { conn ->
                        val raw = computedValues[Pair(conn.sourceNodeId, conn.sourcePortId)]
                        if (raw is JsonElement) fromJsonElement(raw) else raw
                    }
                    if (sortedConns.size == 1) {
                        val singleParsed = results.first()
                        if (singleParsed is List<*>) {
                            return singleParsed
                        } else {
                            return listOf(singleParsed)
                        }
                    }
                    return results
                } else {
                    val conn = sortedConns.first()
                    val raw = computedValues[Pair(conn.sourceNodeId, conn.sourcePortId)]
                    return if (raw is JsonElement) fromJsonElement(raw) else raw
                }
            } else {
                val node = nodesById[nodeId] ?: return defaultValue
                val port = node.inputs.find { it.id == portId }
                val raw = port?.value ?: port?.defaultValue ?: defaultValue
                return if (raw is JsonElement) fromJsonElement(raw) else raw
            }
        }

        // 2. Perform Topological Sort
        val inDegree = mutableMapOf<Long, Int>()
        val adj = mutableMapOf<Long, MutableList<Long>>()

        flow.nodes.forEach { node ->
            inDegree[node.id] = 0
            adj[node.id] = mutableListOf()
        }

        flow.connections.forEach { conn ->
            if (inDegree.containsKey(conn.targetNodeId) && inDegree.containsKey(conn.sourceNodeId)) {
                adj[conn.sourceNodeId]?.add(conn.targetNodeId)
                inDegree[conn.targetNodeId] = (inDegree[conn.targetNodeId] ?: 0) + 1
            }
        }

        val queue = ArrayDeque<Long>()
        flow.nodes.forEach { node ->
            if (inDegree[node.id] == 0) {
                queue.addLast(node.id)
            }
        }

        val executionOrder = mutableListOf<Long>()
        while (queue.isNotEmpty()) {
            val nodeId = queue.removeFirst()
            executionOrder.add(nodeId)
            adj[nodeId]?.forEach { neighbor ->
                inDegree[neighbor] = (inDegree[neighbor] ?: 1) - 1
                if (inDegree[neighbor] == 0) {
                    queue.addLast(neighbor)
                }
            }
        }

        if (executionOrder.size < flow.nodes.size) {
            throw Exception("Cycle or invalid connection structure detected in flow graph.")
        }

        manager.addJobLog(job.id, "Successfully established DAG topological execution sequence.")

        // 3. Execute Nodes in Sequence
        val activeNodes = mutableSetOf<Long>()
        val executedNodeIds = mutableSetOf<Long>()
        val capabilityResumeStates = mutableMapOf<Long, JsonElement>()

        // Register flow-level job handle
        val jobExecution = currentCoroutineContext()[kotlinx.coroutines.Job]!!
        var pauseRequested = false
        var activeCapabilityHandle: JobHandle? = null

        val flowHandle = object : JobHandle {
            override val result: Deferred<ExecutionResult>
                get() = throw UnsupportedOperationException("Not used directly")

            override fun pause() {
                pauseRequested = true
                activeCapabilityHandle?.pause()
            }

            override fun cancel(force: Boolean) {
                activeCapabilityHandle?.cancel(force)
                jobExecution.cancel()
            }
        }
        manager.registerJobHandle(job.id, flowHandle)

        try {
            val resumeState = job.resumeState as? JsonObject
            if (resumeState != null) {
                resumeState["executedNodeIds"]?.jsonArray?.map { it.jsonPrimitive.long }?.let {
                    executedNodeIds.addAll(it)
                }
                resumeState["activeNodes"]?.jsonArray?.map { it.jsonPrimitive.long }?.let {
                    activeNodes.addAll(it)
                }
                resumeState["computedValues"]?.jsonArray?.forEach { entry ->
                    val obj = entry.jsonObject
                    val nodeId = obj["nodeId"]?.jsonPrimitive?.long
                    val portId = obj["portId"]?.jsonPrimitive?.content
                    val valueJson = obj["value"]
                    if (nodeId != null && portId != null && valueJson != null) {
                        computedValues[Pair(nodeId, portId)] = fromJsonElement(valueJson)
                    }
                }
                resumeState["capabilityResumeStates"]?.jsonObject?.forEach { (nodeIdStr, resumeStateJson) ->
                    nodeIdStr.toLongOrNull()?.let { nodeId ->
                        capabilityResumeStates[nodeId] = resumeStateJson
                    }
                }
                manager.addJobLog(job.id, "Resuming flow execution from saved state. Executed nodes: ${executedNodeIds.size}, Active nodes: ${activeNodes.size}")
            } else {
                val nodesWithIncoming = flow.connections.map { it.targetNodeId }.toSet()
                flow.nodes.forEach { n ->
                    if (!nodesWithIncoming.contains(n.id)) {
                        activeNodes.add(n.id)
                    }
                }
            }

            executionOrder.forEachIndexed { index, nodeId ->
                val node = nodesById[nodeId] ?: return@forEachIndexed
                if (executedNodeIds.contains(node.id)) {
                    return@forEachIndexed
                }
                if (!activeNodes.contains(node.id)) {
                    return@forEachIndexed
                }

                // Check for pause request before executing a capability node
                if (pauseRequested && node is org.wip.plugintoolkit.features.flows.model.Node.CapabilityNode) {
                    manager.addJobLog(job.id, "Flow execution paused before capability: ${node.title}")
                    val resumeStateJson = JsonObject(mapOf(
                        "executedNodeIds" to JsonArray(executedNodeIds.map { JsonPrimitive(it) }),
                        "activeNodes" to JsonArray(activeNodes.map { JsonPrimitive(it) }),
                        "computedValues" to JsonArray(computedValues.map { (key, value) ->
                            JsonObject(mapOf(
                                "nodeId" to JsonPrimitive(key.first),
                                "portId" to JsonPrimitive(key.second),
                                "value" to toJsonElement(value)
                            ))
                        }),
                        "capabilityResumeStates" to JsonObject(capabilityResumeStates.mapKeys { it.key.toString() })
                    ))
                    manager.tryPauseJob(job.id, resumeStateJson)
                    return
                }

                manager.addJobLog(job.id, "Executing node [${node.title}] (${index + 1}/${flow.nodes.size})...")
                
                // Periodically report progress (0.1 to 0.95 range)
                val nodeProgress = 0.1f + (index.toFloat() / flow.nodes.size.toFloat()) * 0.8f
                manager.updateJobProgress(job.id, nodeProgress)

                when (node) {
                    is org.wip.plugintoolkit.features.flows.model.Node.FlowInputNode -> {
                        val outputPort = node.outputs.firstOrNull()
                        if (outputPort != null) {
                            val key = "${node.id}"
                            val valueJson = job.parameters[key] ?: job.parameters[outputPort.id]
                            val rawValue = valueJson?.let { je ->
                                fromJsonElement(je)
                            } ?: ""
                            computedValues[Pair(node.id, outputPort.id)] = rawValue
                            manager.addJobLog(job.id, "Flow input node [${node.title}] populated with: $rawValue")
                        }
                    }
                    is org.wip.plugintoolkit.features.flows.model.Node.SystemNode -> {
                        val executor = executorRegistry.getExecutor(node.systemAction)
                        val context = object : NodeExecutionContext {
                            override val node: org.wip.plugintoolkit.features.flows.model.Node.SystemNode = node
                            override val job: BackgroundJob = job
                            override val appDataDir: String = appDataDir
                            override val runtimeInferredTypes: Map<Pair<Long, String>, DataType> = runtimeInferred

                            override fun getInputValue(portId: String, defaultValue: Any?): Any? {
                                return getInputValue(node.id, portId, defaultValue)
                            }

                            override fun setOutputValue(portId: String, value: Any?) {
                                computedValues[Pair(node.id, portId)] = value
                            }

                            override fun addLog(message: String, level: String) {
                                manager.addJobLog(job.id, message, level)
                            }

                            override suspend fun executeSubFlow(flowName: String, parameters: Map<String, JsonElement>): Map<String, Any?> {
                                val subFlowFile = Path("$appDataDir/flows/${flowName.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.json")
                                if (!SystemFileSystem.exists(subFlowFile)) {
                                    throw Exception("Subflow file not found: $flowName")
                                }
                                val subFlowContent = SystemFileSystem.source(subFlowFile).buffered().use { it.readString() }
                                val subFlow = Json { ignoreUnknownKeys = true; encodeDefaults = true }
                                    .decodeFromString<org.wip.plugintoolkit.features.flows.model.Flow>(subFlowContent)

                                val subJob = BackgroundJob(
                                    id = "${job.id}-sub-${node.id}",
                                    name = "Subflow: $flowName",
                                    type = JobType.Flow,
                                    pluginId = "system",
                                    capabilityName = flowName,
                                    parameters = parameters
                                )
                                return executeSubFlowRecursively(subFlow, subJob, appDataDir)
                            }
                        }
                        executor.execute(context)
                    }
                    is org.wip.plugintoolkit.features.flows.model.Node.CapabilityNode -> {
                        // Gather inputs as a Map<String, JsonElement>
                        val capabilityParameters = mutableMapOf<String, JsonElement>()
                        node.inputs.forEach { port ->
                            val resolvedVal = getInputValue(node.id, port.id, null)
                            capabilityParameters[port.id] = toJsonElement(resolvedVal)
                        }
                        
                        val plugin = PluginLoader.getPluginById(node.pluginInfo.id)
                            ?: throw Exception("Plugin ${node.pluginInfo.id} not found")
                        
                        validateCapabilityParameters(plugin.getManifest(), node.capability.name, capabilityParameters)
                        
                        val processor = plugin.getProcessor()
                        val context = pluginManager.createPluginContext(node.pluginInfo.id, job.id)
                        
                        val savedCapResumeState = capabilityResumeStates[node.id]
                        val request = PluginRequest(
                            method = node.capability.name,
                            parameters = capabilityParameters,
                            resumeState = savedCapResumeState
                        )
                        
                        manager.addJobLog(job.id, "Invoking plugin capability: ${node.pluginInfo.name} -> ${node.capability.name}...")
                        
                        val deferredResult = workerScope.async {
                            withTimeout(600_000L) { // 10 minutes timeout
                                processor.process(request, context)
                            }
                        }

                        val handle = object : JobHandle {
                            override val result: Deferred<ExecutionResult> = deferredResult
                            override fun pause() {
                                workerScope.launch { context.signals.sendSignal(org.wip.plugintoolkit.api.PluginSignal.PAUSE) }
                            }
                            override fun cancel(force: Boolean) {
                                workerScope.launch { context.signals.sendSignal(org.wip.plugintoolkit.api.PluginSignal.CANCEL) }
                                deferredResult.cancel()
                            }
                        }
                        
                        activeCapabilityHandle = handle
                        
                        val result = try {
                            handle.result.await()
                        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                            manager.addJobLog(job.id, "CRITICAL WARNING: Capability ${node.capability.name} timed out.", "ERROR")
                            ExecutionResult.Error("Timeout", e)
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            if (pauseRequested) {
                                ExecutionResult.Paused(JsonNull)
                            } else {
                                throw e
                            }
                        } catch (e: Exception) {
                            throw Exception("Capability ${node.capability.name} failed during await: ${e.message}", e)
                        } finally {
                            activeCapabilityHandle = null
                        }
                        
                        when (result) {
                            is ExecutionResult.Success -> {
                                capabilityResumeStates.remove(node.id)
                                val outputVal = result.response.result
                                computedValues[Pair(node.id, "result")] = outputVal
                                if (outputVal is JsonObject) {
                                    for ((key, value) in outputVal) {
                                        computedValues[Pair(node.id, key)] = value
                                    }
                                }
                                manager.addJobLog(job.id, "Capability invocation succeeded.")
                            }
                            is ExecutionResult.Error -> {
                                capabilityResumeStates.remove(node.id)
                                throw Exception("Capability invocation failed: ${result.message}")
                            }
                            is ExecutionResult.Paused -> {
                                capabilityResumeStates[node.id] = result.resumeState
                                manager.addJobLog(job.id, "Capability requested pause mid-work.")
                                
                                val resumeStateJson = JsonObject(mapOf(
                                    "executedNodeIds" to JsonArray(executedNodeIds.map { JsonPrimitive(it) }),
                                    "activeNodes" to JsonArray(activeNodes.map { JsonPrimitive(it) }),
                                    "computedValues" to JsonArray(computedValues.map { (key, value) ->
                                        JsonObject(mapOf(
                                            "nodeId" to JsonPrimitive(key.first),
                                            "portId" to JsonPrimitive(key.second),
                                            "value" to toJsonElement(value)
                                        ))
                                    }),
                                    "capabilityResumeStates" to JsonObject(capabilityResumeStates.mapKeys { it.key.toString() })
                                ))
                                manager.tryPauseJob(job.id, resumeStateJson)
                                return
                            }
                        }
                    }
                    is org.wip.plugintoolkit.features.flows.model.Node.FlowOutputNode -> {
                        val inputPort = node.inputs.firstOrNull()
                        if (inputPort != null) {
                            val finalVal = getInputValue(node.id, inputPort.id, null)
                            flowOutputs[inputPort.name] = finalVal
                            computedValues[Pair(node.id, inputPort.id)] = finalVal
                            manager.addJobLog(job.id, "Flow output collected [${inputPort.name}]: $finalVal")
                        }
                    }
                    is org.wip.plugintoolkit.features.flows.model.Node.SubFlowNode -> {
                        // Recursive subflow execution
                        manager.addJobLog(job.id, "Entering subflow execution for '${node.flowName}'...")
                        
                        val subFlowFile = Path("$appDataDir/flows/${node.flowName.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.json")
                        if (!SystemFileSystem.exists(subFlowFile)) {
                            throw Exception("Subflow file not found: ${node.flowName}")
                        }
                        val subFlowContent = SystemFileSystem.source(subFlowFile).buffered().use { it.readString() }
                        val subFlow = Json { ignoreUnknownKeys = true; encodeDefaults = true }.decodeFromString<org.wip.plugintoolkit.features.flows.model.Flow>(subFlowContent)
                        
                        // Build nested parameters
                        val subParameters = mutableMapOf<String, JsonElement>()
                        node.inputs.forEach { port ->
                            val mapping = node.inputMappings.find { it.portId == port.id }
                            if (mapping != null) {
                                val resolvedVal = getInputValue(node.id, port.id, null)
                                subParameters["${mapping.boundaryNodeId}"] = toJsonElement(resolvedVal)
                            }
                        }
                        
                        // Create simulated sub-job
                        val subJob = BackgroundJob(
                            id = "${job.id}-sub-${node.id}",
                            name = "Subflow: ${node.flowName}",
                            type = JobType.Flow,
                            pluginId = "system",
                            capabilityName = node.flowName,
                            parameters = subParameters
                        )
                        
                        val subOutputs = executeSubFlowRecursively(subFlow, subJob, appDataDir)
                        
                        // Map subflow outputs back to parent output ports
                        val subNodesById = subFlow.nodes.associateBy { it.id }
                        node.outputs.forEach { port ->
                            val mapping = node.outputMappings.find { it.portId == port.id }
                            if (mapping != null) {
                                val boundaryOutputNode = subNodesById[mapping.boundaryNodeId] as? org.wip.plugintoolkit.features.flows.model.Node.FlowOutputNode
                                val boundaryPortName = boundaryOutputNode?.inputs?.firstOrNull()?.name ?: "output_data"
                                val valFromSub = subOutputs[boundaryPortName]
                                computedValues[Pair(node.id, port.id)] = valFromSub
                            }
                        }
                        manager.addJobLog(job.id, "Successfully exited subflow execution for '${node.flowName}'.")
                    }
                }

                val activeOutputs = mutableSetOf<String>()
                when (node) {
                    is org.wip.plugintoolkit.features.flows.model.Node.FlowInputNode -> activeOutputs.addAll(node.outputs.map { it.id })
                    is org.wip.plugintoolkit.features.flows.model.Node.FlowOutputNode -> activeOutputs.addAll(node.outputs.map { it.id })
                    is org.wip.plugintoolkit.features.flows.model.Node.CapabilityNode -> activeOutputs.addAll(node.outputs.map { it.id })
                    is org.wip.plugintoolkit.features.flows.model.Node.SubFlowNode -> activeOutputs.addAll(node.outputs.map { it.id })
                    is org.wip.plugintoolkit.features.flows.model.Node.SystemNode -> {
                        if (node.systemAction.lowercase() == "conditional") {
                            val conditionVal = getInputValue(node.id, "condition", false)
                            val condition = when (conditionVal) {
                                is Boolean -> conditionVal
                                is String -> conditionVal.toBoolean()
                                is Number -> conditionVal.toInt() != 0
                                else -> false
                            }
                            if (condition) {
                                activeOutputs.add("if_true")
                            } else {
                                activeOutputs.add("if_false")
                            }
                        } else {
                            activeOutputs.addAll(node.outputs.map { it.id })
                        }
                    }
                }

                activeOutputs.forEach { portId ->
                    connectionsBySource[Pair(node.id, portId)]?.forEach { conn ->
                        activeNodes.add(conn.targetNodeId)
                    }
                }

                executedNodeIds.add(node.id)
            }

            // 4. Serialize results and complete job
            val jsonOutputs = toJsonElement(flowOutputs).toString()
            manager.addJobLog(job.id, "Flow executed successfully. Final output results: $jsonOutputs")
            manager.tryCompleteJob(job.id, jsonOutputs)
            lifecycleCoordinator.onLifecycleJobCompleted(job)
        } finally {
            manager.unregisterJobHandle(job.id)
        }
    }

    internal suspend fun executeSubFlowRecursively(
        flow: org.wip.plugintoolkit.features.flows.model.Flow,
        job: BackgroundJob,
        appDataDir: String
    ): Map<String, Any?> {
        // Cache the type inference
        val runtimeInferred = FlowTypeInferenceCache.getOrCreate(flow) {
            runRuntimeTypeInference(flow)
        }

        // Build lookup maps
        val nodesById = flow.nodes.associateBy { it.id }
        val connectionsByTarget = flow.connections.groupBy { Pair(it.targetNodeId, it.targetPortId) }
        val connectionsBySource = flow.connections.groupBy { Pair(it.sourceNodeId, it.sourcePortId) }

        // Build sub-DAG
        val inDegree = mutableMapOf<Long, Int>()
        val adj = mutableMapOf<Long, MutableList<Long>>()

        flow.nodes.forEach { node ->
            inDegree[node.id] = 0
            adj[node.id] = mutableListOf()
        }

        flow.connections.forEach { conn ->
            if (inDegree.containsKey(conn.targetNodeId) && inDegree.containsKey(conn.sourceNodeId)) {
                adj[conn.sourceNodeId]?.add(conn.targetNodeId)
                inDegree[conn.targetNodeId] = (inDegree[conn.targetNodeId] ?: 0) + 1
            }
        }

        val queue = ArrayDeque<Long>()
        flow.nodes.forEach { node ->
            if (inDegree[node.id] == 0) {
                queue.addLast(node.id)
            }
        }

        val executionOrder = mutableListOf<Long>()
        while (queue.isNotEmpty()) {
            val nodeId = queue.removeFirst()
            executionOrder.add(nodeId)
            adj[nodeId]?.forEach { neighbor ->
                inDegree[neighbor] = (inDegree[neighbor] ?: 1) - 1
                if (inDegree[neighbor] == 0) {
                    queue.addLast(neighbor)
                }
            }
        }

        val computedValues = mutableMapOf<Pair<Long, String>, Any?>()
        val flowOutputs = mutableMapOf<String, Any?>()

        // Helper to get input value
        fun getInputValue(nodeId: Long, portId: String, defaultValue: Any?): Any? {
            val conns = connectionsByTarget[Pair(nodeId, portId)]
            if (conns != null && conns.isNotEmpty()) {
                val sortedConns = conns.sortedBy { it.orderIndex ?: 0 }
                val targetNode = nodesById[nodeId]
                val targetPort = targetNode?.inputs?.find { it.id == portId }
                
                if (targetPort?.dataType is DataType.Array) {
                    val results = sortedConns.map { conn ->
                        val raw = computedValues[Pair(conn.sourceNodeId, conn.sourcePortId)]
                        if (raw is JsonElement) fromJsonElement(raw) else raw
                    }
                    if (sortedConns.size == 1) {
                        val singleParsed = results.first()
                        if (singleParsed is List<*>) {
                            return singleParsed
                        } else {
                            return listOf(singleParsed)
                        }
                    }
                    return results
                } else {
                    val conn = sortedConns.first()
                    val raw = computedValues[Pair(conn.sourceNodeId, conn.sourcePortId)]
                    return if (raw is JsonElement) fromJsonElement(raw) else raw
                }
            } else {
                val node = nodesById[nodeId] ?: return defaultValue
                val port = node.inputs.find { it.id == portId }
                val raw = port?.value ?: port?.defaultValue ?: defaultValue
                return if (raw is JsonElement) fromJsonElement(raw) else raw
            }
        }

        val activeNodes = mutableSetOf<Long>()
        val nodesWithIncoming = flow.connections.map { it.targetNodeId }.toSet()
        flow.nodes.forEach { n ->
            if (!nodesWithIncoming.contains(n.id)) {
                activeNodes.add(n.id)
            }
        }

        executionOrder.forEach { nodeId ->
            val node = nodesById[nodeId] ?: return@forEach
            if (!activeNodes.contains(node.id)) {
                return@forEach
            }
            
            when (node) {
                is org.wip.plugintoolkit.features.flows.model.Node.FlowInputNode -> {
                    val outputPort = node.outputs.firstOrNull()
                    if (outputPort != null) {
                        val key = "${node.id}"
                        val valueJson = job.parameters[key] ?: job.parameters[outputPort.id]
                        val rawValue = valueJson?.let { je ->
                            fromJsonElement(je)
                        } ?: ""
                        computedValues[Pair(node.id, outputPort.id)] = rawValue
                    }
                }
                is org.wip.plugintoolkit.features.flows.model.Node.SystemNode -> {
                    val executor = executorRegistry.getExecutor(node.systemAction)
                    val context = object : NodeExecutionContext {
                        override val node: org.wip.plugintoolkit.features.flows.model.Node.SystemNode = node
                        override val job: BackgroundJob = job
                        override val appDataDir: String = appDataDir
                        override val runtimeInferredTypes: Map<Pair<Long, String>, DataType> = runtimeInferred

                        override fun getInputValue(portId: String, defaultValue: Any?): Any? {
                            return getInputValue(node.id, portId, defaultValue)
                        }

                        override fun setOutputValue(portId: String, value: Any?) {
                            computedValues[Pair(node.id, portId)] = value
                        }

                        override fun addLog(message: String, level: String) {
                            manager.addJobLog(job.id, message, level)
                        }

                        override suspend fun executeSubFlow(flowName: String, parameters: Map<String, JsonElement>): Map<String, Any?> {
                            val subFlowFile = Path("$appDataDir/flows/${flowName.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.json")
                            if (!SystemFileSystem.exists(subFlowFile)) {
                                throw Exception("Subflow file not found: $flowName")
                            }
                            val subFlowContent = SystemFileSystem.source(subFlowFile).buffered().use { it.readString() }
                            val subFlow = Json { ignoreUnknownKeys = true; encodeDefaults = true }
                                .decodeFromString<org.wip.plugintoolkit.features.flows.model.Flow>(subFlowContent)

                            val subJob = BackgroundJob(
                                id = "${job.id}-sub-${node.id}",
                                name = "Subflow: $flowName",
                                type = JobType.Flow,
                                pluginId = "system",
                                capabilityName = flowName,
                                parameters = parameters
                            )
                            return executeSubFlowRecursively(subFlow, subJob, appDataDir)
                        }
                    }
                    executor.execute(context)
                }
                is org.wip.plugintoolkit.features.flows.model.Node.CapabilityNode -> {
                    val capabilityParameters = mutableMapOf<String, JsonElement>()
                    node.inputs.forEach { port ->
                        val resolvedVal = getInputValue(node.id, port.id, null)
                        capabilityParameters[port.id] = toJsonElement(resolvedVal)
                    }
                    
                    val plugin = PluginLoader.getPluginById(node.pluginInfo.id)
                        ?: throw Exception("Plugin ${node.pluginInfo.id} not found")
                    
                    validateCapabilityParameters(plugin.getManifest(), node.capability.name, capabilityParameters)
                    
                    val processor = plugin.getProcessor()
                    val context = pluginManager.createPluginContext(node.pluginInfo.id, job.id)
                    val request = PluginRequest(
                        method = node.capability.name,
                        parameters = capabilityParameters
                    )
                    
                    val deferredResult = workerScope.async {
                        withTimeout(600_000L) {
                            processor.process(request, context)
                        }
                    }
                    val result = try {
                        deferredResult.await()
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        ExecutionResult.Error("Timeout", e)
                    } catch (e: Exception) {
                        ExecutionResult.Error("Exception", e)
                    }
                    
                    when (result) {
                        is ExecutionResult.Success -> {
                            val outputVal = result.response.result
                            computedValues[Pair(node.id, "result")] = outputVal
                            if (outputVal is JsonObject) {
                                for ((key, value) in outputVal) {
                                    computedValues[Pair(node.id, key)] = value
                                }
                            }
                        }
                        else -> throw Exception("Subflow Capability execution failed.")
                    }
                }
                is org.wip.plugintoolkit.features.flows.model.Node.FlowOutputNode -> {
                    val inputPort = node.inputs.firstOrNull()
                    if (inputPort != null) {
                        val finalVal = getInputValue(node.id, inputPort.id, null)
                        flowOutputs[inputPort.name] = finalVal
                        computedValues[Pair(node.id, inputPort.id)] = finalVal
                    }
                }
                is org.wip.plugintoolkit.features.flows.model.Node.SubFlowNode -> {
                    val subFlowFile = Path("$appDataDir/flows/${node.flowName.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.json")
                    if (SystemFileSystem.exists(subFlowFile)) {
                        val subFlowContent = SystemFileSystem.source(subFlowFile).buffered().use { it.readString() }
                        val subFlow = Json { ignoreUnknownKeys = true; encodeDefaults = true }.decodeFromString<org.wip.plugintoolkit.features.flows.model.Flow>(subFlowContent)
                        
                        val subParameters = mutableMapOf<String, JsonElement>()
                        node.inputs.forEach { port ->
                            val mapping = node.inputMappings.find { it.portId == port.id }
                            if (mapping != null) {
                                val resolvedVal = getInputValue(node.id, port.id, null)
                                subParameters["${mapping.boundaryNodeId}"] = toJsonElement(resolvedVal)
                            }
                        }
                        val subJob = BackgroundJob(
                            id = "${job.id}-sub-${node.id}",
                            name = "Subflow: ${node.flowName}",
                            type = JobType.Flow,
                            pluginId = "system",
                            capabilityName = node.flowName,
                            parameters = subParameters
                        )
                        val subOutputs = executeSubFlowRecursively(subFlow, subJob, appDataDir)
                        
                        val subNodesById = subFlow.nodes.associateBy { it.id }
                        node.outputs.forEach { port ->
                            val mapping = node.outputMappings.find { it.portId == port.id }
                            if (mapping != null) {
                                val boundaryOutputNode = subNodesById[mapping.boundaryNodeId] as? org.wip.plugintoolkit.features.flows.model.Node.FlowOutputNode
                                val boundaryPortName = boundaryOutputNode?.inputs?.firstOrNull()?.name ?: "output_data"
                                computedValues[Pair(node.id, port.id)] = subOutputs[boundaryPortName]
                            }
                        }
                    }
                }
            }

            val activeOutputs = mutableSetOf<String>()
            when (node) {
                is org.wip.plugintoolkit.features.flows.model.Node.FlowInputNode -> activeOutputs.addAll(node.outputs.map { it.id })
                is org.wip.plugintoolkit.features.flows.model.Node.FlowOutputNode -> activeOutputs.addAll(node.outputs.map { it.id })
                is org.wip.plugintoolkit.features.flows.model.Node.CapabilityNode -> activeOutputs.addAll(node.outputs.map { it.id })
                is org.wip.plugintoolkit.features.flows.model.Node.SubFlowNode -> activeOutputs.addAll(node.outputs.map { it.id })
                is org.wip.plugintoolkit.features.flows.model.Node.SystemNode -> {
                    if (node.systemAction.lowercase() == "conditional") {
                        val conditionVal = getInputValue(node.id, "condition", false)
                        val condition = when (conditionVal) {
                            is Boolean -> conditionVal
                            is String -> conditionVal.toBoolean()
                            is Number -> conditionVal.toInt() != 0
                            else -> false
                        }
                        if (condition) {
                            activeOutputs.add("if_true")
                        } else {
                            activeOutputs.add("if_false")
                        }
                    } else {
                        activeOutputs.addAll(node.outputs.map { it.id })
                    }
                }
            }

            activeOutputs.forEach { portId ->
                connectionsBySource[Pair(node.id, portId)]?.forEach { conn ->
                    activeNodes.add(conn.targetNodeId)
                }
            }
        }
        return flowOutputs
    }
}
