package org.wip.plugintoolkit.features.board.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.features.board.viewmodel.FlowEvent
import org.wip.plugintoolkit.features.board.viewmodel.FlowViewModel
import org.wip.plugintoolkit.shared.components.GlassCard
import org.wip.plugintoolkit.shared.components.SectionHeader

@Composable
fun FlowManagerView(
    viewModel: FlowViewModel,
    onEditFlow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var newFlowName by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(32.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader(
                title = "Flow Manager",
                icon = Icons.Default.Add, // Using Add as it represents the main action here
                modifier = Modifier.weight(1f)
            )

            Button(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create New Flow")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (state.flows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No flows created yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                state.flows.forEach { flow ->
                    FlowItem(
                        name = flow.name,
                        nodeCount = flow.nodes.size,
                        isSelected = state.selectedFlowId == flow.name,
                        onSelect = { viewModel.onEvent(FlowEvent.SelectFlow(flow.name)) },
                        onEdit = { 
                            viewModel.onEvent(FlowEvent.SelectFlow(flow.name))
                            onEditFlow()
                        },
                        onDelete = { viewModel.onEvent(FlowEvent.DeleteFlow(flow.name)) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Flow") },
            text = {
                TextField(
                    value = newFlowName,
                    onValueChange = { newFlowName = it },
                    label = { Text("Flow Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newFlowName.isNotBlank()) {
                        viewModel.onEvent(FlowEvent.CreateFlow(newFlowName))
                        showCreateDialog = false
                        newFlowName = ""
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FlowItem(
    name: String,
    nodeCount: Int,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onSelect,
//        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("$nodeCount nodes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
