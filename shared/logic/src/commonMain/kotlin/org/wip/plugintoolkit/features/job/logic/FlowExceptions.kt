package org.wip.plugintoolkit.features.job.logic

import kotlinx.serialization.json.JsonElement

class PauseFlowException(val resumeState: JsonElement) : RuntimeException("Flow execution paused")

class FlowExecutionFailureException(
    val nodeId: Long,
    val nodeTitle: String,
    val resumeState: JsonElement,
    override val message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
