package org.wip.plugintoolkit.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.wip.plugintoolkit.core.model.localized
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
import org.wip.plugintoolkit.ui.titlebar.CustomTitleBar
import org.wip.plugintoolkit.ui.titlebar.LocalWindowController
import org.wip.plugintoolkit.ui.titlebar.MacWindowControls
import org.wip.plugintoolkit.ui.titlebar.TitleBarLeftOffsetSync
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.app_name

@Composable
fun AppScaffold(
    settings: AppSettings,
    sections: List<org.wip.plugintoolkit.shared.components.sidebar.SidebarSectionData<Screen>>,
    bottomSections: List<org.wip.plugintoolkit.shared.components.sidebar.SidebarSectionData<Screen>>,
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    notificationService: NotificationService,
    dialogService: DialogService,
    onToggleNavbarState: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val initialCollapsed = remember {
        when (settings.appearance.sidebarStartMode) {
            SidebarStartMode.Expanded -> false
            SidebarStartMode.Collapsed -> true
            SidebarStartMode.Remember -> settings.appearance.isSidebarCollapsed
        }
    }
    var isNavbarCollapsed by remember { mutableStateOf(initialCollapsed) }
    val layoutSidebarWidth by animateDpAsState(
        targetValue = if (isNavbarCollapsed) ToolkitTheme.dimensions.sidebarCollapsedWidth else ToolkitTheme.dimensions.sidebarExpandedWidth,
        animationSpec = tween(durationMillis = 200)
    )

    val useCustomTitleBar = settings.appearance.useCustomTitleBar
    val windowController = LocalWindowController.current

    if (useCustomTitleBar) {
        TitleBarLeftOffsetSync(leftOffsetDp = layoutSidebarWidth.value.toInt())
    }

    val scaffoldBgColor = if (useCustomTitleBar) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.background
    }

    Surface(modifier = Modifier.fillMaxSize(), color = scaffoldBgColor) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.width(layoutSidebarWidth))
                if (useCustomTitleBar) {
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        CustomTitleBar(
                            modifier = Modifier.fillMaxWidth(),
                            showTitleAndIcon = false
                        )
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(
                                topStart = ToolkitTheme.dimensions.contentCanvasCornerRadius
                            ),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                content()
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        content()
                    }
                }
            }

            val macHeaderContent: (@Composable () -> Unit)? = if (useCustomTitleBar && PlatformUtils.isMac && windowController != null) {
                {
                    Box(modifier = Modifier.padding(bottom = ToolkitTheme.spacing.small)) {
                        MacWindowControls(controller = windowController)
                    }
                }
            } else null

            NavigationSidebar(
                title = Res.string.app_name.localized,
                bodySections = sections,
                bottomSections = bottomSections,
                currentScreen = currentScreen,
                onScreenSelected = onScreenSelected,
                isNavbarCollapsed = isNavbarCollapsed,
                onToggleNavbar = {
                    val newCollapsed = !isNavbarCollapsed
                    isNavbarCollapsed = newCollapsed
                    onToggleNavbarState?.invoke(newCollapsed)
                },
                headerContent = macHeaderContent ?: {},
                modifier = Modifier.fillMaxHeight()
            )

            ToastHost(
                notificationService = notificationService, settings = settings.notifications
            )
            DialogHost(dialogService)
        }
    }
}

