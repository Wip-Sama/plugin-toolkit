package com.wip.kpm_cpm_wotoolkit.features.colorpicker.model

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

sealed class ColorPickerType {
    class Classic(val showAlphaBar: Boolean = true) : ColorPickerType()

    class Circle(
        val showBrightnessBar: Boolean = true, val showAlphaBar: Boolean = true, val lightCenter: Boolean = true
    ) : ColorPickerType()

    class Ring(
        val ringWidth: Dp = 10.dp,
        val previewRadius: Dp = 80.dp,
        val showLightnessBar: Boolean = true,
        val showDarknessBar: Boolean = true,
        val showAlphaBar: Boolean = true,
        val showColorPreview: Boolean = true
    ) : ColorPickerType()

    class SimpleRing(
        val colorWidth: Dp = 20.dp,
        val tracksCount: Int = 5,
        val sectorsCount: Int = 24,
    ) : ColorPickerType()
}
