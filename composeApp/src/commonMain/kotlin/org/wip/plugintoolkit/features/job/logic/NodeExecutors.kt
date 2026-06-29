package org.wip.plugintoolkit.features.job.logic

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.write
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
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

class DefaultSystemNodeExecutorRegistry(
    private val semanticRegistry: SemanticRegistry
) : SystemNodeExecutorRegistry {
    private val executors = mapOf(
        "save" to SaveNodeExecutor(),
        "save_file" to SaveFileNodeExecutor(),
        "save_folder" to SaveFolderNodeExecutor(),
        "load" to LoadNodeExecutor(semanticRegistry),

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
        val isDestructiveVal = context.getInputValue("is_destructive", false)
        val isDestructive = when (isDestructiveVal) {
            is Boolean -> isDestructiveVal
            is String -> isDestructiveVal.toBoolean()
            is Number -> isDestructiveVal.toInt() != 0
            else -> false
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

        val dataString = when (data) {
            is JsonElement -> {
                if (data is JsonPrimitive && data.isString) data.content else data.toString()
            }

            else -> data?.toString() ?: ""
        }

        val possibleSourcePath = Path(dataString)
        if (possibleSourcePath.isAbsolute && SystemFileSystem.exists(possibleSourcePath) && !SystemFileSystem.metadataOrNull(
                possibleSourcePath
            )!!.isDirectory
        ) {
            // It's a file, perform copy
            val metadata = SystemFileSystem.metadataOrNull(fullPath)
            if (metadata?.isDirectory == true) {
                throw Exception("Cannot save to a directory: $fullPath")
            }
            val sourceBytes = SystemFileSystem.source(possibleSourcePath).buffered().use { it.readByteArray() }
            SystemFileSystem.sink(fullPath).buffered().use { it.write(sourceBytes) }
            context.addLog("Copied file from $possibleSourcePath to $fullPath")

            if (isDestructive) {
                try {
                    SystemFileSystem.delete(possibleSourcePath)
                    context.addLog("Deleted source file $possibleSourcePath (is_destructive=true)")
                } catch (e: Exception) {
                    context.addLog("Warning: failed to delete source file $possibleSourcePath: ${e.message}", "WARN")
                }
            }
        } else {
            // It's plain text data
            val metadata = SystemFileSystem.metadataOrNull(fullPath)
            if (metadata?.isDirectory == true) {
                throw Exception("Cannot save to a directory: $fullPath")
            }
            SystemFileSystem.sink(fullPath).buffered().use { it.writeString(dataString) }
            context.addLog("Saved data to file: $filePath")
        }

        context.setOutputValue("success", true)
        context.setOutputValue("saved_path", fullPath.toString())
    }
}

/**
 * Executor for the "save_file" system node.
 * Copies or moves a file from a source path to a destination folder.
 */
class SaveFileNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val dataVal = context.getInputValue("data", "")
        val sourcePathStr = if (dataVal is JsonPrimitive && dataVal.isString) dataVal.content else dataVal?.toString() ?: ""
        
        val destinationFolderVal = context.getInputValue("destination_folder", "")
        val destinationFolderStr = if (destinationFolderVal is JsonPrimitive && destinationFolderVal.isString) destinationFolderVal.content else destinationFolderVal?.toString() ?: ""
        
        val fileNameVal = context.getInputValue("file_name", "")
        val providedFileName = if (fileNameVal is JsonPrimitive && fileNameVal.isString) fileNameVal.content else fileNameVal?.toString() ?: ""
        
        val isDestructiveVal = context.getInputValue("is_destructive", false)
        val isDestructive = when (isDestructiveVal) {
            is Boolean -> isDestructiveVal
            is String -> isDestructiveVal.toBoolean()
            is Number -> isDestructiveVal.toInt() != 0
            else -> false
        }

        if (sourcePathStr.isBlank()) {
            throw Exception("Source data (file path) is empty.")
        }
        if (destinationFolderStr.isBlank()) {
            throw Exception("Destination folder path is empty.")
        }

        val sourcePath = kotlinx.io.files.Path(sourcePathStr)
        if (!SystemFileSystem.exists(sourcePath)) {
            throw Exception("Source file does not exist: $sourcePath")
        }
        if (SystemFileSystem.metadataOrNull(sourcePath)?.isDirectory == true) {
            throw Exception("Source path is a directory, not a file: $sourcePath")
        }

        val destFolderPath = kotlinx.io.files.Path(destinationFolderStr)
        val fullDestFolderPath = if (destFolderPath.isAbsolute) {
            destFolderPath
        } else {
            Path(context.appDataDir, destinationFolderStr)
        }

        if (!SystemFileSystem.exists(fullDestFolderPath)) {
            SystemFileSystem.createDirectories(fullDestFolderPath)
        } else if (SystemFileSystem.metadataOrNull(fullDestFolderPath)?.isDirectory != true) {
            throw Exception("Destination path exists but is not a directory: $fullDestFolderPath")
        }

        val finalFileName = if (providedFileName.isNotBlank()) providedFileName else sourcePath.name
        val fullDestFilePath = Path(fullDestFolderPath, finalFileName)

        val sourceBytes = SystemFileSystem.source(sourcePath).buffered().use { it.readByteArray() }
        SystemFileSystem.sink(fullDestFilePath).buffered().use { it.write(sourceBytes) }
        context.addLog("Copied file from $sourcePath to $fullDestFilePath")

        if (isDestructive) {
            try {
                SystemFileSystem.delete(sourcePath)
                context.addLog("Deleted source file $sourcePath (is_destructive=true)")
            } catch (e: Exception) {
                context.addLog("Warning: failed to delete source file $sourcePath: ${e.message}", "WARN")
            }
        }

        context.setOutputValue("success", true)
        context.setOutputValue("saved_path", fullDestFilePath.toString())
    }
}

private fun copyRecursively(source: kotlinx.io.files.Path, dest: kotlinx.io.files.Path) {
    if (!SystemFileSystem.exists(source)) return
    
    val metadata = SystemFileSystem.metadataOrNull(source)
    if (metadata?.isDirectory == true) {
        if (!SystemFileSystem.exists(dest)) {
            SystemFileSystem.createDirectories(dest)
        }
        SystemFileSystem.list(source).forEach { child ->
            val childDest = kotlinx.io.files.Path(dest, child.name)
            copyRecursively(child, childDest)
        }
    } else {
        val sourceBytes = SystemFileSystem.source(source).buffered().use { it.readByteArray() }
        SystemFileSystem.sink(dest).buffered().use { it.write(sourceBytes) }
    }
}

/**
 * Executor for the "save_folder" system node.
 * Copies or moves a folder from a source path to a destination folder.
 */
class SaveFolderNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val dataVal = context.getInputValue("data", "")
        val sourcePathStr = if (dataVal is JsonPrimitive && dataVal.isString) dataVal.content else dataVal?.toString() ?: ""
        
        val destinationFolderVal = context.getInputValue("destination_folder", "")
        val destinationFolderStr = if (destinationFolderVal is JsonPrimitive && destinationFolderVal.isString) destinationFolderVal.content else destinationFolderVal?.toString() ?: ""
        
        val folderNameVal = context.getInputValue("folder_name", "")
        val providedFolderName = if (folderNameVal is JsonPrimitive && folderNameVal.isString) folderNameVal.content else folderNameVal?.toString() ?: ""
        
        val isDestructiveVal = context.getInputValue("is_destructive", false)
        val isDestructive = when (isDestructiveVal) {
            is Boolean -> isDestructiveVal
            is String -> isDestructiveVal.toBoolean()
            is Number -> isDestructiveVal.toInt() != 0
            else -> false
        }

        if (sourcePathStr.isBlank()) {
            throw Exception("Source data (folder path) is empty.")
        }
        if (destinationFolderStr.isBlank()) {
            throw Exception("Destination folder path is empty.")
        }

        val sourcePath = kotlinx.io.files.Path(sourcePathStr)
        if (!SystemFileSystem.exists(sourcePath)) {
            throw Exception("Source folder does not exist: $sourcePath")
        }
        if (SystemFileSystem.metadataOrNull(sourcePath)?.isDirectory != true) {
            throw Exception("Source path is a file, not a directory: $sourcePath")
        }

        val destFolderPath = kotlinx.io.files.Path(destinationFolderStr)
        val fullDestFolderPath = if (destFolderPath.isAbsolute) {
            destFolderPath
        } else {
            Path(context.appDataDir, destinationFolderStr)
        }

        if (!SystemFileSystem.exists(fullDestFolderPath)) {
            SystemFileSystem.createDirectories(fullDestFolderPath)
        } else if (SystemFileSystem.metadataOrNull(fullDestFolderPath)?.isDirectory != true) {
            throw Exception("Destination path exists but is not a directory: $fullDestFolderPath")
        }

        val finalFolderName = if (providedFolderName.isNotBlank()) providedFolderName else sourcePath.name
        val fullDestFolderPathFinal = Path(fullDestFolderPath, finalFolderName)

        copyRecursively(sourcePath, fullDestFolderPathFinal)
        context.addLog("Copied folder from $sourcePath to $fullDestFolderPathFinal")

        if (isDestructive) {
            try {
                // Assuming JobWorkerUtils.deleteRecursively is available or we implement a simple delete
                org.wip.plugintoolkit.features.job.logic.deleteRecursively(sourcePath)
                context.addLog("Deleted source folder $sourcePath (is_destructive=true)")
            } catch (e: Exception) {
                context.addLog("Warning: failed to delete source folder $sourcePath: ${e.message}", "WARN")
            }
        }

        context.setOutputValue("success", true)
        context.setOutputValue("saved_path", fullDestFolderPathFinal.toString())
    }
}

/**
 * Executor for the "load" system node.
 * Reads data from a file. If the file path is relative, it's resolved against the app data directory.
 * Returns the file content as a string.
 */
class LoadNodeExecutor(
    private val semanticRegistry: SemanticRegistry
) : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val filePathVal = context.getInputValue("file_path", "")
        val filePath = if (filePathVal is JsonPrimitive && filePathVal.isString) filePathVal.content else filePathVal?.toString() ?: ""
        if (filePath.isBlank()) {
            throw Exception("File path is required")
        }
        val dataPort = context.node.outputs.find { it.id == "data" }
        val semanticTypes = dataPort?.semanticTypes ?: emptyList()
        if (semanticTypes.isNotEmpty()) {
            val allowedExtensions = semanticRegistry.getAllowedExtensions(semanticTypes)
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
            val metadata = SystemFileSystem.metadataOrNull(fullPath)
            if (metadata?.isDirectory == true) {
                throw Exception("Cannot load a directory: $fullPath")
            }
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
                is JsonPrimitive -> {
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
                            is JsonPrimitive -> {
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
            val subFlow = Json { ignoreUnknownKeys = true; encodeDefaults = true }
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
                    kotlinx.coroutines.yield()
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
                kotlinx.coroutines.yield()
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
