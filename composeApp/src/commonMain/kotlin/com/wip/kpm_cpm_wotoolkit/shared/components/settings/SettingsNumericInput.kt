package com.wip.kpm_cpm_wotoolkit.shared.components.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun SettingsNumericInput(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange = 1..1000,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var textValue by remember(value) { mutableStateOf(value.toString()) }

    OutlinedTextField(
        value = textValue,
        onValueChange = {
            textValue = it
            it.toIntOrNull()?.let { num ->
                if (num in valueRange) onValueChange(num)
            }
        },
        enabled = enabled,
        modifier = modifier.width(100.dp).height(48.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    )
}

@Preview
@Composable
private fun SettingsNumericInputPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SettingsNumericInput(value = 10, onValueChange = {})
        }
    }
}

