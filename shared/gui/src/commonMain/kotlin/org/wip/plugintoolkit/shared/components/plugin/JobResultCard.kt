package org.wip.plugintoolkit.shared.components.plugin

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.utils.MemoryUtils
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Instant
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.model.JobType
import org.wip.plugintoolkit.features.job.ui.StatusBadge
import org.wip.plugintoolkit.shared.components.GlassCard
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_delete
import plugintoolkit.composeapp.generated.resources.flow_console_logs_title
import plugintoolkit.composeapp.generated.resources.flow_output_results_title
import plugintoolkit.composeapp.generated.resources.flow_run_id_label
import plugintoolkit.composeapp.generated.resources.flow_triggered_label
import plugintoolkit.composeapp.generated.resources.plugin_executing_progress
import plugintoolkit.composeapp.generated.resources.plugin_execution_id_format
import plugintoolkit.composeapp.generated.resources.*

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun JobResultCard(
    job: BackgroundJob,
    progress: org.wip.plugintoolkit.features.job.model.JobProgress,
    logs: List<String>,
    onDelete: (() -> Unit)? = null,
    onCancel: ((Boolean) -> Unit)? = null,
    onPause: (() -> Unit)? = null,
    onResume: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val defaultLogHeight = ToolkitTheme.dimensions.logTerminalDefaultHeight
    val minLogHeight = ToolkitTheme.dimensions.logTerminalMinHeight
    val maxLogHeight = ToolkitTheme.dimensions.logTerminalMaxHeight
    var logHeight by remember { mutableStateOf(defaultLogHeight) }
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val notificationService: NotificationService = koinInject()

    val isAtBottom by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf true
            lastVisibleItem.index >= logs.lastIndex - 1
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !isAtBottom && autoScroll) {
            autoScroll = false
        }
    }

    LaunchedEffect(logs.size, logs.lastOrNull()) {
        if (autoScroll && logs.isNotEmpty()) {
            listState.scrollToItem(logs.lastIndex)
        }
    }

    // Outer GlassCard with animateContentSize enables extremely smooth expansion transitions!
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(ToolkitTheme.spacing.medium)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
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

                    // Only show expand button if there are details to show (success result, error, metrics, or logs)
                    val hasDetails = job.status == JobStatus.Completed ||
                            job.status == JobStatus.Failed ||
                            job.status == JobStatus.Cancelled ||
                            job.executionMetrics != null ||
                            logs.isNotEmpty()

                    if (hasDetails) {
                        IconButton(
                            onClick = { expanded = !expanded },
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

            // Progress bar for running jobs
            if (job.status == JobStatus.Running || job.status == JobStatus.Queued) {
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                Column(modifier = Modifier.fillMaxWidth()) {
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
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                                .height(2.dp)
                                .clip(MaterialTheme.shapes.small),
                            color = MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }

            // Expanded Details Section
            if (expanded) {
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                HorizontalDivider(Modifier, DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

                // Execution Info Section
                ExecutionInfoSection(job = job)
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

                when (job.status) {
                    JobStatus.Completed -> {
                        if (job.type == JobType.Flow) {
                            // Render flow parsed outputs beautifully
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
                                                MaterialTheme.colorScheme.surface.copy(alpha = ToolkitTheme.opacity.divider),
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
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                            }
                        } else {
                            // Render plugin capability output result
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
                                text = "Execution Result",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                                shape = ToolkitTheme.shapes.large,
                                border = BorderStroke(
                                    ToolkitTheme.dimensions.borderThin,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                SelectionContainer {
                                    Text(
                                        text = jsonResult.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontFamily = FontFamily.Monospace,
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

                // Render Console logs if present
                if (logs.isNotEmpty()) {
                    Text(
                        text = stringResource(Res.string.flow_console_logs_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))

                    var isShiftPressed by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(logHeight)
                            .clip(ToolkitTheme.shapes.large)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerLowest,
                                ToolkitTheme.shapes.large
                            )
                            .border(
                                ToolkitTheme.dimensions.borderThin,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider),
                                ToolkitTheme.shapes.large
                            )
                            .onPointerEvent(PointerEventType.Move) {
                                isShiftPressed = it.keyboardModifiers.isShiftPressed
                            }
                            .onPointerEvent(PointerEventType.Press) {
                                isShiftPressed = it.keyboardModifiers.isShiftPressed
                            }
                            .onPointerEvent(PointerEventType.Release) {
                                isShiftPressed = it.keyboardModifiers.isShiftPressed
                            }
                            .padding(ToolkitTheme.spacing.medium)
                    ) {
                        SelectionContainer {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
                            ) {
                                items(logs) { logLine ->
                                    TerminalLogLine(
                                        logLine = logLine,
                                        isShiftPressed = isShiftPressed,
                                        onOpenUrl = { url -> uriHandler.openUri(url) },
                                        onOpenFolder = { path -> PlatformUtils.openFolder(path) }
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ToolkitTheme.dimensions.logHandleHeight)
                            .pointerInput(minLogHeight, maxLogHeight) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val newHeight = logHeight + dragAmount.y.toDp()
                                    logHeight = newHeight.coerceIn(
                                        minLogHeight,
                                        maxLogHeight
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(ToolkitTheme.dimensions.logHandleWidth)
                                .height(ToolkitTheme.dimensions.logHandleBarHeight)
                                .background(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                        )
                    }
                }
            }

            // Action buttons row
            val hasActions = onPause != null || onResume != null || onCancel != null || onClear != null
            if (hasActions || expanded) {
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (expanded) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = autoScroll,
                                onCheckedChange = { isChecked ->
                                    autoScroll = isChecked
                                    if (isChecked && logs.isNotEmpty()) {
                                        coroutineScope.launch {
                                            listState.scrollToItem(logs.lastIndex)
                                        }
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
                    val canExport = logs.isNotEmpty() || job.executionMetrics != null || job.result != null
                    if (canExport) {
                        val exportSuccessMsg = stringResource(Res.string.job_export_success_toast)
                        val startedAtLabel = stringResource(Res.string.job_started_at_label)
                        val completedAtLabel = stringResource(Res.string.job_completed_at_label)
                        val durationLabel = stringResource(Res.string.job_duration_label)
                        val memoryLabel = stringResource(Res.string.job_memory_label)
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
                                        capabilityBreakdownLabel = capBreakdownLabel
                                    )
                                    val savedPath = PlatformUtils.saveFile(baseName, "txt", reportContent.encodeToByteArray())
                                    if (savedPath != null) {
                                        notificationService.toast(exportSuccessMsg)
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Download, contentDescription = stringResource(Res.string.action_export))
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(stringResource(Res.string.action_export))
                        }
                    }
                    if (job.status == JobStatus.Paused && onResume != null) {
                        TextButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(Res.string.action_resume))
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(stringResource(Res.string.action_resume))
                        }
                    }
                    if (job.isPausable && (job.status == JobStatus.Running || job.status == JobStatus.Queued) && onPause != null) {
                        TextButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = stringResource(Res.string.action_pause))
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(stringResource(Res.string.action_pause))
                        }
                    }
                    if (job.status != JobStatus.Completed && job.status != JobStatus.Cancelled && job.status != JobStatus.Failed && onCancel != null) {
                        @OptIn(ExperimentalComposeUiApi::class)
                        var isShiftPressed by remember { mutableStateOf(false) }
                        TextButton(
                            onClick = { onCancel(isShiftPressed) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.onPointerEvent(PointerEventType.Press) {
                                isShiftPressed = it.keyboardModifiers.isShiftPressed
                            }
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = if (isShiftPressed) stringResource(Res.string.action_force_cancel) else stringResource(Res.string.dialog_cancel))
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(if (isShiftPressed) stringResource(Res.string.action_force_cancel) else stringResource(Res.string.dialog_cancel))
                        }
                    }
                    if ((job.status == JobStatus.Completed || job.status == JobStatus.Cancelled || job.status == JobStatus.Failed) && onClear != null) {
                        TextButton(
                            onClick = onClear,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.outline)
                        ) {
                            Icon(Icons.Default.Cancel, contentDescription = stringResource(Res.string.action_clear))
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(stringResource(Res.string.action_clear))
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun ElapsedTimeText(job: BackgroundJob) {
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
    Text(text = formatDuration(displayTime), style = MaterialTheme.typography.labelSmall)
}

private fun formatDuration(ms: Long): String {
    if (ms < 1000L) return "${ms}ms"
    val totalSeconds = ms / 1000.0
    val minutes = (ms / 60000L)
    val seconds = ((ms % 60000L) / 1000.0 * 10.0).roundToLong() / 10.0
    return if (minutes > 0) "${minutes}m ${seconds.toInt()}s" else "${seconds}s"
}

private data class LinkSpan(
    val start: Int,
    val end: Int,
    val target: String,
    val isUrl: Boolean
)

private val URL_REGEX = Regex("""https?://[^\s)\]>"']+""")
private val PATH_REGEX = Regex("""(?:[a-zA-Z]:[/\\]|\\\\|/(?:Users|home|tmp|var|etc|opt|usr|Applications|mnt|Volumes)/)[^\s)\]>"']+""")

private fun findLinkSpans(line: String): List<LinkSpan> {
    val spans = mutableListOf<LinkSpan>()
    for (match in URL_REGEX.findAll(line)) {
        val cleanTarget = match.value.trimEnd('.', ',', ';', ':', ')', ']')
        val end = match.range.first + cleanTarget.length
        spans.add(LinkSpan(match.range.first, end, cleanTarget, isUrl = true))
    }
    for (match in PATH_REGEX.findAll(line)) {
        val start = match.range.first
        val cleanTarget = match.value.trimEnd('.', ',', ';', ':', ')', ']')
        val end = start + cleanTarget.length
        val overlaps = spans.any { it.start < end && start < it.end }
        if (!overlaps) {
            spans.add(LinkSpan(start, end, cleanTarget, isUrl = false))
        }
    }
    return spans.sortedBy { it.start }
}

@Composable
private fun TerminalLogLine(
    logLine: String,
    isShiftPressed: Boolean,
    onOpenUrl: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val errorColor = MaterialTheme.colorScheme.error
    val warnColor = MaterialTheme.colorScheme.tertiary
    val defaultColor = MaterialTheme.colorScheme.onSurfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary

    val baseColor = when {
        logLine.contains("[ERROR]", ignoreCase = true) || logLine.startsWith("ERROR:") -> errorColor
        logLine.contains("[WARN]", ignoreCase = true) || logLine.startsWith("WARN:") -> warnColor
        else -> defaultColor
    }

    val linkSpans = remember(logLine) { findLinkSpans(logLine) }

    val annotatedText = remember(logLine, isShiftPressed, baseColor, linkColor, linkSpans) {
        buildAnnotatedString {
            if (linkSpans.isEmpty()) {
                withStyle(SpanStyle(color = baseColor)) {
                    append(logLine)
                }
            } else {
                var currentIndex = 0
                for (span in linkSpans) {
                    if (span.start > currentIndex) {
                        withStyle(SpanStyle(color = baseColor)) {
                            append(logLine.substring(currentIndex, span.start))
                        }
                    }
                    val style = if (isShiftPressed) {
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        SpanStyle(color = baseColor)
                    }
                    withStyle(style) {
                        append(logLine.substring(span.start, span.end))
                    }
                    currentIndex = span.end
                }
                if (currentIndex < logLine.length) {
                    withStyle(SpanStyle(color = baseColor)) {
                        append(logLine.substring(currentIndex))
                    }
                }
            }
        }
    }

    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val clickModifier = if (isShiftPressed && linkSpans.isNotEmpty()) {
        Modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .pointerInput(isShiftPressed, linkSpans) {
                detectTapGestures { offset ->
                    val layout = layoutResult ?: return@detectTapGestures
                    val characterOffset = layout.getOffsetForPosition(offset)
                    val clickedSpan = linkSpans.firstOrNull { characterOffset in it.start until it.end }
                    if (clickedSpan != null) {
                        if (clickedSpan.isUrl) {
                            onOpenUrl(clickedSpan.target)
                        } else {
                            onOpenFolder(clickedSpan.target)
                        }
                    }
                }
            }
    } else {
        Modifier
    }

    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        onTextLayout = { layoutResult = it },
        modifier = modifier.fillMaxWidth().then(clickModifier)
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ExecutionInfoSection(
    job: BackgroundJob,
    modifier: Modifier = Modifier
) {
    val metrics = job.executionMetrics
    val startedAt = metrics?.startedAt ?: job.startedAt
    val completedAt = metrics?.completedAt ?: job.completedAt
    val durationMs = metrics?.totalDurationMs ?: run {
        if (startedAt != null) {
            val end = completedAt ?: Clock.System.now()
            (end - startedAt).inWholeMilliseconds.coerceAtLeast(0L)
        } else null
    }
    val memoryUsage = metrics?.memoryUsageBytes ?: if (job.status == JobStatus.Running) {
        MemoryUtils.getCurrentMemoryUsageBytes()
    } else null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.divider),
                MaterialTheme.shapes.medium
            )
            .padding(ToolkitTheme.spacing.medium)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
        ) {
            Icon(
                imageVector = Icons.Default.Speed,
                contentDescription = null,
                modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(Res.string.job_execution_info_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
            modifier = Modifier.fillMaxWidth()
        ) {
            MetricTile(
                icon = Icons.Default.PlayCircleOutline,
                label = stringResource(Res.string.job_started_at_label),
                value = formatTimestamp(startedAt)
            )
            MetricTile(
                icon = Icons.Default.StopCircle,
                label = stringResource(Res.string.job_completed_at_label),
                value = if (job.status == JobStatus.Running) "..." else formatTimestamp(completedAt)
            )
            MetricTile(
                icon = Icons.Default.Timer,
                label = stringResource(Res.string.job_duration_label),
                value = if (durationMs != null) formatDuration(durationMs) else "—"
            )
            MetricTile(
                icon = Icons.Default.Memory,
                label = stringResource(Res.string.job_memory_label),
                value = if (memoryUsage != null) MemoryUtils.formatMemoryBytes(memoryUsage) else "—"
            )
        }

        // Per-capability breakdown
        val capDurations = metrics?.totalDurationPerCapability ?: emptyMap()
        val capCounts = metrics?.executionCountPerCapability ?: emptyMap()
        if (capDurations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.mediumSmall))
            Text(
                text = stringResource(Res.string.job_capability_breakdown_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraSmall))

            val totalMs = durationMs?.coerceAtLeast(1L) ?: capDurations.values.sum().coerceAtLeast(1L)
            Column(
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                modifier = Modifier.fillMaxWidth()
            ) {
                capDurations.forEach { (capName, ms) ->
                    val count = capCounts[capName] ?: 1
                    val fraction = (ms.toFloat() / totalMs).coerceIn(0f, 1f)
                    val percent = (fraction * 100).roundToInt()

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = ToolkitTheme.opacity.divider),
                                MaterialTheme.shapes.small
                            )
                            .padding(ToolkitTheme.spacing.small)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = capName,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (count > 1) {
                                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                                    Text(
                                        text = stringResource(Res.string.job_execution_count_format, count),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                            Text(
                                text = "${formatDuration(ms)} ($percent%)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraSmall))
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(ToolkitTheme.dimensions.capabilityProgressBarHeight)
                                .clip(MaterialTheme.shapes.extraSmall),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = ToolkitTheme.opacity.high),
        shape = MaterialTheme.shapes.small,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.smallMedium, vertical = ToolkitTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun formatTimestamp(instant: Instant?): String {
    if (instant == null) return "—"
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${
        local.minute.toString().padStart(2, '0')
    }:${local.second.toString().padStart(2, '0')}"
}

private fun buildJobExportReport(
    job: BackgroundJob,
    logs: List<String>,
    startedAtLabel: String,
    completedAtLabel: String,
    durationLabel: String,
    memoryLabel: String,
    capabilityBreakdownLabel: String
): String {
    val sb = StringBuilder()
    sb.appendLine("================================================================================")
    sb.appendLine("JOB EXECUTION REPORT")
    sb.appendLine("================================================================================")
    sb.appendLine("Job ID:         ${job.id}")
    sb.appendLine("Job Name:       ${job.name}")
    sb.appendLine("Job Type:       ${job.type}")
    sb.appendLine("Status:         ${job.status}")
    sb.appendLine("Triggered At:   ${job.enqueuedAt}")

    val metrics = job.executionMetrics
    val startedAt = metrics?.startedAt ?: job.startedAt
    val completedAt = metrics?.completedAt ?: job.completedAt
    val durationMs = metrics?.totalDurationMs ?: run {
        if (startedAt != null) {
            val end = completedAt ?: Clock.System.now()
            (end - startedAt).inWholeMilliseconds.coerceAtLeast(0L)
        } else null
    }

    if (startedAt != null) {
        sb.appendLine("$startedAtLabel:     $startedAt")
    }
    if (completedAt != null) {
        sb.appendLine("$completedAtLabel:   $completedAt")
    }
    if (durationMs != null) {
        sb.appendLine("$durationLabel: $durationMs ms (${formatDuration(durationMs)})")
    }
    val memoryUsage = metrics?.memoryUsageBytes
    if (memoryUsage != null) {
        sb.appendLine("$memoryLabel:   ${MemoryUtils.formatMemoryBytes(memoryUsage)}")
    }

    if (metrics != null && metrics.capabilityMetrics.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine(capabilityBreakdownLabel.uppercase())
        sb.appendLine("--------------------------------------------------------------------------------")
        val durations = metrics.totalDurationPerCapability
        val counts = metrics.executionCountPerCapability
        durations.forEach { (capName, ms) ->
            val count = counts[capName] ?: 1
            val countStr = if (count > 1) " (x$count)" else ""
            val pctStr = if (durationMs != null && durationMs > 0L) {
                " [${((ms.toDouble() / durationMs) * 100.0).roundToInt()}%]"
            } else ""
            sb.appendLine("- $capName: $ms ms (${formatDuration(ms)})$countStr$pctStr")
        }
    }

    if (job.result != null) {
        sb.appendLine()
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("RESULT")
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine(job.result)
    }

    if (job.errorMessage != null) {
        sb.appendLine()
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine("ERROR")
        sb.appendLine("--------------------------------------------------------------------------------")
        sb.appendLine(job.errorMessage)
    }

    if (logs.isNotEmpty()) {
        sb.appendLine()
        sb.appendLine("================================================================================")
        sb.appendLine("CONSOLE LOGS (${logs.size} lines)")
        sb.appendLine("================================================================================")
        sb.appendLine(logs.joinToString("\n"))
    }

    return sb.toString()
}

