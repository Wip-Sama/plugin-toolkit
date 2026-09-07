package org.wip.plugintoolkit.features.job.logic

import kotlinx.serialization.json.JsonElement
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.core.utils.SemanticRegistry
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.job.model.BackgroundJob

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
    semanticRegistry: SemanticRegistry
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
