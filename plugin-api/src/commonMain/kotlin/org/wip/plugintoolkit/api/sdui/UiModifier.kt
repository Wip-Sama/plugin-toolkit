package org.wip.plugintoolkit.api.sdui

import kotlinx.serialization.Serializable

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
 * Declarative modifiers configuring the visual presentation, layout constraints,
 * and behavior of an SDUI node.
 */
@Serializable
data class UiModifier(
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
    val visible: Boolean = true
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
    fun enabled(enabled: Boolean): UiModifier = copy(enabled = enabled)
    fun visible(visible: Boolean): UiModifier = copy(visible = visible)

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
        fun enabled(enabled: Boolean): UiModifier = None.enabled(enabled)
        fun visible(visible: Boolean): UiModifier = None.visible(visible)
    }
}
