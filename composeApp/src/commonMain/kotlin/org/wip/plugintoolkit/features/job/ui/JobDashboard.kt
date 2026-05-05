package org.wip.plugintoolkit.features.job.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.coroutines.delay
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.wip.plugintoolkit.core.model.localized
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.viewmodel.JobViewModel
import org.wip.plugintoolkit.shared.components.SectionHeader
import org.wip.plugintoolkit.shared.components.sidebar.NavigationSidebar
import org.wip.plugintoolkit.shared.components.sidebar.SidebarElement
import org.wip.plugintoolkit.shared.components.sidebar.SidebarSectionData
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_collapse
import plugintoolkit.composeapp.generated.resources.action_expand
import plugintoolkit.composeapp.generated.resources.action_pause
import plugintoolkit.composeapp.generated.resources.action_resume
import plugintoolkit.composeapp.generated.resources.dialog_cancel
import plugintoolkit.composeapp.generated.resources.job_error_format
import plugintoolkit.composeapp.generated.resources.job_no_active
import plugintoolkit.composeapp.generated.resources.job_no_archived
import plugintoolkit.composeapp.generated.resources.job_paused_jobs
import plugintoolkit.composeapp.generated.resources.job_queue
import plugintoolkit.composeapp.generated.resources.job_running_jobs
import plugintoolkit.composeapp.generated.resources.job_scheduler_soon
import plugintoolkit.composeapp.generated.resources.nav_job_archive
import plugintoolkit.composeapp.generated.resources.nav_job_general
import plugintoolkit.composeapp.generated.resources.nav_job_history
import plugintoolkit.composeapp.generated.resources.nav_job_scheduler
import plugintoolkit.composeapp.generated.resources.nav_jobs
import plugintoolkit.composeapp.generated.resources.plugin_id_format
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
sealed interface JobNavKey : NavKey {
    @Serializable
    data object General : JobNavKey

    @Serializable
    data object Archive : JobNavKey

    @Serializable
    data object Scheduler : JobNavKey

    @Serializable
    data object History : JobNavKey
}

val JobNavConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(JobNavKey.General::class, JobNavKey.General.serializer())
            subclass(JobNavKey.Archive::class, JobNavKey.Archive.serializer())
            subclass(JobNavKey.Scheduler::class, JobNavKey.Scheduler.serializer())
            subclass(JobNavKey.History::class, JobNavKey.History.serializer())
        }
    }
}

@Composable
fun JobDashboard(
    viewModel: JobViewModel = koinViewModel()
) {
    val backStack = rememberNavBackStack(JobNavConfig, JobNavKey.General as JobNavKey)
    val currentKey = backStack.lastOrNull() ?: JobNavKey.General

    val sections = listOf(
        SidebarSectionData(
            title = Res.string.nav_jobs.localized,
            elements = listOf(
                SidebarElement(JobNavKey.General, Icons.Default.Dashboard, Res.string.nav_job_general.localized),
                SidebarElement(JobNavKey.Archive, Icons.Default.Archive, Res.string.nav_job_archive.localized),
                SidebarElement(JobNavKey.Scheduler, Icons.Default.Schedule, Res.string.nav_job_scheduler.localized),
                SidebarElement(JobNavKey.History, Icons.Default.History, Res.string.nav_job_history.localized),
            )
        )
    )

    Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Internal Sidebar
        NavigationSidebar(
            title = Res.string.nav_jobs.localized,
            bodySections = sections,
            currentScreen = currentKey,
            onScreenSelected = { key ->
                if (backStack.lastOrNull() != key) {
                    backStack.add(key)
                }
            },
            isNavbarCollapsed = false,
            onToggleNavbar = {},
            canCollapse = false,
            modifier = Modifier.fillMaxHeight()
        )

        // Detail Panel
        Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(24.dp)) {
            val titleText = when (currentKey) {
                JobNavKey.General -> stringResource(Res.string.nav_job_general)
                JobNavKey.Archive -> stringResource(Res.string.nav_job_archive)
                JobNavKey.Scheduler -> stringResource(Res.string.nav_job_scheduler)
                JobNavKey.History -> stringResource(Res.string.nav_job_history)
                else -> "Jobs"
            }

            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Box(modifier = Modifier.weight(1f)) {
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.fillMaxSize(),
                    onBack = { if (backStack.size > 1) backStack.removeLast() }
                ) { key ->
                    when (key) {
                        is JobNavKey.General -> NavEntry(key) { GeneralTab(viewModel) }
                        is JobNavKey.Archive -> NavEntry(key) { ArchiveTab(viewModel) }
                        is JobNavKey.Scheduler -> NavEntry(key) { SchedulerTab() }
                        is JobNavKey.History -> NavEntry(key) { HistoryTab(viewModel) }
                        else -> NavEntry(key) { }
                    }
                }
            }
        }
    }
}

@Composable
fun GeneralTab(viewModel: JobViewModel) {
    val runningJobs by viewModel.runningJobs.collectAsState()
    val queuedJobs by viewModel.queuedJobs.collectAsState()
    val progressMap by viewModel.jobProgress.collectAsState(initial = emptyMap())
    val logsMap by viewModel.jobLogs.collectAsState(initial = emptyMap())
    val expandedJobs = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (runningJobs.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(Res.string.job_running_jobs), icon = Icons.Default.PlayArrow)
            }
            items(runningJobs) { job ->
                JobCard(
                    job = job,
                    progress = progressMap[job.id] ?: 0f,
                    logs = logsMap[job.id] ?: emptyList(),
                    isExpanded = expandedJobs[job.id] ?: false,
                    onToggleExpand = { expandedJobs[job.id] = !(expandedJobs[job.id] ?: false) },
                    onCancel = { force -> viewModel.cancelJob(job.id, force) },
                    onPause = { viewModel.pauseJob(job.id) }
                )
            }
        }

        if (queuedJobs.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(Res.string.job_queue), icon = Icons.AutoMirrored.Filled.List)
            }
            items(queuedJobs) { job ->
                JobCard(
                    job = job,
                    progress = progressMap[job.id] ?: 0f,
                    logs = logsMap[job.id] ?: emptyList(),
                    isExpanded = expandedJobs[job.id] ?: false,
                    onToggleExpand = { expandedJobs[job.id] = !(expandedJobs[job.id] ?: false) },
                    onCancel = { force -> viewModel.cancelJob(job.id, force) },
                    onPause = { viewModel.pauseJob(job.id) }
                )
            }
        }

        if (runningJobs.isEmpty() && queuedJobs.isEmpty()) {
            item {
                EmptyState(stringResource(Res.string.job_no_active), Icons.Default.Inbox)
            }
        }
    }
}

@Composable
fun ArchiveTab(viewModel: JobViewModel) {
    val pausedJobs by viewModel.pausedJobs.collectAsState()
    val progressMap by viewModel.jobProgress.collectAsState(initial = emptyMap())
    val logsMap by viewModel.jobLogs.collectAsState(initial = emptyMap())
    val expandedJobs = remember { mutableStateMapOf<String, Boolean>() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (pausedJobs.isNotEmpty()) {
            item {
                SectionHeader(title = stringResource(Res.string.job_paused_jobs), icon = Icons.Default.Pause)
            }
            items(pausedJobs) { job ->
                JobCard(
                    job = job,
                    progress = progressMap[job.id] ?: 0f,
                    logs = logsMap[job.id] ?: emptyList(),
                    isExpanded = expandedJobs[job.id] ?: false,
                    onToggleExpand = { expandedJobs[job.id] = !(expandedJobs[job.id] ?: false) },
                    onCancel = { force -> viewModel.cancelJob(job.id, force) },
                    onResume = { viewModel.resumeJob(job.id) }
                )
            }
        } else {
            item {
                EmptyState(stringResource(Res.string.job_no_archived), Icons.Default.Archive)
            }
        }
    }
}

@Composable
fun SchedulerTab() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.job_scheduler_soon),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun HistoryTab(viewModel: JobViewModel) {
    val history by viewModel.history.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(history) { entry ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when (entry.event) {
                        "Enqueued" -> Icons.Default.Add
                        "Started" -> Icons.Default.PlayArrow
                        "Completed" -> Icons.Default.CheckCircle
                        "Failed" -> Icons.Default.Error
                        "Cancelled" -> Icons.Default.Cancel
                        "Paused" -> Icons.Default.Pause
                        "Resumed" -> Icons.Default.Refresh
                        else -> Icons.Default.Info
                    }
                    val color = when (entry.event) {
                        "Completed" -> Color(0xFF4CAF50)
                        "Failed" -> MaterialTheme.colorScheme.error
                        "Started" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${entry.jobName}: ${entry.event}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (entry.details != null) {
                            Text(text = entry.details, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(
                        text = formatTime(entry.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun JobCard(
    job: BackgroundJob,
    progress: Float = 0f,
    logs: List<String> = emptyList(),
    isExpanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onCancel: (Boolean) -> Unit = {},
    onPause: () -> Unit = {},
    onResume: () -> Unit = {}
) {
    // Ticker to force recomposition every second for running jobs
    var ticker by remember { mutableStateOf(0) }
    LaunchedEffect(job.id, job.status) {
        if (job.status == JobStatus.Running) {
            while (true) {
                delay(1000)
                ticker++
            }
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) stringResource(Res.string.action_collapse) else stringResource(
                            Res.string.action_expand
                        )
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = job.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = stringResource(Res.string.plugin_id_format, job.id),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StatusBadge(job.status)
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (job.status == JobStatus.Running) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small),
                    color = ProgressIndicatorDefaults.linearColor,
                    trackColor = ProgressIndicatorDefaults.linearTrackColor,
                    strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    // Use ticker to force update
                    val displayTime = if (ticker >= 0) {
                        val end = job.completedAt ?: Clock.System.now()
                        val start = job.startedAt
                        if (start != null) (end - start).inWholeMilliseconds else 0L
                    } else 0L
                    Text(text = formatDuration(displayTime), style = MaterialTheme.typography.labelSmall)
                }
            }

            if (job.errorMessage != null) {
                Text(
                    text = stringResource(Res.string.job_error_format, job.errorMessage),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                TerminalLogView(logs)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (job.status == JobStatus.Paused) {
                    TextButton(onClick = onResume) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(Res.string.action_resume))
                    }
                }
                if (job.isPausable && (job.status == JobStatus.Running || job.status == JobStatus.Queued)) {
                    TextButton(onClick = onPause) {
                        Icon(Icons.Default.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(Res.string.action_pause))
                    }
                }
                if (job.status != JobStatus.Completed && job.status != JobStatus.Cancelled && job.status != JobStatus.Failed) {
                    var isShiftPressed by remember { mutableStateOf(false) }
                    TextButton(
                        onClick = { onCancel(isShiftPressed) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.onPointerEvent(PointerEventType.Press) {
                            isShiftPressed = it.keyboardModifiers.isShiftPressed
                        }
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isShiftPressed) "Force Cancel" else stringResource(Res.string.dialog_cancel))
                    }
                }
            }
        }
    }
}

@Composable
fun TerminalLogView(logs: List<String>) {
    val scrollState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            scrollState.animateScrollToItem(logs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp) // Approx 10 rows
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E1E1E))
            .padding(8.dp)
    ) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxWidth()
        ) {
            items(logs) { log ->
                Text(
                    text = log,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    ),
                    color = when {
                        log.contains("[ERROR]") -> Color(0xFFF44336)
                        log.contains("[WARN]") -> Color(0xFFFFEB3B)
                        log.contains("[VERBOSE]") -> Color(0xFF9E9E9E)
                        log.contains("[DEBUG]") -> Color(0xFF2196F3)
                        else -> Color(0xFFE0E0E0)
                    }
                )
            }
        }
    }
}

@Composable
fun StatusBadge(status: JobStatus) {
    val color = when (status) {
        JobStatus.Queued -> Color.Gray
        JobStatus.Running -> MaterialTheme.colorScheme.primary
        JobStatus.Paused -> Color(0xFFFFA000)
        JobStatus.Completed -> Color(0xFF4CAF50)
        JobStatus.Failed -> MaterialTheme.colorScheme.error
        JobStatus.Cancelled -> Color.DarkGray
    }

    Surface(
        color = color.copy(alpha = 0.1f),
        contentColor = color,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = status.name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun EmptyState(text: String, icon: ImageVector) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTime(instant: Instant): String {
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localDateTime.hour.toString().padStart(2, '0')}:${
        localDateTime.minute.toString().padStart(2, '0')
    }:${localDateTime.second.toString().padStart(2, '0')}"
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000) % 60
    val minutes = (ms / (1000 * 60)) % 60
    val hours = (ms / (1000 * 60 * 60))
    return if (hours > 0) "${hours}h ${minutes}m ${seconds}s" else "${minutes}m ${seconds}s"
}
