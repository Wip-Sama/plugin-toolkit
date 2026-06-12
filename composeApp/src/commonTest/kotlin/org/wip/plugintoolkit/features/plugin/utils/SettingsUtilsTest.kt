package org.wip.plugintoolkit.features.plugin.utils

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.api.ParameterConstraints
import kotlin.test.Test
import kotlin.test.assertEquals

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
        assertEquals(JsonPrimitive(""), SettingsUtils.stringToJson("", stringType))
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
        
        // Empty array
        assertEquals(JsonArray(emptyList()), SettingsUtils.stringToJson("", listIntType))
    }

    @Test
    fun testNestedArrays() {
        val listListDouble = DataType.Array(DataType.Array(DataType.Primitive(PrimitiveType.DOUBLE)))

        // Test jsonToString
        val doubleArray = JsonArray(listOf(
            JsonArray(listOf(JsonPrimitive(100.0), JsonPrimitive(100.0))),
            JsonArray(listOf(JsonPrimitive(200.0), JsonPrimitive(200.0)))
        ))
        assertEquals("(100.0,100.0), (200.0,200.0)", SettingsUtils.jsonToString(doubleArray, listListDouble))

        // Test stringToJson with matching parentheses
        assertEquals(doubleArray, SettingsUtils.stringToJson("(100.0,100.0), (200.0,200.0)", listListDouble))
        assertEquals(doubleArray, SettingsUtils.stringToJson("(100.0, 100.0) (200.0, 200.0)", listListDouble))
        assertEquals(doubleArray, SettingsUtils.stringToJson("[(100.0, 100.0), (200.0, 200.0)]", listListDouble))

        // Test validateParameter
        val constraints = ParameterConstraints(minValue = 50.0, maxValue = 350.0)
        assertEquals(null, SettingsUtils.validateParameter("(100,100), (200,200)", isRequired = true, type = listListDouble, constraints = constraints))

        // Out of range check
        val errorMsg = SettingsUtils.validateParameter("(100,400), (200,200)", isRequired = true, type = listListDouble, constraints = constraints)
        assertEquals("Value must be <= 350.0", errorMsg)
    }
}
