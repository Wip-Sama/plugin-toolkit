package org.wip.plugintoolkit.shared.components.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.shared.components.ToolkitTextField
import org.wip.plugintoolkit.core.theme.ToolkitTheme

@Composable
fun SettingsNumericInput(
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: IntRange = 1..1000,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var textValue by remember(value) { mutableStateOf(value.toString()) }

    ToolkitTextField(
        value = textValue,
        onValueChange = {
            textValue = it
            it.toIntOrNull()?.let { num ->
                if (num in valueRange) onValueChange(num)
            }
        },
        enabled = enabled,
        modifier = modifier.width(ToolkitTheme.dimensions.genericInputWidthLarge).height(ToolkitTheme.dimensions.pluginIcon),
        textStyle = MaterialTheme.typography.bodyMedium,
        singleLine = true
    )
}

@Preview
@Composable
private fun SettingsNumericInputPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(ToolkitTheme.spacing.medium)) {
            SettingsNumericInput(value = 10, onValueChange = {})
        }
    }
}
