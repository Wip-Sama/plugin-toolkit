package com.wip.kpm_cpm_wotoolkit.core.notification

import kotlinx.serialization.Serializable

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
        val message: String, 
        val isNotification: Boolean = true
    ) : NotificationEvent()
}
