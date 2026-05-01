package com.wip.kpm_cpm_wotoolkit.features.board.ui

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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoardComponent(
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

@Preview
@Composable
private fun BoardComponentPreview() {
    MaterialTheme {
        Box(modifier = Modifier.size(100.dp)) {
            BoardComponent(
                id = 1L,
                currentPosition = Offset.Zero,
                scale = 1.0f,
                onMove = { _, _ -> },
                onDelete = {}
            )
        }
    }
}

