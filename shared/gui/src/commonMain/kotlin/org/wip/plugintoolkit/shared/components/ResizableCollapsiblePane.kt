package org.wip.plugintoolkit.shared.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.hypot
import kotlinx.coroutines.launch
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformUtils

private const val COLLAPSED_VISIBILITY_THRESHOLD_DP = 0.5f
private const val HANDLE_HOVER_LENGTH_MULTIPLIER = 1.4f
private const val HANDLE_COLLAPSE_INTENT_SHRINK_FACTOR = 0.35f
private const val HANDLE_HOVER_BG_ALPHA = 0.55f

/**
 * Edge of the host workspace to which a collapsible/resizable pane is docked.
 */
enum class PaneAttachmentEdge {
    Start,
    End,
    Top,
    Bottom;

    val isHorizontal: Boolean
        get() = this == Start || this == End
}

/**
 * Immutable snapshot representing the drag/resistance state of a resizable collapsible pane.
 */
data class ResizablePaneDragSnapshot(
    val isExpanded: Boolean,
    val visibleSizeDp: Float,
    val lastExpandedSizeDp: Float,
    val overDragCollapseDp: Float = 0f,
    val dragOpenProgressDp: Float = 0f
) {
    /** Normalized `[0f..1f]` progress through the Stage-1 resistance zone toward Stage-2 collapse. */
    fun collapseIntentProgress(collapseThresholdDp: Float): Float =
        if (collapseThresholdDp <= 0f) 0f else (overDragCollapseDp / collapseThresholdDp).coerceIn(0f, 1f)

    /** Normalized `[0f..1f]` progress pulling a collapsed pane away from its docked edge. */
    fun expandIntentProgress(expandThresholdDp: Float): Float =
        if (expandThresholdDp <= 0f) 0f else (dragOpenProgressDp / expandThresholdDp).coerceIn(0f, 1f)
}

/**
 * Pure, deterministic drag & two-stage collapse/expand physics reducer shared across all
 * horizontal and vertical resizable panes in the application.
 */
object ResizablePaneDragPhysics {

    /**
     * Converts raw pointer `(deltaX, deltaY)` into a signed outward expansion delta where positive
     * values grow the pane away from [edge] and negative values shrink/collapse it toward [edge].
     */
    fun toExpandDelta(edge: PaneAttachmentEdge, deltaX: Float, deltaY: Float): Float = when (edge) {
        PaneAttachmentEdge.Start -> deltaX
        PaneAttachmentEdge.End -> -deltaX
        PaneAttachmentEdge.Top -> deltaY
        PaneAttachmentEdge.Bottom -> -deltaY
    }

    /**
     * Computes the maximum allowable size for the resizable pane while guaranteeing both the
     * collapsible pane and the companion main pane stay above their minimum thresholds.
     */
    fun computeMaxExpandedSizeDp(
        totalAvailableDp: Float,
        minPaneSizeDp: Float,
        minMainPaneSizeDp: Float,
        handleThicknessDp: Float,
        maxPaneFraction: Float
    ): Float {
        val byMainPane = (totalAvailableDp - minMainPaneSizeDp - handleThicknessDp).coerceAtLeast(minPaneSizeDp)
        val byFraction = (totalAvailableDp * maxPaneFraction).coerceAtLeast(minPaneSizeDp)
        return minOf(byMainPane, byFraction).coerceAtLeast(minPaneSizeDp)
    }

    /**
     * Advances drag state by [expandDeltaDp] (positive = away from wall, negative = toward wall).
     *
     * Implements two-stage drag-to-collapse:
     * 1. **Stop / Resistance Hold:** Dragging past [minSizeDp] clamps [ResizablePaneDragSnapshot.visibleSizeDp]
     *    at [minSizeDp] (zero squishing) while accumulating [ResizablePaneDragSnapshot.overDragCollapseDp].
     * 2. **Collapse Detent:** Crossing [collapseThresholdDp] triggers collapse (`isExpanded = false`).
     *
     * Implements continuous drag-to-open:
     * - Pulling a collapsed pane past [expandThresholdDp] transitions to `isExpanded = true` so
     *   continued pointer movement in the same gesture seamlessly resizes the pane.
     */
    fun onDragDelta(
        state: ResizablePaneDragSnapshot,
        expandDeltaDp: Float,
        minSizeDp: Float,
        maxSizeDp: Float,
        collapseThresholdDp: Float,
        expandThresholdDp: Float,
        collapsible: Boolean
    ): ResizablePaneDragSnapshot {
        val safeMax = maxOf(minSizeDp, maxSizeDp)
        if (state.isExpanded) {
            if (state.overDragCollapseDp > 0f) {
                val nextOverDrag = state.overDragCollapseDp - expandDeltaDp
                return when {
                    nextOverDrag <= 0f -> {
                        val grown = (minSizeDp + (-nextOverDrag)).coerceIn(minSizeDp, safeMax)
                        state.copy(
                            visibleSizeDp = grown,
                            lastExpandedSizeDp = grown,
                            overDragCollapseDp = 0f
                        )
                    }
                    collapsible && nextOverDrag >= collapseThresholdDp -> {
                        state.copy(
                            isExpanded = false,
                            visibleSizeDp = 0f,
                            lastExpandedSizeDp = state.lastExpandedSizeDp.coerceIn(minSizeDp, safeMax),
                            overDragCollapseDp = 0f,
                            dragOpenProgressDp = 0f
                        )
                    }
                    else -> {
                        state.copy(
                            visibleSizeDp = minSizeDp,
                            overDragCollapseDp = nextOverDrag
                        )
                    }
                }
            }

            val candidate = state.visibleSizeDp + expandDeltaDp
            if (candidate < minSizeDp) {
                if (!collapsible) {
                    return state.copy(
                        visibleSizeDp = minSizeDp,
                        lastExpandedSizeDp = minSizeDp,
                        overDragCollapseDp = 0f
                    )
                }
                val overDrag = minSizeDp - candidate
                return if (overDrag >= collapseThresholdDp) {
                    state.copy(
                        isExpanded = false,
                        visibleSizeDp = 0f,
                        lastExpandedSizeDp = state.lastExpandedSizeDp.coerceIn(minSizeDp, safeMax),
                        overDragCollapseDp = 0f,
                        dragOpenProgressDp = 0f
                    )
                } else {
                    state.copy(
                        visibleSizeDp = minSizeDp,
                        overDragCollapseDp = overDrag
                    )
                }
            }

            val clamped = candidate.coerceIn(minSizeDp, safeMax)
            return state.copy(
                visibleSizeDp = clamped,
                lastExpandedSizeDp = clamped,
                overDragCollapseDp = 0f
            )
        } else {
            val nextOpenProgress = (state.dragOpenProgressDp + expandDeltaDp).coerceAtLeast(0f)
            return if (nextOpenProgress >= expandThresholdDp) {
                val initialOpenSize = maxOf(minSizeDp, nextOpenProgress).coerceIn(minSizeDp, safeMax)
                state.copy(
                    isExpanded = true,
                    visibleSizeDp = initialOpenSize,
                    lastExpandedSizeDp = initialOpenSize,
                    overDragCollapseDp = 0f,
                    dragOpenProgressDp = 0f
                )
            } else {
                state.copy(
                    isExpanded = false,
                    visibleSizeDp = 0f,
                    dragOpenProgressDp = nextOpenProgress
                )
            }
        }
    }

    /**
     * Finalizes drag state on pointer release, clearing transient over-drag/open progress and
     * applying optional magnetic snapping near [defaultSizeDp].
     */
    fun onDragEnd(
        state: ResizablePaneDragSnapshot,
        defaultSizeDp: Float,
        minSizeDp: Float,
        maxSizeDp: Float,
        magneticSnapDp: Float
    ): ResizablePaneDragSnapshot {
        val safeMax = maxOf(minSizeDp, maxSizeDp)
        if (!state.isExpanded) {
            return state.copy(
                visibleSizeDp = 0f,
                overDragCollapseDp = 0f,
                dragOpenProgressDp = 0f
            )
        }
        val clamped = state.visibleSizeDp.coerceIn(minSizeDp, safeMax)
        val clampedDefault = defaultSizeDp.coerceIn(minSizeDp, safeMax)
        val settled = if (magneticSnapDp > 0f && abs(clamped - clampedDefault) <= magneticSnapDp) {
            clampedDefault
        } else {
            clamped
        }
        return state.copy(
            visibleSizeDp = settled,
            lastExpandedSizeDp = settled,
            overDragCollapseDp = 0f,
            dragOpenProgressDp = 0f
        )
    }

    /**
     * Toggles between expanded (restoring [ResizablePaneDragSnapshot.lastExpandedSizeDp]) and
     * collapsed (`0f`) states.
     */
    fun toggle(
        state: ResizablePaneDragSnapshot,
        minSizeDp: Float,
        maxSizeDp: Float
    ): ResizablePaneDragSnapshot {
        val safeMax = maxOf(minSizeDp, maxSizeDp)
        return if (state.isExpanded) {
            state.copy(
                isExpanded = false,
                visibleSizeDp = 0f,
                overDragCollapseDp = 0f,
                dragOpenProgressDp = 0f
            )
        } else {
            val restored = state.lastExpandedSizeDp.coerceIn(minSizeDp, safeMax)
            state.copy(
                isExpanded = true,
                visibleSizeDp = restored,
                lastExpandedSizeDp = restored,
                overDragCollapseDp = 0f,
                dragOpenProgressDp = 0f
            )
        }
    }
}

/**
 * Unified, spring-driven resizable and collapsible two-pane host supporting all four edges
 * ([PaneAttachmentEdge.Start], [PaneAttachmentEdge.End], [PaneAttachmentEdge.Top], and
 * [PaneAttachmentEdge.Bottom]).
 *
 * Features:
 * - Instant 1:1 pointer tracking during active drag and Material 3 Expressive spring settle
 *   (`defaultSpatialSpec()`) on release or programmatic toggle.
 * - Minimum-size clamping on both the collapsible pane and the companion main pane (zero squish).
 * - Two-stage drag-to-collapse (hold at [minPaneSize], animate shut after [collapseDragThreshold]).
 * - Continuous drag-to-open from the docked edge handle, plus click-to-expand.
 * - Single persistent handle slot so gestures are never interrupted when crossing collapsed/expanded.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolkitResizableCollapsiblePane(
    edge: PaneAttachmentEdge,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    defaultPaneSize: Dp,
    minPaneSize: Dp,
    minMainPaneSize: Dp,
    modifier: Modifier = Modifier,
    maxPaneFraction: Float = 0.58f,
    collapsible: Boolean = true,
    clickToCollapseWhenExpanded: Boolean = !edge.isHorizontal,
    handleTooltipCollapse: String = "",
    handleTooltipExpand: String = "",
    collapseDragThreshold: Dp = if (edge.isHorizontal) {
        ToolkitTheme.dimensions.paneCollapseDragThreshold
    } else {
        ToolkitTheme.dimensions.paneVerticalCollapseDragThreshold
    },
    expandDragThreshold: Dp = ToolkitTheme.dimensions.paneExpandDragThreshold,
    magneticSnapThreshold: Dp = ToolkitTheme.dimensions.paneMagneticSnapThreshold,
    handleTestTag: String? = null,
    onLivePaneSizeChanged: ((Dp) -> Unit)? = null,
    mainContent: @Composable BoxScope.() -> Unit,
    paneContent: @Composable BoxScope.() -> Unit
) {
    val motion = MaterialTheme.motionScheme
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val handleThickness = ToolkitTheme.dimensions.paneDividerHandleThickness
    val collapseAnimSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
    }

    var dragSnapshot by remember {
        mutableStateOf(
            ResizablePaneDragSnapshot(
                isExpanded = isExpanded,
                visibleSizeDp = if (isExpanded) defaultPaneSize.value else 0f,
                lastExpandedSizeDp = defaultPaneSize.value
            )
        )
    }
    var isDragging by remember { mutableStateOf(false) }
    val animatedSizeDp = remember {
        Animatable(if (isExpanded) defaultPaneSize.value else 0f)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val availableTotalDp = if (edge.isHorizontal) maxWidth.value else maxHeight.value
        val effectiveMinDp = minPaneSize.value.coerceAtMost(
            (availableTotalDp - handleThickness.value).coerceAtLeast(0f)
        )
        val effectiveMaxDp = ResizablePaneDragPhysics.computeMaxExpandedSizeDp(
            totalAvailableDp = availableTotalDp,
            minPaneSizeDp = effectiveMinDp,
            minMainPaneSizeDp = minMainPaneSize.value,
            handleThicknessDp = handleThickness.value,
            maxPaneFraction = maxPaneFraction
        )
        val clampedDefaultDp = defaultPaneSize.value.coerceIn(effectiveMinDp, effectiveMaxDp)

        // Sync external expanded state changes (e.g., programmatic toggle or AppBar button)
        LaunchedEffect(isExpanded) {
            if (!isDragging && dragSnapshot.isExpanded != isExpanded) {
                val nextSize = if (isExpanded) {
                    dragSnapshot.lastExpandedSizeDp.coerceIn(effectiveMinDp, effectiveMaxDp)
                } else {
                    0f
                }
                dragSnapshot = dragSnapshot.copy(
                    isExpanded = isExpanded,
                    visibleSizeDp = nextSize,
                    overDragCollapseDp = 0f,
                    dragOpenProgressDp = 0f
                )
                animatedSizeDp.animateTo(
                    targetValue = nextSize,
                    animationSpec = if (isExpanded) motion.defaultSpatialSpec() else collapseAnimSpec
                )
            }
        }

        // Sync external preferred/default width updates (e.g., sequel split mode toggle)
        var lastAppliedDefaultDp by remember { mutableFloatStateOf(defaultPaneSize.value) }
        LaunchedEffect(defaultPaneSize, effectiveMinDp, effectiveMaxDp) {
            if (!isDragging) {
                val targetDefaultChanged = abs(defaultPaneSize.value - lastAppliedDefaultDp) > 0.5f
                if (targetDefaultChanged) {
                    lastAppliedDefaultDp = defaultPaneSize.value
                    val nextTarget = defaultPaneSize.value.coerceIn(effectiveMinDp, effectiveMaxDp)
                    dragSnapshot = dragSnapshot.copy(
                        lastExpandedSizeDp = nextTarget,
                        visibleSizeDp = if (dragSnapshot.isExpanded) nextTarget else 0f
                    )
                    if (dragSnapshot.isExpanded) {
                        animatedSizeDp.animateTo(
                            targetValue = nextTarget,
                            animationSpec = motion.defaultSpatialSpec()
                        )
                    }
                } else if (dragSnapshot.isExpanded) {
                    val reclamped = dragSnapshot.visibleSizeDp.coerceIn(effectiveMinDp, effectiveMaxDp)
                    if (abs(reclamped - dragSnapshot.visibleSizeDp) > 0.5f) {
                        dragSnapshot = dragSnapshot.copy(
                            visibleSizeDp = reclamped,
                            lastExpandedSizeDp = reclamped
                        )
                        animatedSizeDp.snapTo(reclamped)
                    }
                }
            }
        }

        val renderedSizeDp = animatedSizeDp.value.coerceAtLeast(0f).dp
        val latestOnLiveSize by rememberUpdatedState(onLivePaneSizeChanged)

        val latestOnExpandedChange by rememberUpdatedState(onExpandedChange)
        val latestMinDp by rememberUpdatedState(effectiveMinDp)
        val latestMaxDp by rememberUpdatedState(effectiveMaxDp)
        val latestDefaultDp by rememberUpdatedState(clampedDefaultDp)

        val handleSlot: @Composable () -> Unit = {
            ToolkitPaneResizeHandle(
                edge = edge,
                isExpanded = dragSnapshot.isExpanded,
                isDragging = isDragging,
                collapseIntentProgress = dragSnapshot.collapseIntentProgress(collapseDragThreshold.value),
                expandIntentProgress = dragSnapshot.expandIntentProgress(expandDragThreshold.value),
                tooltip = if (dragSnapshot.isExpanded) handleTooltipCollapse else handleTooltipExpand,
                testTag = handleTestTag,
                onClick = {
                    val shouldToggle = !dragSnapshot.isExpanded || (collapsible && clickToCollapseWhenExpanded)
                    if (shouldToggle) {
                        val next = ResizablePaneDragPhysics.toggle(
                            state = dragSnapshot,
                            minSizeDp = latestMinDp,
                            maxSizeDp = latestMaxDp
                        )
                        dragSnapshot = next
                        latestOnExpandedChange(next.isExpanded)
                        scope.launch {
                            animatedSizeDp.animateTo(
                                targetValue = next.visibleSizeDp,
                                animationSpec = if (next.isExpanded) motion.defaultSpatialSpec() else collapseAnimSpec
                            )
                        }
                    }
                },
                onDragStart = {
                    isDragging = true
                },
                onDragDelta = { deltaX, deltaY ->
                    val expandDeltaPx = ResizablePaneDragPhysics.toExpandDelta(edge, deltaX, deltaY)
                    val expandDeltaDp = with(density) { expandDeltaPx.toDp().value }
                    val prevExpanded = dragSnapshot.isExpanded
                    val next = ResizablePaneDragPhysics.onDragDelta(
                        state = dragSnapshot,
                        expandDeltaDp = expandDeltaDp,
                        minSizeDp = latestMinDp,
                        maxSizeDp = latestMaxDp,
                        collapseThresholdDp = collapseDragThreshold.value,
                        expandThresholdDp = expandDragThreshold.value,
                        collapsible = collapsible
                    )
                    dragSnapshot = next
                    if (next.isExpanded != prevExpanded) {
                        latestOnExpandedChange(next.isExpanded)
                        scope.launch {
                            animatedSizeDp.animateTo(
                                targetValue = next.visibleSizeDp,
                                animationSpec = if (next.isExpanded) motion.defaultSpatialSpec() else collapseAnimSpec
                            )
                        }
                    } else if (next.isExpanded) {
                        latestOnLiveSize?.invoke(next.visibleSizeDp.dp)
                        scope.launch {
                            animatedSizeDp.snapTo(next.visibleSizeDp)
                        }
                    }
                },
                onDragEnd = {
                    isDragging = false
                    val settled = ResizablePaneDragPhysics.onDragEnd(
                        state = dragSnapshot,
                        defaultSizeDp = latestDefaultDp,
                        minSizeDp = latestMinDp,
                        maxSizeDp = latestMaxDp,
                        magneticSnapDp = magneticSnapThreshold.value
                    )
                    dragSnapshot = settled
                    if (settled.isExpanded) {
                        latestOnLiveSize?.invoke(settled.visibleSizeDp.dp)
                    }
                    scope.launch {
                        animatedSizeDp.animateTo(
                            targetValue = settled.visibleSizeDp,
                            animationSpec = if (settled.isExpanded) motion.defaultSpatialSpec() else collapseAnimSpec
                        )
                    }
                }
            )
        }

        val collapsiblePaneSlot: @Composable () -> Unit = {
            if (renderedSizeDp.value > COLLAPSED_VISIBILITY_THRESHOLD_DP) {
                val safeContentSize = renderedSizeDp.coerceAtLeast(effectiveMinDp.dp)
                if (edge.isHorizontal) {
                    Box(
                        modifier = Modifier
                            .width(renderedSizeDp)
                            .fillMaxHeight()
                            .clipToBounds(),
                        contentAlignment = if (edge == PaneAttachmentEdge.Start) {
                            Alignment.CenterEnd
                        } else {
                            Alignment.CenterStart
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .requiredWidth(safeContentSize)
                                .fillMaxHeight(),
                            content = paneContent
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(renderedSizeDp)
                            .clipToBounds(),
                        contentAlignment = if (edge == PaneAttachmentEdge.Top) {
                            Alignment.BottomCenter
                        } else {
                            Alignment.TopCenter
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .requiredHeight(safeContentSize),
                            content = paneContent
                        )
                    }
                }
            }
        }

        if (edge.isHorizontal) {
            Row(modifier = Modifier.fillMaxSize()) {
                if (edge == PaneAttachmentEdge.Start) {
                    collapsiblePaneSlot()
                    handleSlot()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        content = mainContent
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        content = mainContent
                    )
                    handleSlot()
                    collapsiblePaneSlot()
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                if (edge == PaneAttachmentEdge.Top) {
                    collapsiblePaneSlot()
                    handleSlot()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        content = mainContent
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        content = mainContent
                    )
                    handleSlot()
                    collapsiblePaneSlot()
                }
            }
        }
    }
}

/**
 * Shared desktop drag/click handle supporting both horizontal and vertical resize gestures,
 * click-to-toggle without competing clickable modifiers, and visual collapse/expand intent cues.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ToolkitPaneResizeHandle(
    edge: PaneAttachmentEdge,
    isExpanded: Boolean,
    isDragging: Boolean,
    collapseIntentProgress: Float,
    expandIntentProgress: Float,
    tooltip: String,
    onClick: () -> Unit,
    onDragStart: () -> Unit,
    onDragDelta: (deltaX: Float, deltaY: Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val motion = MaterialTheme.motionScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val viewConfiguration = LocalViewConfiguration.current

    val latestOnClick by rememberUpdatedState(onClick)
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDragDelta by rememberUpdatedState(onDragDelta)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)

    val pointerIcon = if (edge.isHorizontal) {
        PlatformUtils.horizontalResizePointerIcon()
    } else {
        PlatformUtils.verticalResizePointerIcon()
    }

    val tooltipPosition = when (edge) {
        PaneAttachmentEdge.Start -> ToolkitTooltipPosition.Right
        PaneAttachmentEdge.End -> ToolkitTooltipPosition.Left
        PaneAttachmentEdge.Top -> ToolkitTooltipPosition.Below
        PaneAttachmentEdge.Bottom -> ToolkitTooltipPosition.Above
    }

    val basePillColor = when {
        collapseIntentProgress > 0f -> lerp(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.error,
            collapseIntentProgress
        )
        expandIntentProgress > 0f || isDragging || isHovered -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val pillColor by animateColorAsState(
        targetValue = basePillColor,
        animationSpec = motion.fastEffectsSpec(),
        label = "paneHandlePillColor"
    )
    val trackBgColor by animateColorAsState(
        targetValue = if (isHovered || isDragging) {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = HANDLE_HOVER_BG_ALPHA)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = ToolkitTheme.opacity.transparent)
        },
        animationSpec = motion.fastEffectsSpec(),
        label = "paneHandleTrackBg"
    )

    val baseLength = ToolkitTheme.dimensions.sduiSplitHandlePillLength
    val pillLength = when {
        collapseIntentProgress > 0f -> baseLength * (1f - collapseIntentProgress * HANDLE_COLLAPSE_INTENT_SHRINK_FACTOR)
        isHovered || isDragging || expandIntentProgress > 0f -> baseLength * HANDLE_HOVER_LENGTH_MULTIPLIER
        else -> baseLength
    }
    val pillThickness = ToolkitTheme.dimensions.sduiSplitHandlePillThickness
    val handleThickness = ToolkitTheme.dimensions.paneDividerHandleThickness

    val sizeModifier = if (edge.isHorizontal) {
        Modifier
            .fillMaxHeight()
            .width(handleThickness)
    } else {
        Modifier
            .fillMaxWidth()
            .height(handleThickness)
    }

    ToolkitTooltipBox(
        tooltip = tooltip.ifBlank { null },
        position = tooltipPosition,
        modifier = modifier
    ) {
        Box(
            modifier = sizeModifier
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                .clip(ToolkitTheme.shapes.small)
                .background(trackBgColor)
                .hoverable(interactionSource)
                .pointerHoverIcon(pointerIcon)
                .semantics {
                    if (tooltip.isNotBlank()) {
                        contentDescription = tooltip
                    }
                    role = Role.Button
                    onClick(label = tooltip.ifBlank { null }) {
                        latestOnClick()
                        true
                    }
                }
                .pointerInput(edge, viewConfiguration) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalX = 0f
                        var totalY = 0f
                        var dragging = false
                        var pointerId = down.id

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) {
                                if (dragging) {
                                    latestOnDragEnd()
                                } else {
                                    latestOnClick()
                                }
                                break
                            }
                            val delta = change.positionChange()
                            if (delta.x != 0f || delta.y != 0f) {
                                totalX += delta.x
                                totalY += delta.y
                                if (!dragging && hypot(totalX, totalY) >= touchSlop) {
                                    dragging = true
                                    latestOnDragStart()
                                    change.consume()
                                    latestOnDragDelta(totalX, totalY)
                                } else if (dragging) {
                                    change.consume()
                                    latestOnDragDelta(delta.x, delta.y)
                                }
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = (if (edge.isHorizontal) {
                    Modifier.width(pillThickness).height(pillLength)
                } else {
                    Modifier.width(pillLength).height(pillThickness)
                }).background(color = pillColor, shape = CircleShape)
            )
        }
    }
}
