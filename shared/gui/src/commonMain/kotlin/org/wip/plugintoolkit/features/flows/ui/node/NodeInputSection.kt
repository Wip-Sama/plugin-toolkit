package org.wip.plugintoolkit.features.flows.ui.node

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.ui.PortCircle
import org.wip.plugintoolkit.features.flows.model.ValidationError

@Composable
fun NodeInputSection(
    sectionType: InputSectionType,
    title: String,
    inputs: List<InputPort>,
    isSectionCollapsed: Boolean,
    onToggleCollapse: () -> Unit,
    node: Node,
    headerColor: Color,
    connectedInputPortIds: Set<String>,
    inferredTypes: Map<Pair<Long, String>, DataType>,
    inferredSemanticTypes: Map<Pair<Long, String>, List<SemanticType>>,
    validationErrors: List<ValidationError>,
    providedSettings: Map<String, kotlinx.serialization.json.JsonElement>,
    highlightedPortId: String? = null,
    highlightedPortIds: Set<String> = emptySet(),
    highlightedPortColor: Color?,
    isReadOnly: Boolean,
    onStartConnection: (Long, String, Boolean) -> Unit,
    onDragConnection: (Offset) -> Unit,
    onDropConnection: (Boolean) -> Unit,
    onPortPositioned: (Long, String, Boolean, LayoutCoordinates) -> Unit,
    onUpdateValue: (Long, String, Any?) -> Unit,
    onFocusLost: () -> Unit,
    onUpdateInputPortDefault: (Long, String, Any?) -> Unit = { _, _, _ -> },
    inactiveConnectedPortIds: Set<String> = emptySet(),
    onPortDisposed: (Long, String, Boolean) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    DisposableEffect(node.id, isSectionCollapsed, inputs) {
        onDispose {
            if (isSectionCollapsed) {
                inputs.forEach { input ->
                    onPortDisposed(node.id, input.id, false)
                    if (sectionType == InputSectionType.OUTPUT_LOCATIONS) {
                        val correspondingOutput = node.outputs.find { it.id == input.id }
                        if (correspondingOutput != null) {
                            onPortDisposed(node.id, correspondingOutput.id, true)
                        }
                    }
                }
            }
        }
    }
    val sectionTag = "input_section_${sectionType.name.lowercase()}_${node.id}"

    val effectiveHighlightedPortIds = remember(highlightedPortId, highlightedPortIds) {
        if (highlightedPortId != null) highlightedPortIds + highlightedPortId else highlightedPortIds
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onToggleCollapse() }
            .testTag(sectionTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isSectionCollapsed) {
                PortCircle(
                    color = headerColor,
                    isHighlighted = inputs.any { effectiveHighlightedPortIds.contains(it.id) },
                    onDragStart = {}, onDrag = {}, onDragEnd = {},
                    modifier = Modifier.onGloballyPositioned { coords ->
                        inputs.forEach { input ->
                            onPortPositioned(node.id, input.id, false, coords)
                        }
                    }
                )
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
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
                        imageVector = if (isSectionCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                        contentDescription = "Toggle $title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                    )
                }
            }
            if (isSectionCollapsed && sectionType == InputSectionType.OUTPUT_LOCATIONS) {
                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                PortCircle(
                    color = headerColor,
                    isHighlighted = inputs.any { effectiveHighlightedPortIds.contains(it.id) },
                    onDragStart = {}, onDrag = {}, onDragEnd = {},
                    modifier = Modifier.onGloballyPositioned { coords ->
                        inputs.forEach { input ->
                            val correspondingOutput = node.outputs.find { it.id == input.id }
                            if (correspondingOutput != null) {
                                onPortPositioned(node.id, correspondingOutput.id, true, coords)
                            }
                        }
                    }
                )
            }
        }
    }

    AnimatedVisibility(
        visible = !isSectionCollapsed,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.mediumSmall)
        ) {
            inputs.forEach { input ->
                InputPortRow(
                    input = input,
                    node = node,
                    headerColor = headerColor,
                    connectedInputPortIds = connectedInputPortIds,
                    inferredTypes = inferredTypes,
                    inferredSemanticTypes = inferredSemanticTypes,
                    validationErrors = validationErrors,
                    providedSettings = providedSettings,
                    highlightedPortId = if (effectiveHighlightedPortIds.contains(input.id)) input.id else null,
                    highlightedPortColor = highlightedPortColor,
                    isReadOnly = isReadOnly,
                    onStartConnection = onStartConnection,
                    onDragConnection = onDragConnection,
                    onDropConnection = onDropConnection,
                    onPortPositioned = onPortPositioned,
                    onUpdateValue = onUpdateValue,
                    onFocusLost = onFocusLost,
                    onUpdateInputPortDefault = onUpdateInputPortDefault,
                    isInactiveAndConnected = inactiveConnectedPortIds.contains(input.id),
                    onPortDisposed = onPortDisposed
                )
            }
        }
    }
}
