package org.wip.plugintoolkit.features.plugin.utils

import kotlinx.serialization.json.*
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsUtilsTest {

    @Test
    fun testJsonToStringPrimitives() {
        // Boolean
        val boolType = DataType.Primitive(PrimitiveType.BOOLEAN)
        assertEquals("true", SettingsUtils.jsonToString(JsonPrimitive(true), boolType))
        assertEquals("false", SettingsUtils.jsonToString(JsonPrimitive("false"), boolType))

        // Int
        val intType = DataType.Primitive(PrimitiveType.INT)
        assertEquals("42", SettingsUtils.jsonToString(JsonPrimitive(42), intType))
        assertEquals("42", SettingsUtils.jsonToString(JsonPrimitive("42"), intType))

        // String
        val stringType = DataType.Primitive(PrimitiveType.STRING)
        assertEquals("hello", SettingsUtils.jsonToString(JsonPrimitive("hello"), stringType))
        assertEquals("", SettingsUtils.jsonToString(JsonNull, stringType))
    }

    @Test
    fun testStringToJsonPrimitives() {
        // Boolean
        val boolType = DataType.Primitive(PrimitiveType.BOOLEAN)
        assertEquals(JsonPrimitive(true), SettingsUtils.stringToJson("true", boolType))
        assertEquals(JsonPrimitive(false), SettingsUtils.stringToJson("false", boolType))
        assertEquals(JsonPrimitive(false), SettingsUtils.stringToJson("invalid", boolType))

        // Int
        val intType = DataType.Primitive(PrimitiveType.INT)
        assertEquals(JsonPrimitive(123L), SettingsUtils.stringToJson("123", intType))
        assertEquals(JsonPrimitive(0L), SettingsUtils.stringToJson("invalid", intType))

        // String
        val stringType = DataType.Primitive(PrimitiveType.STRING)
        assertEquals(JsonPrimitive("hello"), SettingsUtils.stringToJson("hello", stringType))
        assertEquals(JsonNull, SettingsUtils.stringToJson("", stringType))
    }

    @Test
    fun testJsonToStringArrays() {
        // List<Bool>
        val listBoolType = DataType.Array(DataType.Primitive(PrimitiveType.BOOLEAN))
        val boolArray = JsonArray(listOf(JsonPrimitive(true), JsonPrimitive(false)))
        assertEquals("true,false", SettingsUtils.jsonToString(boolArray, listBoolType))

        // List<Int>
        val listIntType = DataType.Array(DataType.Primitive(PrimitiveType.INT))
        val intArray = JsonArray(listOf(JsonPrimitive(1), JsonPrimitive(2), JsonPrimitive(3)))
        assertEquals("1,2,3", SettingsUtils.jsonToString(intArray, listIntType))

        // List<String>
        val listStringType = DataType.Array(DataType.Primitive(PrimitiveType.STRING))
        val stringArray = JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b")))
        assertEquals("a,b", SettingsUtils.jsonToString(stringArray, listStringType))
    }

    @Test
    fun testStringToJsonArrays() {
        // List<Bool>
        val listBoolType = DataType.Array(DataType.Primitive(PrimitiveType.BOOLEAN))
        val expectedBoolArray = JsonArray(listOf(JsonPrimitive(true), JsonPrimitive(false)))
        
        // Comma-separated list
        assertEquals(expectedBoolArray, SettingsUtils.stringToJson("true,false", listBoolType))
        assertEquals(expectedBoolArray, SettingsUtils.stringToJson("true, false", listBoolType))
        
        // JSON array format (backward compatibility)
        assertEquals(expectedBoolArray, SettingsUtils.stringToJson("[true, false]", listBoolType))
        assertEquals(expectedBoolArray, SettingsUtils.stringToJson("[\"true\", \"false\"]", listBoolType))

        // List<Int>
        val listIntType = DataType.Array(DataType.Primitive(PrimitiveType.INT))
        val expectedIntArray = JsonArray(listOf(JsonPrimitive(1L), JsonPrimitive(2L), JsonPrimitive(3L)))
        assertEquals(expectedIntArray, SettingsUtils.stringToJson("1,2,3", listIntType))
        assertEquals(expectedIntArray, SettingsUtils.stringToJson("[1, 2, 3]", listIntType))
    }
}
