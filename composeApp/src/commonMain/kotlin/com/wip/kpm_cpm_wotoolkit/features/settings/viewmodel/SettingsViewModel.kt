package com.wip.kpm_cpm_wotoolkit.features.settings.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wip.kpm_cpm_wotoolkit.features.settings.logic.SettingsRepository
import com.wip.kpm_cpm_wotoolkit.features.settings.model.AppSettings
import kotlinx.coroutines.launch
import com.wip.kpm_cpm_wotoolkit.core.notification.NotificationService
import com.wip.kpm_cpm_wotoolkit.core.notification.NotificationType
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.SettingsRegistry
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.SearchableSetting
import com.wip.kpm_cpm_wotoolkit.features.settings.utils.SettingText
import com.wip.kpm_cpm_wotoolkit.features.settings.ui.SettingNavKey
import kpm_cpm_wotoolkit.composeapp.generated.resources.Res
import kpm_cpm_wotoolkit.composeapp.generated.resources.*
import org.koin.mp.KoinPlatform

class SettingsViewModel(
    private val repository: SettingsRepository = SettingsRepository(),
) : ViewModel() {
    
    val notificationService: NotificationService by lazy { KoinPlatform.getKoin().get() }
    val notificationHistory by lazy { notificationService.history }
    val settingsRegistry: SettingsRegistry by lazy { KoinPlatform.getKoin().get() }
    
    var settings by mutableStateOf(repository.loadSettings())
        private set

    var searchQuery by mutableStateOf("")

    init {
        registerStaticSettings()
    }

    private fun registerStaticSettings() {
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
        // Clear before registering to avoid duplicates if ViewModel is recreated
        settingsRegistry.clear()
        settingsRegistry.register(staticSettings)
    }

    fun updateSettings(update: (AppSettings) -> AppSettings) {
        settings = update(settings)
        save()
    }

    private fun save() {
        viewModelScope.launch {
            repository.saveSettings(settings)
        }
    }

    fun openLogFolder() {
        repository.openLogFolder()
    }

    fun clearNotificationHistory() {
        notificationService.clearHistory()
    }

    fun removeNotificationItem(id: String) {
        notificationService.removeHistoryItem(id)
    }

    fun testSystemNotification(type: NotificationType) {
        notificationService.notify("Test Notification", "This is a test ${type.name} notification", type)
    }

    fun testToastNotification() {
        notificationService.toast("This is a test toast notification")
    }
}
