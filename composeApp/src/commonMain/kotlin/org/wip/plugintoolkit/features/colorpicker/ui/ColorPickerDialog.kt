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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.window.Dialog
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.colorpicker.model.ColorPickerType
import org.wip.plugintoolkit.features.colorpicker.utils.parseHexColor
import org.wip.plugintoolkit.features.colorpicker.utils.toHex
import org.wip.plugintoolkit.features.colorpicker.utils.transparentBackground
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.action_cancel
import plugintoolkit.composeapp.generated.resources.color_picker_apply
import plugintoolkit.composeapp.generated.resources.color_picker_hex
import plugintoolkit.composeapp.generated.resources.color_picker_hex_hint
import plugintoolkit.composeapp.generated.resources.color_picker_title

/** A focused, editable color picker dialog with explicit cancel/apply actions. */
@Composable
fun ColorPickerDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    initialColor: Color = Color.White,
    showAlpha: Boolean = false,
    onPickedColor: (Color) -> Unit
) {
    if (!show) return

    var color by remember(initialColor) { mutableStateOf(initialColor) }
    var hexInput by remember(initialColor, showAlpha) {
        mutableStateOf(initialColor.toHex(hexPrefix = true, includeAlpha = showAlpha).uppercase())
    }
    val parsedHex = remember(hexInput) { parseHexColor(hexInput) }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = Modifier.widthIn(max = ToolkitTheme.dimensions.minWidthMedium),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = ToolkitTheme.dimensions.elevationHighMedium
        ) {
            Column(
                modifier = Modifier.padding(ToolkitTheme.spacing.extraLarge),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
            ) {
                Text(
                    text = stringResource(Res.string.color_picker_title),
                    style = MaterialTheme.typography.headlineSmall
                )

                ColorPicker(
                    type = ColorPickerType.Classic(showAlphaBar = showAlpha),
                    initialColor = initialColor,
                    onPickedColor = {
                        color = it
                        hexInput = it.toHex(hexPrefix = true, includeAlpha = showAlpha).uppercase()
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.medium)
                ) {
                    Box(
                        modifier = Modifier
                            .size(ToolkitTheme.dimensions.heightMediumLarge)
                            .clip(RoundedCornerShape(ToolkitTheme.spacing.small))
                            .transparentBackground(verticalBoxesAmount = 4)
                            .background(parsedHex ?: color)
                    )
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { input ->
                            hexInput = input.take(9)
                            parseHexColor(hexInput)?.let { color = it }
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(Res.string.color_picker_hex)) },
                        supportingText = if (parsedHex == null) {
                            { Text(stringResource(Res.string.color_picker_hex_hint)) }
                        } else null,
                        isError = parsedHex == null,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(stringResource(Res.string.action_cancel))
                    }
                    Button(
                        onClick = { parsedHex?.let(onPickedColor) },
                        enabled = parsedHex != null
                    ) {
                        Text(stringResource(Res.string.color_picker_apply))
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
        ColorPickerDialog(show = true, onDismissRequest = {}, onPickedColor = {})
    }
}
