package org.wip.plugintoolkit.shared.components.sidebar

import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.model.LocalizedString
import org.wip.plugintoolkit.core.model.localized
import org.wip.plugintoolkit.core.model.resolve
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformVerticalScrollbar
import org.wip.plugintoolkit.features.navigation.model.Screen
import org.wip.plugintoolkit.shared.components.ToolkitTooltipBox
import org.wip.plugintoolkit.shared.components.ToolkitTooltipPosition
import org.wip.plugintoolkit.shared.components.verticalFadingEdges
import org.wip.plugintoolkit.ui.titlebar.WindowDraggableArea
import org.wip.plugintoolkit.ui.titlebar.nonDraggableTitleBar
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.app_name
import plugintoolkit.composeapp.generated.resources.nav_collapse_rail
import plugintoolkit.composeapp.generated.resources.nav_expand_rail

/** Delay before a collapsed primary sidebar expands on mouse hover, preventing accidental triggers when leaving the window. */
internal const val SIDEBAR_HOVER_EXPAND_DELAY_MS: Long = 250L

/** Short debounce before collapsing a hover-expanded sidebar when the pointer leaves. */
internal const val SIDEBAR_HOVER_COLLAPSE_DEBOUNCE_MS: Long = 120L

private const val TITLE_REVEAL_THRESHOLD = 0.01f

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> NavigationSidebar(
    title: LocalizedString,
    bodySections: List<SidebarSectionData<out T>>,
    bottomSections: List<SidebarSectionData<out T>> = emptyList(),
    currentScreen: T,
    onScreenSelected: (T) -> Unit,
    isNavbarCollapsed: Boolean,
    onToggleNavbar: () -> Unit,
    canCollapse: Boolean = true,
    isSecondary: Boolean = !canCollapse,
    containerColor: Color = if (isSecondary) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    headerContent: @Composable () -> Unit = {},
    bottomExtraContent: @Composable ColumnScope.() -> Unit = {},
    expandedWidth: Dp = ToolkitTheme.dimensions.sidebarExpandedWidth,
    hoverExpandDelayMs: Long = SIDEBAR_HOVER_EXPAND_DELAY_MS,
    hoverCollapseDebounceMs: Long = SIDEBAR_HOVER_COLLAPSE_DEBOUNCE_MS,
    modifier: Modifier = Modifier
) {
    var isPointerInside by remember { mutableStateOf(false) }
    var isHoverExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(isPointerInside, isNavbarCollapsed, canCollapse, hoverExpandDelayMs, hoverCollapseDebounceMs) {
        if (!canCollapse || !isNavbarCollapsed) {
            isHoverExpanded = false
        } else if (isPointerInside) {
            if (hoverExpandDelayMs > 0L) {
                delay(hoverExpandDelayMs)
            }
            isHoverExpanded = true
        } else if (isHoverExpanded) {
            if (hoverCollapseDebounceMs > 0L) {
                delay(hoverCollapseDebounceMs)
            }
            isHoverExpanded = false
        }
    }

    // If navbar is collapsed, it temporarily expands on sustained hover.
    val isActuallyExpanded = !canCollapse || !isNavbarCollapsed || isHoverExpanded

    val motion = MaterialTheme.motionScheme
    val resolvedExpandedWidth = if (expandedWidth == Dp.Unspecified) {
        ToolkitTheme.dimensions.sidebarExpandedWidth
    } else {
        expandedWidth
    }

    // Single unified transition keeps sidebar width, header title, and all item labels in lockstep.
    val expansionTransition = updateTransition(
        targetState = isActuallyExpanded,
        label = "navigationSidebarExpansion"
    )
    val sidebarWidth by expansionTransition.animateDp(
        transitionSpec = { motion.defaultSpatialSpec() },
        label = "navigationSidebarWidth"
    ) { expanded ->
        if (expanded) resolvedExpandedWidth else ToolkitTheme.dimensions.sidebarCollapsedWidth
    }
    val labelRevealProgress by expansionTransition.animateFloat(
        transitionSpec = { motion.fastEffectsSpec() },
        label = "navigationSidebarLabelAlpha"
    ) { expanded ->
        if (expanded) 1f else 0f
    }

    val widthModifier = if (expandedWidth == Dp.Unspecified && isActuallyExpanded) {
        Modifier.fillMaxWidth()
    } else {
        Modifier.width(sidebarWidth)
    }

    val isFloatingHover = isHoverExpanded && isNavbarCollapsed && canCollapse

    Surface(
        modifier = modifier
            .then(widthModifier)
            .fillMaxHeight()
            .clipToBounds()
            .testTag("navigation_sidebar")
            .onPointerEvent(PointerEventType.Enter) { isPointerInside = true }
            .onPointerEvent(PointerEventType.Exit) { isPointerInside = false },
        color = containerColor,
        shadowElevation = if (isFloatingHover) ToolkitTheme.dimensions.elevationMedium else ToolkitTheme.spacing.none
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ToolkitTheme.dimensions.sduiCompactSpacing)
        ) {
            // Optional platform header (e.g., macOS traffic light controls)
            if (isActuallyExpanded || !canCollapse) {
                headerContent()
            }

            // Header / toggle row aligned identically to SidebarItem leading icon slot
            val resolvedTitle = title.resolve()
            val showHeaderRow = canCollapse || resolvedTitle.isNotBlank()
            if (showHeaderRow) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = ToolkitTheme.spacing.small,
                            vertical = ToolkitTheme.spacing.small
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    if (canCollapse) {
                        val toggleDesc = stringResource(
                            if (isNavbarCollapsed) Res.string.nav_expand_rail else Res.string.nav_collapse_rail
                        )
                        ToolkitTooltipBox(
                            tooltip = toggleDesc,
                            position = ToolkitTooltipPosition.Right
                        ) {
                            IconButton(
                                onClick = onToggleNavbar,
                                modifier = Modifier
                                    .size(ToolkitTheme.dimensions.settingsIconContainerSize)
                                    .nonDraggableTitleBar("sidebar_hamburger_button")
                                    .pointerHoverIcon(PointerIcon.Hand)
                            ) {
                                Icon(
                                    imageVector = if (isNavbarCollapsed) Icons.Default.Menu else Icons.AutoMirrored.Filled.MenuOpen,
                                    contentDescription = toggleDesc
                                )
                            }
                        }
                    }

                    val titleContent: @Composable () -> Unit = {
                        if (labelRevealProgress > TITLE_REVEAL_THRESHOLD && resolvedTitle.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.graphicsLayer { alpha = labelRevealProgress.coerceIn(0f, 1f) }
                            ) {
                                if (canCollapse) {
                                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))
                                }
                                Text(
                                    text = resolvedTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip
                                )
                            }
                        }
                    }

                    if (!isSecondary) {
                        WindowDraggableArea(
                            modifier = Modifier.weight(1f)
                        ) {
                            titleContent()
                        }
                    } else {
                        titleContent()
                    }
                }
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
            }

            val bodyScrollState = rememberScrollState()
            val scrollbarReserve = if (bodyScrollState.maxValue > 0) {
                ToolkitTheme.dimensions.sduiScrollbarEndPadding
            } else {
                ToolkitTheme.spacing.none
            }

            // Nav sections (BODY SECTION - Scrollable, full verticality)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalFadingEdges(
                            scrollState = bodyScrollState,
                            topFadeLength = ToolkitTheme.spacing.large,
                            bottomFadeLength = ToolkitTheme.spacing.large,
                            almostOpaque = ToolkitTheme.opacity.almostOpaque
                        )
                        .verticalScroll(bodyScrollState)
                        .padding(end = scrollbarReserve)
                ) {
                    bodySections.forEach { section ->
                        if (section.elements.any { it.isVisible }) {
                            SidebarSection(
                                section = section,
                                currentSelection = currentScreen,
                                onItemSelected = onScreenSelected,
                                isExpanded = isActuallyExpanded,
                                expandProgress = labelRevealProgress
                            )
                            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                        }
                    }
                }
                if (bodyScrollState.maxValue > 0) {
                    PlatformVerticalScrollbar(
                        scrollState = bodyScrollState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                    )
                }
            }

            // Bottom sections (BOTTOM SECTION - Pinned, never clipped)
            if (bottomSections.any { s -> s.elements.any { it.isVisible } }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = ToolkitTheme.spacing.small)
                ) {
                    bottomExtraContent()
                    bottomSections.forEach { section ->
                        if (section.elements.any { it.isVisible }) {
                            SidebarSection(
                                section = section,
                                currentSelection = currentScreen,
                                onItemSelected = onScreenSelected,
                                isExpanded = isActuallyExpanded,
                                expandProgress = labelRevealProgress
                            )
                            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraSmall))
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun NavigationSidebarPreview() {
    MaterialTheme {
        NavigationSidebar(
            title = Res.string.app_name.localized,
            bodySections = listOf<SidebarSectionData<Screen>>(),
            currentScreen = Screen.Main,
            onScreenSelected = {},
            isNavbarCollapsed = false,
            onToggleNavbar = {}
        )
    }
}
