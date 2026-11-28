package org.wip.plugintoolkit.shared.components.sidebar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.shared.components.ToolkitTooltipBox
import org.wip.plugintoolkit.shared.components.ToolkitTooltipPosition
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.sidebar_sequel_collapse
import plugintoolkit.composeapp.generated.resources.sidebar_sequel_expand

/**
 * Controls whether a [ToolkitFloatingSidebar] with a sequel pane expands side-by-side or stays
 * in single-column drill-down mode.
 */
enum class SequelSidebarMode {
    /** Automatically expands side-by-side at Large+ widths (`>= 1200.dp`) and drills down below. */
    Auto,
    /** Forces the sequel pane open side-by-side regardless of window width. */
    ForcedExpanded,
    /** Forces compact single-column drill-down regardless of window width. */
    ForcedCompact
}

/**
 * Resolves whether the sequel sidebar pane should be shown given the user's [mode] and
 * whether the current viewport qualifies as wide (`>= 1200.dp`).
 */
fun resolveSequelExpanded(
    mode: SequelSidebarMode,
    isWideViewport: Boolean
): Boolean = when (mode) {
    SequelSidebarMode.Auto -> isWideViewport
    SequelSidebarMode.ForcedExpanded -> true
    SequelSidebarMode.ForcedCompact -> false
}

/**
 * Resolves whether two-column sequel mode can actually be shown given the workspace's real
 * [maxListPaneWidth] capacity, preventing clipped second-column slivers when the available
 * list-pane space is narrower than [minTwoColumnWidth].
 */
fun resolveAdaptiveSequelExpanded(
    maxListPaneWidth: Dp,
    minTwoColumnWidth: Dp,
    mode: SequelSidebarMode,
    isWideViewport: Boolean,
    hasSelection: Boolean,
    dragOverride: Boolean? = null
): Boolean {
    if (maxListPaneWidth < minTwoColumnWidth) return false
    val target = resolveSequelExpanded(mode, isWideViewport) &&
        (hasSelection || mode == SequelSidebarMode.ForcedExpanded)
    return dragOverride ?: target
}

/**
 * Toggles the user's [SequelSidebarMode] override from the currently visible [isCurrentlyExpanded]
 * state, allowing Auto mode to be overridden either ON or OFF with a single click.
 */
fun toggleSequelSidebarMode(
    isCurrentlyExpanded: Boolean
): SequelSidebarMode = if (isCurrentlyExpanded) {
    SequelSidebarMode.ForcedCompact
} else {
    SequelSidebarMode.ForcedExpanded
}

/**
 * Returns `true` when the active window reaches the Material 3 Large width breakpoint (`>= 1200.dp`).
 */
@Composable
fun isLargeAdaptiveWidth(): Boolean {
    val windowSizeClass = currentWindowAdaptiveInfo(supportLargeAndXLargeWidth = true).windowSizeClass
    return windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND)
}

/**
 * Hysteresis thresholds for switching a floating sidebar between single-column drill-down and
 * two-column sequel split mode when the user manually drags the pane resize handle.
 */
object SequelSidebarHysteresis {
    /** Dragging narrower than this width downgrades two-column mode to single-column drill-down. */
    val COLLAPSE_TO_SINGLE_COLUMN_WIDTH: Dp = 440.dp

    /** Dragging wider than this width restores two-column sequel mode. */
    val EXPAND_TO_TWO_COLUMN_WIDTH: Dp = 500.dp

    /**
     * Resolves the next split-mode state given [isCurrentlySplit] and the live [draggedWidth],
     * applying hysteresis between [COLLAPSE_TO_SINGLE_COLUMN_WIDTH] and
     * [EXPAND_TO_TWO_COLUMN_WIDTH] to prevent oscillation.
     */
    fun resolveFromDraggedWidth(
        isCurrentlySplit: Boolean,
        draggedWidth: Dp
    ): Boolean = when {
        isCurrentlySplit && draggedWidth < COLLAPSE_TO_SINGLE_COLUMN_WIDTH -> false
        !isCurrentlySplit && draggedWidth >= EXPAND_TO_TWO_COLUMN_WIDTH -> true
        else -> isCurrentlySplit
    }
}

/**
 * Unified floating sidebar surface used across secondary workspaces, canvas overlays, and SDUI.
 *
 * Renders a single clipped rounded [Surface] (`surfaceContainer` + `ToolkitTheme.shapes.large`)
 * with:
 * - Optional spring-driven internal navigation via [navigationKey] (`AnimatedContent`)
 * - Optional adjacent sequel sidebar pane docked seamlessly on the right inside the exact same
 *   rounded block, separated by a subtle [VerticalDivider].
 *
 * When hosted inside an outer width-animating pane (such as `ToolkitAdaptiveListDetailWorkspace`
 * or SDUI `RenderSidebar`), set [animateSequelWidth] to `false` so the outer container owns the
 * sole spatial width spring while the sequel pane reveals cleanly via an effects-only crossfade
 * and clipped width, preventing competing springs.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <K> ToolkitFloatingSidebar(
    navigationKey: K,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    sequelContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape: Shape = ToolkitTheme.shapes.large,
    border: BorderStroke? = null,
    shadowElevation: Dp = ToolkitTheme.spacing.none,
    animateNavigation: Boolean = true,
    navigationContentKey: (K) -> Any? = { it },
    navigationTransitionSpec: (initialState: K, targetState: K) -> ContentTransform? = { _, _ -> null },
    sequelVisible: Boolean = false,
    sequelWidth: Dp = ToolkitTheme.dimensions.floatingSidebarSequelWidth,
    animateSequelWidth: Boolean = true,
    sequelContent: (@Composable ColumnScope.() -> Unit)? = null,
    primaryContent: @Composable (K) -> Unit
) {
    val motion = MaterialTheme.motionScheme
    val sequelTransition = updateTransition(targetState = sequelVisible, label = "floatingSidebarSequel")
    val isSequelComposed = sequelContent != null && (sequelTransition.currentState || sequelTransition.targetState)

    Surface(
        modifier = modifier.testTag("toolkit_floating_sidebar"),
        shape = shape,
        color = containerColor,
        border = border,
        shadowElevation = shadowElevation
    ) {
        if (!animateSequelWidth && sequelContent != null) {
            val defaultPrimaryWidth = ToolkitTheme.dimensions.floatingSidebarDefaultWidth
            var anchoredPrimaryWidth by remember { mutableStateOf(defaultPrimaryWidth) }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val minPrimaryWidth = ToolkitTheme.dimensions.floatingSidebarMinWidth.coerceAtMost(maxWidth)
                if (!isSequelComposed && maxWidth >= minPrimaryWidth) {
                    anchoredPrimaryWidth = maxWidth.coerceIn(minPrimaryWidth, defaultPrimaryWidth)
                }
                val availableForSequel = (maxWidth - minPrimaryWidth).coerceAtLeast(ToolkitTheme.spacing.none)
                val renderedSequelWidth = if (maxWidth >= ToolkitTheme.dimensions.floatingSidebarMinTwoColumnWidth) {
                    minOf(sequelWidth, availableForSequel)
                } else {
                    sequelWidth
                }
                val primaryWidth = if (isSequelComposed) {
                    val fullSplitWidth = anchoredPrimaryWidth + renderedSequelWidth
                    if (maxWidth >= fullSplitWidth) {
                        (maxWidth - renderedSequelWidth).coerceIn(minPrimaryWidth, maxWidth)
                    } else {
                        anchoredPrimaryWidth.coerceAtMost(maxWidth)
                    }
                } else {
                    maxWidth
                }

                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .width(primaryWidth)
                            .fillMaxHeight()
                            .clipToBounds()
                            .testTag("toolkit_floating_sidebar_primary")
                    ) {
                        FloatingSidebarPrimaryBody(
                            navigationKey = navigationKey,
                            animateNavigation = animateNavigation,
                            navigationContentKey = navigationContentKey,
                            navigationTransitionSpec = navigationTransitionSpec,
                            primaryContent = primaryContent
                        )
                    }

                    sequelTransition.AnimatedVisibility(
                        visible = { it },
                        enter = fadeIn(motion.fastEffectsSpec()),
                        exit = fadeOut(motion.fastEffectsSpec()),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clipToBounds()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds(),
                            contentAlignment = Alignment.TopStart
                        ) {
                            Row(
                                modifier = Modifier
                                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                                    .width(renderedSequelWidth)
                                    .fillMaxHeight()
                                    .testTag("toolkit_floating_sidebar_sequel")
                            ) {
                                VerticalDivider(
                                    thickness = ToolkitTheme.dimensions.borderThin,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
                                )
                                Surface(
                                    color = sequelContainerColor,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        content = sequelContent
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clipToBounds()
                        .testTag("toolkit_floating_sidebar_primary")
                ) {
                    FloatingSidebarPrimaryBody(
                        navigationKey = navigationKey,
                        animateNavigation = animateNavigation,
                        navigationContentKey = navigationContentKey,
                        navigationTransitionSpec = navigationTransitionSpec,
                        primaryContent = primaryContent
                    )
                }

                if (sequelContent != null) {
                    AnimatedVisibility(
                        visible = sequelVisible,
                        enter = expandHorizontally(
                            animationSpec = motion.defaultSpatialSpec(),
                            expandFrom = Alignment.Start
                        ) + fadeIn(motion.fastEffectsSpec()),
                        exit = shrinkHorizontally(
                            animationSpec = motion.defaultSpatialSpec(),
                            shrinkTowards = Alignment.Start
                        ) + fadeOut(motion.fastEffectsSpec())
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .testTag("toolkit_floating_sidebar_sequel")
                        ) {
                            VerticalDivider(
                                thickness = ToolkitTheme.dimensions.borderThin,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
                            )
                            Surface(
                                color = sequelContainerColor,
                                modifier = Modifier
                                    .width(sequelWidth)
                                    .fillMaxHeight()
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    content = sequelContent
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <K> FloatingSidebarPrimaryBody(
    navigationKey: K,
    animateNavigation: Boolean,
    navigationContentKey: (K) -> Any?,
    navigationTransitionSpec: (initialState: K, targetState: K) -> ContentTransform?,
    primaryContent: @Composable (K) -> Unit
) {
    val motion = MaterialTheme.motionScheme
    if (animateNavigation) {
        AnimatedContent(
            targetState = navigationKey,
            contentKey = navigationContentKey,
            transitionSpec = {
                navigationTransitionSpec(initialState, targetState)
                    ?: (fadeIn(motion.fastEffectsSpec()) + slideInHorizontally(motion.defaultSpatialSpec()) { it / 6 }
                        togetherWith fadeOut(motion.fastEffectsSpec()) + slideOutHorizontally(motion.defaultSpatialSpec()) { -it / 6 })
            },
            label = "floatingSidebarNavigation"
        ) { key ->
            primaryContent(key)
        }
    } else {
        primaryContent(navigationKey)
    }
}

/**
 * Standardized single-column floating sidebar layout with consistent header typography, optional
 * navigation/action icons, extra header controls (search/tabs), flexible body, and pinned bottom slot.
 */
@Composable
fun ToolkitFloatingSidebarScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    headerActions: (@Composable RowScope.() -> Unit)? = null,
    headerExtraContent: (@Composable ColumnScope.() -> Unit)? = null,
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null,
    bodyContent: @Composable BoxScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ToolkitTheme.dimensions.sduiCompactSpacing)
    ) {
        if (title.isNotBlank() || navigationIcon != null || headerActions != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = ToolkitTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
            ) {
                if (navigationIcon != null) {
                    navigationIcon()
                }
                if (title.isNotBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                if (headerActions != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall),
                        content = headerActions
                    )
                }
            }
        }

        if (headerExtraContent != null) {
            headerExtraContent()
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            content = bodyContent
        )

        if (bottomContent != null) {
            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
            bottomContent()
        }
    }
}

/**
 * Standardized header icon button for toggling a [ToolkitFloatingSidebar]'s sequel split pane
 * between expanded and compact drill-down modes.
 */
@Composable
fun ToolkitSequelSidebarToggleButton(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tooltip = stringResource(
        if (isExpanded) Res.string.sidebar_sequel_collapse else Res.string.sidebar_sequel_expand
    )
    ToolkitTooltipBox(
        tooltip = tooltip,
        position = ToolkitTooltipPosition.Below
    ) {
        if (isExpanded) {
            FilledTonalIconButton(
                onClick = onToggle,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = modifier
                    .size(ToolkitTheme.dimensions.denseIconButtonSize)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .testTag("sequel_sidebar_toggle")
            ) {
                Icon(
                    imageVector = Icons.Default.VerticalSplit,
                    contentDescription = tooltip,
                    modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                )
            }
        } else {
            IconButton(
                onClick = onToggle,
                modifier = modifier
                    .size(ToolkitTheme.dimensions.denseIconButtonSize)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .testTag("sequel_sidebar_toggle")
            ) {
                Icon(
                    imageVector = Icons.Default.VerticalSplit,
                    contentDescription = tooltip,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                )
            }
        }
    }
}
