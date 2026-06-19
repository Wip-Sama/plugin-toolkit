package org.wip.plugintoolkit.features.job.logic

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PluginManifest
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.viewmodel.SystemNodesRegistry

object FlowTypeInferenceCache {
    private val mutex = Mutex()
    private val cache = mutableMapOf<Int, Map<Pair<Long, String>, DataType>>()

    suspend fun getOrCreate(
        flow: Flow,
        compute: suspend () -> Map<Pair<Long, String>, DataType>
    ): Map<Pair<Long, String>, DataType> {
        val key = flow.hashCode()
        mutex.withLock {
            val cached = cache[key]
            if (cached != null) return cached
            val computed = compute()
            cache[key] = computed
            return computed
        }
    }
}

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

fun fromJsonElement(je: JsonElement): Any? {
    return when (je) {
        is JsonNull -> null
        is JsonPrimitive -> {
            if (je.isString) {
                je.content
            } else {
                je.booleanOrNull ?: je.intOrNull ?: je.longOrNull ?: je.doubleOrNull ?: je.content
            }
        }

        is JsonArray -> {
            je.map { fromJsonElement(it) }
        }

        is JsonObject -> {
            je.entries.associate { it.key to fromJsonElement(it.value) }
        }
    }
}

fun runRuntimeTypeInference(flow: Flow): Map<Pair<Long, String>, DataType> {
    val inferred = mutableMapOf<Pair<Long, String>, DataType>()
    flow.nodes.forEach { node ->
        node.inputs.forEach { port -> inferred[Pair(node.id, port.id)] = port.dataType }
        node.outputs.forEach { port -> inferred[Pair(node.id, port.id)] = port.dataType }
    }
    var changed = true
    var iteration = 0
    while (changed && iteration < 10) {
        changed = false
        iteration++
        flow.connections.forEach { conn ->
            val srcKey = Pair(conn.sourceNodeId, conn.sourcePortId)
            val tgtKey = Pair(conn.targetNodeId, conn.targetPortId)
            val srcType = inferred[srcKey]
            val tgtType = inferred[tgtKey]
            if (srcType != null && tgtType != null) {
                if (srcType is DataType.Primitive && srcType.primitiveType == PrimitiveType.ANY &&
                    !(tgtType is DataType.Primitive && tgtType.primitiveType == PrimitiveType.ANY)
                ) {
                    inferred[srcKey] = tgtType
                    changed = true
                }
                if (tgtType is DataType.Primitive && tgtType.primitiveType == PrimitiveType.ANY &&
                    !(srcType is DataType.Primitive && srcType.primitiveType == PrimitiveType.ANY)
                ) {
                    inferred[tgtKey] = srcType
                    changed = true
                }
            }
        }
        flow.nodes.forEach { node ->
            if (node is org.wip.plugintoolkit.features.flows.model.Node.SystemNode) {
                if (SystemNodesRegistry.propagateTypes(node, inferred)) {
                    changed = true
                }
            }
        }
    }
    return inferred
}

fun convertValue(value: Any?, targetType: DataType): Any? {
    if (value == null) return null
    if (targetType !is DataType.Primitive) return value

    val targetPrimitive = targetType.primitiveType
    return when (targetPrimitive) {
        PrimitiveType.STRING -> value.toString()
        PrimitiveType.INT -> {
            if (value is Number) value.toInt()
            else {
                val str = value.toString().trim()
                str.toIntOrNull() ?: throw Exception("Failed to convert '$value' to Int")
            }
        }

        PrimitiveType.DOUBLE -> {
            if (value is Number) value.toDouble()
            else {
                val str = value.toString().trim()
                str.toDoubleOrNull() ?: throw Exception("Failed to convert '$value' to Double")
            }
        }

        PrimitiveType.BOOLEAN -> {
            if (value is Boolean) value
            else if (value is Number) {
                value.toInt() != 0
            } else {
                val str = value.toString().trim().lowercase()
                if (str == "true" || str == "1") true
                else if (str == "false" || str == "0") false
                else {
                    val intVal = str.toIntOrNull()
                    if (intVal != null) {
                        intVal != 0
                    } else {
                        val doubleVal = str.toDoubleOrNull()
                        if (doubleVal != null) {
                            doubleVal.toInt() != 0
                        } else {
                            throw Exception("Failed to convert '$value' to Boolean")
                        }
                    }
                }
            }
        }

        PrimitiveType.ANY -> value
        PrimitiveType.UNIT -> Unit
    }
}

fun validateCapabilityParameters(
    manifest: PluginManifest,
    capabilityName: String,
    parameters: Map<String, JsonElement>
) {
    val capability = manifest.capabilities.find { it.name == capabilityName } ?: return
    val paramMetadataMap = capability.parameters ?: return

    for ((paramName, metadata) in paramMetadataMap) {
        val valueElement = parameters[paramName]

        // Check if required
        if (metadata.required) {
            if (valueElement == null || valueElement is JsonNull) {
                throw IllegalArgumentException("Parameter '$paramName' is required but was not provided.")
            }
            val metaType = metadata.type
            if (metaType is DataType.Primitive && metaType.primitiveType == PrimitiveType.STRING) {
                if (valueElement is JsonPrimitive && valueElement.content.isBlank()) {
                    throw IllegalArgumentException("Parameter '$paramName' is required but was empty.")
                }
            }
        }

        if (valueElement == null || valueElement is JsonNull) {
            continue
        }

        val constraints = metadata.constraints ?: continue
        val regex = constraints.regex
        val minLength = constraints.minLength
        val maxLength = constraints.maxLength
        val minValue = constraints.minValue
        val maxValue = constraints.maxValue

        // Extract values to validate
        val valuesToValidate = getLeafPrimitives(valueElement, metadata.type)

        for (valStr in valuesToValidate) {
            if (minLength != null && valStr.length < minLength) {
                throw IllegalArgumentException("Parameter '$paramName' value '$valStr' violates minLength constraint of $minLength.")
            }
            if (maxLength != null && valStr.length > maxLength) {
                throw IllegalArgumentException("Parameter '$paramName' value '$valStr' violates maxLength constraint of $maxLength.")
            }
            if (!regex.isNullOrEmpty()) {
                try {
                    val pattern = Regex(regex)
                    if (!pattern.matches(valStr)) {
                        throw IllegalArgumentException("Parameter '$paramName' value '$valStr' does not match the required format: $regex")
                    }
                } catch (e: Exception) {
                    throw IllegalArgumentException("Parameter '$paramName' has an invalid regex constraint pattern: $regex")
                }
            }

            // If numeric, validate min/max values
            val doubleVal = valStr.toDoubleOrNull()
            if (doubleVal != null) {
                if (minValue != null && doubleVal < minValue) {
                    throw IllegalArgumentException("Parameter '$paramName' value $doubleVal violates minValue constraint of $minValue.")
                }
                if (maxValue != null && doubleVal > maxValue) {
                    throw IllegalArgumentException("Parameter '$paramName' value $doubleVal violates maxValue constraint of $maxValue.")
                }
            } else if (minValue != null || maxValue != null) {
                val isNumericType = when (val t = metadata.type) {
                    is DataType.Primitive -> t.primitiveType == PrimitiveType.INT || t.primitiveType == PrimitiveType.DOUBLE
                    is DataType.Array -> {
                        val itemType = t.items
                        itemType is DataType.Primitive && (itemType.primitiveType == PrimitiveType.INT || itemType.primitiveType == PrimitiveType.DOUBLE)
                    }

                    else -> false
                }
                if (isNumericType && valStr.isNotEmpty()) {
                    throw IllegalArgumentException("Parameter '$paramName' value '$valStr' is not a valid number.")
                }
            }
        }
    }
}

private fun getLeafPrimitives(element: JsonElement, type: DataType): List<String> {
    return when (type) {
        is DataType.Array -> {
            when (element) {
                is JsonArray -> {
                    element.flatMap { getLeafPrimitives(it, type.items) }
                }

                is JsonPrimitive -> {
                    val parts =
                        org.wip.plugintoolkit.features.plugin.utils.SettingsUtils.splitArrayValue(element.content, type)
                    parts.flatMap {
                        getLeafPrimitives(JsonPrimitive(it), type.items)
                    }
                }

                else -> emptyList()
            }
        }

        else -> {
            if (element is JsonPrimitive) listOf(element.content) else emptyList()
        }
    }
}
