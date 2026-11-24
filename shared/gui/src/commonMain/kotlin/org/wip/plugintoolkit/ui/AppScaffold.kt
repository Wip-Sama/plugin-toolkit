package org.wip.plugintoolkit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.model.resolve
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.ui.DialogHost
import org.wip.plugintoolkit.core.ui.DialogService
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.navigation.model.Screen
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.SidebarStartMode
import org.wip.plugintoolkit.shared.components.ToastHost
import org.wip.plugintoolkit.shared.components.ToolkitTooltipBox
import org.wip.plugintoolkit.shared.components.ToolkitTooltipPosition
import org.wip.plugintoolkit.shared.components.sidebar.SidebarElement
import org.wip.plugintoolkit.shared.components.sidebar.SidebarSectionData
import org.wip.plugintoolkit.ui.titlebar.CustomTitleBar
import org.wip.plugintoolkit.ui.titlebar.LocalWindowController
import org.wip.plugintoolkit.ui.titlebar.MacWindowControls
import org.wip.plugintoolkit.ui.titlebar.TitleBarLeftOffsetSync
import org.wip.plugintoolkit.ui.titlebar.WindowDraggableArea
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.app_name
import plugintoolkit.composeapp.generated.resources.nav_collapse_rail
import plugintoolkit.composeapp.generated.resources.nav_expand_rail

/**
 * Root adaptive application scaffold using Material 3 Expressive [NavigationSuiteScaffoldLayout].
 * Renders [WideNavigationRail] (collapsed or expanded) on Medium+ widths (`>= 600.dp`) and falls
 * back to `ShortNavigationBarCompact` on Compact widths (`< 600.dp`), while keeping the desktop
 * custom title bar, window controls, toasts, and global dialogs intact.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppScaffold(
    settings: AppSettings,
    sections: List<SidebarSectionData<Screen>>,
    bottomSections: List<SidebarSectionData<Screen>>,
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    notificationService: NotificationService,
    dialogService: DialogService,
    onToggleNavbarState: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val windowController = LocalWindowController.current
    val appearance = settings.appearance
    val useCustomTitleBar = appearance.useCustomTitleBar && windowController != null
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    val isCompactWidth = !windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)

    var isCollapsed by remember(appearance.sidebarStartMode) {
        mutableStateOf(
            when (appearance.sidebarStartMode) {
                SidebarStartMode.Collapsed -> true
                SidebarStartMode.Expanded -> false
                SidebarStartMode.Remember -> appearance.isSidebarCollapsed
            }
        )
    }

    LaunchedEffect(appearance.isSidebarCollapsed, appearance.sidebarStartMode) {
        if (appearance.sidebarStartMode == SidebarStartMode.Remember) {
            isCollapsed = appearance.isSidebarCollapsed
        }
    }

    val railExpanded = !isCollapsed
    val railState = rememberWideNavigationRailState(
        initialValue = if (railExpanded) WideNavigationRailValue.Expanded else WideNavigationRailValue.Collapsed
    )
    LaunchedEffect(railExpanded) {
        if (railExpanded) railState.expand() else railState.collapse()
    }

    val density = LocalDensity.current
    var measuredRailWidthDp by remember { mutableFloatStateOf(0f) }

    TitleBarLeftOffsetSync(
        leftOffsetDp = if (!isCompactWidth && useCustomTitleBar) measuredRailWidthDp.roundToInt() else 0
    )

    val layoutType = when {
        isCompactWidth -> NavigationSuiteType.ShortNavigationBarCompact
        railExpanded -> NavigationSuiteType.WideNavigationRailExpanded
        else -> NavigationSuiteType.WideNavigationRailCollapsed
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        NavigationSuiteScaffoldLayout(
            layoutType = layoutType,
            navigationSuite = {
                if (isCompactWidth) {
                    val allElements = remember(sections, bottomSections) {
                        (sections + bottomSections)
                            .flatMap { it.elements }
                            .filter { it.isVisible }
                    }
                    NavigationSuite(
                        layoutType = NavigationSuiteType.ShortNavigationBarCompact,
                        colors = NavigationSuiteDefaults.colors(
                            shortNavigationBarContainerColor = MaterialTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        allElements.forEach { element ->
                            item(
                                selected = currentScreen == element.id,
                                onClick = { onScreenSelected(element.id) },
                                icon = {
                                    val titleText = element.title.resolve()
                                    Icon(
                                        imageVector = element.icon,
                                        contentDescription = titleText
                                    )
                                },
                                label = {
                                    Text(
                                        text = element.title.resolve(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            )
                        }
                    }
                } else {
                    WideNavigationRail(
                        state = railState,
                        modifier = Modifier
                            .fillMaxHeight()
                            .onSizeChanged { size ->
                                measuredRailWidthDp = with(density) { size.width.toDp().value }
                            },
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        colors = WideNavigationRailDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        header = {
                            Column(
                                modifier = if (railExpanded) {
                                    Modifier.padding(horizontal = ToolkitTheme.spacing.small)
                                } else {
                                    Modifier
                                },
                                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
                            ) {
                                if (PlatformUtils.isMac && useCustomTitleBar) {
                                    WindowDraggableArea(
                                        modifier = Modifier.fillMaxWidth(),
                                        onDoubleClick = { windowController.onMaximizeToggle.invoke() }
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(
                                                top = ToolkitTheme.spacing.small,
                                                bottom = ToolkitTheme.spacing.extraSmall
                                            )
                                        ) {
                                            MacWindowControls(controller = windowController)
                                        }
                                    }
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                                ) {
                                    val toggleTooltip = stringResource(
                                        if (railExpanded) Res.string.nav_collapse_rail else Res.string.nav_expand_rail
                                    )
                                    ToolkitTooltipBox(
                                        tooltip = toggleTooltip,
                                        position = ToolkitTooltipPosition.Right
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val nextCollapsed = !isCollapsed
                                                isCollapsed = nextCollapsed
                                                onToggleNavbarState?.invoke(nextCollapsed)
                                            }
                                        ) {
                                            Icon(
                                                imageVector = if (railExpanded) {
                                                    Icons.AutoMirrored.Filled.MenuOpen
                                                } else {
                                                    Icons.Default.Menu
                                                },
                                                contentDescription = toggleTooltip
                                            )
                                        }
                                    }
                                    if (railExpanded) {
                                        Text(
                                            text = stringResource(Res.string.app_name),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    ) {
                        Column(modifier = Modifier.fillMaxHeight()) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
                            ) {
                                sections.forEach { section ->
                                    val visibleElements = section.elements.filter { it.isVisible }
                                    if (railExpanded && section.title != null && visibleElements.isNotEmpty()) {
                                        Text(
                                            text = section.title.resolve(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(
                                                start = ToolkitTheme.spacing.large,
                                                end = ToolkitTheme.spacing.medium,
                                                top = ToolkitTheme.spacing.small,
                                                bottom = ToolkitTheme.spacing.extraSmall
                                            )
                                        )
                                    }
                                    visibleElements.forEach { element ->
                                        AdaptiveRailNavigationItem(
                                            element = element,
                                            selected = currentScreen == element.id,
                                            railExpanded = railExpanded,
                                            onClick = { onScreenSelected(element.id) }
                                        )
                                    }
                                }
                            }
                            if (bottomSections.isNotEmpty()) {
                                Spacer(modifier = Modifier.padding(top = ToolkitTheme.spacing.small))
                                Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)) {
                                    bottomSections.forEach { section ->
                                        section.elements.filter { it.isVisible }.forEach { element ->
                                            AdaptiveRailNavigationItem(
                                                element = element,
                                                selected = currentScreen == element.id,
                                                railExpanded = railExpanded,
                                                onClick = { onScreenSelected(element.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (useCustomTitleBar) {
                        CustomTitleBar(
                            modifier = Modifier.fillMaxWidth(),
                            showTitleAndIcon = isCompactWidth,
                            isMac = PlatformUtils.isMac && isCompactWidth
                        )
                    }

                    val contentCornerRadius = if (!isCompactWidth) {
                        ToolkitTheme.dimensions.contentCanvasCornerRadius
                    } else {
                        0.dp
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = contentCornerRadius)),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        content()
                    }
                }

                ToastHost(
                    notificationService = notificationService,
                    settings = settings.notifications
                )
                DialogHost(dialogService = dialogService)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AdaptiveRailNavigationItem(
    element: SidebarElement<Screen>,
    selected: Boolean,
    railExpanded: Boolean,
    onClick: () -> Unit
) {
    val labelText = element.title.resolve()
    ToolkitTooltipBox(
        tooltip = if (!railExpanded) labelText else null,
        position = ToolkitTooltipPosition.Right
    ) {
        WideNavigationRailItem(
            selected = selected,
            onClick = onClick,
            railExpanded = railExpanded,
            icon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = element.icon,
                        contentDescription = labelText
                    )
                    if (!railExpanded) {
                        element.trailingContent(false)
                    }
                }
            },
            label = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                ) {
                    Text(
                        text = labelText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (railExpanded) {
                        element.trailingContent(true)
                    }
                }
            }
        )
    }
}
