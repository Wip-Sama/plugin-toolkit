package com.wip.kpm_cpm_wotoolkit.features.settings.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.wip.kpm_cpm_wotoolkit.features.settings.model.SettingDefinition
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.SettingsRegistry
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.SettingText
import com.wip.kpm_cpm_wotoolkit.features.settings.ui.SettingNavKey

class SettingsSearchViewModel(
    val registry: SettingsRegistry
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
        val currentMatches = definitions.filter { 
            it.navKey == currentKey && 
            ((resolvedStrings[it.title] ?: "").contains(searchQuery, ignoreCase = true) || 
             (it.subtitle != null && (resolvedStrings[it.subtitle] ?: "").contains(searchQuery, ignoreCase = true))) 
        }
        return currentMatches.isNotEmpty()
    }
    
    fun getBroadSearchResults(
        definitions: List<SettingDefinition>,
        resolvedStrings: Map<SettingText, String>
    ): Map<String, List<SettingDefinition>> {
        val searchPool = definitions.filter { it.navKey != SettingNavKey.NotificationHistory }
        
        val matches = if (searchQuery.isBlank()) {
            searchPool
        } else {
            searchPool.filter {
                (resolvedStrings[it.title] ?: "").contains(searchQuery, ignoreCase = true) ||
                (it.subtitle != null && (resolvedStrings[it.subtitle] ?: "").contains(searchQuery, ignoreCase = true))
            }
        }

        return matches.groupBy { resolvedStrings[it.sectionTitle] ?: "" }
    }
}
