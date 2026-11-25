package org.wip.plugintoolkit.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import kotlin.math.roundToInt
import org.wip.plugintoolkit.core.model.localized
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
import org.wip.plugintoolkit.shared.components.sidebar.NavigationSidebar
import org.wip.plugintoolkit.shared.components.sidebar.SidebarSectionData
import org.wip.plugintoolkit.ui.titlebar.CustomTitleBar
import org.wip.plugintoolkit.ui.titlebar.LocalWindowController
import org.wip.plugintoolkit.ui.titlebar.MacWindowControls
import org.wip.plugintoolkit.ui.titlebar.TitleBarLeftOffsetSync
import org.wip.plugintoolkit.ui.titlebar.WindowDraggableArea
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.app_name

/**
 * Root adaptive application scaffold combining Material 3 [WindowSizeClass] breakpoints with the
 * Toolkit desktop [NavigationSidebar] shell.
 *
 * - On Medium+ widths (`>= 600.dp`): renders the full-height collapsible [NavigationSidebar] with
 *   collapsible categories, pinned bottom actions, delayed hover-expand overlay, and seamless
 *   `surfaceContainerLow` shell chrome shared with [CustomTitleBar].
 * - On Compact widths (`< 600.dp`): switches automatically to `ShortNavigationBarCompact` via
 *   [NavigationSuiteScaffoldLayout].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    val motion = MaterialTheme.motionScheme
    val layoutSidebarWidth by animateDpAsState(
        targetValue = if (isCollapsed) {
            ToolkitTheme.dimensions.sidebarCollapsedWidth
        } else {
            ToolkitTheme.dimensions.sidebarExpandedWidth
        },
        animationSpec = motion.defaultSpatialSpec(),
        label = "appScaffoldSidebarWidth"
    )

    TitleBarLeftOffsetSync(
        leftOffsetDp = if (!isCompactWidth && useCustomTitleBar) layoutSidebarWidth.value.roundToInt() else 0
    )

    val shellColor = MaterialTheme.colorScheme.surfaceContainerLow
    val persistentContent = remember(content) {
        movableContentOf { content() }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = shellColor
    ) {
        if (isCompactWidth) {
            val allElements = remember(sections, bottomSections) {
                (sections + bottomSections)
                    .flatMap { it.elements }
                    .filter { it.isVisible }
            }
            NavigationSuiteScaffoldLayout(
                layoutType = NavigationSuiteType.ShortNavigationBarCompact,
                navigationSuite = {
                    NavigationSuite(
                        layoutType = NavigationSuiteType.ShortNavigationBarCompact,
                        colors = NavigationSuiteDefaults.colors(
                            shortNavigationBarContainerColor = shellColor
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
                }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (useCustomTitleBar) {
                            CustomTitleBar(
                                modifier = Modifier.fillMaxWidth(),
                                showTitleAndIcon = true,
                                isMac = PlatformUtils.isMac,
                                containerColor = shellColor
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            persistentContent()
                        }
                    }

                    ToastHost(
                        notificationService = notificationService,
                        settings = settings.notifications
                    )
                    DialogHost(dialogService = dialogService)
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Reserves pinned rail width; hover-expanded sidebar floats cleanly over content
                    Spacer(modifier = Modifier.width(layoutSidebarWidth))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        if (useCustomTitleBar) {
                            CustomTitleBar(
                                modifier = Modifier.fillMaxWidth(),
                                showTitleAndIcon = false,
                                isMac = false,
                                containerColor = shellColor
                            )
                        }

                        val contentCornerRadius = if (useCustomTitleBar) {
                            ToolkitTheme.dimensions.contentCanvasCornerRadius
                        } else {
                            0.dp
                        }
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(topStart = contentCornerRadius)),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            persistentContent()
                        }
                    }
                }

                val macHeaderContent: @Composable () -> Unit = {
                    if (PlatformUtils.isMac && useCustomTitleBar) {
                        WindowDraggableArea(
                            modifier = Modifier.fillMaxWidth(),
                            onDoubleClick = { windowController.onMaximizeToggle.invoke() }
                        ) {
                            Box(
                                modifier = Modifier.padding(
                                    start = ToolkitTheme.spacing.small,
                                    top = ToolkitTheme.spacing.extraSmall,
                                    bottom = ToolkitTheme.spacing.small
                                )
                            ) {
                                MacWindowControls(controller = windowController)
                            }
                        }
                    }
                }

                NavigationSidebar(
                    title = Res.string.app_name.localized,
                    bodySections = sections,
                    bottomSections = bottomSections,
                    currentScreen = currentScreen,
                    onScreenSelected = onScreenSelected,
                    isNavbarCollapsed = isCollapsed,
                    onToggleNavbar = {
                        val nextCollapsed = !isCollapsed
                        isCollapsed = nextCollapsed
                        onToggleNavbarState?.invoke(nextCollapsed)
                    },
                    containerColor = shellColor,
                    headerContent = macHeaderContent,
                    modifier = Modifier.fillMaxHeight()
                )

                ToastHost(
                    notificationService = notificationService,
                    settings = settings.notifications
                )
                DialogHost(dialogService = dialogService)
            }
        }
    }
}
