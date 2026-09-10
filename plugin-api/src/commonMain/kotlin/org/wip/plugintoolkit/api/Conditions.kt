package org.wip.plugintoolkit.api

import kotlin.jvm.JvmOverloads
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

object ConditionOperatorSerializer : KSerializer<ConditionOperator> by createSafeEnumSerializer("ConditionOperator", ConditionOperator.EQUALS)

@Serializable(with = ConditionOperatorSerializer::class)
enum class ConditionOperator {
    EQUALS,
    NOT_EQUALS,
    IN,
    NOT_IN,
    GREATER_THAN,
    LESS_THAN,
    GREATER_OR_EQUAL,
    LESS_OR_EQUAL,
    REGEX_MATCH,
    IS_SET,
    IS_NOT_BLANK
}

object ConditionSourceSerializer : KSerializer<ConditionSource> by createSafeEnumSerializer("ConditionSource", ConditionSource.PARAMETER)

@Serializable(with = ConditionSourceSerializer::class)
enum class ConditionSource {
    PARAMETER,
    SETTING,
    LOCK
}

/**
 * A single condition that tests a parameter, setting, or lock against expected value(s).
 */
@Serializable
data class ParameterCondition @JvmOverloads constructor(
    val source: ConditionSource = ConditionSource.PARAMETER,
    val target: String,
    val operator: ConditionOperator = ConditionOperator.EQUALS,
    val value: String = "",
    val values: List<String> = emptyList()
)

/**
 * A group of conditions combined with AND or OR logic.
 *
 * @property conditions The list of conditions to evaluate.
 * @property isOr If true, evaluates with OR logic (satisfied if ANY condition matches).
 *                If false, evaluates with AND logic (satisfied if ALL conditions match).
 */
@Serializable
data class ConditionGroup @JvmOverloads constructor(
    val conditions: List<ParameterCondition> = emptyList(),
    val isOr: Boolean = false
) {
    companion object {
        val EMPTY = ConditionGroup(emptyList(), false)

        fun of(vararg conditions: ParameterCondition): ConditionGroup {
            return ConditionGroup(conditions.toList(), isOr = false)
        }

        fun anyOf(vararg conditions: ParameterCondition): ConditionGroup {
            return ConditionGroup(conditions.toList(), isOr = true)
        }
    }
}

/**
 * Evaluates parameter condition groups against active parameter values, settings, and lock states.
 */
object ParameterConditionEvaluator {

    fun isSatisfied(
        conditionGroup: ConditionGroup?,
        parameters: Map<String, JsonElement>,
        settings: Map<String, JsonElement> = emptyMap(),
        locks: Map<String, Boolean> = emptyMap()
    ): Boolean {
        if (conditionGroup == null || conditionGroup.conditions.isEmpty()) {
            return true
        }

        return if (conditionGroup.isOr) {
            conditionGroup.conditions.any { evaluateSingle(it, parameters, settings, locks) }
        } else {
            conditionGroup.conditions.all { evaluateSingle(it, parameters, settings, locks) }
        }
    }

    private fun evaluateSingle(
        condition: ParameterCondition,
        parameters: Map<String, JsonElement>,
        settings: Map<String, JsonElement>,
        locks: Map<String, Boolean>
    ): Boolean {
        return when (condition.source) {
            ConditionSource.LOCK -> {
                val lockValue = locks[condition.target] ?: false
                val expected = condition.value.toBooleanStrictOrNull() ?: true
                if (condition.operator == ConditionOperator.NOT_EQUALS) {
                    lockValue != expected
                } else {
                    lockValue == expected
                }
            }

            ConditionSource.SETTING -> {
                val element = resolveJsonPath(settings, condition.target)
                evaluateElement(element, condition)
            }

            ConditionSource.PARAMETER -> {
                val element = resolveJsonPath(parameters, condition.target)
                evaluateElement(element, condition)
            }
        }
    }

    /**
     * Resolves a JSON element from a map using direct key lookup or dot notation path (e.g. "config.provider").
     */
    fun resolveJsonPath(map: Map<String, JsonElement>, path: String): JsonElement? {
        if (map.containsKey(path)) return map[path]
        if (!path.contains('.')) return map[path]

        val segments = path.split('.')
        var current: JsonElement = map[segments[0]] ?: return null
        for (i in 1 until segments.size) {
            current = (current as? JsonObject)?.get(segments[i]) ?: return null
        }
        return current
    }

    private fun evaluateElement(
        element: JsonElement?,
        condition: ParameterCondition
    ): Boolean {
        val isNull = element == null || element is JsonNull

        if (condition.operator == ConditionOperator.IS_SET) {
            return !isNull
        }

        if (condition.operator == ConditionOperator.IS_NOT_BLANK) {
            if (isNull) return false
            val text = (element as? JsonPrimitive)?.contentOrNull ?: element.toString()
            return text.isNotBlank()
        }

        if (isNull) {
            return condition.operator == ConditionOperator.NOT_EQUALS ||
                    condition.operator == ConditionOperator.NOT_IN
        }

        val primitive = element as? JsonPrimitive
        val strValue = primitive?.content ?: element.toString()

        val effectiveValues = if (condition.values.isNotEmpty()) {
            condition.values
        } else if (condition.value.isNotEmpty()) {
            listOf(condition.value)
        } else {
            emptyList()
        }

        return when (condition.operator) {
            ConditionOperator.EQUALS -> {
                compareEquals(primitive, strValue, condition.value)
            }

            ConditionOperator.NOT_EQUALS -> {
                !compareEquals(primitive, strValue, condition.value)
            }

            ConditionOperator.IN -> {
                effectiveValues.any { compareEquals(primitive, strValue, it) }
            }

            ConditionOperator.NOT_IN -> {
                effectiveValues.none { compareEquals(primitive, strValue, it) }
            }

            ConditionOperator.REGEX_MATCH -> {
                try {
                    Regex(condition.value).containsMatchIn(strValue)
                } catch (e: Exception) {
                    false
                }
            }

            ConditionOperator.GREATER_THAN,
            ConditionOperator.LESS_THAN,
            ConditionOperator.GREATER_OR_EQUAL,
            ConditionOperator.LESS_OR_EQUAL -> {
                compareNumeric(primitive, condition.value, condition.operator)
            }

            ConditionOperator.IS_SET,
            ConditionOperator.IS_NOT_BLANK -> true
        }
    }

    private fun compareEquals(
        primitive: JsonPrimitive?,
        strValue: String,
        expectedValue: String
    ): Boolean {
        val boolVal = primitive?.booleanOrNull
        val expectedBool = expectedValue.toBooleanStrictOrNull()
        if (boolVal != null && expectedBool != null) {
            return boolVal == expectedBool
        }

        val numVal = primitive?.doubleOrNull
        val expectedNum = expectedValue.toDoubleOrNull()
        if (numVal != null && expectedNum != null) {
            return numVal == expectedNum
        }

        return strValue.equals(expectedValue, ignoreCase = true)
    }

    private fun compareNumeric(
        primitive: JsonPrimitive?,
        expectedValueStr: String,
        operator: ConditionOperator
    ): Boolean {
        val actualNum = primitive?.doubleOrNull ?: return false
        val expectedNum = expectedValueStr.toDoubleOrNull() ?: return false

        return when (operator) {
            ConditionOperator.GREATER_THAN -> actualNum > expectedNum
            ConditionOperator.LESS_THAN -> actualNum < expectedNum
            ConditionOperator.GREATER_OR_EQUAL -> actualNum >= expectedNum
            ConditionOperator.LESS_OR_EQUAL -> actualNum <= expectedNum
            else -> false
        }
    }
}
