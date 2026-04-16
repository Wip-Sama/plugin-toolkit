package com.wip.cmp_desktop_test.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cmp_desktop_test.composeapp.generated.resources.*
import com.wip.cmp_desktop_test.ui.components.SidebarElement
import com.wip.cmp_desktop_test.ui.components.SidebarSection
import com.wip.cmp_desktop_test.ui.components.SidebarSectionData
import org.jetbrains.compose.resources.stringResource

/**
 * Internal route keys for the Settings master-detail navigation.
 * These are separate from [Screen] which drives the top-level app navigation.
 */
sealed interface SettingRoute {
    data object AccentColor : SettingRoute
    data object Theme       : SettingRoute
    data object Language    : SettingRoute
    data object Scaling     : SettingRoute
    data object ModuleRepo  : SettingRoute
    data object About       : SettingRoute
}

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var activeRoute: Any by remember { mutableStateOf(SettingRoute.AccentColor) }

    val applicationSection = SidebarSectionData(
        title = Res.string.section_application,
        elements = listOf(
            SidebarElement(SettingRoute.AccentColor, Icons.Default.Palette,    Res.string.setting_accent_color),
            SidebarElement(SettingRoute.Theme,       Icons.Default.DarkMode,   Res.string.setting_theme),
            SidebarElement(SettingRoute.Language,    Icons.Default.Language,   Res.string.setting_language),
            SidebarElement(SettingRoute.Scaling,     Icons.Default.FormatSize, Res.string.setting_scaling)
        )
    )

    val moduleSection = SidebarSectionData(
        title = Res.string.section_module_repo,
        elements = listOf(
            SidebarElement(SettingRoute.ModuleRepo, Icons.Default.Inventory, Res.string.section_module_repo)
        )
    )

    val aboutSection = SidebarSectionData(
        title = Res.string.section_about,
        elements = listOf(
            SidebarElement(SettingRoute.About, Icons.Default.Info, Res.string.section_about)
        )
    )

    Row(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // ── Left: internal settings sidebar ─────────────────────────────────
        Surface(
            modifier = Modifier.width(250.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(0.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Text(
                    text = stringResource(Res.string.settings),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
                )

                SidebarSection(
                    section = applicationSection,
                    currentSelection = activeRoute,
                    onItemSelected = { activeRoute = it }
                )
                Spacer(modifier = Modifier.height(16.dp))

                SidebarSection(
                    section = moduleSection,
                    currentSelection = activeRoute,
                    onItemSelected = { activeRoute = it }
                )

                Spacer(modifier = Modifier.weight(1f))

                SidebarSection(
                    section = aboutSection,
                    currentSelection = activeRoute,
                    onItemSelected = { activeRoute = it }
                )
            }
        }

        // ── Right: detail panel ──────────────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(24.dp)
        ) {
            val titleText = when (activeRoute) {
                SettingRoute.AccentColor -> stringResource(Res.string.setting_accent_color)
                SettingRoute.Theme       -> stringResource(Res.string.setting_theme)
                SettingRoute.Language    -> stringResource(Res.string.setting_language)
                SettingRoute.Scaling     -> stringResource(Res.string.setting_scaling)
                SettingRoute.ModuleRepo  -> stringResource(Res.string.section_module_repo)
                SettingRoute.About       -> stringResource(Res.string.section_about)
                else                     -> "Settings"
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = MaterialTheme.shapes.medium
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Controls for $titleText will appear here.")
                }
            }
        }
    }
}
