package org.wip.plugintoolkit.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AppBarRow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import org.wip.plugintoolkit.shared.components.AdaptiveAppBarOverflowButton
import org.wip.plugintoolkit.shared.components.AdaptiveBackNavigationButton
import org.wip.plugintoolkit.shared.components.AdaptiveConstrainedContent
import org.wip.plugintoolkit.shared.components.AdaptivePaneHeader
import org.wip.plugintoolkit.shared.components.DesktopPaneDragHandle
import org.wip.plugintoolkit.shared.components.adaptiveOuterHorizontalPadding
import org.wip.plugintoolkit.shared.components.rememberAdaptivePaneScaffoldDirective
import org.wip.plugintoolkit.shared.components.rememberFixedListPaneExpansionState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation3.runtime.NavEntry
import kotlinx.coroutines.delay
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.wip.plugintoolkit.ui.desktopNavTransitionSpec
import org.wip.plugintoolkit.ui.opaqueNavEntry
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wip.plugintoolkit.core.model.localized
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.ui.DialogService
import org.wip.plugintoolkit.features.settings.model.SettingDefinition
import org.wip.plugintoolkit.features.settings.model.SettingsEvent
import org.wip.plugintoolkit.features.settings.model.SettingsToast
import org.wip.plugintoolkit.features.settings.utils.LocalSettingsRegistry
import org.wip.plugintoolkit.features.settings.utils.LocalSettingsResolvedStrings
import org.wip.plugintoolkit.features.settings.utils.LocalSettingsSearchQuery
import org.wip.plugintoolkit.features.settings.utils.SettingText
import org.wip.plugintoolkit.features.settings.utils.resolve
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsSearchViewModel
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsViewModel
import org.wip.plugintoolkit.features.shortcuts.logic.ShortcutManager
import org.wip.plugintoolkit.features.shortcuts.ui.ShortcutsSettingsView
import plugintoolkit.composeapp.generated.resources.nav_shortcuts
import plugintoolkit.composeapp.generated.resources.shortcuts_reset_all
import plugintoolkit.composeapp.generated.resources.shortcuts_reset_all_confirm
import plugintoolkit.composeapp.generated.resources.shortcuts_title
import org.wip.plugintoolkit.shared.components.settings.SettingsGroup
import org.wip.plugintoolkit.shared.components.settings.SettingsItem
import org.wip.plugintoolkit.shared.components.settings.getGroupedShape
import org.wip.plugintoolkit.shared.components.sidebar.NavigationSidebar
import org.wip.plugintoolkit.shared.components.sidebar.SidebarElement
import org.wip.plugintoolkit.shared.components.sidebar.SidebarSectionData
import org.wip.plugintoolkit.shared.components.verticalFadingEdges
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.nav_notification_history
import plugintoolkit.composeapp.generated.resources.section_about
import plugintoolkit.composeapp.generated.resources.section_general
import plugintoolkit.composeapp.generated.resources.section_plugins_manager
import plugintoolkit.composeapp.generated.resources.section_plugins_repositories
import plugintoolkit.composeapp.generated.resources.section_system
import plugintoolkit.composeapp.generated.resources.setting_appearance
import plugintoolkit.composeapp.generated.resources.settings_global_search_notice
import plugintoolkit.composeapp.generated.resources.settings_no_global_results
import plugintoolkit.composeapp.generated.resources.settings
import plugintoolkit.composeapp.generated.resources.settings_feature_placeholder
import plugintoolkit.composeapp.generated.resources.settings_no_results
import plugintoolkit.composeapp.generated.resources.settings_search_placeholder
import plugintoolkit.composeapp.generated.resources.settings_search_whole
import plugintoolkit.composeapp.generated.resources.update_check_failed
import plugintoolkit.composeapp.generated.resources.update_check_started
import plugintoolkit.composeapp.generated.resources.update_new_version_found
import plugintoolkit.composeapp.generated.resources.update_no_updates

/**
 * Internal route keys for the Settings master-detail navigation.
 */
@Serializable
sealed interface SettingNavKey : NavKey {
    @Serializable
    data object Appearance : SettingNavKey

    @Serializable
    data object SystemSettings : SettingNavKey

    @Serializable
    data object PluginRepo : SettingNavKey

    @Serializable
    data object PluginManager : SettingNavKey

    @Serializable
    data object NotificationHistory : SettingNavKey

    @Serializable
    data object Shortcuts : SettingNavKey

    @Serializable
    data object About : SettingNavKey

    @Serializable
    data object BroadSearch : SettingNavKey
}

/** Nav3 configuration for Settings internal navigation. */
val SettingNavConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(SettingNavKey.Appearance::class, SettingNavKey.Appearance.serializer())
            subclass(SettingNavKey.SystemSettings::class, SettingNavKey.SystemSettings.serializer())
            subclass(SettingNavKey.PluginRepo::class, SettingNavKey.PluginRepo.serializer())
            subclass(SettingNavKey.PluginManager::class, SettingNavKey.PluginManager.serializer())
            subclass(SettingNavKey.NotificationHistory::class, SettingNavKey.NotificationHistory.serializer())
            subclass(SettingNavKey.Shortcuts::class, SettingNavKey.Shortcuts.serializer())
            subclass(SettingNavKey.About::class, SettingNavKey.About.serializer())
            subclass(SettingNavKey.BroadSearch::class, SettingNavKey.BroadSearch.serializer())
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3AdaptiveApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinInject(),
    searchViewModel: SettingsSearchViewModel = koinInject(),
    notificationService: NotificationService = koinInject(),
    shortcutManager: ShortcutManager = koinInject(),
    dialogService: DialogService = koinInject()
) {
    val settings by viewModel.settings.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowToast -> {
                    val message = when (event.toast) {
                        SettingsToast.UpdateCheckStarted -> getString(Res.string.update_check_started)
                        SettingsToast.UpdateNewVersionFound -> getString(
                            Res.string.update_new_version_found,
                            *event.args.toTypedArray()
                        )

                        SettingsToast.UpdateNoUpdates -> getString(Res.string.update_no_updates)
                        SettingsToast.UpdateCheckFailed -> getString(Res.string.update_check_failed)
                    }
                    notificationService.toast(message)
                }
            }
        }
    }

    val backStack = rememberNavBackStack(SettingNavConfig, SettingNavKey.Appearance as SettingNavKey)
    val currentKey = backStack.lastOrNull() ?: SettingNavKey.BroadSearch
    val searchQuery = searchViewModel.searchQuery
    val allDefinitions by searchViewModel.allDefinitions.collectAsState()
    val registry = searchViewModel.registry
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(50)
        runCatching { searchFocusRequester.requestFocus() }
    }

    // Resolve all settings strings to avoid @Composable issues in ViewModel logic
    val resolvedStrings = allDefinitions.flatMap {
        listOfNotNull(it.title, it.subtitle, it.sectionTitle)
    }.distinct().associateWith { it.resolve() }

    val applicationSection = SidebarSectionData(
        title = Res.string.section_general.localized, elements = listOf(
            SidebarElement(SettingNavKey.Appearance, Icons.Default.Palette, Res.string.setting_appearance.localized),
            SidebarElement(SettingNavKey.SystemSettings, Icons.Default.Settings, Res.string.section_system.localized),
            SidebarElement(SettingNavKey.Shortcuts, Icons.Default.Keyboard, Res.string.nav_shortcuts.localized),
            SidebarElement(
                SettingNavKey.NotificationHistory,
                Icons.Default.History,
                Res.string.nav_notification_history.localized
            ),
        )
    )

    val aboutSection = SidebarSectionData(
        title = Res.string.section_about.localized, elements = listOf(
            SidebarElement(SettingNavKey.About, Icons.Default.Info, Res.string.section_about.localized)
        )
    )

    val scope = rememberCoroutineScope()
    val directive = rememberAdaptivePaneScaffoldDirective()
    val navigator = rememberListDetailPaneScaffoldNavigator<SettingNavKey>(scaffoldDirective = directive)
    val paneExpansionState = rememberFixedListPaneExpansionState(
        initialWidth = ToolkitTheme.dimensions.adaptiveListPaneWidth
    )

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        modifier = modifier
            .fillMaxSize()
            .padding(
                horizontal = adaptiveOuterHorizontalPadding(),
                vertical = ToolkitTheme.spacing.medium
            )
            .onPreviewKeyEvent { event ->
                if (event.isCtrlPressed && event.key == Key.F && event.type == KeyEventType.KeyDown) {
                    searchFocusRequester.requestFocus()
                    true
                } else false
            },
        paneExpansionState = paneExpansionState,
        paneExpansionDragHandle = { state ->
            DesktopPaneDragHandle(paneExpansionState = state)
        },
        listPane = {
            AnimatedPane(
                modifier = Modifier.preferredWidth(ToolkitTheme.dimensions.adaptiveListPaneWidth)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = ToolkitTheme.shapes.large,
                    modifier = Modifier.fillMaxSize()
                ) {
                    NavigationSidebar(
                        title = Res.string.settings.localized,
                        bodySections = listOf(applicationSection),
                        bottomSections = listOf(aboutSection),
                        currentScreen = currentKey,
                        onScreenSelected = { route ->
                            if (backStack.lastOrNull() != route) {
                                backStack.add(route)
                            }
                            if (route is SettingNavKey) {
                                scope.launch {
                                    navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, route)
                                }
                            }
                        },
                        isNavbarCollapsed = false,
                        onToggleNavbar = {},
                        canCollapse = false,
                        expandedWidth = Dp.Unspecified,
                        headerContent = {
                            org.wip.plugintoolkit.shared.components.ToolkitTextField(
                                value = searchQuery,
                                onValueChange = { searchViewModel.searchQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = ToolkitTheme.spacing.medium)
                                    .focusRequester(searchFocusRequester),
                                placeholder = { Text(stringResource(Res.string.settings_search_placeholder)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = stringResource(Res.string.settings_search_placeholder)
                                    )
                                },
                                singleLine = true
                            )
                        }
                    )
                }
            }
        },
        detailPane = {
            AnimatedPane {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = ToolkitTheme.shapes.large,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = ToolkitTheme.spacing.large)
                    ) {
                        val titleText = when (currentKey) {
                            SettingNavKey.Appearance -> stringResource(Res.string.setting_appearance)
                            SettingNavKey.SystemSettings -> stringResource(Res.string.section_system)
                            SettingNavKey.Shortcuts -> stringResource(Res.string.shortcuts_title)
                            SettingNavKey.NotificationHistory -> stringResource(Res.string.nav_notification_history)
                            SettingNavKey.PluginRepo -> stringResource(Res.string.section_plugins_repositories)
                            SettingNavKey.PluginManager -> stringResource(Res.string.section_plugins_manager)
                            SettingNavKey.About -> stringResource(Res.string.section_about)
                            SettingNavKey.BroadSearch -> stringResource(Res.string.settings)
                            else -> "Error"
                        }

                        AdaptivePaneHeader(
                            title = titleText,
                            navigationIcon = if (navigator.canNavigateBack()) {
                                {
                                    AdaptiveBackNavigationButton(
                                        onClick = { scope.launch { navigator.navigateBack() } }
                                    )
                                }
                            } else null,
                            actions = {
                                if (currentKey == SettingNavKey.Shortcuts) {
                                    val resetTitle = stringResource(Res.string.shortcuts_reset_all)
                                    val resetMsg = stringResource(Res.string.shortcuts_reset_all_confirm)
                                    val hasCustomBindings = settings.shortcuts.customBindings.isNotEmpty() ||
                                        settings.shortcuts.customRelativePriorities.isNotEmpty()
                                    AppBarRow(
                                        maxItemCount = 1,
                                        overflowIndicator = { menuState ->
                                            AdaptiveAppBarOverflowButton(menuState = menuState)
                                        }
                                    ) {
                                        clickableItem(
                                            onClick = {
                                                dialogService.showConfirmation(
                                                    title = resetTitle,
                                                    message = resetMsg,
                                                    onConfirm = { shortcutManager.resetAllToDefaults() }
                                                )
                                            },
                                            enabled = hasCustomBindings,
                                            icon = {
                                                Icon(
                                                    imageVector = Icons.Default.Restore,
                                                    contentDescription = resetTitle,
                                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                                )
                                            },
                                            label = resetTitle
                                        )
                                    }
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

                        AdaptiveConstrainedContent(
                            maxWidth = ToolkitTheme.dimensions.sduiFormMaxWidth,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = adaptiveOuterHorizontalPadding())
                        ) {
                            CompositionLocalProvider(
                LocalSettingsSearchQuery provides searchQuery,
                LocalSettingsRegistry provides allDefinitions,
                LocalSettingsResolvedStrings provides resolvedStrings
            ) {
                val currentSettingKey = currentKey as? SettingNavKey ?: SettingNavKey.BroadSearch
                val hasLocalMatches =
                    searchViewModel.hasLocalMatches(currentSettingKey, allDefinitions, resolvedStrings)

                if (!hasLocalMatches && currentKey != SettingNavKey.BroadSearch) {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Surface(
                            shape = ToolkitTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = ToolkitTheme.spacing.medium)
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = ToolkitTheme.spacing.medium,
                                    vertical = ToolkitTheme.spacing.small
                                ),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                )
                                Text(
                                    text = stringResource(Res.string.settings_global_search_notice, titleText),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        BroadSearchResultsView(
                            searchQuery = searchQuery,
                            allDefinitions = allDefinitions,
                            searchViewModel = searchViewModel,
                            resolvedStrings = resolvedStrings,
                            onNavigate = { targetKey ->
                                if (backStack.lastOrNull() != targetKey) {
                                    backStack.add(targetKey)
                                }
                            }
                        )
                    }
                } else {
                    NavDisplay(
                        backStack = backStack,
                        modifier = Modifier.fillMaxSize(),
                        onBack = { if (backStack.size > 1) backStack.removeLast() },
                        transitionSpec = desktopNavTransitionSpec(),
                        popTransitionSpec = desktopNavTransitionSpec()
                    ) { key ->
                        when (key) {
                            SettingNavKey.Appearance -> opaqueNavEntry(key) {
                                AutoSettingsView(key as SettingNavKey, viewModel, registry)
                            }

                            SettingNavKey.SystemSettings -> opaqueNavEntry(key) {
                                AutoSettingsView(key as SettingNavKey, viewModel, registry)
                            }

                            SettingNavKey.Shortcuts -> opaqueNavEntry(key) {
                                ShortcutsSettingsView(
                                    shortcutManager = shortcutManager,
                                    settingsViewModel = viewModel,
                                    dialogService = dialogService
                                )
                            }

                            SettingNavKey.NotificationHistory -> opaqueNavEntry(key) { NotificationHistoryView() }
                            SettingNavKey.About -> opaqueNavEntry(key) { AboutView() }
                            SettingNavKey.BroadSearch -> opaqueNavEntry(key) {
                                BroadSearchResultsView(
                                    searchQuery = searchQuery,
                                    allDefinitions = allDefinitions,
                                    searchViewModel = searchViewModel,
                                    resolvedStrings = resolvedStrings,
                                    onNavigate = { targetKey ->
                                        if (backStack.lastOrNull() != targetKey) {
                                            backStack.add(targetKey)
                                        }
                                        scope.launch {
                                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, targetKey)
                                        }
                                    })
                            }

                            else -> opaqueNavEntry(key) { PlaceholderView("Error") }
                        }
                    }
                }
                        }
                        }
                    }
                }
            }
        }
    )
}


@Composable
fun BroadSearchResultsView(
    searchQuery: String,
    allDefinitions: List<SettingDefinition>,
    searchViewModel: SettingsSearchViewModel,
    resolvedStrings: Map<SettingText, String>,
    onNavigate: (SettingNavKey) -> Unit
) {
    val grouped = searchViewModel.getBroadSearchResults(allDefinitions, resolvedStrings)

    if (grouped.isEmpty()) {
        PlaceholderView(stringResource(Res.string.settings_no_global_results, searchQuery))
        return
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalFadingEdges(
                scrollState = scrollState,
                topFadeLength = ToolkitTheme.spacing.medium,
                bottomFadeLength = ToolkitTheme.spacing.medium
            )
            .verticalScroll(scrollState)
    ) {
        grouped.forEach { (sectionName, items) ->
            SettingsGroup(title = sectionName) {
                items.forEachIndexed { index, definition ->
                    SettingsItem(
                        title = resolvedStrings[definition.title] ?: (definition.title as? SettingText.Raw)?.text ?: "",
                        subtitle = definition.subtitle?.let { resolvedStrings[it] ?: (it as? SettingText.Raw)?.text },
                        icon = definition.icon,
                        shape = getGroupedShape(index, items.size),
                        onClick = {
                            onNavigate(definition.navKey)
                        })
                }
            }
        }
    }
}

@Composable
fun PlaceholderView(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(
            color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium
        ), contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(Res.string.settings_feature_placeholder, text),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    MaterialTheme {
        SettingsScreen()
    }
}
