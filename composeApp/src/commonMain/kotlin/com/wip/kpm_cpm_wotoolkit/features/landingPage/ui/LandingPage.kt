package com.wip.kpm_cpm_wotoolkit.features.landingPage.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wip.kpm_cpm_wotoolkit.features.plugin.viewmodel.PluginViewModel
import com.wip.kpm_cpm_wotoolkit.features.job.logic.JobManager
import com.wip.kpm_cpm_wotoolkit.shared.components.GlassCard
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.ui.tooling.preview.Preview
import com.wip.kpm_cpm_wotoolkit.features.job.model.JobStatus
import com.wip.kpm_cpm_wotoolkit.core.theme.WOTheme

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
            .padding(WOTheme.spacing.extraLarge)
    ) {
        // Hero Section
        DashboardHero()

        Spacer(modifier = Modifier.height(WOTheme.spacing.huge))

        // Stats Grid
        Text(
            "System Overview",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = WOTheme.spacing.medium)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(WOTheme.spacing.large)
        ) {
            StatCard(
                title = "Loaded Modules",
                value = loadedPlugins.size.toString(),
                icon = Icons.Default.Extension,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Active Jobs",
                value = activeJobsCount.toString(),
                icon = Icons.Default.PlayArrow,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Queued Jobs",
                value = queuedJobsCount.toString(),
                icon = Icons.Default.List,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(WOTheme.spacing.huge))

        // Quick Actions or Recent Activity
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(WOTheme.spacing.large)) {
            GlassCard(modifier = Modifier.weight(1.5f).height(300.dp)) {
                Column(modifier = Modifier.padding(WOTheme.spacing.medium)) {
                    Text("Recent Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(WOTheme.spacing.medium))
                    
                    if (jobs.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No recent activity", color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(WOTheme.spacing.mediumSmall)) {
                            jobs.take(5).forEach { job ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        modifier = Modifier.size(WOTheme.dimensions.iconSmall),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(WOTheme.spacing.mediumSmall))
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
                Column(modifier = Modifier.padding(WOTheme.spacing.medium)) {
                    Text("Quick Links", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(WOTheme.spacing.medium))
                    
                    QuickLinkItem("Open Job Dashboard", Icons.Default.Dashboard)
                    QuickLinkItem("View All Modules", Icons.Default.SettingsInputComponent)
                    QuickLinkItem("System Settings", Icons.Default.Settings)
                    QuickLinkItem("Documentation", Icons.Default.Info)
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
            .padding(WOTheme.spacing.extraLarge),
        contentAlignment = Alignment.CenterStart
    ) {
        Column {
            Text(
                "Welcome to WOToolkit",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "Your central hub for module orchestration and automation.",
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
            Spacer(modifier = Modifier.width(WOTheme.spacing.medium))
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
            .padding(vertical = WOTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(WOTheme.spacing.mediumSmall))
        Text(title, style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(WOTheme.dimensions.iconSmall),
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


