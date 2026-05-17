package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEvent
import org.wip.plugintoolkit.features.flows.viewmodel.FlowViewModel
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.plugin.ui.JobResultItem
import org.wip.plugintoolkit.shared.components.GlassCard
import org.wip.plugintoolkit.shared.components.SectionHeader

@Composable
fun FlowRunnerView(
    viewModel: FlowViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    
    // Using a simple selection for flows to run
    var selectedFlowToRun by remember { mutableStateOf(state.flows.firstOrNull()) }
    
    // We would need to integrate with JobManager here. 
    // For this demonstration, I'll assume viewModel has access or I'll simulate.
    // In a real implementation, we'd pull jobs filtered by JobType.Flow and flowName.
    
    Row(modifier = modifier.fillMaxSize()) {
        // Sidebar: Select Flow to Run
        Surface(
            modifier = Modifier.width(ToolkitTheme.dimensions.sidebarExpandedWidth).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(ToolkitTheme.dimensions.cardElevation)
        ) {
            Column(modifier = Modifier.padding(ToolkitTheme.spacing.medium)) {
                Text("Select Flow", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)) {
                    state.flows.forEach { flow ->
                        val isSelected = selectedFlowToRun?.name == flow.name
                        Surface(
                            onClick = { selectedFlowToRun = flow },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                flow.name,
                                modifier = Modifier.padding(ToolkitTheme.spacing.mediumSmall),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }

        // Main Area: Run and History
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(ToolkitTheme.spacing.extraLarge)
        ) {
            val currentFlow = selectedFlowToRun
            if (currentFlow == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a flow to see options", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // Runner Card
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader(
                            title = "Run Flow: ${currentFlow.name}",
                            icon = Icons.Default.PlayArrow,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.medium))
                    Text("This will execute all nodes in the flow sequence.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.large))
                    
                    Button(
                        onClick = { /* TODO: Trigger Flow Execution via JobManager */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                        Text("Execute Flow")
                    }
                }

                Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraLarge))

                // History Area (Simulated for now, as JobManager integration needs specific setup)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = ToolkitTheme.spacing.medium),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Execution History",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    TextButton(onClick = { /* viewModel.clearHistory(currentFlow.name) */ }) {
                        Icon(Icons.Default.ClearAll, contentDescription = null)
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                        Text("Clear History")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)) {
                    // Placeholder for history items
                    Text("No runs recorded for this flow.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
