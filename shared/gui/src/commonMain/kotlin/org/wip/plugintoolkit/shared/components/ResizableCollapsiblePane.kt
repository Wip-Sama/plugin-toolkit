package org.wip.plugintoolkit.shared.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
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
private const val RUBBER_BAND_MAX_DP = 14f
private const val RUBBER_BAND_DAMPING_DISTANCE_DP = 120f
private const val FLING_VELOCITY_THRESHOLD_DP_PER_SEC = 700f
private const val FLING_COLLAPSE_PROXIMITY_DP = 28f
private const val FLING_EXPAND_MIN_PULL_DP = 8f
private const val MAX_SETTLE_VELOCITY_DP_PER_SEC = 2400f

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
 *
 * [isExpanded] represents the committed open/closed state; during an active drag it is never
 * flipped mid-gesture so the handle never teleports away from the user's cursor. Instead,
 * [rawCursorSizeDp] tracks the true cursor distance from the docked wall and [ResizablePaneDragPhysics.onDragEnd]
 * commits collapse/expand cleanly on pointer release.
 */
data class ResizablePaneDragSnapshot(
    val isExpanded: Boolean,
    val visibleSizeDp: Float,
    val lastExpandedSizeDp: Float,
    val overDragCollapseDp: Float = 0f,
    val dragOpenProgressDp: Float = 0f,
    val rawCursorSizeDp: Float = visibleSizeDp
) {
    /** Normalized `[0f..1f]` progress through the Stage-1 resistance zone toward Stage-2 collapse. */
    fun collapseIntentProgress(collapseThresholdDp: Float): Float =
        if (collapseThresholdDp <= 0f) 0f else (overDragCollapseDp / collapseThresholdDp).coerceIn(0f, 1f)

    /** Normalized `[0f..1f]` progress pulling a collapsed pane away from its docked edge. */
    fun expandIntentProgress(expandThresholdDp: Float): Float =
        if (expandThresholdDp <= 0f) 0f else (dragOpenProgressDp / expandThresholdDp).coerceIn(0f, 1f)
}

/**
 * Pure, deterministic, cursor-locked drag & commit-on-release physics reducer shared across all
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
     * Initializes [ResizablePaneDragSnapshot.rawCursorSizeDp] at the start of a pointer drag gesture.
     */
    fun onDragStart(state: ResizablePaneDragSnapshot): ResizablePaneDragSnapshot = state.copy(
        rawCursorSizeDp = if (state.isExpanded) state.visibleSizeDp else 0f,
        overDragCollapseDp = 0f,
        dragOpenProgressDp = 0f
    )

    /**
     * Advances drag state by [expandDeltaDp] (positive = away from wall, negative = toward wall)
     * without flipping [ResizablePaneDragSnapshot.isExpanded] mid-gesture:
     *
     * - **Started Expanded**:
     *   - Within `[minSizeDp, maxSizeDp]`, [ResizablePaneDragSnapshot.visibleSizeDp] tracks the
     *     cursor 1:1.
     *   - Below `minSizeDp`, the pane resists shrinking (subtle rubber-band pull while inner content
     *     remains clamped at `minSizeDp` with zero squishing) and accumulates `overDragCollapseDp`.
     *     Reversing direction back past `minSizeDp` smoothly resumes 1:1 resizing.
     *   - Above `maxSizeDp`, applies gentle elastic rubber-band stretch that springs back on release.
     * - **Started Collapsed**:
     *   - Unveils the clipped pane 1:1 directly behind the cursor from `0.dp` onward so the handle
     *     stays locked to the pointer. Reversing back toward `0.dp` smoothly closes the preview.
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
        val nextRaw = state.rawCursorSizeDp + expandDeltaDp

        if (state.isExpanded) {
            if (nextRaw < minSizeDp) {
                if (!collapsible) {
                    return state.copy(
                        visibleSizeDp = minSizeDp,
                        lastExpandedSizeDp = minSizeDp,
                        overDragCollapseDp = 0f,
                        rawCursorSizeDp = minSizeDp
                    )
                }
                val overDrag = minSizeDp - nextRaw
                val rubberPull = rubberBand(overDrag, RUBBER_BAND_MAX_DP)
                return state.copy(
                    visibleSizeDp = (minSizeDp - rubberPull).coerceAtLeast(0f),
                    overDragCollapseDp = overDrag,
                    rawCursorSizeDp = nextRaw
                )
            }

            if (nextRaw > safeMax) {
                val stretch = rubberBand(nextRaw - safeMax, RUBBER_BAND_MAX_DP)
                return state.copy(
                    visibleSizeDp = safeMax + stretch,
                    lastExpandedSizeDp = safeMax,
                    overDragCollapseDp = 0f,
                    rawCursorSizeDp = nextRaw
                )
            }

            return state.copy(
                visibleSizeDp = nextRaw,
                lastExpandedSizeDp = nextRaw,
                overDragCollapseDp = 0f,
                rawCursorSizeDp = nextRaw
            )
        } else {
            val openDistance = nextRaw.coerceAtLeast(0f)
            val previewSize = if (openDistance <= safeMax) {
                openDistance
            } else {
                safeMax + rubberBand(openDistance - safeMax, RUBBER_BAND_MAX_DP)
            }
            return state.copy(
                isExpanded = false,
                visibleSizeDp = previewSize,
                dragOpenProgressDp = openDistance,
                overDragCollapseDp = 0f,
                rawCursorSizeDp = nextRaw
            )
        }
    }

    /**
     * Commits collapse/expand on pointer release based on over-drag/open thresholds or fling
     * velocity, and applies optional magnetic snapping near [defaultSizeDp].
     */
    fun onDragEnd(
        state: ResizablePaneDragSnapshot,
        defaultSizeDp: Float,
        minSizeDp: Float,
        maxSizeDp: Float,
        magneticSnapDp: Float,
        collapseThresholdDp: Float = 64f,
        expandThresholdDp: Float = 40f,
        collapsible: Boolean = true,
        releaseExpandVelocityDpPerSec: Float = 0f
    ): ResizablePaneDragSnapshot {
        val safeMax = maxOf(minSizeDp, maxSizeDp)
        val clampedDefault = defaultSizeDp.coerceIn(minSizeDp, safeMax)

        if (state.isExpanded) {
            val isInwardFlingNearMin = state.rawCursorSizeDp <= (minSizeDp + FLING_COLLAPSE_PROXIMITY_DP) &&
                releaseExpandVelocityDpPerSec <= -FLING_VELOCITY_THRESHOLD_DP_PER_SEC
            val shouldCollapse = collapsible && (state.overDragCollapseDp >= collapseThresholdDp || isInwardFlingNearMin)
            if (shouldCollapse) {
                return state.copy(
                    isExpanded = false,
                    visibleSizeDp = 0f,
                    lastExpandedSizeDp = state.lastExpandedSizeDp.coerceIn(minSizeDp, safeMax),
                    overDragCollapseDp = 0f,
                    dragOpenProgressDp = 0f,
                    rawCursorSizeDp = 0f
                )
            }

            val clamped = state.rawCursorSizeDp.coerceIn(minSizeDp, safeMax)
            val settled = if (magneticSnapDp > 0f && abs(clamped - clampedDefault) <= magneticSnapDp) {
                clampedDefault
            } else {
                clamped
            }
            return state.copy(
                isExpanded = true,
                visibleSizeDp = settled,
                lastExpandedSizeDp = settled,
                overDragCollapseDp = 0f,
                dragOpenProgressDp = 0f,
                rawCursorSizeDp = settled
            )
        } else {
            val isOutwardFling = state.dragOpenProgressDp >= FLING_EXPAND_MIN_PULL_DP &&
                releaseExpandVelocityDpPerSec >= FLING_VELOCITY_THRESHOLD_DP_PER_SEC
            val shouldExpand = state.dragOpenProgressDp >= expandThresholdDp || isOutwardFling
            if (!shouldExpand) {
                return state.copy(
                    isExpanded = false,
                    visibleSizeDp = 0f,
                    overDragCollapseDp = 0f,
                    dragOpenProgressDp = 0f,
                    rawCursorSizeDp = 0f
                )
            }

            val targetSize = if (state.rawCursorSizeDp >= minSizeDp) {
                val clamped = state.rawCursorSizeDp.coerceIn(minSizeDp, safeMax)
                if (magneticSnapDp > 0f && abs(clamped - clampedDefault) <= magneticSnapDp) {
                    clampedDefault
                } else {
                    clamped
                }
            } else {
                state.lastExpandedSizeDp.coerceIn(minSizeDp, safeMax)
            }
            return state.copy(
                isExpanded = true,
                visibleSizeDp = targetSize,
                lastExpandedSizeDp = targetSize,
                overDragCollapseDp = 0f,
                dragOpenProgressDp = 0f,
                rawCursorSizeDp = targetSize
            )
        }
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
                dragOpenProgressDp = 0f,
                rawCursorSizeDp = 0f
            )
        } else {
            val restored = state.lastExpandedSizeDp.coerceIn(minSizeDp, safeMax)
            state.copy(
                isExpanded = true,
                visibleSizeDp = restored,
                lastExpandedSizeDp = restored,
                overDragCollapseDp = 0f,
                dragOpenProgressDp = 0f,
                rawCursorSizeDp = restored
            )
        }
    }

    private fun rubberBand(overDragDp: Float, maxPullDp: Float): Float {
        if (overDragDp <= 0f) return 0f
        return maxPullDp * (overDragDp / (overDragDp + RUBBER_BAND_DAMPING_DISTANCE_DP))
    }
}

/**
 * Unified, spring-driven resizable and collapsible two-pane host supporting all four edges
 * ([PaneAttachmentEdge.Start], [PaneAttachmentEdge.End], [PaneAttachmentEdge.Top], and
 * [PaneAttachmentEdge.Bottom]).
 *
 * Features:
 * - Root-space 1:1 pointer tracking during active drag so the handle never detaches from the cursor.
 * - Commit-on-release collapse/expand with elastic rubber-band resistance at bounds and
 *   velocity-preserving Material 3 Expressive spring settle (`defaultSpatialSpec()`).
 * - Minimum-size clamping on both the collapsible pane and the companion main pane (zero squish).
 * - Unified click-to-toggle (open <-> closed) on every horizontal and vertical handle.
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
    maxPaneFraction: Float = 0.55f,
    collapsible: Boolean = true,
    clickToCollapseWhenExpanded: Boolean = true,
    handleThickness: Dp = ToolkitTheme.dimensions.paneDividerHandleThickness,
    collapseDragThreshold: Dp = if (edge.isHorizontal) {
        ToolkitTheme.dimensions.paneCollapseDragThreshold
    } else {
        ToolkitTheme.dimensions.paneVerticalCollapseDragThreshold
    },
    expandDragThreshold: Dp = ToolkitTheme.dimensions.paneExpandDragThreshold,
    magneticSnapThreshold: Dp = ToolkitTheme.dimensions.paneMagneticSnapThreshold,
    handleTooltipCollapse: String = "",
    handleTooltipExpand: String = "",
    handleTestTag: String? = null,
    paneSizeRequestKey: Int = 0,
    onLivePaneSizeChanged: ((Dp) -> Unit)? = null,
    mainContent: @Composable BoxScope.() -> Unit,
    paneContent: @Composable BoxScope.() -> Unit
) {
    val motion = MaterialTheme.motionScheme
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

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
                    dragOpenProgressDp = 0f,
                    rawCursorSizeDp = nextSize
                )
                animatedSizeDp.animateTo(
                    targetValue = nextSize,
                    animationSpec = motion.defaultSpatialSpec()
                )
            }
        }

        // Sync external preferred/default width updates (e.g., sequel split mode toggle)
        var lastAppliedDefaultDp by remember { mutableFloatStateOf(defaultPaneSize.value) }
        var lastAppliedRequestKey by remember { mutableStateOf(paneSizeRequestKey) }
        LaunchedEffect(defaultPaneSize, paneSizeRequestKey, effectiveMinDp, effectiveMaxDp) {
            if (!isDragging) {
                val targetDefaultChanged = abs(defaultPaneSize.value - lastAppliedDefaultDp) > 0.5f
                val requestKeyChanged = paneSizeRequestKey != lastAppliedRequestKey
                if (targetDefaultChanged || requestKeyChanged) {
                    lastAppliedDefaultDp = defaultPaneSize.value
                    lastAppliedRequestKey = paneSizeRequestKey
                    val nextTarget = defaultPaneSize.value.coerceIn(effectiveMinDp, effectiveMaxDp)
                    dragSnapshot = dragSnapshot.copy(
                        lastExpandedSizeDp = nextTarget,
                        visibleSizeDp = if (dragSnapshot.isExpanded) nextTarget else 0f,
                        rawCursorSizeDp = if (dragSnapshot.isExpanded) nextTarget else 0f
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
                            lastExpandedSizeDp = reclamped,
                            rawCursorSizeDp = reclamped
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
                                animationSpec = motion.defaultSpatialSpec()
                            )
                        }
                    }
                },
                onDragStart = {
                    isDragging = true
                    dragSnapshot = ResizablePaneDragPhysics.onDragStart(dragSnapshot)
                },
                onDragDelta = { deltaX, deltaY ->
                    val expandDeltaPx = ResizablePaneDragPhysics.toExpandDelta(edge, deltaX, deltaY)
                    val expandDeltaDp = with(density) { expandDeltaPx.toDp().value }
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
                    if (next.isExpanded) {
                        latestOnLiveSize?.invoke(next.visibleSizeDp.dp)
                    }
                    scope.launch {
                        animatedSizeDp.snapTo(next.visibleSizeDp)
                    }
                },
                onDragEnd = { velocityX, velocityY ->
                    isDragging = false
                    val prevExpanded = dragSnapshot.isExpanded
                    val expandVelocityPx = ResizablePaneDragPhysics.toExpandDelta(edge, velocityX, velocityY)
                    val expandVelocityDp = with(density) { expandVelocityPx.toDp().value }
                        .coerceIn(-MAX_SETTLE_VELOCITY_DP_PER_SEC, MAX_SETTLE_VELOCITY_DP_PER_SEC)
                    val settled = ResizablePaneDragPhysics.onDragEnd(
                        state = dragSnapshot,
                        defaultSizeDp = latestDefaultDp,
                        minSizeDp = latestMinDp,
                        maxSizeDp = latestMaxDp,
                        magneticSnapDp = magneticSnapThreshold.value,
                        collapseThresholdDp = collapseDragThreshold.value,
                        expandThresholdDp = expandDragThreshold.value,
                        collapsible = collapsible,
                        releaseExpandVelocityDpPerSec = expandVelocityDp
                    )
                    dragSnapshot = settled
                    if (settled.isExpanded != prevExpanded) {
                        latestOnExpandedChange(settled.isExpanded)
                    }
                    if (settled.isExpanded) {
                        latestOnLiveSize?.invoke(settled.visibleSizeDp.dp)
                    }
                    scope.launch {
                        animatedSizeDp.animateTo(
                            targetValue = settled.visibleSizeDp,
                            animationSpec = motion.defaultSpatialSpec(),
                            initialVelocity = expandVelocityDp
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
 * Shared desktop drag/click handle supporting both horizontal and vertical resize gestures in
 * root/window coordinates (immune to handle movement), velocity tracking for expressive spring
 * settles, click-to-toggle without competing clickable modifiers, and visual collapse/expand cues.
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
    onDragEnd: (velocityX: Float, velocityY: Float) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val motion = MaterialTheme.motionScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val viewConfiguration = LocalViewConfiguration.current
    var handleCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

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
                .onGloballyPositioned { handleCoordinates = it }
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
                    val toRoot: (Offset) -> Offset = { localPos ->
                        val coords = handleCoordinates
                        if (coords != null && coords.isAttached) {
                            coords.localToRoot(localPos)
                        } else {
                            localPos
                        }
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downRoot = toRoot(down.position)
                        var lastRoot = downRoot
                        var dragging = false
                        val pointerId = down.id
                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, downRoot)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            val currentRoot = toRoot(change.position)
                            if (!change.pressed) {
                                if (dragging) {
                                    velocityTracker.addPosition(change.uptimeMillis, currentRoot)
                                    val velocity = velocityTracker.calculateVelocity()
                                    latestOnDragEnd(velocity.x, velocity.y)
                                } else {
                                    latestOnClick()
                                }
                                break
                            }
                            val rootDelta = currentRoot - lastRoot
                            if (rootDelta.x != 0f || rootDelta.y != 0f) {
                                lastRoot = currentRoot
                                velocityTracker.addPosition(change.uptimeMillis, currentRoot)
                                val totalFromDown = currentRoot - downRoot
                                if (!dragging && hypot(totalFromDown.x, totalFromDown.y) >= touchSlop) {
                                    dragging = true
                                    latestOnDragStart()
                                    change.consume()
                                    latestOnDragDelta(totalFromDown.x, totalFromDown.y)
                                } else if (dragging) {
                                    change.consume()
                                    latestOnDragDelta(rootDelta.x, rootDelta.y)
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
