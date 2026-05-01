package com.wip.kpm_cpm_wotoolkit.shared.components.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

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

