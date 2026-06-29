package org.wip.plugintoolkit.features.job.logic

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.JobHandle
import org.wip.plugintoolkit.api.ParameterRole
import org.wip.plugintoolkit.api.PluginRequest
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.logic.PathPatternResolver
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.logic.PluginLifecycleCoordinator
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence

class FlowEngine(
    private val manager: JobManager,
    private val executorRegistry: SystemNodeExecutorRegistry,
    private val pluginManager: PluginManager,
    private val lifecycleCoordinator: PluginLifecycleCoordinator,
    private val workerScope: CoroutineScope
) : KoinComponent {
    private val settingsRepository: org.wip.plugintoolkit.features.settings.logic.SettingsRepository by inject()

    suspend fun executeFlowJob(job: BackgroundJob) {
        manager.addJobLog(job.id, "Starting flow execution for '${job.capabilityName}'...")
        manager.updateJobProgress(job.id, 0.05f)

        val settingsPersistence: SettingsPersistence by inject()
        val appDataDir = settingsPersistence.getSettingsDir()
        val safeName = job.capabilityName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val file = Path("$appDataDir/flows/$safeName.json")

        if (!SystemFileSystem.exists(file)) {
            throw Exception("Flow definition file not found: ${file.name}")
        }

        val flowContent = SystemFileSystem.source(file).buffered().use { it.readString() }
        val flow = Json { ignoreUnknownKeys = true; encodeDefaults = true }
            .decodeFromString<org.wip.plugintoolkit.features.flows.model.Flow>(flowContent)

        manager.addJobLog(
            job.id,
            "Loaded flow '${flow.name}' with ${flow.nodes.size} nodes and ${flow.connections.size} connections."
        )

        try {
            val jsonOutputs = executeGraph(flow, job, appDataDir, job.parameters, true, job.resumeState as? JsonObject, 0)
            val finalJson = toJsonElement(jsonOutputs).toString()
            val outputFileStr = job.parameters["-1"]?.let { param ->
                try {
                    val primitive = param as? JsonPrimitive
                    if (primitive != null && primitive.isString) primitive.content else null
                } catch (e: Exception) {
                    null
                }
            }
            if (!outputFileStr.isNullOrBlank() && job.keepResult) {
                try {
                    val path = kotlinx.io.files.Path(outputFileStr)
                    val parent = path.parent
                    if (parent != null && !SystemFileSystem.exists(parent)) {
                        SystemFileSystem.createDirectories(parent)
                    }
                    val metadata = kotlinx.io.files.SystemFileSystem.metadataOrNull(path)
                    if (metadata?.isDirectory == true) {
                        manager.addJobLog(job.id, "Warning: Flow output path is a directory, cannot save result to $outputFileStr")
                    } else {
                        SystemFileSystem.sink(path).buffered().use { sink ->
                            sink.writeString(finalJson)
                        }
                        manager.addJobLog(job.id, "Flow result saved to: $outputFileStr")
                    }
                } catch (e: Exception) {
                    manager.addJobLog(job.id, "Warning: Failed to save flow result to $outputFileStr: ${e.message}")
                }
            }

            manager.addJobLog(job.id, "Flow executed successfully. Final output results: $finalJson")
            manager.tryCompleteJob(job.id, finalJson)
            lifecycleCoordinator.onLifecycleJobCompleted(job)
        } catch (e: PauseFlowException) {
            manager.addJobLog(job.id, "Flow requested pause mid-work.")
            manager.tryPauseJob(job.id, e.resumeState as JsonObject)
        } catch (e: CancellationException) {
            throw e
        } finally {
            manager.unregisterJobHandle(job.id)
        }
    }

    internal suspend fun executeSubFlowRecursively(
        flow: org.wip.plugintoolkit.features.flows.model.Flow,
        job: BackgroundJob,
        appDataDir: String,
        resumeStateOverride: JsonObject? = null,
        depth: Int = 0
    ): Map<String, Any?> {
        if (depth > 50) throw Exception("StackOverflow prevention: Subflow recursion depth exceeded 50.")
        return executeGraph(flow, job, appDataDir, job.parameters, false, resumeStateOverride, depth)
    }

    private suspend fun executeGraph(
        flow: org.wip.plugintoolkit.features.flows.model.Flow,
        job: BackgroundJob,
        appDataDir: String,
        initialParameters: Map<String, JsonElement>,
        isRoot: Boolean,
        resumeStateOverride: JsonObject?,
        depth: Int
    ): Map<String, Any?> {
        val runtimeInferred = FlowTypeInferenceCache.getOrCreate(flow) { runRuntimeTypeInference(flow) }
        val nodesById = flow.nodes.associateBy { it.id }
        val connectionsByTarget = flow.connections.groupBy { Pair(it.targetNodeId, it.targetPortId) }
        val connectionsBySource = flow.connections.groupBy { Pair(it.sourceNodeId, it.sourcePortId) }

        val computedValues = mutableMapOf<Pair<Long, String>, Any?>()
        val flowOutputs = mutableMapOf<String, Any?>()

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
                    return results.flatMap {
                        if (it is List<*>) it else listOf(it)
                    }
                } else {
                    val conn = sortedConns.first()
                    val raw = computedValues[Pair(conn.sourceNodeId, conn.sourcePortId)]
                    return if (raw is JsonElement) fromJsonElement(raw) else raw
                }
            } else {
                val overrideKey = "${nodeId}_${portId}"
                val overrideVal = initialParameters[overrideKey]
                if (overrideVal != null) {
                    return fromJsonElement(overrideVal)
                }

                val node = nodesById[nodeId] ?: return defaultValue
                val port = node.inputs.find { it.id == portId }
                val rawValue = port?.value
                val rawDefault = port?.defaultValue

                val isStringPort =
                    port?.dataType is DataType.Primitive && port.dataType.primitiveType == PrimitiveType.STRING
                val isRawValueEmptyString =
                    (rawValue is JsonPrimitive && rawValue.isString && rawValue.content.isEmpty()) || (rawValue is String && rawValue.isEmpty())
                val isRawDefaultEmptyString =
                    (rawDefault is JsonPrimitive && rawDefault.isString && rawDefault.content.isEmpty()) || (rawDefault is String && rawDefault.isEmpty())

                val raw = if (rawValue != null && rawValue !is JsonNull && (isStringPort || !isRawValueEmptyString)) {
                    rawValue
                } else if (rawDefault != null && rawDefault !is JsonNull && (isStringPort || !isRawDefaultEmptyString)) {
                    rawDefault
                } else {
                    defaultValue
                }

                return if (raw is JsonElement) fromJsonElement(raw) else raw
            }
        }

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
                if (inDegree[neighbor] == 0) queue.addLast(neighbor)
            }
        }
        if (executionOrder.size < flow.nodes.size) {
            throw Exception("Cycle or invalid connection structure detected in flow graph.")
        }

        val activeNodes = mutableSetOf<Long>()
        val executedNodeIds = mutableSetOf<Long>()
        val capabilityResumeStates = mutableMapOf<Long, JsonElement>()

        val jobExecution = currentCoroutineContext()[kotlinx.coroutines.Job]!!
        var pauseRequested = false
        var activeCapabilityHandle: JobHandle? = null

        if (isRoot) {
            val flowHandle = object : JobHandle {
                override val result: Deferred<ExecutionResult> get() = throw UnsupportedOperationException("Not used directly")
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
        }

        if (resumeStateOverride != null) {
            resumeStateOverride["executedNodeIds"]?.jsonArray?.map { it.jsonPrimitive.long }
                ?.let { executedNodeIds.addAll(it) }
            resumeStateOverride["activeNodes"]?.jsonArray?.map { it.jsonPrimitive.long }?.let { activeNodes.addAll(it) }
            resumeStateOverride["computedValues"]?.jsonArray?.forEach { entry ->
                val obj = entry.jsonObject
                val nodeId = obj["nodeId"]?.jsonPrimitive?.long
                val portId = obj["portId"]?.jsonPrimitive?.content
                val valueJson = obj["value"]
                if (nodeId != null && portId != null && valueJson != null) {
                    computedValues[Pair(nodeId, portId)] = fromJsonElement(valueJson)
                }
            }
            resumeStateOverride["capabilityResumeStates"]?.jsonObject?.forEach { (nodeIdStr, resumeStateJson) ->
                nodeIdStr.toLongOrNull()?.let { nodeId ->
                    capabilityResumeStates[nodeId] = resumeStateJson
                }
            }
            if (isRoot) manager.addJobLog(
                job.id,
                "Resuming flow execution from saved state. Executed nodes: ${executedNodeIds.size}"
            )
        } else {
            val nodesWithIncoming = flow.connections.map { it.targetNodeId }.toSet()
            flow.nodes.forEach { n ->
                if (!nodesWithIncoming.contains(n.id)) activeNodes.add(n.id)
            }
        }

        fun buildCurrentState(): JsonObject {
            return JsonObject(
                mapOf(
                    "executedNodeIds" to JsonArray(executedNodeIds.map { JsonPrimitive(it) }),
                    "activeNodes" to JsonArray(activeNodes.map { JsonPrimitive(it) }),
                    "computedValues" to JsonArray(computedValues.map { (key, value) ->
                        JsonObject(
                            mapOf(
                                "nodeId" to JsonPrimitive(key.first),
                                "portId" to JsonPrimitive(key.second),
                                "value" to toJsonElement(value)
                            )
                        )
                    }),
                    "capabilityResumeStates" to JsonObject(capabilityResumeStates.mapKeys { it.key.toString() })
                )
            )
        }

        executionOrder.forEachIndexed { index, nodeId ->
            kotlinx.coroutines.yield()
            val node = nodesById[nodeId] ?: return@forEachIndexed
            if (executedNodeIds.contains(node.id)) return@forEachIndexed
            if (!activeNodes.contains(node.id)) return@forEachIndexed

            if (pauseRequested && node is org.wip.plugintoolkit.features.flows.model.Node.CapabilityNode) {
                if (isRoot) {
                    manager.addJobLog(job.id, "Flow execution paused before capability: ${node.title}")
                    throw PauseFlowException(buildCurrentState())
                }
            }

            if (isRoot) {
                val nodeProgress = 0.1f + (index.toFloat() / flow.nodes.size.toFloat()) * 0.8f
                manager.updateJobProgress(job.id, nodeProgress)
            }

            when (node) {
                is org.wip.plugintoolkit.features.flows.model.Node.FlowInputNode -> {
                    val outputPort = node.outputs.firstOrNull()
                    if (outputPort != null) {
                        val key = "${node.id}"
                        val valueJson = initialParameters[key] ?: initialParameters[outputPort.id]
                        val rawValue = valueJson?.let { je -> fromJsonElement(je) } ?: ""
                        computedValues[Pair(node.id, outputPort.id)] = rawValue
                    }
                }

                is org.wip.plugintoolkit.features.flows.model.Node.SystemNode -> {
                    val executor = executorRegistry.getExecutor(node.systemAction)
                    val sysResumeState = capabilityResumeStates[node.id]
                    val context = object : NodeExecutionContext {
                        override val node: org.wip.plugintoolkit.features.flows.model.Node.SystemNode = node
                        override val job = job
                        override val appDataDir = appDataDir
                        override val runtimeInferredTypes = runtimeInferred
                        override val resumeState = sysResumeState
                        override fun getInputValue(portId: String, defaultValue: Any?) =
                            getInputValue(node.id, portId, defaultValue)

                        override fun setOutputValue(portId: String, value: Any?) {
                            computedValues[Pair(node.id, portId)] = value
                        }

                        override fun addLog(message: String, level: String) {
                            manager.addJobLog(job.id, message, level)
                        }

                        override suspend fun executeSubFlow(
                            flowName: String,
                            parameters: Map<String, JsonElement>
                        ): Map<String, Any?> {
                            val subFlowFile =
                                Path("$appDataDir/flows/${flowName.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.json")
                            if (!SystemFileSystem.exists(subFlowFile)) throw Exception("Subflow file not found: $flowName")
                            val subFlowContent = SystemFileSystem.source(subFlowFile).buffered().use { it.readString() }
                            val subFlow = Json {
                                ignoreUnknownKeys = true; encodeDefaults = true
                            }.decodeFromString<org.wip.plugintoolkit.features.flows.model.Flow>(subFlowContent)
                            val subJob = BackgroundJob(
                                id = "${job.id}-sub-${node.id}",
                                name = "Subflow: $flowName",
                                type = JobType.Flow,
                                pluginId = "system",
                                capabilityName = flowName,
                                parameters = parameters
                            )

                            val nestedResumeState =
                                (sysResumeState as? JsonObject)?.get("subflowResumeState") as? JsonObject
                            return executeSubFlowRecursively(subFlow, subJob, appDataDir, nestedResumeState, depth + 1)
                        }
                    }
                    try {
                        executor.execute(context)
                        capabilityResumeStates.remove(node.id)
                    } catch (e: PauseFlowException) {
                        capabilityResumeStates[node.id] = e.resumeState
                        throw PauseFlowException(buildCurrentState())
                    }
                }

                is org.wip.plugintoolkit.features.flows.model.Node.CapabilityNode -> {
                    val capabilityParameters = mutableMapOf<String, JsonElement>()
                    node.inputs.forEach { port ->
                        val resolvedVal = getInputValue(node.id, port.id, null)
                        val jsonVal = toJsonElement(resolvedVal)
                        if (jsonVal !is JsonNull) capabilityParameters[port.id] = jsonVal
                    }

                    val nodeSandbox = "$appDataDir/jobs/${job.id}/sandbox/node_${node.id}"

                    node.capability.parameters?.forEach { (key, meta) ->
                        val isOutputLocation = meta.role == ParameterRole.OUTPUT_LOCATION
                        val currentValue = capabilityParameters[key]?.let { if (it is JsonPrimitive && it.isString) it.content else null }
                        
                        if (isOutputLocation && currentValue.isNullOrBlank()) {
                            if (!meta.autogeneratedPattern.isNullOrBlank()) {
                                val stringValues = capabilityParameters.mapValues {
                                    val e = it.value
                                    if (e is JsonPrimitive && e.isString) e.content else e.toString()
                                }.toMutableMap()
                                stringValues["output_folder"] = nodeSandbox
                                try {
                                    val resolvedPath =
                                        PathPatternResolver.resolve(meta.autogeneratedPattern!!, stringValues)
                                    val pathObj = kotlinx.io.files.Path(resolvedPath)
                                    val finalPath = kotlinx.io.files.Path(nodeSandbox, pathObj.name).toString()
                                    capabilityParameters[key] = JsonPrimitive(finalPath)
                                } catch (e: IllegalArgumentException) {
                                    if (e.message?.startsWith("Missing required parameter") != true) {
                                        throw Exception(
                                            "Failed to autogenerate output path for capability '${node.capability.name}': ${e.message}",
                                            e
                                        )
                                    }
                                } catch (e: Exception) {
                                    throw Exception(
                                        "Failed to autogenerate output path for capability '${node.capability.name}': ${e.message}",
                                        e
                                    )
                                }
                            } else {
                                val generatedPath = kotlinx.io.files.Path(
                                    nodeSandbox,
                                    "${node.capability.name}_${key}_${kotlin.random.Random.nextInt(10000)}.tmp"
                                ).toString()
                                capabilityParameters[key] = JsonPrimitive(generatedPath)
                            }
                        }
                    }

                    val plugin = PluginLoader.getPluginById(node.pluginInfo.id)
                        ?: throw Exception("Plugin ${node.pluginInfo.id} not found")
                    val manifest = plugin.getManifest().getOrThrow()
                    val (allowedPaths, isDestructive) = resolveFileAccess(
                        manifest,
                        node.capability.name,
                        capabilityParameters,
                        nodeSandbox
                    )
                    validateCapabilityParameters(manifest, node.capability.name, capabilityParameters)

                    val processor = plugin.getProcessor().getOrThrow()
                    val execFs = org.wip.plugintoolkit.features.plugin.logic.DefaultExecutionFileSystem(nodeSandbox)
                    val context = pluginManager.createPluginContext(
                        node.pluginInfo.id,
                        job.id,
                        allowedPaths = allowedPaths,
                        isDestructiveAllowed = isDestructive,
                        executionFileSystem = execFs
                    )
                    val savedCapResumeState = capabilityResumeStates[node.id]
                    val request = PluginRequest(node.capability.name, capabilityParameters, savedCapResumeState)

                    manager.addJobLog(
                        job.id,
                        "Invoking plugin capability: ${node.pluginInfo.name} -> ${node.capability.name}..."
                    )

                    val paramLogStr = capabilityParameters.entries.joinToString("\n") { (k, v) -> 
                        val vStr = if (v is kotlinx.serialization.json.JsonPrimitive && v.isString) v.content else v.toString()
                        "  $k: $vStr"
                    }
                    if (paramLogStr.isNotEmpty()) {
                        manager.addJobLog(job.id, "With parameters:\n$paramLogStr", "VERBOSE")
                    }

                    val deferredResult = workerScope.async {
                        val settings = settingsRepository.settings.value.jobs
                        val timeout = settings.pluginTimeoutMs
                        val retries = if (settings.enableTransientRetries) settings.maxRetries else 0
                        var attempt = 0
                        var lastError: Throwable? = null

                        while (attempt <= retries) {
                            try {
                                if (timeout == -1L) {
                                    return@async kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        processor.process(request, context)
                                    }
                                } else {
                                    return@async kotlinx.coroutines.withTimeout(timeout) {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            processor.process(request, context)
                                        }
                                    }
                                }
                            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                                lastError = e
                                attempt++
                                if (attempt <= retries) {
                                    manager.addJobLog(job.id, "Timeout executing node ${node.capability.name}, retrying ($attempt/$retries)...", "WARN")
                                    kotlinx.coroutines.delay(2000)
                                }
                            } catch (e: kotlinx.io.IOException) {
                                lastError = e
                                attempt++
                                if (attempt <= retries) {
                                    manager.addJobLog(job.id, "IO error executing node ${node.capability.name}, retrying ($attempt/$retries)...", "WARN")
                                    kotlinx.coroutines.delay(2000)
                                }
                            } catch (e: Exception) {
                                // If it's a PauseFlowException or CancellationException, let it bubble up immediately
                                if (e is org.wip.plugintoolkit.features.job.logic.PauseFlowException || e is kotlinx.coroutines.CancellationException) {
                                    throw e
                                }
                                throw e // Non-transient errors bubble up immediately
                            }
                        }
                        throw Exception(lastError?.message ?: "Execution failed", lastError)
                    }

                    val handle = object : JobHandle {
                        override val result = deferredResult
                        override fun pause() {
                            workerScope.launch { context.signals.sendSignal(org.wip.plugintoolkit.api.PluginSignal.PAUSE) }
                        }

                        override fun cancel(force: Boolean) {
                            workerScope.launch { context.signals.sendSignal(org.wip.plugintoolkit.api.PluginSignal.CANCEL) }; deferredResult.cancel()
                        }
                    }
                    activeCapabilityHandle = handle

                    val result = try {
                        handle.result.await()
                    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                        ExecutionResult.Error("Timeout", e)
                    } catch (e: CancellationException) {
                        if (pauseRequested) ExecutionResult.Paused(JsonNull) else throw e
                    } catch (e: Exception) {
                        throw Exception(
                            "Capability invocation failed for node '${node.title.ifEmpty { node.id.toString() }}' (Plugin: '${node.pluginInfo.name}', Capability: '${node.capability.name}'): ${e.message}",
                            e
                        )
                    } finally {
                        activeCapabilityHandle = null
                    }

                    when (result) {
                        is ExecutionResult.Success -> {
                            capabilityResumeStates.remove(node.id)
                            val outputVal = result.response.result
                            computedValues[Pair(node.id, "result")] = outputVal
                            if (outputVal is JsonObject) {
                                for ((key, value) in outputVal) computedValues[Pair(node.id, key)] = value
                            }
                            node.capability.parameters?.forEach { (key, meta) ->
                                if (meta.role == ParameterRole.OUTPUT_LOCATION) {
                                    capabilityParameters[key]?.let {
                                        computedValues[Pair(node.id, key)] = fromJsonElement(it)
                                    }
                                }
                            }
                        }

                        is ExecutionResult.Error -> {
                            capabilityResumeStates.remove(node.id)
                            throw Exception("Capability invocation failed for node '${node.title.ifEmpty { node.id.toString() }}' (Plugin: '${node.pluginInfo.name}', Capability: '${node.capability.name}'): ${result.message}")
                        }

                        is ExecutionResult.Paused -> {
                            capabilityResumeStates[node.id] = result.resumeState
                            manager.addJobLog(job.id, "Capability requested pause mid-work.")
                            throw PauseFlowException(buildCurrentState())
                        }
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
                    val subFlowFile =
                        Path("$appDataDir/flows/${node.flowName.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.json")
                    if (SystemFileSystem.exists(subFlowFile)) {
                        val subFlowContent = SystemFileSystem.source(subFlowFile).buffered().use { it.readString() }
                        val subFlow = Json {
                            ignoreUnknownKeys = true; encodeDefaults = true
                        }.decodeFromString<org.wip.plugintoolkit.features.flows.model.Flow>(subFlowContent)

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

                        val subResumeState = capabilityResumeStates[node.id] as? JsonObject
                        val subOutputs = try {
                            executeSubFlowRecursively(subFlow, subJob, appDataDir, subResumeState, depth + 1)
                        } catch (e: PauseFlowException) {
                            capabilityResumeStates[node.id] = e.resumeState
                            throw PauseFlowException(buildCurrentState())
                        }

                        capabilityResumeStates.remove(node.id)

                        val subNodesById = subFlow.nodes.associateBy { it.id }
                        node.outputs.forEach { port ->
                            val mapping = node.outputMappings.find { it.portId == port.id }
                            if (mapping != null) {
                                val boundaryOutputNode =
                                    subNodesById[mapping.boundaryNodeId] as? org.wip.plugintoolkit.features.flows.model.Node.FlowOutputNode
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
                        if (condition) activeOutputs.add("if_true") else activeOutputs.add("if_false")
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

        return flowOutputs
    }
}
