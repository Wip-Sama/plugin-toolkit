package org.wip.plugintoolkit.features.flows.model

import kotlinx.serialization.Serializable

@Serializable
data class ValidationError(
    val sourceNodeId: Long,
    val sourcePortId: String,
    val targetNodeId: Long,
    val targetPortId: String,
    val message: String
)
