package org.wip.plugintoolkit.api.sdui

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Polymorphic event dispatched from the Host UI to the plugin's interactive session.
 */
@Serializable(with = UiEventSerializer::class)
sealed interface UiEvent {
    val nodeId: String

    /**
     * Discrete user action (button click, card tap, etc.).
     */
    @Serializable
    @SerialName("click")
    data class Click(override val nodeId: String) : UiEvent

    /**
     * Vector drawing stroke finalized and committed (onPointerUp).
     */
    @Serializable
    @SerialName("path_committed")
    data class PathCommitted(
        override val nodeId: String,
        val path: VectorPathData
    ) : UiEvent

    /**
     * Selection change in a dropdown or segmented selector.
     */
    @Serializable
    @SerialName("option_selected")
    data class OptionSelected(
        override val nodeId: String,
        val selectedKey: String
    ) : UiEvent

    /**
     * State toggle on a switch or checkbox.
     */
    @Serializable
    @SerialName("toggle_changed")
    data class ToggleChanged(
        override val nodeId: String,
        val checked: Boolean
    ) : UiEvent

    /**
     * Continuous or debounced text change in an input field.
     */
    @Serializable
    @SerialName("text_changed")
    data class TextChanged(
        override val nodeId: String,
        val text: String
    ) : UiEvent

    /**
     * Continuous or stepped numeric value change on a slider.
     */
    @Serializable
    @SerialName("value_changed")
    data class ValueChanged(
        override val nodeId: String,
        val value: Double
    ) : UiEvent

    /**
     * Custom event payload for domain-specific controls.
     */
    @Serializable
    @SerialName("custom")
    data class Custom(
        override val nodeId: String,
        val action: String = "custom",
        val payload: JsonElement? = null
    ) : UiEvent
}

/**
 * Polymorphic serializer for UiEvent with fallback to Custom event for forward compatibility.
 */
object UiEventSerializer : KSerializer<UiEvent> {
    override val descriptor: kotlinx.serialization.descriptors.SerialDescriptor =
        kotlinx.serialization.descriptors.buildClassSerialDescriptor("UiEvent")

    override fun serialize(encoder: Encoder, value: UiEvent) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw kotlinx.serialization.SerializationException("Only JSON is supported")
        val typeName = when (value) {
            is UiEvent.Click -> "click"
            is UiEvent.PathCommitted -> "path_committed"
            is UiEvent.OptionSelected -> "option_selected"
            is UiEvent.ToggleChanged -> "toggle_changed"
            is UiEvent.TextChanged -> "text_changed"
            is UiEvent.ValueChanged -> "value_changed"
            is UiEvent.Custom -> "custom"
        }
        val element = when (value) {
            is UiEvent.Click -> jsonEncoder.json.encodeToJsonElement(UiEvent.Click.serializer(), value)
            is UiEvent.PathCommitted -> jsonEncoder.json.encodeToJsonElement(UiEvent.PathCommitted.serializer(), value)
            is UiEvent.OptionSelected -> jsonEncoder.json.encodeToJsonElement(UiEvent.OptionSelected.serializer(), value)
            is UiEvent.ToggleChanged -> jsonEncoder.json.encodeToJsonElement(UiEvent.ToggleChanged.serializer(), value)
            is UiEvent.TextChanged -> jsonEncoder.json.encodeToJsonElement(UiEvent.TextChanged.serializer(), value)
            is UiEvent.ValueChanged -> jsonEncoder.json.encodeToJsonElement(UiEvent.ValueChanged.serializer(), value)
            is UiEvent.Custom -> jsonEncoder.json.encodeToJsonElement(UiEvent.Custom.serializer(), value)
        }
        if (element is JsonObject) {
            val withType = JsonObject(mapOf("type" to JsonPrimitive(typeName)) + element)
            jsonEncoder.encodeJsonElement(withType)
        } else {
            jsonEncoder.encodeJsonElement(element)
        }
    }

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): UiEvent {
        val input = decoder as? JsonDecoder ?: throw kotlinx.serialization.SerializationException("Only JSON is supported")
        val element = input.decodeJsonElement()
        val type = (element as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull
        val deserializer: KSerializer<out UiEvent> = when (type) {
            "click" -> UiEvent.Click.serializer()
            "path_committed" -> UiEvent.PathCommitted.serializer()
            "option_selected" -> UiEvent.OptionSelected.serializer()
            "toggle_changed" -> UiEvent.ToggleChanged.serializer()
            "text_changed" -> UiEvent.TextChanged.serializer()
            "value_changed" -> UiEvent.ValueChanged.serializer()
            "custom" -> UiEvent.Custom.serializer()
            else -> UiEvent.Custom.serializer()
        }
        return input.json.decodeFromJsonElement(deserializer, element)
    }
}
