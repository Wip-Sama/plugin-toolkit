package org.wip.plugintoolkit.features.job.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.JobHandle
import org.wip.plugintoolkit.api.PluginRequest
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.plugin.logic.PluginLoader
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.logic.PluginLifecycleCoordinator
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.doubleOrNull
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

        executionOrder.forEachIndexed { index, nodeId ->
            val node = flow.nodes.first { it.id == nodeId }
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
                        // The user can supply the value either keyed by node ID or output port ID
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
                            computedValues[Pair(node.id, "output_data")] = inputData
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
                    
                    val processor = plugin.getProcessor()
                    val context = pluginManager.createPluginContext(node.pluginInfo.id, job.id)
                    
                    val request = org.wip.plugintoolkit.api.PluginRequest(
                        method = node.capability.name,
                        parameters = capabilityParameters
                    )
                    
                    manager.addJobLog(job.id, "Invoking plugin capability: ${node.pluginInfo.name} -> ${node.capability.name}...")
                    val handle = processor.processAsync(request, context)
                    
                    // Simple cancel-forwarding handle registration
                    val jobExecution = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]!!
                    val wrappedHandle = object : org.wip.plugintoolkit.api.JobHandle by handle {
                        override fun cancel(force: Boolean) {
                            handle.cancel(force)
                            jobExecution.cancel()
                        }
                    }
                    manager.registerJobHandle(job.id, wrappedHandle)
                    
                    val result = try {
                        handle.result.await()
                    } catch (e: Exception) {
                        throw Exception("Capability ${node.capability.name} failed during await: ${e.message}", e)
                    } finally {
                        manager.unregisterJobHandle(job.id)
                    }
                    
                    when (result) {
                        is org.wip.plugintoolkit.api.ExecutionResult.Success -> {
                            val outputVal = result.response.result
                            computedValues[Pair(node.id, "result")] = outputVal
                            manager.addJobLog(job.id, "Capability invocation succeeded.")
                        }
                        is org.wip.plugintoolkit.api.ExecutionResult.Error -> {
                            throw Exception("Capability invocation failed: ${result.message}")
                        }
                        is org.wip.plugintoolkit.api.ExecutionResult.Paused -> {
                            throw Exception("Flow execution does not support interactive pausing of individual capabilities.")
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
        }

        // 4. Serialize results and complete job
        val jsonOutputs = toJsonElement(flowOutputs).toString()
        manager.addJobLog(job.id, "Flow executed successfully. Final output results: $jsonOutputs")
        manager.tryCompleteJob(job.id, jsonOutputs)
        lifecycleCoordinator.onLifecycleJobCompleted(job)
    }

    private suspend fun executeSubFlowRecursively(
        flow: org.wip.plugintoolkit.features.flows.model.Flow,
        job: BackgroundJob,
        appDataDir: String
    ): Map<String, Any?> {
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

        executionOrder.forEach { nodeId ->
            val node = flow.nodes.first { it.id == nodeId }
            
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
                            computedValues[Pair(node.id, "output_data")] = inputData
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
                            computedValues[Pair(node.id, "result")] = result.response.result
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

    fun stop() {
        isActive = false
        workerJob.cancel()
    }
}
