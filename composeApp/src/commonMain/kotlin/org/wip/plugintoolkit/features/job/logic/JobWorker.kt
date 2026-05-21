package org.wip.plugintoolkit.features.job.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.JobHandle
import org.wip.plugintoolkit.api.PluginRequest
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.viewmodel.SystemNodesRegistry
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
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
            override val result: kotlinx.coroutines.Deferred<ExecutionResult>
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
            override val result: kotlinx.coroutines.Deferred<ExecutionResult>
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
            override val result: kotlinx.coroutines.Deferred<ExecutionResult>
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
            override val result: kotlinx.coroutines.Deferred<ExecutionResult>
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
            override val result: kotlinx.coroutines.Deferred<ExecutionResult>
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

        val runtimeInferred = runRuntimeTypeInference(flow)

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
        val computedValues = mutableMapOf<Pair<Long, String>, Any?>()
        val flowOutputs = mutableMapOf<String, Any?>()
        val activeNodes = mutableSetOf<Long>()
        val executedNodeIds = mutableSetOf<Long>()
        val capabilityResumeStates = mutableMapOf<Long, JsonElement>()

        // Register flow-level job handle
        val jobExecution = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]!!
        var pauseRequested = false
        var activeCapabilityHandle: org.wip.plugintoolkit.api.JobHandle? = null

        val flowHandle = object : org.wip.plugintoolkit.api.JobHandle {
            override val result: kotlinx.coroutines.Deferred<org.wip.plugintoolkit.api.ExecutionResult>
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
                flow.nodes.forEach { n ->
                    val hasIncoming = flow.connections.any { it.targetNodeId == n.id }
                    if (!hasIncoming) {
                        activeNodes.add(n.id)
                    }
                }
            }

            executionOrder.forEachIndexed { index, nodeId ->
                val node = flow.nodes.first { it.id == nodeId }
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

                // Resolve inputs
                fun getInputValue(portId: String, defaultValue: Any?): Any? {
                    val conn = flow.connections.find { it.targetNodeId == node.id && it.targetPortId == portId }
                    return if (conn != null) {
                        computedValues[Pair(conn.sourceNodeId, conn.sourcePortId)]
                    } else {
                        val port = node.inputs.find { it.id == portId }
                        port?.value ?: port?.defaultValue ?: defaultValue
                    }
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
                            manager.addJobLog(job.id, "Flow input node [${node.title}] populated with: $rawValue")
                        }
                    }
                    is org.wip.plugintoolkit.features.flows.model.Node.SystemNode -> {
                        when (node.systemAction.lowercase()) {
                            "save" -> {
                                val data = getInputValue("data", "")
                                val filePath = getInputValue("file_path", "output.txt") as String
                                val dataString = when (data) {
                                    is kotlinx.serialization.json.JsonElement -> data.toString()
                                    else -> data?.toString() ?: ""
                                }
                                
                                val fullPath = Path("$appDataDir/$filePath")
                                val parent = fullPath.parent
                                if (parent != null && !SystemFileSystem.exists(parent)) {
                                    SystemFileSystem.createDirectories(parent)
                                }
                                SystemFileSystem.sink(fullPath).buffered().use { it.writeString(dataString) }
                                
                                computedValues[Pair(node.id, "success")] = true
                                manager.addJobLog(job.id, "Saved data to file: $filePath")
                            }
                            "load" -> {
                                val filePath = getInputValue("file_path", "output.txt") as String
                                val fullPath = Path("$appDataDir/$filePath")
                                
                                val fileContent = if (SystemFileSystem.exists(fullPath)) {
                                    SystemFileSystem.source(fullPath).buffered().use { it.readString() }
                                } else {
                                    manager.addJobLog(job.id, "Warning: file to load not found, returning empty: $filePath", "WARN")
                                    ""
                                }
                                
                                computedValues[Pair(node.id, "data")] = fileContent
                                manager.addJobLog(job.id, "Loaded content from file: $filePath (Size: ${fileContent.length} chars)")
                            }
                            "log" -> {
                                val level = getInputValue("level", "INFO") as String
                                val message = getInputValue("message", "") as String
                                val data = getInputValue("data", null)
                                
                                val logMessage = buildString {
                                    append(message)
                                    if (data != null) {
                                        append(" | Data: ")
                                        append(data)
                                    }
                                }
                                manager.addJobLog(job.id, logMessage, level.uppercase())
                                computedValues[Pair(node.id, "output")] = message
                            }
                            "delay" -> {
                                val duration = when (val dur = getInputValue("duration", 1000)) {
                                    is Number -> dur.toLong()
                                    is String -> dur.toLongOrNull() ?: 1000L
                                    else -> 1000L
                                }
                                val inputData = getInputValue("input_data", null)
                                
                                manager.addJobLog(job.id, "Sleeping for $duration ms...")
                                kotlinx.coroutines.delay(duration)
                                
                                computedValues[Pair(node.id, "output_data")] = inputData
                            }
                            "convert" -> {
                                val inputData = getInputValue("input_data", null)
                                val targetType = runtimeInferred[Pair(node.id, "output_data")] ?: DataType.Primitive(PrimitiveType.ANY)
                                try {
                                    val converted = convertValue(inputData, targetType)
                                    computedValues[Pair(node.id, "output_data")] = converted
                                    computedValues[Pair(node.id, "success")] = true
                                } catch (e: Exception) {
                                    manager.addJobLog(job.id, "Conversion warning: ${e.message}", "WARN")
                                    computedValues[Pair(node.id, "output_data")] = null
                                    computedValues[Pair(node.id, "success")] = false
                                }
                            }
                            "conditional" -> {
                                val conditionVal = getInputValue("condition", false)
                                val condition = when (conditionVal) {
                                    is Boolean -> conditionVal
                                    is String -> conditionVal.toBoolean()
                                    is Number -> conditionVal.toInt() != 0
                                    else -> false
                                }
                                val inputData = getInputValue("input_data", null)
                                if (condition) {
                                    computedValues[Pair(node.id, "if_true")] = inputData
                                    computedValues[Pair(node.id, "if_false")] = null
                                } else {
                                    computedValues[Pair(node.id, "if_true")] = null
                                    computedValues[Pair(node.id, "if_false")] = inputData
                                }
                            }
                            "error" -> {
                                val message = getInputValue("message", "An error occurred during flow execution") as String
                                val data = getInputValue("data", null)
                                val errorMessage = if (data != null) {
                                    val dataStr = when (data) {
                                        is kotlinx.serialization.json.JsonPrimitive -> {
                                            if (data.isString) data.content else data.toString()
                                        }
                                        else -> data.toString()
                                    }
                                    if (message.endsWith(": ") || message.endsWith(":") || message.endsWith(" ")) {
                                        "$message$dataStr"
                                    } else {
                                        "$message: $dataStr"
                                    }
                                } else {
                                    message
                                }
                                throw Exception(errorMessage)
                            }
                            "merger" -> {
                                val list1 = getInputValue("list1", null)
                                val list2 = getInputValue("list2", null)
                                
                                val merged = mutableListOf<Any?>()
                                
                                fun addToList(item: Any?) {
                                    when (item) {
                                        is List<*> -> merged.addAll(item)
                                        is Array<*> -> merged.addAll(item)
                                        is kotlinx.serialization.json.JsonArray -> {
                                            item.forEach { je ->
                                                val unwrapped = when (je) {
                                                    is kotlinx.serialization.json.JsonPrimitive -> {
                                                        if (je.isString) je.content
                                                        else je.booleanOrNull ?: je.intOrNull ?: je.longOrNull ?: je.doubleOrNull ?: je.content
                                                    }
                                                    else -> je
                                                }
                                                merged.add(unwrapped)
                                            }
                                        }
                                        null -> {}
                                        else -> merged.add(item)
                                    }
                                }
                                
                                addToList(list1)
                                addToList(list2)
                                
                                computedValues[Pair(node.id, "output")] = merged
                                manager.addJobLog(job.id, "Merged lists. Item count: ${merged.size}")
                            }
                            "comparator" -> {
                                val a = getInputValue("a", null)
                                val b = getInputValue("b", null)

                                var minorVal = false
                                var majorVal = false
                                var equalVal = false

                                if (a != null && b != null) {
                                    val aNum = when (a) {
                                        is Number -> a.toDouble()
                                        else -> a.toString().toDoubleOrNull()
                                    }
                                    val bNum = when (b) {
                                        is Number -> b.toDouble()
                                        else -> b.toString().toDoubleOrNull()
                                    }

                                    if (aNum != null && bNum != null) {
                                        minorVal = aNum < bNum
                                        majorVal = aNum > bNum
                                        equalVal = aNum == bNum
                                    } else {
                                        val aStr = a.toString()
                                        val bStr = b.toString()
                                        val cmp = aStr.compareTo(bStr)
                                        minorVal = cmp < 0
                                        majorVal = cmp > 0
                                        equalVal = cmp == 0
                                    }
                                } else {
                                    equalVal = (a == null && b == null)
                                    minorVal = (a == null && b != null)
                                    majorVal = (a != null && b == null)
                                }

                                val notEqualVal = !equalVal
                                computedValues[Pair(node.id, "minor")] = minorVal
                                computedValues[Pair(node.id, "major")] = majorVal
                                computedValues[Pair(node.id, "equal")] = equalVal
                                computedValues[Pair(node.id, "not_equal")] = notEqualVal
                                manager.addJobLog(job.id, "Comparator result: minor=$minorVal, major=$majorVal, equal=$equalVal, not_equal=$notEqualVal")
                            }
                            "for" -> {
                                val subflowName = getInputValue("subflow_name", "") as String
                                if (subflowName.isNotEmpty()) {
                                    val subFlowFile = Path("$appDataDir/flows/${subflowName.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.json")
                                    if (!SystemFileSystem.exists(subFlowFile)) {
                                        throw Exception("Subflow file not found: $subflowName")
                                    }
                                    val subFlowContent = SystemFileSystem.source(subFlowFile).buffered().use { it.readString() }
                                    val subFlow = Json { ignoreUnknownKeys = true; encodeDefaults = true }.decodeFromString<org.wip.plugintoolkit.features.flows.model.Flow>(subFlowContent)

                                    val start = (getInputValue("start", 0) as? Number)?.toInt() ?: 0
                                    val end = (getInputValue("end", 10) as? Number)?.toInt() ?: 10
                                    val step = (getInputValue("step", 1) as? Number)?.toInt() ?: 1
                                    var accumulator = getInputValue("input_data", null)

                                    if (step != 0) {
                                        val range = if (step > 0) start until end step step else start downTo end + 1 step (-step)
                                        for (i in range) {
                                            val subParameters = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
                                            subFlow.nodes.filterIsInstance<org.wip.plugintoolkit.features.flows.model.Node.FlowInputNode>().forEach { inputNode ->
                                                val portName = inputNode.outputs.firstOrNull()?.name?.lowercase() ?: ""
                                                val portId = inputNode.outputs.firstOrNull()?.id ?: ""
                                                when {
                                                    portName == "index" || portName == "idx" || portId == "index" || portId == "idx" -> {
                                                        subParameters["${inputNode.id}"] = toJsonElement(i)
                                                    }
                                                    portName == "input_data" || portName == "input" || portName == "data" || portId == "input_data" || portId == "input" || portId == "data" -> {
                                                        subParameters["${inputNode.id}"] = toJsonElement(accumulator)
                                                    }
                                                }
                                            }

                                            val subJob = BackgroundJob(
                                                id = "${job.id}-for-${node.id}-$i",
                                                name = "For loop index $i",
                                                type = JobType.Flow,
                                                pluginId = "system",
                                                capabilityName = subflowName,
                                                parameters = subParameters
                                            )

                                            manager.addJobLog(job.id, "Executing for loop iteration index = $i")
                                            val subOutputs = executeSubFlowRecursively(subFlow, subJob, appDataDir)
                                            val outVal = subOutputs["output_data"] ?: subOutputs["output"] ?: subOutputs["data"] ?: subOutputs.values.firstOrNull()
                                            accumulator = outVal
                                        }
                                    }
                                    computedValues[Pair(node.id, "output_data")] = accumulator
                                } else {
                                    computedValues[Pair(node.id, "output_data")] = getInputValue("input_data", null)
                                }
                            }
                            "while" -> {
                                val subflowName = getInputValue("subflow_name", "") as String
                                if (subflowName.isNotEmpty()) {
                                    val subFlowFile = Path("$appDataDir/flows/${subflowName.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.json")
                                    if (!SystemFileSystem.exists(subFlowFile)) {
                                        throw Exception("Subflow file not found: $subflowName")
                                    }
                                    val subFlowContent = SystemFileSystem.source(subFlowFile).buffered().use { it.readString() }
                                    val subFlow = Json { ignoreUnknownKeys = true; encodeDefaults = true }.decodeFromString<org.wip.plugintoolkit.features.flows.model.Flow>(subFlowContent)

                                    var conditionVal = getInputValue("condition", true)
                                    var condition = when (conditionVal) {
                                        is Boolean -> conditionVal
                                        is String -> conditionVal.toBoolean()
                                        is Number -> conditionVal.toInt() != 0
                                        else -> false
                                    }
                                    var accumulator = getInputValue("input_data", null)
                                    var iteration = 0

                                    while (condition) {
                                        val subParameters = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
                                        subFlow.nodes.filterIsInstance<org.wip.plugintoolkit.features.flows.model.Node.FlowInputNode>().forEach { inputNode ->
                                            val portName = inputNode.outputs.firstOrNull()?.name?.lowercase() ?: ""
                                            val portId = inputNode.outputs.firstOrNull()?.id ?: ""
                                            when {
                                                portName == "condition" || portName == "cond" || portId == "condition" || portId == "cond" -> {
                                                    subParameters["${inputNode.id}"] = toJsonElement(condition)
                                                }
                                                portName == "input_data" || portName == "input" || portName == "data" || portId == "input_data" || portId == "input" || portId == "data" -> {
                                                    subParameters["${inputNode.id}"] = toJsonElement(accumulator)
                                                }
                                            }
                                        }

                                        val subJob = BackgroundJob(
                                            id = "${job.id}-while-${node.id}-$iteration",
                                            name = "While loop iteration $iteration",
                                            type = JobType.Flow,
                                            pluginId = "system",
                                            capabilityName = subflowName,
                                            parameters = subParameters
                                        )

                                        manager.addJobLog(job.id, "Executing while loop iteration $iteration")
                                        val subOutputs = executeSubFlowRecursively(subFlow, subJob, appDataDir)
                                        
                                        val newAccumulator = subOutputs["output_data"] ?: subOutputs["output"] ?: subOutputs["data"]
                                        val newConditionVal = subOutputs["condition"] ?: subOutputs["cond"]
                                        
                                        if (newAccumulator != null || subOutputs.containsKey("output_data") || subOutputs.containsKey("output") || subOutputs.containsKey("data")) {
                                            accumulator = newAccumulator
                                        } else {
                                            val nonConditionOutput = subOutputs.filterKeys { it != "condition" && it != "cond" }.values.firstOrNull()
                                            if (nonConditionOutput != null) {
                                                accumulator = nonConditionOutput
                                            }
                                        }

                                        if (newConditionVal != null) {
                                            condition = when (newConditionVal) {
                                                is Boolean -> newConditionVal
                                                is String -> newConditionVal.toBoolean()
                                                is Number -> newConditionVal.toInt() != 0
                                                else -> false
                                            }
                                        } else {
                                            val boolOutput = subOutputs.values.filterIsInstance<Boolean>().firstOrNull()
                                            if (boolOutput != null) {
                                                condition = boolOutput
                                            } else {
                                                manager.addJobLog(job.id, "Warning: while loop did not return a condition output, exiting loop", "WARN")
                                                break
                                            }
                                        }
                                        
                                        iteration++
                                        if (iteration > 10000) {
                                            throw Exception("While loop exceeded safety limit of 10000 iterations")
                                        }
                                    }

                                    computedValues[Pair(node.id, "output_data")] = accumulator
                                } else {
                                    computedValues[Pair(node.id, "output_data")] = getInputValue("input_data", null)
                                }
                            }
                            else -> {
                                throw Exception("Unsupported system node action: ${node.systemAction}")
                            }
                        }
                    }
                    is org.wip.plugintoolkit.features.flows.model.Node.CapabilityNode -> {
                        // Gather inputs as a Map<String, JsonElement>
                        val capabilityParameters = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
                        node.inputs.forEach { port ->
                            val resolvedVal = getInputValue(port.id, null)
                            capabilityParameters[port.id] = toJsonElement(resolvedVal)
                        }
                        
                        val plugin = PluginLoader.getPluginById(node.pluginInfo.id)
                            ?: throw Exception("Plugin ${node.pluginInfo.id} not found")
                        
                        validateCapabilityParameters(plugin.getManifest(), node.capability.name, capabilityParameters)
                        
                        val processor = plugin.getProcessor()
                        val context = pluginManager.createPluginContext(node.pluginInfo.id, job.id)
                        
                        val savedCapResumeState = capabilityResumeStates[node.id]
                        val request = org.wip.plugintoolkit.api.PluginRequest(
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
                                org.wip.plugintoolkit.api.ExecutionResult.Paused(JsonNull)
                            } else {
                                throw e
                            }
                        } catch (e: Exception) {
                            throw Exception("Capability ${node.capability.name} failed during await: ${e.message}", e)
                        } finally {
                            activeCapabilityHandle = null
                        }
                        
                        when (result) {
                            is org.wip.plugintoolkit.api.ExecutionResult.Success -> {
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
                            is org.wip.plugintoolkit.api.ExecutionResult.Error -> {
                                capabilityResumeStates.remove(node.id)
                                throw Exception("Capability invocation failed: ${result.message}")
                            }
                            is org.wip.plugintoolkit.api.ExecutionResult.Paused -> {
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
                            val finalVal = getInputValue(inputPort.id, null)
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
                        val subParameters = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
                        node.inputs.forEach { port ->
                            val mapping = node.inputMappings.find { it.portId == port.id }
                            if (mapping != null) {
                                val resolvedVal = getInputValue(port.id, null)
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
                        node.outputs.forEach { port ->
                            val mapping = node.outputMappings.find { it.portId == port.id }
                            if (mapping != null) {
                                val boundaryOutputNode = subFlow.nodes.find { it.id == mapping.boundaryNodeId } as? org.wip.plugintoolkit.features.flows.model.Node.FlowOutputNode
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
                            val conditionVal = getInputValue("condition", false)
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
                    flow.connections.forEach { conn ->
                        if (conn.sourceNodeId == node.id && conn.sourcePortId == portId) {
                            activeNodes.add(conn.targetNodeId)
                        }
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
        val runtimeInferred = runRuntimeTypeInference(flow)
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

        val activeNodes = mutableSetOf<Long>()
        flow.nodes.forEach { n ->
            val hasIncoming = flow.connections.any { it.targetNodeId == n.id }
            if (!hasIncoming) {
                activeNodes.add(n.id)
            }
        }

        executionOrder.forEach { nodeId ->
            val node = flow.nodes.first { it.id == nodeId }
            if (!activeNodes.contains(node.id)) {
                return@forEach
            }
            
            fun getInputValue(portId: String, defaultValue: Any?): Any? {
                val conn = flow.connections.find { it.targetNodeId == node.id && it.targetPortId == portId }
                return if (conn != null) {
                    computedValues[Pair(conn.sourceNodeId, conn.sourcePortId)]
                } else {
                    val port = node.inputs.find { it.id == portId }
                    port?.value ?: port?.defaultValue ?: defaultValue
                }
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
                    when (node.systemAction.lowercase()) {
                        "save" -> {
                            val data = getInputValue("data", "")
                            val filePath = getInputValue("file_path", "output.txt") as String
                            val dataString = when (data) {
                                is kotlinx.serialization.json.JsonElement -> data.toString()
                                else -> data?.toString() ?: ""
                            }
                            
                            val fullPath = Path("$appDataDir/$filePath")
                            val parent = fullPath.parent
                            if (parent != null && !SystemFileSystem.exists(parent)) {
                                SystemFileSystem.createDirectories(parent)
                            }
                            SystemFileSystem.sink(fullPath).buffered().use { it.writeString(dataString) }
                            computedValues[Pair(node.id, "success")] = true
                        }
                        "load" -> {
                            val filePath = getInputValue("file_path", "output.txt") as String
                            val fullPath = Path("$appDataDir/$filePath")
                            val fileContent = if (SystemFileSystem.exists(fullPath)) {
                                SystemFileSystem.source(fullPath).buffered().use { it.readString() }
                            } else {
                                ""
                            }
                            computedValues[Pair(node.id, "data")] = fileContent
                        }
                        "log" -> {
                            val message = getInputValue("message", "") as String
                            computedValues[Pair(node.id, "output")] = message
                        }
                        "delay" -> {
                            val duration = when (val dur = getInputValue("duration", 1000)) {
                                is Number -> dur.toLong()
                                is String -> dur.toLongOrNull() ?: 1000L
                                else -> 1000L
                            }
                            val inputData = getInputValue("input_data", null)
                            kotlinx.coroutines.delay(duration)
                            computedValues[Pair(node.id, "output_data")] = inputData
                        }
                        "convert" -> {
                            val inputData = getInputValue("input_data", null)
                            val targetType = runtimeInferred[Pair(node.id, "output_data")] ?: DataType.Primitive(PrimitiveType.ANY)
                            try {
                                val converted = convertValue(inputData, targetType)
                                computedValues[Pair(node.id, "output_data")] = converted
                                computedValues[Pair(node.id, "success")] = true
                            } catch (e: Exception) {
                                computedValues[Pair(node.id, "output_data")] = null
                                computedValues[Pair(node.id, "success")] = false
                            }
                        }
                        "conditional" -> {
                            val conditionVal = getInputValue("condition", false)
                            val condition = when (conditionVal) {
                                is Boolean -> conditionVal
                                is String -> conditionVal.toBoolean()
                                is Number -> conditionVal.toInt() != 0
                                else -> false
                            }
                            val inputData = getInputValue("input_data", null)
                            if (condition) {
                                computedValues[Pair(node.id, "if_true")] = inputData
                                computedValues[Pair(node.id, "if_false")] = null
                            } else {
                                computedValues[Pair(node.id, "if_true")] = null
                                computedValues[Pair(node.id, "if_false")] = inputData
                            }
                        }
                        "error" -> {
                            val message = getInputValue("message", "An error occurred during flow execution") as String
                            val data = getInputValue("data", null)
                            val errorMessage = if (data != null) {
                                val dataStr = when (data) {
                                    is kotlinx.serialization.json.JsonPrimitive -> {
                                        if (data.isString) data.content else data.toString()
                                    }
                                    else -> data.toString()
                                }
                                if (message.endsWith(": ") || message.endsWith(":") || message.endsWith(" ")) {
                                    "$message$dataStr"
                                } else {
                                    "$message: $dataStr"
                                }
                            } else {
                                message
                            }
                            throw Exception(errorMessage)
                        }
                        "merger" -> {
                            val list1 = getInputValue("list1", null)
                            val list2 = getInputValue("list2", null)
                            val merged = mutableListOf<Any?>()
                            fun addToList(item: Any?) {
                                when (item) {
                                    is List<*> -> merged.addAll(item)
                                    is Array<*> -> merged.addAll(item)
                                    is kotlinx.serialization.json.JsonArray -> {
                                        item.forEach { je ->
                                            val unwrapped = when (je) {
                                                is kotlinx.serialization.json.JsonPrimitive -> {
                                                    if (je.isString) je.content
                                                    else je.booleanOrNull ?: je.intOrNull ?: je.longOrNull ?: je.doubleOrNull ?: je.content
                                                }
                                                else -> je
                                            }
                                            merged.add(unwrapped)
                                        }
                                    }
                                    null -> {}
                                    else -> merged.add(item)
                                }
                            }
                            addToList(list1)
                            addToList(list2)
                            computedValues[Pair(node.id, "output")] = merged
                        }
                        "comparator" -> {
                            val a = getInputValue("a", null)
                            val b = getInputValue("b", null)

                            var minorVal = false
                            var majorVal = false
                            var equalVal = false

                            if (a != null && b != null) {
                                val aNum = when (a) {
                                    is Number -> a.toDouble()
                                    else -> a.toString().toDoubleOrNull()
                                }
                                val bNum = when (b) {
                                    is Number -> b.toDouble()
                                    else -> b.toString().toDoubleOrNull()
                                }

                                if (aNum != null && bNum != null) {
                                    minorVal = aNum < bNum
                                    majorVal = aNum > bNum
                                    equalVal = aNum == bNum
                                } else {
                                    val aStr = a.toString()
                                    val bStr = b.toString()
                                    val cmp = aStr.compareTo(bStr)
                                    minorVal = cmp < 0
                                    majorVal = cmp > 0
                                    equalVal = cmp == 0
                                }
                            } else {
                                equalVal = (a == null && b == null)
                                minorVal = (a == null && b != null)
                                majorVal = (a != null && b == null)
                            }

                            val notEqualVal = !equalVal
                            computedValues[Pair(node.id, "minor")] = minorVal
                            computedValues[Pair(node.id, "major")] = majorVal
                            computedValues[Pair(node.id, "equal")] = equalVal
                            computedValues[Pair(node.id, "not_equal")] = notEqualVal
                        }
                        "for" -> {
                            val subflowName = getInputValue("subflow_name", "") as String
                            if (subflowName.isNotEmpty()) {
                                val subFlowFile = Path("$appDataDir/flows/${subflowName.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.json")
                                if (!SystemFileSystem.exists(subFlowFile)) {
                                    throw Exception("Subflow file not found: $subflowName")
                                }
                                val subFlowContent = SystemFileSystem.source(subFlowFile).buffered().use { it.readString() }
                                val subFlow = Json { ignoreUnknownKeys = true; encodeDefaults = true }.decodeFromString<org.wip.plugintoolkit.features.flows.model.Flow>(subFlowContent)

                                val start = (getInputValue("start", 0) as? Number)?.toInt() ?: 0
                                val end = (getInputValue("end", 10) as? Number)?.toInt() ?: 10
                                val step = (getInputValue("step", 1) as? Number)?.toInt() ?: 1
                                var accumulator = getInputValue("input_data", null)

                                if (step != 0) {
                                    val range = if (step > 0) start until end step step else start downTo end + 1 step (-step)
                                    for (i in range) {
                                        val subParameters = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
                                        subFlow.nodes.filterIsInstance<org.wip.plugintoolkit.features.flows.model.Node.FlowInputNode>().forEach { inputNode ->
                                            val portName = inputNode.outputs.firstOrNull()?.name?.lowercase() ?: ""
                                            val portId = inputNode.outputs.firstOrNull()?.id ?: ""
                                            when {
                                                portName == "index" || portName == "idx" || portId == "index" || portId == "idx" -> {
                                                    subParameters["${inputNode.id}"] = toJsonElement(i)
                                                }
                                                portName == "input_data" || portName == "input" || portName == "data" || portId == "input_data" || portId == "input" || portId == "data" -> {
                                                    subParameters["${inputNode.id}"] = toJsonElement(accumulator)
                                                }
                                            }
                                        }

                                        val subJob = BackgroundJob(
                                            id = "${job.id}-for-${node.id}-$i",
                                            name = "For loop index $i",
                                            type = JobType.Flow,
                                            pluginId = "system",
                                            capabilityName = subflowName,
                                            parameters = subParameters
                                        )

                                        val subOutputs = executeSubFlowRecursively(subFlow, subJob, appDataDir)
                                        val outVal = subOutputs["output_data"] ?: subOutputs["output"] ?: subOutputs["data"] ?: subOutputs.values.firstOrNull()
                                        accumulator = outVal
                                    }
                                }
                                computedValues[Pair(node.id, "output_data")] = accumulator
                            } else {
                                computedValues[Pair(node.id, "output_data")] = getInputValue("input_data", null)
                            }
                        }
                        "while" -> {
                            val subflowName = getInputValue("subflow_name", "") as String
                            if (subflowName.isNotEmpty()) {
                                val subFlowFile = Path("$appDataDir/flows/${subflowName.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.json")
                                if (!SystemFileSystem.exists(subFlowFile)) {
                                    throw Exception("Subflow file not found: $subflowName")
                                }
                                val subFlowContent = SystemFileSystem.source(subFlowFile).buffered().use { it.readString() }
                                val subFlow = Json { ignoreUnknownKeys = true; encodeDefaults = true }.decodeFromString<org.wip.plugintoolkit.features.flows.model.Flow>(subFlowContent)

                                var conditionVal = getInputValue("condition", true)
                                var condition = when (conditionVal) {
                                    is Boolean -> conditionVal
                                    is String -> conditionVal.toBoolean()
                                    is Number -> conditionVal.toInt() != 0
                                    else -> false
                                }
                                var accumulator = getInputValue("input_data", null)
                                var iteration = 0

                                while (condition) {
                                    val subParameters = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
                                    subFlow.nodes.filterIsInstance<org.wip.plugintoolkit.features.flows.model.Node.FlowInputNode>().forEach { inputNode ->
                                        val portName = inputNode.outputs.firstOrNull()?.name?.lowercase() ?: ""
                                        val portId = inputNode.outputs.firstOrNull()?.id ?: ""
                                        when {
                                            portName == "condition" || portName == "cond" || portId == "condition" || portId == "cond" -> {
                                                subParameters["${inputNode.id}"] = toJsonElement(condition)
                                            }
                                            portName == "input_data" || portName == "input" || portName == "data" || portId == "input_data" || portId == "input" || portId == "data" -> {
                                                subParameters["${inputNode.id}"] = toJsonElement(accumulator)
                                            }
                                        }
                                    }

                                    val subJob = BackgroundJob(
                                        id = "${job.id}-while-${node.id}-$iteration",
                                        name = "While loop iteration $iteration",
                                        type = JobType.Flow,
                                        pluginId = "system",
                                        capabilityName = subflowName,
                                        parameters = subParameters
                                    )

                                    val subOutputs = executeSubFlowRecursively(subFlow, subJob, appDataDir)
                                    
                                    val newAccumulator = subOutputs["output_data"] ?: subOutputs["output"] ?: subOutputs["data"]
                                    val newConditionVal = subOutputs["condition"] ?: subOutputs["cond"]
                                    
                                    if (newAccumulator != null || subOutputs.containsKey("output_data") || subOutputs.containsKey("output") || subOutputs.containsKey("data")) {
                                        accumulator = newAccumulator
                                    } else {
                                        val nonConditionOutput = subOutputs.filterKeys { it != "condition" && it != "cond" }.values.firstOrNull()
                                        if (nonConditionOutput != null) {
                                            accumulator = nonConditionOutput
                                        }
                                    }

                                    if (newConditionVal != null) {
                                        condition = when (newConditionVal) {
                                            is Boolean -> newConditionVal
                                            is String -> newConditionVal.toBoolean()
                                            is Number -> newConditionVal.toInt() != 0
                                            else -> false
                                        }
                                    } else {
                                        val boolOutput = subOutputs.values.filterIsInstance<Boolean>().firstOrNull()
                                        if (boolOutput != null) {
                                            condition = boolOutput
                                        } else {
                                            break
                                        }
                                    }
                                    
                                    iteration++
                                    if (iteration > 10000) {
                                        throw Exception("While loop exceeded safety limit of 10000 iterations")
                                    }
                                }

                                computedValues[Pair(node.id, "output_data")] = accumulator
                            } else {
                                computedValues[Pair(node.id, "output_data")] = getInputValue("input_data", null)
                            }
                        }
                    }
                }
                is org.wip.plugintoolkit.features.flows.model.Node.CapabilityNode -> {
                    val capabilityParameters = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
                    node.inputs.forEach { port ->
                        val resolvedVal = getInputValue(port.id, null)
                        capabilityParameters[port.id] = toJsonElement(resolvedVal)
                    }
                    
                    val plugin = PluginLoader.getPluginById(node.pluginInfo.id)
                        ?: throw Exception("Plugin ${node.pluginInfo.id} not found")
                    
                    validateCapabilityParameters(plugin.getManifest(), node.capability.name, capabilityParameters)
                    
                    val processor = plugin.getProcessor()
                    val context = pluginManager.createPluginContext(node.pluginInfo.id, job.id)
                    val request = org.wip.plugintoolkit.api.PluginRequest(
                        method = node.capability.name,
                        parameters = capabilityParameters
                    )
                    val handle = processor.processAsync(request, context)
                    val result = handle.result.await()
                    
                    when (result) {
                        is org.wip.plugintoolkit.api.ExecutionResult.Success -> {
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
                        val finalVal = getInputValue(inputPort.id, null)
                        flowOutputs[inputPort.name] = finalVal
                        computedValues[Pair(node.id, inputPort.id)] = finalVal
                    }
                }
                is org.wip.plugintoolkit.features.flows.model.Node.SubFlowNode -> {
                    val subFlowFile = Path("$appDataDir/flows/${node.flowName.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.json")
                    if (SystemFileSystem.exists(subFlowFile)) {
                        val subFlowContent = SystemFileSystem.source(subFlowFile).buffered().use { it.readString() }
                        val subFlow = Json { ignoreUnknownKeys = true; encodeDefaults = true }.decodeFromString<org.wip.plugintoolkit.features.flows.model.Flow>(subFlowContent)
                        
                        val subParameters = mutableMapOf<String, kotlinx.serialization.json.JsonElement>()
                        node.inputs.forEach { port ->
                            val mapping = node.inputMappings.find { it.portId == port.id }
                            if (mapping != null) {
                                val resolvedVal = getInputValue(port.id, null)
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
                        node.outputs.forEach { port ->
                            val mapping = node.outputMappings.find { it.portId == port.id }
                            if (mapping != null) {
                                val boundaryOutputNode = subFlow.nodes.find { it.id == mapping.boundaryNodeId } as? org.wip.plugintoolkit.features.flows.model.Node.FlowOutputNode
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
                        val conditionVal = getInputValue("condition", false)
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
                flow.connections.forEach { conn ->
                    if (conn.sourceNodeId == node.id && conn.sourcePortId == portId) {
                        activeNodes.add(conn.targetNodeId)
                    }
                }
            }
        }
        return flowOutputs
    }

    private fun toJsonElement(value: Any?): kotlinx.serialization.json.JsonElement {
        return when (value) {
            null -> kotlinx.serialization.json.JsonNull
            is kotlinx.serialization.json.JsonElement -> value
            is Boolean -> kotlinx.serialization.json.JsonPrimitive(value)
            is Number -> kotlinx.serialization.json.JsonPrimitive(value)
            is String -> kotlinx.serialization.json.JsonPrimitive(value)
            is Map<*, *> -> kotlinx.serialization.json.JsonObject(value.entries.associate { it.key.toString() to toJsonElement(it.value) })
            is List<*> -> kotlinx.serialization.json.JsonArray(value.map { toJsonElement(it) })
            is Array<*> -> kotlinx.serialization.json.JsonArray(value.map { toJsonElement(it) })
            else -> kotlinx.serialization.json.JsonPrimitive(value.toString())
        }
    }

    private fun runRuntimeTypeInference(flow: org.wip.plugintoolkit.features.flows.model.Flow): Map<Pair<Long, String>, DataType> {
        val inferred = mutableMapOf<Pair<Long, String>, DataType>()
        flow.nodes.forEach { node ->
            node.inputs.forEach { port -> inferred[Pair(node.id, port.id)] = port.dataType }
            node.outputs.forEach { port -> inferred[Pair(node.id, port.id)] = port.dataType }
        }
        var changed = true
        var iteration = 0
        while (changed && iteration < 10) {
            changed = false
            iteration++
            flow.connections.forEach { conn ->
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
            flow.nodes.forEach { node ->
                if (node is org.wip.plugintoolkit.features.flows.model.Node.SystemNode) {
                    if (SystemNodesRegistry.propagateTypes(node, inferred)) {
                        changed = true
                    }
                }
            }
        }
        return inferred
    }

    private fun convertValue(value: Any?, targetType: DataType): Any? {
        if (value == null) return null
        if (targetType !is DataType.Primitive) return value

        val targetPrimitive = targetType.primitiveType
        return when (targetPrimitive) {
            PrimitiveType.STRING -> value.toString()
            PrimitiveType.INT -> {
                if (value is Number) value.toInt()
                else {
                    val str = value.toString().trim()
                    str.toIntOrNull() ?: throw Exception("Failed to convert '$value' to Int")
                }
            }
            PrimitiveType.DOUBLE -> {
                if (value is Number) value.toDouble()
                else {
                    val str = value.toString().trim()
                    str.toDoubleOrNull() ?: throw Exception("Failed to convert '$value' to Double")
                }
            }
            PrimitiveType.BOOLEAN -> {
                if (value is Boolean) value
                else if (value is Number) {
                    value.toInt() != 0
                } else {
                    val str = value.toString().trim().lowercase()
                    if (str == "true" || str == "1") true
                    else if (str == "false" || str == "0") false
                    else {
                        val intVal = str.toIntOrNull()
                        if (intVal != null) {
                            intVal != 0
                        } else {
                            val doubleVal = str.toDoubleOrNull()
                            if (doubleVal != null) {
                                doubleVal.toInt() != 0
                            } else {
                                throw Exception("Failed to convert '$value' to Boolean")
                            }
                        }
                    }
                }
            }
            PrimitiveType.ANY -> value
            PrimitiveType.UNIT -> Unit
        }
    }

    private fun fromJsonElement(je: JsonElement): Any? {
        return when (je) {
            is JsonNull -> null
            is JsonPrimitive -> {
                if (je.isString) {
                    je.content
                } else {
                    je.booleanOrNull ?: je.intOrNull ?: je.longOrNull ?: je.doubleOrNull ?: je.content
                }
            }
            is JsonArray -> {
                je.map { fromJsonElement(it) }
            }
            is JsonObject -> {
                je.entries.associate { it.key to fromJsonElement(it.value) }
            }
        }
    }

    private fun validateCapabilityParameters(
        manifest: org.wip.plugintoolkit.api.PluginManifest,
        capabilityName: String,
        parameters: Map<String, kotlinx.serialization.json.JsonElement>
    ) {
        val capability = manifest.capabilities.find { it.name == capabilityName } ?: return
        val paramMetadataMap = capability.parameters ?: return

        for ((paramName, metadata) in paramMetadataMap) {
            val valueElement = parameters[paramName]
            
            // Check if required
            if (metadata.required) {
                if (valueElement == null || valueElement is kotlinx.serialization.json.JsonNull) {
                    throw IllegalArgumentException("Parameter '$paramName' is required but was not provided.")
                }
                val metaType = metadata.type
                if (metaType is DataType.Primitive && metaType.primitiveType == PrimitiveType.STRING) {
                    if (valueElement is JsonPrimitive && valueElement.content.isBlank()) {
                        throw IllegalArgumentException("Parameter '$paramName' is required but was empty.")
                    }
                }
            }

            if (valueElement == null || valueElement is kotlinx.serialization.json.JsonNull) {
                continue
            }

            val constraints = metadata.constraints ?: continue
            val regex = constraints.regex
            val minLength = constraints.minLength
            val maxLength = constraints.maxLength
            val minValue = constraints.minValue
            val maxValue = constraints.maxValue

            // Extract values to validate
            val valuesToValidate = mutableListOf<String>()
            if (metadata.type is DataType.Array) {
                when (valueElement) {
                    is kotlinx.serialization.json.JsonArray -> {
                        valueElement.forEach { elem ->
                            if (elem is JsonPrimitive) {
                                valuesToValidate.add(elem.content)
                            }
                        }
                    }
                    is JsonPrimitive -> {
                        val content = valueElement.content
                        content.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                            valuesToValidate.add(it)
                        }
                    }
                    else -> {}
                }
            } else {
                if (valueElement is JsonPrimitive) {
                    valuesToValidate.add(valueElement.content)
                }
            }

            for (valStr in valuesToValidate) {
                if (minLength != null && valStr.length < minLength) {
                    throw IllegalArgumentException("Parameter '$paramName' value '$valStr' violates minLength constraint of $minLength.")
                }
                if (maxLength != null && valStr.length > maxLength) {
                    throw IllegalArgumentException("Parameter '$paramName' value '$valStr' violates maxLength constraint of $maxLength.")
                }
                if (!regex.isNullOrEmpty()) {
                    try {
                        val pattern = Regex(regex)
                        if (!pattern.matches(valStr)) {
                            throw IllegalArgumentException("Parameter '$paramName' value '$valStr' does not match the required format: $regex")
                        }
                    } catch (e: Exception) {
                        throw IllegalArgumentException("Parameter '$paramName' has an invalid regex constraint pattern: $regex")
                    }
                }

                // If numeric, validate min/max values
                val doubleVal = valStr.toDoubleOrNull()
                if (doubleVal != null) {
                    if (minValue != null && doubleVal < minValue) {
                        throw IllegalArgumentException("Parameter '$paramName' value $doubleVal violates minValue constraint of $minValue.")
                    }
                    if (maxValue != null && doubleVal > maxValue) {
                        throw IllegalArgumentException("Parameter '$paramName' value $doubleVal violates maxValue constraint of $maxValue.")
                    }
                } else if (minValue != null || maxValue != null) {
                    val isNumericType = when (val t = metadata.type) {
                        is DataType.Primitive -> t.primitiveType == PrimitiveType.INT || t.primitiveType == PrimitiveType.DOUBLE
                        is DataType.Array -> {
                            val itemType = t.items
                            itemType is DataType.Primitive && (itemType.primitiveType == PrimitiveType.INT || itemType.primitiveType == PrimitiveType.DOUBLE)
                        }
                        else -> false
                    }
                    if (isNumericType && valStr.isNotEmpty()) {
                        throw IllegalArgumentException("Parameter '$paramName' value '$valStr' is not a valid number.")
                    }
                }
            }
        }
    }

    fun stop() {
        isActive = false
        workerJob.cancel()
    }
}
