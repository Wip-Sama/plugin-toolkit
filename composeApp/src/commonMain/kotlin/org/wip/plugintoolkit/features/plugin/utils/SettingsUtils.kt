package org.wip.plugintoolkit.features.plugin.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.ParameterConstraints
import org.wip.plugintoolkit.api.PrimitiveType

object SettingsUtils {

    /**
     * Validates a parameter value against its metadata constraints.
     *
     * @param value The raw string value from the text field.
     * @param isRequired Whether the parameter is required.
     * @param isArray Whether the parameter is a list/array type.
     * @param constraints The parameter constraints (regex, minLength, etc.).
     * @return null if the value is valid, or an error message string if validation fails.
     */
    fun validateParameter(
        value: String,
        isRequired: Boolean,
        isArray: Boolean,
        constraints: ParameterConstraints?
    ): String? {
        if (isRequired && value.isBlank()) return "Required"
        if (value.isBlank()) return null

        if (constraints == null) return null

        val minLength = constraints.minLength
        val maxLength = constraints.maxLength
        val regex = constraints.regex
        val minValue = constraints.minValue
        val maxValue = constraints.maxValue

        val items = if (isArray) {
            value.split(",").map { it.trim() }
        } else {
            listOf(value)
        }

        for (item in items) {
            if (isArray && item.isEmpty()) continue

            if (minLength != null && item.length < minLength) {
                return if (isArray) "Each item must be >= $minLength chars" else "Minimum length is $minLength"
            }
            if (maxLength != null && item.length > maxLength) {
                return if (isArray) "Each item must be <= $maxLength chars" else "Maximum length is $maxLength"
            }
            if (!regex.isNullOrEmpty()) {
                try {
                    if (!Regex(regex).matches(item)) {
                        return if (isArray) "Item '$item' does not match format" else "Does not match required format"
                    }
                } catch (_: Exception) {
                    return "Invalid format pattern"
                }
            }
            val numVal = item.toDoubleOrNull()
            if (numVal != null) {
                if (minValue != null && numVal < minValue) {
                    return "Value must be >= $minValue"
                }
                if (maxValue != null && numVal > maxValue) {
                    return "Value must be <= $maxValue"
                }
            }
        }
        return null
    }

    /**
     * Validates all parameters of a capability against their metadata constraints.
     * @return A map of parameter name to error message for each invalid parameter, or empty if all valid.
     */
    fun validateAllParameters(
        parameterValues: Map<String, String>,
        parameters: Map<String, org.wip.plugintoolkit.api.ParameterMetadata>?
    ): Map<String, String> {
        if (parameters == null) return emptyMap()
        val errors = mutableMapOf<String, String>()
        for ((name, meta) in parameters) {
            val value = parameterValues[name] ?: ""
            val isArray = meta.type is DataType.Array
            val error = validateParameter(value, meta.required, isArray, meta.constraints)
            if (error != null) {
                errors[name] = error
            }
        }
        return errors
    }
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
