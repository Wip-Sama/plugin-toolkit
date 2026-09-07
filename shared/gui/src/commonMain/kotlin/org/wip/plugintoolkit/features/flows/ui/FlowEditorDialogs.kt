package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.format
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.viewmodel.PendingConnection
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_save
import plugintoolkit.composeapp.generated.resources.dialog_cancel
import plugintoolkit.composeapp.generated.resources.flow_editor_convert_connect
import plugintoolkit.composeapp.generated.resources.flow_editor_enter_name
import plugintoolkit.composeapp.generated.resources.flow_editor_incompatible_message
import plugintoolkit.composeapp.generated.resources.flow_editor_incompatible_title
import plugintoolkit.composeapp.generated.resources.flow_editor_save_as_title
import plugintoolkit.composeapp.generated.resources.flow_name_label

@Composable
internal fun IncompatibleConnectionDialog(
    pendingConnection: PendingConnection,
    onConvertAndConnect: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.flow_editor_incompatible_title)) },
        text = {
            Text(
                stringResource(
                    Res.string.flow_editor_incompatible_message,
                    pendingConnection.sourceType.format(),
                    pendingConnection.targetType.format()
                )
            )
        },
        confirmButton = {
            Button(onClick = onConvertAndConnect) {
                Text(stringResource(Res.string.flow_editor_convert_connect))
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
internal fun FlowEditorSaveAsDialog(
    initialName: String = "",
    existingFlowNames: List<String>,
    duplicateErrorMessage: String,
    onShowToast: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var saveAsName by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.flow_editor_save_as_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.flow_editor_enter_name),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = ToolkitTheme.spacing.medium)
                )
                ToolkitTextField(
                    value = saveAsName,
                    onValueChange = { saveAsName = it },
                    label = { Text(stringResource(Res.string.flow_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedName = saveAsName.trim()
                    if (trimmedName.isNotBlank()) {
                        val exists = existingFlowNames.any { it.equals(trimmedName, ignoreCase = true) }
                        if (exists) {
                            onShowToast(duplicateErrorMessage.format(trimmedName))
                        } else {
                            onConfirm(trimmedName)
                        }
                    }
                },
                enabled = saveAsName.isNotBlank()
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
