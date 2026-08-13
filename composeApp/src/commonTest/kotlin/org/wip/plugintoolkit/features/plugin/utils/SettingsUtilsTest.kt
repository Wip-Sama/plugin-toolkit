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
}
