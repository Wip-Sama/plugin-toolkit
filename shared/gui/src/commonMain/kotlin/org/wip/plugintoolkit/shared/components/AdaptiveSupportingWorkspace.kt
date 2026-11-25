package org.wip.plugintoolkit.shared.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformVerticalScrollbar
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.pane_collapse_bottom_panel
import plugintoolkit.composeapp.generated.resources.pane_collapse_sidebar
import plugintoolkit.composeapp.generated.resources.pane_expand_bottom_panel
import plugintoolkit.composeapp.generated.resources.pane_expand_sidebar

private const val MAX_BOTTOM_SUPPORTING_PANE_FRACTION = 0.62f
private const val BOTTOM_DRAG_COLLAPSE_THRESHOLD_DP = 76f

/**
 * Reusable two-pane workspace hosting a primary `mainPane` and a collapsible, resizable
 * `supportingPane`.
 *
 * - On wide containers (`maxWidth >= minTwoPaneWidth`), places the supporting pane on the right
 *   inside a Material 3 [SupportingPaneScaffold] with a draggable vertical divider handle. When
 *   collapsed, the supporting pane disappears completely and remains retrievable from a slim edge
 *   divider bar along the right edge.
 * - On narrower containers (`maxWidth < minTwoPaneWidth`), stacks the supporting pane below the
 *   main pane separated by a slim horizontal divider handle supporting vertical drag-resizing and
 *   full collapse-to-bar so main content is never squished or starved of vertical space.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3ExpressiveApi::class)
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
    val motion = MaterialTheme.motionScheme
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val defaultBottomHeight = ToolkitTheme.dimensions.bottomSupportingPaneDefaultHeight
    val minBottomHeight = ToolkitTheme.dimensions.bottomSupportingPaneMinHeight
    val workspacePadding = adaptiveWorkspacePadding()
    var bottomPaneHeight by remember { mutableStateOf(defaultBottomHeight) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .then(if (applyWorkspacePadding) Modifier.padding(workspacePadding) else Modifier)
    ) {
        val canFitTwoPanes = maxWidth >= minTwoPaneWidth

        if (canFitTwoPanes) {
            val directive = rememberAdaptivePaneScaffoldDirective(
                availableContentWidth = maxWidth,
                minTwoPaneWidth = minTwoPaneWidth
            ).let { base ->
                if (showSupportingPane) base else base.copy(maxHorizontalPartitions = 1)
            }
            val paneExpansionState = rememberSupportingPaneExpansionState(
                initialWidth = supportingPaneWidth
            )
            val navigator = rememberSupportingPaneScaffoldNavigator<Unit>(scaffoldDirective = directive)
            LaunchedEffect(showSupportingPane) {
                if (!showSupportingPane && navigator.currentDestination?.pane == SupportingPaneScaffoldRole.Supporting && navigator.canNavigateBack()) {
                    navigator.navigateBack()
                }
            }

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.none)
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    SupportingPaneScaffold(
                        directive = directive,
                        value = navigator.scaffoldValue,
                        modifier = Modifier.fillMaxSize(),
                        paneExpansionState = paneExpansionState,
                        paneExpansionDragHandle = { state ->
                            DesktopPaneDragHandle(paneExpansionState = state)
                        },
                        mainPane = {
                            AnimatedPane {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                    shape = ToolkitTheme.shapes.large,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    mainPane()
                                }
                            }
                        },
                        supportingPane = {
                            AnimatedPane(
                                modifier = Modifier.preferredWidth(supportingPaneWidth)
                            ) {
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

                                        val collapseSidebarLabel = stringResource(Res.string.pane_collapse_sidebar)
                                        ToolkitTooltipBox(
                                            tooltip = collapseTooltip.ifBlank { collapseSidebarLabel },
                                            position = ToolkitTooltipPosition.Below,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(ToolkitTheme.spacing.small)
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    onShowSupportingPaneChange(false)
                                                    if (navigator.canNavigateBack()) {
                                                        scope.launch { navigator.navigateBack() }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .size(ToolkitTheme.dimensions.denseIconButtonSize)
                                                    .pointerHoverIcon(PointerIcon.Hand)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ChevronRight,
                                                    contentDescription = collapseTooltip.ifBlank { collapseSidebarLabel },
                                                    modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    )
                }

                AnimatedVisibility(
                    visible = !showSupportingPane,
                    enter = expandHorizontally(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                    exit = shrinkHorizontally(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec())
                ) {
                    ToolkitCollapsedSideDividerHandle(
                        tooltip = expandTooltip.ifBlank { stringResource(Res.string.pane_expand_sidebar) },
                        onClick = {
                            onShowSupportingPaneChange(true)
                            scope.launch {
                                navigator.navigateTo(SupportingPaneScaffoldRole.Supporting)
                            }
                        }
                    )
                }
            }
        } else {
            val maxAllowedBottomHeight = (maxHeight * MAX_BOTTOM_SUPPORTING_PANE_FRACTION)
                .coerceAtLeast(ToolkitTheme.dimensions.bottomSupportingPaneMinHeight)
            val clampedBottomHeight = bottomPaneHeight.coerceIn(
                ToolkitTheme.dimensions.bottomSupportingPaneMinHeight,
                maxAllowedBottomHeight
            )
            val latestHeight by rememberUpdatedState(clampedBottomHeight)
            val latestShowPane by rememberUpdatedState(showSupportingPane)

            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = ToolkitTheme.shapes.large,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    mainPane()
                }

                BottomSupportingDividerBar(
                    isExpanded = showSupportingPane,
                    tooltip = if (showSupportingPane) {
                        collapseTooltip.ifBlank { stringResource(Res.string.pane_collapse_bottom_panel) }
                    } else {
                        expandTooltip.ifBlank { stringResource(Res.string.pane_expand_bottom_panel) }
                    },
                    onToggle = { onShowSupportingPaneChange(!showSupportingPane) },
                    onVerticalDrag = { dragAmountPx ->
                        val deltaDp = with(density) { dragAmountPx.toDp() }
                        if (!latestShowPane) {
                            if (dragAmountPx < -4f) {
                                bottomPaneHeight = minBottomHeight
                                onShowSupportingPaneChange(true)
                            }
                        } else {
                            val candidate = latestHeight - deltaDp
                            if (candidate.value < BOTTOM_DRAG_COLLAPSE_THRESHOLD_DP) {
                                onShowSupportingPaneChange(false)
                            } else {
                                bottomPaneHeight = candidate.coerceIn(
                                    minBottomHeight,
                                    maxAllowedBottomHeight
                                )
                            }
                        }
                    }
                )

                AnimatedVisibility(
                    visible = showSupportingPane,
                    enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                    exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec())
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = ToolkitTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(clampedBottomHeight)
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
            }
        }
    }
}

@Composable
private fun ToolkitCollapsedSideDividerHandle(
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    ToolkitTooltipBox(
        tooltip = tooltip,
        position = ToolkitTooltipPosition.Left,
        modifier = modifier
            .fillMaxHeight()
            .padding(start = ToolkitTheme.spacing.extraSmall)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(ToolkitTheme.dimensions.paneDividerHandleThickness)
                .clip(ToolkitTheme.shapes.small)
                .semantics { contentDescription = tooltip }
                .hoverable(interactionSource)
                .clickable(onClick = onClick)
                .pointerHoverIcon(PointerIcon.Hand),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(ToolkitTheme.dimensions.sduiSplitHandlePillThickness)
                    .height(
                        if (isHovered) {
                            ToolkitTheme.dimensions.sduiSplitHandlePillLength * 1.4f
                        } else {
                            ToolkitTheme.dimensions.sduiSplitHandlePillLength
                        }
                    )
                    .background(
                        color = if (isHovered) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
private fun BottomSupportingDividerBar(
    isExpanded: Boolean,
    tooltip: String,
    onToggle: () -> Unit,
    onVerticalDrag: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val currentDragHandler by rememberUpdatedState(onVerticalDrag)

    ToolkitTooltipBox(
        tooltip = tooltip,
        position = ToolkitTooltipPosition.Above,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = ToolkitTheme.spacing.extraSmall)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ToolkitTheme.dimensions.paneDividerHandleThickness)
                .clip(ToolkitTheme.shapes.small)
                .hoverable(interactionSource)
                .background(
                    if (isHovered) {
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                    }
                )
                .pointerHoverIcon(PointerIcon.Hand)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        currentDragHandler(dragAmount)
                    }
                }
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
            ) {
                Box(
                    modifier = Modifier
                        .width(
                            if (isHovered) {
                                ToolkitTheme.dimensions.sduiSplitHandlePillLength * 1.4f
                            } else {
                                ToolkitTheme.dimensions.sduiSplitHandlePillLength
                            }
                        )
                        .height(ToolkitTheme.dimensions.sduiSplitHandlePillThickness)
                        .background(
                            color = if (isHovered) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                            shape = CircleShape
                        )
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = tooltip,
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconExtraSmall),
                    tint = if (isHovered) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
