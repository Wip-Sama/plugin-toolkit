package org.wip.plugintoolkit.features.board.ui

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
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.features.board.viewmodel.FlowEvent
import org.wip.plugintoolkit.features.board.viewmodel.FlowViewModel
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
            modifier = Modifier.width(250.dp).fillMaxHeight(),
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Flow", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                modifier = Modifier.padding(12.dp),
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
            modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(32.dp)
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

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("This will execute all nodes in the flow sequence.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Button(
                        onClick = { /* TODO: Trigger Flow Execution via JobManager */ },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Execute Flow")
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // History Area (Simulated for now, as JobManager integration needs specific setup)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
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
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear History")
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Placeholder for history items
                    Text("No runs recorded for this flow.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
