package com.wip.kpm_cpm_wotoolkit.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

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
                        }) { Text("Confirm") }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialogService.dismiss() }) { Text("Cancel") }
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
                        ) { Text("Proceed Anyway") }
                    },
                    dismissButton = {
                        TextButton(onClick = { dialogService.dismiss() }) { Text("Cancel") }
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
                                Text("Cancel")
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
                        com.wip.kpm_cpm_wotoolkit.features.plugin.ui.ChangelogView(
                            moduleName = data.moduleName,
                            versions = data.versions,
                            onClose = { dialogService.dismiss() }
                        )
                    }
                }
            }
        }
    }
}
