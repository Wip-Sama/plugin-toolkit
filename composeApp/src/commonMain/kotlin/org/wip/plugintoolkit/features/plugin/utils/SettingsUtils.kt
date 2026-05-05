package org.wip.plugintoolkit.features.plugin.utils

import kotlinx.serialization.json.*
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType

object SettingsUtils {
    fun jsonToString(element: JsonElement?, type: DataType): String {
        if (element == null || element is JsonNull) return ""
        return when (type) {
            is DataType.Primitive -> {
                if (element is JsonPrimitive) {
                    if (type.primitiveType == PrimitiveType.STRING) element.content else element.toString()
                } else element.toString()
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
                try {
                    Json.decodeFromString<JsonArray>(value)
                } catch (e: Exception) {
                    JsonArray(value.split(",").map { JsonPrimitive(it.trim()) })
                }
            }
            else -> JsonPrimitive(value)
        }
    }
}
