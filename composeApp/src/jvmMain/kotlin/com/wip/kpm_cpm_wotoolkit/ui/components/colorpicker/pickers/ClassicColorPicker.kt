package com.wip.kpm_cpm_wotoolkit.ui.components.colorpicker.pickers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.singleWindowApplication
import com.wip.kpm_cpm_wotoolkit.data.colorpicker.Colors.gradientColors
import com.wip.kpm_cpm_wotoolkit.extensions.colorpicker.blue
import com.wip.kpm_cpm_wotoolkit.extensions.colorpicker.darken
import com.wip.kpm_cpm_wotoolkit.extensions.colorpicker.drawColorSelector
import com.wip.kpm_cpm_wotoolkit.extensions.colorpicker.fromHueProgress
import com.wip.kpm_cpm_wotoolkit.extensions.colorpicker.green
import com.wip.kpm_cpm_wotoolkit.extensions.colorpicker.lighten
import com.wip.kpm_cpm_wotoolkit.extensions.colorpicker.red
import com.wip.kpm_cpm_wotoolkit.ui.components.colorpicker.ColorPickerPreview
import com.wip.kpm_cpm_wotoolkit.ui.components.colorpicker.ColorSlideBar
import kotlin.math.roundToInt

/**
 * Classic square color picker with a hue slider (and optional alpha bar).
 *
 * @param showAlphaBar Whether to show the alpha/transparency bar.
 * @param onPickedColor Callback invoked whenever the selected color changes.
 */
@Composable
internal fun ClassicColorPicker(
    modifier: Modifier = Modifier,
    showAlphaBar: Boolean,
    onPickedColor: (Color) -> Unit
) {
    var pickerLocation by remember { mutableStateOf(Offset.Zero) }
    var colorPickerSize by remember { mutableStateOf(IntSize.Zero) }
    var alpha by remember { mutableStateOf(1f) }
    var rangeColor by remember { mutableStateOf(Color.White) }
    var hueSlider by remember { mutableStateOf(0f) }

    var color by remember { mutableStateOf(Color.White) }

    LaunchedEffect(rangeColor, pickerLocation, colorPickerSize, alpha) {
        if (colorPickerSize.width > 0 && colorPickerSize.height > 0) {
            val xProgress = if (colorPickerSize.width > 0) {
                (1 - (pickerLocation.x / colorPickerSize.width)).coerceIn(0f, 1f)
            } else 0f
            val yProgress = if (colorPickerSize.height > 0) {
                (pickerLocation.y / colorPickerSize.height).coerceIn(0f, 1f)
            } else 0f
            color = Color(
                rangeColor.red().lighten(xProgress).darken(yProgress),
                rangeColor.green().lighten(xProgress).darken(yProgress),
                rangeColor.blue().lighten(xProgress).darken(yProgress),
                alpha = (255 * alpha).roundToInt()
            )
        }
    }
    LaunchedEffect(color) {
        onPickedColor(color)
    }

    Column(modifier = Modifier.width(IntrinsicSize.Max)) {
        Box(
            modifier = modifier
                .onSizeChanged { colorPickerSize = it }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (colorPickerSize.width > 0 && colorPickerSize.height > 0) {
                                pickerLocation = Offset(
                                    offset.x.coerceIn(0f, colorPickerSize.width.toFloat()),
                                    offset.y.coerceIn(0f, colorPickerSize.height.toFloat())
                                )
                            }
                        }
                    ) { change, _ ->
                        change.consume()
                        if (colorPickerSize.width > 0 && colorPickerSize.height > 0) {
                            pickerLocation = Offset(
                                change.position.x.coerceIn(0f, colorPickerSize.width.toFloat()),
                                change.position.y.coerceIn(0f, colorPickerSize.height.toFloat())
                            )
                        }
                    }
                }
                .size(200.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .matchParentSize()
                    .clip(MaterialTheme.shapes.medium)
            ) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, rangeColor)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            }
            Canvas(modifier = Modifier.matchParentSize()) {
                drawColorSelector(color.copy(alpha = 1f), pickerLocation)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        ColorSlideBar(
            value = hueSlider,
            onValueChange = {
                rangeColor = Color.fromHueProgress(it)
                hueSlider = it
            },
            colors = gradientColors
        )

        if (showAlphaBar) {
            Spacer(modifier = Modifier.height(16.dp))
            ColorSlideBar(
                value = alpha,
                onValueChange = { alpha = it },
                colors = listOf(Color.Transparent, rangeColor)
            )
        }
    }
}


@Composable
@Preview
fun ClassicColorPickerPreview() {
    MaterialTheme {
        ClassicColorPicker(showAlphaBar = true, onPickedColor = {})
    }
}

fun main() {
    singleWindowApplication {
        Surface(color = MaterialTheme.colorScheme.background) {
            ColorPickerPreview()
        }
    }
}