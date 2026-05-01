package com.wip.kpm_cpm_wotoolkit.core.notification

import co.touchlab.kermit.Logger
import com.wip.kpm_cpm_wotoolkit.core.KeepTrack
import com.wip.kpm_cpm_wotoolkit.features.settings.model.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class JvmNotificationService(
    private val settingsProvider: () -> AppSettings
) : NotificationService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _events = MutableSharedFlow<NotificationEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<NotificationEvent> = _events.asSharedFlow()

    private val _history = MutableStateFlow<List<NotificationRecord>>(emptyList())
    override val history: StateFlow<List<NotificationRecord>> = _history.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val historyFilePath =
        "${System.getProperty("user.home")}/${KeepTrack.SETTINGS_DIR_NAME}/notification_history.json"
    private val historyFile = Path(historyFilePath)

    init {
        loadHistory()
        // Start cleanup task
        scope.launch {
            while (isActive) {
                cleanupOldNotifications()
                delay(1000 * 60 * 60) // Once an hour
            }
        }
    }

    override fun notify(title: String, message: String, type: NotificationType) {
        val settings = settingsProvider()

        val record = NotificationRecord(
            id = UUID.randomUUID().toString(),
            timestamp = Instant.now().toEpochMilli(),
            title = title,
            message = message,
            type = type
        )

        // Always record to history (as requested)
        addHistoryItem(record)

        // Only emit event if system notifications are enabled for this type
        if (settings.notifications.enableSystemNotifications) {
            val shouldShow = when (type) {
                NotificationType.Info -> settings.notifications.showInfo
                NotificationType.Warning -> settings.notifications.showWarning
                NotificationType.Error -> settings.notifications.showError
            }
            if (shouldShow) {
                _events.tryEmit(NotificationEvent.System(record))
            }
        }
    }

    override fun toast(message: String, isNotification: Boolean) {
        val settings = settingsProvider()

        // Only skip if it's a notification-toast AND toasts are disabled
        if (isNotification && !settings.notifications.enableToasts) return

        _events.tryEmit(NotificationEvent.Toast(message, isNotification))
    }

    override fun clearHistory() {
        _history.value = emptyList()
        saveHistory()
    }

    override fun removeHistoryItem(id: String) {
        _history.value = _history.value.filterNot { it.id == id }
        saveHistory()
    }

    private fun addHistoryItem(record: NotificationRecord) {
        _history.value = (listOf(record) + _history.value).take(1000) // Cap at 1000 items
        saveHistory()
    }

    private fun loadHistory() {
        try {
            if (SystemFileSystem.exists(historyFile)) {
                val content = SystemFileSystem.source(historyFile).use { it.buffered().readString() }
                _history.value = json.decodeFromString<List<NotificationRecord>>(content)
                cleanupOldNotifications() // Cleanup on load
            }
        } catch (e: Exception) {
            Logger.e(e) { "Error loading notification history" }
        }
    }

    private fun saveHistory() {
        scope.launch {
            try {
                val parent = historyFile.parent
                if (parent != null && !SystemFileSystem.exists(parent)) {
                    SystemFileSystem.createDirectories(parent)
                }
                SystemFileSystem.sink(historyFile)
                    .use { it.buffered().writeString(json.encodeToString(_history.value)) }
            } catch (e: Exception) {
                Logger.e(e) { "Error saving notification history" }
            }
        }
    }

    private fun cleanupOldNotifications() {
        val retentionDays = settingsProvider().notifications.history.retentionDays
        val cutoff = Instant.now().minus(retentionDays.toLong(), ChronoUnit.DAYS).toEpochMilli()

        val newHistory = _history.value.filter { it.timestamp >= cutoff }
        if (newHistory.size != _history.value.size) {
            _history.value = newHistory
            saveHistory()
        }
    }
}
