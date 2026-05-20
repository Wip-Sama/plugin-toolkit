package org.wip.plugintoolkit.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataTypeTest {

    @Test
    fun testPrimitiveString() {
        val type = DataType.Primitive(PrimitiveType.STRING)
        
        assertTrue(type.isProvided(JsonPrimitive("hello")), "Should be provided for non-blank string")
        assertFalse(type.isProvided(JsonPrimitive("")), "Should not be provided for empty string")
        assertFalse(type.isProvided(JsonPrimitive("   ")), "Should not be provided for blank string")
        assertFalse(type.isProvided(JsonNull), "Should not be provided for JsonNull")
        assertFalse(type.isProvided(null), "Should not be provided for null")
    }

    @Test
    fun testPrimitiveInt() {
        val type = DataType.Primitive(PrimitiveType.INT)
        
        assertTrue(type.isProvided(JsonPrimitive(123)), "Should be provided for integer")
        assertTrue(type.isProvided(JsonPrimitive(0)), "Should be provided for zero")
        assertFalse(type.isProvided(JsonNull), "Should not be provided for JsonNull")
    }

    @Test
    fun testArray() {
        val type = DataType.Array(DataType.Primitive(PrimitiveType.STRING))
        
        assertTrue(type.isProvided(buildJsonArray { add("item") }), "Should be provided for non-empty array")
        assertFalse(type.isProvided(JsonArray(emptyList())), "Should not be provided for empty array")
        assertFalse(type.isProvided(JsonNull), "Should not be provided for JsonNull")
    }

    @Test
    fun testObject() {
        val type = DataType.Object("MyClass")
        
        assertTrue(type.isProvided(buildJsonObject { put("key", "value") }), "Should be provided for non-null object")
        assertFalse(type.isProvided(JsonNull), "Should not be provided for JsonNull")
    }

    @Test
    fun testEnum() {
        val type = DataType.Enum("MyEnum", listOf("A", "B"))
        
        assertTrue(type.isProvided(JsonPrimitive("A")), "Should be provided for enum value")
        assertFalse(type.isProvided(JsonNull), "Should not be provided for JsonNull")
    }

    @Test
    fun testDataTypeCompatibility() {
        val stringType = DataType.Primitive(PrimitiveType.STRING)
        val intType = DataType.Primitive(PrimitiveType.INT)
        val anyType = DataType.Primitive(PrimitiveType.ANY)
        
        // Primitives
        assertTrue(stringType.isCompatibleWith(stringType), "Identical types should be compatible")
        assertFalse(stringType.isCompatibleWith(intType), "Different primitives should not be compatible")
        
        // Wildcard
        assertTrue(stringType.isCompatibleWith(anyType), "ANY should be compatible with String")
        assertTrue(anyType.isCompatibleWith(intType), "ANY should be compatible with Int")
        
        // Arrays
        val stringArray = DataType.Array(stringType)
        val intArray = DataType.Array(intType)
        val anyArray = DataType.Array(anyType)
        assertTrue(stringArray.isCompatibleWith(stringArray), "Identical arrays should be compatible")
        assertFalse(stringArray.isCompatibleWith(intArray), "Arrays of different primitives should not be compatible")
        assertTrue(stringArray.isCompatibleWith(anyArray), "Array of ANY should be compatible with Array of String")

        // Objects
        val objA1 = DataType.Object("com.example.ClassA")
        val objA2 = DataType.Object("com.example.ClassA")
        val objB = DataType.Object("com.example.ClassB")
        assertTrue(objA1.isCompatibleWith(objA2), "Objects with same class name should be compatible")
        assertFalse(objA1.isCompatibleWith(objB), "Objects with different class names should not be compatible")

        // Enums
        val enumA1 = DataType.Enum("com.example.EnumA", listOf("X", "Y"))
        val enumA2 = DataType.Enum("com.example.EnumA", listOf("Y", "X")) // Different order/options
        val enumB = DataType.Enum("com.example.EnumB", listOf("X", "Y"))
        assertTrue(enumA1.isCompatibleWith(enumA2), "Enums with same class name should be compatible regardless of option details")
        assertFalse(enumA1.isCompatibleWith(enumB), "Enums with different class names should not be compatible")
    }

    @Test
    fun testSemanticTypeCompatibility() {
        assertTrue(isSemanticTypeCompatible(null, null), "Null/null should be compatible")
        assertTrue(isSemanticTypeCompatible("filepath", null), "Value/null should be compatible")
        assertTrue(isSemanticTypeCompatible(null, "filepath"), "Null/value should be compatible")
        assertTrue(isSemanticTypeCompatible("filepath", "filepath"), "Identical semantic types should be compatible")
        assertTrue(isSemanticTypeCompatible("Filepath", "filepath"), "Case-insensitive semantic types should be compatible")
        assertFalse(isSemanticTypeCompatible("filepath", "url"), "Different semantic types should not be compatible")
    }

    @Test
    fun testCanConvert() {
        val intType = DataType.Primitive(PrimitiveType.INT)
        val doubleType = DataType.Primitive(PrimitiveType.DOUBLE)
        val stringType = DataType.Primitive(PrimitiveType.STRING)
        
        val intArray = DataType.Array(intType)
        val stringArray = DataType.Array(stringType)
        
        val tupleType = DataType.Object("Tuple2")
        val listType = DataType.Object("List")
        
        // Rule 1: Int <-> Double conversion
        assertTrue(intType.canConvert(doubleType), "Int should convert to Double")
        assertTrue(doubleType.canConvert(intType), "Double should convert to Int")
        assertTrue(intType.canConvert(stringType), "Int should convert to String")
        
        // Rule 2: Array/List <-> Tuple/Pair/Triple conversion
        assertTrue(intArray.canConvert(tupleType), "Array of Int should convert to Tuple")
        assertTrue(tupleType.canConvert(intArray), "Tuple should convert to Array of Int")
        assertTrue(intArray.canConvert(listType), "Array should convert to List")
        assertTrue(stringArray.canConvert(tupleType), "Array of String should convert to Tuple")
        
        // Rule 3: Single element (Any / Primitive) to List / Tuple
        val anyType = DataType.Primitive(PrimitiveType.ANY)
        assertTrue(anyType.canConvert(intArray), "Any should convert to List/Array")
        assertTrue(anyType.canConvert(tupleType), "Any should convert to Tuple")
        assertTrue(intType.canConvert(intArray), "Primitive Int should convert to Array")
    }
}

