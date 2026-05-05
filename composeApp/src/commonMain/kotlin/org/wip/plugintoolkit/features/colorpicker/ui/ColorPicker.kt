package org.wip.plugintoolkit.features.colorpicker.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
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
 * @param onPickedColor  Callback invoked with the currently selected [Color].
 */
@Composable
fun ColorPicker(
    modifier: Modifier = Modifier,
    type: ColorPickerType = ColorPickerType.Classic(),
    onPickedColor: (Color) -> Unit
) {
    Box(modifier = modifier) {
        when (type) {
            is ColorPickerType.Classic -> ClassicColorPicker(
                showAlphaBar = type.showAlphaBar,
                onPickedColor = onPickedColor,
            )

            is ColorPickerType.Circle -> CircleColorPicker(
                showAlphaBar = type.showAlphaBar,
                showBrightnessBar = type.showBrightnessBar,
                lightCenter = type.lightCenter,
                onPickedColor = onPickedColor
            )

            is ColorPickerType.Ring -> RingColorPicker(
                ringWidth = type.ringWidth,
                previewRadius = type.previewRadius,
                showLightColorBar = type.showLightnessBar,
                showDarkColorBar = type.showDarknessBar,
                showAlphaBar = type.showAlphaBar,
                showColorPreview = type.showColorPreview,
                onPickedColor = onPickedColor
            )

            is ColorPickerType.SimpleRing -> SimpleRingColorPicker(
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

