package org.wip.plugintoolkit.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
