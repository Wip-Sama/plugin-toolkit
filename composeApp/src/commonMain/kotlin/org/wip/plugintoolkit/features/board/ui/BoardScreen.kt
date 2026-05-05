package org.wip.plugintoolkit.features.board.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.features.board.viewmodel.BoardEvent
import org.wip.plugintoolkit.features.board.viewmodel.BoardViewModel
import org.wip.plugintoolkit.shared.components.ZoomControls
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.board_controls
import plugintoolkit.composeapp.generated.resources.board_reset
import kotlin.math.roundToInt

@Composable
fun BoardScreen(
    viewModel: BoardViewModel = viewModel { BoardViewModel() }
) {
    val state by viewModel.state.collectAsState()
    val gridSize = 50f
    var boardSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boardSize = it }
            .background(MaterialTheme.colorScheme.surfaceVariant)
            // Background gestures: Pan
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, _, _ ->
                    viewModel.onEvent(BoardEvent.Pan(pan))
                }
            }
            // Mouse scroll for zoom
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val change = event.changes.firstOrNull()
                            val scrollDeltaY = change?.scrollDelta?.y ?: 0f
                            val position = change?.position
                            if (scrollDeltaY != 0f && position != null) {
                                viewModel.onEvent(BoardEvent.Zoom(scrollDeltaY, position))
                            }
                        }
                    }
                }
            }
            // Background gestures: Create widget on Long Press
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { tapOffset ->
                    viewModel.onEvent(BoardEvent.AddWidget(tapOffset))
                })
            }
    ) {
        // 1. Grid of Dots
        Canvas(modifier = Modifier.fillMaxSize()) {
            val scaledGridSize = gridSize * state.scale
            val startX = (state.offset.x % scaledGridSize) - scaledGridSize
            val startY = (state.offset.y % scaledGridSize) - scaledGridSize

            val cols = (size.width / scaledGridSize).toInt() + 2
            val rows = (size.height / scaledGridSize).toInt() + 2

            for (i in 0..cols) {
                for (j in 0..rows) {
                    val x = startX + i * scaledGridSize
                    val y = startY + j * scaledGridSize
                    drawCircle(
                        color = Color.Gray.copy(alpha = 0.4f),
                        radius = 2f * state.scale,
                        center = Offset(x, y)
                    )
                }
            }
        }

        // 2. Widgets
        state.widgets.forEach { widget ->
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            ((widget.position.x * state.scale) + state.offset.x).roundToInt(),
                            ((widget.position.y * state.scale) + state.offset.y).roundToInt()
                        )
                    }
                    .graphicsLayer(
                        scaleX = state.scale,
                        scaleY = state.scale,
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                    )
            ) {
                BoardComponent(
                    id = widget.id,
                    currentPosition = widget.position,
                    scale = state.scale,
                    onMove = { id, offset -> viewModel.onEvent(BoardEvent.MoveWidget(id, offset)) },
                    onDelete = { id -> viewModel.onEvent(BoardEvent.DeleteWidget(id)) }
                )
            }
        }

        // 3. UI Overlay - Top Left
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), shape = MaterialTheme.shapes.medium)
                .padding(12.dp)
        ) {
            Text(stringResource(Res.string.board_controls), style = MaterialTheme.typography.titleSmall)
            Text("• Long press board: Create widget", style = MaterialTheme.typography.bodySmall)
            Text("• Long press & drag widget: Move (snaps)", style = MaterialTheme.typography.bodySmall)
            Text("• Right click widget: Delete", style = MaterialTheme.typography.bodySmall)
            Text("• Drag background: Pan", style = MaterialTheme.typography.bodySmall)
            Text("• Mouse scroll: Zoom", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.onEvent(BoardEvent.ResetBoard) },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(stringResource(Res.string.board_reset), style = MaterialTheme.typography.labelLarge)
            }
        }

        // 4. Zoom Controls UI - Bottom Right
        val centerPosition = Offset(boardSize.width / 2f, boardSize.height / 2f)
        ZoomControls(
            scale = state.scale,
            onZoomIn = { viewModel.onEvent(BoardEvent.Zoom(-1f, centerPosition)) },
            onZoomOut = { viewModel.onEvent(BoardEvent.Zoom(1f, centerPosition)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

@Preview
@Composable
private fun BoardScreenPreview() {
    MaterialTheme {
        BoardScreen()
    }
}

