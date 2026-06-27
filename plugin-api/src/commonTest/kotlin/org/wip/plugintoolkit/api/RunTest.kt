package org.wip.plugintoolkit.api

import kotlinx.serialization.json.Json

fun main() {
    val jsonString = """{
        "description": "First input file",
        "type": {
            "type": "primitive",
            "primitiveType": "STRING"
        },
        "required": true,
        "semanticTypes": [
            {
                "namespace": "file",
                "name": "text",
                "variant": null
            }
        ],
        "role": "INPUT_LOCATION"
    }"""
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    try {
        val parsed = json.decodeFromString<ParameterMetadata>(jsonString)
        println("SUCCESS: " + parsed.role)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
