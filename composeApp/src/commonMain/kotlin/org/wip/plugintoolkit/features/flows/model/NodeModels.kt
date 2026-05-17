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

    override fun serialize(encoder: Encoder, value: Any?) {
        val jsonElement = when (value) {
            null -> JsonNull
            is JsonElement -> value
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
        encoder.encodeSerializableValue(JsonElement.serializer(), jsonElement)
    }

    override fun deserialize(decoder: Decoder): Any? {
        val jsonElement = decoder.decodeSerializableValue(JsonElement.serializer())
        return when (jsonElement) {
            is JsonNull -> null
            is JsonPrimitive -> {
                if (jsonElement.isString) {
                    jsonElement.content
                } else {
                    jsonElement.booleanOrNull ?: jsonElement.intOrNull ?: jsonElement.longOrNull ?: jsonElement.doubleOrNull ?: jsonElement.content
                }
            }
            else -> jsonElement
        }
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
    abstract fun copyWithUpdatedInputs(inputValues: Map<Pair<Long, String>, Any?>): Node

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
        override fun copyWithUpdatedInputs(inputValues: Map<Pair<Long, String>, Any?>): Node {
            return copy(inputs = inputs.map { input ->
                input.copy(value = inputValues[id to input.id] ?: input.value)
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
        override fun copyWithUpdatedInputs(inputValues: Map<Pair<Long, String>, Any?>): Node {
            return copy(inputs = inputs.map { input ->
                input.copy(value = inputValues[id to input.id] ?: input.value)
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
        override val title: String = "Flow Input"
        override val inputs: List<InputPort> = emptyList() // Uses outputs to provide data into the flow
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
        override fun copyWithUpdatedInputs(inputValues: Map<Pair<Long, String>, Any?>): Node = this
    }

    @Serializable
    @SerialName("flow_output")
    data class FlowOutputNode(
        override val id: Long,
        @Serializable(with = OffsetSerializer::class) override val position: Offset,
        override val inputs: List<InputPort>
    ) : Node() {
        override val title: String = "Flow Output"
        override val outputs: List<OutputPort> = emptyList() // Uses inputs to collect data from the flow
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
        override fun copyWithUpdatedInputs(inputValues: Map<Pair<Long, String>, Any?>): Node {
            return copy(inputs = inputs.map { input ->
                input.copy(value = inputValues[id to input.id] ?: input.value)
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
        override fun copyWithUpdatedInputs(inputValues: Map<Pair<Long, String>, Any?>): Node {
            return copy(inputs = inputs.map { input ->
                input.copy(value = inputValues[id to input.id] ?: input.value)
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
