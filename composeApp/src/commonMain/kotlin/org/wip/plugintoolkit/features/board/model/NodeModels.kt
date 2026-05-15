package org.wip.plugintoolkit.features.board.model

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PluginInfo

interface Port {
    val id: String
    val name: String
    val dataType: DataType
    val semanticType: String?
}

data class InputPort(
    override val id: String,
    override val name: String,
    override val dataType: DataType,
    override val semanticType: String? = null,
    val defaultValue: Any? = null,
    val value: Any? = null
) : Port

data class OutputPort(
    override val id: String,
    override val name: String,
    override val dataType: DataType,
    override val semanticType: String? = null
) : Port

sealed class Node {
    abstract val id: Long
    abstract val position: Offset
    abstract val title: String
    abstract val inputs: List<InputPort>
    abstract val outputs: List<OutputPort>
    
    abstract fun copyWithPosition(newPosition: Offset): Node

    data class CapabilityNode(
        override val id: Long,
        override val position: Offset,
        val pluginInfo: PluginInfo,
        val capability: Capability,
        override val inputs: List<InputPort>,
        override val outputs: List<OutputPort>
    ) : Node() {
        override val title: String get() = capability.name
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
    }

    data class SystemNode(
        override val id: Long,
        override val position: Offset,
        override val title: String,
        val systemAction: String, // e.g., "save", "load"
        override val inputs: List<InputPort>,
        override val outputs: List<OutputPort>
    ) : Node() {
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
    }

    data class FlowInputNode(
        override val id: Long,
        override val position: Offset,
        override val outputs: List<OutputPort>
    ) : Node() {
        override val title: String = "Flow Input"
        override val inputs: List<InputPort> = emptyList() // Uses outputs to provide data into the flow
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
    }

    data class FlowOutputNode(
        override val id: Long,
        override val position: Offset,
        override val inputs: List<InputPort>
    ) : Node() {
        override val title: String = "Flow Output"
        override val outputs: List<OutputPort> = emptyList() // Uses inputs to collect data from the flow
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
    }
    
    data class SubFlowNode(
        override val id: Long,
        override val position: Offset,
        val flowName: String,
        override val inputs: List<InputPort>,
        override val outputs: List<OutputPort>
    ) : Node() {
        override val title: String get() = flowName
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
    }
}

data class Connection(
    val sourceNodeId: Long,
    val sourcePortId: String,
    val targetNodeId: Long,
    val targetPortId: String
)

data class Flow(
    val name: String,
    val nodes: List<Node> = emptyList(),
    val connections: List<Connection> = emptyList()
)
