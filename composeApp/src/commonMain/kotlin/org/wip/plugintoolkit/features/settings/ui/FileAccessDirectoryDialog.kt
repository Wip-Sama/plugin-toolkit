package org.wip.plugintoolkit.features.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.job.logic.SystemPathSecurity
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.FileAccessMode
import org.wip.plugintoolkit.shared.components.GlassCard
import plugintoolkit.composeapp.generated.resources.*

/**
 * Control rendered in settings for launching the Blacklist/Whitelist directory configuration dialog.
 */
@Composable
fun ConfigureDirectoriesControl(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    val mode = settings.extensions.fileAccessMode
    if (mode == FileAccessMode.Unrestricted) return

    val isBlacklist = mode == FileAccessMode.Blacklist
    val count = if (isBlacklist) {
        if (settings.extensions.blacklistedDirectories.isNotEmpty()) settings.extensions.blacklistedDirectories.size
        else SystemPathSecurity.BUILTIN_BLACKLIST.size
    } else {
        settings.extensions.allowedDirectories.size
    }

    val buttonLabel = if (isBlacklist) {
        stringResource(Res.string.configure_blacklist_title) + " ($count)"
    } else {
        stringResource(Res.string.configure_whitelist_title) + " ($count)"
    }

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = Modifier.padding(vertical = ToolkitTheme.spacing.extraSmall)
    ) {
        Icon(
            Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
        )
        Spacer(Modifier.width(ToolkitTheme.spacing.small))
        Text(buttonLabel)
    }

    if (showDialog) {
        FileAccessDirectoryDialog(
            mode = mode,
            settings = settings,
            onDismiss = { showDialog = false },
            onSave = { updatedDirectories ->
                showDialog = false
                val updatedExtensions = if (isBlacklist) {
                    settings.extensions.copy(blacklistedDirectories = updatedDirectories)
                } else {
                    settings.extensions.copy(allowedDirectories = updatedDirectories)
                }
                onUpdate(settings.copy(extensions = updatedExtensions))
            }
        )
    }
}

/**
 * Dialog popup for adding, viewing, deleting, and resetting blacklisted/whitelisted directories.
 */
@Composable
fun FileAccessDirectoryDialog(
    mode: FileAccessMode,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    val isBlacklist = mode == FileAccessMode.Blacklist
    val initialList = remember(mode, settings.extensions) {
        if (isBlacklist) {
            if (settings.extensions.blacklistedDirectories.isNotEmpty()) {
                settings.extensions.blacklistedDirectories
            } else {
                SystemPathSecurity.BUILTIN_BLACKLIST
            }
        } else {
            settings.extensions.allowedDirectories
        }
    }

    var directoryList by remember { mutableStateOf(initialList.toMutableList()) }
    var manualPathInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.85f)
                .widthIn(max = ToolkitTheme.dimensions.dialogMaxWidth)
                .heightIn(max = ToolkitTheme.dimensions.dialogMaxHeight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(ToolkitTheme.spacing.large)
            ) {
                // Header Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = ToolkitTheme.spacing.medium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (isBlacklist) stringResource(Res.string.configure_blacklist_title)
                            else stringResource(Res.string.configure_whitelist_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = if (isBlacklist) stringResource(Res.string.setting_file_access_mode_subtitle)
                            else stringResource(Res.string.setting_configure_directories_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)) {
                        // Reset to defaults button
                        OutlinedIconButton(
                            onClick = {
                                directoryList = if (isBlacklist) {
                                    SystemPathSecurity.BUILTIN_BLACKLIST.toMutableList()
                                } else {
                                    mutableStateListOf()
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(Res.string.action_reset_defaults)
                            )
                        }

                        // Add directory folder picker button
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    val picked = PlatformUtils.pickFolder() ?: return@launch
                                    if (picked.isNotBlank() && !directoryList.contains(picked)) {
                                        directoryList = (directoryList + picked).toMutableList()
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                            )
                            Spacer(Modifier.width(ToolkitTheme.spacing.small))
                            Text(stringResource(Res.string.action_add_directory))
                        }
                    }
                }

                // Add manual path input row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = ToolkitTheme.spacing.medium),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = manualPathInput,
                        onValueChange = { manualPathInput = it },
                        placeholder = { Text(stringResource(Res.string.add_path_placeholder)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(ToolkitTheme.spacing.small))
                    IconButton(
                        onClick = {
                            val trimmed = manualPathInput.trim()
                            if (trimmed.isNotEmpty() && !directoryList.contains(trimmed)) {
                                directoryList = (directoryList + trimmed).toMutableList()
                                manualPathInput = ""
                            }
                        },
                        enabled = manualPathInput.isNotBlank()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.action_add_directory))
                    }
                }

                // Directory List Scrollable Container
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (directoryList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(Res.string.configure_directories_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(ToolkitTheme.spacing.small),
                            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.extraSmall)
                        ) {
                            itemsIndexed(directoryList) { index, pathStr ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(
                                                horizontal = ToolkitTheme.spacing.medium,
                                                vertical = ToolkitTheme.spacing.small
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = pathStr,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                val updated = directoryList.toMutableList()
                                                updated.removeAt(index)
                                                directoryList = updated
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = stringResource(Res.string.action_delete),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(ToolkitTheme.spacing.large))

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                    Spacer(Modifier.width(ToolkitTheme.spacing.medium))
                    Button(onClick = { onSave(directoryList) }) {
                        Text(stringResource(Res.string.action_save))
                    }
                }
            }
        }
    }
}

private fun String?.isNullBlink(): Boolean = this == null || this.isBlank()
