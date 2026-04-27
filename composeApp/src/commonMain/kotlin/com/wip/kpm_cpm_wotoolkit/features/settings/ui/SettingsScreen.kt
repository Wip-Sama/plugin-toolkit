package com.wip.kpm_cpm_wotoolkit.features.settings.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.wip.kpm_cpm_wotoolkit.core.notification.NotificationType
import com.wip.kpm_cpm_wotoolkit.features.settings.model.AppSettings
import com.wip.kpm_cpm_wotoolkit.features.settings.model.SettingDefinition
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.LocalSettingsRegistry
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.LocalSettingsResolvedStrings
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.LocalSettingsSearchQuery
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.SettingText
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.resolve
import com.wip.kpm_cpm_wotoolkit.features.settings.viewmodel.NotificationViewModel
import com.wip.kpm_cpm_wotoolkit.features.settings.viewmodel.SettingsSearchViewModel
import com.wip.kpm_cpm_wotoolkit.features.settings.viewmodel.SettingsViewModel
import com.wip.kpm_cpm_wotoolkit.shared.components.settings.SettingsGroup
import com.wip.kpm_cpm_wotoolkit.shared.components.settings.SettingsItem
import com.wip.kpm_cpm_wotoolkit.shared.components.sidebar.NavigationSidebar
import com.wip.kpm_cpm_wotoolkit.shared.components.sidebar.SidebarElement
import com.wip.kpm_cpm_wotoolkit.shared.components.sidebar.SidebarSectionData
import com.wip.kpm_cpm_wotoolkit.features.repository.ui.ModuleRepoView
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kpm_cpm_wotoolkit.composeapp.generated.resources.Res
import kpm_cpm_wotoolkit.composeapp.generated.resources.nav_notification_history
import kpm_cpm_wotoolkit.composeapp.generated.resources.section_about
import kpm_cpm_wotoolkit.composeapp.generated.resources.section_application
import kpm_cpm_wotoolkit.composeapp.generated.resources.section_modules
import kpm_cpm_wotoolkit.composeapp.generated.resources.section_modules_manager
import kpm_cpm_wotoolkit.composeapp.generated.resources.section_modules_repositories
import kpm_cpm_wotoolkit.composeapp.generated.resources.section_system
import kpm_cpm_wotoolkit.composeapp.generated.resources.setting_appearance
import kpm_cpm_wotoolkit.composeapp.generated.resources.setting_open_log_folder
import kpm_cpm_wotoolkit.composeapp.generated.resources.settings
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

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
    data object ModuleRepo : SettingNavKey
    @Serializable
    data object ModuleManager : SettingNavKey
    @Serializable
    data object NotificationHistory : SettingNavKey
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
            subclass(SettingNavKey.ModuleRepo::class, SettingNavKey.ModuleRepo.serializer())
            subclass(SettingNavKey.ModuleManager::class, SettingNavKey.ModuleManager.serializer())
            subclass(SettingNavKey.NotificationHistory::class, SettingNavKey.NotificationHistory.serializer())
            subclass(SettingNavKey.About::class, SettingNavKey.About.serializer())
            subclass(SettingNavKey.BroadSearch::class, SettingNavKey.BroadSearch.serializer())
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinInject(),
    searchViewModel: SettingsSearchViewModel = koinInject(),
    notificationViewModel: NotificationViewModel = koinInject()
) {
    // Start with Appearance — showing settings immediately
    val backStack = rememberNavBackStack(SettingNavConfig, SettingNavKey.Appearance as SettingNavKey)
    val currentKey = backStack.lastOrNull() ?: SettingNavKey.BroadSearch
    val searchQuery = searchViewModel.searchQuery
    val allDefinitions by searchViewModel.allDefinitions.collectAsState()
    val registry = searchViewModel.registry

    // Resolve all settings strings to avoid @Composable issues in ViewModel logic
    val resolvedStrings = allDefinitions.flatMap {
        listOfNotNull(it.title, it.subtitle, it.sectionTitle)
    }.distinct().associateWith { it.resolve() }

    // Build control overrides for definitions that need ViewModel-dependent controls
    val testNotificationOverride: @Composable (AppSettings, (AppSettings) -> Unit) -> Unit = { _, _ ->
            Row {
                IconButton(onClick = { notificationViewModel.testSystemNotification(NotificationType.Info) }) {
                    Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = { notificationViewModel.testSystemNotification(NotificationType.Warning) }) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = androidx.compose.ui.graphics.Color(0xFFFFA500)
                    )
                }
                IconButton(onClick = { notificationViewModel.testSystemNotification(NotificationType.Error) }) {
                    Icon(Icons.Default.Error, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                }
                IconButton(onClick = { notificationViewModel.testToastNotification() }) {
                    Icon(Icons.Default.ChatBubble, contentDescription = "Toast")
                }
            }
        }

    // Find the "Test Notifications" definition's hashCode for the override
    val testNotifDef = allDefinitions.find {
        it.title == SettingText.Raw("Test Notifications")
    }
    val controlOverrides = buildMap {
        testNotifDef?.let { put(it.hashCode(), testNotificationOverride) }
    }

    // Build action overrides for ActionSettings that need ViewModel-level callbacks
    val openLogDef = allDefinitions.find {
        it.title == SettingText.Resource(Res.string.setting_open_log_folder) && it is SettingDefinition.ActionSetting
    }
    val actionOverrides = buildMap {
        openLogDef?.let { put(it.hashCode()) { viewModel.openLogFolder() } }
    }

    val applicationSection = SidebarSectionData(
        title = Res.string.section_application, elements = listOf(
            SidebarElement(SettingNavKey.Appearance, Icons.Default.Palette, Res.string.setting_appearance),
            SidebarElement(SettingNavKey.SystemSettings, Icons.Default.Settings, Res.string.section_system),
            SidebarElement(
                SettingNavKey.NotificationHistory,
                Icons.Default.History,
                Res.string.nav_notification_history
            ),
        )
    )

    val moduleSection = SidebarSectionData(
        title = Res.string.section_modules, elements = listOf(
            SidebarElement(SettingNavKey.ModuleManager, Icons.Default.Inventory, Res.string.section_modules),
            SidebarElement(SettingNavKey.ModuleRepo, Icons.Default.Inventory, Res.string.section_modules_repositories)
        )
    )

    val aboutSection = SidebarSectionData(
        title = Res.string.section_about, elements = listOf(
            SidebarElement(SettingNavKey.About, Icons.Default.Info, Res.string.section_about)
        )
    )

    Row(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ── Left: internal settings sidebar ─────────────────────────────────
        NavigationSidebar(
            title = Res.string.settings,
            bodySections = listOf(applicationSection, moduleSection),
            bottomSections = listOf(aboutSection),
            currentScreen = currentKey,
            onScreenSelected = { route ->
                if (backStack.lastOrNull() != route) {
                    backStack.add(route)
                }
            },
            isNavbarCollapsed = false,
            onToggleNavbar = {},
            canCollapse = false,
            headerContent = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchViewModel.searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    placeholder = { Text("Search settings...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            })

        // ── Right: detail panel ──────────────────────────────────────────────
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp)
        ) {
            val titleText = when (currentKey) {
                SettingNavKey.Appearance -> stringResource(Res.string.setting_appearance)
                SettingNavKey.SystemSettings -> stringResource(Res.string.section_system)
                SettingNavKey.NotificationHistory -> stringResource(Res.string.nav_notification_history)
                SettingNavKey.ModuleRepo -> stringResource(Res.string.section_modules_repositories)
                SettingNavKey.ModuleManager -> stringResource(Res.string.section_modules_manager)
                SettingNavKey.About -> stringResource(Res.string.section_about)
                SettingNavKey.BroadSearch -> stringResource(Res.string.settings)
                else -> "Error"
            }

            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            CompositionLocalProvider(
                LocalSettingsSearchQuery provides searchQuery,
                LocalSettingsRegistry provides allDefinitions,
                LocalSettingsResolvedStrings provides resolvedStrings
            ) {
                val currentSettingKey = currentKey as? SettingNavKey ?: SettingNavKey.BroadSearch
                val hasLocalMatches = searchViewModel.hasLocalMatches(currentSettingKey, allDefinitions, resolvedStrings)

                if (!hasLocalMatches && currentKey != SettingNavKey.BroadSearch) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("No results found in ${titleText}", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            if (backStack.lastOrNull() != SettingNavKey.BroadSearch) {
                                backStack.add(SettingNavKey.BroadSearch)
                            }
                        }) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Search in whole settings")
                        }
                    }
                } else {
                    NavDisplay(
                        backStack = backStack,
                        modifier = Modifier.fillMaxSize(),
                        onBack = { if (backStack.size > 1) backStack.removeLast() }) { key ->
                        when (key) {
                            // ── Auto-generated pages ──────────────────────
                            SettingNavKey.Appearance -> NavEntry(key) {
                                AutoSettingsView(
                                    navKey = SettingNavKey.Appearance,
                                    viewModel = viewModel,
                                    registry = registry,
                                    controlOverrides = controlOverrides,
                                    actionOverrides = actionOverrides
                                )
                            }

                            SettingNavKey.SystemSettings -> NavEntry(key) {
                                AutoSettingsView(
                                    navKey = SettingNavKey.SystemSettings,
                                    viewModel = viewModel,
                                    registry = registry,
                                    controlOverrides = controlOverrides,
                                    actionOverrides = actionOverrides
                                )
                            }

                            SettingNavKey.ModuleManager -> NavEntry(key) {
                                AutoSettingsView(
                                    navKey = SettingNavKey.ModuleManager,
                                    viewModel = viewModel,
                                    registry = registry,
                                    controlOverrides = controlOverrides,
                                    actionOverrides = actionOverrides
                                )
                            }

                            // ── Custom pages (parallel system) ────────────
                            SettingNavKey.NotificationHistory -> NavEntry(key) { NotificationHistoryView() }
                            SettingNavKey.ModuleRepo -> NavEntry(key) { ModuleRepoView() }
                            SettingNavKey.About -> NavEntry(key) { PlaceholderView("About This App") }

                            // ── Broad search (initial entry state) ────────
                            SettingNavKey.BroadSearch -> NavEntry(key) {
                                BroadSearchResultsView(
                                    searchQuery = searchQuery,
                                    allDefinitions = allDefinitions,
                                    searchViewModel = searchViewModel,
                                    resolvedStrings = resolvedStrings,
                                    onNavigate = { targetKey ->
                                        if (backStack.lastOrNull() != targetKey) {
                                            backStack.add(targetKey)
                                        }
                                    })
                            }

                            else -> NavEntry(key) { PlaceholderView("Error") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BroadSearchResultsView(
    searchQuery: String,
    allDefinitions: List<SettingDefinition>,
    searchViewModel: SettingsSearchViewModel,
    resolvedStrings: Map<SettingText, String>,
    onNavigate: (SettingNavKey) -> Unit
) {
    // We now show all settings when blank, handled by the ViewModel

    val grouped = searchViewModel.getBroadSearchResults(allDefinitions, resolvedStrings)

    if (grouped.isEmpty()) {
        PlaceholderView("No results found for \"$searchQuery\"")
        return
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        grouped.forEach { (sectionName, items) ->
            SettingsGroup(title = sectionName) {
                items.forEach { definition ->
                    SettingsItem(
                        title = resolvedStrings[definition.title] ?: "",
                        subtitle = definition.subtitle?.let { resolvedStrings[it] },
                        icon = definition.icon,
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
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape = MaterialTheme.shapes.medium
            ), contentAlignment = Alignment.Center
    ) {
        Text("Content for $text will appear here.", style = MaterialTheme.typography.bodyLarge)
    }
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    MaterialTheme {
        SettingsScreen()
    }
}
