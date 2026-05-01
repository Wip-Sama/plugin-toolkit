package com.wip.kpm_cpm_wotoolkit.core.notification

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface NotificationService {
    val events: SharedFlow<NotificationEvent>
    val history: StateFlow<List<NotificationRecord>>

    fun notify(title: String, message: String, type: NotificationType = NotificationType.Info)
    fun toast(message: String, isNotification: Boolean = true)

    fun clearHistory()
    fun removeHistoryItem(id: String)
}
