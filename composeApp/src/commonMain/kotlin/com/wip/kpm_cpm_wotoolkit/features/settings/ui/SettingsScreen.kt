package com.wip.kpm_cpm_wotoolkit.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kpm_cpm_wotoolkit.composeapp.generated.resources.*
import com.wip.kpm_cpm_wotoolkit.features.settings.viewmodel.SettingsViewModel
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.*
import com.wip.kpm_cpm_wotoolkit.shared.components.sidebar.*
import com.wip.kpm_cpm_wotoolkit.shared.components.settings.*
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import androidx.navigation3.runtime.NavKey
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import androidx.compose.ui.tooling.preview.Preview

/**
 * Internal route keys for the Settings master-detail navigation.
 */
@Serializable
sealed interface SettingNavKey : NavKey {
    @Serializable data object Appearance  : SettingNavKey
    @Serializable data object SystemSettings : SettingNavKey
    @Serializable data object ModuleRepo   : SettingNavKey
    @Serializable data object ModuleManager   : SettingNavKey
    @Serializable data object NotificationHistory : SettingNavKey
    @Serializable data object About        : SettingNavKey
    @Serializable data object BroadSearch  : SettingNavKey
}

/** Nav3 configuration for Settings internal navigation. */
val SettingNavConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(SettingNavKey.Appearance::class, SettingNavKey.Appearance.serializer())
            subclass(SettingNavKey.SystemSettings::class, SettingNavKey.SystemSettings.serializer())
            subclass(SettingNavKey.ModuleRepo::class, SettingNavKey.ModuleRepo.serializer())
            subclass(SettingNavKey.NotificationHistory::class, SettingNavKey.NotificationHistory.serializer())
            subclass(SettingNavKey.About::class, SettingNavKey.About.serializer())
            subclass(SettingNavKey.BroadSearch::class, SettingNavKey.BroadSearch.serializer())
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() }
) {
    val backStack = rememberNavBackStack(SettingNavConfig, SettingNavKey.BroadSearch as SettingNavKey)
    val currentKey = backStack.lastOrNull() ?: SettingNavKey.BroadSearch
    val searchQuery = viewModel.searchQuery
    val allSettings by viewModel.settingsRegistry.settings.collectAsState()

    val interfaceSection = SidebarSectionData(
        title = Res.string.section_application,
        elements = listOf(
            SidebarElement(SettingNavKey.Appearance, Icons.Default.Palette, Res.string.setting_appearance),
            SidebarElement(SettingNavKey.SystemSettings, Icons.Default.Settings, Res.string.section_system),
            SidebarElement(SettingNavKey.NotificationHistory, Icons.Default.History, Res.string.nav_notification_history),
        )
    )

    val moduleSection = SidebarSectionData(
        title = Res.string.section_modules,
        elements = listOf(
            SidebarElement(SettingNavKey.ModuleManager, Icons.Default.Inventory, Res.string.section_modules),
            SidebarElement(SettingNavKey.ModuleRepo, Icons.Default.Inventory, Res.string.section_modules_repositories)
        )
    )

    val aboutSection = SidebarSectionData(
        title = Res.string.section_about,
        elements = listOf(
            SidebarElement(SettingNavKey.About, Icons.Default.Info, Res.string.section_about)
        )
    )

    Row(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ── Left: internal settings sidebar ─────────────────────────────────
        NavigationSidebar(
            title = Res.string.settings,
            bodySections = listOf(interfaceSection, moduleSection),
            bottomSections = listOf(aboutSection),
            currentScreen = currentKey,
            onScreenSelected = { key ->
                val route = key as SettingNavKey
                if (backStack.lastOrNull() != route) {
                    backStack.removeAll { it == route }
                    backStack.add(route)
                }
            },
            isNavbarCollapsed = false,
            onToggleNavbar = {},
            canCollapse = false,
            headerContent = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    placeholder = { Text("Search settings...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            }
        )

        // ── Right: detail panel ──────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(24.dp)
        ) {
            val titleText = when (currentKey) {
                SettingNavKey.Appearance   -> stringResource(Res.string.setting_appearance)
                SettingNavKey.SystemSettings -> stringResource(Res.string.section_system)
                SettingNavKey.NotificationHistory -> stringResource(Res.string.nav_notification_history)
                SettingNavKey.ModuleRepo   -> stringResource(Res.string.section_modules)
                SettingNavKey.About        -> stringResource(Res.string.section_about)
                SettingNavKey.BroadSearch  -> "Global Search"
                else                       -> "Error"
            }

            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            CompositionLocalProvider(LocalSettingsSearchQuery provides searchQuery) {
                // Determine if we should show the "search globally" button
                var hasLocalMatches by remember { mutableStateOf(true) }
                
                if (searchQuery.isNotBlank() && currentKey != SettingNavKey.BroadSearch && currentKey != SettingNavKey.NotificationHistory) {
                    val currentMatches = allSettings.filter { 
                        it.navKey == currentKey && 
                        (it.title.resolve().contains(searchQuery, ignoreCase = true) || 
                         it.subtitle?.resolve()?.contains(searchQuery, ignoreCase = true) == true) 
                    }
                    hasLocalMatches = currentMatches.isNotEmpty()
                } else {
                    hasLocalMatches = true
                }

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
                                backStack.removeAll { it == SettingNavKey.BroadSearch }
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
                        onBack = { if (backStack.size > 1) backStack.removeLast() }
                    ) { key ->
                        when (key) {
                            SettingNavKey.Appearance   -> NavEntry(key) { AppearanceSettingsView(viewModel) }
                            SettingNavKey.SystemSettings -> NavEntry(key) { SystemSettingsView(viewModel) }
                            SettingNavKey.NotificationHistory -> NavEntry(key) { NotificationHistoryView(viewModel) }
                            SettingNavKey.ModuleRepo   -> NavEntry(key) { PlaceholderView("Module Repository") }
                            SettingNavKey.About        -> NavEntry(key) { PlaceholderView("About This App") }
                            SettingNavKey.BroadSearch  -> NavEntry(key) { 
                                BroadSearchResultsView(
                                    searchQuery = searchQuery, 
                                    allSettings = allSettings, 
                                    onNavigate = { targetKey ->
                                        if (backStack.lastOrNull() != targetKey) {
                                            backStack.removeAll { it == targetKey }
                                            backStack.add(targetKey)
                                        }
                                    }
                                ) 
                            }
                            else                       -> NavEntry(key) { PlaceholderView("Error") }
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
    allSettings: List<SearchableSetting>,
    onNavigate: (SettingNavKey) -> Unit
) {
    if (searchQuery.isBlank()) {
        PlaceholderView("Type to search across all settings")
        return
    }

    // Filter out Notification History
    val searchPool = allSettings.filter { it.navKey != SettingNavKey.NotificationHistory }
    
    val matches = searchPool.filter {
        it.title.resolve().contains(searchQuery, ignoreCase = true) ||
        it.subtitle?.resolve()?.contains(searchQuery, ignoreCase = true) == true
    }

    if (matches.isEmpty()) {
        PlaceholderView("No results found for \"$searchQuery\"")
        return
    }

    // Group by section
    val grouped = matches.groupBy { it.sectionTitle.resolve() }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        grouped.forEach { (sectionName, items) ->
            SettingsGroup(title = sectionName) {
                items.forEach { setting ->
                    SettingsItem(
                        title = setting.title.resolve(),
                        subtitle = setting.subtitle?.resolve(),
                        icon = Icons.Default.Search,
                        onClick = {
                            onNavigate(setting.navKey)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PlaceholderView(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.medium
            ),
        contentAlignment = Alignment.Center
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

