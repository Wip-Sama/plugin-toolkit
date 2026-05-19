package org.wip.plugintoolkit.features.plugin.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType

object SettingsUtils {
    fun jsonToString(element: JsonElement?, type: DataType): String {
        if (element == null || element is JsonNull) return ""
        return when (type) {
            is DataType.Primitive -> {
                if (element is JsonPrimitive) {
                    element.content
                } else {
                    element.toString()
                }
            }
            is DataType.Enum -> {
                if (element is JsonPrimitive) {
                    element.content
                } else {
                    element.toString()
                }
            }
            is DataType.Array -> {
                if (element is JsonArray) {
                    element.map { jsonToString(it, type.items) }.joinToString(",")
                } else {
                    element.toString()
                }
            }
            else -> element.toString()
        }
    }

    fun stringToJson(value: String, type: DataType): JsonElement {
        if (value.isEmpty()) return JsonNull
        return when (type) {
            is DataType.Primitive -> {
                when (type.primitiveType) {
                    PrimitiveType.BOOLEAN -> JsonPrimitive(value.toBooleanStrictOrNull() ?: false)
                    PrimitiveType.INT -> JsonPrimitive(value.toLongOrNull() ?: 0L)
                    PrimitiveType.DOUBLE -> JsonPrimitive(value.toDoubleOrNull() ?: 0.0)
                    else -> JsonPrimitive(value)
                }
            }
            is DataType.Enum -> JsonPrimitive(value)
            is DataType.Array -> {
                val cleanedValue = value.trim()
                val elements = if (cleanedValue.startsWith("[") && cleanedValue.endsWith("]")) {
                    try {
                        val parsed = Json.decodeFromString<JsonArray>(cleanedValue)
                        parsed.map { stringToJson(jsonToString(it, type.items), type.items) }
                    } catch (e: Exception) {
                        cleanedValue.removePrefix("[").removeSuffix("]").split(",").map { stringToJson(it.trim(), type.items) }
                    }
                } else {
                    cleanedValue.split(",").map { stringToJson(it.trim(), type.items) }
                }
                JsonArray(elements)
            }
            else -> JsonPrimitive(value)
        }
    }
}
