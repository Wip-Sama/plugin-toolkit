package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.CompatibilityResult
import org.wip.plugintoolkit.api.canConvert
import org.wip.plugintoolkit.api.checkSemanticCompatibility
import org.wip.plugintoolkit.api.format
import org.wip.plugintoolkit.api.isCompatibleWith
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.shared.components.ToolkitChip
import org.wip.plugintoolkit.shared.components.ToolkitChipStyle
import org.wip.plugintoolkit.shared.components.menu.ToolkitDropdownMenu
import org.wip.plugintoolkit.shared.components.menu.ToolkitDropdownMenuItem
import org.wip.plugintoolkit.shared.components.verticalFadingEdges
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.replace_node_confirm
import plugintoolkit.composeapp.generated.resources.replace_node_cancel
import plugintoolkit.composeapp.generated.resources.replace_node_dialog_title
import plugintoolkit.composeapp.generated.resources.replace_node_disconnect
import plugintoolkit.composeapp.generated.resources.replace_node_incoming_connections
import plugintoolkit.composeapp.generated.resources.replace_node_migrate_connections
import plugintoolkit.composeapp.generated.resources.replace_node_no_connections
import plugintoolkit.composeapp.generated.resources.replace_node_no_matches
import plugintoolkit.composeapp.generated.resources.replace_node_outgoing_connections
import plugintoolkit.composeapp.generated.resources.replace_node_search_placeholder
import plugintoolkit.composeapp.generated.resources.replace_node_select_target

private fun getPaletteNodeDisplayName(node: PaletteNode): String {
    return when (node) {
        is PaletteNode.Capability -> node.capability.name
        is PaletteNode.System -> node.action
        is PaletteNode.FlowInput -> "Flow Input"
        is PaletteNode.FlowOutput -> "Flow Output"
        is PaletteNode.SubFlow -> node.name
    }
}

private fun getPaletteNodeSubtitle(node: PaletteNode): String {
    return when (node) {
        is PaletteNode.Capability -> node.pluginInfo.name
        is PaletteNode.System -> "System Action"
        is PaletteNode.FlowInput -> "Flow Boundary"
        is PaletteNode.FlowOutput -> "Flow Boundary"
        is PaletteNode.SubFlow -> "SubFlow"
    }
}

private const val PORTS_LIST_HEIGHT_RATIO = 0.6f
private const val SEARCH_DROPDOWN_HEIGHT_DIVISOR = 2

@Composable
fun ReplaceNodeDialog(
    originalNode: Node,
    allNodes: List<Node>,
    connections: List<Connection>,
    availablePaletteNodes: List<PaletteNode>,
    availableFlows: List<Flow> = emptyList(),
    onConfirm: (newNode: Node, inputMappings: Map<String, String?>, outputMappings: Map<String, String?>) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var isPickerMenuOpen by remember { mutableStateOf(false) }
    var selectedPaletteNode by remember { mutableStateOf<PaletteNode?>(null) }

    val prototypeTargetNode: Node? = remember(selectedPaletteNode) {
        selectedPaletteNode?.toNode(originalNode.id, originalNode.position, availableFlows)
    }

    val connectedInputs = remember(originalNode, connections) {
        originalNode.inputs.filter { input ->
            connections.any { it.targetNodeId == originalNode.id && it.targetPortId == input.id }
        }
    }

    val connectedOutputs = remember(originalNode, connections) {
        originalNode.outputs.filter { output ->
            connections.any { it.sourceNodeId == originalNode.id && it.sourcePortId == output.id }
        }
    }

    // Mapping states: originalPortId -> targetPortId? (null = disconnect)
    val inputMappings = remember { mutableStateMapOf<String, String?>() }
    val outputMappings = remember { mutableStateMapOf<String, String?>() }

    // When prototypeTargetNode changes, calculate valid candidates and auto-detect
    LaunchedEffect(prototypeTargetNode) {
        inputMappings.clear()
        outputMappings.clear()

        if (prototypeTargetNode != null) {
            // Reconcile inputs
            for (input in connectedInputs) {
                val incomingConns = connections.filter { it.targetNodeId == originalNode.id && it.targetPortId == input.id }
                val validCandidates = prototypeTargetNode.inputs.filter { candidate ->
                    incomingConns.all { conn ->
                        val srcNode = allNodes.find { it.id == conn.sourceNodeId }
                        val srcPort = srcNode?.outputs?.find { it.id == conn.sourcePortId }
                        val srcType = srcPort?.dataType ?: input.dataType
                        val srcSem = srcPort?.semanticTypes ?: input.semanticTypes
                        val typeOk = srcType.isCompatibleWith(candidate.dataType) || srcType.canConvert(candidate.dataType)
                        val semCheck = checkSemanticCompatibility(srcSem, candidate.semanticTypes)
                        typeOk && semCheck !is CompatibilityResult.Incompatible
                    }
                }

                // Auto-detect by name
                val nameMatch = validCandidates.find { it.name.equals(input.name, ignoreCase = true) }
                inputMappings[input.id] = nameMatch?.id
            }

            // Reconcile outputs
            for (output in connectedOutputs) {
                val outgoingConns = connections.filter { it.sourceNodeId == originalNode.id && it.sourcePortId == output.id }
                val validCandidates = prototypeTargetNode.outputs.filter { candidate ->
                    outgoingConns.all { conn ->
                        val tgtNode = allNodes.find { it.id == conn.targetNodeId }
                        val tgtPort = tgtNode?.inputs?.find { it.id == conn.targetPortId }
                        val tgtType = tgtPort?.dataType ?: output.dataType
                        val tgtSem = tgtPort?.semanticTypes ?: output.semanticTypes
                        val typeOk = candidate.dataType.isCompatibleWith(tgtType) || candidate.dataType.canConvert(tgtType)
                        val semCheck = checkSemanticCompatibility(candidate.semanticTypes, tgtSem)
                        typeOk && semCheck !is CompatibilityResult.Incompatible
                    }
                }

                // Auto-detect by name
                val nameMatch = validCandidates.find { it.name.equals(output.name, ignoreCase = true) }
                outputMappings[output.id] = nameMatch?.id
            }
        }
    }

    val filteredPaletteNodes = remember(availablePaletteNodes, searchQuery, selectedPaletteNode) {
        val currentNode = selectedPaletteNode
        val isJustSelectedNodeName = currentNode != null && searchQuery == getPaletteNodeDisplayName(currentNode)
        if (searchQuery.isBlank() || isJustSelectedNodeName) {
            availablePaletteNodes
        } else {
            availablePaletteNodes.filter { node ->
                getPaletteNodeDisplayName(node).contains(searchQuery, ignoreCase = true) ||
                        getPaletteNodeSubtitle(node).contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(min = ToolkitTheme.dimensions.dialogMaxWidthMedium, max = ToolkitTheme.dimensions.dialogMaxWidthMedium)
            .heightIn(max = ToolkitTheme.dimensions.dialogUpdateMaxHeight)
            .testTag("replace_node_dialog"),
        title = {
            Column {
                Text(
                    text = stringResource(Res.string.replace_node_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = originalNode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
            ) {
                // Section 1: Replacement Node Selection (no Card wrapper)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                ) {
                    Text(
                        text = stringResource(Res.string.replace_node_search_placeholder),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val searchFieldWidth = maxWidth

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                isPickerMenuOpen = true
                            },
                            placeholder = { Text(stringResource(Res.string.replace_node_search_placeholder)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                )
                            },
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = {
                                                searchQuery = ""
                                                isPickerMenuOpen = true
                                            },
                                            modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { isPickerMenuOpen = !isPickerMenuOpen },
                                        modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                                    ) {
                                        Icon(
                                            imageVector = if (isPickerMenuOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = ToolkitTheme.shapes.pill,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { if (it.isFocused) isPickerMenuOpen = true }
                                .testTag("replace_node_search_field"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.chipOutlinedBackground),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.chipOutlinedBackground)
                            )
                        )

                        ToolkitDropdownMenu(
                            expanded = isPickerMenuOpen,
                            onDismissRequest = { isPickerMenuOpen = false },
                            properties = PopupProperties(
                                focusable = false,
                                dismissOnClickOutside = true,
                                dismissOnBackPress = true
                            ),
                            modifier = Modifier
                                .width(searchFieldWidth)
                                .heightIn(max = ToolkitTheme.dimensions.dialogUpdateHeight / SEARCH_DROPDOWN_HEIGHT_DIVISOR)
                        ) {
                            if (filteredPaletteNodes.isEmpty()) {
                                ToolkitDropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(Res.string.replace_node_no_matches),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    },
                                    onClick = { isPickerMenuOpen = false }
                                )
                            } else {
                                filteredPaletteNodes.forEach { paletteNode ->
                                    ToolkitDropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = getPaletteNodeDisplayName(paletteNode),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = getPaletteNodeSubtitle(paletteNode),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        leadingIcon = {
                                            val icon = when (paletteNode) {
                                                is PaletteNode.Capability -> Icons.Default.Cable
                                                is PaletteNode.System -> Icons.Default.Settings
                                                is PaletteNode.FlowInput -> Icons.Default.Login
                                                is PaletteNode.FlowOutput -> Icons.Default.Logout
                                                is PaletteNode.SubFlow -> Icons.Default.AccountTree
                                            }
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                            )
                                        },
                                        isSelected = selectedPaletteNode == paletteNode,
                                        onClick = {
                                            selectedPaletteNode = paletteNode
                                            searchQuery = getPaletteNodeDisplayName(paletteNode)
                                            isPickerMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    selectedPaletteNode?.let { currentNode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = ToolkitTheme.opacity.chipOutlinedBackground),
                                    ToolkitTheme.shapes.medium
                                )
                                .padding(horizontal = ToolkitTheme.spacing.medium, vertical = ToolkitTheme.spacing.small)
                        ) {
                            val icon = when (currentNode) {
                                is PaletteNode.Capability -> Icons.Default.Cable
                                is PaletteNode.System -> Icons.Default.Settings
                                is PaletteNode.FlowInput -> Icons.Default.Login
                                is PaletteNode.FlowOutput -> Icons.Default.Logout
                                is PaletteNode.SubFlow -> Icons.Default.AccountTree
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = getPaletteNodeDisplayName(currentNode),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = getPaletteNodeSubtitle(currentNode),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = ToolkitTheme.opacity.secondaryText)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
                )

                // Section 2: Connection Migration (no Card wrapper)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                ) {
                    Text(
                        text = stringResource(Res.string.replace_node_migrate_connections),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    if (prototypeTargetNode == null) {
                        Text(
                            text = stringResource(Res.string.replace_node_select_target),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (connectedInputs.isEmpty() && connectedOutputs.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.replace_node_no_connections),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val portsScrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = ToolkitTheme.dimensions.dialogUpdateHeight * PORTS_LIST_HEIGHT_RATIO)
                                .verticalFadingEdges(
                                    scrollState = portsScrollState,
                                    topFadeLength = ToolkitTheme.spacing.medium,
                                    bottomFadeLength = ToolkitTheme.spacing.medium,
                                    almostOpaque = ToolkitTheme.opacity.almostOpaque
                                )
                                .verticalScroll(portsScrollState),
                            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                        ) {
                            // Incoming Connections
                            if (connectedInputs.isNotEmpty()) {
                                Text(
                                    text = stringResource(Res.string.replace_node_incoming_connections),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                connectedInputs.forEach { inputPort ->
                                    val incomingConns = connections.filter { it.targetNodeId == originalNode.id && it.targetPortId == inputPort.id }
                                    val validCandidates = prototypeTargetNode.inputs.filter { candidate ->
                                        incomingConns.all { conn ->
                                            val srcNode = allNodes.find { it.id == conn.sourceNodeId }
                                            val srcPort = srcNode?.outputs?.find { it.id == conn.sourcePortId }
                                            val srcType = srcPort?.dataType ?: inputPort.dataType
                                            val srcSem = srcPort?.semanticTypes ?: inputPort.semanticTypes
                                            val typeOk = srcType.isCompatibleWith(candidate.dataType) || srcType.canConvert(candidate.dataType)
                                            val semCheck = checkSemanticCompatibility(srcSem, candidate.semanticTypes)
                                            typeOk && semCheck !is CompatibilityResult.Incompatible
                                        }
                                    }

                                    PortMigrationRow(
                                        originalName = inputPort.name,
                                        originalType = inputPort.dataType.format(),
                                        validCandidates = validCandidates.map { it.id to "${it.name} (${it.dataType.format()})" },
                                        selectedTargetPortId = inputMappings[inputPort.id],
                                        onSelectTargetPortId = { inputMappings[inputPort.id] = it }
                                    )
                                }
                            }

                            // Outgoing Connections
                            if (connectedOutputs.isNotEmpty()) {
                                if (connectedInputs.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.small))
                                }
                                Text(
                                    text = stringResource(Res.string.replace_node_outgoing_connections),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                connectedOutputs.forEach { outputPort ->
                                    val outgoingConns = connections.filter { it.sourceNodeId == originalNode.id && it.sourcePortId == outputPort.id }
                                    val validCandidates = prototypeTargetNode.outputs.filter { candidate ->
                                        outgoingConns.all { conn ->
                                            val tgtNode = allNodes.find { it.id == conn.targetNodeId }
                                            val tgtPort = tgtNode?.inputs?.find { it.id == conn.targetPortId }
                                            val tgtType = tgtPort?.dataType ?: outputPort.dataType
                                            val tgtSem = tgtPort?.semanticTypes ?: outputPort.semanticTypes
                                            val typeOk = candidate.dataType.isCompatibleWith(tgtType) || candidate.dataType.canConvert(tgtType)
                                            val semCheck = checkSemanticCompatibility(candidate.semanticTypes, tgtSem)
                                            typeOk && semCheck !is CompatibilityResult.Incompatible
                                        }
                                    }

                                    PortMigrationRow(
                                        originalName = outputPort.name,
                                        originalType = outputPort.dataType.format(),
                                        validCandidates = validCandidates.map { it.id to "${it.name} (${it.dataType.format()})" },
                                        selectedTargetPortId = outputMappings[outputPort.id],
                                        onSelectTargetPortId = { outputMappings[outputPort.id] = it }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (prototypeTargetNode != null) {
                        onConfirm(prototypeTargetNode, inputMappings.toMap(), outputMappings.toMap())
                    }
                },
                enabled = prototypeTargetNode != null,
                modifier = Modifier.testTag("replace_node_confirm_button")
            ) {
                Text(stringResource(Res.string.replace_node_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("replace_node_cancel_button")
            ) {
                Text(stringResource(Res.string.replace_node_cancel))
            }
        }
    )
}

@Composable
private fun PortMigrationRow(
    originalName: String,
    originalType: String,
    validCandidates: List<Pair<String, String>>,
    selectedTargetPortId: String?,
    onSelectTargetPortId: (String?) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ToolkitTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Original port info
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
        ) {
            Text(
                text = originalName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "($originalType)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Default.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier
                .padding(horizontal = ToolkitTheme.spacing.small)
                .size(ToolkitTheme.dimensions.iconSmall)
        )

        // Right: Target port dropdown or Disconnect chip
        Box(modifier = Modifier.weight(1.2f), contentAlignment = Alignment.CenterEnd) {
            if (validCandidates.isEmpty()) {
                ToolkitChip(
                    text = stringResource(Res.string.replace_node_disconnect),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    style = ToolkitChipStyle.Filled
                )
            } else {
                val currentText = validCandidates.find { it.first == selectedTargetPortId }?.second
                    ?: stringResource(Res.string.replace_node_disconnect)

                val isDisconnect = selectedTargetPortId == null

                OutlinedButton(
                    onClick = { menuOpen = true },
                    shape = ToolkitTheme.shapes.small,
                    contentPadding = PaddingValues(
                        horizontal = ToolkitTheme.spacing.small,
                        vertical = ToolkitTheme.spacing.extraSmall
                    ),
                    colors = if (isDisconnect) {
                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    } else {
                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = currentText,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconExtraSmall)
                    )
                }

                ToolkitDropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    validCandidates.forEach { (portId, label) ->
                        ToolkitDropdownMenuItem(
                            text = { Text(label, style = MaterialTheme.typography.bodySmall) },
                            isSelected = selectedTargetPortId == portId,
                            onClick = {
                                onSelectTargetPortId(portId)
                                menuOpen = false
                            }
                        )
                    }

                    ToolkitDropdownMenuItem(
                        text = {
                            Text(
                                stringResource(Res.string.replace_node_disconnect),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        isSelected = selectedTargetPortId == null,
                        isDestructive = true,
                        onClick = {
                            onSelectTargetPortId(null)
                            menuOpen = false
                        }
                    )
                }
            }
        }
    }
}
