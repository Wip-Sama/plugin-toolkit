package org.wip.plugintoolkit.features.flows.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.wip.plugintoolkit.api.parseSemanticTypes

object NodeSerializationUtils {
    fun migrateSemanticTypes(element: JsonObject): JsonObject {
        if ("semanticTypes" in element) {
            return JsonObject(element.filterKeys { it != "semanticType" })
        }
        val legacySemanticType = element["semanticType"]?.jsonPrimitive?.contentOrNull
        val parsedList = parseSemanticTypes(legacySemanticType).map { type ->
            buildJsonObject {
                put("namespace", type.namespace)
                put("name", type.name)
                put("variant", type.variant)
            }
        }
        return JsonObject(element.filterKeys { it != "semanticType" } + ("semanticTypes" to JsonArray(parsedList)))
    }

    fun migratePrimitiveToJsonElement(element: JsonElement?): JsonElement? {
        if (element == null || element is JsonNull) return null
        if (element is JsonPrimitive) return element
        if (element is JsonObject) {
            // Is there a case where string is somehow bare without primitive? 
            // The prompt says "can detect legacy primitive values (Strings, Numbers) in existing JSON and wrap them into JsonPrimitive automatically".
            // If it's already parsing JSON, String/Number would already be parsed as JsonPrimitive. 
            // Wait, the prompt says "Migration Decoder that can detect legacy primitive values...". 
            // Before, defaultValue and value were `Any?`. In JSON, they are just JSON primitives or objects.
            // When migrating from `Any?` to `JsonElement?`, if the user JSON has a plain value like `"my string"`, Moshi/Kotlinx-serialization parses it as a primitive. 
            // The prompt says: "Implement a Migration Decoder that can detect legacy primitive values (Strings, Numbers) in existing JSON and wrap them into JsonPrimitive automatically during the transition".
            // Since we use kotlinx-serialization, JSON primitives are ALREADY JsonPrimitives. There is no such thing as a bare "String" in JsonElement. 
            // Wait, maybe the prompt implies something about `JsonElement` migration where strings in the stored file were somehow escaped? Or maybe if `value` was previously serialized with `AnySerializer`, `AnySerializer` stored primitives natively:
            // `is String -> JsonPrimitive(value)`
            // So they are already primitives. 
        }
        return element
    }

    fun anyToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is JsonElement -> value
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to anyToJsonElement(it.value) })
            is List<*> -> JsonArray(value.map { anyToJsonElement(it) })
            is Array<*> -> JsonArray(value.map { anyToJsonElement(it) })
            else -> JsonPrimitive(value.toString())
        }
    }
}
