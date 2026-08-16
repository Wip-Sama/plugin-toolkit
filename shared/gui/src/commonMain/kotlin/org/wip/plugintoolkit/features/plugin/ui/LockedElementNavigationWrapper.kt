package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.jetbrains.compose.resources.stringResource
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.dialog_cancel
import plugintoolkit.composeapp.generated.resources.dialog_unsaved_changes

@Composable
fun LockedCapabilityIcon(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = Icons.Default.Lock,
        contentDescription = "Locked capability icon",
        modifier = modifier
            .semantics { contentDescription = "Locked capability icon" }
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    )
}

@Composable
fun LockedElementNavigationWrapper(
    targetSettingKey: String,
    hasUnsavedChanges: Boolean,
    onNavigate: (String) -> Unit,
    content: @Composable (onClickLocked: () -> Unit) -> Unit = { onClickLocked ->
        LockedCapabilityIcon(onClick = onClickLocked)
    }
) {
    var showWarningDialog by remember { mutableStateOf(false) }

    val handleAction = {
        if (hasUnsavedChanges) {
            showWarningDialog = true
        } else {
            onNavigate(targetSettingKey)
        }
    }

    content(handleAction)

    if (showWarningDialog) {
        AlertDialog(
            onDismissRequest = { showWarningDialog = false },
            title = { Text(stringResource(Res.string.dialog_unsaved_changes)) },
            text = { Text("All the unsaved data will be lost. Are you sure you want to exit?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWarningDialog = false
                        onNavigate(targetSettingKey)
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWarningDialog = false }) {
                    Text(stringResource(Res.string.dialog_cancel))
                }
            }
        )
    }
}
