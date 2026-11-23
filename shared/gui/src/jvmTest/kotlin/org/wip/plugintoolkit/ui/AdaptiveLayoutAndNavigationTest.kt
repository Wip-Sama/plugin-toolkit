package org.wip.plugintoolkit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AppBarRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.wip.plugintoolkit.core.model.LocalizedString
import org.wip.plugintoolkit.core.model.localized
import org.wip.plugintoolkit.core.notification.NotificationEvent
import org.wip.plugintoolkit.core.notification.NotificationRecord
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.notification.NotificationType
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.ui.DialogService
import org.wip.plugintoolkit.features.navigation.model.Screen
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import org.wip.plugintoolkit.features.settings.model.SidebarStartMode
import org.wip.plugintoolkit.shared.components.AdaptiveAppBarOverflowButton
import org.wip.plugintoolkit.shared.components.AdaptiveConstrainedContent
import org.wip.plugintoolkit.shared.components.AdaptivePaneHeader
import org.wip.plugintoolkit.shared.components.adaptiveOuterHorizontalPadding
import org.wip.plugintoolkit.shared.components.rememberAdaptivePaneScaffoldDirective
import org.wip.plugintoolkit.shared.components.sidebar.SidebarElement
import org.wip.plugintoolkit.shared.components.sidebar.SidebarSectionData
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.nav_collapse_rail
import plugintoolkit.composeapp.generated.resources.nav_expand_rail
import plugintoolkit.composeapp.generated.resources.nav_more_options
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdaptiveLayoutAndNavigationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class FakeNotificationService : NotificationService {
        private val _events = MutableSharedFlow<NotificationEvent>()
        override val events: SharedFlow<NotificationEvent> = _events

        private val _history = MutableStateFlow<List<NotificationRecord>>(emptyList())
        override val history: StateFlow<List<NotificationRecord>> = _history

        override fun notify(title: String, message: String, type: NotificationType) = Unit
        override fun toast(message: String, isNotification: Boolean) = Unit
        override fun toast(message: LocalizedString, isNotification: Boolean) = Unit
        override fun clearHistory() = Unit
        override fun removeHistoryItem(id: String) = Unit
    }

    @Test
    fun adaptiveDirectivesAndPaddingMatchWindowSizeClass() {
        var mediumPlusSpacer: Dp = 0.dp
        var mediumPlusOuter: Dp = 0.dp
        var compactOuter: Dp = 0.dp

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    mediumPlusSpacer = rememberAdaptivePaneScaffoldDirective().horizontalPartitionSpacerSize
                    mediumPlusOuter = adaptiveOuterHorizontalPadding()
                }
                CompositionLocalProvider(LocalDensity provides Density(2.5f)) {
                    compactOuter = adaptiveOuterHorizontalPadding()
                }
            }
        }

        assertEquals(24.dp, mediumPlusSpacer)
        assertEquals(24.dp, mediumPlusOuter)
        assertEquals(16.dp, compactOuter)
    }

    @Test
    fun adaptiveConstrainedContentClampsProseWidthTo680Dp() {
        var measuredChildWidthDp = 0f

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                val density = LocalDensity.current
                Box(modifier = Modifier.width(1200.dp)) {
                    AdaptiveConstrainedContent {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { size ->
                                    measuredChildWidthDp = with(density) { size.width.toDp().value }
                                }
                        ) {
                            Text("Constrained content")
                        }
                    }
                }
            }
        }

        assertTrue(measuredChildWidthDp > 0f)
        assertTrue(measuredChildWidthDp <= 680.5f, "Expected <= 680.dp, got $measuredChildWidthDp")
    }

    @Test
    fun appScaffoldDesktopWidthUsesWideRailAndTogglesState() {
        var collapseLabel = ""
        var expandLabel = ""
        var toggledStates = mutableListOf<Boolean>()
        var currentScreen by mutableStateOf<Screen>(Screen.Main)

        val sections = listOf(
            SidebarSectionData(
                title = "Management".localized,
                elements = listOf(
                    SidebarElement(
                        id = Screen.Main,
                        icon = Icons.Default.Home,
                        title = "Main".localized
                    ),
                    SidebarElement(
                        id = Screen.Settings,
                        icon = Icons.Default.Settings,
                        title = "SettingsNav".localized
                    )
                )
            )
        )

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                collapseLabel = stringResource(Res.string.nav_collapse_rail)
                expandLabel = stringResource(Res.string.nav_expand_rail)

                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    AppScaffold(
                        settings = AppSettings(
                            appearance = AppearanceSettings(
                                useCustomTitleBar = false,
                                sidebarStartMode = SidebarStartMode.Expanded,
                                isSidebarCollapsed = false
                            )
                        ),
                        sections = sections,
                        bottomSections = emptyList(),
                        currentScreen = currentScreen,
                        onScreenSelected = { currentScreen = it },
                        notificationService = FakeNotificationService(),
                        dialogService = DialogService(),
                        onToggleNavbarState = { toggledStates.add(it) }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text("Body Content")
                        }
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Main").assertIsDisplayed()
        composeTestRule.onNodeWithText("Body Content").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(collapseLabel).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(collapseLabel).performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf(true), toggledStates)
        composeTestRule.onNodeWithContentDescription(expandLabel).assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("SettingsNav").performClick()
        assertEquals(Screen.Settings, currentScreen)
    }

    @Test
    fun appScaffoldCompactWidthSwitchesToShortNavigationBar() {
        var collapseLabel = ""
        var currentScreen by mutableStateOf<Screen>(Screen.Main)

        val sections = listOf(
            SidebarSectionData(
                title = null,
                elements = listOf(
                    SidebarElement(
                        id = Screen.Main,
                        icon = Icons.Default.Home,
                        title = "Main".localized
                    ),
                    SidebarElement(
                        id = Screen.Settings,
                        icon = Icons.Default.Settings,
                        title = "SettingsNav".localized
                    )
                )
            )
        )

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                collapseLabel = stringResource(Res.string.nav_collapse_rail)

                CompositionLocalProvider(LocalDensity provides Density(2.5f)) {
                    AppScaffold(
                        settings = AppSettings(
                            appearance = AppearanceSettings(
                                useCustomTitleBar = false,
                                sidebarStartMode = SidebarStartMode.Expanded,
                                isSidebarCollapsed = false
                            )
                        ),
                        sections = sections,
                        bottomSections = emptyList(),
                        currentScreen = currentScreen,
                        onScreenSelected = { currentScreen = it },
                        notificationService = FakeNotificationService(),
                        dialogService = DialogService()
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text("Compact Body")
                        }
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Compact Body").assertIsDisplayed()
        composeTestRule.onNodeWithText("SettingsNav").performClick()
        assertEquals(Screen.Settings, currentScreen)
        composeTestRule.onNodeWithContentDescription(collapseLabel).assertDoesNotExist()
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Test
    fun adaptivePaneHeaderAndAppBarRowOverflowTriggerActions() {
        var overflowTooltip = ""
        var overflowActionClicked = false

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                overflowTooltip = stringResource(Res.string.nav_more_options)
                AdaptivePaneHeader(
                    title = "Plugins Header",
                    subtitle = "Manage workspaces",
                    actions = {
                        AppBarRow(
                            maxItemCount = 1,
                            overflowIndicator = { menuState ->
                                AdaptiveAppBarOverflowButton(menuState = menuState)
                            }
                        ) {
                            clickableItem(
                                onClick = {},
                                icon = { Icon(Icons.Default.Refresh, contentDescription = "RefreshAction") },
                                label = "RefreshAction"
                            )
                            clickableItem(
                                onClick = { overflowActionClicked = true },
                                icon = { Icon(Icons.Default.Add, contentDescription = "InstallAction") },
                                label = "InstallAction"
                            )
                        }
                    }
                )
            }
        }

        composeTestRule.onNodeWithText("Plugins Header").assertIsDisplayed()
        composeTestRule.onNodeWithText("Manage workspaces").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(overflowTooltip).performClick()
        composeTestRule.onNodeWithText("InstallAction").performClick()
        assertTrue(overflowActionClicked)
    }
}
