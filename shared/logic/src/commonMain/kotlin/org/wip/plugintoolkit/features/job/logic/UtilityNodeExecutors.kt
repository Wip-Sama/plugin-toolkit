package org.wip.plugintoolkit.features.job.logic

import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.milliseconds

/**
 * Executor for the "log" system node.
 * Logs a message and optional data to the job context.
 */
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

/**
 * Executor for the "delay" system node.
 * Pauses execution for a specified duration in milliseconds.
 */
class DelayNodeExecutor : NodeExecutor {
    override suspend fun execute(context: NodeExecutionContext) {
        val duration = when (val dur = context.getInputValue("duration", 1000)) {
            is Number -> dur.toLong()
            is String -> dur.toLongOrNull() ?: 1000L
            else -> 1000L
        }
        val inputData = context.getInputValue("input_data", null)

        context.addLog("Sleeping for $duration ms...")
        delay(duration.milliseconds)

        context.setOutputValue("output_data", inputData)
    }
}

/**
 * Executor for the "error" system node.
 * Throws an exception to intentionally fail the flow.
 */
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
