package org.wip.plugintoolkit.api.sdui

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
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
 * Root interface for all declarative, framework-agnostic SDUI components.
 */
@Serializable(with = UiNodeSerializer::class)
sealed interface UiNode {
    val id: String
    val modifier: UiModifier
}

/**
 * Top-level immutable snapshot of an entire SDUI hierarchy.
 */
@Serializable
data class UiNodeTree(
    val root: UiNode,
    val version: Int = 1
)

// ── Container Nodes ─────────────────────────────────────────────────────────

@Serializable
@SerialName("column")
data class ColumnNode(
    override val id: String,
    val children: List<UiNode> = emptyList(),
    val spacing: SpacingSize = SpacingSize.NONE,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

@Serializable
@SerialName("row")
data class RowNode(
    override val id: String,
    val children: List<UiNode> = emptyList(),
    val spacing: SpacingSize = SpacingSize.NONE,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

@Serializable
@SerialName("box")
data class BoxNode(
    override val id: String,
    val children: List<UiNode> = emptyList(),
    override val modifier: UiModifier = UiModifier.None
) : UiNode

@Serializable
@SerialName("card")
data class CardNode(
    override val id: String,
    val title: String? = null,
    val children: List<UiNode> = emptyList(),
    override val modifier: UiModifier = UiModifier.None
) : UiNode

@Serializable
@SerialName("scrollable_column")
data class ScrollableColumnNode(
    override val id: String,
    val children: List<UiNode> = emptyList(),
    val spacing: SpacingSize = SpacingSize.NONE,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

// ── Control Nodes ───────────────────────────────────────────────────────────

@Serializable
@SerialName("text")
data class TextNode(
    override val id: String,
    val text: String,
    val role: TypographyRole = TypographyRole.BODY_MEDIUM,
    val color: SemanticColor? = null,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

@Serializable
@SerialName("button")
data class ButtonNode(
    override val id: String,
    val label: String,
    val isPrimary: Boolean = true,
    val isDestructive: Boolean = false,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

/**
 * Text input field designed for seamless state synchronization.
 *
 * @property revision Incremented when the plugin intentionally overwrites the text (e.g. form reset),
 * preventing routine recomposition from overriding user typing.
 * @property forceOverwrite When true, applies the plugin text immediately even if currently focused.
 */
@Serializable
@SerialName("text_input")
data class TextInputNode(
    override val id: String,
    val text: String = "",
    val label: String? = null,
    val placeholder: String? = null,
    val singleLine: Boolean = true,
    val debounceMs: Long = 150L,
    val revision: Int = 0,
    val forceOverwrite: Boolean = false,
    val regexPattern: String? = null,
    val maxLength: Int? = null,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

@Serializable
@SerialName("toggle")
data class ToggleNode(
    override val id: String,
    val checked: Boolean,
    val label: String? = null,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

@Serializable
@SerialName("slider")
data class SliderNode(
    override val id: String,
    val value: Double,
    val min: Double = 0.0,
    val max: Double = 1.0,
    val steps: Int = 0,
    val label: String? = null,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

@Serializable
@SerialName("dropdown")
data class DropdownNode(
    override val id: String,
    val selectedKey: String,
    val options: Map<String, String>,
    val label: String? = null,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

// ── Media & Visual Nodes ────────────────────────────────────────────────────

@Serializable
@SerialName("image")
data class ImageNode(
    override val id: String,
    val asset: AssetReference,
    val contentDescription: String? = null,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

@Serializable
@SerialName("icon")
data class IconNode(
    override val id: String,
    val name: String,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

@Serializable
@SerialName("badge")
data class BadgeNode(
    override val id: String,
    val text: String,
    val color: SemanticColor = SemanticColor.PRIMARY,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

@Serializable
@SerialName("divider")
data class DividerNode(
    override val id: String,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

// ── Canvas / Interactive Board ──────────────────────────────────────────────

@Serializable
@SerialName("board")
data class BoardNode(
    override val id: String,
    val backgroundAsset: AssetReference? = null,
    val layers: List<BoardLayer> = emptyList(),
    val activeTool: BoardTool = BoardTool.Brush(),
    val clearRevision: Int = 0,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

// ── Safety & Fallback Nodes ─────────────────────────────────────────────────

/**
 * Fallback node emitted when an unrecognized type string is encountered during deserialization.
 */
@Serializable(with = UnknownNodeCustomSerializer::class)
@SerialName("unknown")
data class UnknownNode(
    override val id: String,
    val rawType: String = "unknown",
    val fallbackText: String? = null,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

object UnknownNodeCustomSerializer : KSerializer<UnknownNode> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("UnknownNode")

    override fun serialize(encoder: Encoder, value: UnknownNode) {
        val jsonEncoder = encoder as? JsonEncoder ?: return
        val map = mutableMapOf<String, JsonElement>(
            "type" to JsonPrimitive(value.rawType),
            "id" to JsonPrimitive(value.id)
        )
        value.fallbackText?.let { map["fallbackText"] = JsonPrimitive(it) }
        map["modifier"] = jsonEncoder.json.encodeToJsonElement(UiModifier.serializer(), value.modifier)
        jsonEncoder.encodeJsonElement(JsonObject(map))
    }

    override fun deserialize(decoder: Decoder): UnknownNode {
        val input = decoder as? JsonDecoder ?: return UnknownNode(id = "unknown")
        val obj = input.decodeJsonElement() as? JsonObject ?: return UnknownNode(id = "unknown")
        val rawType = obj["type"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: "unknown"
        val fallbackText = obj["fallbackText"]?.jsonPrimitive?.contentOrNull
        val modifier = obj["modifier"]?.let {
            input.json.decodeFromJsonElement(UiModifier.serializer(), it)
        } ?: UiModifier.None
        return UnknownNode(id = id, rawType = rawType, fallbackText = fallbackText, modifier = modifier)
    }
}

/**
 * Error boundary node injected by the Pre-Composition AST Sanitizer when a subtree
 * violates tree constraints (e.g. depth > 32, node count > 1000, NaN ranges).
 */
@Serializable
@SerialName("error_boundary")
data class ErrorBoundaryNode(
    override val id: String,
    val errorMessage: String,
    val failedNodeType: String? = null,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

// ── Polymorphic Serializer ──────────────────────────────────────────────────

object UiNodeSerializer : KSerializer<UiNode> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("UiNode")

    override fun serialize(encoder: Encoder, value: UiNode) {
        val jsonEncoder = encoder as? JsonEncoder ?: throw kotlinx.serialization.SerializationException("Only JSON is supported")
        val typeName = when (value) {
            is ColumnNode -> "column"
            is RowNode -> "row"
            is BoxNode -> "box"
            is CardNode -> "card"
            is ScrollableColumnNode -> "scrollable_column"
            is TextNode -> "text"
            is ButtonNode -> "button"
            is TextInputNode -> "text_input"
            is ToggleNode -> "toggle"
            is SliderNode -> "slider"
            is DropdownNode -> "dropdown"
            is ImageNode -> "image"
            is IconNode -> "icon"
            is BadgeNode -> "badge"
            is DividerNode -> "divider"
            is BoardNode -> "board"
            is ErrorBoundaryNode -> "error_boundary"
            is UnknownNode -> value.rawType
        }
        val element = when (value) {
            is ColumnNode -> jsonEncoder.json.encodeToJsonElement(ColumnNode.serializer(), value)
            is RowNode -> jsonEncoder.json.encodeToJsonElement(RowNode.serializer(), value)
            is BoxNode -> jsonEncoder.json.encodeToJsonElement(BoxNode.serializer(), value)
            is CardNode -> jsonEncoder.json.encodeToJsonElement(CardNode.serializer(), value)
            is ScrollableColumnNode -> jsonEncoder.json.encodeToJsonElement(ScrollableColumnNode.serializer(), value)
            is TextNode -> jsonEncoder.json.encodeToJsonElement(TextNode.serializer(), value)
            is ButtonNode -> jsonEncoder.json.encodeToJsonElement(ButtonNode.serializer(), value)
            is TextInputNode -> jsonEncoder.json.encodeToJsonElement(TextInputNode.serializer(), value)
            is ToggleNode -> jsonEncoder.json.encodeToJsonElement(ToggleNode.serializer(), value)
            is SliderNode -> jsonEncoder.json.encodeToJsonElement(SliderNode.serializer(), value)
            is DropdownNode -> jsonEncoder.json.encodeToJsonElement(DropdownNode.serializer(), value)
            is ImageNode -> jsonEncoder.json.encodeToJsonElement(ImageNode.serializer(), value)
            is IconNode -> jsonEncoder.json.encodeToJsonElement(IconNode.serializer(), value)
            is BadgeNode -> jsonEncoder.json.encodeToJsonElement(BadgeNode.serializer(), value)
            is DividerNode -> jsonEncoder.json.encodeToJsonElement(DividerNode.serializer(), value)
            is BoardNode -> jsonEncoder.json.encodeToJsonElement(BoardNode.serializer(), value)
            is ErrorBoundaryNode -> jsonEncoder.json.encodeToJsonElement(ErrorBoundaryNode.serializer(), value)
            is UnknownNode -> jsonEncoder.json.encodeToJsonElement(UnknownNodeCustomSerializer, value)
        }
        if (element is JsonObject) {
            val withType = JsonObject(mapOf("type" to JsonPrimitive(typeName)) + element)
            jsonEncoder.encodeJsonElement(withType)
        } else {
            jsonEncoder.encodeJsonElement(element)
        }
    }

    override fun deserialize(decoder: Decoder): UiNode {
        val input = decoder as? JsonDecoder ?: throw kotlinx.serialization.SerializationException("Only JSON is supported")
        val element = input.decodeJsonElement()
        val type = (element as? JsonObject)?.get("type")?.jsonPrimitive?.contentOrNull
        val deserializer: KSerializer<out UiNode> = when (type) {
            "column" -> ColumnNode.serializer()
            "row" -> RowNode.serializer()
            "box" -> BoxNode.serializer()
            "card" -> CardNode.serializer()
            "scrollable_column" -> ScrollableColumnNode.serializer()
            "text" -> TextNode.serializer()
            "button" -> ButtonNode.serializer()
            "text_input" -> TextInputNode.serializer()
            "toggle" -> ToggleNode.serializer()
            "slider" -> SliderNode.serializer()
            "dropdown" -> DropdownNode.serializer()
            "image" -> ImageNode.serializer()
            "icon" -> IconNode.serializer()
            "badge" -> BadgeNode.serializer()
            "divider" -> DividerNode.serializer()
            "board" -> BoardNode.serializer()
            "error_boundary" -> ErrorBoundaryNode.serializer()
            "unknown" -> UnknownNodeCustomSerializer
            else -> UnknownNodeCustomSerializer
        }
        return input.json.decodeFromJsonElement(deserializer, element)
    }
}
