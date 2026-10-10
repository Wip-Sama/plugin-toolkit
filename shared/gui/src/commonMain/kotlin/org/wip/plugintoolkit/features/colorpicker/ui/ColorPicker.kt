package org.wip.plugintoolkit.features.colorpicker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import org.wip.plugintoolkit.features.colorpicker.model.ColorPickerType
import org.wip.plugintoolkit.features.colorpicker.ui.pickers.CircleColorPicker
import org.wip.plugintoolkit.features.colorpicker.ui.pickers.ClassicColorPicker
import org.wip.plugintoolkit.features.colorpicker.ui.pickers.RingColorPicker
import org.wip.plugintoolkit.features.colorpicker.ui.pickers.SimpleRingColorPicker

/**
 * Inline color picker composable.
 *
 * @param type           The picker style — defaults to [ColorPickerType.Classic].
 * @param selectedColor  The current color to be reflected by the picker.
 * @param showSliders    Whether embedded sliders should be displayed (defaults to true for standalone use).
 * @param onPickedColor  Callback invoked with the currently selected [Color].
 */
@Composable
fun ColorPicker(
    modifier: Modifier = Modifier,
    type: ColorPickerType = ColorPickerType.Classic(),
    selectedColor: Color = Color.White,
    hue: Float? = null,
    showSliders: Boolean = true,
    onPickedColor: (Color) -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (type) {
            is ColorPickerType.Classic -> ClassicColorPicker(
                modifier = Modifier.align(Alignment.Center),
                selectedColor = selectedColor,
                hue = hue,
                showAlphaBar = type.showAlphaBar && showSliders,
                showHueBar = showSliders,
                onPickedColor = onPickedColor,
            )

            is ColorPickerType.Circle -> CircleColorPicker(
                modifier = Modifier.align(Alignment.Center),
                selectedColor = selectedColor,
                showAlphaBar = type.showAlphaBar && showSliders,
                showBrightnessBar = type.showBrightnessBar && showSliders,
                lightCenter = type.lightCenter,
                onPickedColor = onPickedColor
            )

            is ColorPickerType.Ring -> RingColorPicker(
                modifier = Modifier.align(Alignment.Center),
                selectedColor = selectedColor,
                ringWidth = type.ringWidth,
                previewRadius = type.previewRadius,
                showLightColorBar = type.showLightnessBar && showSliders,
                showDarkColorBar = type.showDarknessBar && showSliders,
                showAlphaBar = type.showAlphaBar && showSliders,
                showColorPreview = type.showColorPreview,
                onPickedColor = onPickedColor
            )

            is ColorPickerType.SimpleRing -> SimpleRingColorPicker(
                modifier = Modifier.align(Alignment.Center),
                selectedColor = selectedColor,
                colorWidth = type.colorWidth,
                tracksCount = type.tracksCount,
                sectorsCount = type.sectorsCount,
                onPickedColor = onPickedColor
            )
        }
    }
}

@Preview
@Composable
private fun ColorPickerPreview() {
    ColorPicker(onPickedColor = {})
}
