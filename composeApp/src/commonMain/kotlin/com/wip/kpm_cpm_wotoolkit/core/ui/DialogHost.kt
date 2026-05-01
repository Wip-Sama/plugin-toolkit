package com.wip.kpm_cpm_wotoolkit.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wip.kpm_cpm_wotoolkit.features.plugin.ui.ChangelogView
import kpm_cpm_wotoolkit.composeapp.generated.resources.Res
import kpm_cpm_wotoolkit.composeapp.generated.resources.dialog_cancel
import kpm_cpm_wotoolkit.composeapp.generated.resources.dialog_confirm
import kpm_cpm_wotoolkit.composeapp.generated.resources.dialog_proceed_anyway
import org.jetbrains.compose.resources.stringResource

@Composable
fun DialogHost(dialogService: DialogService) {
    val state by dialogService.dialogState.collectAsState()

    state?.let { data ->
        when (data) {
            is DialogData.Confirmation -> {
                AlertDialog(
                    onDismissRequest = { dialogService.dismiss() },
                    title = { Text(data.title) },
                    text = { Text(data.message) },
                    confirmButton = {
                        Button(onClick = {
                            data.onConfirm()
                            dialogService.dismiss()
                        }) { Text(stringResource(Res.string.dialog_confirm)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialogService.dismiss() }) { Text(stringResource(Res.string.dialog_cancel)) }
                    }
                )
            }

            is DialogData.Warning -> {
                AlertDialog(
                    onDismissRequest = { dialogService.dismiss() },
                    title = { Text(data.title) },
                    text = { Text(data.message) },
                    confirmButton = {
                        Button(
                            onClick = {
                                data.onConfirm()
                                dialogService.dismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text(stringResource(Res.string.dialog_proceed_anyway)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialogService.dismiss() }) { Text(stringResource(Res.string.dialog_cancel)) }
                    }
                )
            }

            is DialogData.LocationPicker -> {
                Dialog(onDismissRequest = { dialogService.dismiss() }) {
                    Card(
                        modifier = Modifier.fillMaxWidth(0.8f).padding(16.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(data.title, style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(16.dp))
                            data.folders.forEach { folder ->
                                OutlinedButton(
                                    onClick = {
                                        data.onSelected(folder)
                                        dialogService.dismiss()
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    Text(folder)
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            TextButton(
                                onClick = { dialogService.dismiss() },
                                modifier = Modifier.align(androidx.compose.ui.Alignment.End)
                            ) {
                                Text(stringResource(Res.string.dialog_cancel))
                            }
                        }
                    }
                }
            }

            is DialogData.Changelog -> {
                Dialog(onDismissRequest = { dialogService.dismiss() }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .fillMaxHeight(0.85f)
                            .padding(vertical = 16.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        ChangelogView(
                            pluginName = data.pluginName,
                            versions = data.versions,
                            onClose = { dialogService.dismiss() }
                        )
                    }
                }
            }
        }
    }
}
