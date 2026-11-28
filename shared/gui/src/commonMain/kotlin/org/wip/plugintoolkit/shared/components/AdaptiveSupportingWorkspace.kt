package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.Dp
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformVerticalScrollbar
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.pane_collapse_bottom_panel
import plugintoolkit.composeapp.generated.resources.pane_collapse_sidebar
import plugintoolkit.composeapp.generated.resources.pane_drag_to_resize_or_collapse
import plugintoolkit.composeapp.generated.resources.pane_expand_bottom_panel
import plugintoolkit.composeapp.generated.resources.pane_expand_sidebar

private const val MAX_BOTTOM_SUPPORTING_PANE_FRACTION = 0.62f
private const val MAX_SIDE_SUPPORTING_PANE_FRACTION = 0.55f

/**
 * Reusable two-pane workspace hosting a primary `mainPane` and a collapsible, resizable
 * `supportingPane` powered by [ToolkitResizableCollapsiblePane].
 *
 * - On wide containers (`maxWidth >= minTwoPaneWidth`), docks the supporting pane on the right
 *   ([PaneAttachmentEdge.End]) with two-stage drag-to-collapse, continuous drag-to-open from the
 *   edge handle, click-to-expand, and Material 3 Expressive spring physics.
 * - On narrower containers (`maxWidth < minTwoPaneWidth`), docks the supporting pane at the bottom
 *   ([PaneAttachmentEdge.Bottom]) with the exact same drag-to-resize, two-stage drag-to-collapse,
 *   drag-to-open, click-to-toggle, and spring settle behavior.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolkitAdaptiveSupportingWorkspace(
    showSupportingPane: Boolean,
    onShowSupportingPaneChange: (Boolean) -> Unit,
    collapseTooltip: String,
    expandTooltip: String,
    modifier: Modifier = Modifier,
    supportingPaneWidth: Dp = ToolkitTheme.dimensions.adaptiveSupportingPaneWidth,
    minTwoPaneWidth: Dp = supportingPaneWidth + ToolkitTheme.dimensions.breakpointMedium,
    applyWorkspacePadding: Boolean = true,
    mainPane: @Composable () -> Unit,
    supportingPane: @Composable () -> Unit
) {
    val workspacePadding = adaptiveWorkspacePadding()
    val defaultBottomHeight = ToolkitTheme.dimensions.bottomSupportingPaneDefaultHeight
    val minBottomHeight = ToolkitTheme.dimensions.bottomSupportingPaneMinHeight
    val minSideWidth = ToolkitTheme.dimensions.floatingSidebarMinWidth

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .then(if (applyWorkspacePadding) Modifier.padding(workspacePadding) else Modifier)
    ) {
        val canFitTwoPanes = maxWidth >= minTwoPaneWidth

        if (canFitTwoPanes) {
            val collapseSidebarLabel = collapseTooltip.ifBlank { stringResource(Res.string.pane_collapse_sidebar) }
            val expandSidebarLabel = expandTooltip.ifBlank { stringResource(Res.string.pane_expand_sidebar) }
            val dragResizeLabel = stringResource(Res.string.pane_drag_to_resize_or_collapse)

            ToolkitResizableCollapsiblePane(
                edge = PaneAttachmentEdge.End,
                isExpanded = showSupportingPane,
                onExpandedChange = onShowSupportingPaneChange,
                defaultPaneSize = supportingPaneWidth,
                minPaneSize = minSideWidth,
                minMainPaneSize = ToolkitTheme.dimensions.breakpointMedium,
                maxPaneFraction = MAX_SIDE_SUPPORTING_PANE_FRACTION,
                collapsible = true,
                clickToCollapseWhenExpanded = false,
                handleTooltipCollapse = dragResizeLabel,
                handleTooltipExpand = expandSidebarLabel,
                handleTestTag = "supporting_pane_side_handle",
                mainContent = {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = ToolkitTheme.shapes.large,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        mainPane()
                    }
                },
                paneContent = {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = ToolkitTheme.shapes.large,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val sideScrollState = rememberScrollState()
                            val sideReserve = if (sideScrollState.maxValue > 0) {
                                ToolkitTheme.dimensions.sduiScrollbarEndPadding
                            } else {
                                ToolkitTheme.spacing.none
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(sideScrollState)
                                    .padding(end = sideReserve)
                            ) {
                                supportingPane()
                            }
                            if (sideScrollState.maxValue > 0) {
                                PlatformVerticalScrollbar(
                                    scrollState = sideScrollState,
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .fillMaxHeight()
                                )
                            }

                            ToolkitTooltipBox(
                                tooltip = collapseSidebarLabel,
                                position = ToolkitTooltipPosition.Below,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(ToolkitTheme.spacing.small)
                            ) {
                                IconButton(
                                    onClick = { onShowSupportingPaneChange(false) },
                                    modifier = Modifier
                                        .size(ToolkitTheme.dimensions.denseIconButtonSize)
                                        .pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = collapseSidebarLabel,
                                        modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            )
        } else {
            val collapseBottomLabel = collapseTooltip.ifBlank { stringResource(Res.string.pane_collapse_bottom_panel) }
            val expandBottomLabel = expandTooltip.ifBlank { stringResource(Res.string.pane_expand_bottom_panel) }

            ToolkitResizableCollapsiblePane(
                edge = PaneAttachmentEdge.Bottom,
                isExpanded = showSupportingPane,
                onExpandedChange = onShowSupportingPaneChange,
                defaultPaneSize = defaultBottomHeight,
                minPaneSize = minBottomHeight,
                minMainPaneSize = ToolkitTheme.dimensions.bottomSupportingPaneMinHeight,
                maxPaneFraction = MAX_BOTTOM_SUPPORTING_PANE_FRACTION,
                collapsible = true,
                clickToCollapseWhenExpanded = true,
                handleTooltipCollapse = collapseBottomLabel,
                handleTooltipExpand = expandBottomLabel,
                handleTestTag = "supporting_pane_bottom_handle",
                mainContent = {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = ToolkitTheme.shapes.large,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        mainPane()
                    }
                },
                paneContent = {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = ToolkitTheme.shapes.large,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val bottomScrollState = rememberScrollState()
                        val bottomReserve = if (bottomScrollState.maxValue > 0) {
                            ToolkitTheme.dimensions.sduiScrollbarEndPadding
                        } else {
                            ToolkitTheme.spacing.none
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(bottomScrollState)
                                    .padding(end = bottomReserve)
                            ) {
                                supportingPane()
                            }
                            if (bottomScrollState.maxValue > 0) {
                                PlatformVerticalScrollbar(
                                    scrollState = bottomScrollState,
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .fillMaxHeight()
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}
