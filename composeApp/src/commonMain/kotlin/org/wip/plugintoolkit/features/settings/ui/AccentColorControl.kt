package org.wip.plugintoolkit.features.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.features.colorpicker.model.ColorPickerType
import org.wip.plugintoolkit.features.colorpicker.ui.ColorPickerDialog
import org.wip.plugintoolkit.features.settings.model.AppSettings

@Composable
fun AccentColorControl(settings: AppSettings, onUpdate: (AppSettings) -> Unit) {
    var showColorPicker by remember { mutableStateOf(false) }

    ColorPickerDialog(
        show = showColorPicker,
        initialType = ColorPickerType.Classic(),
        onDismissRequest = { showColorPicker = false },
        onPickedColor = { color ->
            onUpdate(
                settings.copy(
                    appearance = settings.appearance.copy(
                        accentColor = color.toArgb().toLong()
                    )
                )
            )
            showColorPicker = false
        }
    )

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(settings.appearance.accentColor))
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(enabled = !settings.appearance.followSystemAccent) {
                showColorPicker = true
            }
    )
}
