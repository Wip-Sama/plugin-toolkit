package org.wip.plugintoolkit.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
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
import org.wip.plugintoolkit.features.navigation.model.Screen
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.shared.components.ToastHost
import org.wip.plugintoolkit.shared.components.sidebar.NavigationSidebar
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
    content: @Composable () -> Unit
) {
    var isNavbarCollapsed by remember { mutableStateOf(false) }
    val layoutSidebarWidth by animateDpAsState(
        targetValue = if (isNavbarCollapsed) ToolkitTheme.dimensions.sidebarCollapsedWidth else ToolkitTheme.dimensions.sidebarExpandedWidth,
        animationSpec = tween(durationMillis = 200)
    )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                Spacer(modifier = Modifier.width(layoutSidebarWidth))
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    content()
                }
            }
            NavigationSidebar(
                title = Res.string.app_name.localized,
                bodySections = sections,
                bottomSections = bottomSections,
                currentScreen = currentScreen,
                onScreenSelected = onScreenSelected,
                isNavbarCollapsed = isNavbarCollapsed,
                onToggleNavbar = { isNavbarCollapsed = !isNavbarCollapsed },
                modifier = Modifier.fillMaxHeight()
            )

            ToastHost(
                notificationService = notificationService, settings = settings.notifications
            )
            DialogHost(dialogService)
        }
    }
}
