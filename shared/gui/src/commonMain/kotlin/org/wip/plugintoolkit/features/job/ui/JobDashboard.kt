package org.wip.plugintoolkit.features.job.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Error
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformVerticalScrollbar
import org.wip.plugintoolkit.features.job.model.JobProgress
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.job.viewmodel.JobViewModel
import org.wip.plugintoolkit.shared.components.AdaptiveBackNavigationButton
import org.wip.plugintoolkit.shared.components.AdaptivePaneHeader
import org.wip.plugintoolkit.shared.components.SectionHeader
import org.wip.plugintoolkit.shared.components.ToolkitAdaptiveListDetailWorkspace
import org.wip.plugintoolkit.shared.components.ToolkitChip
import org.wip.plugintoolkit.shared.components.ToolkitChipStyle
import org.wip.plugintoolkit.shared.components.ToolkitEmptyState
import org.wip.plugintoolkit.shared.components.ToolkitStatusBadge
import org.wip.plugintoolkit.shared.components.plugin.JobResultCard
import org.wip.plugintoolkit.shared.components.sidebar.ToolkitFloatingSidebar
import org.wip.plugintoolkit.shared.components.sidebar.ToolkitFloatingSidebarScaffold
import org.wip.plugintoolkit.ui.desktopNavTransitionSpec
import org.wip.plugintoolkit.ui.opaqueNavEntry
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_clear_all
import plugintoolkit.composeapp.generated.resources.job_ended_jobs
import plugintoolkit.composeapp.generated.resources.job_no_active
import plugintoolkit.composeapp.generated.resources.job_no_archived
import plugintoolkit.composeapp.generated.resources.job_no_ended
import plugintoolkit.composeapp.generated.resources.job_paused_jobs
import plugintoolkit.composeapp.generated.resources.job_queue
import plugintoolkit.composeapp.generated.resources.job_running_jobs
import plugintoolkit.composeapp.generated.resources.job_scheduler_soon
import plugintoolkit.composeapp.generated.resources.nav_job_archive
import plugintoolkit.composeapp.generated.resources.nav_job_ended
import plugintoolkit.composeapp.generated.resources.nav_job_general
import plugintoolkit.composeapp.generated.resources.nav_job_history
import plugintoolkit.composeapp.generated.resources.nav_job_scheduler
import plugintoolkit.composeapp.generated.resources.nav_jobs
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

    @Serializable
    data object Ended : JobNavKey
}

val JobNavConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(JobNavKey.General::class, JobNavKey.General.serializer())
            subclass(JobNavKey.Archive::class, JobNavKey.Archive.serializer())
            subclass(JobNavKey.Scheduler::class, JobNavKey.Scheduler.serializer())
            subclass(JobNavKey.History::class, JobNavKey.History.serializer())
            subclass(JobNavKey.Ended::class, JobNavKey.Ended.serializer())
        }
    }
}

private data class JobNavItemSpec(
    val key: JobNavKey,
    val icon: ImageVector,
    val label: String,
    val badgeCount: Int = 0,
    val badgeColor: Color? = null
)

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun JobDashboard(
    viewModel: JobViewModel = koinInject()
) {
    val backStack = rememberNavBackStack(JobNavConfig, JobNavKey.General as JobNavKey)
    val currentKey = backStack.lastOrNull() ?: JobNavKey.General
    val scope = rememberCoroutineScope()

    val runningJobs by viewModel.runningJobs.collectAsState()
    val queuedJobs by viewModel.queuedJobs.collectAsState()
    val pausedJobs by viewModel.pausedJobs.collectAsState()
    val endedJobs by viewModel.endedJobs.collectAsState()

    val activeCount = runningJobs.size + queuedJobs.size
    val detailSurfaceColor = MaterialTheme.colorScheme.surfaceContainerLowest

    val navItems = listOf(
        JobNavItemSpec(
            key = JobNavKey.General,
            icon = Icons.Default.Dashboard,
            label = stringResource(Res.string.nav_job_general),
            badgeCount = activeCount,
            badgeColor = MaterialTheme.colorScheme.primary
        ),
        JobNavItemSpec(
            key = JobNavKey.Archive,
            icon = Icons.Default.Archive,
            label = stringResource(Res.string.nav_job_archive),
            badgeCount = pausedJobs.size,
            badgeColor = MaterialTheme.colorScheme.tertiary
        ),
        JobNavItemSpec(
            key = JobNavKey.Ended,
            icon = Icons.Default.CheckCircle,
            label = stringResource(Res.string.nav_job_ended),
            badgeCount = endedJobs.size,
            badgeColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        JobNavItemSpec(
            key = JobNavKey.Scheduler,
            icon = Icons.Default.Schedule,
            label = stringResource(Res.string.nav_job_scheduler)
        ),
        JobNavItemSpec(
            key = JobNavKey.History,
            icon = Icons.Default.History,
            label = stringResource(Res.string.nav_job_history)
        )
    )

    ToolkitAdaptiveListDetailWorkspace<JobNavKey>(
        detailContainerColor = detailSurfaceColor,
        listPane = { navigator ->
            ToolkitFloatingSidebar(
                navigationKey = Unit,
                animateNavigation = false,
                modifier = Modifier.fillMaxSize()
            ) {
                ToolkitFloatingSidebarScaffold(
                    title = stringResource(Res.string.nav_jobs)
                ) {
                    val sidebarScroll = rememberScrollState()
                    val scrollbarReserve = if (sidebarScroll.maxValue > 0) {
                        ToolkitTheme.dimensions.sduiScrollbarEndPadding
                    } else {
                        ToolkitTheme.spacing.none
                    }
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(sidebarScroll)
                                .padding(end = scrollbarReserve),
                            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
                        ) {
                            navItems.forEach { item ->
                                JobSidebarNavItem(
                                    spec = item,
                                    isSelected = currentKey == item.key,
                                    onClick = {
                                        if (backStack.lastOrNull() != item.key) {
                                            backStack.add(item.key)
                                        }
                                        scope.launch {
                                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, item.key)
                                        }
                                    }
                                )
                            }
                        }
                        if (sidebarScroll.maxValue > 0) {
                            PlatformVerticalScrollbar(
                                scrollState = sidebarScroll,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }
        },
        detailPane = { navigator ->
            val titleText = when (currentKey) {
                JobNavKey.General -> stringResource(Res.string.nav_job_general)
                JobNavKey.Archive -> stringResource(Res.string.nav_job_archive)
                JobNavKey.Ended -> stringResource(Res.string.nav_job_ended)
                JobNavKey.Scheduler -> stringResource(Res.string.nav_job_scheduler)
                JobNavKey.History -> stringResource(Res.string.nav_job_history)
                else -> stringResource(Res.string.nav_jobs)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(ToolkitTheme.spacing.large),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
            ) {
                AdaptivePaneHeader(
                    title = titleText,
                    navigationIcon = if (navigator.canNavigateBack()) {
                        {
                            AdaptiveBackNavigationButton(
                                onClick = { scope.launch { navigator.navigateBack() } }
                            )
                        }
                    } else null
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    NavDisplay(
                        backStack = backStack,
                        modifier = Modifier.fillMaxSize(),
                        onBack = { if (backStack.size > 1) backStack.removeLast() },
                        transitionSpec = desktopNavTransitionSpec(),
                        popTransitionSpec = desktopNavTransitionSpec()
                    ) { key ->
                        when (key) {
                            is JobNavKey.General -> opaqueNavEntry(key, containerColor = detailSurfaceColor) {
                                GeneralTab(viewModel)
                            }
                            is JobNavKey.Archive -> opaqueNavEntry(key, containerColor = detailSurfaceColor) {
                                ArchiveTab(viewModel)
                            }
                            is JobNavKey.Ended -> opaqueNavEntry(key, containerColor = detailSurfaceColor) {
                                EndedTab(viewModel)
                            }
                            is JobNavKey.Scheduler -> opaqueNavEntry(key, containerColor = detailSurfaceColor) {
                                SchedulerTab()
                            }
                            is JobNavKey.History -> opaqueNavEntry(key, containerColor = detailSurfaceColor) {
                                HistoryTab(viewModel)
                            }
                            else -> opaqueNavEntry(key, containerColor = detailSurfaceColor) { }
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun JobSidebarNavItem(
    spec: JobNavItemSpec,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val motion = MaterialTheme.motionScheme
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = ToolkitTheme.opacity.buttonBackground)
            isHovered || isFocused -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> ToolkitTheme.colors.transparent
        },
        animationSpec = motion.fastEffectsSpec(),
        label = "jobSidebarItemBg"
    )

    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = ToolkitTheme.opacity.secondaryText)
    }

    val iconBg = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.high)
    }

    val iconTint = if (isSelected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = ToolkitTheme.opacity.high)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ToolkitTheme.shapes.medium)
            .background(backgroundColor)
            .hoverable(interactionSource)
            .focusable(interactionSource = interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(
                horizontal = ToolkitTheme.spacing.mediumSmall,
                vertical = ToolkitTheme.spacing.small
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.mediumSmall)
    ) {
        Box(
            modifier = Modifier
                .size(ToolkitTheme.dimensions.iconLarge)
                .clip(ToolkitTheme.shapes.small)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = spec.icon,
                contentDescription = spec.label,
                tint = iconTint,
                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
            )
        }
        Text(
            text = spec.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (spec.badgeCount > 0) {
            ToolkitStatusBadge(
                text = spec.badgeCount.toString(),
                color = spec.badgeColor ?: MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun GeneralTab(viewModel: JobViewModel) {
    val runningJobs by viewModel.runningJobs.collectAsState()
    val queuedJobs by viewModel.queuedJobs.collectAsState()
    val progressMap by viewModel.jobProgress.collectAsState(initial = emptyMap())
    val logsMap by viewModel.jobLogs.collectAsState(initial = emptyMap())
    val listState = rememberLazyListState()

    if (runningJobs.isEmpty() && queuedJobs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ToolkitEmptyState(
                title = stringResource(Res.string.job_no_active),
                icon = Icons.Default.Inbox
            )
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(end = ToolkitTheme.dimensions.sduiScrollbarEndPadding),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
        ) {
            if (runningJobs.isNotEmpty()) {
                item {
                    SectionHeader(title = stringResource(Res.string.job_running_jobs), icon = Icons.Default.PlayArrow)
                }
                items(runningJobs) { job ->
                    JobResultCard(
                        job = job,
                        progress = progressMap[job.id] ?: JobProgress(),
                        logs = logsMap[job.id] ?: emptyList(),
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
                    JobResultCard(
                        job = job,
                        progress = progressMap[job.id] ?: JobProgress(),
                        logs = logsMap[job.id] ?: emptyList(),
                        onCancel = { force -> viewModel.cancelJob(job.id, force) },
                        onPause = { viewModel.pauseJob(job.id) }
                    )
                }
            }
        }
        PlatformVerticalScrollbar(
            lazyListState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        )
    }
}

@Composable
fun ArchiveTab(viewModel: JobViewModel) {
    val pausedJobs by viewModel.pausedJobs.collectAsState()
    val progressMap by viewModel.jobProgress.collectAsState(initial = emptyMap())
    val logsMap by viewModel.jobLogs.collectAsState(initial = emptyMap())
    val listState = rememberLazyListState()

    if (pausedJobs.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ToolkitEmptyState(
                title = stringResource(Res.string.job_no_archived),
                icon = Icons.Default.Archive
            )
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(end = ToolkitTheme.dimensions.sduiScrollbarEndPadding),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
        ) {
            item {
                SectionHeader(title = stringResource(Res.string.job_paused_jobs), icon = Icons.Default.Pause)
            }
            items(pausedJobs) { job ->
                JobResultCard(
                    job = job,
                    progress = progressMap[job.id] ?: JobProgress(),
                    logs = logsMap[job.id] ?: emptyList(),
                    onCancel = { force -> viewModel.cancelJob(job.id, force) },
                    onResume = { viewModel.resumeJob(job.id) }
                )
            }
        }
        PlatformVerticalScrollbar(
            lazyListState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        )
    }
}

@Composable
fun EndedTab(viewModel: JobViewModel) {
    val endedJobs by viewModel.endedJobs.collectAsState()
    val logsMap by viewModel.jobLogs.collectAsState(initial = emptyMap())
    val progressMap by viewModel.jobProgress.collectAsState(initial = emptyMap())
    val listState = rememberLazyListState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = { viewModel.clearAllEndedJobs() },
                enabled = endedJobs.isNotEmpty(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.Cancel, contentDescription = null)
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                Text(stringResource(Res.string.action_clear_all))
            }
        }

        if (endedJobs.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ToolkitEmptyState(
                    title = stringResource(Res.string.job_no_ended),
                    icon = Icons.Default.Inbox
                )
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(end = ToolkitTheme.dimensions.sduiScrollbarEndPadding),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
                ) {
                    item {
                        SectionHeader(title = stringResource(Res.string.job_ended_jobs), icon = Icons.Default.CheckCircle)
                    }
                    items(endedJobs) { job ->
                        JobResultCard(
                            job = job,
                            progress = progressMap[job.id] ?: JobProgress(),
                            logs = logsMap[job.id] ?: emptyList(),
                            onResume = if (job.status == JobStatus.Failed && job.resumeState != null) {
                                { viewModel.resumeJob(job.id) }
                            } else null,
                            onRestart = { viewModel.restartJob(job.id) },
                            onClear = { viewModel.clearEndedJob(job.id) }
                        )
                    }
                }
                PlatformVerticalScrollbar(
                    lazyListState = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
fun SchedulerTab() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ToolkitEmptyState(
            title = stringResource(Res.string.job_scheduler_soon),
            icon = Icons.Default.Schedule
        )
    }
}

@Composable
fun HistoryTab(viewModel: JobViewModel) {
    val history by viewModel.history.collectAsState()
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(end = ToolkitTheme.dimensions.sduiScrollbarEndPadding),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
        ) {
            items(history) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(ToolkitTheme.spacing.mediumSmall),
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
                            "Completed" -> ToolkitTheme.colors.success
                            "Failed" -> MaterialTheme.colorScheme.error
                            "Started" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        Icon(
                            icon,
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                        )
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.mediumSmall))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${entry.jobName}: ${entry.event}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val details = entry.details
                            if (details != null) {
                                Text(text = details, style = MaterialTheme.typography.labelSmall)
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
        PlatformVerticalScrollbar(
            lazyListState = listState,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
        )
    }
}

@Composable
fun StatusBadge(status: JobStatus) {
    val color = when (status) {
        JobStatus.Queued -> MaterialTheme.colorScheme.onSurfaceVariant
        JobStatus.Running -> MaterialTheme.colorScheme.primary
        JobStatus.PauseRequested -> MaterialTheme.colorScheme.primary
        JobStatus.Paused -> MaterialTheme.colorScheme.primary
        JobStatus.Completed -> ToolkitTheme.colors.success
        JobStatus.Failed -> MaterialTheme.colorScheme.error
        JobStatus.Cancelled -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    ToolkitChip(
        text = status.name,
        containerColor = color,
        contentColor = color,
        style = ToolkitChipStyle.Outlined,
        shape = RoundedCornerShape(ToolkitTheme.dimensions.settingsIconCornerRadius)
    )
}

@Composable
fun EmptyState(text: String, icon: ImageVector) {
    ToolkitEmptyState(title = text, icon = icon)
}

private fun formatTime(instant: Instant): String {
    val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localDateTime.hour.toString().padStart(2, '0')}:${
        localDateTime.minute.toString().padStart(2, '0')
    }:${localDateTime.second.toString().padStart(2, '0')}"
}
