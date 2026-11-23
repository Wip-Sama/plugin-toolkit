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
import kotlin.jvm.JvmOverloads

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
data class ColumnNode @JvmOverloads constructor(
    override val id: String,
    val children: List<UiNode> = emptyList(),
    val spacing: SpacingSize = SpacingSize.NONE,
    override val modifier: UiModifier = UiModifier.None,
    val reorderable: Boolean = false
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
data class CardNode @JvmOverloads constructor(
    override val id: String,
    val title: String? = null,
    val children: List<UiNode> = emptyList(),
    override val modifier: UiModifier = UiModifier.None,
    val surfaceRole: SemanticColor? = null,
    val outlined: Boolean = false
) : UiNode

@Serializable
@SerialName("scrollable_column")
data class ScrollableColumnNode @JvmOverloads constructor(
    override val id: String,
    val children: List<UiNode> = emptyList(),
    val spacing: SpacingSize = SpacingSize.NONE,
    override val modifier: UiModifier = UiModifier.None,
    val reorderable: Boolean = false
) : UiNode

/**
 * Multi-column layout container supporting adaptive item widths, fixed column counts,
 * lazy virtualization for large collections, and pointer drag-and-drop reordering.
 *
 * When [reorderable] is true, dragging and dropping a child card emits [UiEvent.ItemsReordered].
 */
@Serializable
@SerialName("grid")
data class GridNode @JvmOverloads constructor(
    override val id: String,
    val columns: Int? = null,
    val minItemWidthDp: Float = 156f,
    val spacing: SpacingSize = SpacingSize.SMALL,
    val reorderable: Boolean = false,
    val children: List<UiNode> = emptyList(),
    override val modifier: UiModifier = UiModifier.None
) : UiNode

/**
 * Root workspace layout defining structural attach points for sidebars and floating panels.
 */
@Serializable
@SerialName("scaffold")
data class ScaffoldNode(
    override val id: String,
    val content: UiNode,
    val leftSidebar: UiNode? = null,
    val rightSidebar: UiNode? = null,
    val topBar: UiNode? = null,
    val bottomBar: UiNode? = null,
    val floatingPanels: List<FloatingPanelNode> = emptyList(),
    override val modifier: UiModifier = UiModifier.None
) : UiNode

/**
 * Floating panel attached to an anchor (e.g. corner) with collapsible support.
 */
@Serializable
@SerialName("floating_panel")
data class FloatingPanelNode @JvmOverloads constructor(
    override val id: String,
    val anchor: FloatingAnchor = FloatingAnchor.TOP_END,
    val title: String? = null,
    val children: List<UiNode> = emptyList(),
    val isCollapsible: Boolean = true,
    val isCollapsed: Boolean = false,
    override val modifier: UiModifier = UiModifier.None,
    val compact: Boolean = false
) : UiNode

/**
 * Collapsible edge sidebar attached to a boundary (left, top, right, bottom).
 */
@Serializable
@SerialName("sidebar")
data class SidebarNode(
    override val id: String,
    val attachPoint: SidebarAttachPoint = SidebarAttachPoint.LEFT,
    val title: String? = null,
    val children: List<UiNode> = emptyList(),
    val isCollapsible: Boolean = true,
    val isCollapsed: Boolean = false,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

// ── Control Nodes ───────────────────────────────────────────────────────────

/**
 * Displays formatted text with optional selection support and line-clamping controls.
 */
@Serializable
@SerialName("text")
data class TextNode @JvmOverloads constructor(
    override val id: String,
    val text: String,
    val role: TypographyRole = TypographyRole.BODY_MEDIUM,
    val color: SemanticColor? = null,
    override val modifier: UiModifier = UiModifier.None,
    val selectable: Boolean = false,
    val maxLines: Int? = null,
    val overflowEllipsis: Boolean = false
) : UiNode

@Serializable
@SerialName("button")
data class ButtonNode @JvmOverloads constructor(
    override val id: String,
    val label: String,
    val action: String? = null,
    val isPrimary: Boolean = true,
    val isDestructive: Boolean = false,
    override val modifier: UiModifier = UiModifier.None,
    val isTonal: Boolean = false,
    val iconName: String? = null
) : UiNode

/**
 * Single connected pill item inside a Material 3 Expressive [ButtonGroupNode].
 *
 * Emits [UiEvent.Click] on the parent [ButtonGroupNode.id] with `action = item.action ?: item.key`.
 */
@Serializable
data class ButtonGroupItem @JvmOverloads constructor(
    val key: String,
    val label: String,
    val action: String? = null,
    val iconName: String? = null,
    val selected: Boolean = false,
    val color: SemanticColor? = null,
    val enabled: Boolean = true,
    val tooltip: String? = null
)

/**
 * Connected Material 3 Expressive pill group for navigation strips, mode switchers,
 * compact action clusters, or color-coded integration state groups without dot clutter.
 */
@Serializable
@SerialName("button_group")
data class ButtonGroupNode @JvmOverloads constructor(
    override val id: String,
    val items: List<ButtonGroupItem> = emptyList(),
    val selectionMode: Boolean = true,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

/**
 * Contextual popup menu item inside a [SplitButtonNode] anchored dropdown menu.
 */
@Serializable
data class ActionMenuItem @JvmOverloads constructor(
    val key: String,
    val label: String,
    val action: String? = null,
    val iconName: String? = null,
    val isDestructive: Boolean = false,
    val enabled: Boolean = true,
    val startsSection: Boolean = false
)

/**
 * Material 3 Expressive Split Button combining a primary pill action with an expandable
 * connected chevron trigger opening a contextual popup menu of related actions.
 */
@Serializable
@SerialName("split_button")
data class SplitButtonNode @JvmOverloads constructor(
    override val id: String,
    val label: String,
    val primaryAction: String,
    val iconName: String? = null,
    val isTonal: Boolean = false,
    val menuItems: List<ActionMenuItem> = emptyList(),
    val menuTooltip: String? = null,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

/**
 * Image scaling strategy applied inside [ImageNode] containers.
 */
@Serializable
enum class ImageScale {
    FIT,
    CROP,
    FILL_BOUNDS,
    INSIDE
}

/**
 * Text input field designed for seamless state synchronization.
 *
 * @property revision Incremented when the plugin intentionally overwrites the text (e.g. form reset),
 * preventing routine recomposition from overriding user typing.
 * @property forceOverwrite When true, applies the plugin text immediately even if currently focused.
 * @property isSecret When true, masks entered characters with a reveal/hide visibility toggle.
 */
@Serializable
@SerialName("text_input")
data class TextInputNode @JvmOverloads constructor(
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
    override val modifier: UiModifier = UiModifier.None,
    val isSecret: Boolean = false
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

/**
 * Option entry inside a [RadioGroupNode].
 */
@Serializable
data class RadioOptionItem @JvmOverloads constructor(
    val key: String,
    val label: String,
    val subtitle: String? = null,
    val enabled: Boolean = true
)

/**
 * Exclusive single-choice radio group control emitting [UiEvent.OptionSelected].
 */
@Serializable
@SerialName("radio_group")
data class RadioGroupNode @JvmOverloads constructor(
    override val id: String,
    val options: List<RadioOptionItem>,
    val selectedKey: String? = null,
    val label: String? = null,
    val horizontal: Boolean = false,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

/**
 * Numeric stepper / input control supporting bounded values, optional decimal entry, and unit labels.
 */
@Serializable
@SerialName("number_input")
data class NumberInputNode @JvmOverloads constructor(
    override val id: String,
    val value: Double = 0.0,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double = 1.0,
    val isDecimal: Boolean = false,
    val unitSuffix: String? = null,
    val label: String? = null,
    val placeholder: String? = null,
    val revision: Int = 0,
    val forceOverwrite: Boolean = false,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

/**
 * Search field with clear affordance and optional suggestion popup emitting [UiEvent.TextChanged] and [UiEvent.Click].
 */
@Serializable
@SerialName("search_bar")
data class SearchBarNode @JvmOverloads constructor(
    override val id: String,
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val placeholder: String? = null,
    val label: String? = null,
    val showClearButton: Boolean = true,
    val action: String? = null,
    val debounceMs: Long = 150L,
    val revision: Int = 0,
    val forceOverwrite: Boolean = false,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

// ── Media & Visual Nodes ────────────────────────────────────────────────────

@Serializable
@SerialName("image")
data class ImageNode @JvmOverloads constructor(
    override val id: String,
    val asset: AssetReference,
    val contentDescription: String? = null,
    override val modifier: UiModifier = UiModifier.None,
    val contentScale: ImageScale = ImageScale.FIT,
    val clickable: Boolean = false,
    val action: String? = null,
    val selected: Boolean = false,
    val badgeText: String? = null,
    val zoomable: Boolean = false
) : UiNode

@Serializable
@SerialName("icon")
data class IconNode(
    override val id: String,
    val name: String,
    val tint: SemanticColor? = null,
    val size: ControlSize = ControlSize.MEDIUM,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

@Serializable
@SerialName("icon_button")
data class IconButtonNode(
    override val id: String,
    val iconName: String,
    val action: String? = null,
    val tooltip: String? = null,
    val tint: SemanticColor? = null,
    val isTonal: Boolean = false,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

@Serializable
@SerialName("progress_bar")
data class ProgressBarNode(
    override val id: String,
    val progress: Float? = null,
    val secondaryProgress: Float? = null,
    val label: String? = null,
    val statusText: String? = null,
    val isCircular: Boolean = false,
    val color: SemanticColor = SemanticColor.PRIMARY,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

@Serializable
data class TabItem(
    val key: String,
    val label: String,
    val iconName: String? = null,
    val badgeText: String? = null
)

@Serializable
@SerialName("tabs")
data class TabsNode(
    override val id: String,
    val tabs: List<TabItem> = emptyList(),
    val selectedTabKey: String,
    val content: UiNode,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

@Serializable
@SerialName("list_item")
data class ListItemNode @JvmOverloads constructor(
    override val id: String,
    val title: String,
    val subtitle: String? = null,
    val leadingIcon: String? = null,
    val badgeText: String? = null,
    val badgeColor: SemanticColor? = null,
    val selected: Boolean = false,
    val selectable: Boolean = false,
    val trailingContent: List<UiNode> = emptyList(),
    override val modifier: UiModifier = UiModifier.None,
    val leadingAsset: AssetReference? = null,
    val action: String? = null
) : UiNode

@Serializable
@SerialName("checkbox")
data class CheckboxNode(
    override val id: String,
    val checked: Boolean,
    val label: String? = null,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

@Serializable
data class ChipItem(
    val key: String,
    val label: String,
    val iconName: String? = null
)

@Serializable
@SerialName("chip_group")
data class ChipGroupNode(
    override val id: String,
    val chips: List<ChipItem> = emptyList(),
    val selectedKeys: Set<String> = emptySet(),
    val multiSelect: Boolean = false,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

@Serializable
@SerialName("alert_banner")
data class AlertBannerNode(
    override val id: String,
    val title: String? = null,
    val message: String,
    val severity: SemanticColor = SemanticColor.INFO,
    val iconName: String? = null,
    val actionLabel: String? = null,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

@Serializable
@SerialName("empty_state")
data class EmptyStateNode(
    override val id: String,
    val title: String,
    val description: String? = null,
    val iconName: String = "info",
    val actionLabel: String? = null,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

@Serializable
@SerialName("dialog")
data class DialogNode(
    override val id: String,
    val title: String,
    val isOpen: Boolean = true,
    val confirmLabel: String = "OK",
    val dismissLabel: String? = "Cancel",
    val isDestructive: Boolean = false,
    val children: List<UiNode> = emptyList(),
    override val modifier: UiModifier = UiModifier.None
) : UiNode

@Serializable
@SerialName("split_pane")
data class SplitPaneNode @JvmOverloads constructor(
    override val id: String,
    val first: UiNode,
    val second: UiNode,
    val splitFraction: Float = 0.35f,
    val isHorizontal: Boolean = true,
    override val modifier: UiModifier = UiModifier.None,
    val resizable: Boolean = true
) : UiNode

@Serializable
@SerialName("spacer")
data class SpacerNode(
    override val id: String,
    val size: SpacingSize = SpacingSize.MEDIUM,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

@Serializable
@SerialName("scrollable_row")
data class ScrollableRowNode(
    override val id: String,
    val children: List<UiNode> = emptyList(),
    val spacing: SpacingSize = SpacingSize.NONE,
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
    val viewportConfig: BoardViewportConfig = BoardViewportConfig(),
    val layers: List<BoardLayer> = emptyList(),
    val selectedLayerIds: Set<String> = emptySet(),
    val activeTool: BoardTool = BoardTool.Brush(),
    val clearRevision: Int = 0,
    val showToolbar: Boolean = false,
    val showZoomControls: Boolean = true,
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
 * Collapsible section header wrapping child SDUI nodes, emitting [UiEvent.ToggleChanged] when toggled.
 */
@Serializable
@SerialName("accordion")
data class AccordionNode @JvmOverloads constructor(
    override val id: String,
    val title: String,
    val subtitle: String? = null,
    val expanded: Boolean = false,
    val leadingIcon: String? = null,
    val badgeText: String? = null,
    val badgeColor: SemanticColor? = null,
    val children: List<UiNode> = emptyList(),
    override val modifier: UiModifier = UiModifier.None
) : UiNode

/**
 * Monospace source code or JSON viewer with optional line numbers, line wrapping, and one-click clipboard copy.
 */
@Serializable
@SerialName("code_block")
data class CodeBlockNode @JvmOverloads constructor(
    override val id: String,
    val code: String,
    val language: String? = null,
    val title: String? = null,
    val showLineNumbers: Boolean = true,
    val wrapLines: Boolean = false,
    val showCopyButton: Boolean = true,
    val maxHeightDp: Int? = null,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

/**
 * Error boundary node injected by the Pre-Composition AST Sanitizer when a subtree
 * violates tree constraints (e.g. max nesting depth, max node count, NaN ranges).
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
            is GridNode -> "grid"
            is ScaffoldNode -> "scaffold"
            is FloatingPanelNode -> "floating_panel"
            is SidebarNode -> "sidebar"
            is TextNode -> "text"
            is ButtonNode -> "button"
            is ButtonGroupNode -> "button_group"
            is SplitButtonNode -> "split_button"
            is TextInputNode -> "text_input"
            is ToggleNode -> "toggle"
            is SliderNode -> "slider"
            is DropdownNode -> "dropdown"
            is ImageNode -> "image"
            is IconNode -> "icon"
            is IconButtonNode -> "icon_button"
            is ProgressBarNode -> "progress_bar"
            is TabsNode -> "tabs"
            is ListItemNode -> "list_item"
            is CheckboxNode -> "checkbox"
            is ChipGroupNode -> "chip_group"
            is AlertBannerNode -> "alert_banner"
            is EmptyStateNode -> "empty_state"
            is DialogNode -> "dialog"
            is SplitPaneNode -> "split_pane"
            is SpacerNode -> "spacer"
            is ScrollableRowNode -> "scrollable_row"
            is BadgeNode -> "badge"
            is DividerNode -> "divider"
            is RadioGroupNode -> "radio_group"
            is NumberInputNode -> "number_input"
            is SearchBarNode -> "search_bar"
            is AccordionNode -> "accordion"
            is CodeBlockNode -> "code_block"
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
            is GridNode -> jsonEncoder.json.encodeToJsonElement(GridNode.serializer(), value)
            is ScaffoldNode -> jsonEncoder.json.encodeToJsonElement(ScaffoldNode.serializer(), value)
            is FloatingPanelNode -> jsonEncoder.json.encodeToJsonElement(FloatingPanelNode.serializer(), value)
            is SidebarNode -> jsonEncoder.json.encodeToJsonElement(SidebarNode.serializer(), value)
            is TextNode -> jsonEncoder.json.encodeToJsonElement(TextNode.serializer(), value)
            is ButtonNode -> jsonEncoder.json.encodeToJsonElement(ButtonNode.serializer(), value)
            is ButtonGroupNode -> jsonEncoder.json.encodeToJsonElement(ButtonGroupNode.serializer(), value)
            is SplitButtonNode -> jsonEncoder.json.encodeToJsonElement(SplitButtonNode.serializer(), value)
            is TextInputNode -> jsonEncoder.json.encodeToJsonElement(TextInputNode.serializer(), value)
            is ToggleNode -> jsonEncoder.json.encodeToJsonElement(ToggleNode.serializer(), value)
            is SliderNode -> jsonEncoder.json.encodeToJsonElement(SliderNode.serializer(), value)
            is DropdownNode -> jsonEncoder.json.encodeToJsonElement(DropdownNode.serializer(), value)
            is ImageNode -> jsonEncoder.json.encodeToJsonElement(ImageNode.serializer(), value)
            is IconNode -> jsonEncoder.json.encodeToJsonElement(IconNode.serializer(), value)
            is IconButtonNode -> jsonEncoder.json.encodeToJsonElement(IconButtonNode.serializer(), value)
            is ProgressBarNode -> jsonEncoder.json.encodeToJsonElement(ProgressBarNode.serializer(), value)
            is TabsNode -> jsonEncoder.json.encodeToJsonElement(TabsNode.serializer(), value)
            is ListItemNode -> jsonEncoder.json.encodeToJsonElement(ListItemNode.serializer(), value)
            is CheckboxNode -> jsonEncoder.json.encodeToJsonElement(CheckboxNode.serializer(), value)
            is ChipGroupNode -> jsonEncoder.json.encodeToJsonElement(ChipGroupNode.serializer(), value)
            is AlertBannerNode -> jsonEncoder.json.encodeToJsonElement(AlertBannerNode.serializer(), value)
            is EmptyStateNode -> jsonEncoder.json.encodeToJsonElement(EmptyStateNode.serializer(), value)
            is DialogNode -> jsonEncoder.json.encodeToJsonElement(DialogNode.serializer(), value)
            is SplitPaneNode -> jsonEncoder.json.encodeToJsonElement(SplitPaneNode.serializer(), value)
            is SpacerNode -> jsonEncoder.json.encodeToJsonElement(SpacerNode.serializer(), value)
            is ScrollableRowNode -> jsonEncoder.json.encodeToJsonElement(ScrollableRowNode.serializer(), value)
            is BadgeNode -> jsonEncoder.json.encodeToJsonElement(BadgeNode.serializer(), value)
            is DividerNode -> jsonEncoder.json.encodeToJsonElement(DividerNode.serializer(), value)
            is RadioGroupNode -> jsonEncoder.json.encodeToJsonElement(RadioGroupNode.serializer(), value)
            is NumberInputNode -> jsonEncoder.json.encodeToJsonElement(NumberInputNode.serializer(), value)
            is SearchBarNode -> jsonEncoder.json.encodeToJsonElement(SearchBarNode.serializer(), value)
            is AccordionNode -> jsonEncoder.json.encodeToJsonElement(AccordionNode.serializer(), value)
            is CodeBlockNode -> jsonEncoder.json.encodeToJsonElement(CodeBlockNode.serializer(), value)
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
            "grid" -> GridNode.serializer()
            "scaffold" -> ScaffoldNode.serializer()
            "floating_panel" -> FloatingPanelNode.serializer()
            "sidebar" -> SidebarNode.serializer()
            "text" -> TextNode.serializer()
            "button" -> ButtonNode.serializer()
            "button_group" -> ButtonGroupNode.serializer()
            "split_button" -> SplitButtonNode.serializer()
            "text_input" -> TextInputNode.serializer()
            "toggle" -> ToggleNode.serializer()
            "slider" -> SliderNode.serializer()
            "dropdown" -> DropdownNode.serializer()
            "image" -> ImageNode.serializer()
            "icon" -> IconNode.serializer()
            "icon_button" -> IconButtonNode.serializer()
            "progress_bar" -> ProgressBarNode.serializer()
            "tabs" -> TabsNode.serializer()
            "list_item" -> ListItemNode.serializer()
            "checkbox" -> CheckboxNode.serializer()
            "chip_group" -> ChipGroupNode.serializer()
            "alert_banner" -> AlertBannerNode.serializer()
            "empty_state" -> EmptyStateNode.serializer()
            "dialog" -> DialogNode.serializer()
            "split_pane" -> SplitPaneNode.serializer()
            "spacer" -> SpacerNode.serializer()
            "scrollable_row" -> ScrollableRowNode.serializer()
            "badge" -> BadgeNode.serializer()
            "divider" -> DividerNode.serializer()
            "radio_group" -> RadioGroupNode.serializer()
            "number_input" -> NumberInputNode.serializer()
            "search_bar" -> SearchBarNode.serializer()
            "accordion" -> AccordionNode.serializer()
            "code_block" -> CodeBlockNode.serializer()
            "board" -> BoardNode.serializer()
            "error_boundary" -> ErrorBoundaryNode.serializer()
            "unknown" -> UnknownNodeCustomSerializer
            else -> UnknownNodeCustomSerializer
        }
        val targetElement = if (element is JsonObject && deserializer !== UnknownNodeCustomSerializer) {
            JsonObject(element.filterKeys { it != "type" })
        } else {
            element
        }
        return input.json.decodeFromJsonElement(deserializer, targetElement)
    }
}
