package org.wip.plugintoolkit.features.flows.ui.node

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.ParameterMetadata
import org.wip.plugintoolkit.api.ParameterRole
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.logic.PathPatternResolver
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.NodeSerializationUtils
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.ui.PortCircle
import org.wip.plugintoolkit.features.flows.ui.formatDataType
import org.wip.plugintoolkit.features.flows.ui.getPortValueString
import org.wip.plugintoolkit.features.flows.viewmodel.ValidationError
import org.wip.plugintoolkit.features.plugin.utils.SettingsUtils
import org.wip.plugintoolkit.shared.components.TooltipArea
import org.wip.plugintoolkit.shared.components.plugin.DynamicParameterInput

@Composable
fun InputPortRow(
    input: InputPort,
    node: Node,
    headerColor: Color,
    connectedInputPortIds: Set<String>,
    inferredTypes: Map<Pair<Long, String>, DataType>,
    inferredSemanticTypes: Map<Pair<Long, String>, List<SemanticType>>,
    validationErrors: List<ValidationError>,
    providedSettings: Map<String, kotlinx.serialization.json.JsonElement>,
    providedLocks: Map<String, Boolean> = emptyMap(),
    highlightedPortId: String?,
    highlightedPortColor: Color?,
    isReadOnly: Boolean,
    onStartConnection: (Long, String, Boolean) -> Unit,
    onDragConnection: (Offset) -> Unit,
    onDropConnection: (Boolean) -> Unit,
    onPortPositioned: (Long, String, Boolean, LayoutCoordinates) -> Unit,
    onUpdateValue: (Long, String, Any?) -> Unit,
    onFocusLost: () -> Unit
) {
    val pluginManager: org.wip.plugintoolkit.features.plugin.logic.PluginManager = org.koin.compose.koinInject()
    val pluginLocksState by pluginManager.pluginLocksState.collectAsState()
    val effectiveLocks = remember(providedLocks, pluginLocksState) {
        if (providedLocks.isNotEmpty()) providedLocks
        else pluginLocksState.values.fold(emptyMap<String, Boolean>()) { acc, map -> acc + map }
    }


    val currentPortValue = input.value ?: input.defaultValue

    val portErrors = validationErrors.filter {
        (it.sourceNodeId == node.id && it.sourcePortId == input.id) ||
                (it.targetNodeId == node.id && it.targetPortId == input.id)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ToolkitTheme.dimensions.pluginIcon)
            .padding(vertical = ToolkitTheme.spacing.extraSmall)
            .testTag("port_row_${node.id}_${input.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            PortCircle(
                color = if (highlightedPortId == input.id) (highlightedPortColor ?: headerColor) else headerColor,
                isHighlighted = highlightedPortId == input.id,
                onDragStart = {
                    if (!isReadOnly) onStartConnection(node.id, input.id, false)
                },
                onDrag = { if (!isReadOnly) onDragConnection(it) },
                onDragEnd = { if (!isReadOnly) onDropConnection(it) },
                modifier = Modifier
                    .testTag("port_${node.id}_${input.id}_input")
                    .onGloballyPositioned { coords ->
                        onPortPositioned(node.id, input.id, false, coords)
                    }
            )
            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
            Column(modifier = Modifier.weight(1f)) {
                val capNode = node as? Node.CapabilityNode
                val paramMetadata = capNode?.capability?.parameters?.get(input.id)
                val canBeAutogenerated = paramMetadata?.autogeneratedPattern != null &&
                        PathPatternResolver.canResolve(paramMetadata.autogeneratedPattern!!, capNode?.capability?.parameters?.keys ?: emptySet())
                val isRequired = if (capNode != null) {
                    paramMetadata?.required == true && !canBeAutogenerated
                } else {
                    input.isRequired
                }
                val displaySuffix = when {
                    isRequired -> " *"
                    else -> ""
                }
                val displayName = "${input.name}$displaySuffix"
                val labelColor = if (canBeAutogenerated) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = ToolkitTheme.opacity.disabled)
                } else {
                    MaterialTheme.colorScheme.onSurface
                }

                if (!input.description.isNullOrBlank()) {
                    TooltipArea(
                        delayMillis = 3000,
                        tooltip = {
                            Text(
                                text = input.description!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    ) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = labelColor
                        )
                    }
                } else {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = labelColor
                    )
                }
                val inferredType = inferredTypes[Pair(node.id, input.id)] ?: input.dataType
                val typeLabel =
                    if (input.dataType is DataType.Primitive && input.dataType.primitiveType == PrimitiveType.ANY &&
                        !(inferredType is DataType.Primitive && inferredType.primitiveType == PrimitiveType.ANY)
                    ) {
                        "${formatDataType(inferredType)} (Implied)"
                    } else {
                        formatDataType(input.dataType)
                    }
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = ToolkitTheme.opacity.disabled)
                )
                val inferredSem = inferredSemanticTypes[Pair(node.id, input.id)] ?: input.semanticTypes
                if (inferredSem.isNotEmpty()) {
                    val first = inferredSem.first().canonicalId
                    val semText = if (inferredSem.size > 1) {
                        "$first (+${inferredSem.size - 1} more)"
                    } else {
                        first
                    }
                    TooltipArea(
                        tooltip = {
                            Column(modifier = Modifier.padding(ToolkitTheme.spacing.extraSmall)) {
                                inferredSem.forEach { sem ->
                                    Text(
                                        text = "• ${sem.canonicalId}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    ) {
                        Text(
                            text = semText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (portErrors.isNotEmpty()) {
                    Text(
                        text = portErrors.first().message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Default Value Editor
        val isConnected = connectedInputPortIds.contains(input.id)
        val valueModifier = if (isConnected) Modifier.alpha(0.5f) else Modifier

        val metadata = (node as? Node.CapabilityNode)?.capability?.parameters?.get(input.id)
            ?: ParameterMetadata(
                type = input.dataType,
                semanticTypes = inferredSemanticTypes[Pair(node.id, input.id)] ?: input.semanticTypes,
                description = input.description ?: "",
                required = input.isRequired,
                defaultValue = input.defaultValue?.let { NodeSerializationUtils.anyToJsonElement(it) }
            )

        Box(
            modifier = Modifier
                .width(ToolkitTheme.dimensions.widthLarge)
                .then(valueModifier)
                .onFocusChanged { if (!it.isFocused) onFocusLost() }
        ) {
            val nodePluginId = (node as? Node.CapabilityNode)?.pluginInfo?.id ?: ""
            DynamicParameterInput(
                name = "",
                metadata = metadata,
                value = getPortValueString(currentPortValue, input.dataType),
                onValueChange = { newValue ->
                    onUpdateValue(
                        node.id,
                        input.id,
                        SettingsUtils.stringToJson(newValue, input.dataType)
                    )
                },
                enabled = !isConnected && !isReadOnly,
                isAutoGenerated = metadata.autogeneratedPattern != null,
                providedSettings = providedSettings,
                providedLocks = effectiveLocks,
                pluginId = nodePluginId,
                compact = true
            )
        }

        val correspondingOutput = node.outputs.find { it.id == input.id }
        val isOutputLocation =
            (node as? Node.CapabilityNode)?.capability?.parameters?.get(input.id)?.role == ParameterRole.OUTPUT_LOCATION
        if (isOutputLocation && correspondingOutput != null) {
            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
            PortCircle(
                color = if (highlightedPortId == correspondingOutput.id) (highlightedPortColor ?: headerColor) else headerColor,
                isHighlighted = highlightedPortId == correspondingOutput.id,
                onDragStart = {
                    if (!isReadOnly) onStartConnection(node.id, correspondingOutput.id, true)
                },
                onDrag = { if (!isReadOnly) onDragConnection(it) },
                onDragEnd = { if (!isReadOnly) onDropConnection(it) },
                modifier = Modifier
                    .testTag("port_${node.id}_${correspondingOutput.id}_output")
                    .onGloballyPositioned { coords ->
                        onPortPositioned(node.id, correspondingOutput.id, true, coords)
                    }
            )
        }
    }
}

@Composable
fun OutputPortRow(
    output: OutputPort,
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
    onPortPositioned: (Long, String, Boolean, LayoutCoordinates) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ToolkitTheme.dimensions.pluginIcon)
            .padding(vertical = ToolkitTheme.spacing.extraSmall)
            .testTag("port_row_${node.id}_${output.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
            if (!output.description.isNullOrBlank()) {
                TooltipArea(
                    delayMillis = 3000,
                    tooltip = {
                        Text(
                            text = output.description!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                ) {
                    Text(
                        text = output.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Text(
                    text = output.name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            val inferredType = inferredTypes[Pair(node.id, output.id)] ?: output.dataType
            val typeLabel =
                if (output.dataType is DataType.Primitive && output.dataType.primitiveType == PrimitiveType.ANY &&
                    !(inferredType is DataType.Primitive && inferredType.primitiveType == PrimitiveType.ANY)
                ) {
                    "${formatDataType(inferredType)} (Implied)"
                } else {
                    formatDataType(output.dataType)
                }
            Text(
                text = typeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = ToolkitTheme.opacity.disabled)
            )
            val inferredSem = inferredSemanticTypes[Pair(node.id, output.id)] ?: output.semanticTypes
            if (inferredSem.isNotEmpty()) {
                val first = inferredSem.first().canonicalId
                val semText = if (inferredSem.size > 1) {
                    "$first (+${inferredSem.size - 1} more)"
                } else {
                    first
                }
                TooltipArea(
                    tooltip = {
                        Column(modifier = Modifier.padding(ToolkitTheme.spacing.extraSmall)) {
                            inferredSem.forEach { sem ->
                                Text(
                                    text = "• ${sem.canonicalId}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                ) {
                    Text(
                        text = semText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val portErrors = validationErrors.filter {
                (it.sourceNodeId == node.id && it.sourcePortId == output.id) ||
                        (it.targetNodeId == node.id && it.targetPortId == output.id)
            }
            if (portErrors.isNotEmpty()) {
                Text(
                    text = portErrors.first().message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        PortCircle(
            color = if (highlightedPortId == output.id) (highlightedPortColor ?: headerColor) else headerColor,
            isHighlighted = highlightedPortId == output.id,
            onDragStart = { if (!isReadOnly) onStartConnection(node.id, output.id, true) },
            onDrag = { if (!isReadOnly) onDragConnection(it) },
            onDragEnd = { if (!isReadOnly) onDropConnection(it) },
            modifier = Modifier
                .testTag("port_${node.id}_${output.id}_output")
                .onGloballyPositioned { coords ->
                    onPortPositioned(node.id, output.id, true, coords)
                }
        )
    }
}
