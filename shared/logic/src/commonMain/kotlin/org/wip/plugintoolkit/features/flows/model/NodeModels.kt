package org.wip.plugintoolkit.features.flows.model

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
import org.wip.plugintoolkit.api.ConditionGroup
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.ParameterConditionEvaluator
import org.wip.plugintoolkit.api.PluginInfo
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.api.parseSemanticTypes
import org.wip.plugintoolkit.features.flows.logic.PathPatternResolver
import org.wip.plugintoolkit.features.plugin.utils.CapabilityLockUtils

@Serializable
data class Offset(val x: Float = 0f, val y: Float = 0f) {
    operator fun plus(other: Offset) = Offset(x + other.x, y + other.y)
    operator fun minus(other: Offset) = Offset(x - other.x, y - other.y)
    operator fun times(factor: Float) = Offset(x * factor, y * factor)
    operator fun div(factor: Float) = Offset(x / factor, y / factor)

    companion object {
        val Zero = Offset(0f, 0f)
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
    val isRequired: Boolean = true,
    override val description: String? = null,
    val isAdvanced: Boolean = false,
    val condition: ConditionGroup? = null
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
            constraints = value.constraints,
            isRequired = value.isRequired,
            isAdvanced = value.isAdvanced,
            condition = value.condition
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
            constraints = migratedConstraints,
            isRequired = surrogate.isRequired,
            isAdvanced = surrogate.isAdvanced,
            condition = surrogate.condition
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
    val isRequired: Boolean = true,
    val description: String? = null,
    val isAdvanced: Boolean = false,
    val condition: ConditionGroup? = null
)

@Serializable(with = OutputPortSerializer::class)
data class OutputPort(
    override val id: String,
    override val name: String,
    override val dataType: DataType,
    override val semanticTypes: List<SemanticType> = emptyList(),
    override val description: String? = null,
    val isAdvanced: Boolean = false,
    val condition: ConditionGroup? = null
) : Port

object OutputPortSerializer : KSerializer<OutputPort> {
    override val descriptor: SerialDescriptor = OutputPortSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: OutputPort) {
        val surrogate = OutputPortSurrogate(
            id = value.id,
            name = value.name,
            description = value.description,
            dataType = value.dataType,
            semanticTypes = value.semanticTypes,
            isAdvanced = value.isAdvanced,
            condition = value.condition
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
            semanticTypes = surrogate.semanticTypes,
            isAdvanced = surrogate.isAdvanced,
            condition = surrogate.condition
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
    val description: String? = null,
    val isAdvanced: Boolean = false,
    val condition: ConditionGroup? = null
)

@Serializable
data class SubflowPortMapping(
    val portId: String,
    val boundaryNodeId: Long
)

/**
 * Common base interface for all visual and interactive elements placed on the Flow board.
 */
interface BoardElement {
    val id: Long
    val position: Offset
    fun copyWithPosition(newPosition: Offset): BoardElement
}

/**
 * Interface for board elements that have an explicit width and height on the canvas.
 */
interface ResizableBoardElement : BoardElement {
    val size: Offset
    fun copyWithSize(newSize: Offset): ResizableBoardElement
}

@Serializable
sealed class Node : BoardElement {
    abstract override val id: Long

    abstract override val position: Offset
    abstract val title: String
    abstract val inputs: List<InputPort>
    abstract val outputs: List<OutputPort>
    abstract val isCollapsed: Boolean
    abstract val isInputsCollapsed: Boolean
    abstract val isOutputsCollapsed: Boolean
    abstract val color: String?

    abstract override fun copyWithPosition(newPosition: Offset): Node
    abstract fun copyWithUpdatedInput(portId: String, value: JsonElement?): Node
    abstract fun copyWithUpdatedInputDefault(portId: String, defaultValue: Any?): Node
    abstract fun copyWithId(newId: Long): Node
    abstract fun copyWithCollapsedState(isCollapsed: Boolean): Node
    abstract fun copyWithInputsCollapsedState(isCollapsed: Boolean): Node
    abstract fun copyWithOutputsCollapsedState(isCollapsed: Boolean): Node
    abstract fun copyWithColor(color: String?): Node
    abstract fun isReady(connections: List<Connection>, settings: Map<String, JsonElement>? = null, locks: Map<String, Boolean>? = null): Boolean

    @Serializable
    @SerialName("capability")
    data class CapabilityNode(
        override val id: Long,
        override val position: Offset,
        val pluginInfo: PluginInfo,
        val capability: Capability,
        override val inputs: List<InputPort>,
        override val outputs: List<OutputPort>,
        override val isCollapsed: Boolean = false,
        override val isInputsCollapsed: Boolean = false,
        override val isOutputsCollapsed: Boolean = false,
        val isBroken: Boolean = false,
        override val color: String? = null
    ) : Node() {
        override val title: String get() = capability.name
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
        override fun copyWithId(newId: Long) = copy(id = newId)
        override fun copyWithCollapsedState(isCollapsed: Boolean) = copy(isCollapsed = isCollapsed)
        override fun copyWithInputsCollapsedState(isCollapsed: Boolean) = copy(isInputsCollapsed = isCollapsed)
        override fun copyWithOutputsCollapsedState(isCollapsed: Boolean) = copy(isOutputsCollapsed = isCollapsed)
        override fun copyWithColor(color: String?): Node = copy(color = color)
        override fun copyWithUpdatedInput(portId: String, value: JsonElement?): Node {
            return copy(inputs = inputs.map { input ->
                if (input.id == portId) input.copy(value = value) else input
            })
        }
        override fun copyWithUpdatedInputDefault(portId: String, defaultValue: Any?): Node {
            return copy(inputs = inputs.map { input ->
                if (input.id == portId) input.copy(defaultValue = defaultValue) else input
            })
        }

        override fun isReady(
            connections: List<Connection>,
            settings: Map<String, JsonElement>?,
            locks: Map<String, Boolean>?
        ): Boolean {
            if (isBroken) return false
            val parameters = capability.parameters ?: return true
            val currentParamElements = inputs.associate { input ->
                input.id to AnySerializer.toJsonElement(input.value ?: input.defaultValue)
            }
            for ((portId, metadata) in parameters) {
                if (!ParameterConditionEvaluator.isSatisfied(metadata.condition, currentParamElements, settings ?: emptyMap(), locks ?: emptyMap())) {
                    continue
                }

                val inputPort = inputs.find { it.id == portId }
                val metaType = metadata.type
                val implicitDefault = when {
                    metaType is DataType.Enum -> metaType.options.firstOrNull()
                    metaType is DataType.Primitive && metaType.primitiveType == org.wip.plugintoolkit.api.PrimitiveType.BOOLEAN -> false
                    else -> null
                }
                val effectiveValue = inputPort?.value
                    ?: inputPort?.defaultValue
                    ?: metadata.defaultValue
                    ?: implicitDefault

                if (metaType is DataType.Enum) {
                    val enumType = metaType
                    val selectedValueStr = effectiveValue?.let {
                        if (it is JsonPrimitive) it.content else it.toString()
                    } ?: (metadata.defaultValue as? JsonPrimitive)?.content ?: enumType.options.firstOrNull()

                    if (selectedValueStr != null) {
                        if (settings != null) {
                            val reqs = enumType.optionRequirements[selectedValueStr]
                            if (reqs != null && reqs.any { reqSetting ->
                                    val s = settings[reqSetting]
                                    s == null || s is JsonNull || (s as? JsonPrimitive)?.content?.isBlank() == true
                                }) {
                                return false
                            }
                        }
                        if (locks != null) {
                            val lockReqs = enumType.optionLockRequirements[selectedValueStr]
                            if (lockReqs != null && lockReqs.any { lockKey ->
                                    !CapabilityLockUtils.isLockSatisfied(lockKey, locks)
                                }) {
                                return false
                            }
                        }
                    }
                }

                if (metadata.required) {
                    val dataType = inputPort?.dataType ?: metadata.type
                    val providedByValue = dataType.isProvided(AnySerializer.toJsonElement(effectiveValue))
                    val providedByConnection = connections.any { it.targetNodeId == id && it.targetPortId == portId }

                    val canBeAutogenerated = metadata.autogeneratedPattern != null &&
                            PathPatternResolver.canResolve(
                                metadata.autogeneratedPattern!!,
                                parameters.keys
                            )

                    if (!providedByValue && !providedByConnection && !canBeAutogenerated) {
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
        override val position: Offset,
        override val title: String,
        val systemAction: String, // e.g., "save", "load"
        override val inputs: List<InputPort>,
        override val outputs: List<OutputPort>,
        override val isCollapsed: Boolean = false,
        override val isInputsCollapsed: Boolean = false,
        override val isOutputsCollapsed: Boolean = false,
        override val color: String? = null
    ) : Node() {
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
        override fun copyWithId(newId: Long) = copy(id = newId)
        override fun copyWithCollapsedState(isCollapsed: Boolean) = copy(isCollapsed = isCollapsed)
        override fun copyWithInputsCollapsedState(isCollapsed: Boolean) = copy(isInputsCollapsed = isCollapsed)
        override fun copyWithOutputsCollapsedState(isCollapsed: Boolean) = copy(isOutputsCollapsed = isCollapsed)
        override fun copyWithColor(color: String?): Node = copy(color = color)
        override fun copyWithUpdatedInput(portId: String, value: JsonElement?): Node {
            return copy(inputs = inputs.map { input ->
                if (input.id == portId) input.copy(value = value) else input
            })
        }
        override fun copyWithUpdatedInputDefault(portId: String, defaultValue: Any?): Node {
            return copy(inputs = inputs.map { input ->
                if (input.id == portId) input.copy(defaultValue = defaultValue) else input
            })
        }

        override fun isReady(
            connections: List<Connection>,
            settings: Map<String, JsonElement>?,
            locks: Map<String, Boolean>?
        ): Boolean {
            if (systemAction.lowercase() == "load") return true
            return inputs.all { input ->
                if (!input.isRequired) return@all true
                val inputType = input.dataType
                val implicitDefault = when {
                    inputType is DataType.Enum -> inputType.options.firstOrNull()
                    inputType is DataType.Primitive && inputType.primitiveType == org.wip.plugintoolkit.api.PrimitiveType.BOOLEAN -> false
                    else -> null
                }
                val effectiveValue = input.value
                    ?: input.defaultValue
                    ?: implicitDefault
                val providedByValue = input.dataType.isProvided(AnySerializer.toJsonElement(effectiveValue))
                val providedByConnection = connections.any { it.targetNodeId == id && it.targetPortId == input.id }
                providedByValue || providedByConnection
            }
        }
    }

    @Serializable
    @SerialName("flow_input")
    data class FlowInputNode(
        override val id: Long,
        override val position: Offset,
        override val outputs: List<OutputPort>,
        val constraints: PortConstraints? = null,
        val isList: Boolean = false,
        val isRequired: Boolean = true,
        @Serializable(with = AnySerializer::class) val defaultValue: Any? = null,
        override val isCollapsed: Boolean = false,
        override val isInputsCollapsed: Boolean = false,
        override val isOutputsCollapsed: Boolean = false,
        override val color: String? = null
    ) : Node() {
        override val title: String get() = "Flow Input (${outputs.firstOrNull()?.name ?: "input_data"})"
        override val inputs: List<InputPort> = emptyList() // Uses outputs to provide data into the flow
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
        override fun copyWithId(newId: Long) = copy(id = newId)
        override fun copyWithCollapsedState(isCollapsed: Boolean) = copy(isCollapsed = isCollapsed)
        override fun copyWithInputsCollapsedState(isCollapsed: Boolean) = copy(isInputsCollapsed = isCollapsed)
        override fun copyWithOutputsCollapsedState(isCollapsed: Boolean) = copy(isOutputsCollapsed = isCollapsed)
        override fun copyWithColor(color: String?): Node = copy(color = color)
        override fun copyWithUpdatedInput(portId: String, value: JsonElement?): Node = this
        override fun copyWithUpdatedInputDefault(portId: String, defaultValue: Any?): Node = copy(defaultValue = defaultValue)
        override fun isReady(
            connections: List<Connection>,
            settings: Map<String, JsonElement>?,
            locks: Map<String, Boolean>?
        ): Boolean = true
    }

    @Serializable
    @SerialName("flow_output")
    data class FlowOutputNode(
        override val id: Long,
        override val position: Offset,
        override val inputs: List<InputPort>,
        override val isCollapsed: Boolean = false,
        override val isInputsCollapsed: Boolean = false,
        override val isOutputsCollapsed: Boolean = false,
        override val color: String? = null
    ) : Node() {
        override val title: String get() = "Flow Output (${inputs.firstOrNull()?.name ?: "output_data"})"
        override val outputs: List<OutputPort> = emptyList() // Uses inputs to collect data from the flow
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
        override fun copyWithId(newId: Long) = copy(id = newId)
        override fun copyWithCollapsedState(isCollapsed: Boolean) = copy(isCollapsed = isCollapsed)
        override fun copyWithInputsCollapsedState(isCollapsed: Boolean) = copy(isInputsCollapsed = isCollapsed)
        override fun copyWithOutputsCollapsedState(isCollapsed: Boolean) = copy(isOutputsCollapsed = isCollapsed)
        override fun copyWithColor(color: String?): Node = copy(color = color)
        override fun copyWithUpdatedInput(portId: String, value: JsonElement?): Node {
            return copy(inputs = inputs.map { input ->
                if (input.id == portId) input.copy(value = value) else input
            })
        }
        override fun copyWithUpdatedInputDefault(portId: String, defaultValue: Any?): Node {
            return copy(inputs = inputs.map { input ->
                if (input.id == portId) input.copy(defaultValue = defaultValue) else input
            })
        }

        override fun isReady(
            connections: List<Connection>,
            settings: Map<String, JsonElement>?,
            locks: Map<String, Boolean>?
        ): Boolean = true
    }

    @Serializable
    @SerialName("sub_flow")
    data class SubFlowNode(
        override val id: Long,
        override val position: Offset,
        val flowName: String,
        override val inputs: List<InputPort>,
        override val outputs: List<OutputPort>,
        val inputMappings: List<SubflowPortMapping> = emptyList(),
        val outputMappings: List<SubflowPortMapping> = emptyList(),
        override val isCollapsed: Boolean = false,
        override val isInputsCollapsed: Boolean = false,
        override val isOutputsCollapsed: Boolean = false,
        override val color: String? = null
    ) : Node() {
        override val title: String get() = flowName
        override fun copyWithPosition(newPosition: Offset) = copy(position = newPosition)
        override fun copyWithId(newId: Long) = copy(id = newId)
        override fun copyWithCollapsedState(isCollapsed: Boolean) = copy(isCollapsed = isCollapsed)
        override fun copyWithInputsCollapsedState(isCollapsed: Boolean) = copy(isInputsCollapsed = isCollapsed)
        override fun copyWithOutputsCollapsedState(isCollapsed: Boolean) = copy(isOutputsCollapsed = isCollapsed)
        override fun copyWithColor(color: String?): Node = copy(color = color)
        override fun copyWithUpdatedInput(portId: String, value: JsonElement?): Node {
            return copy(inputs = inputs.map { input ->
                if (input.id == portId) input.copy(value = value) else input
            })
        }
        override fun copyWithUpdatedInputDefault(portId: String, defaultValue: Any?): Node {
            return copy(inputs = inputs.map { input ->
                if (input.id == portId) input.copy(defaultValue = defaultValue) else input
            })
        }

        override fun isReady(
            connections: List<Connection>,
            settings: Map<String, JsonElement>?,
            locks: Map<String, Boolean>?
        ): Boolean = true
    }
}

@Serializable
data class FlowJunction(
    override val id: Long,
    override val position: Offset,
    val color: String? = null
) : BoardElement {
    override fun copyWithPosition(newPosition: Offset): FlowJunction = copy(position = newPosition)
}

@Serializable
data class FlowGroup(
    override val id: Long,
    val title: String,
    override val position: Offset,
    override val size: Offset,
    val color: String? = null,
    val isCollapsed: Boolean = false,
    val nodeIds: List<Long> = emptyList()
) : ResizableBoardElement {
    override fun copyWithPosition(newPosition: Offset): FlowGroup = copy(position = newPosition)
    override fun copyWithSize(newSize: Offset): FlowGroup = copy(size = newSize)
}

@Serializable
data class FlowLabel(
    override val id: Long,
    val text: String,
    override val position: Offset,
    val color: String? = null,
    val fontSize: Float = 14f
) : BoardElement {
    override fun copyWithPosition(newPosition: Offset): FlowLabel = copy(position = newPosition)
}

@Serializable
data class Connection(
    val sourceNodeId: Long,
    val sourcePortId: String,
    val targetNodeId: Long,
    val targetPortId: String,
    val orderIndex: Int? = null,
    val color: String? = null,
    val waypoints: List<Offset> = emptyList(),
    val junctionIds: List<Long> = emptyList(),
    val sourceJunctionId: Long? = null,
    val targetJunctionId: Long? = null,
    val floatingTarget: Offset? = null,
    val isStructured: Boolean = false
) {
    val isFloating: Boolean
        get() = floatingTarget != null ||
                (targetNodeId == FLOATING_NODE_ID && targetJunctionId == null) ||
                (sourceNodeId == FLOATING_NODE_ID && sourceJunctionId == null)

    companion object {
        const val FLOATING_NODE_ID: Long = -1L
        const val FLOATING_PORT_ID: String = ""

        fun createFloating(
            sourceNodeId: Long,
            sourcePortId: String,
            floatingTarget: Offset,
            color: String? = null,
            junctionIds: List<Long> = emptyList(),
            waypoints: List<Offset> = emptyList(),
            isStructured: Boolean = false
        ): Connection = Connection(
            sourceNodeId = sourceNodeId,
            sourcePortId = sourcePortId,
            targetNodeId = FLOATING_NODE_ID,
            targetPortId = FLOATING_PORT_ID,
            color = color,
            waypoints = waypoints,
            junctionIds = junctionIds,
            floatingTarget = floatingTarget,
            isStructured = isStructured
        )
    }
}

@Serializable
data class Flow(
    val name: String,
    val nodes: List<Node> = emptyList(),
    val connections: List<Connection> = emptyList(),
    val groups: List<FlowGroup> = emptyList(),
    val labels: List<FlowLabel> = emptyList(),
    val junctions: List<FlowJunction> = emptyList(),
    val version: String = "1.0.0",
    val description: String? = null,
    val defaultValues: Map<String, JsonElement> = emptyMap()
) {
    fun allBoardElements(): List<BoardElement> = nodes + groups + labels + junctions
    fun findBoardElement(id: Long): BoardElement? =
        nodes.find { it.id == id }
            ?: groups.find { it.id == id }
            ?: labels.find { it.id == id }
            ?: junctions.find { it.id == id }

    fun withUpdatedBoardElement(element: BoardElement): Flow = when (element) {
        is Node -> copy(nodes = nodes.map { if (it.id == element.id) element else it })
        is FlowGroup -> copy(groups = groups.map { if (it.id == element.id) element else it })
        is FlowLabel -> copy(labels = labels.map { if (it.id == element.id) element else it })
        is FlowJunction -> copy(junctions = junctions.map { if (it.id == element.id) element else it })
        else -> this
    }

    fun getEffectiveConnections(): List<Connection> {
        val nonFloating = connections.filter { !it.isFloating }
        if (nonFloating.none { it.sourceJunctionId != null || it.targetJunctionId != null }) {
            return nonFloating
        }

        val directConnections = nonFloating.filter { it.sourceJunctionId == null && it.targetJunctionId == null }
        val outFromJunction = nonFloating.filter { it.sourceJunctionId != null }.groupBy { it.sourceJunctionId }
        val inToJunction = nonFloating.filter { it.targetJunctionId != null }

        val resolved = mutableListOf<Connection>()
        resolved.addAll(directConnections)

        fun traceJunction(
            currentJunctionId: Long,
            originSourceNodeId: Long,
            originSourcePortId: String,
            color: String?,
            accumulatedJunctions: List<Long>,
            visited: Set<Long>
        ) {
            if (currentJunctionId in visited) return
            val nextVisited = visited + currentJunctionId
            val outgoing = outFromJunction[currentJunctionId].orEmpty()
            for (outConn in outgoing) {
                val nextJunctions = accumulatedJunctions + currentJunctionId
                if (outConn.targetJunctionId != null) {
                    traceJunction(
                        outConn.targetJunctionId,
                        originSourceNodeId,
                        originSourcePortId,
                        outConn.color ?: color,
                        nextJunctions,
                        nextVisited
                    )
                } else if (outConn.targetNodeId >= 0L) {
                    resolved.add(
                        Connection(
                            sourceNodeId = originSourceNodeId,
                            sourcePortId = originSourcePortId,
                            targetNodeId = outConn.targetNodeId,
                            targetPortId = outConn.targetPortId,
                            orderIndex = outConn.orderIndex,
                            color = outConn.color ?: color,
                            junctionIds = nextJunctions
                        )
                    )
                }
            }
        }

        for (inConn in inToJunction) {
            if (inConn.sourceNodeId >= 0L && inConn.targetJunctionId != null) {
                traceJunction(
                    currentJunctionId = inConn.targetJunctionId,
                    originSourceNodeId = inConn.sourceNodeId,
                    originSourcePortId = inConn.sourcePortId,
                    color = inConn.color,
                    accumulatedJunctions = emptyList(),
                    visited = emptySet()
                )
            }
        }

        return resolved
    }
    fun getInferredDataTypeForOutput(nodeId: Long, portId: String, fallbackType: DataType): DataType {
        val baseArray = fallbackType as? DataType.Array
        val baseItems = baseArray?.items

        if (fallbackType is DataType.Primitive && fallbackType.primitiveType == org.wip.plugintoolkit.api.PrimitiveType.ANY) {
            val connection = connections.find { it.sourceNodeId == nodeId && it.sourcePortId == portId }
            val targetNode = nodes.find { it.id == connection?.targetNodeId }
            val targetPort = targetNode?.inputs?.find { it.id == connection?.targetPortId }
            return targetPort?.dataType ?: fallbackType
        } else if (baseArray != null && baseItems is DataType.Primitive && baseItems.primitiveType == org.wip.plugintoolkit.api.PrimitiveType.ANY) {
            val connection = connections.find { it.sourceNodeId == nodeId && it.sourcePortId == portId }
            val targetNode = nodes.find { it.id == connection?.targetNodeId }
            val targetPort = targetNode?.inputs?.find { it.id == connection?.targetPortId }
            val targetType = targetPort?.dataType
            return targetType as? DataType.Array ?: fallbackType
        }
        return fallbackType
    }

    fun getInferredDataTypeForInput(nodeId: Long, portId: String, fallbackType: DataType): DataType {
        val baseArray = fallbackType as? DataType.Array
        val baseItems = baseArray?.items

        if (fallbackType is DataType.Primitive && fallbackType.primitiveType == org.wip.plugintoolkit.api.PrimitiveType.ANY) {
            val connection = connections.find { it.targetNodeId == nodeId && it.targetPortId == portId }
            val sourceNode = nodes.find { it.id == connection?.sourceNodeId }
            val sourcePort = sourceNode?.outputs?.find { it.id == connection?.sourcePortId }
            return sourcePort?.dataType ?: fallbackType
        } else if (baseArray != null && baseItems is DataType.Primitive && baseItems.primitiveType == org.wip.plugintoolkit.api.PrimitiveType.ANY) {
            val connection = connections.find { it.targetNodeId == nodeId && it.targetPortId == portId }
            val sourceNode = nodes.find { it.id == connection?.sourceNodeId }
            val sourcePort = sourceNode?.outputs?.find { it.id == connection?.sourcePortId }
            val sourceType = sourcePort?.dataType
            return sourceType as? DataType.Array ?: fallbackType
        }
        return fallbackType
    }

    fun isBroken(
        activeCapabilities: Set<String>,
        settingsMap: Map<Long, Map<String, JsonElement>>? = null,
        locksMap: Map<Long, Map<String, Boolean>>? = null
    ): Boolean {
        val hasBrokenNode = this.nodes.any { it is Node.CapabilityNode && it.isBroken }
        val hasMissingCapability =
            this.nodes.filterIsInstance<Node.CapabilityNode>().any { it.capability.name !in activeCapabilities }
        val hasMissingRequiredPort = this.nodes.filterIsInstance<Node.CapabilityNode>().any { capNode ->
            capNode.capability.parameters?.any { (key, meta) ->
                meta.required &&
                    meta.role != org.wip.plugintoolkit.api.ParameterRole.OUTPUT_LOCATION &&
                    meta.defaultValue == null &&
                    meta.autogeneratedPattern == null &&
                    capNode.inputs.none { it.id == key }
            } == true
        }
        val hasNotReadyNode = this.nodes.any { node ->
            val settings = settingsMap?.get(node.id)
            val locks = locksMap?.get(node.id)
            !node.isReady(connections, settings, locks)
        }
        return hasBrokenNode || hasMissingCapability || hasMissingRequiredPort || hasNotReadyNode
    }

    fun isDestructive(): Boolean {
        return nodes.filterIsInstance<Node.CapabilityNode>().any {
            it.capability.fileAccess?.isDestructive == true
        }
    }

    fun getFileAccess(): org.wip.plugintoolkit.api.FileAccess? {
        val reads =
            nodes.filterIsInstance<Node.CapabilityNode>().any { it.capability.fileAccess?.readsFiles == true } ||
                    nodes.filterIsInstance<Node.SystemNode>().any { it.systemAction.lowercase() == "load" }
        val writes =
            nodes.filterIsInstance<Node.CapabilityNode>().any { it.capability.fileAccess?.writesFiles == true } ||
                    nodes.filterIsInstance<Node.SystemNode>().any { it.systemAction.lowercase() == "save" }
        val destructive = isDestructive()
        if (!reads && !writes && !destructive) return null
        return org.wip.plugintoolkit.api.FileAccess(
            readsFiles = reads,
            writesFiles = writes,
            isDestructive = destructive
        )
    }
}

@Serializable
data class PortConstraints(
    val regex: String? = null,
    val min: Double? = null,
    val max: Double? = null,
    val extensions: List<String>? = null
)
