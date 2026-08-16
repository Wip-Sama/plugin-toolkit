package org.wip.plugintoolkit.features.settings.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.job.logic.SystemPathSecurity
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.FileAccessMode
import org.wip.plugintoolkit.shared.components.ToolkitButtonGroup
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import org.wip.plugintoolkit.shared.components.verticalFadingEdges
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
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()

    val isInputPathValid = remember(manualPathInput) {
        isValidPathFormat(manualPathInput)
    }

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
                    Column(modifier = Modifier.weight(1f)) {
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

                    // Button group for Red Reset + Add Directory buttons
                    ToolkitButtonGroup {
                        item { shape, modifierSpec ->
                            Button(
                                onClick = { showResetConfirmDialog = true },
                                shape = shape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = modifierSpec
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                                )
                                Spacer(Modifier.width(ToolkitTheme.spacing.small))
                                Text(stringResource(Res.string.action_reset_defaults))
                            }
                        }
                        item { shape, modifierSpec ->
                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        val picked = PlatformUtils.pickFolder() ?: return@launch
                                        if (picked.isNotBlank() && !directoryList.contains(picked)) {
                                            directoryList = (directoryList + picked).toMutableList()
                                        }
                                    }
                                },
                                shape = shape,
                                modifier = modifierSpec
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
                }

                // Add manual path input row styled as a Button Group with matching heights & corners
                ToolkitButtonGroup(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(bottom = ToolkitTheme.spacing.medium)
                ) {
                    item { shape, modifierSpec ->
                        ToolkitTextField(
                            value = manualPathInput,
                            onValueChange = { manualPathInput = it },
                            placeholder = { Text(stringResource(Res.string.add_path_placeholder)) },
                            singleLine = true,
                            shape = shape,
                            modifier = modifierSpec
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }

                    item { shape, modifierSpec ->
                        val addTooltipText = stringResource(Res.string.action_add_directory)
                        FilledIconButton(
                            onClick = {
                                val trimmed = manualPathInput.trim()
                                if (isValidPathFormat(trimmed) && !directoryList.contains(trimmed)) {
                                    directoryList = (directoryList + trimmed).toMutableList()
                                    manualPathInput = ""
                                }
                            },
                            enabled = isInputPathValid,
                            shape = shape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                    alpha = ToolkitTheme.opacity.sidebarBackground
                                ),
                                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = ToolkitTheme.opacity.settingsItemHover
                                )
                            ),
                            modifier = modifierSpec.fillMaxHeight()
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = addTooltipText,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                            )
                        }
                    }
                }

                // Directory List directly inside popup with dynamic fading edges (only active when scrollable)
                Box(
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
                            state = lazyListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalFadingEdges(
                                    lazyListState = lazyListState,
                                    topFadeLength = ToolkitTheme.spacing.large,
                                    bottomFadeLength = ToolkitTheme.spacing.large,
                                    almostOpaque = ToolkitTheme.opacity.almostOpaque
                                ),
                            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
                        ) {
                            itemsIndexed(directoryList) { index, pathStr ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                            alpha = ToolkitTheme.opacity.sidebarBackground
                                        )
                                    ),
                                    shape = MaterialTheme.shapes.medium,
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

                Spacer(Modifier.height(ToolkitTheme.spacing.medium))

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
                    Button(
                        onClick = { onSave(directoryList) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                }
            }
        }
    }

    // Reset Confirmation Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = {
                Text(
                    stringResource(Res.string.reset_confirm_title),
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    stringResource(Res.string.reset_confirm_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetConfirmDialog = false
                        directoryList = if (isBlacklist) {
                            SystemPathSecurity.BUILTIN_BLACKLIST.toMutableList()
                        } else {
                            mutableStateListOf()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(Res.string.action_reset))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text(stringResource(Res.string.action_cancel))
                }
            }
        )
    }
}

/**
 * Validates if the string is formatted as a valid directory path.
 */
private fun isValidPathFormat(pathStr: String): Boolean {
    val trimmed = pathStr.trim()
    if (trimmed.isEmpty()) return false
    val invalidChars = listOf('<', '>', '"', '|', '?', '*')
    return invalidChars.none { trimmed.contains(it) }
}


