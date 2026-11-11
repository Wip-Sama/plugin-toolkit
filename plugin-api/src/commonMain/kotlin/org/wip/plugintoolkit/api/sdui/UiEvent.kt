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
     *
     * @property action Optional semantic action identifier configured on the source node.
     */
    @Serializable
    @SerialName("click")
    data class Click(
        override val nodeId: String,
        val action: String? = null
    ) : UiEvent

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
     * Selection change on a spatial [BoardNode].
     */
    @Serializable
    @SerialName("board_layer_selected")
    data class BoardLayerSelected(
        override val nodeId: String,
        val selectedLayerIds: List<String>
    ) : UiEvent

    /**
     * Layer drag-move committed on a spatial [BoardNode].
     */
    @Serializable
    @SerialName("board_layer_moved")
    data class BoardLayerMoved(
        override val nodeId: String,
        val layerId: String,
        val position: NormalizedPoint
    ) : UiEvent

    /**
     * Layer resize committed on a spatial [BoardNode].
     */
    @Serializable
    @SerialName("board_layer_resized")
    data class BoardLayerResized(
        override val nodeId: String,
        val layerId: String,
        val position: NormalizedPoint,
        val size: NormalizedSize
    ) : UiEvent

    /**
     * Inline text editing committed on a [BoardLayer.TextLayer] or [BoardLayer.ShapeLayer].
     */
    @Serializable
    @SerialName("board_text_edited")
    data class BoardTextEdited(
        override val nodeId: String,
        val layerId: String,
        val text: String
    ) : UiEvent

    /**
     * New text placement requested on a spatial [BoardNode] using [BoardTool.PlaceText].
     */
    @Serializable
    @SerialName("board_text_placed")
    data class BoardTextPlaced(
        override val nodeId: String,
        val position: NormalizedPoint,
        val text: String
    ) : UiEvent

    /**
     * New geometric shape committed on a spatial [BoardNode] using [BoardTool.Shape] or [BoardTool.Rectangle].
     */
    @Serializable
    @SerialName("board_shape_committed")
    data class BoardShapeCommitted(
        override val nodeId: String,
        val shapeType: BoardShapeType,
        val position: NormalizedPoint,
        val size: NormalizedSize,
        val strokeColor: SemanticColor = SemanticColor.PRIMARY,
        val fillColor: SemanticColor? = null,
        val strokeWidth: Float = 2f
    ) : UiEvent

    /**
     * Deletion requested for one or more selected layers on a spatial [BoardNode].
     */
    @Serializable
    @SerialName("board_layer_deleted")
    data class BoardLayerDeleted(
        override val nodeId: String,
        val layerIds: List<String>
    ) : UiEvent

    /**
     * Click on empty space of a spatial [BoardNode] at a normalized position.
     */
    @Serializable
    @SerialName("board_canvas_clicked")
    data class BoardCanvasClicked(
        override val nodeId: String,
        val position: NormalizedPoint
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
            is UiEvent.BoardLayerSelected -> "board_layer_selected"
            is UiEvent.BoardLayerMoved -> "board_layer_moved"
            is UiEvent.BoardLayerResized -> "board_layer_resized"
            is UiEvent.BoardTextEdited -> "board_text_edited"
            is UiEvent.BoardTextPlaced -> "board_text_placed"
            is UiEvent.BoardShapeCommitted -> "board_shape_committed"
            is UiEvent.BoardLayerDeleted -> "board_layer_deleted"
            is UiEvent.BoardCanvasClicked -> "board_canvas_clicked"
            is UiEvent.Custom -> "custom"
        }
        val element = when (value) {
            is UiEvent.Click -> jsonEncoder.json.encodeToJsonElement(UiEvent.Click.serializer(), value)
            is UiEvent.PathCommitted -> jsonEncoder.json.encodeToJsonElement(UiEvent.PathCommitted.serializer(), value)
            is UiEvent.OptionSelected -> jsonEncoder.json.encodeToJsonElement(UiEvent.OptionSelected.serializer(), value)
            is UiEvent.ToggleChanged -> jsonEncoder.json.encodeToJsonElement(UiEvent.ToggleChanged.serializer(), value)
            is UiEvent.TextChanged -> jsonEncoder.json.encodeToJsonElement(UiEvent.TextChanged.serializer(), value)
            is UiEvent.ValueChanged -> jsonEncoder.json.encodeToJsonElement(UiEvent.ValueChanged.serializer(), value)
            is UiEvent.BoardLayerSelected -> jsonEncoder.json.encodeToJsonElement(UiEvent.BoardLayerSelected.serializer(), value)
            is UiEvent.BoardLayerMoved -> jsonEncoder.json.encodeToJsonElement(UiEvent.BoardLayerMoved.serializer(), value)
            is UiEvent.BoardLayerResized -> jsonEncoder.json.encodeToJsonElement(UiEvent.BoardLayerResized.serializer(), value)
            is UiEvent.BoardTextEdited -> jsonEncoder.json.encodeToJsonElement(UiEvent.BoardTextEdited.serializer(), value)
            is UiEvent.BoardTextPlaced -> jsonEncoder.json.encodeToJsonElement(UiEvent.BoardTextPlaced.serializer(), value)
            is UiEvent.BoardShapeCommitted -> jsonEncoder.json.encodeToJsonElement(UiEvent.BoardShapeCommitted.serializer(), value)
            is UiEvent.BoardLayerDeleted -> jsonEncoder.json.encodeToJsonElement(UiEvent.BoardLayerDeleted.serializer(), value)
            is UiEvent.BoardCanvasClicked -> jsonEncoder.json.encodeToJsonElement(UiEvent.BoardCanvasClicked.serializer(), value)
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
            "board_layer_selected" -> UiEvent.BoardLayerSelected.serializer()
            "board_layer_moved" -> UiEvent.BoardLayerMoved.serializer()
            "board_layer_resized" -> UiEvent.BoardLayerResized.serializer()
            "board_text_edited" -> UiEvent.BoardTextEdited.serializer()
            "board_text_placed" -> UiEvent.BoardTextPlaced.serializer()
            "board_shape_committed" -> UiEvent.BoardShapeCommitted.serializer()
            "board_layer_deleted" -> UiEvent.BoardLayerDeleted.serializer()
            "board_canvas_clicked" -> UiEvent.BoardCanvasClicked.serializer()
            "custom" -> UiEvent.Custom.serializer()
            else -> UiEvent.Custom.serializer()
        }
        val targetElement = if (element is JsonObject) {
            JsonObject(element.filterKeys { it != "type" })
        } else {
            element
        }
        return input.json.decodeFromJsonElement(deserializer, targetElement)
    }
}
