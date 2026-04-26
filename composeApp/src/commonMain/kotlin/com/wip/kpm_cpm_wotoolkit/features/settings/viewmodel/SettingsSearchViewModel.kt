package com.wip.kpm_cpm_wotoolkit.features.settings.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.SearchableSetting
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.SettingsRegistry
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.SettingText
import com.wip.kpm_cpm_wotoolkit.features.settings.ui.SettingNavKey

class SettingsSearchViewModel(
    val registry: SettingsRegistry
) : ViewModel() {
    
    val allSettings = registry.settings

    var searchQuery by mutableStateOf("")

    fun hasLocalMatches(
        currentKey: SettingNavKey, 
        settings: List<SearchableSetting>,
        resolvedStrings: Map<SettingText, String>
    ): Boolean {
        if (searchQuery.isBlank() || currentKey == SettingNavKey.BroadSearch || currentKey == SettingNavKey.NotificationHistory) {
            return true
        }
        val currentMatches = settings.filter { 
            it.navKey == currentKey && 
            ((resolvedStrings[it.title] ?: "").contains(searchQuery, ignoreCase = true) || 
             (it.subtitle != null && (resolvedStrings[it.subtitle] ?: "").contains(searchQuery, ignoreCase = true))) 
        }
        return currentMatches.isNotEmpty()
    }
    
    fun getBroadSearchResults(
        settings: List<SearchableSetting>,
        resolvedStrings: Map<SettingText, String>
    ): Map<String, List<SearchableSetting>> {
        if (searchQuery.isBlank()) return emptyMap()

        val searchPool = settings.filter { it.navKey != SettingNavKey.NotificationHistory }
        val matches = searchPool.filter {
            (resolvedStrings[it.title] ?: "").contains(searchQuery, ignoreCase = true) ||
            (it.subtitle != null && (resolvedStrings[it.subtitle] ?: "").contains(searchQuery, ignoreCase = true))
        }

        return matches.groupBy { resolvedStrings[it.sectionTitle] ?: "" }
    }
}
