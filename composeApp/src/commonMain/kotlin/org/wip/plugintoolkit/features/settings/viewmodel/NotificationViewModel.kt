package org.wip.plugintoolkit.features.settings.viewmodel

import androidx.lifecycle.ViewModel
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.notification.NotificationType

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
