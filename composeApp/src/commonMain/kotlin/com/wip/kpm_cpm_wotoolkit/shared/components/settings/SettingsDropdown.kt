package com.wip.kpm_cpm_wotoolkit.shared.components.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun <T> SettingsDropdown(
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    labelProvider: @Composable (T) -> String,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            onClick = { if (enabled) expanded = true },
            color = if (enabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            shape = MaterialTheme.shapes.medium,
            enabled = enabled
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = labelProvider(selectedOption),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surface)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelProvider(option)) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Preview
@Composable
private fun SettingsDropdownPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SettingsDropdown(
                options = listOf("Option 1", "Option 2", "Option 3"),
                selectedOption = "Option 1",
                onOptionSelected = {},
                labelProvider = { it }
            )
        }
    }
}

