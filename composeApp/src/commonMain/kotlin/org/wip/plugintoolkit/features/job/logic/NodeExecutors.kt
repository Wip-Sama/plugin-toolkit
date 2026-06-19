package org.wip.plugintoolkit.features.job.logic

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.core.utils.SemanticRegistry
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import kotlin.time.Duration.Companion.milliseconds

/**
 * Context provided to [NodeExecutor] during execution.
 * Provides access to input values, ability to set output values, logging, and sub-flow execution.
 */
interface NodeExecutionContext {
    /** The system node being executed. */
    val node: Node.SystemNode

    /** The background job containing this node execution. */
    val job: BackgroundJob

    /** The base directory for application data. */
    val appDataDir: String

    /** Runtime inferred types for ports. */
    val runtimeInferredTypes: Map<Pair<Long, String>, DataType>

    /** Resume state if the node was paused mid-execution. */
    val resumeState: JsonElement?

    /**
     * Retrieves the value of an input port.
     * @param portId The ID of the port.
     * @param defaultValue The value to return if no value is connected or set.
     */
    fun getInputValue(portId: String, defaultValue: Any?): Any?

    /**
     * Sets the value of an output port.
     * @param portId The ID of the port.
     * @param value The value to set.
     */
    fun setOutputValue(portId: String, value: Any?)

    /**
     * Adds a log entry to the job execution.
     * @param message The log message.
     * @param level The log level (e.g., "INFO", "WARN", "ERROR").
     */
    fun addLog(message: String, level: String = "INFO")

    /**
     * Executes a sub-flow.
     * @param flowName The name of the flow to execute.
     * @param parameters Input parameters for the sub-flow.
     * @return A map of output port names to their values.
     */
    suspend fun executeSubFlow(flowName: String, parameters: Map<String, JsonElement>): Map<String, Any?>
}

/**
 * Interface for executing a specific type of system node.
 */
interface NodeExecutor {
    /**
     * Executes the node logic.
     * @param context The execution context.
     */
    suspend fun execute(context: NodeExecutionContext)
}

/**
 * Registry for system node executors.
 */
interface SystemNodeExecutorRegistry {
    /**
     * Returns an executor for the given action name.
     * @param action The name of the system action (e.g., "load", "save").
     */
    fun getExecutor(action: String): NodeExecutor
}

class DefaultSystemNodeExecutorRegistry : SystemNodeExecutorRegistry {
    private val executors = mapOf(
        "save" to SaveNodeExecutor(),
        "load" to LoadNodeExecutor(),
        "log" to LogNodeExecutor(),
        "delay" to DelayNodeExecutor(),
        "convert" to ConvertNodeExecutor(),
        "conditional" to ConditionalNodeExecutor(),
        "error" to ErrorNodeExecutor(),
        "merger" to MergerNodeExecutor(),
        "comparator" to ComparatorNodeExecutor(),
        "for" to ForNodeExecutor(),
        "while" to WhileNodeExecutor(),
        "create_folder" to CreateFolderNodeExecutor()
    )

    override fun getExecutor(action: String): NodeExecutor {
        return executors[action.lowercase()] ?: throw IllegalArgumentException("Unsupported system action: $action")
    }
}

/**
 * Executor for the "save" system node.
 * Writes data to a file. If the file path is relative, it's resolved against the app data directory.
 */
class SaveNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val data = context.getInputValue("data", "")
        val filePath = context.getInputValue("file_path", "output.txt") as String
        val dataString = when (data) {
            is JsonElement -> data.toString()
            else -> data?.toString() ?: ""
        }

        val path = Path(filePath)
        val fullPath = if (path.isAbsolute) {
            path
        } else {
            kotlinx.io.files.Path(context.appDataDir, filePath)
        }

        val parent = fullPath.parent
        if (parent != null && !SystemFileSystem.exists(parent)) {
            SystemFileSystem.createDirectories(parent)
        }
        SystemFileSystem.sink(fullPath).buffered().use { it.writeString(dataString) }

        context.setOutputValue("success", true)
        context.addLog("Saved data to file: $filePath")
    }
}

/**
 * Executor for the "load" system node.
 * Reads data from a file. If the file path is relative, it's resolved against the app data directory.
 * Returns the file content as a string.
 */
class LoadNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val filePath = context.getInputValue("file_path", "output.txt") as String
        val dataPort = context.node.outputs.find { it.id == "data" }
        val semanticTypes = dataPort?.semanticTypes ?: emptyList()
        if (semanticTypes.isNotEmpty()) {
            val allowedExtensions = SemanticRegistry.getAllowedExtensions(semanticTypes)
            if (allowedExtensions.isNotEmpty()) {
                val filename = filePath.substringAfterLast('/').substringAfterLast('\\')
                val ext = filename.substringAfterLast('.', "").lowercase()
                if (ext !in allowedExtensions) {
                    throw Exception("File '$filePath' has unsupported extension '$ext'")
                }
            }
        }

        val path = Path(filePath)
        val fullPath = if (path.isAbsolute) {
            path
        } else {
            Path(context.appDataDir, filePath)
        }

        val fileContent = if (SystemFileSystem.exists(fullPath)) {
            SystemFileSystem.source(fullPath).buffered().use { it.readString() }
        } else {
            context.addLog("Warning: file to load not found at $fullPath, returning empty: $filePath", "WARN")
            ""
        }

        context.setOutputValue("data", fileContent)
        context.addLog("Loaded content from file: $filePath (Size: ${fileContent.length} chars)")
    }
}

class LogNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val level = context.getInputValue("level", "INFO") as String
        val message = context.getInputValue("message", "") as String
        val data = context.getInputValue("data", null)

        val logMessage = buildString {
            append(message)
            if (data != null) {
                append(" | Data: ")
                append(data)
            }
        }
        context.addLog(logMessage, level.uppercase())
        context.setOutputValue("output", message)
    }
}

class DelayNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val duration = when (val dur = context.getInputValue("duration", 1000)) {
            is Number -> dur.toLong()
            is String -> dur.toLongOrNull() ?: 1000L
            else -> 1000L
        }
        val inputData = context.getInputValue("input_data", null)

        context.addLog("Sleeping for $duration ms...")
        kotlinx.coroutines.delay(duration.milliseconds)

        context.setOutputValue("output_data", inputData)
    }
}

class ConvertNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val inputData = context.getInputValue("input_data", null)
        val targetType = context.runtimeInferredTypes[Pair(context.node.id, "output_data")]
            ?: DataType.Primitive(PrimitiveType.ANY)
        try {
            val converted = convertValue(inputData, targetType)
            context.setOutputValue("output_data", converted)
            context.setOutputValue("success", true)
        } catch (e: Exception) {
            context.addLog("Conversion warning: ${e.message}", "WARN")
            context.setOutputValue("output_data", null)
            context.setOutputValue("success", false)
        }
    }
}

class ConditionalNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val conditionVal = context.getInputValue("condition", false)
        val condition = when (conditionVal) {
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

class ErrorNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val message = context.getInputValue("message", "An error occurred during flow execution") as String
        val data = context.getInputValue("data", null)
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
}

class MergerNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val list1 = context.getInputValue("list1", null)
        val list2 = context.getInputValue("list2", null)

        val merged = mutableListOf<Any?>()

        fun addToList(item: Any?) {
            when (item) {
                is List<*> -> merged.addAll(item)
                is Array<*> -> merged.addAll(item)
                is JsonArray -> {
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

        context.setOutputValue("output", merged)
        context.addLog("Merged lists. Item count: ${merged.size}")
    }
}

class ComparatorNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val a = context.getInputValue("a", null)
        val b = context.getInputValue("b", null)

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
        context.setOutputValue("minor", minorVal)
        context.setOutputValue("major", majorVal)
        context.setOutputValue("equal", equalVal)
        context.setOutputValue("not_equal", notEqualVal)
        context.addLog("Comparator result: minor=$minorVal, major=$majorVal, equal=$equalVal, not_equal=$notEqualVal")
    }
}

class ForNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val subflowName = context.getInputValue("subflow_name", "") as String
        if (subflowName.isNotEmpty()) {
            val subFlowFile = kotlinx.io.files.Path(
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
            val subFlow = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
                .decodeFromString<org.wip.plugintoolkit.features.flows.model.Flow>(subFlowContent)

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
                    val subParameters = mutableMapOf<String, JsonElement>()
                    subFlow.nodes.filterIsInstance<org.wip.plugintoolkit.features.flows.model.Node.FlowInputNode>()
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

class WhileNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val subflowName = context.getInputValue("subflow_name", "") as String
        if (subflowName.isNotEmpty()) {
            val subFlowFile = kotlinx.io.files.Path(
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
            val subFlow = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
                .decodeFromString<org.wip.plugintoolkit.features.flows.model.Flow>(subFlowContent)

            var conditionVal = context.getInputValue("condition", true)
            var condition = when (conditionVal) {
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
                val subParameters = mutableMapOf<String, JsonElement>()
                subFlow.nodes.filterIsInstance<org.wip.plugintoolkit.features.flows.model.Node.FlowInputNode>()
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

class CreateFolderNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        try {
            val basePath = context.getInputValue("path", "") as String
            val folderName = context.getInputValue("folder_name", "") as String

            val path = kotlinx.io.files.Path(basePath)
            val fullBasePath = if (path.isAbsolute) {
                path
            } else {
                kotlinx.io.files.Path(context.appDataDir, basePath)
            }

            val newFolderPath = kotlinx.io.files.Path(fullBasePath, folderName)

            if (!kotlinx.io.files.SystemFileSystem.exists(newFolderPath)) {
                kotlinx.io.files.SystemFileSystem.createDirectories(newFolderPath)
                context.addLog("Created folder: $newFolderPath")
            } else {
                context.addLog("Folder already exists: $newFolderPath")
            }

            context.setOutputValue("created_path", newFolderPath.toString())
            context.setOutputValue("success", true)
        } catch (e: Exception) {
            context.addLog("Failed to create folder: ${e.message}", "ERROR")
            context.setOutputValue("created_path", null)
            context.setOutputValue("success", false)
        }
    }
}
