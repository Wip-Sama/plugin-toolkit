package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.viewmodel.ConflictResolutionAction
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_delete
import plugintoolkit.composeapp.generated.resources.action_save
import plugintoolkit.composeapp.generated.resources.dialog_cancel
import plugintoolkit.composeapp.generated.resources.error_invalid_version_format
import plugintoolkit.composeapp.generated.resources.flow_clear_defaults
import plugintoolkit.composeapp.generated.resources.flow_create_button
import plugintoolkit.composeapp.generated.resources.flow_create_new
import plugintoolkit.composeapp.generated.resources.flow_delete_confirm
import plugintoolkit.composeapp.generated.resources.flow_delete_title
import plugintoolkit.composeapp.generated.resources.flow_delete_warning_subflow
import plugintoolkit.composeapp.generated.resources.flow_description
import plugintoolkit.composeapp.generated.resources.flow_metadata_edit_title
import plugintoolkit.composeapp.generated.resources.flow_name_label
import plugintoolkit.composeapp.generated.resources.flow_version

@Composable
internal fun CreateFlowDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newFlowName by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.flow_create_new)) },
        text = {
            ToolkitTextField(
                value = newFlowName,
                onValueChange = { newFlowName = it },
                label = { Text(stringResource(Res.string.flow_name_label)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                if (newFlowName.isNotBlank()) {
                    onConfirm(newFlowName)
                }
            }) { Text(stringResource(Res.string.flow_create_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.dialog_cancel)) }
        }
    )
}

@Composable
internal fun DeleteFlowDialog(
    flowName: String,
    parentFlows: List<String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.flow_delete_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)) {
                Text(stringResource(Res.string.flow_delete_confirm, flowName))
                if (parentFlows.isNotEmpty()) {
                    Text(
                        text = stringResource(
                            Res.string.flow_delete_warning_subflow,
                            parentFlows.joinToString(", ")
                        ),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(stringResource(Res.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.dialog_cancel))
            }
        }
    )
}

@Composable
internal fun EditFlowMetadataDialog(
    flow: Flow,
    onSave: (version: String, description: String?) -> Unit,
    onClearDefaults: () -> Unit,
    onDismiss: () -> Unit
) {
    var version by remember { mutableStateOf(flow.version) }
    var description by remember { mutableStateOf(flow.description ?: "") }
    val isVersionValid = remember(version) { version.matches(Regex("^\\d+\\.\\d+\\.\\d+$")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.flow_metadata_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)) {
                ToolkitTextField(
                    value = version,
                    onValueChange = { version = it },
                    label = { Text(stringResource(Res.string.flow_version)) },
                    modifier = Modifier.fillMaxWidth(),
                    isError = !isVersionValid,
                    supportingText = if (!isVersionValid) {
                        { Text(stringResource(Res.string.error_invalid_version_format)) }
                    } else null
                )
                ToolkitTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(Res.string.flow_description)) },
                    modifier = Modifier.fillMaxWidth()
                )

                if (flow.defaultValues.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${flow.defaultValues.size} default parameter(s) configured",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onClearDefaults) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                            Text(stringResource(Res.string.flow_clear_defaults))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(version, description.takeIf { it.isNotBlank() })
                },
                enabled = isVersionValid
            ) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.dialog_cancel))
            }
        }
    )
}

@Composable
internal fun ConflictResolutionDialog(
    importConflicts: List<String>,
    onResolve: (Map<String, ConflictResolutionAction>, Map<String, String>) -> Unit,
    onCancel: () -> Unit
) {
    val resolutions = remember(importConflicts) {
        mutableStateMapOf<String, ConflictResolutionAction>().apply {
            importConflicts.forEach { name ->
                put(name, ConflictResolutionAction.RENAME)
            }
        }
    }

    val customNames = remember(importConflicts) {
        mutableStateMapOf<String, String>().apply {
            importConflicts.forEach { name ->
                put(name, "")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "Resolve Import Conflicts", //TODO: localize
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
            ) {
                Text(
                    text = "The following imported flows already exist. Choose an action for each flow:", //TODO: localize
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                for (clashingName in importConflicts) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ToolkitTheme.opacity.divider)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(ToolkitTheme.spacing.medium),
                            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Conflict",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.circularProgressSize)
                                )
                                Spacer(modifier = Modifier.width(ToolkitTheme.spacing.small))
                                Text(
                                    text = clashingName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            SegmentedButtonGroup(
                                options = ConflictResolutionAction.entries,
                                selectedOption = resolutions[clashingName] ?: ConflictResolutionAction.RENAME,
                                onOptionSelected = { resolutions[clashingName] = it }
                            )

                            AnimatedVisibility(
                                visible = resolutions[clashingName] == ConflictResolutionAction.RENAME
                            ) {
                                Column {
                                    Spacer(modifier = Modifier.height(ToolkitTheme.spacing.extraSmall))
                                    ToolkitTextField(
                                        value = customNames[clashingName] ?: "",
                                        onValueChange = { customNames[clashingName] = it },
                                        label = { Text("New flow name (optional)") }, //TODO: localize
                                        placeholder = { Text("Auto-unique fallback") }, //TODO: localize
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        textStyle = MaterialTheme.typography.bodyMedium
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
                    onResolve(resolutions.toMap(), customNames.toMap())
                }
            ) {
                Text("Import") //TODO: localize
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel
            ) {
                Text("Cancel") //TODO: localize
            }
        }
    )
}

@Composable
internal fun SegmentedButtonGroup(
    options: List<ConflictResolutionAction>,
    selectedOption: ConflictResolutionAction,
    onOptionSelected: (ConflictResolutionAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(ToolkitTheme.dimensions.standardButtonHeight),
        shape = ToolkitTheme.shapes.extraLarge,
        border = BorderStroke(ToolkitTheme.dimensions.borderUnselected, outlineColor),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = option == selectedOption

                val containerColor = if (isSelected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    ToolkitTheme.colors.transparent
                }

                val contentColor = if (isSelected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(containerColor)
                        .clickable { onOptionSelected(option) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.extraSmall)
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                modifier = Modifier.size(ToolkitTheme.dimensions.circularProgressSize),
                                tint = contentColor
                            )
                            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.extraSmall))
                        }
                        Text(
                            text = when (option) {
                                ConflictResolutionAction.RENAME -> "Rename"
                                ConflictResolutionAction.KEEP_LOCAL -> "Keep Local"
                                ConflictResolutionAction.KEEP_IMPORTED -> "Keep Imported"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }

                // Add vertical divider if not the last item
                if (index < options.size - 1) {
                    Spacer(
                        modifier = Modifier
                            .width(ToolkitTheme.dimensions.borderUnselected)
                            .fillMaxHeight()
                            .background(outlineColor)
                    )
                }
            }
        }
    }
}
