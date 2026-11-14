package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.roundToInt
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
private const val DRAG_Z_INDEX = 10f
private const val SWAP_ENTRY_THRESHOLD_RATIO = 0.25f

/**
 * Coordinates pointer drag-and-drop state for reorderable grids and columns,
 * using stationary container coordinates so swaps across rows never interrupt the gesture.
 */
internal class SduiDragReorderState(
    initialIds: List<String>,
    private val containerNodeId: String,
    private val onEvent: (UiEvent) -> Unit
) {
    var orderedIds by mutableStateOf(initialIds)
    var draggedId by mutableStateOf<String?>(null)
        private set
    var startSlotOrigin by mutableStateOf(Offset.Zero)
        private set
    var totalDragDelta by mutableStateOf(Offset.Zero)
        private set

    private var startIndex: Int = -1
    private var startPointerInContainer: Offset = Offset.Zero
    private var slotBounds: List<Rect> = emptyList()
    private var slotColumns: Int = 1
    private var suppressSwapSlotIndex: Int? = null

    fun syncOrder(serverIds: List<String>) {
        if (draggedId == null && orderedIds != serverIds) {
            orderedIds = serverIds
        }
    }

    fun updateLayoutGeometry(bounds: List<Rect>, columnCount: Int) {
        slotBounds = bounds
        slotColumns = columnCount.coerceAtLeast(1)
    }

    fun itemIdAt(containerPoint: Offset): String? {
        val hitIdx = slotBounds.indexOfFirst { it.contains(containerPoint) }
        return if (hitIdx >= 0) orderedIds.getOrNull(hitIdx) else null
    }

    fun startDrag(itemId: String, containerPos: Offset) {
        val idx = orderedIds.indexOf(itemId)
        if (idx < 0) return
        val origin = slotBounds.getOrNull(idx)?.topLeft ?: Offset.Zero
        draggedId = itemId
        startIndex = idx
        startSlotOrigin = origin
        startPointerInContainer = containerPos
        totalDragDelta = Offset.Zero
        suppressSwapSlotIndex = null
    }

    fun updateDrag(pointerInContainer: Offset) {
        val activeId = draggedId ?: return
        totalDragDelta = pointerInContainer - startPointerInContainer

        val currentIdx = orderedIds.indexOf(activeId)
        if (currentIdx < 0) return

        val blockedIdx = suppressSwapSlotIndex
        if (blockedIdx != null) {
            val blockedRect = slotBounds.getOrNull(blockedIdx)
            if (blockedRect == null || !blockedRect.contains(pointerInContainer)) {
                suppressSwapSlotIndex = null
            }
        }

        val targetIdx = find2DSwapTarget(
            activeIndex = currentIdx,
            pointer = pointerInContainer,
            bounds = slotBounds,
            columnCount = slotColumns,
            blockedSlotIndex = suppressSwapSlotIndex
        ) ?: return

        if (targetIdx != currentIdx && targetIdx in orderedIds.indices) {
            val updated = orderedIds.toMutableList()
            val moving = updated.removeAt(currentIdx)
            updated.add(targetIdx, moving)
            orderedIds = updated
            suppressSwapSlotIndex = currentIdx
        }
    }

    /**
     * Evaluates 2D swap candidates directly against stationary grid/column slot bounds.
     * Supports left/right, up/down, and diagonal pointer drags without feedback oscillation.
     */
    private fun find2DSwapTarget(
        activeIndex: Int,
        pointer: Offset,
        bounds: List<Rect>,
        columnCount: Int,
        blockedSlotIndex: Int?
    ): Int? {
        val activeRow = activeIndex / columnCount
        val activeCol = activeIndex % columnCount

        for (candIdx in bounds.indices) {
            if (candIdx == activeIndex || candIdx == blockedSlotIndex) continue
            val candRect = bounds[candIdx]
            if (!candRect.contains(pointer)) continue

            val candRow = candIdx / columnCount
            val candCol = candIdx % columnCount

            val crossedThreshold = if (candRow == activeRow) {
                if (candCol > activeCol) {
                    pointer.x >= candRect.left + (candRect.width * SWAP_ENTRY_THRESHOLD_RATIO)
                } else {
                    pointer.x <= candRect.right - (candRect.width * SWAP_ENTRY_THRESHOLD_RATIO)
                }
            } else {
                if (candRow > activeRow) {
                    pointer.y >= candRect.top + (candRect.height * SWAP_ENTRY_THRESHOLD_RATIO)
                } else {
                    pointer.y <= candRect.bottom - (candRect.height * SWAP_ENTRY_THRESHOLD_RATIO)
                }
            }

            if (crossedThreshold) {
                return candIdx
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
        startSlotOrigin = Offset.Zero
        startPointerInContainer = Offset.Zero
        totalDragDelta = Offset.Zero
        suppressSwapSlotIndex = null
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
}

/**
 * Attaches drag-to-reorder pointer handling to the stationary collection container so neither
 * vertical drags inside outer scrollables nor cross-row slot swaps interrupt active drags.
 */
private fun Modifier.sduiReorderContainerDrag(reorderState: SduiDragReorderState): Modifier =
    pointerInput(reorderState) {
        val touchSlop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (reorderState.orderedIds.size <= 1) return@awaitEachGesture
            val startItemId = reorderState.itemIdAt(down.position) ?: return@awaitEachGesture

            var dragStarted = false
            var activePointerId = down.id
            try {
                while (true) {
                    val pass = if (dragStarted) PointerEventPass.Initial else PointerEventPass.Main
                    val event = awaitPointerEvent(pass = pass)
                    val change = event.changes.firstOrNull { it.id == activePointerId }
                        ?: event.changes.firstOrNull { it.pressed }
                        ?: break
                    activePointerId = change.id

                    if (!change.pressed) {
                        if (dragStarted) {
                            change.consume()
                            reorderState.finishDrag(commit = true)
                        }
                        break
                    }

                    if (!dragStarted) {
                        if (change.isConsumed) break
                        val delta = change.position - down.position
                        if (hypot(delta.x.toDouble(), delta.y.toDouble()) >= touchSlop) {
                            dragStarted = true
                            reorderState.startDrag(startItemId, down.position)
                            reorderState.updateDrag(change.position)
                            change.consume()
                        }
                    } else {
                        reorderState.updateDrag(change.position)
                        change.consume()
                    }
                }
            } finally {
                if (dragStarted && reorderState.draggedId != null) {
                    reorderState.finishDrag(commit = false)
                }
            }
        }
    }

@Composable
internal fun rememberSduiDragReorderState(
    containerNodeId: String,
    children: List<UiNode>,
    onEvent: (UiEvent) -> Unit
): SduiDragReorderState {
    val childIds = children.map { it.id }
    val state = remember(containerNodeId) {
        SduiDragReorderState(
            initialIds = childIds,
            containerNodeId = containerNodeId,
            onEvent = onEvent
        )
    }
    state.syncOrder(childIds)
    return state
}

/**
 * Renders an item slot inside a reorderable container with smooth pointer drag physics,
 * elevation feedback, and a subtle hover-revealed drag indicator.
 */
@Composable
internal fun ReorderableSduiItemSlot(
    node: UiNode,
    isDragged: Boolean,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
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

    val borderStroke = when {
        isDragged -> BorderStroke(ToolkitTheme.dimensions.borderSelected, MaterialTheme.colorScheme.primary)
        isHovered -> BorderStroke(
            ToolkitTheme.dimensions.borderUnselected,
            MaterialTheme.colorScheme.primary.copy(alpha = ToolkitTheme.opacity.high)
        )
        else -> null
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .semantics { contentDescription = dragHint }
            .graphicsLayer {
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

/**
 * Shared single-parent reorderable collection layout supporting both 1D vertical lists (`columnCount = 1`)
 * and 2D multi-column grids (`columnCount > 1`). Keeping all items as direct children of one [Layout]
 * ensures cross-row swaps never reparent composables or cancel active pointer drags.
 */
@Composable
private fun ReorderableCollectionLayout(
    children: List<UiNode>,
    reorderState: SduiDragReorderState,
    columnCount: Int,
    hSpacing: Dp,
    vSpacing: Dp,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeColumns = columnCount.coerceIn(1, MAX_GRID_COLUMNS)
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .sduiReorderContainerDrag(reorderState),
        content = {
            children.forEach { child ->
                key(child.id) {
                    ReorderableSduiItemSlot(
                        node = child,
                        isDragged = reorderState.draggedId == child.id,
                        onEvent = onEvent
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val totalWidth = if (constraints.hasBoundedWidth) {
            constraints.maxWidth.coerceAtLeast(1)
        } else {
            constraints.minWidth.coerceAtLeast(1)
        }
        val hSpacingPx = if (safeColumns > 1) hSpacing.roundToPx() else 0
        val vSpacingPx = vSpacing.roundToPx()
        val cellWidth = ((totalWidth - (hSpacingPx * (safeColumns - 1))) / safeColumns).coerceAtLeast(1)
        val itemConstraints = Constraints(
            minWidth = cellWidth,
            maxWidth = cellWidth,
            minHeight = 0,
            maxHeight = Constraints.Infinity
        )

        val placeablesById = LinkedHashMap<String, Placeable>(children.size)
        children.forEachIndexed { index, child ->
            placeablesById[child.id] = measurables[index].measure(itemConstraints)
        }

        val currentIds = reorderState.orderedIds.filter { it in placeablesById }
        val slotRects = MutableList(currentIds.size) { Rect.Zero }
        var currentY = 0
        val rowChunks = currentIds.chunked(safeColumns)
        rowChunks.forEachIndexed { rowIdx, rowIds ->
            val rowHeight = rowIds.maxOfOrNull { placeablesById[it]?.height ?: 0 } ?: 0
            rowIds.forEachIndexed { colIdx, _ ->
                val slotIdx = (rowIdx * safeColumns) + colIdx
                val left = colIdx * (cellWidth + hSpacingPx)
                slotRects[slotIdx] = Rect(
                    left = left.toFloat(),
                    top = currentY.toFloat(),
                    right = (left + cellWidth).toFloat(),
                    bottom = (currentY + rowHeight).toFloat()
                )
            }
            currentY += rowHeight
            if (rowIdx < rowChunks.lastIndex) {
                currentY += vSpacingPx
            }
        }

        reorderState.updateLayoutGeometry(slotRects, safeColumns)
        val totalHeight = currentY.coerceAtLeast(constraints.minHeight)

        layout(totalWidth, totalHeight) {
            val activeDraggedId = reorderState.draggedId
            val dragOrigin = reorderState.startSlotOrigin
            val dragDelta = reorderState.totalDragDelta

            currentIds.forEachIndexed { slotIdx, id ->
                val placeable = placeablesById[id] ?: return@forEachIndexed
                if (id == activeDraggedId) {
                    placeable.placeRelative(
                        x = (dragOrigin.x + dragDelta.x).roundToInt(),
                        y = (dragOrigin.y + dragDelta.y).roundToInt(),
                        zIndex = DRAG_Z_INDEX
                    )
                } else {
                    val rect = slotRects[slotIdx]
                    placeable.placeRelative(
                        x = rect.left.roundToInt(),
                        y = rect.top.roundToInt(),
                        zIndex = 0f
                    )
                }
            }
        }
    }
}

/**
 * Shared reorderable collection wrapper used by both reorderable columns (`columnCount = 1`)
 * and reorderable grids (`columnCount > 1`), adding a desktop scrollbar when height is bounded.
 */
@Composable
private fun RenderReorderableCollection(
    nodeId: String,
    children: List<UiNode>,
    columnCount: Int,
    hSpacing: Dp,
    vSpacing: Dp,
    scrollable: Boolean,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val inScroll = LocalInVerticalScroll.current
    val reorderState = rememberSduiDragReorderState(nodeId, children, onEvent)

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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(end = scrollbarReserve)
                    ) {
                        ReorderableCollectionLayout(
                            children = children,
                            reorderState = reorderState,
                            columnCount = columnCount,
                            hSpacing = hSpacing,
                            vSpacing = vSpacing,
                            onEvent = onEvent
                        )
                    }
                }
                PlatformVerticalScrollbar(
                    scrollState = scrollState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                )
            }
        } else {
            ReorderableCollectionLayout(
                children = children,
                reorderState = reorderState,
                columnCount = columnCount,
                hSpacing = hSpacing,
                vSpacing = vSpacing,
                onEvent = onEvent
            )
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
    RenderReorderableCollection(
        nodeId = nodeId,
        children = children,
        columnCount = 1,
        hSpacing = ToolkitTheme.spacing.none,
        vSpacing = spacing,
        scrollable = scrollable,
        onEvent = onEvent,
        modifier = modifier
    )
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
            RenderReorderableCollection(
                nodeId = node.id,
                children = node.children,
                columnCount = columnCount,
                hSpacing = hSpacing,
                vSpacing = vSpacing,
                scrollable = true,
                onEvent = onEvent,
                modifier = Modifier.fillMaxWidth()
            )
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
