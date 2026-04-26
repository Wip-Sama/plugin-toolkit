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
import org.koin.mp.KoinPlatform

class SettingsViewModel(
    private val repository: SettingsRepository = SettingsRepository(),
) : ViewModel() {
    
    val notificationService: NotificationService by lazy { KoinPlatform.getKoin().get() }
    val notificationHistory by lazy { notificationService.history }
    var settings by mutableStateOf(repository.loadSettings())
        private set

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
