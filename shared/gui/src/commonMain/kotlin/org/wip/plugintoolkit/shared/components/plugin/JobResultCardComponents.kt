package org.wip.plugintoolkit.shared.components.plugin

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobProgress
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.job.ui.StatusBadge
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutActionId
import org.wip.plugintoolkit.features.shortcuts.ui.LocalShortcutManager
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_clear
import plugintoolkit.composeapp.generated.resources.action_collapse_terminal
import plugintoolkit.composeapp.generated.resources.action_copy_logs
import plugintoolkit.composeapp.generated.resources.action_delete
import plugintoolkit.composeapp.generated.resources.action_expand_terminal
import plugintoolkit.composeapp.generated.resources.action_export
import plugintoolkit.composeapp.generated.resources.action_force_cancel
import plugintoolkit.composeapp.generated.resources.action_pause
import plugintoolkit.composeapp.generated.resources.action_resume
import plugintoolkit.composeapp.generated.resources.action_toggle_details
import plugintoolkit.composeapp.generated.resources.dialog_cancel
import plugintoolkit.composeapp.generated.resources.flow_console_logs_title
import plugintoolkit.composeapp.generated.resources.flow_output_results_title
import plugintoolkit.composeapp.generated.resources.flow_run_id_label
import plugintoolkit.composeapp.generated.resources.flow_triggered_label
import plugintoolkit.composeapp.generated.resources.job_capability_breakdown_title
import plugintoolkit.composeapp.generated.resources.job_completed_at_label
import plugintoolkit.composeapp.generated.resources.job_copy_logs_success_toast
import plugintoolkit.composeapp.generated.resources.job_duration_label
import plugintoolkit.composeapp.generated.resources.job_execution_result_title
import plugintoolkit.composeapp.generated.resources.job_export_success_toast
import plugintoolkit.composeapp.generated.resources.job_peak_memory_label
import plugintoolkit.composeapp.generated.resources.job_started_at_label
import plugintoolkit.composeapp.generated.resources.job_total_memory_label
import plugintoolkit.composeapp.generated.resources.plugin_executing_progress
import plugintoolkit.composeapp.generated.resources.plugin_execution_id_format
import plugintoolkit.composeapp.generated.resources.setting_auto_scroll

/**
 * Header section of a job result card displaying job identification, type, status, and control icons.
 */
@Composable
internal fun JobResultCardHeader(
    job: BackgroundJob,
    expanded: Boolean,
    hasDetails: Boolean,
    onToggleExpand: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                tint = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))

            val title = if (job.type == JobType.Flow) {
                stringResource(Res.string.flow_run_id_label, job.id.takeLast(12))
            } else {
                stringResource(Res.string.plugin_execution_id_format, job.id)
            }

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(Res.string.flow_triggered_label, job.enqueuedAt.toString()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(job.status)
            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))

            if (onDelete != null) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.action_delete),
                        modifier = Modifier.size(ToolkitTheme.dimensions.circularProgressSize),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (hasDetails) {
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(Res.string.action_toggle_details),
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumLarge),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

/**
 * Progress section showing primary execution progress and sub-capability progresses for active jobs.
 */
@Composable
internal fun JobResultProgressSection(
    job: BackgroundJob,
    progress: JobProgress,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress.mainProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(ToolkitTheme.dimensions.heightSmall)
                .clip(MaterialTheme.shapes.small),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraSmall))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(Res.string.plugin_executing_progress, (progress.mainProgress * 100).toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            ElapsedTimeText(job = job)
        }

        progress.capabilitiesProgress.forEach { (capName, capProg) ->
            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
            Text(
                text = "$capName (${(capProg * 100).toInt()}%)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            LinearProgressIndicator(
                progress = { capProg },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ToolkitTheme.dimensions.capabilityProgressBarHeight)
                    .clip(MaterialTheme.shapes.small),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

/**
 * Outputs and errors display section for completed, failed, or cancelled jobs.
 */
@Composable
internal fun JobResultOutputsSection(
    job: BackgroundJob,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        when (job.status) {
            JobStatus.Completed -> {
                if (job.type == JobType.Flow) {
                    val outputsParsed = remember(job.result) {
                        try {
                            job.result?.let {
                                Json.decodeFromString<Map<String, JsonElement>>(it)
                            } ?: emptyMap()
                        } catch (e: Exception) {
                            emptyMap()
                        }
                    }

                    if (outputsParsed.isNotEmpty()) {
                        Text(
                            text = stringResource(Res.string.flow_output_results_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                        SelectionContainer {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerLow,
                                        MaterialTheme.shapes.medium
                                    )
                                    .padding(ToolkitTheme.spacing.medium)
                            ) {
                                outputsParsed.forEach { (portName, element) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = portName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = when (element) {
                                                is JsonPrimitive -> {
                                                    if (element.isString) element.content else element.toString()
                                                }
                                                else -> element.toString()
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = ToolkitTheme.codeFontFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                    }
                } else {
                    val jsonResult = remember(job.result) {
                        job.result?.let {
                            try {
                                Json.parseToJsonElement(it)
                            } catch (e: Exception) {
                                JsonNull
                            }
                        } ?: JsonNull
                    }

                    Text(
                        text = stringResource(Res.string.job_execution_result_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = ToolkitTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                text = jsonResult.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = ToolkitTheme.codeFontFamily,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(ToolkitTheme.spacing.medium)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                }
            }

            JobStatus.Failed, JobStatus.Cancelled -> {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(ToolkitTheme.spacing.medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))
                        Text(
                            text = job.errorMessage ?: job.status.name,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
            }

            else -> {}
        }
    }
}

/**
 * Calculates the new height in pixels after a drag offset, constrained by min and max bounds.
 */
internal fun calculateResizedLogHeightPx(
    currentHeightPx: Float,
    deltaY: Float,
    minHeightPx: Float,
    maxHeightPx: Float
): Float = (currentHeightPx + deltaY).coerceIn(minHeightPx, maxHeightPx)

/**
 * Calculates toggled terminal height between default and expanded states.
 */
internal fun calculateToggledLogHeight(
    currentHeight: Dp,
    defaultHeight: Dp,
    expandedHeight: Dp
): Dp = if (currentHeight > defaultHeight) defaultHeight else expandedHeight

/**
 * Terminal console output section featuring an expand/collapse toggle and a resizable drag handle.
 */
@Composable
internal fun JobResultConsoleSection(
    logs: List<String>,
    scrollState: ScrollState,
    logHeight: Dp,
    onLogHeightChange: (Dp) -> Unit,
    minLogHeight: Dp,
    maxLogHeight: Dp,
    defaultLogHeight: Dp = ToolkitTheme.dimensions.logTerminalDefaultHeight,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val currentLogHeight by rememberUpdatedState(logHeight)
    val currentOnLogHeightChange by rememberUpdatedState(onLogHeightChange)
    val currentMinLogHeight by rememberUpdatedState(minLogHeight)
    val currentMaxLogHeight by rememberUpdatedState(maxLogHeight)
    var isDragging by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.flow_console_logs_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val isExpanded = logHeight > defaultLogHeight
            val expandedHeight = ToolkitTheme.dimensions.logTerminalExpandedHeight
            IconButton(
                onClick = {
                    onLogHeightChange(calculateToggledLogHeight(logHeight, defaultLogHeight, expandedHeight))
                },
                modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.UnfoldLess else Icons.Default.UnfoldMore,
                    contentDescription = stringResource(
                        if (isExpanded) Res.string.action_collapse_terminal else Res.string.action_expand_terminal
                    ),
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(logHeight)
                .clip(ToolkitTheme.shapes.large)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerLowest,
                    ToolkitTheme.shapes.large
                )
                .padding(ToolkitTheme.spacing.medium)
        ) {
            TerminalView(
                logs = logs,
                scrollState = scrollState,
                onOpenUrl = { url -> uriHandler.openUri(url) },
                onOpenFolder = { path -> PlatformUtils.openFolder(path) }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ToolkitTheme.dimensions.logHandleHeight)
                .pointerHoverIcon(PlatformUtils.verticalResizePointerIcon())
                .pointerInput(Unit) {
                    var accumulatedHeightPx = 0f
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            accumulatedHeightPx = currentLogHeight.toPx()
                        },
                        onDragEnd = {
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedHeightPx = calculateResizedLogHeightPx(
                                currentHeightPx = accumulatedHeightPx,
                                deltaY = dragAmount.y,
                                minHeightPx = currentMinLogHeight.toPx(),
                                maxHeightPx = currentMaxLogHeight.toPx()
                            )
                            currentOnLogHeightChange(accumulatedHeightPx.toDp())
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(ToolkitTheme.dimensions.logHandleWidth)
                    .height(ToolkitTheme.dimensions.logHandleBarHeight)
                    .background(
                        if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        MaterialTheme.shapes.small
                    )
            )
        }
    }
}

/**
 * Bottom actions bar allowing users to auto-scroll, copy, export, resume, pause, cancel, and clear jobs.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun JobResultActionsRow(
    job: BackgroundJob,
    logs: List<String>,
    expanded: Boolean,
    autoScroll: Boolean,
    onAutoScrollChange: (Boolean) -> Unit,
    onScrollToBottom: () -> Unit,
    onPause: (() -> Unit)?,
    onResume: (() -> Unit)?,
    onCancel: ((Boolean) -> Unit)?,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val notificationService: NotificationService = koinInject()
    val clipboardManager = LocalClipboardManager.current
    val shortcutManager = LocalShortcutManager.current

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        if (expanded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = autoScroll,
                    onCheckedChange = { isChecked ->
                        onAutoScrollChange(isChecked)
                        if (isChecked && logs.isNotEmpty()) {
                            onScrollToBottom()
                        }
                    }
                )
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                Text(
                    text = stringResource(Res.string.setting_auto_scroll),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.End) {
            if (logs.isNotEmpty()) {
                val copySuccessMsg = stringResource(Res.string.job_copy_logs_success_toast)
                TextButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(logs.joinToString("\n")))
                        notificationService.toast(copySuccessMsg)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringResource(Res.string.action_copy_logs)
                    )
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                    Text(stringResource(Res.string.action_copy_logs))
                }
            }

            val canExport = logs.isNotEmpty() || job.executionMetrics != null || job.result != null
            if (canExport) {
                val exportSuccessMsg = stringResource(Res.string.job_export_success_toast)
                val startedAtLabel = stringResource(Res.string.job_started_at_label)
                val completedAtLabel = stringResource(Res.string.job_completed_at_label)
                val durationLabel = stringResource(Res.string.job_duration_label)
                val memoryLabel = stringResource(Res.string.job_peak_memory_label)
                val totalMemoryLabel = stringResource(Res.string.job_total_memory_label)
                val capBreakdownLabel = stringResource(Res.string.job_capability_breakdown_title)

                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            val dateStr = job.enqueuedAt.toString().take(19).replace(":", "-").replace("T", "_")
                            val baseName = "${job.name}_$dateStr"
                            val reportContent = buildJobExportReport(
                                job = job,
                                logs = logs,
                                startedAtLabel = startedAtLabel,
                                completedAtLabel = completedAtLabel,
                                durationLabel = durationLabel,
                                memoryLabel = memoryLabel,
                                capabilityBreakdownLabel = capBreakdownLabel,
                                totalMemoryLabel = totalMemoryLabel
                            )
                            val savedPath = PlatformUtils.saveFile(baseName, "txt", reportContent.encodeToByteArray())
                            if (savedPath != null) {
                                notificationService.toast(exportSuccessMsg)
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = stringResource(Res.string.action_export)
                    )
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                    Text(stringResource(Res.string.action_export))
                }
            }

            if (job.status == JobStatus.Paused && onResume != null) {
                TextButton(onClick = onResume) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(Res.string.action_resume)
                    )
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                    Text(stringResource(Res.string.action_resume))
                }
            }

            if (job.isPausable && (job.status == JobStatus.Running || job.status == JobStatus.Queued) && onPause != null) {
                TextButton(onClick = onPause) {
                    Icon(
                        imageVector = Icons.Default.Pause,
                        contentDescription = stringResource(Res.string.action_pause)
                    )
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                    Text(stringResource(Res.string.action_pause))
                }
            }

            if (job.status != JobStatus.Completed && job.status != JobStatus.Cancelled && job.status != JobStatus.Failed && onCancel != null) {
                var isForceCancelPressed by remember { mutableStateOf(false) }
                TextButton(
                    onClick = { onCancel(isForceCancelPressed) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.onPointerEvent(PointerEventType.Press) {
                        val isForce = shortcutManager?.isActionTriggered(
                            ShortcutActionId.JOB_FORCE_CANCEL,
                            it.keyboardModifiers
                        ) ?: false
                        val isSkip = shortcutManager?.isActionTriggered(
                            ShortcutActionId.SKIP_CONFIRMATION,
                            it.keyboardModifiers
                        ) ?: false
                        isForceCancelPressed = isForce || isSkip || (shortcutManager == null && it.keyboardModifiers.isShiftPressed)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = if (isForceCancelPressed) stringResource(Res.string.action_force_cancel) else stringResource(Res.string.dialog_cancel)
                    )
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                    Text(if (isForceCancelPressed) stringResource(Res.string.action_force_cancel) else stringResource(Res.string.dialog_cancel))
                }
            }

            if ((job.status == JobStatus.Completed || job.status == JobStatus.Cancelled || job.status == JobStatus.Failed) && onClear != null) {
                TextButton(
                    onClick = onClear,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.outline)
                ) {
                    Icon(
                        imageVector = Icons.Default.Cancel,
                        contentDescription = stringResource(Res.string.action_clear)
                    )
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                    Text(stringResource(Res.string.action_clear))
                }
            }
        }
    }
}

/**
 * Ticker text displaying elapsed runtime duration for running or completed jobs.
 */
@Composable
internal fun ElapsedTimeText(
    job: BackgroundJob,
    modifier: Modifier = Modifier
) {
    var ticker by remember { mutableStateOf(0) }
    LaunchedEffect(job.id, job.status) {
        if (job.status == JobStatus.Running) {
            while (true) {
                delay(1000)
                ticker++
            }
        }
    }
    val displayTime = if (ticker >= 0) {
        val end = job.completedAt ?: Clock.System.now()
        val start = job.startedAt
        if (start != null) (end - start).inWholeMilliseconds else 0L
    } else 0L

    Text(
        text = formatDuration(displayTime),
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
    )
}

