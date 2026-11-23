package org.wip.plugintoolkit.api.sdui

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

/**
 * Semantic spacing tokens mapped directly to ToolkitTheme.spacing.
 */
@Serializable
enum class SpacingSize {
    NONE,
    EXTRA_SMALL,
    SMALL,
    MEDIUM_SMALL,
    MEDIUM,
    LARGE,
    EXTRA_LARGE
}

/**
 * Semantic corner radius tokens mapped to ToolkitTheme.shapes.
 */
@Serializable
enum class ShapeRole {
    NONE,
    EXTRA_SMALL,
    SMALL,
    MEDIUM,
    LARGE,
    EXTRA_LARGE,
    PILL
}

/**
 * Semantic typography roles mapped to ToolkitTheme.typography.
 */
@Serializable
enum class TypographyRole {
    DISPLAY,
    HEADLINE,
    TITLE_LARGE,
    TITLE_MEDIUM,
    BODY_LARGE,
    BODY_MEDIUM,
    BODY_SMALL,
    LABEL
}

/**
 * Semantic alignment options for layout positioning.
 */
@Serializable
enum class AlignmentRole {
    START,
    CENTER,
    END,
    TOP,
    BOTTOM,
    TOP_START,
    TOP_CENTER,
    TOP_END,
    BOTTOM_START,
    BOTTOM_CENTER,
    BOTTOM_END,
    CENTER_START,
    CENTER_END
}

/**
 * Anchor positions for floating SDUI panels.
 */
@Serializable
enum class FloatingAnchor {
    TOP_START,
    TOP_CENTER,
    TOP_END,
    BOTTOM_START,
    BOTTOM_CENTER,
    BOTTOM_END,
    CENTER_START,
    CENTER_END,
    CENTER
}

/**
 * Edge attachment points for collapsible SDUI sidebars.
 */
@Serializable
enum class SidebarAttachPoint {
    LEFT,
    TOP,
    RIGHT,
    BOTTOM
}

/**
 * Standard sizing for interactive controls (e.g. Buttons, Inputs).
 */
@Serializable
enum class ControlSize {
    SMALL,
    MEDIUM,
    LARGE
}

/**
 * Declarative keyboard shortcut binding attached to an SDUI node via [UiModifier.shortcut].
 *
 * When pressed inside the interactive UI session, the host dispatches [UiEvent.ShortcutTriggered]
 * targeting the declaring node ID and [action]. Unmodified / shift-only keys are automatically
 * suppressed while typing inside a focused [TextInputNode] unless [allowWhileTyping] is true.
 *
 * @property key Canonical key name (e.g. `"DELETE"`, `"BACKSPACE"`, `"ARROW_LEFT"`, `"ESCAPE"`, `"S"`, `"1"`).
 * @property action Semantic action identifier emitted in [UiEvent.ShortcutTriggered.action].
 * @property ctrl Require Control modifier key.
 * @property shift Require Shift modifier key.
 * @property alt Require Alt/Option modifier key.
 * @property meta Require Meta/Command/Super modifier key.
 * @property allowWhileTyping Allow firing even while a text input field holds keyboard focus.
 * @property label Optional short display hint for tooltips/cheatsheets (e.g. `"Ctrl+S"`).
 */
@Serializable
data class UiShortcut(
    val key: String,
    val action: String,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false,
    val allowWhileTyping: Boolean = false,
    val label: String? = null
) {
    companion object {
        const val KEY_DELETE = "DELETE"
        const val KEY_BACKSPACE = "BACKSPACE"
        const val KEY_ESCAPE = "ESCAPE"
        const val KEY_ENTER = "ENTER"
        const val KEY_SPACE = "SPACE"
        const val KEY_TAB = "TAB"
        const val KEY_ARROW_LEFT = "ARROW_LEFT"
        const val KEY_ARROW_RIGHT = "ARROW_RIGHT"
        const val KEY_ARROW_UP = "ARROW_UP"
        const val KEY_ARROW_DOWN = "ARROW_DOWN"
        const val KEY_HOME = "HOME"
        const val KEY_END = "END"
        const val KEY_PAGE_UP = "PAGE_UP"
        const val KEY_PAGE_DOWN = "PAGE_DOWN"
    }
}

/**
 * Declarative external OS file/folder drop target attached to an SDUI node via [UiModifier.onFileDrop].
 *
 * When valid files or directories are dropped from the desktop OS onto the node, the host grants
 * read access to every returned path in [UiSessionContext.hostFileSystem] and dispatches
 * [UiEvent.FilesDropped].
 *
 * @property action Semantic action identifier emitted in [UiEvent.FilesDropped.action].
 * @property allowedExtensions Optional lowercase/case-insensitive extension filter (e.g. `listOf("png", "jpg", "webp")`). Empty accepts any file extension.
 * @property acceptFiles Whether regular files are accepted.
 * @property acceptDirectories Whether directories are accepted.
 * @property maxFiles Optional cap on the number of accepted dropped paths (`null` = unlimited).
 * @property hint Optional visual overlay prompt shown when dragging files over the drop target.
 */
@Serializable
data class UiDropTarget(
    val action: String = "files_dropped",
    val allowedExtensions: List<String> = emptyList(),
    val acceptFiles: Boolean = true,
    val acceptDirectories: Boolean = false,
    val maxFiles: Int? = null,
    val hint: String? = null
)

/**
 * Declarative modifiers configuring the visual presentation, layout constraints,
 * and behavior of an SDUI node.
 */
@Serializable
data class UiModifier @JvmOverloads constructor(
    val padding: SpacingSize = SpacingSize.NONE,
    val fillMaxWidth: Float? = null,
    val fillMaxHeight: Float? = null,
    val weight: Float? = null,
    val widthDp: Int? = null,
    val heightDp: Int? = null,
    val minWidthDp: Int? = null,
    val maxWidthDp: Int? = null,
    val minHeightDp: Int? = null,
    val maxHeightDp: Int? = null,
    val aspectRatio: Float? = null,
    val alignment: AlignmentRole? = null,
    val controlSize: ControlSize? = null,
    val backgroundColor: SemanticColor? = null,
    val borderColor: SemanticColor? = null,
    val clipRounded: Boolean = false,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val shortcuts: List<UiShortcut> = emptyList(),
    val dropTarget: UiDropTarget? = null,
    val tooltip: String? = null,
    val contextMenu: List<ActionMenuItem> = emptyList(),
    val cornerRadius: ShapeRole? = null
) {
    fun padding(size: SpacingSize): UiModifier = copy(padding = size)
    fun fillMaxWidth(fraction: Float = 1.0f): UiModifier = copy(fillMaxWidth = fraction)
    fun fillMaxHeight(fraction: Float = 1.0f): UiModifier = copy(fillMaxHeight = fraction)
    fun fillMaxSize(fraction: Float = 1.0f): UiModifier = copy(fillMaxWidth = fraction, fillMaxHeight = fraction)
    fun weight(weight: Float): UiModifier = copy(weight = weight)
    fun width(dp: Int): UiModifier = copy(widthDp = dp)
    fun height(dp: Int): UiModifier = copy(heightDp = dp)
    fun size(widthDp: Int, heightDp: Int = widthDp): UiModifier = copy(widthDp = widthDp, heightDp = heightDp)
    fun widthIn(min: Int? = null, max: Int? = null): UiModifier = copy(minWidthDp = min, maxWidthDp = max)
    fun heightIn(min: Int? = null, max: Int? = null): UiModifier = copy(minHeightDp = min, maxHeightDp = max)
    fun aspectRatio(ratio: Float): UiModifier = copy(aspectRatio = ratio)
    fun align(alignment: AlignmentRole): UiModifier = copy(alignment = alignment)
    fun controlSize(size: ControlSize): UiModifier = copy(controlSize = size)
    fun background(color: SemanticColor?): UiModifier = copy(backgroundColor = color)
    fun border(color: SemanticColor?): UiModifier = copy(borderColor = color)
    fun clipRounded(rounded: Boolean = true): UiModifier = copy(clipRounded = rounded)
    fun cornerRadius(role: ShapeRole?): UiModifier = copy(cornerRadius = role)
    fun enabled(enabled: Boolean): UiModifier = copy(enabled = enabled)
    fun visible(visible: Boolean): UiModifier = copy(visible = visible)
    fun tooltip(text: String?): UiModifier = copy(tooltip = text)
    fun contextMenu(items: List<ActionMenuItem>): UiModifier = copy(contextMenu = items)
    fun contextMenu(block: ActionMenuBuilder.() -> Unit): UiModifier =
        copy(contextMenu = ActionMenuBuilder().apply(block).buildItems())
    fun shortcut(shortcut: UiShortcut): UiModifier = copy(shortcuts = shortcuts + shortcut)
    fun shortcut(
        key: String,
        action: String,
        ctrl: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
        meta: Boolean = false,
        allowWhileTyping: Boolean = false,
        label: String? = null
    ): UiModifier = copy(
        shortcuts = shortcuts + UiShortcut(
            key = key,
            action = action,
            ctrl = ctrl,
            shift = shift,
            alt = alt,
            meta = meta,
            allowWhileTyping = allowWhileTyping,
            label = label
        )
    )
    fun shortcuts(items: List<UiShortcut>): UiModifier = copy(shortcuts = shortcuts + items)
    fun dropTarget(config: UiDropTarget?): UiModifier = copy(dropTarget = config)
    fun onFileDrop(
        action: String = "files_dropped",
        allowedExtensions: List<String> = emptyList(),
        acceptFiles: Boolean = true,
        acceptDirectories: Boolean = false,
        maxFiles: Int? = null,
        hint: String? = null
    ): UiModifier = copy(
        dropTarget = UiDropTarget(
            action = action,
            allowedExtensions = allowedExtensions,
            acceptFiles = acceptFiles,
            acceptDirectories = acceptDirectories,
            maxFiles = maxFiles,
            hint = hint
        )
    )

    companion object {
        val None = UiModifier()

        fun padding(size: SpacingSize): UiModifier = None.padding(size)
        fun fillMaxWidth(fraction: Float = 1.0f): UiModifier = None.fillMaxWidth(fraction)
        fun fillMaxHeight(fraction: Float = 1.0f): UiModifier = None.fillMaxHeight(fraction)
        fun fillMaxSize(fraction: Float = 1.0f): UiModifier = None.fillMaxSize(fraction)
        fun weight(weight: Float): UiModifier = None.weight(weight)
        fun width(dp: Int): UiModifier = None.width(dp)
        fun height(dp: Int): UiModifier = None.height(dp)
        fun size(widthDp: Int, heightDp: Int = widthDp): UiModifier = None.size(widthDp, heightDp)
        fun widthIn(min: Int? = null, max: Int? = null): UiModifier = None.widthIn(min, max)
        fun heightIn(min: Int? = null, max: Int? = null): UiModifier = None.heightIn(min, max)
        fun aspectRatio(ratio: Float): UiModifier = None.aspectRatio(ratio)
        fun align(alignment: AlignmentRole): UiModifier = None.align(alignment)
        fun controlSize(size: ControlSize): UiModifier = None.controlSize(size)
        fun background(color: SemanticColor?): UiModifier = None.background(color)
        fun border(color: SemanticColor?): UiModifier = None.border(color)
        fun clipRounded(rounded: Boolean = true): UiModifier = None.clipRounded(rounded)
        fun cornerRadius(role: ShapeRole?): UiModifier = None.cornerRadius(role)
        fun enabled(enabled: Boolean): UiModifier = None.enabled(enabled)
        fun visible(visible: Boolean): UiModifier = None.visible(visible)
        fun tooltip(text: String?): UiModifier = None.tooltip(text)
        fun contextMenu(items: List<ActionMenuItem>): UiModifier = None.contextMenu(items)
        fun contextMenu(block: ActionMenuBuilder.() -> Unit): UiModifier = None.contextMenu(block)
        fun shortcut(shortcut: UiShortcut): UiModifier = None.shortcut(shortcut)
        fun shortcut(
            key: String,
            action: String,
            ctrl: Boolean = false,
            shift: Boolean = false,
            alt: Boolean = false,
            meta: Boolean = false,
            allowWhileTyping: Boolean = false,
            label: String? = null
        ): UiModifier = None.shortcut(key, action, ctrl, shift, alt, meta, allowWhileTyping, label)
        fun shortcuts(items: List<UiShortcut>): UiModifier = None.shortcuts(items)
        fun dropTarget(config: UiDropTarget?): UiModifier = None.dropTarget(config)
        fun onFileDrop(
            action: String = "files_dropped",
            allowedExtensions: List<String> = emptyList(),
            acceptFiles: Boolean = true,
            acceptDirectories: Boolean = false,
            maxFiles: Int? = null,
            hint: String? = null
        ): UiModifier = None.onFileDrop(action, allowedExtensions, acceptFiles, acceptDirectories, maxFiles, hint)
    }
}
