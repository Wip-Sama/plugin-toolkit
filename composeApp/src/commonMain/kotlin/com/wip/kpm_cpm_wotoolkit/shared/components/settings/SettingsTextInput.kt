package com.wip.kpm_cpm_wotoolkit.shared.components.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun SettingsTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    lengthRange: IntRange = 1..1000,
    regex: Regex = Regex(".*"),
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var textValue by remember(value) { mutableStateOf(value) }

    OutlinedTextField(
        value = textValue,
        onValueChange = {
            textValue = it
            if (it.length in lengthRange && it.matches(regex)) {
                onValueChange(it)
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
private fun SettingsTextInputPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SettingsTextInput(value = "Sample text", onValueChange = {})
        }
    }
}

