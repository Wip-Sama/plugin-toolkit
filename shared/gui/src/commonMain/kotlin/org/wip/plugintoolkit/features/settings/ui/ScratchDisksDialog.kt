package org.wip.plugintoolkit.features.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.GlobalContext
import org.wip.plugintoolkit.core.model.localized
import org.wip.plugintoolkit.core.model.resolveNonComposable
import org.wip.plugintoolkit.core.notification.NotificationService
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformPathUtils
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.job.logic.ScratchDiskManager
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.ScratchDiskConfig
import org.wip.plugintoolkit.shared.components.ToolkitButtonGroup
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import org.wip.plugintoolkit.shared.components.verticalFadingEdges
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_add_scratch_disk
import plugintoolkit.composeapp.generated.resources.action_cancel
import plugintoolkit.composeapp.generated.resources.action_delete
import plugintoolkit.composeapp.generated.resources.action_save
import plugintoolkit.composeapp.generated.resources.action_sort_priority
import plugintoolkit.composeapp.generated.resources.configure_scratch_disks_desc
import plugintoolkit.composeapp.generated.resources.configure_scratch_disks_title
import plugintoolkit.composeapp.generated.resources.scratch_disk_empty
import plugintoolkit.composeapp.generated.resources.scratch_disk_fallback_info
import plugintoolkit.composeapp.generated.resources.scratch_disk_path_placeholder
import plugintoolkit.composeapp.generated.resources.scratch_disk_priority_badge
import plugintoolkit.composeapp.generated.resources.scratch_disk_priority_down
import plugintoolkit.composeapp.generated.resources.scratch_disk_priority_placeholder
import plugintoolkit.composeapp.generated.resources.scratch_disk_priority_up
import plugintoolkit.composeapp.generated.resources.scratch_disk_saved_toast
import plugintoolkit.composeapp.generated.resources.scratch_disk_status_ready
import plugintoolkit.composeapp.generated.resources.scratch_disk_status_unavailable
import plugintoolkit.composeapp.generated.resources.setting_scratch_disks

private const val DIALOG_WIDTH_FRACTION = 0.85f
private const val DIALOG_HEIGHT_FRACTION = 0.85f
private const val DEFAULT_START_PRIORITY = 1
private const val PRIORITY_STEP = 1

/**
 * Settings control for launching the Scratch Disk configuration dialog.
 */
@Composable
fun ConfigureScratchDisksControl(
    settings: AppSettings,
    onUpdate: (AppSettings) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val count = settings.general.scratchDisks.size
    val buttonLabel = "${stringResource(Res.string.setting_scratch_disks)} ($count)"

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = Modifier.padding(vertical = ToolkitTheme.spacing.extraSmall)
    ) {
        Icon(
            Icons.Default.Storage,
            contentDescription = null,
            modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
        )
        Spacer(Modifier.width(ToolkitTheme.spacing.small))
        Text(buttonLabel)
    }

    if (showDialog) {
        ScratchDisksDialog(
            settings = settings,
            onDismiss = { showDialog = false },
            onSave = { updatedDisks ->
                showDialog = false
                val updatedGeneral = settings.general.copy(scratchDisks = updatedDisks)
                onUpdate(settings.copy(general = updatedGeneral))
                val notificationService = GlobalContext.getOrNull()?.get<NotificationService>()
                notificationService?.toast(Res.string.scratch_disk_saved_toast.localized.resolveNonComposable())
            }
        )
    }
}

/**
 * Dialog for adding, prioritizing, toggling, and deleting scratch disk storage locations.
 */
@Composable
fun ScratchDisksDialog(
    settings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (List<ScratchDiskConfig>) -> Unit
) {
    var diskList by remember(settings.general.scratchDisks) {
        mutableStateOf(settings.general.scratchDisks.toMutableList())
    }
    var manualPathInput by remember { mutableStateOf("") }
    var manualPriorityInput by remember(diskList.size) {
        val nextPriority = if (diskList.isEmpty()) DEFAULT_START_PRIORITY else (diskList.maxOfOrNull { it.priority } ?: 0) + PRIORITY_STEP
        mutableStateOf(nextPriority.toString())
    }
    val scope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val fallbackAppData = remember { PlatformPathUtils.getAppDataDir() }

    val isInputPathValid = remember(manualPathInput) {
        manualPathInput.trim().isNotBlank()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(DIALOG_WIDTH_FRACTION)
                .fillMaxHeight(DIALOG_HEIGHT_FRACTION)
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
                            text = stringResource(Res.string.configure_scratch_disks_title),
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = stringResource(Res.string.configure_scratch_disks_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    ToolkitButtonGroup {
                        item { shape, modifierSpec ->
                            Button(
                                onClick = {
                                    diskList = diskList.sortedWith(
                                        compareBy<ScratchDiskConfig> { it.priority }.thenBy { it.path }
                                    ).toMutableList()
                                },
                                shape = shape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                ),
                                modifier = modifierSpec
                            ) {
                                Icon(
                                    Icons.Default.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                                )
                                Spacer(Modifier.width(ToolkitTheme.spacing.small))
                                Text(stringResource(Res.string.action_sort_priority))
                            }
                        }
                        item { shape, modifierSpec ->
                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        val picked = PlatformUtils.pickFolder() ?: return@launch
                                        val trimmed = picked.trim()
                                        if (trimmed.isNotBlank() && diskList.none { it.path == trimmed }) {
                                            val nextPriority = if (diskList.isEmpty()) DEFAULT_START_PRIORITY
                                            else (diskList.maxOfOrNull { it.priority } ?: 0) + PRIORITY_STEP
                                            diskList = (diskList + ScratchDiskConfig(
                                                id = trimmed,
                                                path = trimmed,
                                                priority = nextPriority,
                                                enabled = true
                                            )).toMutableList()
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
                                Text(stringResource(Res.string.action_add_scratch_disk))
                            }
                        }
                    }
                }

                // Add manual path + priority input row
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
                            placeholder = { Text(stringResource(Res.string.scratch_disk_path_placeholder)) },
                            singleLine = true,
                            shape = shape,
                            modifier = modifierSpec
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }

                    item { shape, modifierSpec ->
                        ToolkitTextField(
                            value = manualPriorityInput,
                            onValueChange = { manualPriorityInput = it.filter { ch -> ch.isDigit() } },
                            placeholder = { Text(stringResource(Res.string.scratch_disk_priority_placeholder)) },
                            singleLine = true,
                            shape = shape,
                            modifier = modifierSpec
                                .width(ToolkitTheme.dimensions.colorPickerChannelWidth * 1.5f)
                                .fillMaxHeight()
                        )
                    }

                    item { shape, modifierSpec ->
                        val addTooltipText = stringResource(Res.string.action_add_scratch_disk)
                        FilledIconButton(
                            onClick = {
                                val trimmed = manualPathInput.trim()
                                val parsedPriority = manualPriorityInput.toIntOrNull() ?: (diskList.size + PRIORITY_STEP)
                                if (trimmed.isNotBlank() && diskList.none { it.path == trimmed }) {
                                    diskList = (diskList + ScratchDiskConfig(
                                        id = trimmed,
                                        path = trimmed,
                                        priority = parsedPriority,
                                        enabled = true
                                    )).toMutableList()
                                    manualPathInput = ""
                                    manualPriorityInput = (parsedPriority + PRIORITY_STEP).toString()
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

                // Scratch Disk List
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (diskList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(Res.string.scratch_disk_empty),
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
                            itemsIndexed(diskList) { index, disk ->
                                val isUsable = remember(disk.path) {
                                    ScratchDiskManager().isDiskUsable(disk.path)
                                }

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
                                        // Enabled switch
                                        Switch(
                                            checked = disk.enabled,
                                            onCheckedChange = { isChecked ->
                                                val updated = diskList.toMutableList()
                                                updated[index] = disk.copy(enabled = isChecked)
                                                diskList = updated
                                            },
                                            modifier = Modifier.padding(end = ToolkitTheme.spacing.small)
                                        )

                                        // Priority badge & Up/Down reordering controls
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(end = ToolkitTheme.spacing.small)
                                        ) {
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = MaterialTheme.shapes.small,
                                                modifier = Modifier.padding(end = ToolkitTheme.spacing.extraSmall)
                                            ) {
                                                Text(
                                                    text = stringResource(Res.string.scratch_disk_priority_badge, disk.priority),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(
                                                        horizontal = ToolkitTheme.spacing.small,
                                                        vertical = ToolkitTheme.spacing.extraSmall
                                                    )
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    if (index > 0) {
                                                        val updated = diskList.toMutableList()
                                                        val prev = updated[index - 1]
                                                        val curr = updated[index]
                                                        updated[index - 1] = curr.copy(priority = prev.priority)
                                                        updated[index] = prev.copy(priority = curr.priority + PRIORITY_STEP)
                                                        diskList = updated
                                                    }
                                                },
                                                enabled = index > 0,
                                                modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                                            ) {
                                                Icon(
                                                    Icons.Default.KeyboardArrowUp,
                                                    contentDescription = stringResource(Res.string.scratch_disk_priority_up),
                                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                                                )
                                            }

                                            IconButton(
                                                onClick = {
                                                    if (index < diskList.size - 1) {
                                                        val updated = diskList.toMutableList()
                                                        val next = updated[index + 1]
                                                        val curr = updated[index]
                                                        updated[index + 1] = curr.copy(priority = next.priority)
                                                        updated[index] = next.copy(priority = curr.priority - PRIORITY_STEP)
                                                        diskList = updated
                                                    }
                                                },
                                                enabled = index < diskList.size - 1,
                                                modifier = Modifier.size(ToolkitTheme.dimensions.iconMedium)
                                            ) {
                                                Icon(
                                                    Icons.Default.KeyboardArrowDown,
                                                    contentDescription = stringResource(Res.string.scratch_disk_priority_down),
                                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconMediumSmall)
                                                )
                                            }
                                        }

                                        // Path and Status
                                        Column(
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = disk.path,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = ToolkitTheme.codeFontFamily
                                            )
                                            Text(
                                                text = if (isUsable) stringResource(Res.string.scratch_disk_status_ready)
                                                else stringResource(Res.string.scratch_disk_status_unavailable),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isUsable) ToolkitTheme.colors.success else MaterialTheme.colorScheme.error
                                            )
                                        }

                                        // Delete Button
                                        IconButton(
                                            onClick = {
                                                val updated = diskList.toMutableList()
                                                updated.removeAt(index)
                                                diskList = updated
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

                Spacer(Modifier.height(ToolkitTheme.spacing.small))

                // Default Fallback Info Box
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = ToolkitTheme.opacity.cardBackground
                        )
                    ),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(Res.string.scratch_disk_fallback_info, fallbackAppData),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = ToolkitTheme.codeFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(ToolkitTheme.spacing.small)
                    )
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
                        onClick = { onSave(diskList) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text(stringResource(Res.string.action_save))
                    }
                }
            }
        }
    }
}
