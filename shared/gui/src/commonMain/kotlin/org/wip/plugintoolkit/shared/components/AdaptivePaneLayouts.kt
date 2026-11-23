package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDragHandle
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.PaneExpansionAnchor
import androidx.compose.material3.adaptive.layout.PaneExpansionState
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldScope
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.rememberPaneExpansionState
import androidx.compose.runtime.Composable
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

/** Minimum resizable list pane width in pixels/dp when dragging the pane divider. */
private val MIN_RESIZABLE_LIST_PANE_WIDTH = 300.dp

/** Minimum resizable trailing supporting pane width when dragging the pane divider. */
private val MIN_RESIZABLE_SUPPORTING_PANE_WIDTH = 280.dp

/** Maximum proportional width allocated to resizable primary/list panes during drag. */
private const val MAX_RESIZABLE_PANE_PROPORTION = 0.5f

/**
 * Computes a desktop-tuned [PaneScaffoldDirective] with canonical `24.dp` horizontal partition spacing
 * (`ToolkitTheme.spacing.large`) between adjacent panes.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberAdaptivePaneScaffoldDirective(): PaneScaffoldDirective {
    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    val horizontalSpacer = ToolkitTheme.spacing.large
    val baseDirective = calculatePaneScaffoldDirective(windowAdaptiveInfo)
    return remember(baseDirective, horizontalSpacer) {
        baseDirective.copy(horizontalPartitionSpacerSize = horizontalSpacer)
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
 * Creates a [PaneExpansionState] anchored around [initialWidth] (default `412.dp`) from the start
 * edge for multi-pane `ListDetailPaneScaffold` screens on desktop.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberFixedListPaneExpansionState(
    initialWidth: Dp = ToolkitTheme.dimensions.adaptiveListPaneWidth
): PaneExpansionState {
    val anchors = remember(initialWidth) {
        listOf(
            PaneExpansionAnchor.Offset.fromStart(MIN_RESIZABLE_LIST_PANE_WIDTH),
            PaneExpansionAnchor.Offset.fromStart(initialWidth),
            PaneExpansionAnchor.Proportion(MAX_RESIZABLE_PANE_PROPORTION),
        )
    }
    return rememberPaneExpansionState(
        anchors = anchors,
        initialAnchoredIndex = 1,
    )
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
    val anchors = remember(initialWidth) {
        listOf(
            PaneExpansionAnchor.Offset.fromEnd(MIN_RESIZABLE_SUPPORTING_PANE_WIDTH),
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
