package org.wip.plugintoolkit.features.job.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
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
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.core.utils.SemanticRegistry
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.JobHandle
import org.wip.plugintoolkit.api.PluginRequest
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.viewmodel.SystemNodesRegistry
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.koin.core.component.get
import org.wip.plugintoolkit.features.plugin.logic.PluginLifecycleCoordinator
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence

class JobWorker(
    val workerId: Int,
    private val manager: JobManager,
    private val scope: CoroutineScope
) : KoinComponent {
    private val pluginManager: PluginManager by inject()
    private val lifecycleCoordinator: PluginLifecycleCoordinator by inject()
    private var isActive = true
    private val workerJob = SupervisorJob(scope.coroutineContext[kotlinx.coroutines.Job])
    private val workerScope = scope + workerJob

    private val executorRegistry: SystemNodeExecutorRegistry by lazy {
        try {
            get<SystemNodeExecutorRegistry>()
        } catch (e: Exception) {
            DefaultSystemNodeExecutorRegistry()
        }
    }

    fun start() {
        workerScope.launch {
            while (isActive) {
                try {
                    Logger.d { "Worker $workerId: Waiting for next job..." }
                    val next = manager.waitForNextJob()
                    Logger.d { "Worker $workerId: Picked up job ${next.id} (${next.name})" }

                    // Link this execution to the manager for cancellation support
                    val jobExecution = launch {
                        try {
                            executeJob(next)
                        } finally {
                            Logger.d { "Worker $workerId: Finished job ${next.id}" }
                            manager.unregisterJobHandle(next.id)
                        }
                    }

                    try {
                        jobExecution.join()
                    } catch (e: Exception) {
                        if (e is CancellationException) {
                            Logger.i { "Worker $workerId: Loop coroutine cancelled while joining job execution" }
                            jobExecution.cancelAndJoin()
                            throw e
                        }
                        Logger.e(e) { "Worker $workerId: Error during job coordination" }
                        jobExecution.cancelAndJoin()
                    }

                } catch (e: CancellationException) {
                    Logger.i { "Worker $workerId: Loop received cancellation" }
                    if (!isActive) {
                        Logger.i { "Worker $workerId: Stopping loop" }
                        break
                    }
                } catch (e: Exception) {
                    Logger.e(e) { "Worker $workerId encountered error in loop" }
                    // Cooling-off period on error
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
    }

    private suspend fun executeJob(job: BackgroundJob) {
        val latestJob = manager.jobs.value.find { it.id == job.id }
        if (latestJob == null || latestJob.status != JobStatus.Running) {
            Logger.w { "Worker $workerId: Job ${job.name} (${job.id}) is not in Running state, aborting" }
            return
        }

        Logger.i { "Worker $workerId starting job ${job.name} (${job.id}) of type ${job.type}" }

        try {
            when (job.type) {
                JobType.Capability -> executeCapabilityJob(job)
                JobType.Setup -> executeSetupJob(job)
                JobType.Update -> executeUpdateJob(job)
                JobType.Validation -> executeValidationJob(job)
                JobType.PluginAction -> executePluginActionJob(job)
                JobType.PluginInstallation -> executeInstallationJob(job)
                JobType.Flow -> executeFlowJob(job)
                else -> throw Exception("Unsupported job type: ${job.type}")
            }
        } catch (e: CancellationException) {
            Logger.w { "Worker $workerId: Job ${job.name} was cancelled" }
            throw e
        } catch (e: Exception) {
            manager.tryFailJob(job.id, e.message)
            lifecycleCoordinator.onLifecycleJobFailed(job, e.message)
            Logger.e(e) { "Worker $workerId exception during job ${job.name}" }
        }
    }

    private suspend fun executeCapabilityJob(job: BackgroundJob) {
        val plugin = PluginLoader.getPluginById(job.pluginId)
            ?: throw Exception("Plugin ${job.pluginId} not found")

        validateCapabilityParameters(plugin.getManifest(), job.capabilityName, job.parameters)

        val processor = plugin.getProcessor()
        val context = pluginManager.createPluginContext(job.pluginId, job.id)

        val request = PluginRequest(
            method = job.capabilityName,
            parameters = job.parameters,
            resumeState = job.resumeState
        )

        val progressFlow = processor.observeProgress()
        val progressJob = progressFlow?.let { flow ->
            workerScope.launch {
                flow.collect { p ->
                    val current = manager.jobs.value.find { it.id == job.id }
                    if (current?.status == JobStatus.Running) {
                        manager.updateJobProgress(job.id, p)
                    } else {
                        this.cancel()
                    }
                }
            }
        }

        val handle = processor.processAsync(request, context)
        val currentJob = currentCoroutineContext()[kotlinx.coroutines.Job]!!

        // Wrap the handle to ensure that cancelling it also cancels our worker coroutine.
        // This prevents hanging in await() if the processor doesn't handle cancellation correctly.
        val wrappedHandle = object : JobHandle by handle {
            override fun cancel(force: Boolean) {
                Logger.d { "Worker $workerId: Cancelling capability job ${job.id} (force=$force)" }
                handle.cancel(force)
                currentJob.cancel()
            }
        }
        manager.registerJobHandle(job.id, wrappedHandle)

        val result = try {
            handle.result.await()
        } catch (e: CancellationException) {
            Logger.w { "Worker $workerId: Capability job ${job.id} was cancelled during await" }
            throw e
        } catch (e: Exception) {
            Logger.e(e) { "Worker $workerId: Capability job ${job.id} failed with exception" }
            ExecutionResult.Error(e.message ?: "Unknown error", e)
        } finally {
            progressJob?.cancel()
        }

        when (result) {
            is ExecutionResult.Success -> {
                val response = result.response
                manager.tryCompleteJob(job.id, response.result?.toString())
                lifecycleCoordinator.onLifecycleJobCompleted(job)
            }
            is ExecutionResult.Paused -> {
                manager.tryPauseJob(job.id, result.resumeState)
            }
            is ExecutionResult.Error -> {
                manager.tryFailJob(job.id, result.message)
                lifecycleCoordinator.onLifecycleJobFailed(job, result.message)
            }
        }
    }

    private suspend fun executeSetupJob(job: BackgroundJob) {
        val plugin = PluginLoader.getPluginById(job.pluginId)
            ?: throw Exception("Plugin ${job.pluginId} not found")

        val context = pluginManager.createPluginContext(job.pluginId, job.id)

        // Register a simple handle for cancellation
        val jobExecution = currentCoroutineContext()[kotlinx.coroutines.Job]!!
        manager.registerJobHandle(job.id, object : JobHandle {
            override val result: Deferred<ExecutionResult>
                get() = throw UnsupportedOperationException("Not used for setup")

            override fun pause() { /* Not supported */
            }

            override fun cancel(force: Boolean) {
                jobExecution.cancel()
            }
        })

        manager.updateJobProgress(job.id, 0.1f)
        manager.addJobLog(job.id, "Performing setup for ${plugin.getManifest().plugin.name}...")

        if (!plugin.getManifest().hasSetupHandler) {
            manager.addJobLog(job.id, "No setup handler found, skipping setup phase.")
        } else {
            val setupResult = plugin.performSetup(context)
            if (setupResult.isFailure) {
                val error = setupResult.exceptionOrNull()?.message ?: "Setup failed"
                manager.tryFailJob(job.id, error)
                return
            }
            manager.addJobLog(job.id, "Setup successful.")
        }

        manager.updateJobProgress(job.id, 1.0f)
        manager.addJobLog(job.id, "Setup phase completed.")
        manager.tryCompleteJob(job.id, "Success")
        lifecycleCoordinator.onLifecycleJobCompleted(job)
    }

    private suspend fun executeUpdateJob(job: BackgroundJob) {
        val plugin = PluginLoader.getPluginById(job.pluginId)
            ?: throw Exception("Plugin ${job.pluginId} not found")

        val context = pluginManager.createPluginContext(job.pluginId, job.id)

        // Register a simple handle for cancellation
        val jobExecution = currentCoroutineContext()[kotlinx.coroutines.Job]!!
        manager.registerJobHandle(job.id, object : JobHandle {
            override val result: Deferred<ExecutionResult>
                get() = throw UnsupportedOperationException("Not used for update")

            override fun pause() { /* Not supported */ }

            override fun cancel(force: Boolean) {
                jobExecution.cancel()
            }
        })

        manager.updateJobProgress(job.id, 0.2f)
        manager.addJobLog(job.id, "Running update handler for ${plugin.getManifest().plugin.name}...")

        if (!plugin.getManifest().hasUpdateHandler) {
            manager.addJobLog(job.id, "No update handler found, skipping update phase.")
        } else {
            val updateResult = plugin.performUpdate(context)
            if (updateResult.isFailure) {
                val error = updateResult.exceptionOrNull()?.message ?: "Update failed"
                manager.tryFailJob(job.id, error)
                return
            }
            manager.addJobLog(job.id, "Update successful.")
        }

        manager.updateJobProgress(job.id, 1.0f)
        manager.addJobLog(job.id, "Update phase completed.")
        manager.tryCompleteJob(job.id, "Success")
        lifecycleCoordinator.onLifecycleJobCompleted(job)
    }

    private suspend fun executeValidationJob(job: BackgroundJob) {
        val plugin = PluginLoader.getPluginById(job.pluginId)
            ?: throw Exception("Plugin ${job.pluginId} not found")

        val context = pluginManager.createPluginContext(job.pluginId, job.id)
 
         // Register a simple handle for cancellation
         val jobExecution = currentCoroutineContext()[kotlinx.coroutines.Job]!!
         manager.registerJobHandle(job.id, object : JobHandle {
            override val result: Deferred<ExecutionResult>
                get() = throw UnsupportedOperationException("Not used for validation")

            override fun pause() { /* Not supported */
            }

            override fun cancel(force: Boolean) {
                jobExecution.cancel()
            }
        })

        manager.updateJobProgress(job.id, 0.2f)
        manager.addJobLog(job.id, "Running validation for ${plugin.getManifest().plugin.name}...")

        val validationResult = plugin.validate(context)
        if (validationResult.isFailure) {
            val error = validationResult.exceptionOrNull()?.message ?: "Validation failed"
            manager.tryFailJob(job.id, error)
            return
        }

        manager.updateJobProgress(job.id, 1.0f)
        manager.addJobLog(job.id, "Validation successful.")
        manager.tryCompleteJob(job.id, "Success")
        lifecycleCoordinator.onLifecycleJobCompleted(job)
    }

    private suspend fun executePluginActionJob(job: BackgroundJob) {
        val plugin = PluginLoader.getPluginById(job.pluginId)
            ?: throw Exception("Plugin ${job.pluginId} not found")

        val processor = plugin.getProcessor()
        val context = pluginManager.createPluginContext(job.pluginId, job.id)

        // Register a simple handle for cancellation
        val jobExecution = currentCoroutineContext()[kotlinx.coroutines.Job]!!
        manager.registerJobHandle(job.id, object : JobHandle {
            override val result: Deferred<ExecutionResult>
                get() = throw UnsupportedOperationException("Not used for actions")

            override fun pause() { /* Not supported for actions currently */ }

            override fun cancel(force: Boolean) {
                jobExecution.cancel()
            }
        })

        manager.updateJobProgress(job.id, 0.1f)
        manager.addJobLog(job.id, "Executing action: ${job.capabilityName}")

        val manifest = plugin.getManifest()
        val action = manifest.actions.find { it.functionName == job.capabilityName }
            ?: throw Exception("Action ${job.capabilityName} not found in manifest")

        val result = processor.runAction(action, context)
        
        if (result.isSuccess) {
            manager.updateJobProgress(job.id, 1.0f)
            manager.addJobLog(job.id, "Action completed successfully.")
            manager.tryCompleteJob(job.id, "Success")
            lifecycleCoordinator.onLifecycleJobCompleted(job)
        } else {
            val error = result.exceptionOrNull()?.message ?: "Action failed"
            manager.tryFailJob(job.id, error)
            lifecycleCoordinator.onLifecycleJobFailed(job, error)
        }
    }

    private suspend fun executeInstallationJob(job: BackgroundJob) {
        manager.addJobLog(job.id, "Starting remote installation for ${job.pluginId}...")
        manager.updateJobProgress(job.id, 0.05f)

        val pluginJson = job.parameters["pluginJson"] ?: throw Exception("Missing pluginJson parameter")
        val targetFolderPath = job.parameters["targetFolderPath"]?.let {
            if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString().removeSurrounding("\"")
        } ?: throw Exception("Missing targetFolderPath parameter")

        val plugin = kotlinx.serialization.json.Json.decodeFromJsonElement(
            org.wip.plugintoolkit.features.repository.model.ExtensionPlugin.serializer(),
            pluginJson
        )

        // Register a simple handle for cancellation
        val jobExecution = currentCoroutineContext()[kotlinx.coroutines.Job]!!
        manager.registerJobHandle(job.id, object : JobHandle {
            override val result: Deferred<ExecutionResult>
                get() = throw UnsupportedOperationException("Not used for installation")

            override fun pause() { /* Not supported */ }

            override fun cancel(force: Boolean) {
                jobExecution.cancel()
            }
        })

        val result = pluginManager.installRemote(plugin, targetFolderPath) { progress ->
            manager.updateJobProgress(job.id, progress)
        }

        if (result.isSuccess) {
            manager.updateJobProgress(job.id, 1.0f)
            manager.addJobLog(job.id, "Installation completed successfully.")
            manager.tryCompleteJob(job.id, "Success")
            lifecycleCoordinator.onLifecycleJobCompleted(job)
        } else {
            val error = result.exceptionOrNull()?.message ?: "Installation failed"
            manager.tryFailJob(job.id, error)
            lifecycleCoordinator.onLifecycleJobFailed(job, error)
        }
    }

        private suspend fun executeFlowJob(job: BackgroundJob) {
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
        val connectionsByTarget = flow.connections.associateBy { Pair(it.targetNodeId, it.targetPortId) }
        val connectionsBySource = flow.connections.groupBy { Pair(it.sourceNodeId, it.sourcePortId) }

        val computedValues = mutableMapOf<Pair<Long, String>, Any?>()
        val flowOutputs = mutableMapOf<String, Any?>()

        // Helper to get input value
        fun getInputValue(nodeId: Long, portId: String, defaultValue: Any?): Any? {
            val conn = connectionsByTarget[Pair(nodeId, portId)]
            val raw = if (conn != null) {
                computedValues[Pair(conn.sourceNodeId, conn.sourcePortId)]
            } else {
                val node = nodesById[nodeId] ?: return defaultValue
                val port = node.inputs.find { it.id == portId }
                port?.value ?: port?.defaultValue ?: defaultValue
            }
            return if (raw is JsonElement) fromJsonElement(raw) else raw
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
                                when (je) {
                                    is kotlinx.serialization.json.JsonPrimitive -> {
                                        if (je.isString) je.content
                                        else je.booleanOrNull ?: je.intOrNull ?: je.longOrNull ?: je.doubleOrNull ?: je.content
                                    }
                                    else -> je.toString()
                                }
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
                        val handle = processor.processAsync(request, context)
                        
                        activeCapabilityHandle = handle
                        
                        val result = try {
                            handle.result.await()
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
        val connectionsByTarget = flow.connections.associateBy { Pair(it.targetNodeId, it.targetPortId) }
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
            val conn = connectionsByTarget[Pair(nodeId, portId)]
            val raw = if (conn != null) {
                computedValues[Pair(conn.sourceNodeId, conn.sourcePortId)]
            } else {
                val node = nodesById[nodeId] ?: return defaultValue
                val port = node.inputs.find { it.id == portId }
                port?.value ?: port?.defaultValue ?: defaultValue
            }
            return if (raw is JsonElement) fromJsonElement(raw) else raw
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
                            when (je) {
                                is kotlinx.serialization.json.JsonPrimitive -> {
                                    if (je.isString) je.content
                                    else je.booleanOrNull ?: je.intOrNull ?: je.longOrNull ?: je.doubleOrNull ?: je.content
                                }
                                else -> je.toString()
                            }
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
                    val handle = processor.processAsync(request, context)
                    val result = handle.result.await()
                    
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

    fun stop() {
        isActive = false
        workerJob.cancel()
    }
}
