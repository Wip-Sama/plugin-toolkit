package org.wip.plugintoolkit.features.flows.model

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PluginInfo

object OffsetSerializer : KSerializer<Offset> {
    @Serializable
    private data class SerializableOffset(val x: Float, val y: Float)

    override val descriptor: SerialDescriptor = SerializableOffset.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Offset) {
        encoder.encodeSerializableValue(SerializableOffset.serializer(), SerializableOffset(value.x, value.y))
    }

    override fun deserialize(decoder: Decoder): Offset {
        val s = decoder.decodeSerializableValue(SerializableOffset.serializer())
        return Offset(s.x, s.y)
    }
}

object AnySerializer : KSerializer<Any?> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    private fun toJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is JsonElement -> value
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to toJsonElement(it.value) })
            is List<*> -> JsonArray(value.map { toJsonElement(it) })
            is Array<*> -> JsonArray(value.map { toJsonElement(it) })
            else -> JsonPrimitive(value.toString())
        }
    }

    private fun toPrimitiveOrElement(element: JsonElement): Any? {
        return when (element) {
            is JsonNull -> null
            is JsonPrimitive -> {
                if (element.isString) {
                    element.content
                } else {
                    element.booleanOrNull ?: element.intOrNull ?: element.longOrNull ?: element.doubleOrNull ?: element.content
                }
            }
            is JsonObject -> element.entries.associate { it.key to toPrimitiveOrElement(it.value) }
            is JsonArray -> element.map { toPrimitiveOrElement(it) }
        }
    }

    override fun serialize(encoder: Encoder, value: Any?) {
        encoder.encodeSerializableValue(JsonElement.serializer(), toJsonElement(value))
    }

    override fun deserialize(decoder: Decoder): Any? {
        val jsonElement = decoder.decodeSerializableValue(JsonElement.serializer())
        return toPrimitiveOrElement(jsonElement)
    }
}

interface Port {
    val id: String
    val name: String
    val dataType: DataType
    val semanticType: String?
}

@Serializable
data class InputPort(
    override val id: String,
    override val name: String,
    override val dataType: DataType,
    override val semanticType: String? = null,
    @Serializable(with = AnySerializer::class) val defaultValue: Any? = null,
    @Serializable(with = AnySerializer::class) val value: Any? = null
) : Port

@Serializable
data class OutputPort(
    override val id: String,
    override val name: String,
    override val dataType: DataType,
    override val semanticType: String? = null
) : Port

@Serializable
sealed class Node {
    abstract val id: Long
    @Serializable(with = OffsetSerializer::class)
    abstract val position: Offset
    abstract val title: String
    abstract val inputs: List<InputPort>
    abstract val outputs: List<OutputPort>
    
    abstract fun copyWithPosition(newPosition: Offset): Node
    abstract fun copyWithUpdatedInput(portId: String, value: Any?): Node

    @Serializable
    @SerialName("capability")
    data class CapabilityNode(
        override val id: Long,
        @Serializable(with = OffsetSerializer::class) override val position: Offset,
        val pluginInfo: PluginInfo,
        val capability: Capability,
        override val inputs: List<InputPort>,
        override val outputs: List<OutputPort>
    ) : Node() {
        override val title: String get() = capability.name
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
        override fun copyWithUpdatedInput(portId: String, value: Any?): Node {
            return copy(inputs = inputs.map { input ->
                if (input.id == portId) input.copy(value = value) else input
            })
        }
    }

    @Serializable
    @SerialName("system")
    data class SystemNode(
        override val id: Long,
        @Serializable(with = OffsetSerializer::class) override val position: Offset,
        override val title: String,
        val systemAction: String, // e.g., "save", "load"
        override val inputs: List<InputPort>,
        override val outputs: List<OutputPort>
    ) : Node() {
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
        override fun copyWithUpdatedInput(portId: String, value: Any?): Node {
            return copy(inputs = inputs.map { input ->
                if (input.id == portId) input.copy(value = value) else input
            })
        }
    }

    @Serializable
    @SerialName("flow_input")
    data class FlowInputNode(
        override val id: Long,
        @Serializable(with = OffsetSerializer::class) override val position: Offset,
        override val outputs: List<OutputPort>
    ) : Node() {
        override val title: String get() = "Flow Input (${outputs.firstOrNull()?.name ?: "input_data"})"
        override val inputs: List<InputPort> = emptyList() // Uses outputs to provide data into the flow
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
        override fun copyWithUpdatedInput(portId: String, value: Any?): Node = this
    }

    @Serializable
    @SerialName("flow_output")
    data class FlowOutputNode(
        override val id: Long,
        @Serializable(with = OffsetSerializer::class) override val position: Offset,
        override val inputs: List<InputPort>
    ) : Node() {
        override val title: String get() = "Flow Output (${inputs.firstOrNull()?.name ?: "output_data"})"
        override val outputs: List<OutputPort> = emptyList() // Uses inputs to collect data from the flow
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
        override fun copyWithUpdatedInput(portId: String, value: Any?): Node {
            return copy(inputs = inputs.map { input ->
                if (input.id == portId) input.copy(value = value) else input
            })
        }
    }
    
    @Serializable
    @SerialName("sub_flow")
    data class SubFlowNode(
        override val id: Long,
        @Serializable(with = OffsetSerializer::class) override val position: Offset,
        val flowName: String,
        override val inputs: List<InputPort>,
        override val outputs: List<OutputPort>
    ) : Node() {
        override val title: String get() = flowName
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
        override fun copyWithUpdatedInput(portId: String, value: Any?): Node {
            return copy(inputs = inputs.map { input ->
                if (input.id == portId) input.copy(value = value) else input
            })
        }
    }
}

@Serializable
data class Connection(
    val sourceNodeId: Long,
    val sourcePortId: String,
    val targetNodeId: Long,
    val targetPortId: String
)

@Serializable
data class Flow(
    val name: String,
    val nodes: List<Node> = emptyList(),
    val connections: List<Connection> = emptyList()
)
