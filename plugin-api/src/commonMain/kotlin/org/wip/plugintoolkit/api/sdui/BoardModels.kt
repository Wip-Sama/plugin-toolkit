package org.wip.plugintoolkit.api.sdui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Normalized 2D coordinate inside a canvas or image, in range [0.0f, 1.0f].
 * Decoupled from host screen resolution and zoom/pan transformations.
 */
@Serializable
data class NormalizedPoint(
    val x: Float,
    val y: Float
)

/**
 * Semantic color roles mapped directly to ToolkitTheme color tokens in the host UI.
 */
@Serializable
enum class SemanticColor {
    PRIMARY,
    SECONDARY,
    TERTIARY,
    ERROR,
    SURFACE,
    ON_SURFACE,
    OUTLINE,
    ACCENT,
    HIGHLIGHT
}

/**
 * Serialized vector stroke path data.
 *
 * @property strokeId Unique client-generated ID for optimistic stroke retention (0-flicker).
 * @property points Ordered list of normalized coordinates forming the path.
 * @property color Semantic color role used to render this stroke.
 * @property strokeWidth Stroke width in normalized or display-density scaled units.
 */
@Serializable
data class VectorPathData(
    val strokeId: String,
    val points: List<NormalizedPoint>,
    val color: SemanticColor = SemanticColor.PRIMARY,
    val strokeWidth: Float = 4f
)

/**
 * Active interactive drawing tool selected on the board.
 */
@Serializable
sealed interface BoardTool {
    @Serializable
    @SerialName("brush")
    data class Brush(
        val color: SemanticColor = SemanticColor.PRIMARY,
        val strokeWidth: Float = 4f
    ) : BoardTool

    @Serializable
    @SerialName("highlighter")
    data class Highlighter(
        val color: SemanticColor = SemanticColor.HIGHLIGHT,
        val strokeWidth: Float = 14f
    ) : BoardTool

    @Serializable
    @SerialName("rectangle")
    data class Rectangle(
        val color: SemanticColor = SemanticColor.PRIMARY,
        val strokeWidth: Float = 2f
    ) : BoardTool

    @Serializable
    @SerialName("eraser")
    data class Eraser(
        val radius: Float = 12f
    ) : BoardTool
}

/**
 * A persistent visual layer rendered on top of the background image/canvas.
 */
@Serializable
sealed interface BoardLayer {
    val id: String

    @Serializable
    @SerialName("path")
    data class PathLayer(
        override val id: String,
        val path: VectorPathData
    ) : BoardLayer

    @Serializable
    @SerialName("text")
    data class TextLayer(
        override val id: String,
        val text: String,
        val position: NormalizedPoint,
        val color: SemanticColor = SemanticColor.PRIMARY
    ) : BoardLayer
}

/**
 * Out-of-band binary asset reference.
 * Prevents embedding high-resolution images as Base64 strings in the serializable UI tree.
 */
@Serializable
sealed interface AssetReference {
    /**
     * Relative path within the plugin's managed ScopedFileSystem / PluginFileSystem.
     */
    @Serializable
    @SerialName("relative")
    data class Relative(val path: String) : AssetReference

    /**
     * Platform URI string (e.g., file://...).
     */
    @Serializable
    @SerialName("uri")
    data class Uri(val uriString: String) : AssetReference

    /**
     * Pre-cached asset identifier stored in the host ImageLoader cache.
     */
    @Serializable
    @SerialName("cached")
    data class Cached(val assetId: String) : AssetReference
}
