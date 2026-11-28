package org.wip.plugintoolkit.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import kotlinx.coroutines.launch
import org.wip.plugintoolkit.core.utils.PlatformHorizontalScrollbar
import org.wip.plugintoolkit.core.utils.PlatformVerticalScrollbar
import org.wip.plugintoolkit.shared.components.AdaptiveBackNavigationButton
import org.wip.plugintoolkit.shared.components.ToolkitAdaptiveListDetailWorkspace
import org.wip.plugintoolkit.shared.components.ToolkitAdaptiveSupportingWorkspace
import plugintoolkit.composeapp.generated.resources.pane_collapse_bottom_panel
import plugintoolkit.composeapp.generated.resources.pane_collapse_sidebar
import plugintoolkit.composeapp.generated.resources.pane_expand_bottom_panel
import plugintoolkit.composeapp.generated.resources.pane_expand_sidebar
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.wip.plugintoolkit.features.navigation.model.ScreenNavConfig
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.unit.LayoutDirection
import org.wip.plugintoolkit.shared.components.AdaptiveAppBarOverflowButton
import org.wip.plugintoolkit.shared.components.AdaptiveConstrainedContent
import org.wip.plugintoolkit.shared.components.AdaptivePaneHeader
import org.wip.plugintoolkit.shared.components.adaptiveOuterHorizontalPadding
import org.wip.plugintoolkit.shared.components.adaptiveWorkspacePadding
import org.wip.plugintoolkit.shared.components.rememberAdaptivePaneScaffoldDirective
import org.wip.plugintoolkit.shared.components.sidebar.SidebarElement
import org.wip.plugintoolkit.shared.components.sidebar.SidebarSectionData
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.nav_back_to_list
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
        var desktopWorkspacePadding = PaddingValues(0.dp)
        var compactWorkspacePadding = PaddingValues(0.dp)

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    mediumPlusSpacer = rememberAdaptivePaneScaffoldDirective().horizontalPartitionSpacerSize
                    mediumPlusOuter = adaptiveOuterHorizontalPadding()
                    desktopWorkspacePadding = adaptiveWorkspacePadding()
                }
                CompositionLocalProvider(LocalDensity provides Density(2.5f)) {
                    compactOuter = adaptiveOuterHorizontalPadding()
                    compactWorkspacePadding = adaptiveWorkspacePadding()
                }
            }
        }

        assertEquals(12.dp, mediumPlusSpacer)
        assertEquals(24.dp, mediumPlusOuter)
        assertEquals(16.dp, compactOuter)
        assertEquals(12.dp, desktopWorkspacePadding.calculateStartPadding(LayoutDirection.Ltr))
        assertEquals(16.dp, desktopWorkspacePadding.calculateEndPadding(LayoutDirection.Ltr))
        assertEquals(12.dp, desktopWorkspacePadding.calculateTopPadding())
        assertEquals(8.dp, compactWorkspacePadding.calculateStartPadding(LayoutDirection.Ltr))
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

    @Test
    fun appScaffoldDesktopPreservesCollapsibleSectionsAndPinnedBottomItems() {
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
                        id = Screen.FlowManager,
                        icon = Icons.Default.Refresh,
                        title = "FlowsNav".localized
                    )
                )
            )
        )
        val bottomSections = listOf(
            SidebarSectionData(
                title = null,
                elements = listOf(
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
                        bottomSections = bottomSections,
                        currentScreen = currentScreen,
                        onScreenSelected = { currentScreen = it },
                        notificationService = FakeNotificationService(),
                        dialogService = DialogService()
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text("Desktop Body")
                        }
                    }
                }
            }
        }

        // Both section items and pinned bottom section item are visible initially
        composeTestRule.onNodeWithText("Main").assertIsDisplayed()
        composeTestRule.onNodeWithText("FlowsNav").assertIsDisplayed()
        composeTestRule.onNodeWithText("SettingsNav").assertIsDisplayed()

        // Collapsing the Management category hides unselected FlowsNav while keeping selected Main visible
        composeTestRule.onNodeWithText("Management").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Main").assertIsDisplayed()
        composeTestRule.onNodeWithText("FlowsNav").assertDoesNotExist()
        composeTestRule.onNodeWithText("SettingsNav").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun collapsedSidebarDelaysHoverExpandAndCancelsOnQuickExit() {
        val sections = listOf(
            SidebarSectionData(
                title = "Management".localized,
                elements = listOf(
                    SidebarElement(
                        id = Screen.Main,
                        icon = Icons.Default.Home,
                        title = "Main".localized
                    )
                )
            )
        )

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    AppScaffold(
                        settings = AppSettings(
                            appearance = AppearanceSettings(
                                useCustomTitleBar = false,
                                sidebarStartMode = SidebarStartMode.Collapsed,
                                isSidebarCollapsed = true
                            )
                        ),
                        sections = sections,
                        bottomSections = emptyList(),
                        currentScreen = Screen.Main,
                        onScreenSelected = {},
                        notificationService = FakeNotificationService(),
                        dialogService = DialogService()
                    ) {
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        // Initially collapsed: text label is not rendered
        composeTestRule.onNodeWithText("Main").assertDoesNotExist()

        // Brief hover (< 250ms) followed by exit must NOT trigger expansion
        composeTestRule.onNodeWithTag("navigation_sidebar").performMouseInput {
            enter(center)
        }
        composeTestRule.mainClock.advanceTimeBy(100L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Main").assertDoesNotExist()

        composeTestRule.onNodeWithTag("navigation_sidebar").performMouseInput {
            exit()
        }
        composeTestRule.mainClock.advanceTimeBy(300L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Main").assertDoesNotExist()

        // Sustained hover (>= 250ms) DOES expand the sidebar and reveal labels
        composeTestRule.onNodeWithTag("navigation_sidebar").performMouseInput {
            enter(center)
        }
        composeTestRule.mainClock.advanceTimeBy(350L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Main").assertIsDisplayed()
    }

    @Test
    fun platformScrollbarsDoNotThrowUnderUnboundedConstraints() {
        var measuredVerticalWidth = -1
        var measuredVerticalHeight = -1
        var measuredHorizontalWidth = -1
        var measuredHorizontalHeight = -1

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                val verticalState = rememberScrollState()
                val horizontalState = rememberScrollState()
                Column {
                    SubcomposeLayout { _ ->
                        val vPlaceables = subcompose("v") {
                            PlatformVerticalScrollbar(
                                scrollState = verticalState,
                                modifier = Modifier.fillMaxHeight()
                            )
                        }.map {
                            it.measure(Constraints(maxWidth = 100, maxHeight = Constraints.Infinity))
                        }
                        measuredVerticalWidth = vPlaceables.firstOrNull()?.width ?: 0
                        measuredVerticalHeight = vPlaceables.firstOrNull()?.height ?: 0
                        layout(10, 10) {}
                    }
                    SubcomposeLayout { _ ->
                        val hPlaceables = subcompose("h") {
                            PlatformHorizontalScrollbar(
                                scrollState = horizontalState,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }.map {
                            it.measure(Constraints(maxWidth = Constraints.Infinity, maxHeight = 100))
                        }
                        measuredHorizontalWidth = hPlaceables.firstOrNull()?.width ?: 0
                        measuredHorizontalHeight = hPlaceables.firstOrNull()?.height ?: 0
                        layout(10, 10) {}
                    }
                }
            }
        }

        assertEquals(0, measuredVerticalWidth)
        assertEquals(0, measuredVerticalHeight)
        assertEquals(0, measuredHorizontalWidth)
        assertEquals(0, measuredHorizontalHeight)
    }

    @Test
    fun adaptiveSupportingWorkspaceCollapsesAndRestoresBottomAndSidePanes() {
        var showPane by mutableStateOf(false)
        var wideWidth by mutableStateOf(500.dp)
        var collapseSideLabel = ""
        var expandSideLabel = ""
        var expandBottomLabel = ""
        var collapseBottomLabel = ""

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                collapseSideLabel = stringResource(Res.string.pane_collapse_sidebar)
                expandSideLabel = stringResource(Res.string.pane_expand_sidebar)
                expandBottomLabel = stringResource(Res.string.pane_expand_bottom_panel)
                collapseBottomLabel = stringResource(Res.string.pane_collapse_bottom_panel)

                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(modifier = Modifier.width(wideWidth).height(600.dp)) {
                        ToolkitAdaptiveSupportingWorkspace(
                            showSupportingPane = showPane,
                            onShowSupportingPaneChange = { showPane = it },
                            collapseTooltip = "",
                            expandTooltip = "",
                            mainPane = { Text("Main Content") },
                            supportingPane = { Text("Bottom Supporting Content") }
                        )
                    }
                }
            }
        }

        // Initially collapsed on narrow width
        composeTestRule.onNodeWithText("Main Content").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bottom Supporting Content").assertDoesNotExist()

        // Expand from bottom divider handle
        composeTestRule.onNodeWithContentDescription(expandBottomLabel).performClick()
        composeTestRule.waitForIdle()
        assertTrue(showPane)
        composeTestRule.onNodeWithText("Bottom Supporting Content").assertIsDisplayed()

        // Collapse again from bottom divider handle
        composeTestRule.onNodeWithContentDescription(collapseBottomLabel).performClick()
        composeTestRule.waitForIdle()
        assertEquals(false, showPane)

        // Wide desktop layout: expand and collapse side supporting pane cleanly
        showPane = true
        wideWidth = 1100.dp
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Bottom Supporting Content").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription(collapseSideLabel).performClick()
        composeTestRule.waitForIdle()
        assertEquals(false, showPane)

        // Retrieve via side divider handle
        composeTestRule.onNodeWithContentDescription(expandSideLabel).performClick()
        composeTestRule.waitForIdle()
        assertTrue(showPane)
        composeTestRule.onNodeWithText("Bottom Supporting Content").assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Test
    fun adaptiveListDetailWorkspaceShowsBackButtonWhenConstrainedToSinglePane() {
        var selectedItem by mutableStateOf<String?>(null)
        var backLabel = ""

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                backLabel = stringResource(Res.string.nav_back_to_list)
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(modifier = Modifier.width(650.dp).height(500.dp)) {
                        val scope = rememberCoroutineScope()
                        ToolkitAdaptiveListDetailWorkspace<String>(
                            listPaneWidth = 300.dp,
                            listPane = { navigator ->
                                Button(onClick = {
                                    selectedItem = "SelectedItem"
                                    scope.launch {
                                        navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, "SelectedItem")
                                    }
                                }) {
                                    Text("Choose Item")
                                }
                            },
                            detailPane = { navigator ->
                                Column(modifier = Modifier.fillMaxSize()) {
                                    if (navigator.canNavigateBack()) {
                                        AdaptiveBackNavigationButton(
                                            onClick = { scope.launch { navigator.navigateBack() } }
                                        )
                                    }
                                    Text(selectedItem ?: "Empty Detail")
                                }
                            }
                        )
                    }
                }
            }
        }

        composeTestRule.onNodeWithText("Choose Item").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("SelectedItem").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(backLabel).assertIsDisplayed()

        // Clicking back button restores list pane in single-column mode
        composeTestRule.onNodeWithContentDescription(backLabel).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Choose Item").assertIsDisplayed()
    }

    @OptIn(ExperimentalMaterial3AdaptiveApi::class)
    @Test
    fun appScaffoldCompactToDesktopTransitionPreservesNavDisplayWithoutLookaheadCrash() {
        var currentDensity by mutableStateOf(Density(2.5f)) // Compact (< 600.dp) initial frame, mimicking 680x480 scaled startup
        var currentScreen by mutableStateOf<Screen>(Screen.Main)
        var recomposedCounterValue by mutableStateOf(0)

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
                CompositionLocalProvider(LocalDensity provides currentDensity) {
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
                        val backStack = rememberNavBackStack(ScreenNavConfig, Screen.Main)
                        NavDisplay(
                            backStack = backStack,
                            modifier = Modifier.fillMaxSize(),
                            onBack = {},
                            transitionSpec = desktopNavTransitionSpec(),
                            popTransitionSpec = desktopNavTransitionSpec()
                        ) { key ->
                            opaqueNavEntry(key) {
                                val persistedState = remember { mutableStateOf(42) }
                                recomposedCounterValue = persistedState.value
                                ToolkitAdaptiveListDetailWorkspace<String>(
                                    listPane = {
                                        Text("Persistent List")
                                    },
                                    detailPane = {
                                        Text("Persistent Detail ${persistedState.value}")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Frame 1: Compact width renders without throwing
        composeTestRule.waitForIdle()
        assertEquals(42, recomposedCounterValue)

        // Frame 2: Jump to Expanded desktop width (mimics startup 680x480 -> 1280x900)
        currentDensity = Density(1f)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Persistent List").assertIsDisplayed()
        composeTestRule.onNodeWithText("Persistent Detail 42").assertIsDisplayed()

        // Frame 3 & 4: Rapidly bounce back to Compact and back to Expanded
        currentDensity = Density(2.5f)
        composeTestRule.waitForIdle()
        currentDensity = Density(1f)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Persistent List").assertIsDisplayed()
        composeTestRule.onNodeWithText("Persistent Detail 42").assertIsDisplayed()
        assertEquals(42, recomposedCounterValue)
    }
}
