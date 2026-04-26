package com.wip.kpm_cpm_wotoolkit.features.settings.utils

import androidx.compose.runtime.Composable
import com.wip.kpm_cpm_wotoolkit.features.settings.ui.SettingNavKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * An abstraction to handle both Compose StringResources (for static UI)
 * and plain Strings (for dynamic module settings).
 */
sealed interface SettingText {
    data class Resource(val res: StringResource) : SettingText
    data class Raw(val text: String) : SettingText
}

@Composable
fun SettingText.resolve(): String {
    return when (this) {
        is SettingText.Resource -> stringResource(res)
        is SettingText.Raw -> text
    }
}

/**
 * Metadata for a single searchable setting.
 */
data class SearchableSetting(
    val title: SettingText,
    val subtitle: SettingText?,
    val sectionTitle: SettingText,
    val navKey: SettingNavKey
)

/**
 * A central registry to hold all searchable settings.
 * This allows modules to dynamically register their settings.
 */
class SettingsRegistry {
    private val _settings = MutableStateFlow<List<SearchableSetting>>(emptyList())
    val settings: StateFlow<List<SearchableSetting>> = _settings.asStateFlow()

    fun register(newSettings: List<SearchableSetting>) {
        _settings.value = _settings.value + newSettings
    }

    fun unregister(navKey: SettingNavKey) {
        _settings.value = _settings.value.filter { it.navKey != navKey }
    }
    
    fun clear() {
        _settings.value = emptyList()
    }
}
