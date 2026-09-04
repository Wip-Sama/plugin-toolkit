package org.wip.plugintoolkit.features.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.settings.model.GeneralSettings
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import org.wip.plugintoolkit.shared.components.settings.ExpressiveMenu
import org.wip.plugintoolkit.shared.components.tooltip
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.dialog_apply
import plugintoolkit.composeapp.generated.resources.dialog_cancel
import plugintoolkit.composeapp.generated.resources.setting_max_memory_approx_gb
import plugintoolkit.composeapp.generated.resources.setting_max_memory_approx_mb
import plugintoolkit.composeapp.generated.resources.setting_max_memory_custom_action
import plugintoolkit.composeapp.generated.resources.setting_max_memory_custom_label
import plugintoolkit.composeapp.generated.resources.setting_max_memory_custom_tooltip
import plugintoolkit.composeapp.generated.resources.setting_max_memory_default
import plugintoolkit.composeapp.generated.resources.setting_max_memory_dialog_desc
import plugintoolkit.composeapp.generated.resources.setting_max_memory_dialog_title
import plugintoolkit.composeapp.generated.resources.setting_max_memory_error_min
import plugintoolkit.composeapp.generated.resources.setting_max_memory_unit_gb
import plugintoolkit.composeapp.generated.resources.setting_max_memory_unit_mb
import kotlin.math.roundToInt

val MEMORY_TIERS = listOf(
    1024,   // 1 GB
    1536,   // 1.5 GB
    2048,   // 2 GB (Default)
    3072,   // 3 GB
    4096,   // 4 GB
    6144,   // 6 GB
    8192,   // 8 GB
    12288,  // 12 GB
    16384,  // 16 GB
    24576,  // 24 GB
    32768,  // 32 GB
    49152,  // 48 GB
    65536   // 64 GB
)

const val CUSTOM_OPTION_ID = -1

object MaxMemoryLogic {
    fun parseAndValidateMemory(input: String, isGb: Boolean): Int? {
        val sanitized = input.trim().replace(',', '.')
        if (sanitized.isBlank()) return null
        return if (isGb) {
            val gb = sanitized.toDoubleOrNull() ?: return null
            if (gb < 1.0) return null
            (gb * 1024.0).roundToInt().coerceAtLeast(GeneralSettings.MIN_MAX_MEMORY_MB)
        } else {
            val mb = sanitized.toIntOrNull() ?: return null
            if (mb < GeneralSettings.MIN_MAX_MEMORY_MB) return null
            mb
        }
    }

    fun formatMemoryOption(mb: Int): String {
        return when {
            mb == GeneralSettings.DEFAULT_MAX_MEMORY_MB -> "2 GB"
            mb % 1024 == 0 -> "${mb / 1024} GB"
            mb % 512 == 0 -> "${mb / 1024.0} GB"
            else -> "$mb MB"
        }
    }
}

@Composable
fun memoryOptionLabel(optionId: Int): String {
    return when {
        optionId == CUSTOM_OPTION_ID -> stringResource(Res.string.setting_max_memory_custom_action)
        optionId == GeneralSettings.DEFAULT_MAX_MEMORY_MB -> "2 GB (${stringResource(Res.string.setting_max_memory_default)})"
        optionId in MEMORY_TIERS && optionId % 1024 == 0 -> "${optionId / 1024} GB"
        optionId in MEMORY_TIERS && optionId % 512 == 0 -> "${optionId / 1024.0} GB"
        optionId in MEMORY_TIERS -> "$optionId MB"
        else -> stringResource(Res.string.setting_max_memory_custom_label, optionId)
    }
}

@Composable
fun CustomMemoryDialog(
    initialMb: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val initialIsGb = initialMb % 1024 == 0 && initialMb >= GeneralSettings.MIN_MAX_MEMORY_MB
    var isGb by remember { mutableStateOf(initialIsGb) }
    var textValue by remember(initialMb) {
        val initialText = if (initialIsGb) {
            val gbVal = initialMb / 1024.0
            if (gbVal % 1.0 == 0.0) gbVal.toInt().toString() else gbVal.toString()
        } else {
            initialMb.toString()
        }
        mutableStateOf(initialText)
    }

    val validatedMb = remember(textValue, isGb) {
        MaxMemoryLogic.parseAndValidateMemory(textValue, isGb)
    }
    val isError = textValue.isNotBlank() && validatedMb == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(Res.string.setting_max_memory_dialog_title),
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
            ) {
                Text(
                    text = stringResource(Res.string.setting_max_memory_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !isGb,
                        onClick = {
                            if (isGb) {
                                val currentVal = MaxMemoryLogic.parseAndValidateMemory(textValue, isGb = true) ?: initialMb
                                textValue = currentVal.toString()
                                isGb = false
                            }
                        },
                        label = { Text(stringResource(Res.string.setting_max_memory_unit_mb)) },
                        modifier = Modifier.height(ToolkitTheme.dimensions.filterChipHeight)
                    )
                    FilterChip(
                        selected = isGb,
                        onClick = {
                            if (!isGb) {
                                val currentVal = MaxMemoryLogic.parseAndValidateMemory(textValue, isGb = false) ?: initialMb
                                val gbVal = currentVal / 1024.0
                                textValue = if (gbVal % 1.0 == 0.0) gbVal.toInt().toString() else ((gbVal * 100).roundToInt() / 100.0).toString()
                                isGb = true
                            }
                        },
                        label = { Text(stringResource(Res.string.setting_max_memory_unit_gb)) },
                        modifier = Modifier.height(ToolkitTheme.dimensions.filterChipHeight)
                    )
                }

                ToolkitTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = {
                        Text(
                            if (isGb) stringResource(Res.string.setting_max_memory_unit_gb)
                            else stringResource(Res.string.setting_max_memory_unit_mb)
                        )
                    },
                    isError = isError,
                    supportingText = {
                        if (isError) {
                            Text(
                                text = stringResource(Res.string.setting_max_memory_error_min),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else if (validatedMb != null) {
                            val conversion = if (isGb) {
                                stringResource(Res.string.setting_max_memory_approx_mb, validatedMb)
                            } else {
                                val gb = validatedMb / 1024.0
                                val formattedGb = ((gb * 100).roundToInt() / 100.0).toString()
                                stringResource(Res.string.setting_max_memory_approx_gb, formattedGb)
                            }
                            Text(
                                text = conversion,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    validatedMb?.let {
                        onConfirm(it)
                    }
                },
                enabled = validatedMb != null
            ) {
                Text(stringResource(Res.string.dialog_apply))
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
fun MaxMemoryControl(
    currentMaxMemoryMb: Int,
    onMaxMemoryChanged: (Int) -> Unit,
    enabled: Boolean = true
) {
    var showCustomDialog by remember { mutableStateOf(false) }

    val options = remember(currentMaxMemoryMb) {
        if (currentMaxMemoryMb in MEMORY_TIERS) {
            MEMORY_TIERS + listOf(CUSTOM_OPTION_ID)
        } else {
            listOf(currentMaxMemoryMb) + MEMORY_TIERS + listOf(CUSTOM_OPTION_ID)
        }
    }

    val selectedOption = remember(currentMaxMemoryMb) {
        currentMaxMemoryMb
    }

    if (showCustomDialog) {
        CustomMemoryDialog(
            initialMb = currentMaxMemoryMb,
            onConfirm = { newMb ->
                onMaxMemoryChanged(newMb)
                showCustomDialog = false
            },
            onDismiss = { showCustomDialog = false }
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.small)
    ) {
        ExpressiveMenu(
            options = options,
            selectedOption = selectedOption,
            onOptionSelected = { optionId ->
                if (optionId == CUSTOM_OPTION_ID) {
                    showCustomDialog = true
                } else {
                    onMaxMemoryChanged(optionId)
                }
            },
            labelProvider = { opt -> memoryOptionLabel(opt) },
            enabled = enabled,
            gapAfter = { it == MEMORY_TIERS.last() }
        )

        IconButton(
            onClick = { showCustomDialog = true },
            enabled = enabled,
            modifier = Modifier.tooltip(Res.string.setting_max_memory_custom_tooltip)
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = stringResource(Res.string.setting_max_memory_custom_tooltip),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
