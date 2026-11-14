package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.sdui.GridNode
import org.wip.plugintoolkit.api.sdui.ScrollableColumnNode
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.api.sdui.UiNode
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformVerticalScrollbar
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.sdui_drag_reorder

private const val MAX_GRID_COLUMNS = 12
private const val MIN_GRID_ITEM_WIDTH_DP = 72f
private const val MAX_GRID_ITEM_WIDTH_DP = 600f
private const val DRAG_SCALE_FACTOR = 1.03f
private const val SAME_ROW_VERTICAL_TOLERANCE = 0.5f

/**
 * Coordinates pointer drag-and-drop state for reorderable grids and columns,
 * emitting [UiEvent.ItemsReordered] on drop with live flicker-free slot swapping.
 */
internal class SduiDragReorderState(
    initialIds: List<String>,
    private val containerNodeId: String,
    private val onEvent: (UiEvent) -> Unit
) {
    var orderedIds by mutableStateOf(initialIds)
    var draggedId by mutableStateOf<String?>(null)
        private set

    private var startIndex: Int = -1
    private var initialItemOrigin: Offset = Offset.Zero
    private var startPointerInRoot: Offset = Offset.Zero
    private var currentPointerInRoot: Offset = Offset.Zero
    var rootCoords: LayoutCoordinates? = null
    private val itemCoords = mutableMapOf<String, LayoutCoordinates>()
    private val itemBoundsInRoot = mutableMapOf<String, Rect>()

    fun syncOrder(serverIds: List<String>) {
        if (draggedId == null && orderedIds != serverIds) {
            orderedIds = serverIds
        }
    }

    fun registerItemCoords(itemId: String, coords: LayoutCoordinates) {
        itemCoords[itemId] = coords
        val root = rootCoords ?: return
        if (root.isAttached && coords.isAttached) {
            itemBoundsInRoot[itemId] = root.localBoundingBoxOf(coords, clipBounds = false)
        }
    }

    fun startDrag(itemId: String, localPos: Offset) {
        val root = rootCoords ?: return
        val coords = itemCoords[itemId] ?: return
        val bounds = itemBoundsInRoot[itemId] ?: return
        if (!root.isAttached || !coords.isAttached) return
        val pointerRoot = root.localPositionOf(coords, localPos)
        draggedId = itemId
        startIndex = orderedIds.indexOf(itemId)
        initialItemOrigin = bounds.topLeft
        startPointerInRoot = pointerRoot
        currentPointerInRoot = pointerRoot
    }

    fun updateDrag(itemId: String, localPos: Offset) {
        if (draggedId != itemId) return
        val root = rootCoords ?: return
        val coords = itemCoords[itemId] ?: return
        if (!root.isAttached || !coords.isAttached) return
        val pointerRoot = root.localPositionOf(coords, localPos)
        currentPointerInRoot = pointerRoot

        val currentIdx = orderedIds.indexOf(itemId)
        val currentBounds = itemBoundsInRoot[itemId] ?: return
        if (currentIdx < 0) return

        val targetId = findHysteresisSwapTarget(
            activeId = itemId,
            activeIndex = currentIdx,
            activeBounds = currentBounds,
            pointerRoot = pointerRoot
        ) ?: return

        val targetIdx = orderedIds.indexOf(targetId)
        if (targetIdx >= 0 && targetIdx != currentIdx) {
            val updated = orderedIds.toMutableList()
            val moving = updated.removeAt(currentIdx)
            updated.add(targetIdx, moving)
            orderedIds = updated
        }
    }

    /**
     * Evaluates swap candidates using midpoint hysteresis so uneven item sizes never ping-pong
     * and exact center drops in UI tests swap deterministically.
     */
    private fun findHysteresisSwapTarget(
        activeId: String,
        activeIndex: Int,
        activeBounds: Rect,
        pointerRoot: Offset
    ): String? {
        for ((candidateId, candRect) in itemBoundsInRoot) {
            if (candidateId == activeId) continue
            if (!candRect.contains(pointerRoot)) continue
            val candIdx = orderedIds.indexOf(candidateId)
            if (candIdx < 0 || candIdx == activeIndex) continue

            val minHeight = minOf(candRect.height, activeBounds.height)
            val sameRowBand = abs(candRect.center.y - activeBounds.center.y) <
                minHeight * SAME_ROW_VERTICAL_TOLERANCE
            val movingForward = candIdx > activeIndex

            val crossedMidpoint = if (sameRowBand) {
                if (movingForward) pointerRoot.x >= candRect.center.x else pointerRoot.x <= candRect.center.x
            } else {
                if (movingForward) pointerRoot.y >= candRect.center.y else pointerRoot.y <= candRect.center.y
            }

            if (crossedMidpoint) {
                return candidateId
            }
        }
        return null
    }

    fun finishDrag(commit: Boolean) {
        val activeId = draggedId ?: return
        val endIndex = orderedIds.indexOf(activeId)
        val snapshot = orderedIds.toList()
        val fromIdx = startIndex
        draggedId = null
        startIndex = -1
        initialItemOrigin = Offset.Zero
        startPointerInRoot = Offset.Zero
        currentPointerInRoot = Offset.Zero
        if (commit && fromIdx >= 0 && endIndex >= 0 && fromIdx != endIndex) {
            onEvent(
                UiEvent.ItemsReordered(
                    nodeId = containerNodeId,
                    itemIds = snapshot,
                    fromIndex = fromIdx,
                    toIndex = endIndex
                )
            )
        }
    }

    fun translationFor(itemId: String): Offset {
        if (draggedId != itemId) return Offset.Zero
        val currentBounds = itemBoundsInRoot[itemId] ?: return Offset.Zero
        val delta = currentPointerInRoot - startPointerInRoot
        return (initialItemOrigin + delta) - currentBounds.topLeft
    }
}

@Composable
internal fun rememberSduiDragReorderState(
    containerNodeId: String,
    children: List<UiNode>,
    onEvent: (UiEvent) -> Unit
): Pair<SduiDragReorderState, List<UiNode>> {
    val childIds = children.map { it.id }
    val state = remember(containerNodeId) {
        SduiDragReorderState(
            initialIds = childIds,
            containerNodeId = containerNodeId,
            onEvent = onEvent
        )
    }
    state.syncOrder(childIds)
    val childMap = remember(children) { children.associateBy { it.id } }
    val orderedChildren = state.orderedIds.mapNotNull { childMap[it] }
    return state to orderedChildren
}

/**
 * Renders an item slot inside a reorderable container with smooth pointer drag physics,
 * elevation feedback, and a subtle hover-revealed drag indicator.
 */
@Composable
internal fun ReorderableSduiItemSlot(
    node: UiNode,
    reorderState: SduiDragReorderState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemId = node.id
    val isDragged = reorderState.draggedId == itemId
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val dragHint = stringResource(Res.string.sdui_drag_reorder)
    val density = LocalDensity.current

    val scale by animateFloatAsState(
        targetValue = if (isDragged) DRAG_SCALE_FACTOR else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "reorder_scale"
    )
    val dragElevation by animateDpAsState(
        targetValue = if (isDragged) ToolkitTheme.dimensions.sduiDragElevation else ToolkitTheme.spacing.none,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "reorder_elevation"
    )

    Box(
        modifier = modifier
            .zIndex(if (isDragged) 10f else 0f)
            .onGloballyPositioned { coords -> reorderState.registerItemCoords(itemId, coords) }
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics { contentDescription = dragHint }
            .pointerInput(itemId, reorderState) {
                detectDragGestures(
                    onDragStart = { startOffset -> reorderState.startDrag(itemId, startOffset) },
                    onDrag = { change, _ ->
                        change.consume()
                        reorderState.updateDrag(itemId, change.position)
                    },
                    onDragEnd = { reorderState.finishDrag(commit = true) },
                    onDragCancel = { reorderState.finishDrag(commit = false) }
                )
            }
    ) {
        val translation = reorderState.translationFor(itemId)
        val borderStroke = when {
            isDragged -> BorderStroke(ToolkitTheme.dimensions.borderSelected, MaterialTheme.colorScheme.primary)
            isHovered -> BorderStroke(
                ToolkitTheme.dimensions.borderUnselected,
                MaterialTheme.colorScheme.primary.copy(alpha = ToolkitTheme.opacity.high)
            )
            else -> null
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = translation.x
                    translationY = translation.y
                    scaleX = scale
                    scaleY = scale
                    shadowElevation = with(density) { dragElevation.toPx() }
                },
            shape = ToolkitTheme.shapes.medium,
            border = borderStroke,
            color = MaterialTheme.colorScheme.surface.copy(alpha = ToolkitTheme.opacity.transparent)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                SafeUiNodeRenderer(
                    node = node,
                    onEvent = onEvent,
                    modifier = Modifier.fillMaxWidth()
                )
                AnimatedVisibility(
                    visible = isHovered || isDragged,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(ToolkitTheme.spacing.xs)
                ) {
                    Surface(
                        shape = ToolkitTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(
                            alpha = ToolkitTheme.opacity.almostOpaque
                        ),
                        border = BorderStroke(
                            ToolkitTheme.dimensions.borderUnselected,
                            MaterialTheme.colorScheme.outlineVariant
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragIndicator,
                            contentDescription = dragHint,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(ToolkitTheme.spacing.xxs)
                                .size(ToolkitTheme.dimensions.iconSmall)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders a reorderable vertical list of children, wrapping in vertical scroll with a desktop
 * scrollbar when height is bounded and not already inside an outer scroll container.
 */
@Composable
internal fun RenderReorderableColumnNode(
    nodeId: String,
    children: List<UiNode>,
    spacing: Dp,
    horizontalAlignment: Alignment.Horizontal,
    scrollable: Boolean,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val inScroll = LocalInVerticalScroll.current
    val (reorderState, orderedChildren) = rememberSduiDragReorderState(nodeId, children, onEvent)

    BoxWithConstraints(modifier = modifier) {
        val shouldScroll = scrollable && !inScroll && constraints.hasBoundedHeight
        if (shouldScroll) {
            val scrollState = rememberScrollState()
            val scrollbarReserve = if (scrollState.maxValue > 0) {
                ToolkitTheme.dimensions.sduiScrollbarEndPadding
            } else {
                ToolkitTheme.spacing.none
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                CompositionLocalProvider(LocalInVerticalScroll provides true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(end = scrollbarReserve)
                            .onGloballyPositioned { reorderState.rootCoords = it },
                        verticalArrangement = Arrangement.spacedBy(spacing),
                        horizontalAlignment = horizontalAlignment
                    ) {
                        orderedChildren.forEach { child ->
                            key(child.id) {
                                ReorderableSduiItemSlot(
                                    node = child,
                                    reorderState = reorderState,
                                    onEvent = onEvent,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                PlatformVerticalScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { reorderState.rootCoords = it },
                verticalArrangement = Arrangement.spacedBy(spacing),
                horizontalAlignment = horizontalAlignment
            ) {
                orderedChildren.forEach { child ->
                    key(child.id) {
                        ReorderableSduiItemSlot(
                            node = child,
                            reorderState = reorderState,
                            onEvent = onEvent,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders [ScrollableColumnNode] using virtualized [LazyColumn] when height is bounded,
 * or falls back cleanly inside outer scroll containers / unbounded dialogs.
 */
@Composable
internal fun RenderScrollableColumnNode(
    node: ScrollableColumnNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacingDp = resolveContainerSpacing(node.spacing, node.children, isVertical = true)
    val horizontalAlignment = node.modifier.alignment?.toHorizontalAlignment() ?: Alignment.Start

    if (node.reorderable) {
        RenderReorderableColumnNode(
            nodeId = node.id,
            children = node.children,
            spacing = spacingDp,
            horizontalAlignment = horizontalAlignment,
            scrollable = true,
            onEvent = onEvent,
            modifier = modifier
        )
        return
    }

    val inScroll = LocalInVerticalScroll.current
    if (inScroll) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(spacingDp),
            horizontalAlignment = horizontalAlignment
        ) {
            RenderColumnChildren(children = node.children, onEvent = onEvent, allowWeight = false)
        }
        return
    }

    BoxWithConstraints(modifier = modifier) {
        if (!constraints.hasBoundedHeight) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacingDp),
                horizontalAlignment = horizontalAlignment
            ) {
                RenderColumnChildren(children = node.children, onEvent = onEvent, allowWeight = false)
            }
        } else {
            val lazyListState = rememberLazyListState()
            val canScroll = lazyListState.canScrollForward || lazyListState.canScrollBackward
            val scrollbarReserve = if (canScroll) {
                ToolkitTheme.dimensions.sduiScrollbarEndPadding
            } else {
                ToolkitTheme.spacing.none
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                CompositionLocalProvider(LocalInVerticalScroll provides true) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(end = scrollbarReserve),
                        verticalArrangement = Arrangement.spacedBy(spacingDp),
                        horizontalAlignment = horizontalAlignment
                    ) {
                        items(node.children, key = { it.id }) { child ->
                            SafeUiNodeRenderer(node = child, onEvent = onEvent)
                        }
                    }
                }
                PlatformVerticalScrollbar(
                    lazyListState = lazyListState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                )
            }
        }
    }
}

/**
 * Shared row-chunked grid layout used for both non-lazy nested grids and reorderable grids.
 */
@Composable
private fun GridRowsLayout(
    items: List<UiNode>,
    columnCount: Int,
    hSpacing: Dp,
    vSpacing: Dp,
    modifier: Modifier = Modifier,
    renderItem: @Composable (UiNode, Modifier) -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(vSpacing)
    ) {
        items.chunked(columnCount).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(hSpacing),
                verticalAlignment = Alignment.Top
            ) {
                rowItems.forEach { child ->
                    key(child.id) {
                        renderItem(child, Modifier.weight(1f))
                    }
                }
                repeat(columnCount - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Renders [GridNode] with either adaptive or fixed column counts, lazy virtualization when
 * bounded and static, and full drag-and-drop reordering when `reorderable = true`.
 */
@Composable
internal fun RenderGridNode(
    node: GridNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val vSpacing = resolveContainerSpacing(node.spacing, node.children, isVertical = true)
    val hSpacing = resolveContainerSpacing(node.spacing, node.children, isVertical = false)
    val inScroll = LocalInVerticalScroll.current
    val clampedMinItemDp = node.minItemWidthDp.coerceIn(MIN_GRID_ITEM_WIDTH_DP, MAX_GRID_ITEM_WIDTH_DP)

    BoxWithConstraints(modifier = modifier) {
        val columnCount = node.columns?.coerceIn(1, MAX_GRID_COLUMNS)
            ?: maxOf(1, (maxWidth.value / clampedMinItemDp).toInt())
        val boundedHeight = constraints.hasBoundedHeight && !inScroll

        if (node.reorderable) {
            val (reorderState, orderedChildren) = rememberSduiDragReorderState(node.id, node.children, onEvent)
            if (boundedHeight) {
                val scrollState = rememberScrollState()
                val scrollbarReserve = if (scrollState.maxValue > 0) {
                    ToolkitTheme.dimensions.sduiScrollbarEndPadding
                } else {
                    ToolkitTheme.spacing.none
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    CompositionLocalProvider(LocalInVerticalScroll provides true) {
                        GridRowsLayout(
                            items = orderedChildren,
                            columnCount = columnCount,
                            hSpacing = hSpacing,
                            vSpacing = vSpacing,
                            modifier = Modifier
                                .verticalScroll(scrollState)
                                .padding(end = scrollbarReserve)
                                .onGloballyPositioned { reorderState.rootCoords = it }
                        ) { child, slotMod ->
                            ReorderableSduiItemSlot(
                                node = child,
                                reorderState = reorderState,
                                onEvent = onEvent,
                                modifier = slotMod
                            )
                        }
                    }
                    PlatformVerticalScrollbar(
                        scrollState = scrollState,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    )
                }
            } else {
                GridRowsLayout(
                    items = orderedChildren,
                    columnCount = columnCount,
                    hSpacing = hSpacing,
                    vSpacing = vSpacing,
                    modifier = Modifier.onGloballyPositioned { reorderState.rootCoords = it }
                ) { child, slotMod ->
                    ReorderableSduiItemSlot(
                        node = child,
                        reorderState = reorderState,
                        onEvent = onEvent,
                        modifier = slotMod
                    )
                }
            }
            return@BoxWithConstraints
        }

        if (!boundedHeight) {
            GridRowsLayout(
                items = node.children,
                columnCount = columnCount,
                hSpacing = hSpacing,
                vSpacing = vSpacing
            ) { child, slotMod ->
                SafeUiNodeRenderer(node = child, onEvent = onEvent, modifier = slotMod)
            }
        } else {
            val gridCells = node.columns?.let { GridCells.Fixed(it.coerceIn(1, MAX_GRID_COLUMNS)) }
                ?: GridCells.Adaptive(minSize = clampedMinItemDp.dp)
            val lazyGridState = rememberLazyGridState()
            val canScroll = lazyGridState.canScrollForward || lazyGridState.canScrollBackward
            val scrollbarReserve = if (canScroll) {
                ToolkitTheme.dimensions.sduiScrollbarEndPadding
            } else {
                ToolkitTheme.spacing.none
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                CompositionLocalProvider(LocalInVerticalScroll provides true) {
                    LazyVerticalGrid(
                        columns = gridCells,
                        state = lazyGridState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(end = scrollbarReserve),
                        verticalArrangement = Arrangement.spacedBy(vSpacing),
                        horizontalArrangement = Arrangement.spacedBy(hSpacing)
                    ) {
                        items(node.children, key = { it.id }) { child ->
                            SafeUiNodeRenderer(node = child, onEvent = onEvent)
                        }
                    }
                }
                PlatformVerticalScrollbar(
                    lazyGridState = lazyGridState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                )
            }
        }
    }
}
