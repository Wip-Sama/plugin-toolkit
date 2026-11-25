package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AppBarMenuState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.nav_back_to_list
import plugintoolkit.composeapp.generated.resources.nav_more_options

/** Maximum proportional width allocated to resizable primary/list panes during drag. */
private const val MAX_RESIZABLE_PANE_PROPORTION = 0.55f

/** Minimum comfortable width reserved for a detail or main pane before collapsing to single-pane mode. */
private val MIN_COMFORTABLE_DETAIL_PANE_WIDTH = 440.dp

/**
 * Computes a desktop-tuned [PaneScaffoldDirective] with sleek `12.dp` horizontal partition spacing
 * (`ToolkitTheme.dimensions.workspacePaneGap`) housing the [DesktopPaneDragHandle].
 *
 * When [availableContentWidth] is provided (measured inside the content area after subtracting the
 * primary navigation sidebar), two-pane layouts automatically fall back to a single pane whenever
 * the content viewport cannot comfortably fit both panes side-by-side without squishing cards.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberAdaptivePaneScaffoldDirective(
    availableContentWidth: Dp = Dp.Unspecified,
    minTwoPaneWidth: Dp = 760.dp
): PaneScaffoldDirective {
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val horizontalSpacer = ToolkitTheme.dimensions.workspacePaneGap
    val baseDirective = calculatePaneScaffoldDirective(windowAdaptiveInfo)
    return remember(baseDirective, horizontalSpacer, availableContentWidth, minTwoPaneWidth) {
        val maxPartitions = if (availableContentWidth != Dp.Unspecified && availableContentWidth < minTwoPaneWidth) {
            1
        } else {
            baseDirective.maxHorizontalPartitions
        }
        baseDirective.copy(
            maxHorizontalPartitions = maxPartitions,
            horizontalPartitionSpacerSize = horizontalSpacer
        )
    }
}

/**
 * Returns canonical Material 3 outer horizontal padding:
 * `16.dp` on Compact width windows (`< 600.dp`) and `24.dp` on Medium+ desktop widths (`>= 600.dp`).
 */
@Composable
fun adaptiveOuterHorizontalPadding(): Dp {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isMediumOrWider = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    return if (isMediumOrWider) ToolkitTheme.spacing.large else ToolkitTheme.spacing.medium
}

/**
 * Returns unified desktop workspace padding around floating sidebars and detail panes, using a
 * tighter start margin (`12.dp`) adjacent to the primary navigation rail while preserving clean
 * framing on the end and vertical edges.
 */
@Composable
fun adaptiveWorkspacePadding(): PaddingValues {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isMediumOrWider = windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    return if (isMediumOrWider) {
        PaddingValues(
            start = ToolkitTheme.dimensions.workspaceStartPadding,
            end = ToolkitTheme.dimensions.workspaceEndPadding,
            top = ToolkitTheme.dimensions.workspaceVerticalPadding,
            bottom = ToolkitTheme.dimensions.workspaceVerticalPadding
        )
    } else {
        PaddingValues(ToolkitTheme.spacing.small)
    }
}

/**
 * Creates a [PaneExpansionState] anchored around [initialWidth] (default `300.dp`) from the start
 * edge for multi-pane `ListDetailPaneScaffold` screens on desktop, supporting dynamic width updates
 * when a floating sidebar toggles its sequel pane.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberFixedListPaneExpansionState(
    initialWidth: Dp = ToolkitTheme.dimensions.floatingSidebarDefaultWidth,
    minWidth: Dp = ToolkitTheme.dimensions.floatingSidebarMinWidth
): PaneExpansionState {
    val clampedMin = minOf(minWidth, initialWidth)
    val anchors = remember(initialWidth, clampedMin) {
        listOf(
            PaneExpansionAnchor.Offset.fromStart(clampedMin),
            PaneExpansionAnchor.Offset.fromStart(initialWidth),
            PaneExpansionAnchor.Proportion(MAX_RESIZABLE_PANE_PROPORTION),
        )
    }
    val state = rememberPaneExpansionState(
        anchors = anchors,
        initialAnchoredIndex = 1,
    )
    LaunchedEffect(initialWidth) {
        state.animateTo(PaneExpansionAnchor.Offset.fromStart(initialWidth))
    }
    return state
}

/**
 * Creates a [PaneExpansionState] anchored around [initialWidth] (default `360.dp`) from the end
 * edge for trailing supporting panes in `SupportingPaneScaffold` screens.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberSupportingPaneExpansionState(
    initialWidth: Dp = ToolkitTheme.dimensions.adaptiveSupportingPaneWidth
): PaneExpansionState {
    val minWidth = ToolkitTheme.dimensions.floatingSidebarMinWidth
    val anchors = remember(initialWidth, minWidth) {
        listOf(
            PaneExpansionAnchor.Offset.fromEnd(minWidth),
            PaneExpansionAnchor.Offset.fromEnd(initialWidth),
            PaneExpansionAnchor.Proportion(MAX_RESIZABLE_PANE_PROPORTION),
        )
    }
    return rememberPaneExpansionState(
        anchors = anchors,
        initialAnchoredIndex = 1,
    )
}

/**
 * Standardized two-pane adaptive desktop workspace wrapping Material 3 [ListDetailPaneScaffold].
 *
 * - Applies [adaptiveWorkspacePadding] and `12.dp` gutter with [DesktopPaneDragHandle].
 * - Renders [listPane] directly (expecting a single rounded `ToolkitFloatingSidebar` surface,
 *   preventing nested elevation duplication).
 * - Wraps [detailPane] in a single clean `surfaceContainerLowest` rounded surface.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun <T> ToolkitAdaptiveListDetailWorkspace(
    modifier: Modifier = Modifier,
    navigator: ThreePaneScaffoldNavigator<T>? = null,
    listPaneWidth: Dp = ToolkitTheme.dimensions.floatingSidebarDefaultWidth,
    listPaneMinWidth: Dp = ToolkitTheme.dimensions.floatingSidebarMinWidth,
    detailContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    listPane: @Composable ThreePaneScaffoldScope.(ThreePaneScaffoldNavigator<T>) -> Unit,
    detailPane: @Composable BoxScope.(ThreePaneScaffoldNavigator<T>) -> Unit
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(adaptiveWorkspacePadding())
    ) {
        val minTwoPaneWidth = listPaneWidth + MIN_COMFORTABLE_DETAIL_PANE_WIDTH
        val contentDirective = rememberAdaptivePaneScaffoldDirective(
            availableContentWidth = maxWidth,
            minTwoPaneWidth = minTwoPaneWidth
        )
        val effectiveNavigator = navigator ?: rememberListDetailPaneScaffoldNavigator<T>(
            scaffoldDirective = contentDirective
        )
        val effectiveDirective = if (maxWidth < minTwoPaneWidth) {
            effectiveNavigator.scaffoldDirective.copy(maxHorizontalPartitions = 1)
        } else {
            effectiveNavigator.scaffoldDirective
        }
        val paneExpansionState = rememberFixedListPaneExpansionState(
            initialWidth = listPaneWidth,
            minWidth = listPaneMinWidth
        )

        ListDetailPaneScaffold(
            directive = effectiveDirective,
            value = effectiveNavigator.scaffoldValue,
            modifier = Modifier.fillMaxSize(),
            paneExpansionState = paneExpansionState,
            paneExpansionDragHandle = { state ->
                DesktopPaneDragHandle(paneExpansionState = state)
            },
            listPane = {
                AnimatedPane(
                    modifier = Modifier.preferredWidth(listPaneWidth)
                ) {
                    listPane(effectiveNavigator)
                }
            },
            detailPane = {
                AnimatedPane {
                    Surface(
                        color = detailContainerColor,
                        shape = ToolkitTheme.shapes.large,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            detailPane(effectiveNavigator)
                        }
                    }
                }
            }
        )
    }
}

/**
 * Desktop M3 Expressive [VerticalDragHandle] wired to [PaneExpansionState] with accessible
 * desktop pointer target sizing and interaction state.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ThreePaneScaffoldScope.DesktopPaneDragHandle(
    paneExpansionState: PaneExpansionState,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    VerticalDragHandle(
        modifier = modifier.paneExpansionDraggable(
            state = paneExpansionState,
            minTouchTargetSize = LocalMinimumInteractiveComponentSize.current,
            interactionSource = interactionSource,
        ),
        interactionSource = interactionSource,
    )
}

/**
 * Constrains readable desktop prose, settings, or form content to [maxWidth] (`680.dp` default)
 * so ultra-wide windows maintain optimal horizontal reading measure.
 */
@Composable
fun AdaptiveConstrainedContent(
    modifier: Modifier = Modifier,
    maxWidth: Dp = ToolkitTheme.dimensions.sduiFormMaxWidth,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = contentAlignment
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
            content = content
        )
    }
}

/**
 * Shared pane header using Material 3 Expressive [TopAppBar] with optional [subtitle],
 * zeroed window insets for embedded desktop panes, and `AppBarRow`-compatible [actions].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AdaptivePaneHeader(
    title: String,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    containerColor: Color = Color.Transparent,
    modifier: Modifier = Modifier
) {
    val colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor)
    if (subtitle != null) {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            subtitle = {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            modifier = modifier,
            navigationIcon = navigationIcon ?: {},
            actions = actions,
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = colors
        )
    } else {
        TopAppBar(
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            modifier = modifier,
            navigationIcon = navigationIcon ?: {},
            actions = actions,
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = colors
        )
    }
}

/**
 * Standard tooltip-wrapped overflow trigger button for Material 3 `AppBarRow`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveAppBarOverflowButton(menuState: AppBarMenuState) {
    val label = stringResource(Res.string.nav_more_options)
    ToolkitTooltipBox(tooltip = label) {
        IconButton(onClick = { if (menuState.isShowing) menuState.dismiss() else menuState.show() }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = label
            )
        }
    }
}

/**
 * Tooltip-wrapped back navigation button shown when a `ListDetailPaneScaffold` collapses
 * to a single pane on narrower viewports.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveBackNavigationButton(onClick: () -> Unit) {
    val label = stringResource(Res.string.nav_back_to_list)
    ToolkitTooltipBox(tooltip = label) {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = label
            )
        }
    }
}
