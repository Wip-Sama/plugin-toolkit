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

/**
 * Programmatic scroll edge target for scrollable containers.
 */
@Serializable
enum class ScrollEdge {
    TOP,
    BOTTOM
}

@Serializable
@SerialName("scrollable_column")
data class ScrollableColumnNode @JvmOverloads constructor(
    override val id: String,
    val children: List<UiNode> = emptyList(),
    val spacing: SpacingSize = SpacingSize.NONE,
    override val modifier: UiModifier = UiModifier.None,
    val reorderable: Boolean = false,
    val scrollToIndex: Int? = null,
    val scrollToEdge: ScrollEdge? = null,
    val scrollRevision: Int = 0
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
    override val modifier: UiModifier = UiModifier.None,
    val scrollToIndex: Int? = null,
    val scrollToEdge: ScrollEdge? = null,
    val scrollRevision: Int = 0
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

/**
 * Visual style preset for [ProgressBarNode].
 */
@Serializable
enum class ProgressBarStyle {
    STANDARD,
    WAVY,
    MORPHING_INDICATOR,
    CONTAINED_INDICATOR
}

/**
 * Progress indicator node supporting standard linear/circular bars, M3 Expressive wavy tracks,
 * and morphing polygon loading indicators.
 */
@Serializable
@SerialName("progress_bar")
data class ProgressBarNode @JvmOverloads constructor(
    override val id: String,
    val progress: Float? = null,
    val secondaryProgress: Float? = null,
    val label: String? = null,
    val statusText: String? = null,
    val isCircular: Boolean = false,
    val color: SemanticColor = SemanticColor.PRIMARY,
    override val modifier: UiModifier = UiModifier.None,
    val style: ProgressBarStyle = ProgressBarStyle.STANDARD
) : UiNode

/**
 * Standalone Material 3 Expressive morphing [LoadingIndicatorNode] for indeterminate or
 * bounded loading states.
 */
@Serializable
@SerialName("loading_indicator")
data class LoadingIndicatorNode @JvmOverloads constructor(
    override val id: String,
    val label: String? = null,
    val contained: Boolean = false,
    val progress: Float? = null,
    val color: SemanticColor = SemanticColor.PRIMARY,
    val size: ControlSize = ControlSize.MEDIUM,
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

// ── Structured Data & Rich Controls ─────────────────────────────────────────

/**
 * Column specification for [TableNode].
 */
@Serializable
data class TableColumn @JvmOverloads constructor(
    val key: String,
    val title: String,
    val weight: Float = 1f,
    val widthDp: Int? = null,
    val sortable: Boolean = false
)

/**
 * Row entry inside a [TableNode], keyed by [key] with cell values indexed by column key.
 */
@Serializable
data class TableRow @JvmOverloads constructor(
    val key: String,
    val cells: Map<String, String>,
    val badgeColumnKey: String? = null,
    val badgeColor: SemanticColor? = null
)

/**
 * Structured desktop data table supporting sortable columns, fixed/weighted column widths,
 * sticky headers, row selection/click events, and bidirectional scrollbars.
 */
@Serializable
@SerialName("table")
data class TableNode @JvmOverloads constructor(
    override val id: String,
    val columns: List<TableColumn>,
    val rows: List<TableRow>,
    val selectedRowKeys: Set<String> = emptySet(),
    val sortColumnKey: String? = null,
    val sortAscending: Boolean = true,
    val emptyText: String? = null,
    val maxHeightDp: Int? = null,
    val action: String? = null,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

/**
 * Horizontal wrapping container that automatically flows children onto new rows
 * when horizontal space is constrained.
 */
@Serializable
@SerialName("flow_row")
data class FlowRowNode @JvmOverloads constructor(
    override val id: String,
    val children: List<UiNode> = emptyList(),
    val horizontalSpacing: SpacingSize = SpacingSize.SMALL,
    val verticalSpacing: SpacingSize = SpacingSize.SMALL,
    val maxItemsInEachRow: Int? = null,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

/**
 * Label-value entry inside a [KeyValueListNode].
 */
@Serializable
data class KeyValueEntry @JvmOverloads constructor(
    val key: String,
    val label: String,
    val value: String,
    val monospace: Boolean = false,
    val copyable: Boolean = false,
    val badgeColor: SemanticColor? = null
)

/**
 * Aligned two-column inspector property list with optional monospace values, semantic badges,
 * and one-click clipboard copy.
 */
@Serializable
@SerialName("key_value_list")
data class KeyValueListNode @JvmOverloads constructor(
    override val id: String,
    val entries: List<KeyValueEntry>,
    val title: String? = null,
    val showDividers: Boolean = true,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

/**
 * Lightweight rich formatted text block supporting Markdown headings, bullet/numbered lists,
 * bold/italic/strikethrough/inline code, and clickable links.
 */
@Serializable
@SerialName("markdown_text")
data class MarkdownTextNode @JvmOverloads constructor(
    override val id: String,
    val markdown: String,
    val selectable: Boolean = true,
    val maxLines: Int? = null,
    val linkAction: String? = null,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

/**
 * Multi-choice dropdown selector supporting filter search, batch select-all / clear-all,
 * and selected item summary chips, emitting [UiEvent.MultiOptionSelected].
 */
@Serializable
@SerialName("multi_select_dropdown")
data class MultiSelectDropdownNode @JvmOverloads constructor(
    override val id: String,
    val selectedKeys: Set<String> = emptySet(),
    val options: Map<String, String>,
    val label: String? = null,
    val placeholder: String? = null,
    val searchable: Boolean = true,
    val showSelectAll: Boolean = true,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

/**
 * Dual-thumb slider control for selecting an inclusive numeric interval,
 * emitting [UiEvent.RangeChanged].
 */
@Serializable
@SerialName("range_slider")
data class RangeSliderNode @JvmOverloads constructor(
    override val id: String,
    val startValue: Double,
    val endValue: Double,
    val min: Double = 0.0,
    val max: Double = 1.0,
    val steps: Int = 0,
    val label: String? = null,
    val unitSuffix: String? = null,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

// ── Workflows, Navigation & Pickers ──────────────────────────────────────────

/**
 * Hierarchical node item inside a [TreeViewNode].
 */
@Serializable
data class TreeNodeItem @JvmOverloads constructor(
    val key: String,
    val label: String,
    val iconName: String? = null,
    val expanded: Boolean = false,
    val children: List<TreeNodeItem> = emptyList()
)

/**
 * Collapsible hierarchical tree view supporting keyboard navigation, selection,
 * and expand/collapse events ([UiEvent.TreeNodeToggled] / [UiEvent.OptionSelected]).
 */
@Serializable
@SerialName("tree_view")
data class TreeViewNode @JvmOverloads constructor(
    override val id: String,
    val items: List<TreeNodeItem>,
    val selectedKey: String? = null,
    val maxHeightDp: Int? = null,
    val action: String? = null,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

/**
 * Target selection mode for [FilePickerNode].
 */
@Serializable
enum class FilePickerMode {
    FILE,
    FILES,
    DIRECTORY,
    SAVE
}

/**
 * Inline desktop file/folder picker control integrated with the host's file access bridge,
 * emitting [UiEvent.FilesPicked] when paths are selected or cleared.
 */
@Serializable
@SerialName("file_picker")
data class FilePickerNode @JvmOverloads constructor(
    override val id: String,
    val value: String = "",
    val paths: List<String> = emptyList(),
    val mode: FilePickerMode = FilePickerMode.FILE,
    val allowedExtensions: List<String> = emptyList(),
    val label: String? = null,
    val placeholder: String? = null,
    val defaultFileName: String = "export",
    val defaultExtension: String = "txt",
    val action: String? = null,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

/**
 * Color selection control with swatch trigger, hex entry, and optional preset swatches,
 * emitting [UiEvent.ColorChanged].
 */
@Serializable
@SerialName("color_picker")
data class ColorPickerNode @JvmOverloads constructor(
    override val id: String,
    val colorHex: String = "#6750A4",
    val label: String? = null,
    val presets: List<String> = emptyList(),
    val showHexInput: Boolean = true,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

/**
 * Segment entry inside a [BreadcrumbsNode].
 */
@Serializable
data class BreadcrumbItem @JvmOverloads constructor(
    val key: String,
    val label: String,
    val iconName: String? = null
)

/**
 * Horizontal navigation trail supporting overflow collapse menus and segment selection
 * via [UiEvent.OptionSelected].
 */
@Serializable
@SerialName("breadcrumbs")
data class BreadcrumbsNode @JvmOverloads constructor(
    override val id: String,
    val items: List<BreadcrumbItem>,
    val separator: String = "/",
    val maxVisibleItems: Int = 5,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

/**
 * Desktop pagination bar with page navigation controls and optional page-size menu,
 * emitting [UiEvent.PageChanged].
 */
@Serializable
@SerialName("pagination")
data class PaginationNode @JvmOverloads constructor(
    override val id: String,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val pageSize: Int? = null,
    val pageSizeOptions: List<Int> = emptyList(),
    override val modifier: UiModifier = UiModifier.None
) : UiNode

/**
 * Lifecycle state for an individual step in [StepperNode].
 */
@Serializable
enum class StepStatus {
    PENDING,
    ACTIVE,
    COMPLETED,
    ERROR
}

/**
 * Step entry inside a [StepperNode].
 */
@Serializable
data class StepperItem @JvmOverloads constructor(
    val key: String,
    val label: String,
    val subtitle: String? = null,
    val status: StepStatus = StepStatus.PENDING,
    val enabled: Boolean = true
)

/**
 * Multi-step wizard progress indicator emitting [UiEvent.OptionSelected] for enabled steps.
 */
@Serializable
@SerialName("stepper")
data class StepperNode @JvmOverloads constructor(
    override val id: String,
    val steps: List<StepperItem>,
    val horizontal: Boolean = true,
    override val modifier: UiModifier = UiModifier.None
) : UiNode

/**
 * Standalone dropdown/overflow menu button opening [ActionMenuItem] entries
 * and emitting [UiEvent.Click].
 */
@Serializable
@SerialName("menu")
data class MenuNode @JvmOverloads constructor(
    override val id: String,
    val items: List<ActionMenuItem>,
    val triggerLabel: String? = null,
    val triggerIconName: String? = "more_vert",
    val tooltip: String? = null,
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
            is LoadingIndicatorNode -> "loading_indicator"
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
            is TableNode -> "table"
            is FlowRowNode -> "flow_row"
            is KeyValueListNode -> "key_value_list"
            is MarkdownTextNode -> "markdown_text"
            is MultiSelectDropdownNode -> "multi_select_dropdown"
            is RangeSliderNode -> "range_slider"
            is TreeViewNode -> "tree_view"
            is FilePickerNode -> "file_picker"
            is ColorPickerNode -> "color_picker"
            is BreadcrumbsNode -> "breadcrumbs"
            is PaginationNode -> "pagination"
            is StepperNode -> "stepper"
            is MenuNode -> "menu"
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
            is TableNode -> jsonEncoder.json.encodeToJsonElement(TableNode.serializer(), value)
            is FlowRowNode -> jsonEncoder.json.encodeToJsonElement(FlowRowNode.serializer(), value)
            is KeyValueListNode -> jsonEncoder.json.encodeToJsonElement(KeyValueListNode.serializer(), value)
            is MarkdownTextNode -> jsonEncoder.json.encodeToJsonElement(MarkdownTextNode.serializer(), value)
            is MultiSelectDropdownNode -> jsonEncoder.json.encodeToJsonElement(MultiSelectDropdownNode.serializer(), value)
            is RangeSliderNode -> jsonEncoder.json.encodeToJsonElement(RangeSliderNode.serializer(), value)
            is TreeViewNode -> jsonEncoder.json.encodeToJsonElement(TreeViewNode.serializer(), value)
            is FilePickerNode -> jsonEncoder.json.encodeToJsonElement(FilePickerNode.serializer(), value)
            is ColorPickerNode -> jsonEncoder.json.encodeToJsonElement(ColorPickerNode.serializer(), value)
            is BreadcrumbsNode -> jsonEncoder.json.encodeToJsonElement(BreadcrumbsNode.serializer(), value)
            is PaginationNode -> jsonEncoder.json.encodeToJsonElement(PaginationNode.serializer(), value)
            is StepperNode -> jsonEncoder.json.encodeToJsonElement(StepperNode.serializer(), value)
            is MenuNode -> jsonEncoder.json.encodeToJsonElement(MenuNode.serializer(), value)
            is LoadingIndicatorNode -> jsonEncoder.json.encodeToJsonElement(LoadingIndicatorNode.serializer(), value)
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
            "loading_indicator" -> LoadingIndicatorNode.serializer()
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
            "table" -> TableNode.serializer()
            "flow_row" -> FlowRowNode.serializer()
            "key_value_list" -> KeyValueListNode.serializer()
            "markdown_text" -> MarkdownTextNode.serializer()
            "multi_select_dropdown" -> MultiSelectDropdownNode.serializer()
            "range_slider" -> RangeSliderNode.serializer()
            "tree_view" -> TreeViewNode.serializer()
            "file_picker" -> FilePickerNode.serializer()
            "color_picker" -> ColorPickerNode.serializer()
            "breadcrumbs" -> BreadcrumbsNode.serializer()
            "pagination" -> PaginationNode.serializer()
            "stepper" -> StepperNode.serializer()
            "menu" -> MenuNode.serializer()
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
