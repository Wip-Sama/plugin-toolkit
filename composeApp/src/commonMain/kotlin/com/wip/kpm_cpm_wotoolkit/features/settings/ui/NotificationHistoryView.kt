package com.wip.kpm_cpm_wotoolkit.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wip.kpm_cpm_wotoolkit.core.notification.NotificationRecord
import com.wip.kpm_cpm_wotoolkit.core.notification.NotificationType
import com.wip.kpm_cpm_wotoolkit.features.settings.viewmodel.SettingsViewModel
import kpm_cpm_wotoolkit.composeapp.generated.resources.Res
import kpm_cpm_wotoolkit.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NotificationHistoryView(viewModel: SettingsViewModel) {
    val history by viewModel.notificationHistory.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = { viewModel.clearNotificationHistory() },
                enabled = history.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.history_clear_all))
            }
        }

        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.history_empty), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(history, key = { it.id }) { record ->
                    NotificationItem(
                        record = record,
                        onDelete = { viewModel.removeNotificationItem(record.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(
    record: NotificationRecord,
    onDelete: () -> Unit
) {
    val typeColor = when (record.type) {
        NotificationType.Info -> MaterialTheme.colorScheme.primary
        NotificationType.Warning -> Color(0xFFFFA500)
        NotificationType.Error -> MaterialTheme.colorScheme.error
    }

    val typeIcon = when (record.type) {
        NotificationType.Info -> Icons.Default.Info
        NotificationType.Warning -> Icons.Default.Warning
        NotificationType.Error -> Icons.Default.Error
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(typeColor.copy(alpha = 0.1f), shape = MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Icon(typeIcon, contentDescription = null, tint = typeColor)
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = record.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatTimestamp(record.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = record.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Delete",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val formatter = DateTimeFormatter.ofPattern("MMM dd, HH:mm:ss")
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}
