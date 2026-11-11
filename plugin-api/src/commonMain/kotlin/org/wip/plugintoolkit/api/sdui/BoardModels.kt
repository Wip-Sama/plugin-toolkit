package org.wip.plugintoolkit.api.sdui

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val MIN_NORMALIZED_POINT = 0f
private const val MAX_NORMALIZED_POINT = 1f
private const val MIN_NORMALIZED_SIZE = 0.01f
private const val MAX_NORMALIZED_SIZE = 1f
private const val DEFAULT_NORMALIZED_SIZE = 0.2f

/**
 * Normalized 2D coordinate inside a canvas or image, in range [0.0f, 1.0f].
 * Decoupled from host screen resolution and zoom/pan transformations.
 */
@Serializable
data class NormalizedPoint(
    val x: Float,
    val y: Float
) {
    fun coercedInBounds(): NormalizedPoint = Companion.sanitized(x, y)

    companion object {
        fun sanitized(x: Float, y: Float): NormalizedPoint {
            val safeX = if (x.isNaN() || x.isInfinite()) MIN_NORMALIZED_POINT else x.coerceIn(MIN_NORMALIZED_POINT, MAX_NORMALIZED_POINT)
            val safeY = if (y.isNaN() || y.isInfinite()) MIN_NORMALIZED_POINT else y.coerceIn(MIN_NORMALIZED_POINT, MAX_NORMALIZED_POINT)
            return NormalizedPoint(safeX, safeY)
        }
    }
}

/**
 * Normalized 2D size inside a board, in range [0.01f, 1.0f].
 */
@Serializable
data class NormalizedSize(
    val width: Float,
    val height: Float
) {
    fun coercedInBounds(): NormalizedSize = Companion.sanitized(width, height)

    companion object {
        fun sanitized(width: Float, height: Float): NormalizedSize {
            val safeW = if (width.isNaN() || width.isInfinite()) DEFAULT_NORMALIZED_SIZE else width.coerceIn(MIN_NORMALIZED_SIZE, MAX_NORMALIZED_SIZE)
            val safeH = if (height.isNaN() || height.isInfinite()) DEFAULT_NORMALIZED_SIZE else height.coerceIn(MIN_NORMALIZED_SIZE, MAX_NORMALIZED_SIZE)
            return NormalizedSize(safeW, safeH)
        }
    }
}

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
 * Background grid visual style for a [BoardNode] viewport.
 */
@Serializable
enum class BoardGridStyle {
    NONE,
    DOTS,
    LINES,
    CROSSHAIRS
}

/**
 * Geometric shape primitive types supported by [BoardLayer.ShapeLayer] and [BoardTool.Shape].
 */
@Serializable
enum class BoardShapeType {
    RECTANGLE,
    ROUNDED_RECT,
    ELLIPSE,
    CALLOUT_BUBBLE,
    ARROW
}

/**
 * Viewport and camera configuration for a spatial [BoardNode].
 */
@Serializable
data class BoardViewportConfig(
    val panEnabled: Boolean = true,
    val zoomEnabled: Boolean = true,
    val minScale: Float = 0.25f,
    val maxScale: Float = 4.0f,
    val initialScale: Float = 1.0f,
    val gridStyle: BoardGridStyle = BoardGridStyle.DOTS,
    val snapToGrid: Boolean = false,
    val gridStep: Float = 0.025f,
    val artboardAspectRatio: Float? = null
)

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
 * Active interactive tool selected on the board.
 */
@Serializable
sealed interface BoardTool {
    /**
     * Pointer tool for selecting, moving, resizing, and inline-editing board layers.
     */
    @Serializable
    @SerialName("select")
    data class Select(
        val allowMove: Boolean = true,
        val allowResize: Boolean = true,
        val allowInlineEdit: Boolean = true
    ) : BoardTool

    /**
     * Hand tool for panning the board viewport camera.
     */
    @Serializable
    @SerialName("pan")
    data object Pan : BoardTool

    /**
     * Tool for placing new editable [BoardLayer.TextLayer] elements on click.
     */
    @Serializable
    @SerialName("place_text")
    data class PlaceText(
        val defaultText: String = "New Text",
        val color: SemanticColor = SemanticColor.ON_SURFACE,
        val backgroundColor: SemanticColor? = SemanticColor.SURFACE,
        val role: TypographyRole = TypographyRole.TITLE_MEDIUM
    ) : BoardTool

    /**
     * Tool for dragging out geometric or speech-bubble [BoardLayer.ShapeLayer] elements.
     */
    @Serializable
    @SerialName("shape")
    data class Shape(
        val shapeType: BoardShapeType = BoardShapeType.ROUNDED_RECT,
        val strokeColor: SemanticColor = SemanticColor.PRIMARY,
        val fillColor: SemanticColor? = null,
        val strokeWidth: Float = 2f
    ) : BoardTool

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
 * A persistent visual or interactive component layer rendered on the spatial board.
 */
@Serializable
sealed interface BoardLayer {
    val id: String
    val zIndex: Int
    val visible: Boolean
    val locked: Boolean

    /**
     * Freehand vector stroke layer.
     */
    @Serializable
    @SerialName("path")
    data class PathLayer(
        override val id: String,
        val path: VectorPathData,
        override val zIndex: Int = 0,
        override val visible: Boolean = true,
        override val locked: Boolean = false,
        val selectable: Boolean = false
    ) : BoardLayer

    /**
     * Selectable, draggable, and inline-editable text box layer on the board.
     */
    @Serializable
    @SerialName("text")
    data class TextLayer(
        override val id: String,
        val text: String,
        val position: NormalizedPoint,
        val size: NormalizedSize? = null,
        val color: SemanticColor = SemanticColor.PRIMARY,
        val backgroundColor: SemanticColor? = null,
        val borderColor: SemanticColor? = null,
        val role: TypographyRole = TypographyRole.BODY_LARGE,
        override val zIndex: Int = 0,
        override val visible: Boolean = true,
        override val locked: Boolean = false,
        val selectable: Boolean = true,
        val draggable: Boolean = true,
        val editable: Boolean = true
    ) : BoardLayer

    /**
     * Raster or vector image asset placed at a normalized position and size on the board.
     */
    @Serializable
    @SerialName("image")
    data class ImageLayer(
        override val id: String,
        val asset: AssetReference,
        val position: NormalizedPoint = NormalizedPoint(0f, 0f),
        val size: NormalizedSize = NormalizedSize(1f, 1f),
        val opacity: Float = 1f,
        val contentDescription: String? = null,
        override val zIndex: Int = 0,
        override val visible: Boolean = true,
        override val locked: Boolean = false,
        val selectable: Boolean = true,
        val draggable: Boolean = true,
        val resizable: Boolean = true
    ) : BoardLayer

    /**
     * Geometric shape or callout bubble layer placed on the board.
     */
    @Serializable
    @SerialName("shape")
    data class ShapeLayer(
        override val id: String,
        val shapeType: BoardShapeType = BoardShapeType.ROUNDED_RECT,
        val position: NormalizedPoint,
        val size: NormalizedSize,
        val strokeColor: SemanticColor = SemanticColor.PRIMARY,
        val fillColor: SemanticColor? = null,
        val strokeWidth: Float = 2f,
        val text: String? = null,
        override val zIndex: Int = 0,
        override val visible: Boolean = true,
        override val locked: Boolean = false,
        val selectable: Boolean = true,
        val draggable: Boolean = true,
        val resizable: Boolean = true
    ) : BoardLayer

    /**
     * Arbitrary interactive SDUI [UiNode] component placed spatially on the board
     * (similar to nodes on the Flow Editor canvas).
     */
    @Serializable
    @SerialName("component")
    data class ComponentLayer(
        override val id: String,
        val position: NormalizedPoint,
        val size: NormalizedSize? = null,
        val content: UiNode,
        override val zIndex: Int = 0,
        override val visible: Boolean = true,
        override val locked: Boolean = false,
        val selectable: Boolean = true,
        val draggable: Boolean = true,
        val resizable: Boolean = false
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
     * Platform URI string (e.g., file://... or data:image/...).
     */
    @Serializable
    @SerialName("uri")
    data class Uri(val uriString: String) : AssetReference

    /**
     * Pre-cached asset identifier stored in the plugin cache FileSystem or host ImageLoader cache.
     */
    @Serializable
    @SerialName("cached")
    data class Cached(val assetId: String) : AssetReference
}
