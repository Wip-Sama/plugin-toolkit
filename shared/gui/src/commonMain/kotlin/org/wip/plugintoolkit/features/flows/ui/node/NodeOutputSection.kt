package org.wip.plugintoolkit.features.flows.ui.node

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    highlightedPortId: String? = null,
    highlightedPortIds: Set<String> = emptySet(),
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

    val effectiveHighlightedPortIds = remember(highlightedPortId, highlightedPortIds) {
        if (highlightedPortId != null) highlightedPortIds + highlightedPortId else highlightedPortIds
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ToolkitTheme.shapes.small)
                .clickable { onToggleOutputsCollapse() }
                .padding(horizontal = ToolkitTheme.spacing.extraSmall, vertical = ToolkitTheme.spacing.extraSmall)
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
                Surface(
                    shape = ToolkitTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    border = BorderStroke(
                        width = ToolkitTheme.dimensions.borderThin,
                        color = MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isOutputsCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = "Toggle Outputs",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                        )
                    }
                }
                if (isOutputsCollapsed) {
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                    PortCircle(
                        color = headerColor,
                        isHighlighted = outputs.any { effectiveHighlightedPortIds.contains(it.id) },
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

        AnimatedVisibility(
            visible = !isOutputsCollapsed,
            enter = fadeIn(animationSpec = tween(durationMillis = 180, delayMillis = 30, easing = LinearOutSlowInEasing)) +
                    expandVertically(
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                        expandFrom = Alignment.Top
                    ),
            exit = fadeOut(animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)) +
                   shrinkVertically(
                       animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                       shrinkTowards = Alignment.Top
                   )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ToolkitTheme.spacing.small),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.mediumSmall)
            ) {
                outputs.forEach { output ->
                    OutputPortRow(
                        output = output,
                        node = node,
                        headerColor = headerColor,
                        inferredTypes = inferredTypes,
                        inferredSemanticTypes = inferredSemanticTypes,
                        validationErrors = validationErrors,
                        highlightedPortId = if (effectiveHighlightedPortIds.contains(output.id)) output.id else null,
                        highlightedPortColor = highlightedPortColor,
                        isReadOnly = isReadOnly,
                        onStartConnection = onStartConnection,
                        onDragConnection = onDragConnection,
                        onDropConnection = onDropConnection,
                        onPortPositioned = if (isOutputsCollapsed) { _, _, _, _ -> } else onPortPositioned,
                        isInactiveAndConnected = inactiveConnectedPortIds.contains(output.id),
                        onPortDisposed = onPortDisposed
                    )
                }
            }
        }
    }
}
