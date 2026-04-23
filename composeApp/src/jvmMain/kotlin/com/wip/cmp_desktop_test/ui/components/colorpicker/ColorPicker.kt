package com.wip.cmp_desktop_test.ui.components.colorpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.singleWindowApplication
import com.wip.cmp_desktop_test.extensions.colorpicker.toCMYK
import com.wip.cmp_desktop_test.extensions.colorpicker.toHSL
import com.wip.cmp_desktop_test.extensions.colorpicker.toHex
import com.wip.cmp_desktop_test.extensions.colorpicker.toRGB
import com.wip.cmp_desktop_test.extensions.colorpicker.transparentBackground
import com.wip.cmp_desktop_test.ui.components.SelectedButtonGroup
import com.wip.cmp_desktop_test.ui.components.colorpicker.pickers.CircleColorPicker
import com.wip.cmp_desktop_test.ui.components.colorpicker.pickers.ClassicColorPicker
import com.wip.cmp_desktop_test.ui.components.colorpicker.pickers.RingColorPicker
import com.wip.cmp_desktop_test.ui.components.colorpicker.pickers.SimpleRingColorPicker

// ---------------------------------------------------------------------------
// Picker type hierarchy
// ---------------------------------------------------------------------------

/**
 * Sealed class representing the available color picker styles.
 *
 * Usage example:
 * ```kotlin
 * ColorPicker(type = ColorPickerType.Circle()) { color -> … }
 * ```
 */
sealed class ColorPickerType {
    /**
     * Classic square picker (gradient box + hue slider).
     * @param showAlphaBar Sets the visibility of the alpha bar.
     */
    class Classic(val showAlphaBar: Boolean = true) : ColorPickerType()

    /**
     * Circular picker.
     * @param showBrightnessBar Sets the visibility of the brightness bar.
     * @param showAlphaBar      Sets the visibility of the alpha bar.
     * @param lightCenter       When true the center fades to white, when false to black.
     */
    class Circle(
        val showBrightnessBar: Boolean = true, val showAlphaBar: Boolean = true, val lightCenter: Boolean = true
    ) : ColorPickerType()

    /**
     * Ring / wheel picker.
     * @param ringWidth        Width of the color ring.
     * @param previewRadius    Radius of the center color preview circle.
     * @param showLightnessBar Sets the visibility of the lightness bar.
     * @param showDarknessBar  Sets the visibility of the darkness bar.
     * @param showAlphaBar     Sets the visibility of the alpha bar.
     * @param showColorPreview Sets the visibility of the center color preview circle.
     */
    class Ring(
        val ringWidth: Dp = 10.dp,
        val previewRadius: Dp = 80.dp,
        val showLightnessBar: Boolean = true,
        val showDarknessBar: Boolean = true,
        val showAlphaBar: Boolean = true,
        val showColorPreview: Boolean = true
    ) : ColorPickerType()

    /**
     * Concentric discrete-ring picker.
     * @param colorWidth   Arc width of each color segment.
     * @param tracksCount  Number of concentric tracks.
     * @param sectorsCount Number of sectors per track.
     */
    class SimpleRing(
        val colorWidth: Dp = 20.dp,
        val tracksCount: Int = 5,
        val sectorsCount: Int = 24,
    ) : ColorPickerType()
}

// ---------------------------------------------------------------------------
// Main composable
// ---------------------------------------------------------------------------

/**
 * Inline color picker composable.
 *
 * @param type           The picker style — defaults to [ColorPickerType.Classic].
 * @param onPickedColor  Callback invoked with the currently selected [Color].
 */
@Composable
fun ColorPicker(
    modifier: Modifier = Modifier, type: ColorPickerType = ColorPickerType.Classic(), onPickedColor: (Color) -> Unit
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

// ---------------------------------------------------------------------------
// Dialog wrapper
// ---------------------------------------------------------------------------

/**
 * Color picker wrapped in a dialog.
 *
 * @param show            Whether the dialog is visible.
 * @param onDismissRequest Called when the user tries to dismiss the dialog.
 * @param initialType            The picker style — defaults to [ColorPickerType.Classic].
 * @param onPickedColor   Callback invoked when the user confirms a color selection.
 */
@Composable
fun ColorPickerDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    initialType: ColorPickerType = ColorPickerType.Classic(),
    onPickedColor: (Color) -> Unit
) {
    var showDialog by remember(show) { mutableStateOf(show) }
    var color by remember { mutableStateOf(Color.White) }
    var selectedFormat by remember { mutableStateOf("HEX") }
    var type by remember<MutableState<ColorPickerType>> { mutableStateOf(initialType) }

    if (showDialog) {
        Dialog(
            onDismissRequest = {
                onDismissRequest()
                showDialog = false
            }) {
            val includeAlpha = when (type) {
                is ColorPickerType.Circle -> (type as ColorPickerType.Circle).showAlphaBar
                is ColorPickerType.Classic -> (type as ColorPickerType.Classic).showAlphaBar
                is ColorPickerType.Ring -> (type as ColorPickerType.Ring).showAlphaBar
                else -> false
            }

            val colorCode = remember(color, selectedFormat) {
                when (selectedFormat) {
                    "HEX" -> color.toHex(
                        hexPrefix = true, includeAlpha = includeAlpha
                    )

                    "RGB" -> color.toRGB(
                        rgbPrefix = true, includeAlpha = includeAlpha
                    )

                    "HSL" -> color.toHSL(
                        hslPrefix = true, includeAlpha = includeAlpha
                    )

                    "CMYK" -> color.toCMYK(
                        cmykPrefix = true, includeAlpha = includeAlpha
                    )

                    else -> color.toHex(hexPrefix = true, includeAlpha = includeAlpha)
                }
            }

            Surface(
                modifier = Modifier
//                    .width(IntrinsicSize.Max)
                    .widthIn(max = 300.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Box(
                    modifier = Modifier.padding(32.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SelectedButtonGroup(
                            buttons = listOf("HEX", "RGB", "HSL", "CMYK"),
                            startingIndex = 0,
                            onButtonSelected = { selectedFormat = it })
                        SelectedButtonGroup(
                            buttons = listOf("Classic", "Circle", "Ring", "Simple"),
                            startingIndex = 0,
                            onButtonSelected = {
                                type = when (it) {
                                    "Classic" -> ColorPickerType.Classic()
                                    "Circle" -> ColorPickerType.Circle()
                                    "Ring" -> ColorPickerType.Ring()
                                    "Simple" -> ColorPickerType.SimpleRing()
                                    else -> ColorPickerType.Classic()
                                }
                            })
                        ColorPicker(type = type, onPickedColor = { color = it })
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier.size(50.dp, 30.dp).clip(RoundedCornerShape(50))
                                    .transparentBackground(verticalBoxesAmount = 4).background(color)
                            )
                            Text(
                                text = colorCode,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth(), onClick = {
                                onPickedColor(color)
                                showDialog = false
                            }, shape = CircleShape
                        ) {
                            Text(text = "Select")
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Preview
fun ColorPickerPreview() {
    MaterialTheme {
//        ColorPicker(
//            type = ColorPickerType.Classic(),
//            onPickedColor = {  },
//        )
        ColorPickerDialog(
            initialType = ColorPickerType.Classic(),
            show = true,
            onDismissRequest = { },
            onPickedColor = { },
        )
    }
}

fun main() = singleWindowApplication(
    state = WindowState(width = 500.dp, height = 400.dp)
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        ColorPickerPreview()
    }
}