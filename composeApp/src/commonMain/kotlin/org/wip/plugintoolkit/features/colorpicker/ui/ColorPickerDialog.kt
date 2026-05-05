package org.wip.plugintoolkit.features.colorpicker.ui

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.wip.plugintoolkit.features.colorpicker.model.ColorPickerType
import org.wip.plugintoolkit.features.colorpicker.utils.toCMYK
import org.wip.plugintoolkit.features.colorpicker.utils.toHSL
import org.wip.plugintoolkit.features.colorpicker.utils.toHex
import org.wip.plugintoolkit.features.colorpicker.utils.toRGB
import org.wip.plugintoolkit.features.colorpicker.utils.transparentBackground
import org.wip.plugintoolkit.shared.components.SelectedButtonGroup

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
    var type by remember { mutableStateOf(initialType) }

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
                    "HEX" -> color.toHex(hexPrefix = true, includeAlpha = includeAlpha)
                    "RGB" -> color.toRGB(rgbPrefix = true, includeAlpha = includeAlpha)
                    "HSL" -> color.toHSL(hslPrefix = true, includeAlpha = includeAlpha)
                    "CMYK" -> color.toCMYK(cmykPrefix = true, includeAlpha = includeAlpha)
                    else -> color.toHex(hexPrefix = true, includeAlpha = includeAlpha)
                }
            }

            Surface(
                modifier = Modifier.widthIn(max = 300.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Box(modifier = Modifier.padding(32.dp)) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SelectedButtonGroup(
                            buttons = listOf("HEX", "RGB", "HSL", "CMYK"),
                            startingIndex = 0,
                            onButtonSelected = { selectedFormat = it }
                        )
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
                            }
                        )
                        ColorPicker(type = type, onPickedColor = { color = it })
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp, 30.dp)
                                    .clip(RoundedCornerShape(50))
                                    .transparentBackground(verticalBoxesAmount = 4)
                                    .background(color)
                            )
                            Text(
                                text = colorCode,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                onPickedColor(color)
                                showDialog = false
                            },
                            shape = CircleShape
                        ) {
                            Text(text = "Select")
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun ColorPickerDialogPreview() {
    MaterialTheme {
        ColorPickerDialog(
            show = true,
            onDismissRequest = {},
            onPickedColor = {}
        )
    }
}

