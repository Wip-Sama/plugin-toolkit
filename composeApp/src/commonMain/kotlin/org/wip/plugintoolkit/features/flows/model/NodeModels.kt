package org.wip.plugintoolkit.features.flows.model

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.api.parseSemanticTypes

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

    fun toJsonElement(value: Any?): JsonElement {
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
                    element.booleanOrNull ?: element.intOrNull ?: element.longOrNull ?: element.doubleOrNull
                    ?: element.content
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
    val semanticTypes: List<SemanticType>
    val description: String?
}

@Serializable(with = InputPortSerializer::class)
data class InputPort(
    override val id: String,
    override val name: String,
    override val dataType: DataType,
    override val semanticTypes: List<SemanticType> = emptyList(),
    @Serializable(with = AnySerializer::class) val defaultValue: Any? = null,
    @Serializable(with = AnySerializer::class) val value: Any? = null,
    val constraints: PortConstraints? = null,
    override val description: String? = null
) : Port

object InputPortSerializer : KSerializer<InputPort> {
    override val descriptor: SerialDescriptor = InputPortSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: InputPort) {
        val surrogate = InputPortSurrogate(
            id = value.id,
            name = value.name,
            description = value.description,
            dataType = value.dataType,
            semanticTypes = value.semanticTypes,
            defaultValue = value.defaultValue,
            value = value.value,
            regex = value.constraints?.regex,
            constraints = value.constraints
        )
        encoder.encodeSerializableValue(InputPortSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): InputPort {
        val surrogate = decoder.decodeSerializableValue(InputPortSurrogate.serializer())
        val migratedConstraints = surrogate.constraints ?: surrogate.regex?.let { PortConstraints(regex = it) }
        return InputPort(
            id = surrogate.id,
            name = surrogate.name,
            description = surrogate.description,
            dataType = surrogate.dataType,
            semanticTypes = surrogate.semanticTypes ?: emptyList(),
            defaultValue = surrogate.defaultValue,
            value = surrogate.value,
            constraints = migratedConstraints
        )
    }
}

@Serializable
@SerialName("InputPort")
private data class InputPortSurrogate(
    val id: String,
    val name: String,
    val dataType: DataType,
    val semanticType: String? = null,
    val semanticTypes: List<SemanticType>? = null,
    @Serializable(with = AnySerializer::class) val defaultValue: Any? = null,
    @Serializable(with = AnySerializer::class) val value: Any? = null,
    val regex: String? = null,
    val constraints: PortConstraints? = null,
    val description: String? = null
)

@Serializable(with = OutputPortSerializer::class)
data class OutputPort(
    override val id: String,
    override val name: String,
    override val dataType: DataType,
    override val semanticTypes: List<SemanticType> = emptyList(),
    override val description: String? = null
) : Port

object OutputPortSerializer : KSerializer<OutputPort> {
    override val descriptor: SerialDescriptor = OutputPortSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: OutputPort) {
        val surrogate = OutputPortSurrogate(
            id = value.id,
            name = value.name,
            description = value.description,
            dataType = value.dataType,
            semanticTypes = value.semanticTypes
        )
        encoder.encodeSerializableValue(OutputPortSurrogate.serializer(), surrogate)
    }

    override fun deserialize(decoder: Decoder): OutputPort {
        val input = decoder as? JsonDecoder ?: throw SerializationException("This serializer only supports JSON")
        val element = input.decodeJsonElement() as JsonObject
        val hasSemanticTypes = "semanticTypes" in element
        val finalElement = if (!hasSemanticTypes) {
            val legacySemanticType = element["semanticType"]?.jsonPrimitive?.contentOrNull
            val parsedList = parseSemanticTypes(legacySemanticType).map { type ->
                buildJsonObject {
                    put("namespace", type.namespace)
                    put("name", type.name)
                    put("variant", type.variant)
                }
            }
            JsonObject(element.filterKeys { it != "semanticType" } + ("semanticTypes" to JsonArray(parsedList)))
        } else {
            JsonObject(element.filterKeys { it != "semanticType" })
        }
        val surrogate = input.json.decodeFromJsonElement(OutputPortSurrogate.serializer(), finalElement)
        return OutputPort(
            id = surrogate.id,
            name = surrogate.name,
            description = surrogate.description,
            dataType = surrogate.dataType,
            semanticTypes = surrogate.semanticTypes
        )
    }
}

@Serializable
@SerialName("OutputPort")
private class OutputPortSurrogate(
    val id: String,
    val name: String,
    val dataType: DataType,
    val semanticTypes: List<SemanticType> = emptyList(),
    val description: String? = null
)

@Serializable
data class SubflowPortMapping(
    val portId: String,
    val boundaryNodeId: Long
)

@Serializable
sealed class Node {
    abstract val id: Long

    @Serializable(with = OffsetSerializer::class)
    abstract val position: Offset
    abstract val title: String
    abstract val inputs: List<InputPort>
    abstract val outputs: List<OutputPort>
    abstract val isCollapsed: Boolean
    abstract val isInputsCollapsed: Boolean
    abstract val isOutputsCollapsed: Boolean

    abstract fun copyWithPosition(newPosition: Offset): Node
    abstract fun copyWithUpdatedInput(portId: String, value: JsonElement?): Node
    abstract fun copyWithId(newId: Long): Node
    abstract fun copyWithCollapsedState(isCollapsed: Boolean): Node
    abstract fun copyWithInputsCollapsedState(isCollapsed: Boolean): Node
    abstract fun copyWithOutputsCollapsedState(isCollapsed: Boolean): Node
    abstract fun isReady(connections: List<Connection>): Boolean

    @Serializable
    @SerialName("capability")
    data class CapabilityNode(
        override val id: Long,
        @Serializable(with = OffsetSerializer::class) override val position: Offset,
        val pluginInfo: PluginInfo,
        val capability: Capability,
        override val inputs: List<InputPort>,
        override val outputs: List<OutputPort>,
        override val isCollapsed: Boolean = false,
        override val isInputsCollapsed: Boolean = false,
        override val isOutputsCollapsed: Boolean = false,
        val isBroken: Boolean = false
    ) : Node() {
        override val title: String get() = capability.name
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
        override fun copyWithId(newId: Long) = copy(id = newId)
        override fun copyWithCollapsedState(isCollapsed: Boolean) = copy(isCollapsed = isCollapsed)
        override fun copyWithInputsCollapsedState(isCollapsed: Boolean) = copy(isInputsCollapsed = isCollapsed)
        override fun copyWithOutputsCollapsedState(isCollapsed: Boolean) = copy(isOutputsCollapsed = isCollapsed)
        override fun copyWithUpdatedInput(portId: String, value: JsonElement?): Node {
            return copy(inputs = inputs.map { input ->
                if (input.id == portId) input.copy(value = value) else input
            })
        }
        override fun isReady(connections: List<Connection>): Boolean {
            if (isBroken) return false
            val parameters = capability.parameters ?: return true
            for ((portId, metadata) in parameters) {
                if (metadata.required) {
                    val inputPort = inputs.find { it.id == portId }
                    val effectiveValue = inputPort?.value ?: inputPort?.defaultValue
                    val providedByValue = inputPort?.dataType?.isProvided(AnySerializer.toJsonElement(effectiveValue)) == true
                    val providedByConnection = connections.any { it.targetNodeId == id && it.targetPortId == portId }
                    if (!providedByValue && !providedByConnection) {
                        return false
                    }
                }
            }
            return true
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
        override val outputs: List<OutputPort>,
        override val isCollapsed: Boolean = false,
        override val isInputsCollapsed: Boolean = false,
        override val isOutputsCollapsed: Boolean = false
    ) : Node() {
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
        override fun copyWithId(newId: Long) = copy(id = newId)
        override fun copyWithCollapsedState(isCollapsed: Boolean) = copy(isCollapsed = isCollapsed)
        override fun copyWithInputsCollapsedState(isCollapsed: Boolean) = copy(isInputsCollapsed = isCollapsed)
        override fun copyWithOutputsCollapsedState(isCollapsed: Boolean) = copy(isOutputsCollapsed = isCollapsed)
        override fun copyWithUpdatedInput(portId: String, value: JsonElement?): Node {
            return copy(inputs = inputs.map { input ->
                if (input.id == portId) input.copy(value = value) else input
            })
        }
        override fun isReady(connections: List<Connection>): Boolean = true
    }

    @Serializable
    @SerialName("flow_input")
    data class FlowInputNode(
        override val id: Long,
        @Serializable(with = OffsetSerializer::class) override val position: Offset,
        override val outputs: List<OutputPort>,
        val constraints: PortConstraints? = null,
        val isList: Boolean = false,
        override val isCollapsed: Boolean = false,
        override val isInputsCollapsed: Boolean = false,
        override val isOutputsCollapsed: Boolean = false
    ) : Node() {
        override val title: String get() = "Flow Input (${outputs.firstOrNull()?.name ?: "input_data"})"
        override val inputs: List<InputPort> = emptyList() // Uses outputs to provide data into the flow
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
        override fun copyWithId(newId: Long) = copy(id = newId)
        override fun copyWithCollapsedState(isCollapsed: Boolean) = copy(isCollapsed = isCollapsed)
        override fun copyWithInputsCollapsedState(isCollapsed: Boolean) = copy(isInputsCollapsed = isCollapsed)
        override fun copyWithOutputsCollapsedState(isCollapsed: Boolean) = copy(isOutputsCollapsed = isCollapsed)
        override fun copyWithUpdatedInput(portId: String, value: JsonElement?): Node = this
        override fun isReady(connections: List<Connection>): Boolean = true
    }

    @Serializable
    @SerialName("flow_output")
    data class FlowOutputNode(
        override val id: Long,
        @Serializable(with = OffsetSerializer::class) override val position: Offset,
        override val inputs: List<InputPort>,
        override val isCollapsed: Boolean = false,
        override val isInputsCollapsed: Boolean = false,
        override val isOutputsCollapsed: Boolean = false
    ) : Node() {
        override val title: String get() = "Flow Output (${inputs.firstOrNull()?.name ?: "output_data"})"
        override val outputs: List<OutputPort> = emptyList() // Uses inputs to collect data from the flow
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
        override fun copyWithId(newId: Long) = copy(id = newId)
        override fun copyWithCollapsedState(isCollapsed: Boolean) = copy(isCollapsed = isCollapsed)
        override fun copyWithInputsCollapsedState(isCollapsed: Boolean) = copy(isInputsCollapsed = isCollapsed)
        override fun copyWithOutputsCollapsedState(isCollapsed: Boolean) = copy(isOutputsCollapsed = isCollapsed)
        override fun copyWithUpdatedInput(portId: String, value: JsonElement?): Node {
            return copy(inputs = inputs.map { input ->
                if (input.id == portId) input.copy(value = value) else input
            })
        }
        override fun isReady(connections: List<Connection>): Boolean = true
    }

    @Serializable
    @SerialName("sub_flow")
    data class SubFlowNode(
        override val id: Long,
        @Serializable(with = OffsetSerializer::class) override val position: Offset,
        val flowName: String,
        override val inputs: List<InputPort>,
        override val outputs: List<OutputPort>,
        val inputMappings: List<SubflowPortMapping> = emptyList(),
        val outputMappings: List<SubflowPortMapping> = emptyList(),
        override val isCollapsed: Boolean = false,
        override val isInputsCollapsed: Boolean = false,
        override val isOutputsCollapsed: Boolean = false
    ) : Node() {
        override val title: String get() = flowName
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
        override fun copyWithId(newId: Long) = copy(id = newId)
        override fun copyWithCollapsedState(isCollapsed: Boolean) = copy(isCollapsed = isCollapsed)
        override fun copyWithInputsCollapsedState(isCollapsed: Boolean) = copy(isInputsCollapsed = isCollapsed)
        override fun copyWithOutputsCollapsedState(isCollapsed: Boolean) = copy(isOutputsCollapsed = isCollapsed)
        override fun copyWithUpdatedInput(portId: String, value: JsonElement?): Node {
            return copy(inputs = inputs.map { input ->
                if (input.id == portId) input.copy(value = value) else input
            })
        }
        override fun isReady(connections: List<Connection>): Boolean = true
    }
}

@Serializable
data class Connection(
    val sourceNodeId: Long,
    val sourcePortId: String,
    val targetNodeId: Long,
    val targetPortId: String,
    val orderIndex: Int? = null
)

@Serializable
data class Flow(
    val name: String,
    val nodes: List<Node> = emptyList(),
    val connections: List<Connection> = emptyList(),
    val version: String = "1.0.0",
    val description: String? = null
) {
    fun getInferredDataTypeForOutput(nodeId: Long, portId: String, fallbackType: DataType): DataType {
        val baseType = fallbackType
        val baseArray = baseType as? DataType.Array
        val baseItems = baseArray?.items

        if (baseType is DataType.Primitive && baseType.primitiveType == org.wip.plugintoolkit.api.PrimitiveType.ANY) {
            val connection = connections.find { it.sourceNodeId == nodeId && it.sourcePortId == portId }
            val targetNode = nodes.find { it.id == connection?.targetNodeId }
            val targetPort = targetNode?.inputs?.find { it.id == connection?.targetPortId }
            return targetPort?.dataType ?: fallbackType
        } else if (baseArray != null && baseItems is DataType.Primitive && baseItems.primitiveType == org.wip.plugintoolkit.api.PrimitiveType.ANY) {
            val connection = connections.find { it.sourceNodeId == nodeId && it.sourcePortId == portId }
            val targetNode = nodes.find { it.id == connection?.targetNodeId }
            val targetPort = targetNode?.inputs?.find { it.id == connection?.targetPortId }
            val targetType = targetPort?.dataType
            if (targetType is DataType.Array) return targetType else return fallbackType
        }
        return fallbackType
    }

    fun getInferredDataTypeForInput(nodeId: Long, portId: String, fallbackType: DataType): DataType {
        val baseType = fallbackType
        val baseArray = baseType as? DataType.Array
        val baseItems = baseArray?.items

        if (baseType is DataType.Primitive && baseType.primitiveType == org.wip.plugintoolkit.api.PrimitiveType.ANY) {
            val connection = connections.find { it.targetNodeId == nodeId && it.targetPortId == portId }
            val sourceNode = nodes.find { it.id == connection?.sourceNodeId }
            val sourcePort = sourceNode?.outputs?.find { it.id == connection?.sourcePortId }
            return sourcePort?.dataType ?: fallbackType
        } else if (baseArray != null && baseItems is DataType.Primitive && baseItems.primitiveType == org.wip.plugintoolkit.api.PrimitiveType.ANY) {
            val connection = connections.find { it.targetNodeId == nodeId && it.targetPortId == portId }
            val sourceNode = nodes.find { it.id == connection?.sourceNodeId }
            val sourcePort = sourceNode?.outputs?.find { it.id == connection?.sourcePortId }
            val sourceType = sourcePort?.dataType
            if (sourceType is DataType.Array) return sourceType else return fallbackType
        }
        return fallbackType
    }

    fun isBroken(activeCapabilities: Set<String>): Boolean {
        val hasBrokenNode = this.nodes.any { it is Node.CapabilityNode && it.isBroken }
        val hasMissingCapability = this.nodes.filterIsInstance<Node.CapabilityNode>().any { it.capability.name !in activeCapabilities }
        val hasNotReadyNode = this.nodes.any { !it.isReady(connections) }
        return hasBrokenNode || hasMissingCapability || hasNotReadyNode
    }

    fun isDestructive(): Boolean {
        return nodes.filterIsInstance<Node.CapabilityNode>().any {
            it.capability.fileAccess?.isDestructive == true
        }
    }

    fun getFileAccess(): org.wip.plugintoolkit.api.FileAccess? {
        val reads = nodes.filterIsInstance<Node.CapabilityNode>().any { it.capability.fileAccess?.readsFiles == true } ||
                    nodes.filterIsInstance<Node.SystemNode>().any { it.systemAction.lowercase() == "load" }
        val writes = nodes.filterIsInstance<Node.CapabilityNode>().any { it.capability.fileAccess?.writesFiles == true } ||
                     nodes.filterIsInstance<Node.SystemNode>().any { it.systemAction.lowercase() == "save" }
        val destructive = isDestructive()
        if (!reads && !writes && !destructive) return null
        return org.wip.plugintoolkit.api.FileAccess(readsFiles = reads, writesFiles = writes, isDestructive = destructive)
    }
}

@Serializable
data class PortConstraints(
    val regex: String? = null,
    val min: Double? = null,
    val max: Double? = null,
    val extensions: List<String>? = null
)
