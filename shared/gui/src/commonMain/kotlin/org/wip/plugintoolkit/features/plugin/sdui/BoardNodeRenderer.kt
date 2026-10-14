package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatPaint
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.sdui.BoardLayer
import org.wip.plugintoolkit.api.sdui.BoardNode
import org.wip.plugintoolkit.api.sdui.BoardTool
import org.wip.plugintoolkit.api.sdui.NormalizedPoint
import org.wip.plugintoolkit.api.sdui.SemanticColor
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.api.sdui.VectorPathData
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.sdui_board_clear
import plugintoolkit.composeapp.generated.resources.sdui_board_eraser
import plugintoolkit.composeapp.generated.resources.sdui_board_pen
import plugintoolkit.composeapp.generated.resources.sdui_board_redo
import plugintoolkit.composeapp.generated.resources.sdui_board_undo
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive drawing board renderer for SDUI [BoardNode].
 * Provides normalized pointer tracking (0..1), Catmull-Rom smoothing, RDP simplification,
 * and optimistic stroke retention.
 */
@Composable
fun BoardNodeRenderer(
    node: BoardNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTool by remember(node.activeTool) { mutableStateOf<BoardTool>(node.activeTool) }
    val currentPoints = remember { mutableStateListOf<NormalizedPoint>() }
    val pendingCommittedStrokes = remember { mutableStateListOf<VectorPathData>() }

    // Clear strokes when clearRevision increments
    LaunchedEffect(node.clearRevision) {
        if (node.clearRevision > 0) {
            pendingCommittedStrokes.clear()
        }
    }

    // Reconcile optimistic strokes when node.layers updates
    LaunchedEffect(node.layers) {
        val serverStrokeIds = node.layers.filterIsInstance<BoardLayer.PathLayer>().map { it.path.strokeId }.toSet()
        pendingCommittedStrokes.removeAll { it.strokeId in serverStrokeIds }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val errorColor = MaterialTheme.colorScheme.error
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerLowest
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val outlineColor = MaterialTheme.colorScheme.outline
    val highlightColor = ToolkitTheme.colors.warningContainer

    val resolveColor: (SemanticColor) -> Color = { color ->
        when (color) {
            SemanticColor.PRIMARY -> primaryColor
            SemanticColor.SECONDARY -> secondaryColor
            SemanticColor.TERTIARY -> tertiaryColor
            SemanticColor.ERROR -> errorColor
            SemanticColor.SURFACE -> surfaceColor
            SemanticColor.ON_SURFACE -> onSurfaceColor
            SemanticColor.OUTLINE -> outlineColor
            SemanticColor.ACCENT -> primaryColor
            SemanticColor.HIGHLIGHT -> highlightColor
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ToolkitTheme.dimensions.textFieldCornerRadius))
            .background(surfaceColor)
            .border(
                width = ToolkitTheme.dimensions.borderUnselected,
                color = outlineVariant,
                shape = RoundedCornerShape(ToolkitTheme.dimensions.textFieldCornerRadius)
            ),
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
    ) {
        // Toolbar with tools and actions
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
                // Tool selection chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedTool is BoardTool.Brush,
                        onClick = {
                            val tool = BoardTool.Brush()
                            selectedTool = tool
                            onEvent(UiEvent.Custom(node.id, "SELECT_TOOL", JsonPrimitive("brush")))
                        },
                        label = { Text(stringResource(Res.string.sdui_board_pen), style = ToolkitTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(Res.string.sdui_board_pen),
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = primaryColor.copy(alpha = 0.2f),
                            selectedLabelColor = primaryColor
                        )
                    )

                    FilterChip(
                        selected = selectedTool is BoardTool.Highlighter,
                        onClick = {
                            val tool = BoardTool.Highlighter()
                            selectedTool = tool
                            onEvent(UiEvent.Custom(node.id, "SELECT_TOOL", JsonPrimitive("highlighter")))
                        },
                        label = { Text("Highlighter", style = ToolkitTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.FormatPaint,
                                contentDescription = "Highlighter",
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                            )
                        }
                    )

                    FilterChip(
                        selected = selectedTool is BoardTool.Eraser,
                        onClick = {
                            val tool = BoardTool.Eraser()
                            selectedTool = tool
                            onEvent(UiEvent.Custom(node.id, "SELECT_TOOL", JsonPrimitive("eraser")))
                        },
                        label = { Text(stringResource(Res.string.sdui_board_eraser), style = ToolkitTheme.typography.labelSmall) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = stringResource(Res.string.sdui_board_eraser),
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = errorColor.copy(alpha = 0.2f),
                            selectedLabelColor = errorColor
                        )
                    )
                }

                // Undo / Redo / Clear actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onEvent(UiEvent.Custom(node.id, "UNDO")) },
                        modifier = Modifier.size(ToolkitTheme.dimensions.standardButtonHeight)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = stringResource(Res.string.sdui_board_undo),
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                        )
                    }

                    IconButton(
                        onClick = { onEvent(UiEvent.Custom(node.id, "REDO")) },
                        modifier = Modifier.size(ToolkitTheme.dimensions.standardButtonHeight)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = stringResource(Res.string.sdui_board_redo),
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                        )
                    }

                    IconButton(
                        onClick = {
                            pendingCommittedStrokes.clear()
                            onEvent(UiEvent.Custom(node.id, "CLEAR"))
                        },
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

        // Drawing Canvas (16:9 responsive aspect ratio)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(surfaceColor)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(node.id, selectedTool) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                if (w > 0 && h > 0) {
                                    val nx = (offset.x / w).coerceIn(0f, 1f)
                                    val ny = (offset.y / h).coerceIn(0f, 1f)
                                    currentPoints.clear()
                                    currentPoints.add(NormalizedPoint(nx, ny))
                                }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                if (w > 0 && h > 0) {
                                    val nx = (change.position.x / w).coerceIn(0f, 1f)
                                    val ny = (change.position.y / h).coerceIn(0f, 1f)
                                    currentPoints.add(NormalizedPoint(nx, ny))
                                }
                            },
                            onDragEnd = {
                                if (currentPoints.size >= 2) {
                                    val simplified = rdpSimplify(currentPoints.toList(), epsilon = 0.002f)
                                    val strokeId = "stroke_${System.currentTimeMillis()}"
                                    val (color, strokeWidth) = when (val t = selectedTool) {
                                        is BoardTool.Brush -> t.color to t.strokeWidth
                                        is BoardTool.Highlighter -> t.color to t.strokeWidth
                                        is BoardTool.Rectangle -> t.color to t.strokeWidth
                                        is BoardTool.Eraser -> SemanticColor.SURFACE to t.radius
                                    }

                                    val newStroke = VectorPathData(
                                        strokeId = strokeId,
                                        points = simplified,
                                        color = color,
                                        strokeWidth = strokeWidth
                                    )
                                    // Retain optimistically
                                    pendingCommittedStrokes.add(newStroke)
                                    // Dispatch to host/plugin
                                    onEvent(UiEvent.PathCommitted(node.id, newStroke))
                                }
                                currentPoints.clear()
                            },
                            onDragCancel = {
                                currentPoints.clear()
                            }
                        )
                    }
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                // 1. Draw confirmed layers from server
                node.layers.forEach { layer ->
                    when (layer) {
                        is BoardLayer.PathLayer -> {
                            drawVectorPath(layer.path, canvasWidth, canvasHeight, surfaceColor, resolveColor)
                        }
                        is BoardLayer.TextLayer -> {
                            // Text layer position is normalized
                        }
                    }
                }

                // 2. Draw optimistic pending strokes
                pendingCommittedStrokes.forEach { stroke ->
                    drawVectorPath(stroke, canvasWidth, canvasHeight, surfaceColor, resolveColor)
                }

                // 3. Draw currently active in-progress stroke with Catmull-Rom smoothing
                if (currentPoints.size >= 2) {
                    val activeColor = if (selectedTool is BoardTool.Eraser) surfaceColor else primaryColor
                    val activeWidth = when (val t = selectedTool) {
                        is BoardTool.Brush -> t.strokeWidth
                        is BoardTool.Highlighter -> t.strokeWidth
                        is BoardTool.Rectangle -> t.strokeWidth
                        is BoardTool.Eraser -> t.radius
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
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVectorPath(
    pathData: VectorPathData,
    width: Float,
    height: Float,
    surfaceColor: Color,
    resolveColor: (SemanticColor) -> Color
) {
    if (pathData.points.size < 2) return
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
 * Construct smooth Catmull-Rom spline path through normalized points.
 */
fun createCatmullRomPath(points: List<NormalizedPoint>, width: Float, height: Float): Path {
    val path = Path()
    if (points.isEmpty()) return path

    val p0 = points.first()
    path.moveTo(p0.x * width, p0.y * height)

    if (points.size == 1) return path

    if (points.size == 2) {
        val p1 = points[1]
        path.lineTo(p1.x * width, p1.y * height)
        return path
    }

    for (i in 0 until points.size - 1) {
        val prev = points[max(0, i - 1)]
        val curr = points[i]
        val next = points[i + 1]
        val nextNext = points[min(points.size - 1, i + 2)]

        val cp1x = curr.x + (next.x - prev.x) / 6f
        val cp1y = curr.y + (next.y - prev.y) / 6f

        val cp2x = next.x - (nextNext.x - curr.x) / 6f
        val cp2y = next.y - (nextNext.y - curr.y) / 6f

        path.cubicTo(
            cp1x * width, cp1y * height,
            cp2x * width, cp2y * height,
            next.x * width, next.y * height
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
}
