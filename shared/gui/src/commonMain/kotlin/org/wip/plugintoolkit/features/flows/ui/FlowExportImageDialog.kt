package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.FlowImageExportBackground
import org.wip.plugintoolkit.features.flows.model.FlowImageExportOptions
import org.wip.plugintoolkit.features.flows.model.FlowImageExportResolution
import org.wip.plugintoolkit.features.flows.model.FlowImageExportScope
import org.wip.plugintoolkit.features.flows.utils.FlowImageExportUtils
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.dialog_cancel
import plugintoolkit.composeapp.generated.resources.flow_export_image_background
import plugintoolkit.composeapp.generated.resources.flow_export_image_bg_grid
import plugintoolkit.composeapp.generated.resources.flow_export_image_bg_solid
import plugintoolkit.composeapp.generated.resources.flow_export_image_bg_transparent
import plugintoolkit.composeapp.generated.resources.flow_export_image_btn_copy
import plugintoolkit.composeapp.generated.resources.flow_export_image_btn_save
import plugintoolkit.composeapp.generated.resources.flow_export_image_dimensions
import plugintoolkit.composeapp.generated.resources.flow_export_image_no_selection_hint
import plugintoolkit.composeapp.generated.resources.flow_export_image_resolution
import plugintoolkit.composeapp.generated.resources.flow_export_image_resolution_1x
import plugintoolkit.composeapp.generated.resources.flow_export_image_resolution_2x
import plugintoolkit.composeapp.generated.resources.flow_export_image_resolution_half
import plugintoolkit.composeapp.generated.resources.flow_export_image_scope
import plugintoolkit.composeapp.generated.resources.flow_export_image_scope_all
import plugintoolkit.composeapp.generated.resources.flow_export_image_scope_selection
import plugintoolkit.composeapp.generated.resources.flow_export_image_selected_count
import plugintoolkit.composeapp.generated.resources.flow_export_image_title
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowExportImageDialog(
    flow: Flow,
    hasSelection: Boolean,
    selectedNodeIds: Set<Long> = emptySet(),
    selectedGroupIds: Set<Long> = emptySet(),
    selectedLabelIds: Set<Long> = emptySet(),
    selectedPointIds: Set<Long> = emptySet(),
    nodeSizes: Map<Long, IntSize> = emptyMap(),
    onExportToPng: (FlowImageExportOptions) -> Unit,
    onCopyToClipboard: (FlowImageExportOptions) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedScope by remember {
        mutableStateOf(if (hasSelection) FlowImageExportScope.SelectedElements else FlowImageExportScope.WholeFlow)
    }
    var selectedResolution by remember { mutableStateOf(FlowImageExportResolution.UltraHD) }
    var selectedBackground by remember { mutableStateOf(FlowImageExportBackground.CanvasGrid) }

    val filteredElements = remember(flow, selectedScope, selectedNodeIds, selectedGroupIds, selectedLabelIds, selectedPointIds) {
        FlowImageExportUtils.filterElements(
            flow = flow,
            scope = selectedScope,
            selectedNodeIds = selectedNodeIds,
            selectedGroupIds = selectedGroupIds,
            selectedLabelIds = selectedLabelIds,
            selectedPointIds = selectedPointIds
        )
    }

    val bounds = remember(filteredElements, nodeSizes) {
        FlowImageExportUtils.computeExportBounds(filteredElements, nodeSizes)
    }

    val estimatedWidth = bounds?.let { (it.width * selectedResolution.scale).roundToInt() } ?: 0
    val estimatedHeight = bounds?.let { (it.height * selectedResolution.scale).roundToInt() } ?: 0
    val isExportDisabled = filteredElements.isEmpty

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                )
                Text(
                    text = stringResource(Res.string.flow_export_image_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Scope Selection
                Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)) {
                    Text(
                        text = stringResource(Res.string.flow_export_image_scope),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedScope == FlowImageExportScope.WholeFlow,
                            onClick = { selectedScope = FlowImageExportScope.WholeFlow },
                            label = { Text(stringResource(Res.string.flow_export_image_scope_all)) },
                            modifier = Modifier.testTag("export_scope_whole_flow")
                        )
                        FilterChip(
                            selected = selectedScope == FlowImageExportScope.SelectedElements,
                            onClick = { selectedScope = FlowImageExportScope.SelectedElements },
                            label = {
                                val count = selectedNodeIds.size + selectedGroupIds.size + selectedLabelIds.size + selectedPointIds.size
                                if (count > 0) {
                                    Text(stringResource(Res.string.flow_export_image_selected_count, count))
                                } else {
                                    Text(stringResource(Res.string.flow_export_image_scope_selection))
                                }
                            },
                            enabled = hasSelection,
                            modifier = Modifier.testTag("export_scope_selected")
                        )
                    }
                    if (!hasSelection && selectedScope == FlowImageExportScope.SelectedElements) {
                        Text(
                            text = stringResource(Res.string.flow_export_image_no_selection_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // 2. Resolution Multiplier
                Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)) {
                    Text(
                        text = stringResource(Res.string.flow_export_image_resolution),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
                    ) {
                        FilterChip(
                            selected = selectedResolution == FlowImageExportResolution.Half,
                            onClick = { selectedResolution = FlowImageExportResolution.Half },
                            label = { Text(stringResource(Res.string.flow_export_image_resolution_half)) },
                            modifier = Modifier.testTag("export_resolution_half")
                        )
                        FilterChip(
                            selected = selectedResolution == FlowImageExportResolution.Standard,
                            onClick = { selectedResolution = FlowImageExportResolution.Standard },
                            label = { Text(stringResource(Res.string.flow_export_image_resolution_1x)) },
                            modifier = Modifier.testTag("export_resolution_standard")
                        )
                        FilterChip(
                            selected = selectedResolution == FlowImageExportResolution.UltraHD,
                            onClick = { selectedResolution = FlowImageExportResolution.UltraHD },
                            label = { Text(stringResource(Res.string.flow_export_image_resolution_2x)) },
                            modifier = Modifier.testTag("export_resolution_ultrahd")
                        )
                    }
                }

                // 3. Background Style
                Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)) {
                    Text(
                        text = stringResource(Res.string.flow_export_image_background),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
                    ) {
                        FilterChip(
                            selected = selectedBackground == FlowImageExportBackground.CanvasGrid,
                            onClick = { selectedBackground = FlowImageExportBackground.CanvasGrid },
                            label = { Text(stringResource(Res.string.flow_export_image_bg_grid)) },
                            modifier = Modifier.testTag("export_bg_grid")
                        )
                        FilterChip(
                            selected = selectedBackground == FlowImageExportBackground.SolidBackground,
                            onClick = { selectedBackground = FlowImageExportBackground.SolidBackground },
                            label = { Text(stringResource(Res.string.flow_export_image_bg_solid)) },
                            modifier = Modifier.testTag("export_bg_solid")
                        )
                        FilterChip(
                            selected = selectedBackground == FlowImageExportBackground.Transparent,
                            onClick = { selectedBackground = FlowImageExportBackground.Transparent },
                            label = { Text(stringResource(Res.string.flow_export_image_bg_transparent)) },
                            modifier = Modifier.testTag("export_bg_transparent")
                        )
                    }
                }

                // 4. Dimensions & Content Summary Box
                Surface(
                    shape = ToolkitTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().testTag("export_dimensions_box")
                ) {
                    Row(
                        modifier = Modifier.padding(ToolkitTheme.spacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                        )
                        Column {
                            Text(
                                text = stringResource(Res.string.flow_export_image_dimensions, estimatedWidth, estimatedHeight),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${filteredElements.nodes.size} nodes • ${filteredElements.connections.size} connections • ${filteredElements.groups.size} groups • ${filteredElements.labels.size} labels",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = ToolkitTheme.opacity.secondaryText)
                            )
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("export_dialog_cancel")
            ) {
                Text(stringResource(Res.string.dialog_cancel))
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val options = FlowImageExportOptions(
                            scope = selectedScope,
                            resolution = selectedResolution,
                            background = selectedBackground
                        )
                        onCopyToClipboard(options)
                    },
                    enabled = !isExportDisabled,
                    modifier = Modifier.testTag("export_dialog_copy_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                    )
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                    Text(stringResource(Res.string.flow_export_image_btn_copy))
                }

                Button(
                    onClick = {
                        val options = FlowImageExportOptions(
                            scope = selectedScope,
                            resolution = selectedResolution,
                            background = selectedBackground
                        )
                        onExportToPng(options)
                    },
                    enabled = !isExportDisabled,
                    modifier = Modifier.testTag("export_dialog_save_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                    )
                    Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                    Text(stringResource(Res.string.flow_export_image_btn_save))
                }
            }
        }
    )
}
