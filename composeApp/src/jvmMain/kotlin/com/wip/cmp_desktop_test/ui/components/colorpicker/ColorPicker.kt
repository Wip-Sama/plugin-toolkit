package com.wip.cmp_desktop_test.ui.components.colorpicker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.wip.cmp_desktop_test.extensions.colorpicker.toHex
import com.wip.cmp_desktop_test.extensions.colorpicker.transparentBackground
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
        val showBrightnessBar: Boolean = true,
        val showAlphaBar: Boolean = true,
        val lightCenter: Boolean = true
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

// ---------------------------------------------------------------------------
// Dialog wrapper
// ---------------------------------------------------------------------------

/**
 * Color picker wrapped in a dialog.
 *
 * @param show            Whether the dialog is visible.
 * @param onDismissRequest Called when the user tries to dismiss the dialog.
 * @param type            The picker style — defaults to [ColorPickerType.Classic].
 * @param onPickedColor   Callback invoked when the user confirms a color selection.
 */
@Composable
fun ColorPickerDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    type: ColorPickerType = ColorPickerType.Classic(),
    onPickedColor: (Color) -> Unit
) {
    var showDialog by remember(show) { mutableStateOf(show) }
    var color by remember { mutableStateOf(Color.White) }

    if (showDialog) {
        Dialog(onDismissRequest = {
            onDismissRequest()
            showDialog = false
        }) {
            Box(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color.White)
            ) {
                Box(modifier = Modifier.padding(32.dp)) {
                    Column {
                        ColorPicker(type = type, onPickedColor = { color = it })
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp, 30.dp)
                                    .clip(RoundedCornerShape(50))
                                    .border(0.3.dp, Color.LightGray, RoundedCornerShape(50))
                                    .transparentBackground(verticalBoxesAmount = 4)
                                    .background(color)
                            )
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = Color.Gray)) { append("#") }
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append(color.toHex())
                                    }
                                },
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                onPickedColor(color)
                                showDialog = false
                            },
                            shape = RoundedCornerShape(50)
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
            type = ColorPickerType.Classic(),
            show = true,
            onDismissRequest = {  },
            onPickedColor = {  },
        )
    }
}
