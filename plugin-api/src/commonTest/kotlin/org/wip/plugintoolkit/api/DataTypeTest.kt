package org.wip.plugintoolkit.api

import kotlinx.serialization.json.*
import kotlin.test.*

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
}
