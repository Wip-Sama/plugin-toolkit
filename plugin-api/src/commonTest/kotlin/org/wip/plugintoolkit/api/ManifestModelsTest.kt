package org.wip.plugintoolkit.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManifestModelsTest {
    @Test
    fun testCapabilityDeserialization() {
        val jsonString = """{
            "name": "batch_process",
            "description": "Processes multiple inputs and writes multiple outputs.",
            "returnType": { "type": "primitive", "primitiveType": "UNIT" },
            "parameters": {
                "inputA": {
                    "description": "First input file",
                    "type": { "type": "primitive", "primitiveType": "STRING" },
                    "required": true,
                    "semanticTypes": [
                        { "namespace": "file", "name": "text", "variant": null }
                    ],
                    "role": "INPUT_LOCATION"
                }
            }
        }"""
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        try {
            val parsed = json.decodeFromString<Capability>(jsonString)
            assertEquals(ParameterRole.INPUT_LOCATION, parsed.parameters!!["inputA"]!!.role)
        } catch (e: Exception) {
            println("EXCEPTION_MESSAGE: " + e.message)
            throw e
        }
    }

    @Test
    fun testCapabilityMaxConcurrentExecutionsSerialization() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val capability = Capability(
            name = "concurrent_task",
            description = "Runs with concurrency limit",
            returnType = DataType.Primitive(PrimitiveType.UNIT),
            maxConcurrentExecutions = 3
        )
        val serialized = json.encodeToString(CapabilitySerializer, capability)
        val deserialized = json.decodeFromString(CapabilitySerializer, serialized)
        assertEquals(3, deserialized.maxConcurrentExecutions)
    }

    @Test
    fun testParameterMetadataToleratesMalformedBooleanLiterals() {
        val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }
        val payload = """
            {
              "name": "legacy_cap",
              "description": "Legacy cap with truncated boolean literal",
              "returnType": { "type": "primitive", "primitiveType": "STRING" },
              "parameters": {
                "mode": {
                  "description": "Mode flag",
                  "type": { "type": "primitive", "primitiveType": "STRING" },
                  "required": "fa",
                  "isAdvanced": tr
                }
              }
            }
        """.trimIndent()
        val parsed = lenientJson.decodeFromString(CapabilitySerializer, payload)
        val param = parsed.parameters!!["mode"]!!
        assertTrue(param.isAdvanced)
        assertFalse(param.required)
    }
}
