package org.wip.plugintoolkit.shared.components.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.theme.ToolkitTheme

@OptIn(ExperimentalMaterial3Api::class)
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
        modifier = Modifier.width(ToolkitTheme.dimensions.widthLarge),
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                drawStopIndicator = null
            )
        }
    )
}

@Preview
@Composable
private fun SettingsSliderPreview() {
    MaterialTheme {
        Box(modifier = Modifier.padding(ToolkitTheme.spacing.medium)) {
            SettingsSlider(value = 0.5f, onValueChange = {})
        }
    }
}

