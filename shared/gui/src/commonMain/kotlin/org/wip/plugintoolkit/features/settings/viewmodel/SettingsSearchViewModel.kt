package org.wip.plugintoolkit.features.settings.viewmodel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import org.wip.plugintoolkit.core.model.resolveNonComposable
import org.wip.plugintoolkit.features.settings.model.SettingDefinition
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey
import org.wip.plugintoolkit.features.settings.utils.SettingText
import org.wip.plugintoolkit.features.settings.utils.SettingsRegistry
import org.wip.plugintoolkit.features.shortcuts.logic.DefaultShortcutCatalog
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutAction
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutInputMode

class SettingsSearchViewModel(
    val registry: SettingsRegistry,
    val shortcutActions: List<ShortcutAction> = DefaultShortcutCatalog.actions
) : ViewModel() {

    val allDefinitions = registry.definitions

    var searchQuery by mutableStateOf("")

    fun hasLocalMatches(
        currentKey: SettingNavKey,
        definitions: List<SettingDefinition>,
        resolvedStrings: Map<SettingText, String>
    ): Boolean {
        if (searchQuery.isBlank() || currentKey == SettingNavKey.BroadSearch || currentKey == SettingNavKey.NotificationHistory) {
            return true
        }
        if (currentKey == SettingNavKey.Shortcuts) {
            val query = searchQuery.trim()
            val matchesPriority = "priority".contains(query, ignoreCase = true) ||
                    "resolution".contains(query, ignoreCase = true) ||
                    "z-index".contains(query, ignoreCase = true) ||
                    "elevation".contains(query, ignoreCase = true)
            if (matchesPriority) return true

            return shortcutActions.any { action ->
                val title = action.title.resolveNonComposable()
                val desc = action.description.resolveNonComposable()
                title.contains(query, ignoreCase = true) ||
                        desc.contains(query, ignoreCase = true) ||
                        action.situation.displayLabel.contains(query, ignoreCase = true) ||
                        action.defaultTriggers.any { it.format().contains(query, ignoreCase = true) }
            }
        }
        val currentMatches = definitions.filter {
            it.navKey == currentKey &&
                    ((resolvedStrings[it.title] ?: "").contains(searchQuery, ignoreCase = true) ||
                            (it.subtitle != null && (resolvedStrings[it.subtitle] ?: "").contains(
                                searchQuery,
                                ignoreCase = true
                            )))
        }
        return currentMatches.isNotEmpty()
    }

    fun getBroadSearchResults(
        definitions: List<SettingDefinition>,
        resolvedStrings: Map<SettingText, String>
    ): Map<String, List<SettingDefinition>> {
        val searchPool = definitions.filter { it.navKey != SettingNavKey.NotificationHistory && it.navKey != SettingNavKey.Shortcuts }

        val matches = if (searchQuery.isBlank()) {
            searchPool
        } else {
            searchPool.filter {
                (resolvedStrings[it.title] ?: "").contains(searchQuery, ignoreCase = true) ||
                        (it.subtitle != null && (resolvedStrings[it.subtitle] ?: "").contains(
                            searchQuery,
                            ignoreCase = true
                        ))
            }
        }

        val matchingShortcuts = if (searchQuery.isBlank()) {
            emptyList()
        } else {
            val query = searchQuery.trim()
            shortcutActions.filter { action ->
                val title = action.title.resolveNonComposable()
                val desc = action.description.resolveNonComposable()
                title.contains(query, ignoreCase = true) ||
                        desc.contains(query, ignoreCase = true) ||
                        action.situation.displayLabel.contains(query, ignoreCase = true) ||
                        action.defaultTriggers.any { it.format().contains(query, ignoreCase = true) }
            }.map { action ->
                val titleStr = action.title.resolveNonComposable()
                val triggerStr = action.defaultTriggers.joinToString(" / ") { it.format() }
                val descStr = action.description.resolveNonComposable()
                val subtitleStr = if (descStr.isNotBlank()) "$descStr • $triggerStr" else triggerStr
                val icon = when (action.inputMode) {
                    ShortcutInputMode.Pointer -> Icons.Default.Mouse
                    ShortcutInputMode.Keyboard -> Icons.Default.Keyboard
                    ShortcutInputMode.Hybrid -> Icons.Default.Tune
                }
                val sectionStr = "Shortcuts: ${action.situation.displayLabel}"
                SettingDefinition.ActionSetting(
                    id = "shortcut.${action.id}",
                    title = SettingText.Raw(titleStr),
                    subtitle = SettingText.Raw(subtitleStr),
                    icon = icon,
                    sectionTitle = SettingText.Raw(sectionStr),
                    navKey = SettingNavKey.Shortcuts,
                    onClick = {}
                )
            }
        }

        val combined = matches + matchingShortcuts
        return combined.groupBy { def ->
            resolvedStrings[def.sectionTitle] ?: (def.sectionTitle as? SettingText.Raw)?.text ?: ""
        }
    }
}
