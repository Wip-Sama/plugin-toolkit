package com.wip.kpm_cpm_wotoolkit.shared.components.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun SettingsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        modifier = Modifier.width(150.dp)
    )
}

@Preview
@Composable
private fun SettingsSliderPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            SettingsSlider(value = 0.5f, onValueChange = {})
        }
    }
}

