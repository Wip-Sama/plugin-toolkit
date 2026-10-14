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
    val aspectRatio: Float? = null,
    val alignment: AlignmentRole? = null,
    val controlSize: ControlSize? = null,
    val enabled: Boolean = true,
    val visible: Boolean = true
) {
    fun padding(size: SpacingSize): UiModifier = copy(padding = size)
    fun fillMaxWidth(fraction: Float = 1.0f): UiModifier = copy(fillMaxWidth = fraction)
    fun fillMaxHeight(fraction: Float = 1.0f): UiModifier = copy(fillMaxHeight = fraction)
    fun weight(weight: Float): UiModifier = copy(weight = weight)
    fun aspectRatio(ratio: Float): UiModifier = copy(aspectRatio = ratio)
    fun align(alignment: AlignmentRole): UiModifier = copy(alignment = alignment)
    fun controlSize(size: ControlSize): UiModifier = copy(controlSize = size)
    fun enabled(enabled: Boolean): UiModifier = copy(enabled = enabled)
    fun visible(visible: Boolean): UiModifier = copy(visible = visible)

    companion object {
        val None = UiModifier()
    }
}
