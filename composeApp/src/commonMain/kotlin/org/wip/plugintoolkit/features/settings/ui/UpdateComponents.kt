package org.wip.plugintoolkit.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.ui.MarkdownText
import org.wip.plugintoolkit.core.update.UpdateInfo
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.update_available_title
import plugintoolkit.composeapp.generated.resources.update_changelog_title
import plugintoolkit.composeapp.generated.resources.update_downloading
import plugintoolkit.composeapp.generated.resources.update_later
import plugintoolkit.composeapp.generated.resources.update_now
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import plugintoolkit.composeapp.generated.resources.*

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    isDownloading: Boolean,
    progress: Float,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = if (isDownloading) ({}) else onDismiss,
        title = {
            Text(
                stringResource(Res.string.update_available_title, updateInfo.version),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                Text(
                    stringResource(Res.string.update_changelog_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = ToolkitTheme.spacing.small)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.divider))
                        .padding(ToolkitTheme.spacing.mediumSmall)
                ) {
                    SelectionContainer {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            MarkdownText(
                                text = updateInfo.changelog ?: "No release notes available.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(Res.string.update_downloading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small)
                        )
                        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraSmall))
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDownload,
                enabled = !isDownloading
            ) {
                Text(stringResource(Res.string.update_now))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDownloading
            ) {
                Text(stringResource(Res.string.update_later))
            }
        }
    )
}

@Composable
fun UpdateConfirmationDialog(
    runningJobsCount: Int,
    onConfirm: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(Res.string.update_jobs_running),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Text(
                "There are $runningJobsCount jobs currently running. Updating now will stop all active jobs. Do you want to proceed?",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(true) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error).run {
                    androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                }
            ) {
                Text(stringResource(Res.string.update_stop_jobs))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.update_later))
            }
        }
    )
}

@Composable
fun TestNotificationButtons(viewModel: org.wip.plugintoolkit.features.settings.viewmodel.NotificationViewModel) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(ToolkitTheme.spacing.small),
        modifier = Modifier.fillMaxWidth().padding(top = ToolkitTheme.spacing.small)
    ) {
        Button(
            onClick = { viewModel.testSystemNotification(org.wip.plugintoolkit.core.notification.NotificationType.Info) },
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(Res.string.test_info))
        }
        Button(
            onClick = { viewModel.testSystemNotification(org.wip.plugintoolkit.core.notification.NotificationType.Error) },
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(Res.string.test_error))
        }
        Button(
            onClick = { viewModel.testToastNotification() },
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(Res.string.test_toast))
        }
    }
}
