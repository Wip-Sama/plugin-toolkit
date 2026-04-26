package com.wip.kpm_cpm_wotoolkit.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.wip.kpm_cpm_wotoolkit.shared.components.sidebar.*
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
    @Serializable data object ModuleRepo   : SettingNavKey
    @Serializable data object ModuleManager   : SettingNavKey
    @Serializable data object About        : SettingNavKey
}

/** Nav3 configuration for Settings internal navigation. */
val SettingNavConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(SettingNavKey.Appearance::class, SettingNavKey.Appearance.serializer())
            subclass(SettingNavKey.ModuleRepo::class, SettingNavKey.ModuleRepo.serializer())
            subclass(SettingNavKey.About::class, SettingNavKey.About.serializer())
        }
    }
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() }
) {
    val backStack = rememberNavBackStack(SettingNavConfig, SettingNavKey.Appearance as SettingNavKey)
    val currentKey = backStack.lastOrNull() ?: SettingNavKey.Appearance

    val interfaceSection = SidebarSectionData(
        title = Res.string.section_application,
        elements = listOf(
            SidebarElement(SettingNavKey.Appearance, Icons.Default.Palette, Res.string.setting_appearance),
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
        Surface(
            modifier = Modifier.width(250.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Text(
                    text = stringResource(Res.string.settings),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
                )

                SidebarSection(
                    section = interfaceSection,
                    currentSelection = currentKey,
                    onItemSelected = { key ->
                        val route = key as SettingNavKey
                        if (backStack.lastOrNull() != route) {
                            backStack.removeAll { it == route }
                            backStack.add(route)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))

                SidebarSection(
                    section = moduleSection,
                    currentSelection = currentKey,
                    onItemSelected = { key ->
                        val route = key as SettingNavKey
                        if (backStack.lastOrNull() != route) {
                            backStack.removeAll { it == route }
                            backStack.add(route)
                        }
                    }
                )

                Spacer(modifier = Modifier.weight(1f))

                SidebarSection(
                    section = aboutSection,
                    currentSelection = currentKey,
                    onItemSelected = { key ->
                        val route = key as SettingNavKey
                        if (backStack.lastOrNull() != route) {
                            backStack.removeAll { it == route }
                            backStack.add(route)
                        }
                    }
                )
            }
        }

        // ── Right: detail panel ──────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(24.dp)
        ) {
            val titleText = when (currentKey) {
                SettingNavKey.Appearance   -> stringResource(Res.string.setting_appearance)
                SettingNavKey.ModuleRepo   -> stringResource(Res.string.section_modules)
                SettingNavKey.About        -> stringResource(Res.string.section_about)
                else                       -> "Error"
            }

            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
                onBack = { if (backStack.size > 1) backStack.removeLast() }
            ) { key ->
                when (key) {
                    SettingNavKey.Appearance   -> NavEntry(key) { AppearanceSettingsView(viewModel) }
                    SettingNavKey.ModuleRepo   -> NavEntry(key) { PlaceholderView("Module Repository") }
                    SettingNavKey.About        -> NavEntry(key) { PlaceholderView("About This App") }
                    else                       -> NavEntry(key) { PlaceholderView("Error") }
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

