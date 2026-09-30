package org.wip.plugintoolkit.features.job.logic

import kotlinx.coroutines.yield
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset
import org.wip.plugintoolkit.features.flows.model.OutputPort

/**
 * Executor for the "conditional" system node.
 * Routes input data to either 'if_true' or 'if_false' output depending on condition.
 * If expected_value is provided, matches condition against expected_value instead of pure boolean evaluation.
 */
class ConditionalNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val conditionVal = context.getInputValue("condition", false)
        val expectedVal = context.getInputValue("expected_value", null)
        val condition = if (expectedVal != null) {
            val condStr = when (conditionVal) {
                is JsonPrimitive -> conditionVal.content
                else -> conditionVal?.toString()
            }
            val expStr = when (expectedVal) {
                is JsonPrimitive -> expectedVal.content
                else -> expectedVal.toString()
            }
            condStr == expStr
        } else {
            when (conditionVal) {
                is Boolean -> conditionVal
                is String -> conditionVal.toBoolean()
                is Number -> conditionVal.toInt() != 0
                is JsonPrimitive -> conditionVal.booleanOrNull ?: conditionVal.content.toBoolean()
                else -> false
            }
        }
        val inputData = context.getInputValue("input_data", null)
        if (condition) {
            context.setOutputValue("if_true", inputData)
            context.setOutputValue("if_false", null)
        } else {
            context.setOutputValue("if_true", null)
            context.setOutputValue("if_false", inputData)
        }
    }
}

/**
 * Executor for the "for" system node.
 * Executes connected downstream nodes or a subflow repeatedly across an enum, list/collection, or numeric range.
 */
class ForNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val subflowName = (context.getInputValue("subflow_name", "") as? String)?.trim().orEmpty()
        if (subflowName.isNotEmpty()) {
            val subFlowFile = Path(
                "${context.appDataDir}/flows/${
                    subflowName.replace(
                        Regex("[\\\\/:*?\"<>|]"),
                        "_"
                    )
                }.json"
            )
            if (!SystemFileSystem.exists(subFlowFile)) {
                throw Exception("Subflow file not found: $subflowName")
            }
            val subFlowContent = SystemFileSystem.source(subFlowFile).buffered().use { it.readString() }
            val subFlow = Json { ignoreUnknownKeys = true; encodeDefaults = true }
                .decodeFromString<Flow>(subFlowContent)

            val start = (context.getInputValue("start", 0) as? Number)?.toInt() ?: 0
            val end = (context.getInputValue("end", 10) as? Number)?.toInt() ?: 10
            val step = (context.getInputValue("step", 1) as? Number)?.toInt() ?: 1
            var accumulator = context.getInputValue("input_data", null)
            var currentIndex = start

            val rs = context.resumeState as? JsonObject
            if (rs != null) {
                currentIndex = rs["index"]?.jsonPrimitive?.intOrNull ?: start
                accumulator = rs["accumulator"]?.let { fromJsonElement(it) } ?: accumulator
            }

            if (step != 0) {
                val range = if (step > 0) currentIndex until end step step else currentIndex downTo end + 1 step (-step)
                for (i in range) {
                    yield()
                    val subParameters = mutableMapOf<String, JsonElement>()
                    subFlow.nodes.filterIsInstance<Node.FlowInputNode>()
                        .forEach { inputNode ->
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

                    context.addLog("Executing for loop iteration index = $i")
                    val subOutputs = try {
                        context.executeSubFlow(subflowName, subParameters)
                    } catch (e: PauseFlowException) {
                        val state = JsonObject(
                            mapOf(
                                "index" to JsonPrimitive(i),
                                "accumulator" to toJsonElement(accumulator),
                                "subflowResumeState" to e.resumeState
                            )
                        )
                        throw PauseFlowException(state)
                    }
                    val outVal = subOutputs["output_data"] ?: subOutputs["output"] ?: subOutputs["data"]
                    ?: subOutputs.values.firstOrNull()
                    accumulator = outVal
                }
            }
            context.setOutputValue("output_data", accumulator)
        } else {
            val itemsToIterate: List<Any?> = when {
                context.getInputValue("items", null) != null -> {
                    when (val itVal = context.getInputValue("items", null)) {
                        is Iterable<*> -> itVal.toList()
                        is JsonArray -> itVal.map { fromJsonElement(it) }
                        else -> listOf(itVal)
                    }
                }
                (context.getInputValue("enum_name", "") as? String)?.isNotBlank() == true -> {
                    val enumName = (context.getInputValue("enum_name", "") as String).trim()
                    val options = context.pluginEnums[enumName]
                        ?: context.pluginEnums[enumName.substringAfterLast('.')]
                        ?: if (enumName.contains(",")) enumName.split(",").map { it.trim() }.filter { it.isNotEmpty() } else null
                    options ?: emptyList()
                }
                context.getInputValue("input_data", null) is Iterable<*> -> {
                    (context.getInputValue("input_data", null) as Iterable<*>).toList()
                }
                context.getInputValue("input_data", null) is JsonArray -> {
                    (context.getInputValue("input_data", null) as JsonArray).map { fromJsonElement(it) }
                }
                else -> {
                    val start = (context.getInputValue("start", 0) as? Number)?.toInt() ?: 0
                    val end = (context.getInputValue("end", 10) as? Number)?.toInt() ?: 10
                    val step = (context.getInputValue("step", 1) as? Number)?.toInt() ?: 1
                    if (step != 0) {
                        if (step > 0) (start until end step step).toList()
                        else (start downTo end + 1 step (-step)).toList()
                    } else emptyList()
                }
            }

            val currentFlow = context.currentFlow
            if (currentFlow != null) {
                val directConnections = currentFlow.connections.filter { it.sourceNodeId == context.node.id }
                val loopStartConnections = directConnections.filter { it.sourcePortId == "item" || it.sourcePortId == "index" }
                    .ifEmpty { directConnections.filter { it.sourcePortId != "output_data" } }
                val postLoopConnections = directConnections.filter { it.sourcePortId == "output_data" }

                val postLoopNodeIds = mutableSetOf<Long>()
                val postQueue = ArrayDeque<Long>()
                postLoopConnections.forEach {
                    postLoopNodeIds.add(it.targetNodeId)
                    postQueue.add(it.targetNodeId)
                }
                while (postQueue.isNotEmpty()) {
                    val cur = postQueue.removeFirst()
                    currentFlow.connections.filter { it.sourceNodeId == cur }.forEach { conn ->
                        if (postLoopNodeIds.add(conn.targetNodeId)) postQueue.add(conn.targetNodeId)
                    }
                }

                val loopBodyNodeIds = mutableSetOf<Long>()
                val queue = ArrayDeque<Long>()
                loopStartConnections.forEach {
                    if (it.targetNodeId !in postLoopNodeIds) {
                        loopBodyNodeIds.add(it.targetNodeId)
                        queue.add(it.targetNodeId)
                    }
                }
                while (queue.isNotEmpty()) {
                    val cur = queue.removeFirst()
                    currentFlow.connections.filter { it.sourceNodeId == cur }.forEach { conn ->
                        if (conn.targetNodeId !in postLoopNodeIds && loopBodyNodeIds.add(conn.targetNodeId)) {
                            queue.add(conn.targetNodeId)
                        }
                    }
                }

                if (loopBodyNodeIds.isNotEmpty()) {
                    val loopBodyNodes = currentFlow.nodes.filter { it.id in loopBodyNodeIds }
                    val syntheticInputNode = Node.FlowInputNode(
                        id = -9999L,
                        position = Offset.Zero,
                        outputs = listOf(
                            OutputPort("item", "item", DataType.Primitive(PrimitiveType.ANY)),
                            OutputPort("index", "index", DataType.Primitive(PrimitiveType.INT))
                        )
                    )

                    val internalConnections = currentFlow.connections.filter {
                        it.sourceNodeId in loopBodyNodeIds && it.targetNodeId in loopBodyNodeIds
                    }
                    val mappedInputConnections = loopStartConnections.filter { it.targetNodeId in loopBodyNodeIds }.map {
                        it.copy(sourceNodeId = syntheticInputNode.id)
                    }

                    val nodesWithDownstreamInLoop = internalConnections.map { it.sourceNodeId }.toSet()
                    val terminalNodes = loopBodyNodes.filter { it.id !in nodesWithDownstreamInLoop && it !is Node.FlowOutputNode }
                    val syntheticOutputNodes = terminalNodes.mapIndexed { idx, term ->
                        Node.FlowOutputNode(
                            id = -8888L - idx,
                            position = Offset.Zero,
                            inputs = listOf(InputPort("data", "data", DataType.Primitive(PrimitiveType.ANY)))
                        )
                    }
                    val mappedOutputConnections = terminalNodes.mapIndexed { idx, term ->
                        val outPort = term.outputs.firstOrNull()?.id ?: "output"
                        Connection(
                            sourceNodeId = term.id,
                            sourcePortId = outPort,
                            targetNodeId = syntheticOutputNodes[idx].id,
                            targetPortId = "data"
                        )
                    }

                    val dynamicSubflow = Flow(
                        name = "DynamicLoop_${context.node.id}",
                        nodes = listOf(syntheticInputNode) + loopBodyNodes + syntheticOutputNodes,
                        connections = internalConnections + mappedInputConnections + mappedOutputConnections
                    )

                    val collectedResults = mutableListOf<Any?>()
                    for ((idx, item) in itemsToIterate.withIndex()) {
                        yield()
                        val params = mapOf(
                            "${syntheticInputNode.id}_item" to toJsonElement(item),
                            "${syntheticInputNode.id}_index" to toJsonElement(idx)
                        )
                        context.addLog("Executing for loop variant [$idx]: $item")
                        val subOutputs = context.executeDynamicSubFlow(dynamicSubflow, params)
                        val outVal = subOutputs["output_data"] ?: subOutputs["output"] ?: subOutputs["data"]
                            ?: subOutputs.values.firstOrNull() ?: item
                        collectedResults.add(outVal)
                    }

                    context.markNodesExecuted(loopBodyNodeIds)
                    context.setOutputValue("item", itemsToIterate.lastOrNull())
                    context.setOutputValue("index", (itemsToIterate.size - 1).coerceAtLeast(0))
                    context.setOutputValue("output_data", collectedResults)
                    return
                }
            }

            context.setOutputValue("item", itemsToIterate.lastOrNull())
            context.setOutputValue("index", (itemsToIterate.size - 1).coerceAtLeast(0))
            context.setOutputValue("output_data", itemsToIterate)
        }
    }
}

/**
 * Executor for the "while" system node.
 * Executes connected downstream nodes or a subflow repeatedly while condition evaluates to true.
 */
class WhileNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val subflowName = (context.getInputValue("subflow_name", "") as? String)?.trim().orEmpty()
        if (subflowName.isNotEmpty()) {
            val subFlowFile = Path(
                "${context.appDataDir}/flows/${
                    subflowName.replace(
                        Regex("[\\\\/:*?\"<>|]"),
                        "_"
                    )
                }.json"
            )
            if (!SystemFileSystem.exists(subFlowFile)) {
                throw Exception("Subflow file not found: $subflowName")
            }
            val subFlowContent = SystemFileSystem.source(subFlowFile).buffered().use { it.readString() }
            val subFlow = Json { ignoreUnknownKeys = true; encodeDefaults = true }
                .decodeFromString<Flow>(subFlowContent)

            var condition = when (val conditionVal = context.getInputValue("condition", true)) {
                is Boolean -> conditionVal
                is String -> conditionVal.toBoolean()
                is Number -> conditionVal.toInt() != 0
                else -> false
            }
            var accumulator = context.getInputValue("input_data", null)
            var iteration = 0

            val rs = context.resumeState as? JsonObject
            if (rs != null) {
                iteration = rs["iteration"]?.jsonPrimitive?.intOrNull ?: 0
                condition = rs["condition"]?.jsonPrimitive?.booleanOrNull ?: condition
                accumulator = rs["accumulator"]?.let { fromJsonElement(it) } ?: accumulator
            }

            while (condition) {
                yield()
                val subParameters = mutableMapOf<String, JsonElement>()
                subFlow.nodes.filterIsInstance<Node.FlowInputNode>()
                    .forEach { inputNode ->
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

                context.addLog("Executing while loop iteration $iteration")
                val subOutputs = try {
                    context.executeSubFlow(subflowName, subParameters)
                } catch (e: PauseFlowException) {
                    val state = JsonObject(
                        mapOf(
                            "iteration" to JsonPrimitive(iteration),
                            "condition" to JsonPrimitive(condition),
                            "accumulator" to toJsonElement(accumulator),
                            "subflowResumeState" to e.resumeState
                        )
                    )
                    throw PauseFlowException(state)
                }

                val newAccumulator = subOutputs["output_data"] ?: subOutputs["output"] ?: subOutputs["data"]
                val newConditionVal = subOutputs["condition"] ?: subOutputs["cond"]

                if (newAccumulator != null || subOutputs.containsKey("output_data") || subOutputs.containsKey("output") || subOutputs.containsKey(
                        "data"
                    )
                ) {
                    accumulator = newAccumulator
                } else {
                    val nonConditionOutput =
                        subOutputs.filterKeys { it != "condition" && it != "cond" }.values.firstOrNull()
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
                    context.addLog(
                        "Warning: while loop subflow did not return 'condition' or 'cond' output, exiting loop",
                        "WARN"
                    )
                    break
                }

                iteration++
            }

            context.setOutputValue("output_data", accumulator)
        } else {
            var condition = when (val conditionVal = context.getInputValue("condition", true)) {
                is Boolean -> conditionVal
                is String -> conditionVal.toBoolean()
                is Number -> conditionVal.toInt() != 0
                is JsonPrimitive -> conditionVal.booleanOrNull ?: conditionVal.content.toBoolean()
                else -> false
            }
            var accumulator = context.getInputValue("input_data", null)
            val maxIterations = (context.getInputValue("max_iterations", 100) as? Number)?.toInt() ?: 100
            val currentFlow = context.currentFlow

            if (currentFlow != null && condition) {
                val directConnections = currentFlow.connections.filter { it.sourceNodeId == context.node.id }
                val loopStartConnections = directConnections.filter { it.sourcePortId == "item" || it.sourcePortId == "iteration" }
                    .ifEmpty { directConnections.filter { it.sourcePortId != "output_data" } }
                val postLoopConnections = directConnections.filter { it.sourcePortId == "output_data" }

                val postLoopNodeIds = mutableSetOf<Long>()
                val postQueue = ArrayDeque<Long>()
                postLoopConnections.forEach {
                    postLoopNodeIds.add(it.targetNodeId)
                    postQueue.add(it.targetNodeId)
                }
                while (postQueue.isNotEmpty()) {
                    val cur = postQueue.removeFirst()
                    currentFlow.connections.filter { it.sourceNodeId == cur }.forEach { conn ->
                        if (postLoopNodeIds.add(conn.targetNodeId)) postQueue.add(conn.targetNodeId)
                    }
                }

                val loopBodyNodeIds = mutableSetOf<Long>()
                val queue = ArrayDeque<Long>()
                loopStartConnections.forEach {
                    if (it.targetNodeId !in postLoopNodeIds) {
                        loopBodyNodeIds.add(it.targetNodeId)
                        queue.add(it.targetNodeId)
                    }
                }
                while (queue.isNotEmpty()) {
                    val cur = queue.removeFirst()
                    currentFlow.connections.filter { it.sourceNodeId == cur }.forEach { conn ->
                        if (conn.targetNodeId !in postLoopNodeIds && loopBodyNodeIds.add(conn.targetNodeId)) {
                            queue.add(conn.targetNodeId)
                        }
                    }
                }

                if (loopBodyNodeIds.isNotEmpty()) {
                    val loopBodyNodes = currentFlow.nodes.filter { it.id in loopBodyNodeIds }
                    val syntheticInputNode = Node.FlowInputNode(
                        id = -9999L,
                        position = Offset.Zero,
                        outputs = listOf(
                            OutputPort("item", "item", DataType.Primitive(PrimitiveType.ANY)),
                            OutputPort("iteration", "iteration", DataType.Primitive(PrimitiveType.INT))
                        )
                    )

                    val internalConnections = currentFlow.connections.filter {
                        it.sourceNodeId in loopBodyNodeIds && it.targetNodeId in loopBodyNodeIds
                    }
                    val mappedInputConnections = loopStartConnections.filter { it.targetNodeId in loopBodyNodeIds }.map {
                        it.copy(sourceNodeId = syntheticInputNode.id)
                    }

                    val nodesWithDownstreamInLoop = internalConnections.map { it.sourceNodeId }.toSet()
                    val terminalNodes = loopBodyNodes.filter { it.id !in nodesWithDownstreamInLoop && it !is Node.FlowOutputNode }
                    val syntheticOutputNodes = terminalNodes.mapIndexed { idx, term ->
                        Node.FlowOutputNode(
                            id = -8888L - idx,
                            position = Offset.Zero,
                            inputs = listOf(InputPort("data", "data", DataType.Primitive(PrimitiveType.ANY)))
                        )
                    }
                    val mappedOutputConnections = terminalNodes.mapIndexed { idx, term ->
                        val outPort = term.outputs.firstOrNull()?.id ?: "output"
                        Connection(
                            sourceNodeId = term.id,
                            sourcePortId = outPort,
                            targetNodeId = syntheticOutputNodes[idx].id,
                            targetPortId = "data"
                        )
                    }

                    val dynamicSubflow = Flow(
                        name = "DynamicWhile_${context.node.id}",
                        nodes = listOf(syntheticInputNode) + loopBodyNodes + syntheticOutputNodes,
                        connections = internalConnections + mappedInputConnections + mappedOutputConnections
                    )

                    var iteration = 0
                    while (condition && iteration < maxIterations) {
                        yield()
                        val params = mapOf(
                            "${syntheticInputNode.id}_item" to toJsonElement(accumulator),
                            "${syntheticInputNode.id}_iteration" to toJsonElement(iteration)
                        )
                        context.addLog("Executing while loop iteration $iteration")
                        val subOutputs = context.executeDynamicSubFlow(dynamicSubflow, params)
                        val newAccumulator = subOutputs["output_data"] ?: subOutputs["output"] ?: subOutputs["data"]
                            ?: subOutputs.values.firstOrNull()
                        if (newAccumulator != null) accumulator = newAccumulator

                        val newCond = subOutputs["condition"] ?: subOutputs["cond"]
                        if (newCond != null) {
                            condition = when (newCond) {
                                is Boolean -> newCond
                                is String -> newCond.toBoolean()
                                is Number -> newCond.toInt() != 0
                                is JsonPrimitive -> newCond.booleanOrNull ?: newCond.content.toBoolean()
                                else -> false
                            }
                        }
                        iteration++
                    }

                    context.markNodesExecuted(loopBodyNodeIds)
                    context.setOutputValue("item", accumulator)
                    context.setOutputValue("iteration", iteration)
                    context.setOutputValue("output_data", accumulator)
                    return
                }
            }

            context.setOutputValue("item", accumulator)
            context.setOutputValue("iteration", 0)
            context.setOutputValue("output_data", accumulator)
        }
    }
}
