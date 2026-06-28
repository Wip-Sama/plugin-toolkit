package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.api.SemanticType
import org.wip.plugintoolkit.api.parseSemanticTypes
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.colorpicker.utils.toHex
import org.wip.plugintoolkit.features.colorpicker.utils.toRGB
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.PortConstraints
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_delete
import plugintoolkit.composeapp.generated.resources.action_save
import plugintoolkit.composeapp.generated.resources.dialog_cancel
import plugintoolkit.composeapp.generated.resources.node_class_name_label
import plugintoolkit.composeapp.generated.resources.node_data_type_label
import plugintoolkit.composeapp.generated.resources.node_delete_confirm
import plugintoolkit.composeapp.generated.resources.node_delete_title
import plugintoolkit.composeapp.generated.resources.node_edit_input_title
import plugintoolkit.composeapp.generated.resources.node_edit_output_title
import plugintoolkit.composeapp.generated.resources.node_port_name_label
import plugintoolkit.composeapp.generated.resources.node_semantic_type_label
import plugintoolkit.composeapp.generated.resources.node_semantic_type_placeholder

@Composable
fun NodeDialogs(
    node: Node,
    inferredSemanticTypes: Map<Pair<Long, String>, List<SemanticType>>,
    showDeleteConfirmation: Boolean,
    onDismissDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    showEditBoundaryDialog: Boolean,
    onDismissEditBoundary: () -> Unit,
    onUpdateBoundaryNode: (Long, String, DataType, List<SemanticType>, PortConstraints?, Boolean) -> Unit,
    showColorPicker: Boolean,
    activeColorInputId: String?,
    onDismissColorPicker: () -> Unit,
    onUpdateValue: (Long, String, Any?) -> Unit,
    showLoadSettingsDialog: Boolean,
    onDismissLoadSettings: () -> Unit,
    onUpdateSystemNodeSettings: (Long, String, List<SemanticType>, String?, List<String>?) -> Unit
) {
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = onDismissDelete,
            title = { Text(stringResource(Res.string.node_delete_title)) },
            text = { Text(stringResource(Res.string.node_delete_confirm, node.title)) },
            confirmButton = {
                Button(
                    onClick = onConfirmDelete,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(Res.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDelete) {
                    Text(stringResource(Res.string.dialog_cancel))
                }
            }
        )
    }

    if (showEditBoundaryDialog && (node is Node.FlowInputNode || node is Node.FlowOutputNode)) {
        val port = if (node is Node.FlowInputNode) node.outputs.firstOrNull() else node.inputs.firstOrNull()
        if (port != null) {
            var name by remember { mutableStateOf(port.name) }
            var selectedTypeOption by remember {
                mutableStateOf(
                    when (val dt =
                        if (port.dataType is DataType.Array) (port.dataType as DataType.Array).items else port.dataType) {
                        is DataType.Primitive -> {
                            if (dt.primitiveType == PrimitiveType.ANY) "Any"
                            else dt.primitiveType.name.lowercase().replaceFirstChar { it.uppercase() }
                        }

                        is DataType.Object -> "Object"
                        is DataType.Array -> "Array"
                        is DataType.Enum -> "Enum"
                        is DataType.MapType -> "Map"
                    }
                )
            }
            var customClassName by remember {
                mutableStateOf(
                    when (val dt =
                        if (port.dataType is DataType.Array) (port.dataType as DataType.Array).items else port.dataType) {
                        is DataType.Object -> dt.className
                        is DataType.Enum -> dt.className
                        else -> ""
                    }
                )
            }
            var semanticType by remember { mutableStateOf(port.semanticTypes.joinToString { it.canonicalId }) }

            var isList by remember {
                mutableStateOf(if (node is Node.FlowInputNode) node.isList else false)
            }
            var minValStr by remember {
                mutableStateOf(if (node is Node.FlowInputNode) node.constraints?.min?.toString() ?: "" else "")
            }
            var maxValStr by remember {
                mutableStateOf(if (node is Node.FlowInputNode) node.constraints?.max?.toString() ?: "" else "")
            }
            var regexStr by remember {
                mutableStateOf(if (node is Node.FlowInputNode) node.constraints?.regex ?: "" else "")
            }

            AlertDialog(
                onDismissRequest = onDismissEditBoundary,
                title = {
                    Text(
                        text = if (node is Node.FlowInputNode) stringResource(Res.string.node_edit_input_title) else stringResource(
                            Res.string.node_edit_output_title
                        ),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ToolkitTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(Res.string.node_port_name_label)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        val typeOptions = listOf("Any", "String", "Int", "Double", "Boolean", "Object")
                        var dropdownExpanded by remember { mutableStateOf(false) }

                        Box(modifier = Modifier.fillMaxWidth()) {
                            ToolkitTextField(
                                value = selectedTypeOption,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(Res.string.node_data_type_label)) },
                                trailingIcon = {
                                    IconButton(onClick = { dropdownExpanded = true }) {
                                        Icon(Icons.Default.UnfoldMore, contentDescription = "Select Type")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                typeOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            selectedTypeOption = option
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        if (selectedTypeOption == "Object") {
                            ToolkitTextField(
                                value = customClassName,
                                onValueChange = { customClassName = it },
                                label = { Text(stringResource(Res.string.node_class_name_label)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        ToolkitTextField(
                            value = semanticType,
                            onValueChange = { semanticType = it },
                            label = { Text(stringResource(Res.string.node_semantic_type_label)) },
                            placeholder = { Text(stringResource(Res.string.node_semantic_type_placeholder)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (node is Node.FlowInputNode) {
                            HorizontalDivider(Modifier, DividerDefaults.Thickness, DividerDefaults.color)
                            Text("Constraints & Settings", style = MaterialTheme.typography.titleSmall)

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Accept multiple items (List)")
                                Spacer(modifier = Modifier.weight(1f))
                                Switch(checked = isList, onCheckedChange = { isList = it })
                            }

                            if (selectedTypeOption == "String") {
                                ToolkitTextField(
                                    value = regexStr,
                                    onValueChange = { regexStr = it },
                                    label = { Text("Regex Pattern") },
                                    placeholder = { Text("e.g. ^[a-zA-Z]+$") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            if (selectedTypeOption == "Int" || selectedTypeOption == "Double") {
                                Row(horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)) {
                                    ToolkitTextField(
                                        value = minValStr,
                                        onValueChange = { minValStr = it },
                                        label = { Text("Min Value") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    ToolkitTextField(
                                        value = maxValStr,
                                        onValueChange = { maxValStr = it },
                                        label = { Text("Max Value") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val computedDataType = when (selectedTypeOption) {
                                "Any" -> DataType.Primitive(PrimitiveType.ANY)
                                "String" -> DataType.Primitive(PrimitiveType.STRING)
                                "Int" -> DataType.Primitive(PrimitiveType.INT)
                                "Double" -> DataType.Primitive(PrimitiveType.DOUBLE)
                                "Boolean" -> DataType.Primitive(PrimitiveType.BOOLEAN)
                                "Object" -> DataType.Object(customClassName.ifBlank { "java.lang.Object" })
                                else -> DataType.Primitive(PrimitiveType.ANY)
                            }

                            val constraints = if (node is Node.FlowInputNode) {
                                org.wip.plugintoolkit.features.flows.model.PortConstraints(
                                    regex = regexStr.takeIf { it.isNotBlank() },
                                    min = minValStr.toDoubleOrNull(),
                                    max = maxValStr.toDoubleOrNull()
                                )
                            } else null

                            onUpdateBoundaryNode(
                                node.id,
                                name.ifBlank { port.name },
                                computedDataType,
                                parseSemanticTypes(semanticType),
                                constraints,
                                isList
                            )
                            onDismissEditBoundary()
                        }
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissEditBoundary) {
                        Text(stringResource(Res.string.dialog_cancel))
                    }
                }
            )
        }
    }

    if (showColorPicker && activeColorInputId != null) {
        val input = node.inputs.firstOrNull { it.id == activeColorInputId }
        val inferredSem = input?.let { inferredSemanticTypes[Pair(node.id, it.id)] ?: it.semanticTypes } ?: emptyList()
        val hasAlpha = inferredSem.any { it.variant?.contains("rgba", ignoreCase = true) == true }
        org.wip.plugintoolkit.features.colorpicker.ui.ColorPickerDialog(
            show = showColorPicker,
            onDismissRequest = onDismissColorPicker,
            onPickedColor = { color ->
                activeColorInputId.let { inputId ->
                    val formatted = if (inferredSem.any {
                            it.name.contains("rgb", ignoreCase = true) || it.variant?.contains(
                                "rgb",
                                ignoreCase = true
                            ) == true
                        }) {
                        color.toRGB(rgbPrefix = true, includeAlpha = hasAlpha)
                    } else {
                        color.toHex(hexPrefix = true, includeAlpha = hasAlpha)
                    }
                    val isArray = input?.dataType is DataType.Array
                    val existingValue = input?.let { getPortValueString(it.value ?: it.defaultValue, it.dataType) } ?: ""
                    val newValue = appendPickedValue(existingValue, formatted, isArray)
                    onUpdateValue(node.id, inputId, newValue)
                }
                onDismissColorPicker()
            }
        )
    }

    if (showLoadSettingsDialog && node is Node.SystemNode && node.systemAction.lowercase() == "load") {
        val port = node.outputs.firstOrNull { it.id == "data" }
        val inPort = node.inputs.firstOrNull { it.id == "file_path" }
        if (port != null && inPort != null) {
            var semanticTypesStr by remember { mutableStateOf(port.semanticTypes.joinToString { it.canonicalId }) }
            var extensionsStr by remember { mutableStateOf(inPort.constraints?.extensions?.joinToString(", ") ?: "") }

            AlertDialog(
                onDismissRequest = onDismissLoadSettings,
                title = {
                    Text(
                        text = "Configure Load Node",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Set the expected semantic types for the loaded file (e.g. image/png, file/txt).",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        ToolkitTextField(
                            value = semanticTypesStr,
                            onValueChange = { semanticTypesStr = it },
                            label = { Text("Supported Semantic Types") },
                            placeholder = { Text("e.g. image/png, file/txt") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Add extensions constraint (!txt to allow semantics but forcefully reject txt)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        ToolkitTextField(
                            value = extensionsStr,
                            onValueChange = { extensionsStr = it },
                            label = { Text("Supported Extensions") },
                            placeholder = { Text("e.g. txt, json, !csv") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val parsed = parseSemanticTypes(semanticTypesStr)
                            val extensionsList = extensionsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            onUpdateSystemNodeSettings(
                                node.id,
                                "data",
                                parsed,
                                "file_path",
                                extensionsList.takeIf { it.isNotEmpty() })
                            onDismissLoadSettings()
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismissLoadSettings) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
