package org.wip.plugintoolkit.features.settings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.utils.ChangelogParser
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.ui.MarkdownText
import org.wip.plugintoolkit.core.update.UpdateInfo
import org.wip.plugintoolkit.features.plugin.ui.ChangelogContent
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.test_error
import plugintoolkit.composeapp.generated.resources.test_info
import plugintoolkit.composeapp.generated.resources.test_toast
import plugintoolkit.composeapp.generated.resources.update_available_title
import plugintoolkit.composeapp.generated.resources.update_changelog_title
import plugintoolkit.composeapp.generated.resources.update_downloading
import plugintoolkit.composeapp.generated.resources.update_jobs_running
import plugintoolkit.composeapp.generated.resources.update_later
import plugintoolkit.composeapp.generated.resources.update_no_release_notes
import plugintoolkit.composeapp.generated.resources.update_now
import plugintoolkit.composeapp.generated.resources.update_stop_jobs

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo,
    isDownloading: Boolean,
    progress: Float,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val parsedReleases = remember(updateInfo.changelog, updateInfo.version) {
        val raw = updateInfo.changelog
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            val parsed = ChangelogParser.parse(raw).releases
            if (parsed.isNotEmpty()) {
                parsed
            } else {
                val withVersion = "Version: ${updateInfo.version}\n$raw"
                val retryParsed = ChangelogParser.parse(withVersion).releases
                if (retryParsed.isNotEmpty() && retryParsed.first().categories.isNotEmpty()) {
                    retryParsed
                } else {
                    emptyList()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = if (isDownloading) ({}) else onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .width(ToolkitTheme.dimensions.dialogUpdateWidth)
                .heightIn(
                    min = ToolkitTheme.dimensions.dialogUpdateHeight,
                    max = ToolkitTheme.dimensions.dialogUpdateMaxHeight
                )
                .clip(MaterialTheme.shapes.extraLarge),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = ToolkitTheme.dimensions.elevationMediumHigh,
            border = BorderStroke(
                width = ToolkitTheme.dimensions.borderThin,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = ToolkitTheme.spacing.large,
                            vertical = ToolkitTheme.spacing.medium
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(ToolkitTheme.dimensions.updateIconContainerSize)
                            .clip(ToolkitTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Upgrade,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumLarge)
                        )
                    }

                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.update_available_title, updateInfo.version),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraSmall))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                        ) {
                            Text(
                                text = stringResource(Res.string.update_changelog_title),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )

                            val releaseDate = parsedReleases.firstOrNull()?.date
                            if (!releaseDate.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = ToolkitTheme.shapes.small
                                ) {
                                    Text(
                                        text = releaseDate,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(
                                            horizontal = ToolkitTheme.spacing.small,
                                            vertical = ToolkitTheme.spacing.extraSmall
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
                )

                // Content area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = ToolkitTheme.spacing.large)
                ) {
                    if (parsedReleases.isNotEmpty()) {
                        ChangelogContent(
                            versions = parsedReleases,
                            modifier = Modifier.fillMaxSize(),
                            showLevelFilter = parsedReleases.size > 1,
                            useCards = false,
                            showVersionHeader = parsedReleases.size > 1
                        )
                    } else {
                        SelectionContainer(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(
                                        top = ToolkitTheme.spacing.medium,
                                        bottom = ToolkitTheme.spacing.large
                                    )
                            ) {
                                MarkdownText(
                                    text = updateInfo.changelog ?: stringResource(Res.string.update_no_release_notes),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                if (isDownloading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ToolkitTheme.spacing.large, vertical = ToolkitTheme.spacing.small)
                    ) {
                        Text(
                            text = stringResource(Res.string.update_downloading),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ToolkitTheme.dimensions.progressIndicatorHeightSmall)
                                .clip(MaterialTheme.shapes.small)
                        )
                        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraSmall))
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
                )

                // Footer Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = ToolkitTheme.spacing.large,
                            vertical = ToolkitTheme.spacing.medium
                        ),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !isDownloading
                    ) {
                        Text(stringResource(Res.string.update_later))
                    }
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                    Button(
                        onClick = onDownload,
                        enabled = !isDownloading,
                        shape = ToolkitTheme.shapes.medium
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                        )
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                        Text(stringResource(Res.string.update_now))
                    }
                }
            }
        }
    }
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
