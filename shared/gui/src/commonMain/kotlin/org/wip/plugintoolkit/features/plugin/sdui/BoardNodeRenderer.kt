package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.sdui.BoardGridStyle
import org.wip.plugintoolkit.api.sdui.BoardLayer
import org.wip.plugintoolkit.api.sdui.BoardNode
import org.wip.plugintoolkit.api.sdui.BoardShapeType
import org.wip.plugintoolkit.api.sdui.BoardTool
import org.wip.plugintoolkit.api.sdui.NormalizedPoint
import org.wip.plugintoolkit.api.sdui.NormalizedSize
import org.wip.plugintoolkit.api.sdui.SemanticColor
import org.wip.plugintoolkit.api.sdui.TypographyRole
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.api.sdui.VectorPathData
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.sdui_board_clear
import plugintoolkit.composeapp.generated.resources.sdui_board_delete
import plugintoolkit.composeapp.generated.resources.sdui_board_edit_layer
import plugintoolkit.composeapp.generated.resources.sdui_board_edit_text_done
import plugintoolkit.composeapp.generated.resources.sdui_board_edit_text_placeholder
import plugintoolkit.composeapp.generated.resources.sdui_board_eraser
import plugintoolkit.composeapp.generated.resources.sdui_board_highlighter
import plugintoolkit.composeapp.generated.resources.sdui_board_move_layer
import plugintoolkit.composeapp.generated.resources.sdui_board_pan
import plugintoolkit.composeapp.generated.resources.sdui_board_pen
import plugintoolkit.composeapp.generated.resources.sdui_board_redo
import plugintoolkit.composeapp.generated.resources.sdui_board_resize_layer
import plugintoolkit.composeapp.generated.resources.sdui_board_select
import plugintoolkit.composeapp.generated.resources.sdui_board_shape
import plugintoolkit.composeapp.generated.resources.sdui_board_text
import plugintoolkit.composeapp.generated.resources.sdui_board_undo
import plugintoolkit.composeapp.generated.resources.sdui_board_zoom_in
import plugintoolkit.composeapp.generated.resources.sdui_board_zoom_out
import plugintoolkit.composeapp.generated.resources.sdui_board_zoom_reset
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val MIN_NORMALIZED_COORD = 0f
private const val MAX_NORMALIZED_COORD = 1f
private const val MIN_NORMALIZED_SIZE = 0.02f
private const val MAX_NORMALIZED_SIZE = 1f
private const val DEFAULT_COMPONENT_WIDTH = 0.28f
private const val DEFAULT_COMPONENT_HEIGHT = 0.22f
private const val DEFAULT_TEXT_BOX_WIDTH = 0.26f
private const val DEFAULT_TEXT_BOX_HEIGHT = 0.1f
private const val MIN_STROKE_POINTS = 2
private const val DEFAULT_RDP_EPSILON = 0.002f
private const val CATMULL_ROM_TANGENT_DIVISOR = 6f
private const val ZOOM_STEP_FACTOR = 1.15f
private const val WHEEL_ZOOM_SENSITIVITY = 0.1f
private const val PERCENT_MULTIPLIER = 100
private const val GRID_DOT_RADIUS_PX = 1.5f
private const val GRID_CROSSHAIR_HALF_PX = 4f
private const val GRID_LINE_STROKE_PX = 1f
private const val SHAPE_ROUNDED_CORNER_RATIO = 0.15f
private const val CALLOUT_BODY_HEIGHT_RATIO = 0.78f
private const val CALLOUT_TAIL_START_X_RATIO = 0.35f
private const val CALLOUT_TAIL_END_X_RATIO = 0.55f
private const val CALLOUT_TAIL_TIP_X_RATIO = 0.25f
private const val ARROW_HEAD_LENGTH_PX = 16f
private const val ARROW_HEAD_ANGLE_RAD = 0.5f
private const val MIN_RUBBER_BAND_SPAN = 0.02f
private const val DEFAULT_SHAPE_FILL_ALPHA = 0.18f

/**
 * Interactive Flow-Editor-style spatial component board renderer for SDUI [BoardNode].
 * Supports:
 * - Spatial component layers ([BoardLayer.ComponentLayer]), image layers ([BoardLayer.ImageLayer]),
 *   inline-editable text layers ([BoardLayer.TextLayer]), geometric/callout shapes ([BoardLayer.ShapeLayer]),
 *   and smoothed freehand vector strokes ([BoardLayer.PathLayer]).
 * - Pan and zoom camera transformations with configurable grid styles ([BoardGridStyle]).
 * - Interactive selection, dragging with optional grid snapping, corner resizing, and inline text editing.
 */
@Composable
fun BoardNodeRenderer(
    node: BoardNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTool by remember(node.activeTool) { mutableStateOf(node.activeTool) }
    var selectedIds by remember(node.selectedLayerIds) { mutableStateOf(node.selectedLayerIds) }
    var editingTextLayerId by remember { mutableStateOf<String?>(null) }
    var editingTextBuffer by remember { mutableStateOf("") }

    val minScale = node.viewportConfig.minScale.coerceIn(0.25f, 4.0f)
    val maxScale = node.viewportConfig.maxScale.coerceIn(minScale, 4.0f)
    var scale by remember(node.id) {
        mutableStateOf(node.viewportConfig.initialScale.coerceIn(minScale, maxScale))
    }
    var panOffset by remember(node.id) { mutableStateOf(Offset.Zero) }

    var activeDragLayerId by remember { mutableStateOf<String?>(null) }
    var activeDragDelta by remember { mutableStateOf(Offset.Zero) }
    var activeResizeLayerId by remember { mutableStateOf<String?>(null) }
    var activeResizeDelta by remember { mutableStateOf(Offset.Zero) }

    var rubberBandStart by remember { mutableStateOf<NormalizedPoint?>(null) }
    var rubberBandCurrent by remember { mutableStateOf<NormalizedPoint?>(null) }

    val currentPoints = remember { mutableStateListOf<NormalizedPoint>() }
    val pendingCommittedStrokes = remember { mutableStateListOf<VectorPathData>() }

    LaunchedEffect(node.activeTool) {
        selectedTool = node.activeTool
    }

    LaunchedEffect(node.selectedLayerIds) {
        selectedIds = node.selectedLayerIds
    }

    LaunchedEffect(node.clearRevision) {
        if (node.clearRevision > 0) {
            pendingCommittedStrokes.clear()
            editingTextLayerId = null
        }
    }

    LaunchedEffect(node.layers) {
        val serverStrokeIds = node.layers.filterIsInstance<BoardLayer.PathLayer>().map { it.path.strokeId }.toSet()
        pendingCommittedStrokes.removeAll { it.strokeId in serverStrokeIds }
        if (editingTextLayerId != null && node.layers.none { it.id == editingTextLayerId }) {
            editingTextLayerId = null
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val outlineColor = MaterialTheme.colorScheme.outline
    val highlightColor = ToolkitTheme.colors.warning
    val infoColor = ToolkitTheme.colors.info
    val successColor = ToolkitTheme.colors.success
    val warningColor = ToolkitTheme.colors.warning

    val resolveColor: (SemanticColor) -> Color = { color ->
        when (color) {
            SemanticColor.PRIMARY -> primaryColor
            SemanticColor.SECONDARY -> secondaryColor
            SemanticColor.TERTIARY -> tertiaryColor
            SemanticColor.ERROR -> errorColor
            SemanticColor.SURFACE -> surfaceColor
            SemanticColor.ON_SURFACE -> onSurfaceColor
            SemanticColor.OUTLINE -> outlineColor
            SemanticColor.ACCENT -> tertiaryColor
            SemanticColor.HIGHLIGHT -> highlightColor
            SemanticColor.INFO -> infoColor
            SemanticColor.SUCCESS -> successColor
            SemanticColor.WARNING -> warningColor
        }
    }

    val hasFixedAspect = node.modifier.aspectRatio != null && node.modifier.aspectRatio!! > 0f
    val shouldApplyBorder = node.showToolbar || hasFixedAspect
    val selectedChipAlpha = ToolkitTheme.opacity.borderLow
    val highlighterAlpha = ToolkitTheme.opacity.divider

    Column(
        modifier = modifier
            .then(if (hasFixedAspect) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
            .then(
                if (shouldApplyBorder) {
                    Modifier
                        .clip(RoundedCornerShape(ToolkitTheme.dimensions.textFieldCornerRadius))
                        .border(
                            width = ToolkitTheme.dimensions.borderUnselected,
                            color = outlineVariant,
                            shape = RoundedCornerShape(ToolkitTheme.dimensions.textFieldCornerRadius)
                        )
                } else {
                    Modifier.clipToBounds()
                }
            )
            .background(surfaceColor),
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.none)
    ) {
        if (node.showToolbar) {
            BoardToolbar(
                selectedTool = selectedTool,
                hasSelection = selectedIds.isNotEmpty(),
                primaryColor = primaryColor,
                errorColor = errorColor,
                selectedChipAlpha = selectedChipAlpha,
                onSelectTool = { tool, toolKey ->
                    selectedTool = tool
                    onEvent(UiEvent.Custom(node.id, "SELECT_TOOL", JsonPrimitive(toolKey)))
                },
                onDeleteSelected = {
                    if (selectedIds.isNotEmpty()) {
                        onEvent(UiEvent.BoardLayerDeleted(node.id, selectedIds.toList()))
                        selectedIds = emptySet()
                    }
                },
                onUndo = { onEvent(UiEvent.Custom(node.id, "UNDO")) },
                onRedo = { onEvent(UiEvent.Custom(node.id, "REDO")) },
                onClear = {
                    pendingCommittedStrokes.clear()
                    selectedIds = emptySet()
                    editingTextLayerId = null
                    onEvent(UiEvent.Custom(node.id, "CLEAR"))
                }
            )
        }

        val canvasModifier = if (hasFixedAspect) {
            Modifier.fillMaxWidth().aspectRatio(node.modifier.aspectRatio!!)
        } else {
            Modifier.fillMaxWidth().weight(1f)
        }

        BoxWithConstraints(
            modifier = canvasModifier
                .clipToBounds()
                .background(surfaceColor)
                .pointerInput(node.id, node.viewportConfig.zoomEnabled, minScale, maxScale) {
                    if (!node.viewportConfig.zoomEnabled) return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Scroll) {
                                val change = event.changes.firstOrNull() ?: continue
                                val scrollY = change.scrollDelta.y
                                if (scrollY != 0f) {
                                    val (newScale, newPan) = computeZoomAroundPoint(
                                        currentScale = scale,
                                        currentPan = panOffset,
                                        scrollDeltaY = scrollY,
                                        cursorPos = change.position,
                                        minScale = minScale,
                                        maxScale = maxScale
                                    )
                                    scale = newScale
                                    panOffset = newPan
                                    change.consume()
                                }
                            }
                        }
                    }
                }
        ) {
            val boardWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val boardHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val boardWidthDp = maxWidth
            val boardHeightDp = maxHeight
            val sortedLayers = remember(node.layers) {
                node.layers.filter { it.visible }.sortedBy { it.zIndex }
            }

            // Transformed spatial artboard container
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = panOffset.x
                        translationY = panOffset.y
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                // Background canvas: Grid + Background Tap/Pan when in Select mode
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(node.id, selectedTool, node.viewportConfig.panEnabled) {
                            if (selectedTool is BoardTool.Select) {
                                detectTapGestures(
                                    onTap = { offset ->
                                        editingTextLayerId = null
                                        if (selectedIds.isNotEmpty()) {
                                            selectedIds = emptySet()
                                            onEvent(UiEvent.BoardLayerSelected(node.id, emptyList()))
                                        }
                                        val normPt = NormalizedPoint(
                                            x = (offset.x / boardWidthPx).coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD),
                                            y = (offset.y / boardHeightPx).coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
                                        )
                                        onEvent(UiEvent.BoardCanvasClicked(node.id, normPt))
                                    }
                                )
                            }
                        }
                        .pointerInput(node.id, selectedTool, node.viewportConfig.panEnabled) {
                            if (selectedTool is BoardTool.Select && node.viewportConfig.panEnabled) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    panOffset += dragAmount * scale
                                }
                            }
                        }
                ) {
                    clipRect {
                        drawBoardGrid(
                            gridStyle = node.viewportConfig.gridStyle,
                            gridStepNormalized = node.viewportConfig.gridStep,
                            gridColor = outlineVariant.copy(alpha = highlighterAlpha)
                        )
                    }
                }

                // Optional background image asset
                node.backgroundAsset?.let { bgAsset ->
                    PluginAssetImage(
                        asset = bgAsset,
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Render layers in zIndex order
                for (layer in sortedLayers) {
                    key(layer.id) {
                        when (layer) {
                            is BoardLayer.PathLayer -> {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    clipRect {
                                        drawVectorPath(
                                            pathData = layer.path,
                                            width = size.width,
                                            height = size.height,
                                            surfaceColor = surfaceColor,
                                            resolveColor = resolveColor
                                        )
                                    }
                                }
                            }

                            is BoardLayer.ImageLayer -> {
                                val isSelected = layer.id in selectedIds
                                val isDragging = activeDragLayerId == layer.id
                                val isResizing = activeResizeLayerId == layer.id
                                val effectivePos = computeEffectivePosition(
                                    basePos = layer.position,
                                    deltaPx = if (isDragging) activeDragDelta else Offset.Zero,
                                    boardWidthPx = boardWidthPx,
                                    boardHeightPx = boardHeightPx,
                                    snapToGrid = node.viewportConfig.snapToGrid,
                                    gridStep = node.viewportConfig.gridStep
                                )
                                val effectiveSize = computeEffectiveSize(
                                    baseSize = layer.size,
                                    deltaPx = if (isResizing) activeResizeDelta else Offset.Zero,
                                    boardWidthPx = boardWidthPx,
                                    boardHeightPx = boardHeightPx,
                                    snapToGrid = node.viewportConfig.snapToGrid,
                                    gridStep = node.viewportConfig.gridStep
                                )

                                SpatialLayerBox(
                                    position = effectivePos,
                                    size = effectiveSize,
                                    boardWidthDp = boardWidthDp,
                                    boardHeightDp = boardHeightDp,
                                    opacity = layer.opacity,
                                    isSelected = isSelected,
                                    isInteractive = selectedTool is BoardTool.Select && !layer.locked,
                                    canSelect = layer.selectable && !layer.locked,
                                    canDrag = layer.draggable && !layer.locked,
                                    canResize = layer.resizable && !layer.locked,
                                    selectionColor = primaryColor,
                                    onSelect = {
                                        editingTextLayerId = null
                                        val updated = setOf(layer.id)
                                        selectedIds = updated
                                        onEvent(UiEvent.BoardLayerSelected(node.id, updated.toList()))
                                    },
                                    onDragStart = {
                                        activeDragLayerId = layer.id
                                        activeDragDelta = Offset.Zero
                                    },
                                    onDrag = { delta ->
                                        activeDragDelta += delta
                                    },
                                    onDragEnd = {
                                        val finalPos = computeEffectivePosition(
                                            basePos = layer.position,
                                            deltaPx = activeDragDelta,
                                            boardWidthPx = boardWidthPx,
                                            boardHeightPx = boardHeightPx,
                                            snapToGrid = node.viewportConfig.snapToGrid,
                                            gridStep = node.viewportConfig.gridStep
                                        )
                                        activeDragLayerId = null
                                        activeDragDelta = Offset.Zero
                                        onEvent(UiEvent.BoardLayerMoved(node.id, layer.id, finalPos))
                                    },
                                    onResizeStart = {
                                        activeResizeLayerId = layer.id
                                        activeResizeDelta = Offset.Zero
                                    },
                                    onResize = { delta ->
                                        activeResizeDelta += delta
                                    },
                                    onResizeEnd = {
                                        val finalSize = computeEffectiveSize(
                                            baseSize = layer.size,
                                            deltaPx = activeResizeDelta,
                                            boardWidthPx = boardWidthPx,
                                            boardHeightPx = boardHeightPx,
                                            snapToGrid = node.viewportConfig.snapToGrid,
                                            gridStep = node.viewportConfig.gridStep
                                        )
                                        activeResizeLayerId = null
                                        activeResizeDelta = Offset.Zero
                                        onEvent(UiEvent.BoardLayerResized(node.id, layer.id, effectivePos, finalSize))
                                    }
                                ) {
                                    PluginAssetImage(
                                        asset = layer.asset,
                                        contentDescription = layer.contentDescription,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            is BoardLayer.ShapeLayer -> {
                                val isSelected = layer.id in selectedIds
                                val isDragging = activeDragLayerId == layer.id
                                val isResizing = activeResizeLayerId == layer.id
                                val effectivePos = computeEffectivePosition(
                                    basePos = layer.position,
                                    deltaPx = if (isDragging) activeDragDelta else Offset.Zero,
                                    boardWidthPx = boardWidthPx,
                                    boardHeightPx = boardHeightPx,
                                    snapToGrid = node.viewportConfig.snapToGrid,
                                    gridStep = node.viewportConfig.gridStep
                                )
                                val effectiveSize = computeEffectiveSize(
                                    baseSize = layer.size,
                                    deltaPx = if (isResizing) activeResizeDelta else Offset.Zero,
                                    boardWidthPx = boardWidthPx,
                                    boardHeightPx = boardHeightPx,
                                    snapToGrid = node.viewportConfig.snapToGrid,
                                    gridStep = node.viewportConfig.gridStep
                                )

                                SpatialLayerBox(
                                    position = effectivePos,
                                    size = effectiveSize,
                                    boardWidthDp = boardWidthDp,
                                    boardHeightDp = boardHeightDp,
                                    opacity = 1f,
                                    isSelected = isSelected,
                                    isInteractive = selectedTool is BoardTool.Select && !layer.locked,
                                    canSelect = layer.selectable && !layer.locked,
                                    canDrag = layer.draggable && !layer.locked,
                                    canResize = layer.resizable && !layer.locked,
                                    selectionColor = primaryColor,
                                    onSelect = {
                                        editingTextLayerId = null
                                        val updated = setOf(layer.id)
                                        selectedIds = updated
                                        onEvent(UiEvent.BoardLayerSelected(node.id, updated.toList()))
                                    },
                                    onDragStart = {
                                        activeDragLayerId = layer.id
                                        activeDragDelta = Offset.Zero
                                    },
                                    onDrag = { delta ->
                                        activeDragDelta += delta
                                    },
                                    onDragEnd = {
                                        val finalPos = computeEffectivePosition(
                                            basePos = layer.position,
                                            deltaPx = activeDragDelta,
                                            boardWidthPx = boardWidthPx,
                                            boardHeightPx = boardHeightPx,
                                            snapToGrid = node.viewportConfig.snapToGrid,
                                            gridStep = node.viewportConfig.gridStep
                                        )
                                        activeDragLayerId = null
                                        activeDragDelta = Offset.Zero
                                        onEvent(UiEvent.BoardLayerMoved(node.id, layer.id, finalPos))
                                    },
                                    onResizeStart = {
                                        activeResizeLayerId = layer.id
                                        activeResizeDelta = Offset.Zero
                                    },
                                    onResize = { delta ->
                                        activeResizeDelta += delta
                                    },
                                    onResizeEnd = {
                                        val finalSize = computeEffectiveSize(
                                            baseSize = layer.size,
                                            deltaPx = activeResizeDelta,
                                            boardWidthPx = boardWidthPx,
                                            boardHeightPx = boardHeightPx,
                                            snapToGrid = node.viewportConfig.snapToGrid,
                                            gridStep = node.viewportConfig.gridStep
                                        )
                                        activeResizeLayerId = null
                                        activeResizeDelta = Offset.Zero
                                        onEvent(UiEvent.BoardLayerResized(node.id, layer.id, effectivePos, finalSize))
                                    }
                                ) {
                                    val strokeCol = resolveColor(layer.strokeColor)
                                    val fillCol = layer.fillColor?.let {
                                        if (it == SemanticColor.SURFACE) surfaceColor.copy(alpha = ToolkitTheme.opacity.almostOpaque)
                                        else resolveColor(it).copy(alpha = DEFAULT_SHAPE_FILL_ALPHA)
                                    }
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawBoardShape(
                                                shapeType = layer.shapeType,
                                                strokeColor = strokeCol,
                                                fillColor = fillCol,
                                                strokeWidth = layer.strokeWidth
                                            )
                                        }
                                        if (!layer.text.isNullOrBlank()) {
                                            Text(
                                                text = layer.text!!,
                                                color = strokeCol,
                                                style = ToolkitTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(ToolkitTheme.spacing.xs)
                                            )
                                        }
                                    }
                                }
                            }

                            is BoardLayer.TextLayer -> {
                                val isSelected = layer.id in selectedIds
                                val isEditing = editingTextLayerId == layer.id
                                val isDragging = activeDragLayerId == layer.id
                                val isResizing = activeResizeLayerId == layer.id
                                val effectivePos = computeEffectivePosition(
                                    basePos = layer.position,
                                    deltaPx = if (isDragging) activeDragDelta else Offset.Zero,
                                    boardWidthPx = boardWidthPx,
                                    boardHeightPx = boardHeightPx,
                                    snapToGrid = node.viewportConfig.snapToGrid,
                                    gridStep = node.viewportConfig.gridStep
                                )
                                val defaultTextSize = layer.size ?: NormalizedSize(DEFAULT_TEXT_BOX_WIDTH, DEFAULT_TEXT_BOX_HEIGHT)
                                val effectiveSize = computeEffectiveSize(
                                    baseSize = defaultTextSize,
                                    deltaPx = if (isResizing) activeResizeDelta else Offset.Zero,
                                    boardWidthPx = boardWidthPx,
                                    boardHeightPx = boardHeightPx,
                                    snapToGrid = node.viewportConfig.snapToGrid,
                                    gridStep = node.viewportConfig.gridStep
                                )

                                SpatialTextLayerBox(
                                    layer = layer,
                                    position = effectivePos,
                                    size = if (layer.size != null || isResizing) effectiveSize else null,
                                    boardWidthDp = boardWidthDp,
                                    boardHeightDp = boardHeightDp,
                                    isSelected = isSelected,
                                    isEditing = isEditing,
                                    editingTextBuffer = editingTextBuffer,
                                    isInteractive = (selectedTool is BoardTool.Select || selectedTool is BoardTool.PlaceText) && !layer.locked,
                                    resolveColor = resolveColor,
                                    primaryColor = primaryColor,
                                    surfaceColor = surfaceColor,
                                    onSelect = {
                                        val updated = setOf(layer.id)
                                        selectedIds = updated
                                        onEvent(UiEvent.BoardLayerSelected(node.id, updated.toList()))
                                    },
                                    onStartEdit = {
                                        if (layer.editable && !layer.locked) {
                                            editingTextLayerId = layer.id
                                            editingTextBuffer = layer.text
                                            val updated = setOf(layer.id)
                                            selectedIds = updated
                                            onEvent(UiEvent.BoardLayerSelected(node.id, updated.toList()))
                                        }
                                    },
                                    onEditBufferChange = { editingTextBuffer = it },
                                    onCommitEdit = {
                                        val committed = editingTextBuffer
                                        editingTextLayerId = null
                                        onEvent(UiEvent.BoardTextEdited(node.id, layer.id, committed))
                                    },
                                    onDragStart = {
                                        activeDragLayerId = layer.id
                                        activeDragDelta = Offset.Zero
                                    },
                                    onDrag = { delta ->
                                        activeDragDelta += delta
                                    },
                                    onDragEnd = {
                                        val finalPos = computeEffectivePosition(
                                            basePos = layer.position,
                                            deltaPx = activeDragDelta,
                                            boardWidthPx = boardWidthPx,
                                            boardHeightPx = boardHeightPx,
                                            snapToGrid = node.viewportConfig.snapToGrid,
                                            gridStep = node.viewportConfig.gridStep
                                        )
                                        activeDragLayerId = null
                                        activeDragDelta = Offset.Zero
                                        onEvent(UiEvent.BoardLayerMoved(node.id, layer.id, finalPos))
                                    },
                                    onResizeStart = {
                                        activeResizeLayerId = layer.id
                                        activeResizeDelta = Offset.Zero
                                    },
                                    onResize = { delta ->
                                        activeResizeDelta += delta
                                    },
                                    onResizeEnd = {
                                        val finalSize = computeEffectiveSize(
                                            baseSize = defaultTextSize,
                                            deltaPx = activeResizeDelta,
                                            boardWidthPx = boardWidthPx,
                                            boardHeightPx = boardHeightPx,
                                            snapToGrid = node.viewportConfig.snapToGrid,
                                            gridStep = node.viewportConfig.gridStep
                                        )
                                        activeResizeLayerId = null
                                        activeResizeDelta = Offset.Zero
                                        onEvent(UiEvent.BoardLayerResized(node.id, layer.id, effectivePos, finalSize))
                                    }
                                )
                            }

                            is BoardLayer.ComponentLayer -> {
                                val isSelected = layer.id in selectedIds
                                val isDragging = activeDragLayerId == layer.id
                                val isResizing = activeResizeLayerId == layer.id
                                val baseCompSize = layer.size ?: NormalizedSize(DEFAULT_COMPONENT_WIDTH, DEFAULT_COMPONENT_HEIGHT)
                                val effectivePos = computeEffectivePosition(
                                    basePos = layer.position,
                                    deltaPx = if (isDragging) activeDragDelta else Offset.Zero,
                                    boardWidthPx = boardWidthPx,
                                    boardHeightPx = boardHeightPx,
                                    snapToGrid = node.viewportConfig.snapToGrid,
                                    gridStep = node.viewportConfig.gridStep
                                )
                                val effectiveSize = computeEffectiveSize(
                                    baseSize = baseCompSize,
                                    deltaPx = if (isResizing) activeResizeDelta else Offset.Zero,
                                    boardWidthPx = boardWidthPx,
                                    boardHeightPx = boardHeightPx,
                                    snapToGrid = node.viewportConfig.snapToGrid,
                                    gridStep = node.viewportConfig.gridStep
                                )

                                SpatialComponentLayerCard(
                                    layer = layer,
                                    position = effectivePos,
                                    size = effectiveSize,
                                    boardWidthDp = boardWidthDp,
                                    boardHeightDp = boardHeightDp,
                                    isSelected = isSelected,
                                    canInteractWithBoard = selectedTool is BoardTool.Select && !layer.locked,
                                    primaryColor = primaryColor,
                                    outlineVariant = outlineVariant,
                                    onSelect = {
                                        editingTextLayerId = null
                                        val updated = setOf(layer.id)
                                        selectedIds = updated
                                        onEvent(UiEvent.BoardLayerSelected(node.id, updated.toList()))
                                    },
                                    onDragStart = {
                                        activeDragLayerId = layer.id
                                        activeDragDelta = Offset.Zero
                                    },
                                    onDrag = { delta ->
                                        activeDragDelta += delta
                                    },
                                    onDragEnd = {
                                        val finalPos = computeEffectivePosition(
                                            basePos = layer.position,
                                            deltaPx = activeDragDelta,
                                            boardWidthPx = boardWidthPx,
                                            boardHeightPx = boardHeightPx,
                                            snapToGrid = node.viewportConfig.snapToGrid,
                                            gridStep = node.viewportConfig.gridStep
                                        )
                                        activeDragLayerId = null
                                        activeDragDelta = Offset.Zero
                                        onEvent(UiEvent.BoardLayerMoved(node.id, layer.id, finalPos))
                                    },
                                    onResizeStart = {
                                        activeResizeLayerId = layer.id
                                        activeResizeDelta = Offset.Zero
                                    },
                                    onResize = { delta ->
                                        activeResizeDelta += delta
                                    },
                                    onResizeEnd = {
                                        val finalSize = computeEffectiveSize(
                                            baseSize = baseCompSize,
                                            deltaPx = activeResizeDelta,
                                            boardWidthPx = boardWidthPx,
                                            boardHeightPx = boardHeightPx,
                                            snapToGrid = node.viewportConfig.snapToGrid,
                                            gridStep = node.viewportConfig.gridStep
                                        )
                                        activeResizeLayerId = null
                                        activeResizeDelta = Offset.Zero
                                        onEvent(UiEvent.BoardLayerResized(node.id, layer.id, effectivePos, finalSize))
                                    },
                                    onEvent = onEvent
                                )
                            }
                        }
                    }
                }

                // Foreground overlay for active tool gestures (Pan, PlaceText, Shape/Rectangle, Brush/Highlighter/Eraser)
                // and optimistic/in-progress vector strokes & rubber-band previews
                val requiresTopToolOverlay = selectedTool !is BoardTool.Select
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .then(
                            if (requiresTopToolOverlay) {
                                Modifier
                                    .pointerInput(node.id, selectedTool) {
                                        if (selectedTool is BoardTool.PlaceText) {
                                            detectTapGestures { offset ->
                                                val tool = selectedTool as BoardTool.PlaceText
                                                val rawPt = NormalizedPoint(
                                                    x = (offset.x / boardWidthPx).coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD),
                                                    y = (offset.y / boardHeightPx).coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
                                                )
                                                val snappedPt = snapPointToGrid(
                                                    point = rawPt,
                                                    snapToGrid = node.viewportConfig.snapToGrid,
                                                    gridStep = node.viewportConfig.gridStep
                                                )
                                                onEvent(UiEvent.BoardTextPlaced(node.id, snappedPt, tool.defaultText))
                                            }
                                        }
                                    }
                                    .pointerInput(node.id, selectedTool, node.viewportConfig.panEnabled) {
                                        if (selectedTool is BoardTool.PlaceText) return@pointerInput
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                val nx = (offset.x / boardWidthPx).coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
                                                val ny = (offset.y / boardHeightPx).coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
                                                val normPt = NormalizedPoint(nx, ny)
                                                when (selectedTool) {
                                                    is BoardTool.Pan -> Unit
                                                    is BoardTool.Shape, is BoardTool.Rectangle -> {
                                                        val snapped = snapPointToGrid(
                                                            point = normPt,
                                                            snapToGrid = node.viewportConfig.snapToGrid,
                                                            gridStep = node.viewportConfig.gridStep
                                                        )
                                                        rubberBandStart = snapped
                                                        rubberBandCurrent = snapped
                                                    }
                                                    is BoardTool.Brush, is BoardTool.Highlighter, is BoardTool.Eraser -> {
                                                        currentPoints.clear()
                                                        currentPoints.add(normPt)
                                                    }
                                                    else -> Unit
                                                }
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                val nx = (change.position.x / boardWidthPx).coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
                                                val ny = (change.position.y / boardHeightPx).coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
                                                val normPt = NormalizedPoint(nx, ny)
                                                when (selectedTool) {
                                                    is BoardTool.Pan -> {
                                                        if (node.viewportConfig.panEnabled) {
                                                            panOffset += dragAmount * scale
                                                        }
                                                    }
                                                    is BoardTool.Shape, is BoardTool.Rectangle -> {
                                                        rubberBandCurrent = snapPointToGrid(
                                                            point = normPt,
                                                            snapToGrid = node.viewportConfig.snapToGrid,
                                                            gridStep = node.viewportConfig.gridStep
                                                        )
                                                    }
                                                    is BoardTool.Brush, is BoardTool.Highlighter, is BoardTool.Eraser -> {
                                                        currentPoints.add(normPt)
                                                    }
                                                    else -> Unit
                                                }
                                            },
                                            onDragEnd = {
                                                when (val tool = selectedTool) {
                                                    is BoardTool.Shape -> {
                                                        val start = rubberBandStart
                                                        val end = rubberBandCurrent
                                                        if (start != null && end != null) {
                                                            val minX = min(start.x, end.x)
                                                            val minY = min(start.y, end.y)
                                                            val w = max(abs(end.x - start.x), MIN_RUBBER_BAND_SPAN)
                                                            val h = max(abs(end.y - start.y), MIN_RUBBER_BAND_SPAN)
                                                            onEvent(
                                                                UiEvent.BoardShapeCommitted(
                                                                    nodeId = node.id,
                                                                    shapeType = tool.shapeType,
                                                                    position = NormalizedPoint(minX, minY),
                                                                    size = NormalizedSize(w, h),
                                                                    strokeColor = tool.strokeColor,
                                                                    fillColor = tool.fillColor,
                                                                    strokeWidth = tool.strokeWidth
                                                                )
                                                            )
                                                        }
                                                        rubberBandStart = null
                                                        rubberBandCurrent = null
                                                    }
                                                    is BoardTool.Rectangle -> {
                                                        val start = rubberBandStart
                                                        val end = rubberBandCurrent
                                                        if (start != null && end != null) {
                                                            val minX = min(start.x, end.x)
                                                            val minY = min(start.y, end.y)
                                                            val w = max(abs(end.x - start.x), MIN_RUBBER_BAND_SPAN)
                                                            val h = max(abs(end.y - start.y), MIN_RUBBER_BAND_SPAN)
                                                            onEvent(
                                                                UiEvent.BoardShapeCommitted(
                                                                    nodeId = node.id,
                                                                    shapeType = BoardShapeType.RECTANGLE,
                                                                    position = NormalizedPoint(minX, minY),
                                                                    size = NormalizedSize(w, h),
                                                                    strokeColor = tool.color,
                                                                    fillColor = null,
                                                                    strokeWidth = tool.strokeWidth
                                                                )
                                                            )
                                                        }
                                                        rubberBandStart = null
                                                        rubberBandCurrent = null
                                                    }
                                                    is BoardTool.Brush, is BoardTool.Highlighter, is BoardTool.Eraser -> {
                                                        if (currentPoints.size >= MIN_STROKE_POINTS) {
                                                            val simplified = rdpSimplify(currentPoints.toList(), epsilon = DEFAULT_RDP_EPSILON)
                                                            val strokeId = "stroke_${System.currentTimeMillis()}"
                                                            val (color, strokeWidth) = when (tool) {
                                                                is BoardTool.Brush -> tool.color to tool.strokeWidth
                                                                is BoardTool.Highlighter -> tool.color to tool.strokeWidth
                                                                is BoardTool.Eraser -> SemanticColor.SURFACE to tool.radius
                                                                else -> SemanticColor.PRIMARY to 4f
                                                            }
                                                            val newStroke = VectorPathData(
                                                                strokeId = strokeId,
                                                                points = simplified,
                                                                color = color,
                                                                strokeWidth = strokeWidth
                                                            )
                                                            pendingCommittedStrokes.add(newStroke)
                                                            onEvent(UiEvent.PathCommitted(node.id, newStroke))
                                                        }
                                                        currentPoints.clear()
                                                    }
                                                    else -> Unit
                                                }
                                            },
                                            onDragCancel = {
                                                currentPoints.clear()
                                                rubberBandStart = null
                                                rubberBandCurrent = null
                                            }
                                        )
                                    }
                            } else {
                                Modifier
                            }
                        )
                ) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    clipRect {
                        // Draw optimistic pending strokes
                        pendingCommittedStrokes.forEach { stroke ->
                            drawVectorPath(stroke, canvasWidth, canvasHeight, surfaceColor, resolveColor)
                        }

                        // Draw active freehand stroke preview
                        if (currentPoints.size >= MIN_STROKE_POINTS) {
                            val activeColor = when (val t = selectedTool) {
                                is BoardTool.Eraser -> surfaceColor
                                is BoardTool.Brush -> resolveColor(t.color)
                                is BoardTool.Highlighter -> resolveColor(t.color).copy(alpha = highlighterAlpha)
                                else -> primaryColor
                            }
                            val activeWidth = when (val t = selectedTool) {
                                is BoardTool.Brush -> t.strokeWidth
                                is BoardTool.Highlighter -> t.strokeWidth
                                is BoardTool.Eraser -> t.radius
                                else -> 4f
                            }
                            val activePath = createCatmullRomPath(currentPoints, canvasWidth, canvasHeight)
                            drawPath(
                                path = activePath,
                                color = activeColor,
                                style = Stroke(
                                    width = activeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }

                        // Draw rubber-band shape preview
                        val rbStart = rubberBandStart
                        val rbEnd = rubberBandCurrent
                        if (rbStart != null && rbEnd != null) {
                            val left = min(rbStart.x, rbEnd.x) * canvasWidth
                            val top = min(rbStart.y, rbEnd.y) * canvasHeight
                            val w = max(abs(rbEnd.x - rbStart.x) * canvasWidth, 4f)
                            val h = max(abs(rbEnd.y - rbStart.y) * canvasHeight, 4f)
                            val previewColor = when (val t = selectedTool) {
                                is BoardTool.Shape -> resolveColor(t.strokeColor)
                                is BoardTool.Rectangle -> resolveColor(t.color)
                                else -> primaryColor
                            }
                            drawRect(
                                color = previewColor.copy(alpha = DEFAULT_SHAPE_FILL_ALPHA),
                                topLeft = Offset(left, top),
                                size = Size(w, h),
                                style = Fill
                            )
                            drawRect(
                                color = previewColor,
                                topLeft = Offset(left, top),
                                size = Size(w, h),
                                style = Stroke(width = 2f)
                            )
                        }
                    }
                }
            }

            // Floating Zoom Controls Overlay (Bottom-End, unaffected by camera transform)
            if (node.showZoomControls && (node.viewportConfig.zoomEnabled || node.viewportConfig.panEnabled)) {
                BoardZoomControlsPill(
                    scale = scale,
                    onZoomOut = {
                        scale = (scale / ZOOM_STEP_FACTOR).coerceIn(minScale, maxScale)
                    },
                    onZoomIn = {
                        scale = (scale * ZOOM_STEP_FACTOR).coerceIn(minScale, maxScale)
                    },
                    onReset = {
                        scale = node.viewportConfig.initialScale.coerceIn(minScale, maxScale)
                        panOffset = Offset.Zero
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(ToolkitTheme.spacing.sm)
                )
            }
        }
    }
}

@Composable
private fun BoardToolbar(
    selectedTool: BoardTool,
    hasSelection: Boolean,
    primaryColor: Color,
    errorColor: Color,
    selectedChipAlpha: Float,
    onSelectTool: (BoardTool, String) -> Unit,
    onDeleteSelected: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = ToolkitTheme.dimensions.cardElevation
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ToolkitTheme.spacing.sm, vertical = ToolkitTheme.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = selectedTool is BoardTool.Select,
                    onClick = { onSelectTool(BoardTool.Select(), "select") },
                    label = { Text(stringResource(Res.string.sdui_board_select), style = ToolkitTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.NearMe,
                            contentDescription = stringResource(Res.string.sdui_board_select),
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = primaryColor.copy(alpha = selectedChipAlpha),
                        selectedLabelColor = primaryColor
                    )
                )

                FilterChip(
                    selected = selectedTool is BoardTool.Pan,
                    onClick = { onSelectTool(BoardTool.Pan, "pan") },
                    label = { Text(stringResource(Res.string.sdui_board_pan), style = ToolkitTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.OpenWith,
                            contentDescription = stringResource(Res.string.sdui_board_pan),
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                        )
                    }
                )

                FilterChip(
                    selected = selectedTool is BoardTool.PlaceText,
                    onClick = { onSelectTool(BoardTool.PlaceText(), "text") },
                    label = { Text(stringResource(Res.string.sdui_board_text), style = ToolkitTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Title,
                            contentDescription = stringResource(Res.string.sdui_board_text),
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                        )
                    }
                )

                FilterChip(
                    selected = selectedTool is BoardTool.Shape || selectedTool is BoardTool.Rectangle,
                    onClick = { onSelectTool(BoardTool.Shape(), "shape") },
                    label = { Text(stringResource(Res.string.sdui_board_shape), style = ToolkitTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.CropSquare,
                            contentDescription = stringResource(Res.string.sdui_board_shape),
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                        )
                    }
                )

                FilterChip(
                    selected = selectedTool is BoardTool.Brush,
                    onClick = { onSelectTool(BoardTool.Brush(), "brush") },
                    label = { Text(stringResource(Res.string.sdui_board_pen), style = ToolkitTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(Res.string.sdui_board_pen),
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = primaryColor.copy(alpha = selectedChipAlpha),
                        selectedLabelColor = primaryColor
                    )
                )

                FilterChip(
                    selected = selectedTool is BoardTool.Highlighter,
                    onClick = { onSelectTool(BoardTool.Highlighter(), "highlighter") },
                    label = { Text(stringResource(Res.string.sdui_board_highlighter), style = ToolkitTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.FormatPaint,
                            contentDescription = stringResource(Res.string.sdui_board_highlighter),
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                        )
                    }
                )

                FilterChip(
                    selected = selectedTool is BoardTool.Eraser,
                    onClick = { onSelectTool(BoardTool.Eraser(), "eraser") },
                    label = { Text(stringResource(Res.string.sdui_board_eraser), style = ToolkitTheme.typography.labelSmall) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoFixHigh,
                            contentDescription = stringResource(Res.string.sdui_board_eraser),
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = errorColor.copy(alpha = selectedChipAlpha),
                        selectedLabelColor = errorColor
                    )
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasSelection) {
                    IconButton(
                        onClick = onDeleteSelected,
                        modifier = Modifier.size(ToolkitTheme.dimensions.standardButtonHeight)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(Res.string.sdui_board_delete),
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall),
                            tint = errorColor
                        )
                    }
                }

                IconButton(
                    onClick = onUndo,
                    modifier = Modifier.size(ToolkitTheme.dimensions.standardButtonHeight)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(Res.string.sdui_board_undo),
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                    )
                }

                IconButton(
                    onClick = onRedo,
                    modifier = Modifier.size(ToolkitTheme.dimensions.standardButtonHeight)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = stringResource(Res.string.sdui_board_redo),
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                    )
                }

                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(ToolkitTheme.dimensions.standardButtonHeight)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(Res.string.sdui_board_clear),
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall),
                        tint = errorColor
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardZoomControlsPill(
    scale: Float,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = ToolkitTheme.shapes.pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = ToolkitTheme.opacity.almostOpaque),
        tonalElevation = ToolkitTheme.dimensions.menuElevation
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.xs, vertical = ToolkitTheme.spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xxs)
        ) {
            IconButton(
                onClick = onZoomOut,
                modifier = Modifier.size(ToolkitTheme.dimensions.textFieldHeightCompact)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = stringResource(Res.string.sdui_board_zoom_out),
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                )
            }
            Text(
                text = "${(scale * PERCENT_MULTIPLIER).roundToInt()}%",
                style = ToolkitTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(
                onClick = onZoomIn,
                modifier = Modifier.size(ToolkitTheme.dimensions.textFieldHeightCompact)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.sdui_board_zoom_in),
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                )
            }
            IconButton(
                onClick = onReset,
                modifier = Modifier.size(ToolkitTheme.dimensions.textFieldHeightCompact)
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = stringResource(Res.string.sdui_board_zoom_reset),
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                )
            }
        }
    }
}

@Composable
private fun SpatialLayerBox(
    position: NormalizedPoint,
    size: NormalizedSize,
    boardWidthDp: androidx.compose.ui.unit.Dp,
    boardHeightDp: androidx.compose.ui.unit.Dp,
    opacity: Float,
    isSelected: Boolean,
    isInteractive: Boolean,
    canSelect: Boolean,
    canDrag: Boolean,
    canResize: Boolean,
    selectionColor: Color,
    onSelect: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onResizeStart: () -> Unit,
    onResize: (Offset) -> Unit,
    onResizeEnd: () -> Unit,
    content: @Composable () -> Unit
) {
    val xDp = boardWidthDp * position.x
    val yDp = boardHeightDp * position.y
    val wDp = boardWidthDp * size.width
    val hDp = boardHeightDp * size.height

    Box(
        modifier = Modifier
            .offset(x = xDp, y = yDp)
            .width(wDp)
            .height(hDp)
            .alpha(opacity.coerceIn(0f, 1f))
            .then(
                if (isSelected) {
                    Modifier.border(
                        width = ToolkitTheme.dimensions.progressIndicatorStroke,
                        color = selectionColor,
                        shape = ToolkitTheme.shapes.extraSmall
                    )
                } else {
                    Modifier
                }
            )
            .then(
                if (isInteractive && canSelect) {
                    Modifier.pointerInput(canSelect) {
                        detectTapGestures(onTap = { onSelect() })
                    }
                } else {
                    Modifier
                }
            )
            .then(
                if (isInteractive && canDrag) {
                    Modifier.pointerInput(canDrag) {
                        detectDragGestures(
                            onDragStart = {
                                onSelect()
                                onDragStart()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        content()

        if (isSelected && isInteractive && canResize) {
            ResizeHandleBadge(
                selectionColor = selectionColor,
                onResizeStart = onResizeStart,
                onResize = onResize,
                onResizeEnd = onResizeEnd,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun SpatialTextLayerBox(
    layer: BoardLayer.TextLayer,
    position: NormalizedPoint,
    size: NormalizedSize?,
    boardWidthDp: androidx.compose.ui.unit.Dp,
    boardHeightDp: androidx.compose.ui.unit.Dp,
    isSelected: Boolean,
    isEditing: Boolean,
    editingTextBuffer: String,
    isInteractive: Boolean,
    resolveColor: (SemanticColor) -> Color,
    primaryColor: Color,
    surfaceColor: Color,
    onSelect: () -> Unit,
    onStartEdit: () -> Unit,
    onEditBufferChange: (String) -> Unit,
    onCommitEdit: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onResizeStart: () -> Unit,
    onResize: (Offset) -> Unit,
    onResizeEnd: () -> Unit
) {
    val xDp = boardWidthDp * position.x
    val yDp = boardHeightDp * position.y
    val textColor = resolveColor(layer.color)
    val bgColor = layer.backgroundColor?.let {
        if (it == SemanticColor.SURFACE) surfaceColor.copy(alpha = ToolkitTheme.opacity.almostOpaque)
        else resolveColor(it).copy(alpha = DEFAULT_SHAPE_FILL_ALPHA)
    } ?: ToolkitTheme.colors.transparent
    val borderColor = when {
        isSelected -> primaryColor
        layer.borderColor != null -> resolveColor(layer.borderColor!!)
        else -> ToolkitTheme.colors.transparent
    }
    val textStyle = when (layer.role) {
        TypographyRole.DISPLAY -> ToolkitTheme.typography.headlineLarge
        TypographyRole.HEADLINE -> ToolkitTheme.typography.headlineSmall
        TypographyRole.TITLE_LARGE -> ToolkitTheme.typography.titleLarge
        TypographyRole.TITLE_MEDIUM -> ToolkitTheme.typography.titleMedium
        TypographyRole.BODY_LARGE -> ToolkitTheme.typography.bodyLarge
        TypographyRole.BODY_MEDIUM -> ToolkitTheme.typography.bodyMedium
        TypographyRole.BODY_SMALL -> ToolkitTheme.typography.bodySmall
        TypographyRole.LABEL -> ToolkitTheme.typography.labelMedium
    }

    Box(
        modifier = Modifier
            .offset(x = xDp, y = yDp)
            .then(
                if (size != null) {
                    Modifier.width(boardWidthDp * size.width).height(boardHeightDp * size.height)
                } else {
                    Modifier.widthIn(min = ToolkitTheme.dimensions.genericInputWidth, max = ToolkitTheme.dimensions.minWidthMedium)
                }
            )
            .background(bgColor, shape = ToolkitTheme.shapes.small)
            .border(
                width = if (isSelected) ToolkitTheme.dimensions.progressIndicatorStroke else ToolkitTheme.dimensions.borderUnselected,
                color = borderColor,
                shape = ToolkitTheme.shapes.small
            )
            .then(
                if (isInteractive && !isEditing && layer.selectable) {
                    Modifier.pointerInput(layer.id, isSelected) {
                        detectTapGestures(
                            onTap = {
                                if (isSelected && layer.editable) {
                                    onStartEdit()
                                } else {
                                    onSelect()
                                }
                            },
                            onDoubleTap = {
                                if (layer.editable) {
                                    onStartEdit()
                                }
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
            .then(
                if (isInteractive && !isEditing && layer.draggable) {
                    Modifier.pointerInput(layer.id) {
                        detectDragGestures(
                            onDragStart = {
                                onSelect()
                                onDragStart()
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount)
                            },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() }
                        )
                    }
                } else {
                    Modifier
                }
            )
            .padding(ToolkitTheme.spacing.xs)
    ) {
        if (isEditing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xxs),
                modifier = Modifier.background(
                    MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = ToolkitTheme.shapes.small
                ).padding(ToolkitTheme.spacing.xxs)
            ) {
                OutlinedTextField(
                    value = editingTextBuffer,
                    onValueChange = onEditBufferChange,
                    placeholder = {
                        Text(
                            text = stringResource(Res.string.sdui_board_edit_text_placeholder),
                            style = ToolkitTheme.typography.bodySmall
                        )
                    },
                    textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                    singleLine = false,
                    modifier = Modifier.widthIn(min = ToolkitTheme.dimensions.genericInputWidthLarge)
                )
                IconButton(
                    onClick = onCommitEdit,
                    modifier = Modifier.size(ToolkitTheme.dimensions.menuItem)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(Res.string.sdui_board_edit_text_done),
                        tint = primaryColor,
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xxs)
            ) {
                Text(
                    text = layer.text,
                    color = textColor,
                    style = textStyle,
                    modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.xxs)
                )
                if (isSelected && layer.editable && isInteractive) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(Res.string.sdui_board_edit_layer),
                        tint = primaryColor,
                        modifier = Modifier
                            .size(ToolkitTheme.dimensions.iconExtraSmall)
                            .clickable { onStartEdit() }
                    )
                }
            }
        }

        if (isSelected && !isEditing && isInteractive && layer.selectable) {
            ResizeHandleBadge(
                selectionColor = primaryColor,
                onResizeStart = onResizeStart,
                onResize = onResize,
                onResizeEnd = onResizeEnd,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun SpatialComponentLayerCard(
    layer: BoardLayer.ComponentLayer,
    position: NormalizedPoint,
    size: NormalizedSize,
    boardWidthDp: androidx.compose.ui.unit.Dp,
    boardHeightDp: androidx.compose.ui.unit.Dp,
    isSelected: Boolean,
    canInteractWithBoard: Boolean,
    primaryColor: Color,
    outlineVariant: Color,
    onSelect: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onResizeStart: () -> Unit,
    onResize: (Offset) -> Unit,
    onResizeEnd: () -> Unit,
    onEvent: (UiEvent) -> Unit
) {
    val xDp = boardWidthDp * position.x
    val yDp = boardHeightDp * position.y
    val wDp = boardWidthDp * size.width
    val hDp = boardHeightDp * size.height

    Surface(
        modifier = Modifier
            .offset(x = xDp, y = yDp)
            .width(wDp)
            .height(hDp)
            .clip(ToolkitTheme.shapes.medium)
            .border(
                width = if (isSelected) ToolkitTheme.dimensions.progressIndicatorStroke else ToolkitTheme.dimensions.borderUnselected,
                color = if (isSelected) primaryColor else outlineVariant,
                shape = ToolkitTheme.shapes.medium
            ),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = ToolkitTheme.dimensions.cardElevation,
        shape = ToolkitTheme.shapes.medium
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Flow-Editor-style node drag bar when draggable or selectable
                if (layer.draggable || layer.selectable) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) primaryColor.copy(alpha = ToolkitTheme.opacity.borderLow)
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                            .then(
                                if (canInteractWithBoard && layer.selectable) {
                                    Modifier.pointerInput(layer.id) {
                                        detectTapGestures(onTap = { onSelect() })
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .then(
                                if (canInteractWithBoard && layer.draggable) {
                                    Modifier.pointerInput(layer.id) {
                                        detectDragGestures(
                                            onDragStart = {
                                                onSelect()
                                                onDragStart()
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                onDrag(dragAmount)
                                            },
                                            onDragEnd = { onDragEnd() },
                                            onDragCancel = { onDragEnd() }
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = ToolkitTheme.spacing.xs, vertical = ToolkitTheme.spacing.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragIndicator,
                            contentDescription = stringResource(Res.string.sdui_board_move_layer),
                            tint = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconExtraSmall)
                        )
                        Text(
                            text = layer.id,
                            style = ToolkitTheme.typography.labelSmall,
                            color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds()
                        .padding(ToolkitTheme.spacing.xs)
                ) {
                    SafeUiNodeRenderer(
                        node = layer.content,
                        onEvent = onEvent,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            if (isSelected && canInteractWithBoard && (layer.resizable || layer.selectable)) {
                ResizeHandleBadge(
                    selectionColor = primaryColor,
                    onResizeStart = onResizeStart,
                    onResize = onResize,
                    onResizeEnd = onResizeEnd,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }
    }
}

@Composable
private fun ResizeHandleBadge(
    selectionColor: Color,
    onResizeStart: () -> Unit,
    onResize: (Offset) -> Unit,
    onResizeEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(ToolkitTheme.dimensions.iconSmall)
            .background(selectionColor, shape = ToolkitTheme.shapes.extraSmall)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onResizeStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onResize(dragAmount)
                    },
                    onDragEnd = { onResizeEnd() },
                    onDragCancel = { onResizeEnd() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.OpenInFull,
            contentDescription = stringResource(Res.string.sdui_board_resize_layer),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(ToolkitTheme.dimensions.iconMicro)
        )
    }
}

private fun DrawScope.drawBoardGrid(
    gridStyle: BoardGridStyle,
    gridStepNormalized: Float,
    gridColor: Color
) {
    if (gridStyle == BoardGridStyle.NONE) return
    val step = gridStepNormalized.coerceIn(0.01f, 0.25f)
    val stepX = (size.width * step).coerceAtLeast(8f)
    val stepY = (size.height * step).coerceAtLeast(8f)

    when (gridStyle) {
        BoardGridStyle.NONE -> Unit
        BoardGridStyle.DOTS -> {
            var x = stepX
            while (x < size.width) {
                var y = stepY
                while (y < size.height) {
                    drawCircle(
                        color = gridColor,
                        radius = GRID_DOT_RADIUS_PX,
                        center = Offset(x, y)
                    )
                    y += stepY
                }
                x += stepX
            }
        }
        BoardGridStyle.LINES -> {
            var x = stepX
            while (x < size.width) {
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = GRID_LINE_STROKE_PX
                )
                x += stepX
            }
            var y = stepY
            while (y < size.height) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = GRID_LINE_STROKE_PX
                )
                y += stepY
            }
        }
        BoardGridStyle.CROSSHAIRS -> {
            var x = stepX
            while (x < size.width) {
                var y = stepY
                while (y < size.height) {
                    drawLine(
                        color = gridColor,
                        start = Offset(x - GRID_CROSSHAIR_HALF_PX, y),
                        end = Offset(x + GRID_CROSSHAIR_HALF_PX, y),
                        strokeWidth = GRID_LINE_STROKE_PX
                    )
                    drawLine(
                        color = gridColor,
                        start = Offset(x, y - GRID_CROSSHAIR_HALF_PX),
                        end = Offset(x, y + GRID_CROSSHAIR_HALF_PX),
                        strokeWidth = GRID_LINE_STROKE_PX
                    )
                    y += stepY
                }
                x += stepX
            }
        }
    }
}

private fun DrawScope.drawBoardShape(
    shapeType: BoardShapeType,
    strokeColor: Color,
    fillColor: Color?,
    strokeWidth: Float
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return

    when (shapeType) {
        BoardShapeType.RECTANGLE -> {
            if (fillColor != null) {
                drawRect(color = fillColor, style = Fill)
            }
            drawRect(color = strokeColor, style = Stroke(width = strokeWidth))
        }
        BoardShapeType.ROUNDED_RECT -> {
            val radius = min(w, h) * SHAPE_ROUNDED_CORNER_RATIO
            if (fillColor != null) {
                drawRoundRect(
                    color = fillColor,
                    cornerRadius = CornerRadius(radius, radius),
                    style = Fill
                )
            }
            drawRoundRect(
                color = strokeColor,
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = strokeWidth)
            )
        }
        BoardShapeType.ELLIPSE -> {
            if (fillColor != null) {
                drawOval(color = fillColor, style = Fill)
            }
            drawOval(color = strokeColor, style = Stroke(width = strokeWidth))
        }
        BoardShapeType.CALLOUT_BUBBLE -> {
            val bodyH = h * CALLOUT_BODY_HEIGHT_RATIO
            val path = Path().apply {
                moveTo(0f, 0f)
                lineTo(w, 0f)
                lineTo(w, bodyH)
                lineTo(w * CALLOUT_TAIL_END_X_RATIO, bodyH)
                lineTo(w * CALLOUT_TAIL_TIP_X_RATIO, h)
                lineTo(w * CALLOUT_TAIL_START_X_RATIO, bodyH)
                lineTo(0f, bodyH)
                close()
            }
            if (fillColor != null) {
                drawPath(path = path, color = fillColor, style = Fill)
            }
            drawPath(
                path = path,
                color = strokeColor,
                style = Stroke(width = strokeWidth, join = StrokeJoin.Round)
            )
        }
        BoardShapeType.ARROW -> {
            val start = Offset(strokeWidth, h / 2f)
            val end = Offset(w - strokeWidth, h / 2f)
            drawLine(
                color = strokeColor,
                start = start,
                end = end,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            val angle = atan2(end.y - start.y, end.x - start.x)
            val head1 = Offset(
                x = end.x - ARROW_HEAD_LENGTH_PX * cos(angle - ARROW_HEAD_ANGLE_RAD),
                y = end.y - ARROW_HEAD_LENGTH_PX * sin(angle - ARROW_HEAD_ANGLE_RAD)
            )
            val head2 = Offset(
                x = end.x - ARROW_HEAD_LENGTH_PX * cos(angle + ARROW_HEAD_ANGLE_RAD),
                y = end.y - ARROW_HEAD_LENGTH_PX * sin(angle + ARROW_HEAD_ANGLE_RAD)
            )
            drawLine(
                color = strokeColor,
                start = end,
                end = head1,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = strokeColor,
                start = end,
                end = head2,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawVectorPath(
    pathData: VectorPathData,
    width: Float,
    height: Float,
    surfaceColor: Color,
    resolveColor: (SemanticColor) -> Color
) {
    if (pathData.points.size < MIN_STROKE_POINTS) return
    val path = createCatmullRomPath(pathData.points, width, height)
    val color = if (pathData.color == SemanticColor.SURFACE) {
        surfaceColor
    } else {
        resolveColor(pathData.color)
    }

    drawPath(
        path = path,
        color = color,
        style = Stroke(
            width = pathData.strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

/**
 * Compute updated normalized position from a base position and pixel drag delta,
 * applying optional grid snapping and clamping within `[0.0f, 1.0f]`.
 */
fun computeEffectivePosition(
    basePos: NormalizedPoint,
    deltaPx: Offset,
    boardWidthPx: Float,
    boardHeightPx: Float,
    snapToGrid: Boolean,
    gridStep: Float
): NormalizedPoint {
    val safeW = boardWidthPx.coerceAtLeast(1f)
    val safeH = boardHeightPx.coerceAtLeast(1f)
    val rawX = (basePos.x + deltaPx.x / safeW).coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
    val rawY = (basePos.y + deltaPx.y / safeH).coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
    return snapPointToGrid(NormalizedPoint(rawX, rawY), snapToGrid, gridStep)
}

/**
 * Compute updated normalized size from a base size and pixel resize delta,
 * applying optional grid snapping and clamping within `[0.02f, 1.0f]`.
 */
fun computeEffectiveSize(
    baseSize: NormalizedSize,
    deltaPx: Offset,
    boardWidthPx: Float,
    boardHeightPx: Float,
    snapToGrid: Boolean,
    gridStep: Float
): NormalizedSize {
    val safeW = boardWidthPx.coerceAtLeast(1f)
    val safeH = boardHeightPx.coerceAtLeast(1f)
    val rawW = (baseSize.width + deltaPx.x / safeW).coerceIn(MIN_NORMALIZED_SIZE, MAX_NORMALIZED_SIZE)
    val rawH = (baseSize.height + deltaPx.y / safeH).coerceIn(MIN_NORMALIZED_SIZE, MAX_NORMALIZED_SIZE)
    return snapSizeToGrid(NormalizedSize(rawW, rawH), snapToGrid, gridStep)
}

/**
 * Snap a single normalized coordinate to the nearest multiple of [gridStep] when [snapToGrid] is true.
 */
fun snapNormalizedToGrid(value: Float, snapToGrid: Boolean, gridStep: Float): Float {
    val clamped = value.coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
    if (!snapToGrid) return clamped
    val safeStep = gridStep.coerceIn(0.01f, 0.25f)
    val snapped = (clamped / safeStep).roundToInt() * safeStep
    return snapped.coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
}

/**
 * Snap a [NormalizedPoint] to the grid when [snapToGrid] is true.
 */
fun snapPointToGrid(point: NormalizedPoint, snapToGrid: Boolean, gridStep: Float): NormalizedPoint {
    if (!snapToGrid) return point.coercedInBounds()
    return NormalizedPoint(
        x = snapNormalizedToGrid(point.x, true, gridStep),
        y = snapNormalizedToGrid(point.y, true, gridStep)
    )
}

/**
 * Snap a [NormalizedSize] to the grid when [snapToGrid] is true.
 */
fun snapSizeToGrid(size: NormalizedSize, snapToGrid: Boolean, gridStep: Float): NormalizedSize {
    if (!snapToGrid) return size.coercedInBounds()
    val safeStep = gridStep.coerceIn(0.01f, 0.25f)
    val snappedW = ((size.width / safeStep).roundToInt() * safeStep).coerceIn(MIN_NORMALIZED_SIZE, MAX_NORMALIZED_SIZE)
    val snappedH = ((size.height / safeStep).roundToInt() * safeStep).coerceIn(MIN_NORMALIZED_SIZE, MAX_NORMALIZED_SIZE)
    return NormalizedSize(snappedW, snappedH)
}

/**
 * Compute new `(scale, panOffset)` when zooming around [cursorPos] with scroll wheel delta [scrollDeltaY].
 */
fun computeZoomAroundPoint(
    currentScale: Float,
    currentPan: Offset,
    scrollDeltaY: Float,
    cursorPos: Offset,
    minScale: Float,
    maxScale: Float
): Pair<Float, Offset> {
    val zoomFactor = (1f - scrollDeltaY * WHEEL_ZOOM_SENSITIVITY).coerceIn(0.5f, 2.0f)
    val newScale = (currentScale * zoomFactor).coerceIn(minScale, maxScale)
    val effectiveFactor = if (currentScale > 0f) newScale / currentScale else 1f
    val newPan = cursorPos - (cursorPos - currentPan) * effectiveFactor
    return newScale to newPan
}

/**
 * Construct smooth Catmull-Rom spline path through normalized points, clamping points and
 * cubic Bezier control points strictly within [0.0f, 1.0f] so strokes never overshoot canvas bounds.
 */
fun createCatmullRomPath(points: List<NormalizedPoint>, width: Float, height: Float): Path {
    val path = Path()
    if (points.isEmpty()) return path

    val p0x = points.first().x.coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
    val p0y = points.first().y.coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
    path.moveTo(p0x * width, p0y * height)

    if (points.size == 1) return path

    if (points.size == MIN_STROKE_POINTS) {
        val p1x = points[1].x.coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
        val p1y = points[1].y.coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
        path.lineTo(p1x * width, p1y * height)
        return path
    }

    for (i in 0 until points.size - 1) {
        val prev = points[max(0, i - 1)]
        val curr = points[i]
        val next = points[i + 1]
        val nextNext = points[min(points.size - 1, i + 2)]

        val prevX = prev.x.coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
        val prevY = prev.y.coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
        val currX = curr.x.coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
        val currY = curr.y.coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
        val nextX = next.x.coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
        val nextY = next.y.coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
        val nextNextX = nextNext.x.coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
        val nextNextY = nextNext.y.coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)

        val cp1x = (currX + (nextX - prevX) / CATMULL_ROM_TANGENT_DIVISOR)
            .coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
        val cp1y = (currY + (nextY - prevY) / CATMULL_ROM_TANGENT_DIVISOR)
            .coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)

        val cp2x = (nextX - (nextNextX - currX) / CATMULL_ROM_TANGENT_DIVISOR)
            .coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)
        val cp2y = (nextY - (nextNextY - currY) / CATMULL_ROM_TANGENT_DIVISOR)
            .coerceIn(MIN_NORMALIZED_COORD, MAX_NORMALIZED_COORD)

        path.cubicTo(
            cp1x * width, cp1y * height,
            cp2x * width, cp2y * height,
            nextX * width, nextY * height
        )
    }

    return path
}

/**
 * Ramer-Douglas-Peucker (RDP) algorithm for polyline simplification in normalized coordinates.
 */
fun rdpSimplify(points: List<NormalizedPoint>, epsilon: Float): List<NormalizedPoint> {
    if (points.size <= 2) return points

    var dmax = 0f
    var index = 0
    val start = points.first()
    val end = points.last()

    for (i in 1 until points.size - 1) {
        val d = perpendicularDistance(points[i], start, end)
        if (d > dmax) {
            index = i
            dmax = d
        }
    }

    return if (dmax > epsilon) {
        val left = rdpSimplify(points.subList(0, index + 1), epsilon)
        val right = rdpSimplify(points.subList(index, points.size), epsilon)
        left.dropLast(1) + right
    } else {
        listOf(start, end)
    }
}

private fun perpendicularDistance(
    pt: NormalizedPoint,
    lineStart: NormalizedPoint,
    lineEnd: NormalizedPoint
): Float {
    val dx = lineEnd.x - lineStart.x
    val dy = lineEnd.y - lineStart.y
    val mag = hypot(dx.toDouble(), dy.toDouble()).toFloat()
    if (mag == 0f) return hypot((pt.x - lineStart.x).toDouble(), (pt.y - lineStart.y).toDouble()).toFloat()
    val u = ((pt.x - lineStart.x) * dx + (pt.y - lineStart.y) * dy) / (mag * mag)
    val clampedU = u.coerceIn(0f, 1f)
    val projX = lineStart.x + clampedU * dx
    val projY = lineStart.y + clampedU * dy
    return hypot((pt.x - projX).toDouble(), (pt.y - projY).toDouble()).toFloat()
}

/**
 * Map [SemanticColor] to Compose Color.
 */
@Composable
fun SemanticColor.toComposeColor(): Color = when (this) {
    SemanticColor.PRIMARY -> MaterialTheme.colorScheme.primary
    SemanticColor.SECONDARY -> MaterialTheme.colorScheme.secondary
    SemanticColor.TERTIARY -> MaterialTheme.colorScheme.tertiary
    SemanticColor.ERROR -> MaterialTheme.colorScheme.error
    SemanticColor.SURFACE -> MaterialTheme.colorScheme.surface
    SemanticColor.ON_SURFACE -> MaterialTheme.colorScheme.onSurface
    SemanticColor.OUTLINE -> MaterialTheme.colorScheme.outline
    SemanticColor.ACCENT -> MaterialTheme.colorScheme.primary
    SemanticColor.HIGHLIGHT -> ToolkitTheme.colors.warningContainer
    SemanticColor.INFO -> ToolkitTheme.colors.info
    SemanticColor.SUCCESS -> ToolkitTheme.colors.success
    SemanticColor.WARNING -> ToolkitTheme.colors.warning
}
