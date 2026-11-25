package org.wip.plugintoolkit.features.settings.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upgrade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
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
import org.wip.plugintoolkit.core.utils.PlatformVerticalScrollbar
import org.wip.plugintoolkit.features.plugin.ui.ChangelogContent
import org.wip.plugintoolkit.shared.components.ToolkitLinearProgressIndicator
import org.wip.plugintoolkit.shared.components.ToolkitStatusBadge
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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

    val motion = MaterialTheme.motionScheme

    Dialog(
        onDismissRequest = if (isDownloading) ({}) else onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .widthIn(min = ToolkitTheme.dimensions.dialogUpdateWidth, max = ToolkitTheme.dimensions.settingsContentMaxWidth)
                .heightIn(
                    min = ToolkitTheme.dimensions.dialogUpdateHeight,
                    max = ToolkitTheme.dimensions.dialogUpdateMaxHeight
                ),
            shape = ToolkitTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = ToolkitTheme.dimensions.elevationHigh,
            border = BorderStroke(
                width = ToolkitTheme.dimensions.borderThin,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ToolkitTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(ToolkitTheme.dimensions.updateIconContainerSize)
                            .clip(ToolkitTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Upgrade,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
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
                                ToolkitStatusBadge(
                                    text = releaseDate,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }

                // Tonal well for changelog content (no wireframed dividers)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    shape = ToolkitTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest
                ) {
                    if (parsedReleases.isNotEmpty()) {
                        ChangelogContent(
                            versions = parsedReleases,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(ToolkitTheme.spacing.medium),
                            showLevelFilter = parsedReleases.size > 1,
                            useCards = false,
                            showVersionHeader = parsedReleases.size > 1
                        )
                    } else {
                        val notesScroll = rememberScrollState()
                        val scrollbarReserve = if (notesScroll.maxValue > 0) {
                            ToolkitTheme.dimensions.sduiScrollbarEndPadding
                        } else {
                            ToolkitTheme.spacing.none
                        }
                        Box(modifier = Modifier.fillMaxSize()) {
                            SelectionContainer(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(notesScroll)
                                        .padding(
                                            start = ToolkitTheme.spacing.large,
                                            top = ToolkitTheme.spacing.medium,
                                            bottom = ToolkitTheme.spacing.large,
                                            end = ToolkitTheme.spacing.large + scrollbarReserve
                                        )
                                ) {
                                    MarkdownText(
                                        text = updateInfo.changelog ?: stringResource(Res.string.update_no_release_notes),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                            PlatformVerticalScrollbar(
                                scrollState = notesScroll,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isDownloading,
                    enter = expandVertically(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                    exit = shrinkVertically(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec())
                ) {
                    Surface(
                        shape = ToolkitTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(ToolkitTheme.spacing.medium),
                            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(Res.string.update_downloading),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${(progress * 100).toInt().coerceIn(0, 100)}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ToolkitLinearProgressIndicator(
                                progress = if (progress > 0f) progress else null,
                                wavy = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Footer Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                        shape = ToolkitTheme.shapes.pill
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
        shape = ToolkitTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                shape = ToolkitTheme.shapes.pill,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
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
