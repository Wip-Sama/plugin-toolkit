package com.wip.cmp_desktop_test.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoardWidget(
    id: Long,
    currentPosition: Offset,
    scale: Float,
    onMove: (Long, Offset) -> Unit,
    onDelete: (Long) -> Unit
) {
    val widgetSize = 50.dp

    Box(
        modifier = Modifier
            .size(widgetSize)
            .background(MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small)
            .pointerInput(id) {
                detectDragGesturesAfterLongPress(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        // dragAmount is in local unscaled coordinates if graphicsLayer is applied 
                        // appropriately on the parent. However, if the user explicitly scaled 
                        // previously with division, let's keep the logic. 
                        // It is safer to provide the scale and compute new position.
                        val newPos = currentPosition + (dragAmount / scale)
                        onMove(id, newPos)
                    }
                )
            }
            .pointerInput(id, "secondaryClick") {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                            event.changes.firstOrNull()?.consume()
                            onDelete(id)
                        }
                    }
                }
            }
    )
}

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun BoardWidgetPreview() {
    MaterialTheme {
        Box(modifier = Modifier.size(100.dp)) {
            BoardWidget(
                id = 1L,
                currentPosition = Offset.Zero,
                scale = 1.0f,
                onMove = { _, _ -> },
                onDelete = {}
            )
        }
    }
}
