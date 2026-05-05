package org.wip.plugintoolkit.features.landingPage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.job.logic.JobManager
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.plugin.viewmodel.PluginViewModel
import org.wip.plugintoolkit.shared.components.GlassCard
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.landing_active_jobs
import plugintoolkit.composeapp.generated.resources.landing_link_docs
import plugintoolkit.composeapp.generated.resources.landing_link_jobs
import plugintoolkit.composeapp.generated.resources.landing_link_plugins
import plugintoolkit.composeapp.generated.resources.landing_link_settings
import plugintoolkit.composeapp.generated.resources.landing_loaded_plugins
import plugintoolkit.composeapp.generated.resources.landing_no_activity
import plugintoolkit.composeapp.generated.resources.landing_queued_jobs
import plugintoolkit.composeapp.generated.resources.landing_quick_links
import plugintoolkit.composeapp.generated.resources.landing_recent_activity
import plugintoolkit.composeapp.generated.resources.landing_system_overview
import plugintoolkit.composeapp.generated.resources.landing_welcome_subtitle
import plugintoolkit.composeapp.generated.resources.landing_welcome_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LandingPage(
    modifier: Modifier = Modifier,
    viewModel: PluginViewModel = koinViewModel(),
    jobManager: JobManager = koinInject()
) {
    val jobs by jobManager.jobs.collectAsState()
    val loadedPlugins = viewModel.loadedPlugins

    val activeJobsCount = jobs.count { it.status == JobStatus.Running }
    val queuedJobsCount = jobs.count { it.status == JobStatus.Queued }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ToolkitTheme.spacing.extraLarge)
    ) {
        // Hero Section
        DashboardHero()

        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.huge))

        // Stats Grid
        Text(
            stringResource(Res.string.landing_system_overview),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = ToolkitTheme.spacing.medium)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.large)
        ) {
            StatCard(
                title = stringResource(Res.string.landing_loaded_plugins),
                value = loadedPlugins.size.toString(),
                icon = Icons.Default.Extension,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(Res.string.landing_active_jobs),
                value = activeJobsCount.toString(),
                icon = Icons.Default.PlayArrow,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = stringResource(Res.string.landing_queued_jobs),
                value = queuedJobsCount.toString(),
                icon = Icons.Default.List,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(ToolkitTheme.spacing.huge))

        // Quick Actions or Recent Activity
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.large)) {
            GlassCard(modifier = Modifier.weight(1.5f).height(300.dp)) {
                Column(modifier = Modifier.padding(ToolkitTheme.spacing.medium)) {
                    Text(
                        stringResource(Res.string.landing_recent_activity),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

                    if (jobs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(Res.string.landing_no_activity),
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.mediumSmall)) {
                            jobs.take(5).forEach { job ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.mediumSmall))
                                    Text(job.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(
                                        job.status.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }

            GlassCard(modifier = Modifier.weight(1f).height(300.dp)) {
                Column(modifier = Modifier.padding(ToolkitTheme.spacing.medium)) {
                    Text(
                        stringResource(Res.string.landing_quick_links),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))

                    QuickLinkItem(stringResource(Res.string.landing_link_jobs), Icons.Default.Dashboard)
                    QuickLinkItem(stringResource(Res.string.landing_link_plugins), Icons.Default.SettingsInputComponent)
                    QuickLinkItem(stringResource(Res.string.landing_link_settings), Icons.Default.Settings)
                    QuickLinkItem(stringResource(Res.string.landing_link_docs), Icons.Default.Info)
                }
            }
        }
    }
}

@Composable
fun DashboardHero() {
    val brush = Brush.horizontalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(brush)
            .padding(ToolkitTheme.spacing.extraLarge),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(
                stringResource(Res.string.landing_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                stringResource(Res.string.landing_welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier = modifier) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = color)
            }
            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.medium))
            Column {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun QuickLinkItem(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Navigate */ }
            .padding(vertical = ToolkitTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.mediumSmall))
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

@Preview
@Composable
private fun LandingPagePreview() {
    MaterialTheme {
        LandingPage()
    }
}


