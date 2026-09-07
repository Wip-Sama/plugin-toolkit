package org.wip.plugintoolkit.features.job.logic

import kotlinx.coroutines.yield
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node

/**
 * Executor for the "conditional" system node.
 * Routes input data to either 'if_true' or 'if_false' output depending on condition.
 */
class ConditionalNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val condition = when (val conditionVal = context.getInputValue("condition", false)) {
            is Boolean -> conditionVal
            is String -> conditionVal.toBoolean()
            is Number -> conditionVal.toInt() != 0
            else -> false
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
 * Executes a sub-flow repeatedly across a numeric range.
 */
class ForNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val subflowName = context.getInputValue("subflow_name", "") as String
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
            context.setOutputValue("output_data", context.getInputValue("input_data", null))
        }
    }
}

/**
 * Executor for the "while" system node.
 * Executes a sub-flow repeatedly while condition evaluates to true.
 */
class WhileNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val subflowName = context.getInputValue("subflow_name", "") as String
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
            context.setOutputValue("output_data", context.getInputValue("input_data", null))
        }
    }
}
