package com.wip.kpm_cpm_wotoolkit.features.settings.utils

import androidx.compose.runtime.Composable
import com.wip.kpm_cpm_wotoolkit.features.settings.ui.SettingNavKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kpm_cpm_wotoolkit.composeapp.generated.resources.Res
import kpm_cpm_wotoolkit.composeapp.generated.resources.*

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

    init {
        registerDefaultSettings()
    }

    private fun registerDefaultSettings() {
        val staticSettings = listOf(
            SearchableSetting(SettingText.Resource(Res.string.setting_theme), SettingText.Raw("Choose between System, Light, Dark or Amoled"), SettingText.Resource(Res.string.setting_appearance), SettingNavKey.Appearance),
            SearchableSetting(SettingText.Raw("Follow System Accent"), SettingText.Raw("Automatically use the accent color from your operating system"), SettingText.Resource(Res.string.setting_appearance), SettingNavKey.Appearance),
            SearchableSetting(SettingText.Resource(Res.string.setting_accent_color), SettingText.Raw("Manually select the accent color for the application"), SettingText.Resource(Res.string.setting_appearance), SettingNavKey.Appearance),
            SearchableSetting(SettingText.Resource(Res.string.setting_scaling), SettingText.Raw("Scale the UI elements"), SettingText.Resource(Res.string.setting_appearance), SettingNavKey.Appearance),
            
            SearchableSetting(SettingText.Resource(Res.string.setting_use_system_language), SettingText.Resource(Res.string.setting_use_system_language_subtitle), SettingText.Resource(Res.string.section_localization), SettingNavKey.Appearance),
            SearchableSetting(SettingText.Resource(Res.string.setting_language), SettingText.Raw("Select your preferred language"), SettingText.Resource(Res.string.section_localization), SettingNavKey.Appearance),
            SearchableSetting(SettingText.Raw("Use System Timezone"), SettingText.Raw("Automatically detect your local timezone from the system"), SettingText.Resource(Res.string.section_localization), SettingNavKey.Appearance),
            SearchableSetting(SettingText.Resource(Res.string.setting_timezone), SettingText.Raw("Manual timezone selection"), SettingText.Resource(Res.string.section_localization), SettingNavKey.Appearance),

            SearchableSetting(SettingText.Resource(Res.string.setting_launch_at_startup), SettingText.Resource(Res.string.setting_launch_at_startup_subtitle), SettingText.Resource(Res.string.section_system), SettingNavKey.SystemSettings),
            SearchableSetting(SettingText.Resource(Res.string.setting_launch_minimized_at_startup), SettingText.Resource(Res.string.setting_launch_minimized_at_startup_subtitle), SettingText.Resource(Res.string.section_system), SettingNavKey.SystemSettings),
            SearchableSetting(SettingText.Resource(Res.string.setting_start_minimized), SettingText.Resource(Res.string.setting_start_minimized_subtitle), SettingText.Resource(Res.string.section_system), SettingNavKey.SystemSettings),
            SearchableSetting(SettingText.Resource(Res.string.setting_close_to_tray), SettingText.Resource(Res.string.setting_close_to_tray_subtitle), SettingText.Resource(Res.string.section_system), SettingNavKey.SystemSettings),

            SearchableSetting(SettingText.Resource(Res.string.setting_log_level), SettingText.Resource(Res.string.setting_log_level_subtitle), SettingText.Resource(Res.string.section_logging), SettingNavKey.SystemSettings),
            SearchableSetting(SettingText.Resource(Res.string.setting_logs_to_keep), SettingText.Resource(Res.string.setting_logs_to_keep_subtitle), SettingText.Resource(Res.string.section_logging), SettingNavKey.SystemSettings),
            SearchableSetting(SettingText.Resource(Res.string.setting_compress_old_logs), SettingText.Resource(Res.string.setting_compress_old_logs_subtitle), SettingText.Resource(Res.string.section_logging), SettingNavKey.SystemSettings),
            SearchableSetting(SettingText.Resource(Res.string.setting_compressed_logs_to_keep), SettingText.Resource(Res.string.setting_compressed_logs_to_keep_subtitle), SettingText.Resource(Res.string.section_logging), SettingNavKey.SystemSettings),
            SearchableSetting(SettingText.Resource(Res.string.setting_open_log_folder), SettingText.Resource(Res.string.setting_open_log_folder_subtitle), SettingText.Resource(Res.string.section_logging), SettingNavKey.SystemSettings),

            SearchableSetting(SettingText.Resource(Res.string.setting_enable_toasts), null, SettingText.Resource(Res.string.section_toasts), SettingNavKey.SystemSettings),
            SearchableSetting(SettingText.Resource(Res.string.setting_toast_auto_dismiss), null, SettingText.Resource(Res.string.section_toasts), SettingNavKey.SystemSettings),
            SearchableSetting(SettingText.Resource(Res.string.setting_toast_dismiss_time), null, SettingText.Resource(Res.string.section_toasts), SettingNavKey.SystemSettings),

            SearchableSetting(SettingText.Resource(Res.string.setting_enable_system_notifications), null, SettingText.Resource(Res.string.section_notifications), SettingNavKey.SystemSettings),
            SearchableSetting(SettingText.Resource(Res.string.setting_show_info), null, SettingText.Resource(Res.string.section_notifications), SettingNavKey.SystemSettings),
            SearchableSetting(SettingText.Resource(Res.string.setting_show_warning), null, SettingText.Resource(Res.string.section_notifications), SettingNavKey.SystemSettings),
            SearchableSetting(SettingText.Resource(Res.string.setting_show_error), null, SettingText.Resource(Res.string.section_notifications), SettingNavKey.SystemSettings),
            SearchableSetting(SettingText.Raw("Test Notifications"), SettingText.Raw("Trigger test notifications to verify behavior"), SettingText.Resource(Res.string.section_notifications), SettingNavKey.SystemSettings)
        )
        register(staticSettings)
    }

    fun register(newSettings: List<SearchableSetting>) {
        _settings.value += newSettings
    }

    fun unregister(navKey: SettingNavKey) {
        _settings.value = _settings.value.filter { it.navKey != navKey }
    }
    
    fun clear() {
        _settings.value = emptyList()
    }
}
