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

    fun DataType.getDepth(): Int {
        return when (this) {
            is DataType.Array -> 1 + this.items.getDepth()
            else -> 0
        }
    }

    fun extractMatchingParentheses(value: String): List<String> {
        val results = mutableListOf<String>()
        var depth = 0
        var startIndex = -1
        for (i in value.indices) {
            val c = value[i]
            if (c == '(' || c == '[') {
                if (depth == 0) {
                    startIndex = i + 1
                }
                depth++
            } else if (c == ')' || c == ']') {
                depth--
                if (depth == 0 && startIndex != -1) {
                    results.add(value.substring(startIndex, i))
                    startIndex = -1
                }
            }
        }
        return results
    }

    fun splitArrayValue(value: String, type: DataType.Array): List<String> {
        val cleaned = value.trim()
        if (cleaned.isEmpty()) return emptyList()

        val isNested = type.items is DataType.Array
        return if (isNested) {
            val blocks = extractMatchingParentheses(cleaned)
            if (blocks.isNotEmpty()) {
                blocks
            } else {
                listOf(cleaned)
            }
        } else {
            if ((cleaned.startsWith("(") && cleaned.endsWith(")")) || (cleaned.startsWith("[") && cleaned.endsWith("]"))) {
                val blocks = extractMatchingParentheses(cleaned)
                if (blocks.isNotEmpty()) {
                    return blocks.flatMap { it.split(",").map { item -> item.trim() }.filter { item -> item.isNotEmpty() } }
                }
            }
            cleaned.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    fun getLeafStrings(value: String, type: DataType): List<String> {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return emptyList()

        if (trimmed.startsWith("[") && trimmed.endsWith("]") && !trimmed.contains("(") && !trimmed.contains(")")) {
            return try {
                val jsonArray = Json.decodeFromString<JsonArray>(trimmed)
                if (type is DataType.Array) {
                    jsonArray.flatMap { getLeafStrings(jsonToString(it, type.items), type.items) }
                } else {
                    listOf(trimmed)
                }
            } catch (e: Exception) {
                val innerValue = trimmed.removePrefix("[").removeSuffix("]").trim()
                splitAndGetLeaves(innerValue, type)
            }
        }
        val innerValue = if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed.removePrefix("[").removeSuffix("]").trim()
        } else {
            trimmed
        }
        return splitAndGetLeaves(innerValue, type)
    }

    private fun splitAndGetLeaves(value: String, type: DataType): List<String> {
        if (type !is DataType.Array) return listOf(value)
        val parts = splitArrayValue(value, type)
        return parts.flatMap { getLeafStrings(it, type.items) }
    }

    /**
     * Validates a parameter value against its metadata constraints.
     *
     * @param value The raw string value from the text field.
     * @param isRequired Whether the parameter is required.
     * @param type The DataType of the parameter.
     * @param constraints The parameter constraints (regex, minLength, etc.).
     * @return null if the value is valid, or an error message string if validation fails.
     */
    fun validateParameter(
        value: String,
        isRequired: Boolean,
        type: DataType,
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

        val isArray = type is DataType.Array
        val items = if (isArray) {
            getLeafStrings(value, type)
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
     * Validates a parameter value against its metadata constraints.
     *
     * @param value The raw string value from the text field.
     * @param isRequired Whether the parameter is required.
     * @param isArray Whether the parameter is a list/array type.
     * @param constraints The parameter constraints (regex, minLength, etc.).
     * @return null if the value is valid, or an error message string if validation fails.
     */
    @Deprecated("Use overload that takes DataType instead of Boolean isArray", ReplaceWith("validateParameter(value, isRequired, type, constraints)"))
    fun validateParameter(
        value: String,
        isRequired: Boolean,
        isArray: Boolean,
        constraints: ParameterConstraints?
    ): String? {
        val type = if (isArray) {
            DataType.Array(DataType.Primitive(PrimitiveType.ANY))
        } else {
            DataType.Primitive(PrimitiveType.ANY)
        }
        return validateParameter(value, isRequired, type, constraints)
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
            val error = validateParameter(value, meta.required, meta.type, meta.constraints)
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
                    val isNested = type.items is DataType.Array
                    if (isNested) {
                        element.map { "(${jsonToString(it, type.items)})" }.joinToString(", ")
                    } else {
                        element.map { jsonToString(it, type.items) }.joinToString(",")
                    }
                } else {
                    element.toString()
                }
            }
            else -> element.toString()
        }
    }

    fun stringToJson(value: String, type: DataType): JsonElement {
        if (value.isEmpty()) {
            return when (type) {
                is DataType.Primitive -> {
                    if (type.primitiveType == PrimitiveType.STRING) {
                        JsonPrimitive("")
                    } else {
                        JsonNull
                    }
                }
                is DataType.Array -> JsonArray(emptyList())
                else -> JsonNull
            }
        }
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
                val elements = if (cleanedValue.startsWith("[") && cleanedValue.endsWith("]") && !cleanedValue.contains("(") && !cleanedValue.contains(")")) {
                    try {
                        val parsed = Json.decodeFromString<JsonArray>(cleanedValue)
                        parsed.map { stringToJson(jsonToString(it, type.items), type.items) }
                    } catch (e: Exception) {
                        val innerValue = cleanedValue.removePrefix("[").removeSuffix("]").trim()
                        val parts = splitArrayValue(innerValue, type)
                        parts.map { stringToJson(it.trim(), type.items) }
                    }
                } else {
                    val innerValue = if (cleanedValue.startsWith("[") && cleanedValue.endsWith("]")) {
                        cleanedValue.removePrefix("[").removeSuffix("]").trim()
                    } else {
                        cleanedValue
                    }
                    val parts = splitArrayValue(innerValue, type)
                    parts.map { stringToJson(it.trim(), type.items) }
                }
                JsonArray(elements)
            }
            else -> JsonPrimitive(value)
        }
    }
}
