package com.wip.kpm_cpm_wotoolkit.features.settings.viewmodel

import androidx.lifecycle.ViewModel
import com.wip.kpm_cpm_wotoolkit.core.notification.NotificationService
import com.wip.kpm_cpm_wotoolkit.core.notification.NotificationType

class NotificationViewModel(
    private val notificationService: NotificationService
) : ViewModel() {

    val history = notificationService.history

    fun clearHistory() {
        notificationService.clearHistory()
    }

    fun removeHistoryItem(id: String) {
        notificationService.removeHistoryItem(id)
    }

    fun testSystemNotification(type: NotificationType) {
        notificationService.notify("Test Notification", "This is a test ${type.name} notification", type)
    }

    fun testToastNotification() {
        notificationService.toast("This is a test toast notification")
    }
}
