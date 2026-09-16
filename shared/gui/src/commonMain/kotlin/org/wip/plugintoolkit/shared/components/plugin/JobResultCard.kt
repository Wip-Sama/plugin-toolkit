package org.wip.plugintoolkit.shared.components.plugin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobProgress
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.shared.components.ToolkitCard

/**
 * Result card displaying execution details, progress, output, and console logs for background jobs.
 */
@Composable
fun JobResultCard(
    job: BackgroundJob,
    progress: JobProgress,
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
    val scrollState = rememberScrollState()
    var autoScroll by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    val isAtBottom by remember {
        derivedStateOf {
            scrollState.value >= (scrollState.maxValue - 20)
        }
    }

    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress && !isAtBottom && autoScroll) {
            autoScroll = false
        }
    }

    LaunchedEffect(logs.size, logs.lastOrNull()) {
        if (autoScroll && logs.isNotEmpty()) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    val hasDetails = job.status == JobStatus.Completed ||
            job.status == JobStatus.Failed ||
            job.status == JobStatus.Cancelled ||
            job.executionMetrics != null ||
            logs.isNotEmpty()

    val hasActions = onPause != null || onResume != null || onCancel != null || onClear != null

    ToolkitCard(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(modifier = Modifier.padding(ToolkitTheme.spacing.medium)) {
            // Header Row
            JobResultCardHeader(
                job = job,
                expanded = expanded,
                hasDetails = hasDetails,
                onToggleExpand = { expanded = !expanded },
                onDelete = onDelete
            )

            // Progress bar for running jobs
            if (job.status == JobStatus.Running || job.status == JobStatus.Queued) {
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                JobResultProgressSection(
                    job = job,
                    progress = progress
                )
            }

            // Expanded Details Section
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 40, easing = LinearOutSlowInEasing)) +
                        expandVertically(
                            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                            expandFrom = Alignment.Top
                        ),
                exit = fadeOut(animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing)) +
                        shrinkVertically(
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                            shrinkTowards = Alignment.Top
                        )
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                    HorizontalDivider(Modifier, DividerDefaults.Thickness, color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

                    // Execution Info Section
                    ExecutionInfoSection(job = job)
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

                    // Outputs / Error Section
                    JobResultOutputsSection(job = job)

                    // Console logs if present
                    if (logs.isNotEmpty()) {
                        JobResultConsoleSection(
                            logs = logs,
                            scrollState = scrollState,
                            logHeight = logHeight,
                            onLogHeightChange = { logHeight = it },
                            minLogHeight = minLogHeight,
                            maxLogHeight = maxLogHeight
                        )
                    }
                }
            }

            // Action buttons row
            if (hasActions || expanded) {
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                JobResultActionsRow(
                    job = job,
                    logs = logs,
                    expanded = expanded,
                    autoScroll = autoScroll,
                    onAutoScrollChange = { autoScroll = it },
                    onScrollToBottom = {
                        coroutineScope.launch {
                            scrollState.scrollTo(scrollState.maxValue)
                        }
                    },
                    onPause = onPause,
                    onResume = onResume,
                    onCancel = onCancel,
                    onClear = onClear
                )
            }
        }
    }
}
