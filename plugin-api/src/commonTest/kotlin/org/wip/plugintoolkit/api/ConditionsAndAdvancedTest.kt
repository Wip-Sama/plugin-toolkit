package org.wip.plugintoolkit.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConditionsAndAdvancedTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun testParameterConditionEvaluatorBasic() {
        val params = mapOf(
            "model" to JsonPrimitive("SDXL"),
            "steps" to JsonPrimitive(25),
            "denoise" to JsonPrimitive(0.75),
            "enableUpscale" to JsonPrimitive(true),
            "customTag" to JsonPrimitive("v2_beta_release")
        )
        val settings = mapOf(
            "gpuEnabled" to JsonPrimitive(true),
            "backend" to JsonPrimitive("TensorRT")
        )
        val locks = mapOf(
            "feature_unlocked" to true,
            "pro_tier" to false
        )

        // Test EQUALS string
        val c1 = ParameterCondition(source = ConditionSource.PARAMETER, target = "model", operator = ConditionOperator.EQUALS, value = "SDXL")
        assertTrue(ParameterConditionEvaluator.isSatisfied(ConditionGroup.of(c1), params, settings, locks))

        val c1Fail = ParameterCondition(source = ConditionSource.PARAMETER, target = "model", operator = ConditionOperator.EQUALS, value = "Flux")
        assertFalse(ParameterConditionEvaluator.isSatisfied(ConditionGroup.of(c1Fail), params, settings, locks))

        // Test IN
        val c2 = ParameterCondition(source = ConditionSource.PARAMETER, target = "model", operator = ConditionOperator.IN, values = listOf("SD15", "SDXL"))
        assertTrue(ParameterConditionEvaluator.isSatisfied(ConditionGroup.of(c2), params, settings, locks))

        // Test Numeric comparisons
        val c3 = ParameterCondition(source = ConditionSource.PARAMETER, target = "steps", operator = ConditionOperator.GREATER_THAN, value = "20")
        assertTrue(ParameterConditionEvaluator.isSatisfied(ConditionGroup.of(c3), params, settings, locks))

        val c4 = ParameterCondition(source = ConditionSource.PARAMETER, target = "denoise", operator = ConditionOperator.LESS_OR_EQUAL, value = "0.75")
        assertTrue(ParameterConditionEvaluator.isSatisfied(ConditionGroup.of(c4), params, settings, locks))

        // Test Regex
        val c5 = ParameterCondition(source = ConditionSource.PARAMETER, target = "customTag", operator = ConditionOperator.REGEX_MATCH, value = "^v2_.*")
        assertTrue(ParameterConditionEvaluator.isSatisfied(ConditionGroup.of(c5), params, settings, locks))

        // Test Settings source
        val c6 = ParameterCondition(source = ConditionSource.SETTING, target = "backend", operator = ConditionOperator.EQUALS, value = "TensorRT")
        assertTrue(ParameterConditionEvaluator.isSatisfied(ConditionGroup.of(c6), params, settings, locks))

        // Test Lock source
        val c7 = ParameterCondition(source = ConditionSource.LOCK, target = "feature_unlocked", operator = ConditionOperator.EQUALS, value = "true")
        assertTrue(ParameterConditionEvaluator.isSatisfied(ConditionGroup.of(c7), params, settings, locks))

        val c8 = ParameterCondition(source = ConditionSource.LOCK, target = "pro_tier", operator = ConditionOperator.EQUALS, value = "true")
        assertFalse(ParameterConditionEvaluator.isSatisfied(ConditionGroup.of(c8), params, settings, locks))
    }

    @Test
    fun testCompoundConditionsAndOr() {
        val params = mapOf(
            "model" to JsonPrimitive("SDXL"),
            "steps" to JsonPrimitive(15)
        )

        val condModel = ParameterCondition(source = ConditionSource.PARAMETER, target = "model", operator = ConditionOperator.EQUALS, value = "SDXL")
        val condStepsHigh = ParameterCondition(source = ConditionSource.PARAMETER, target = "steps", operator = ConditionOperator.GREATER_THAN, value = "20")

        // AND group: model == SDXL AND steps > 20 -> should be false
        val andGroup = ConditionGroup(listOf(condModel, condStepsHigh), isOr = false)
        assertFalse(ParameterConditionEvaluator.isSatisfied(andGroup, params))

        // OR group: model == SDXL OR steps > 20 -> should be true
        val orGroup = ConditionGroup(listOf(condModel, condStepsHigh), isOr = true)
        assertTrue(ParameterConditionEvaluator.isSatisfied(orGroup, params))

        // Empty group is always satisfied
        assertTrue(ParameterConditionEvaluator.isSatisfied(ConditionGroup.EMPTY, params))
        assertTrue(ParameterConditionEvaluator.isSatisfied(null, params))
    }

    @Test
    fun testParameterAndOutputMetadataSerialization() {
        val conditionGroup = ConditionGroup.of(
            ParameterCondition(source = ConditionSource.PARAMETER, target = "engine", operator = ConditionOperator.EQUALS, value = "CYCLES"),
            ParameterCondition(source = ConditionSource.SETTING, target = "highQuality", operator = ConditionOperator.EQUALS, value = "true")
        )

        val paramMeta = ParameterMetadata(
            defaultValue = JsonPrimitive(128),
            description = "Sample count",
            type = DataType.Primitive(PrimitiveType.INT),
            isAdvanced = true,
            condition = conditionGroup
        )

        val encoded = json.encodeToString(paramMeta)
        val decoded = json.decodeFromString<ParameterMetadata>(encoded)

        assertEquals(paramMeta.description, decoded.description)
        assertTrue(decoded.isAdvanced)
        assertEquals(2, decoded.condition?.conditions?.size)
        assertEquals("engine", decoded.condition?.conditions?.get(0)?.target)
        assertEquals("CYCLES", decoded.condition?.conditions?.get(0)?.value)
        assertEquals(ConditionSource.SETTING, decoded.condition?.conditions?.get(1)?.source)

        val outputMeta = OutputMetadata(
            name = "auxPass",
            description = "Auxiliary Pass",
            type = DataType.Primitive(PrimitiveType.STRING),
            isAdvanced = true,
            condition = conditionGroup
        )

        val encodedOutput = json.encodeToString(outputMeta)
        val decodedOutput = json.decodeFromString<OutputMetadata>(encodedOutput)

        assertEquals("auxPass", decodedOutput.name)
        assertTrue(decodedOutput.isAdvanced)
        assertEquals(2, decodedOutput.condition?.conditions?.size)
    }

    @Test
    fun testBackwardCompatibilityWithoutAdvancedOrCondition() {
        val legacyParamJson = """{
            "description": "Legacy param",
            "type": {"type": "primitive", "primitiveType": "STRING"},
            "required": true
        }"""

        val decoded = json.decodeFromString<ParameterMetadata>(legacyParamJson)
        assertEquals("Legacy param", decoded.description)
        assertFalse(decoded.isAdvanced)
        assertNull(decoded.condition)

        val legacyOutputJson = """{
            "name": "result",
            "description": "Legacy output",
            "type": {"type": "primitive", "primitiveType": "STRING"}
        }"""

        val decodedOutput = json.decodeFromString<OutputMetadata>(legacyOutputJson)
        assertEquals("result", decodedOutput.name)
        assertFalse(decodedOutput.isAdvanced)
        assertNull(decodedOutput.condition)
    }

    @Test
    fun testDotNotationPathResolution() {
        val params = mapOf(
            "options" to buildJsonObject {
                put("provider", JsonPrimitive("openai"))
                put("deep", buildJsonObject {
                    put("tier", JsonPrimitive("pro"))
                })
            },
            "flat_param" to JsonPrimitive("value")
        )

        // Target: options.provider == openai
        val c1 = ParameterCondition(
            source = ConditionSource.PARAMETER,
            target = "options.provider",
            operator = ConditionOperator.EQUALS,
            value = "openai"
        )
        assertTrue(ParameterConditionEvaluator.isSatisfied(ConditionGroup.of(c1), params))

        // Target: options.provider == anthropic (false)
        val c2 = ParameterCondition(
            source = ConditionSource.PARAMETER,
            target = "options.provider",
            operator = ConditionOperator.EQUALS,
            value = "anthropic"
        )
        assertFalse(ParameterConditionEvaluator.isSatisfied(ConditionGroup.of(c2), params))

        // Deep nested target: options.deep.tier == pro
        val c3 = ParameterCondition(
            source = ConditionSource.PARAMETER,
            target = "options.deep.tier",
            operator = ConditionOperator.EQUALS,
            value = "pro"
        )
        assertTrue(ParameterConditionEvaluator.isSatisfied(ConditionGroup.of(c3), params))

        // Fallback to flat key with dots if it exists
        val dottedKeyParams = mapOf(
            "model.version" to JsonPrimitive("v2")
        )
        val c4 = ParameterCondition(
            source = ConditionSource.PARAMETER,
            target = "model.version",
            operator = ConditionOperator.EQUALS,
            value = "v2"
        )
        assertTrue(ParameterConditionEvaluator.isSatisfied(ConditionGroup.of(c4), dottedKeyParams))
    }
}
