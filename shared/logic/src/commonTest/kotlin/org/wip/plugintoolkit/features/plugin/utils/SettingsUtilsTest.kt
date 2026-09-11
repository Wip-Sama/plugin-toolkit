package org.wip.plugintoolkit.features.plugin.utils

import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsUtilsTest {

    @Test
    fun testValidateCapabilityLocksAndSettings_allSatisfied() {
        val capability = Capability(
            name = "testCap",
            description = "test",
            returnType = DataType.Primitive(PrimitiveType.UNIT),
            requiredLocks = listOf("feature_unlocked"),
            requiresSettings = listOf("apiKey")
        )
        val locks = mapOf("feature_unlocked" to true)
        val settings = mapOf("apiKey" to JsonPrimitive("12345"))

        val result = SettingsUtils.validateCapabilityLocksAndSettings(capability, locks, settings)
        assertNull(result)
    }

    @Test
    fun testValidateCapabilityLocksAndSettings_missingLock() {
        val capability = Capability(
            name = "testCap",
            description = "test",
            returnType = DataType.Primitive(PrimitiveType.UNIT),
            requiredLocks = listOf("feature_unlocked")
        )
        val locks = mapOf("feature_unlocked" to false)
        val settings = emptyMap<String, kotlinx.serialization.json.JsonElement>()

        val result = SettingsUtils.validateCapabilityLocksAndSettings(capability, locks, settings)
        assertEquals("Requires lock: feature_unlocked", result)
    }

    @Test
    fun testValidateCapabilityLocksAndSettings_missingSetting() {
        val capability = Capability(
            name = "testCap",
            description = "test",
            returnType = DataType.Primitive(PrimitiveType.UNIT),
            requiresSettings = listOf("apiKey")
        )
        val locks = emptyMap<String, Boolean>()
        val settings = emptyMap<String, kotlinx.serialization.json.JsonElement>()

        val result = SettingsUtils.validateCapabilityLocksAndSettings(capability, locks, settings)
        assertEquals("Requires setting: apiKey", result)
    }

    @Test
    fun testStringToJsonAndJsonToStringForPrimitiveBooleanDefaults() {
        val booleanType = DataType.Primitive(PrimitiveType.BOOLEAN)

        val jsonFromEmpty = SettingsUtils.stringToJson("", booleanType)
        assertEquals(kotlinx.serialization.json.JsonPrimitive(false), jsonFromEmpty, "Empty string for Primitive BOOLEAN must convert to JsonPrimitive(false)")

        val jsonFromNullStr = SettingsUtils.stringToJson("null", booleanType)
        assertEquals(kotlinx.serialization.json.JsonPrimitive(false), jsonFromNullStr, "String 'null' for Primitive BOOLEAN must convert to JsonPrimitive(false)")

        val stringFromNullJson = SettingsUtils.jsonToString(kotlinx.serialization.json.JsonNull, booleanType)
        assertEquals("false", stringFromNullJson, "JsonNull for Primitive BOOLEAN must convert to 'false'")

        val stringFromNullElement = SettingsUtils.jsonToString(null, booleanType)
        assertEquals("false", stringFromNullElement, "null JsonElement for Primitive BOOLEAN must convert to 'false'")
    }

    @Test
    fun testValidateCapabilityLocksAndSettings_caseInsensitiveLocks() {
        val capability = Capability(
            name = "testOcrCap",
            description = "OCR with model lock",
            returnType = DataType.Primitive(PrimitiveType.UNIT),
            requiredLocks = listOf("model:Unlimited-OCR")
        )
        val locks = mapOf("model:unlimited-ocr" to true)
        val settings = emptyMap<String, kotlinx.serialization.json.JsonElement>()

        val result = SettingsUtils.validateCapabilityLocksAndSettings(capability, locks, settings)
        assertNull(result, "Case-insensitive lock matching should succeed when lock exists in lowercase")

        val status = CapabilityLockUtils.checkCapabilityLockStatus(capability, locks, settings)
        assertEquals(CapabilityLockStatus.Unlocked, status)
    }

    @Test
    fun testCapabilityLockUtils_isLockSatisfied() {
        val locks = mapOf("model:unlimited-ocr" to true, "FEATURE_A" to true)
        kotlin.test.assertTrue(CapabilityLockUtils.isLockSatisfied("model:Unlimited-OCR", locks))
        kotlin.test.assertTrue(CapabilityLockUtils.isLockSatisfied("model:unlimited-ocr", locks))
        kotlin.test.assertTrue(CapabilityLockUtils.isLockSatisfied("feature_a", locks))
        kotlin.test.assertTrue(CapabilityLockUtils.isLockSatisfied("FEATURE_A", locks))
        kotlin.test.assertFalse(CapabilityLockUtils.isLockSatisfied("non_existent", locks))
    }

    @Test
    fun testValidateParameter_optionalAndRequired() {
        val stringType = DataType.Primitive(PrimitiveType.STRING)

        // Empty with isRequired = false should pass
        val optionalResult = SettingsUtils.validateParameter(
            value = "",
            isRequired = false,
            type = stringType
        )
        assertNull(optionalResult, "Empty optional parameter should have no validation error")

        // Empty with isRequired = true should fail
        val requiredResult = SettingsUtils.validateParameter(
            value = "",
            isRequired = true,
            type = stringType
        )
        assertEquals("Required", requiredResult)
    }

    @Test
    fun testValidateParameter_regexConstraints() {
        val stringType = DataType.Primitive(PrimitiveType.STRING)
        val constraints = org.wip.plugintoolkit.api.ParameterConstraints(regex = "^[A-Z]{3}-\\d+$")

        // Valid match
        val validResult = SettingsUtils.validateParameter(
            value = "ABC-123",
            isRequired = true,
            type = stringType,
            constraints = constraints
        )
        assertNull(validResult, "Matching regex string should be valid")

        // Invalid regex match
        val invalidResult = SettingsUtils.validateParameter(
            value = "abc-123",
            isRequired = true,
            type = stringType,
            constraints = constraints
        )
        assertEquals("Does not match required format", invalidResult)

        // Non-required empty with regex constraints should pass
        val emptyOptionalResult = SettingsUtils.validateParameter(
            value = "",
            isRequired = false,
            type = stringType,
            constraints = constraints
        )
        assertNull(emptyOptionalResult, "Empty optional input with regex constraint should be valid")
    }

    @Test
    fun testResolveEffectiveValue() {
        val stringType = DataType.Primitive(PrimitiveType.STRING)
        val boolType = DataType.Primitive(PrimitiveType.BOOLEAN)
        val enumType = DataType.Enum("MyEnum", listOf("OptionA", "OptionB"))

        // Null value with defaultValue
        assertEquals("defaultVal", SettingsUtils.resolveEffectiveValue(null, JsonPrimitive("defaultVal"), stringType))
        // Blank string with defaultValue
        assertEquals("defaultVal", SettingsUtils.resolveEffectiveValue("", JsonPrimitive("defaultVal"), stringType))
        // Explicit value takes precedence
        assertEquals("explicitVal", SettingsUtils.resolveEffectiveValue("explicitVal", JsonPrimitive("defaultVal"), stringType))

        // Boolean implicit default is false
        assertEquals("false", SettingsUtils.resolveEffectiveValue(null, null, boolType))
        assertEquals("false", SettingsUtils.resolveEffectiveValue("", null, boolType))
        assertEquals("true", SettingsUtils.resolveEffectiveValue("true", null, boolType))

        // Enum implicit default is first option
        assertEquals("OptionA", SettingsUtils.resolveEffectiveValue(null, null, enumType))
        assertEquals("OptionA", SettingsUtils.resolveEffectiveValue("", null, enumType))
        assertEquals("OptionB", SettingsUtils.resolveEffectiveValue("OptionB", null, enumType))
    }

    @Test
    fun testResolveEffectiveJson() {
        val stringType = DataType.Primitive(PrimitiveType.STRING)
        val boolType = DataType.Primitive(PrimitiveType.BOOLEAN)
        val enumType = DataType.Enum("MyEnum", listOf("FIRST", "SECOND"))

        // Null json with defaultValue
        assertEquals(
            JsonPrimitive("def"),
            SettingsUtils.resolveEffectiveJson(null, JsonPrimitive("def"), stringType)
        )

        // Boolean null json defaults to JsonPrimitive(false)
        assertEquals(
            JsonPrimitive(false),
            SettingsUtils.resolveEffectiveJson(null, null, boolType)
        )

        // Enum null json defaults to JsonPrimitive("FIRST")
        assertEquals(
            JsonPrimitive("FIRST"),
            SettingsUtils.resolveEffectiveJson(null, null, enumType)
        )

        // Explicit value takes precedence
        assertEquals(
            JsonPrimitive("SECOND"),
            SettingsUtils.resolveEffectiveJson(JsonPrimitive("SECOND"), null, enumType)
        )
    }

    @Test
    fun testValidateParameter_withDefaultValue() {
        val stringType = DataType.Primitive(PrimitiveType.STRING)
        val boolType = DataType.Primitive(PrimitiveType.BOOLEAN)
        val enumType = DataType.Enum("MyEnum", listOf("Option1", "Option2"))

        // Empty string but defaultValue provided -> valid!
        val withDefaultResult = SettingsUtils.validateParameter(
            value = "",
            isRequired = true,
            type = stringType,
            defaultValue = JsonPrimitive("myDefault")
        )
        assertNull(withDefaultResult, "Parameter with default value should be valid even if current input is empty")

        // Empty string without defaultValue -> "Required"
        val withoutDefaultResult = SettingsUtils.validateParameter(
            value = "",
            isRequired = true,
            type = stringType,
            defaultValue = null
        )
        assertEquals("Required", withoutDefaultResult)

        // Boolean without explicit value has implicit default false -> valid!
        val boolResult = SettingsUtils.validateParameter(
            value = "",
            isRequired = true,
            type = boolType,
            defaultValue = null
        )
        assertNull(boolResult, "Boolean parameter has implicit false default and should be valid")

        // Enum without explicit value has implicit default to first option -> valid!
        val enumResult = SettingsUtils.validateParameter(
            value = "",
            isRequired = true,
            type = enumType,
            defaultValue = null
        )
        assertNull(enumResult, "Enum parameter has implicit first option default and should be valid")
    }
}
