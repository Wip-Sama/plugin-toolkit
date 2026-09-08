package org.wip.plugintoolkit.features.flows.ui.node

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.ui.PortCircle
import org.wip.plugintoolkit.features.flows.model.ValidationError
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.node_results_section

@Composable
fun NodeOutputSection(
    outputs: List<OutputPort>,
    isOutputsCollapsed: Boolean,
    onToggleOutputsCollapse: () -> Unit,
    node: Node,
    headerColor: Color,
    inferredTypes: Map<Pair<Long, String>, DataType>,
    inferredSemanticTypes: Map<Pair<Long, String>, List<SemanticType>>,
    validationErrors: List<ValidationError>,
    highlightedPortId: String?,
    highlightedPortColor: Color?,
    isReadOnly: Boolean,
    onStartConnection: (Long, String, Boolean) -> Unit,
    onDragConnection: (Offset) -> Unit,
    onDropConnection: (Boolean) -> Unit,
    onPortPositioned: (Long, String, Boolean, LayoutCoordinates) -> Unit,
    inactiveConnectedPortIds: Set<String> = emptySet(),
    onPortDisposed: (Long, String, Boolean) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    if (outputs.isEmpty()) return

    DisposableEffect(node.id, isOutputsCollapsed, outputs) {
        onDispose {
            if (isOutputsCollapsed) {
                outputs.forEach { output ->
                    onPortDisposed(node.id, output.id, true)
                }
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggleOutputsCollapse() }
            .testTag("output_section_${node.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(Res.string.node_results_section),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isOutputsCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = "Toggle Outputs"
            )
            if (isOutputsCollapsed) {
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                PortCircle(
                    color = headerColor,
                    isHighlighted = false,
                    onDragStart = {}, onDrag = {}, onDragEnd = {},
                    modifier = Modifier.onGloballyPositioned { coords ->
                        outputs.forEach { output ->
                            onPortPositioned(node.id, output.id, true, coords)
                        }
                    }
                )
            }
        }
    }

    if (!isOutputsCollapsed) {
        outputs.forEach { output ->
            OutputPortRow(
                output = output,
                node = node,
                headerColor = headerColor,
                inferredTypes = inferredTypes,
                inferredSemanticTypes = inferredSemanticTypes,
                validationErrors = validationErrors,
                highlightedPortId = highlightedPortId,
                highlightedPortColor = highlightedPortColor,
                isReadOnly = isReadOnly,
                onStartConnection = onStartConnection,
                onDragConnection = onDragConnection,
                onDropConnection = onDropConnection,
                onPortPositioned = onPortPositioned,
                isInactiveAndConnected = inactiveConnectedPortIds.contains(output.id),
                onPortDisposed = onPortDisposed
            )
        }
    }
}
