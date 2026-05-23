package org.wip.plugintoolkit.core.notification

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.wip.plugintoolkit.core.model.LocalizedString

interface NotificationService {
    val events: SharedFlow<NotificationEvent>
    val history: StateFlow<List<NotificationRecord>>

    fun notify(title: String, message: String, type: NotificationType = NotificationType.Info)
    fun toast(message: String, isNotification: Boolean = true)
    // Prefer using LocalizedString for messages that should remain up-to-date when language changes.
    fun toast(message: LocalizedString, isNotification: Boolean = true)

    fun clearHistory()
    fun removeHistoryItem(id: String)
}
