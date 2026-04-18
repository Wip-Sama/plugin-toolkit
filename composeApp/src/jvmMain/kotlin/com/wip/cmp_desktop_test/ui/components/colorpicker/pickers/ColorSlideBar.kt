package com.wip.cmp_desktop_test.ui.components.colorpicker.pickers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.wip.cmp_desktop_test.data.colorpicker.Colors
import com.wip.cmp_desktop_test.extensions.colorpicker.drawTransparentBackground

private const val THUMB_RADIUS = 20f

/**
 * A horizontal color slide bar that reports progress [0f..1f] via [onProgress].
 * Used internally by all color picker variants for hue, brightness, and alpha bars.
 */
@Composable
internal fun ColorSlideBar(colors: List<Color>, onProgress: (Float) -> Unit) {
    var progress by remember { mutableStateOf(1f) }
    var slideBarSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(progress) {
        onProgress(progress)
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .onSizeChanged { slideBarSize = it }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (slideBarSize.width > 0) {
                            progress = (offset.x / slideBarSize.width).coerceIn(0f, 1f)
                        }
                    }
                ) { change, _ ->
                    change.consume()
                    if (slideBarSize.width > 0) {
                        progress = (change.position.x / slideBarSize.width).coerceIn(0f, 1f)
                    }
                }
            }
            .clipToBounds()
            .clip(RoundedCornerShape(100))
            .border(0.2.dp, Color.LightGray, RoundedCornerShape(100))
    ) {
        drawTransparentBackground(3)
        drawRect(
            Brush.horizontalGradient(
                colors,
                startX = size.height / 2,
                endX = size.width - size.height / 2
            )
        )
        drawCircle(
            Color.White,
            radius = THUMB_RADIUS,
            center = Offset(
                THUMB_RADIUS + (size.height / 2 - THUMB_RADIUS) +
                        ((size.width - (THUMB_RADIUS + (size.height / 2 - THUMB_RADIUS)) * 2) * progress),
                size.height / 2
            )
        )
    }
}


@Composable
@Preview
fun ColorSliderBarPreview() {
    MaterialTheme {
        Slider(
            value = 0.5f,
            onValueChange = {},
            modifier = Modifier.padding(16.dp)
        )
    }
}