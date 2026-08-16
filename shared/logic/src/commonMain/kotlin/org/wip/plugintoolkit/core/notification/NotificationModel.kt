package org.wip.plugintoolkit.core.notification

import kotlinx.serialization.Serializable
import org.wip.plugintoolkit.core.model.LocalizedString

@Serializable
enum class NotificationType {
    Info,
    Warning,
    Error
}

@Serializable
data class NotificationRecord(
    val id: String,
    val timestamp: Long,
    val title: String,
    val message: String,
    val type: NotificationType
)

sealed class NotificationEvent {
    data class System(val record: NotificationRecord) : NotificationEvent()
    data class Toast(
        val message: LocalizedString,
        val isNotification: Boolean = true
    ) : NotificationEvent()
}
