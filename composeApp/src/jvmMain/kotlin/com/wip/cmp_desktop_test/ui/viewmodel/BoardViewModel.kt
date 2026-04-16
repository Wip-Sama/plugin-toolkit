package com.wip.cmp_desktop_test.ui.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import com.wip.cmp_desktop_test.data.Widget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.roundToInt

data class BoardState(
    val widgets: List<Widget> = emptyList(),
    val offset: Offset = Offset.Zero,
    val scale: Float = 1f,
    val nextId: Long = 0L
)

sealed interface BoardEvent {
    data class AddWidget(val tapPosition: Offset) : BoardEvent
    data class MoveWidget(val id: Long, val newPosition: Offset) : BoardEvent
    data class DeleteWidget(val id: Long) : BoardEvent
    data class Pan(val delta: Offset) : BoardEvent
    data class Zoom(val delta: Float, val focusPosition: Offset) : BoardEvent
    data class SetZoom(val scale: Float) : BoardEvent
    data object ResetBoard : BoardEvent
}

class BoardViewModel : ViewModel() {
    private val _state = MutableStateFlow(BoardState())
    val state: StateFlow<BoardState> = _state.asStateFlow()

    private val gridSize = 50f
    private val zoomLevels = listOf(0.1f, 0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f, 5.0f)

    fun onEvent(event: BoardEvent) {
        when (event) {
            is BoardEvent.AddWidget -> handleAddWidget(event.tapPosition)
            is BoardEvent.MoveWidget -> handleMoveWidget(event.id, event.newPosition)
            is BoardEvent.DeleteWidget -> handleDeleteWidget(event.id)
            is BoardEvent.Pan -> handlePan(event.delta)
            is BoardEvent.Zoom -> handleZoom(event.delta, event.focusPosition)
            is BoardEvent.SetZoom -> handleSetZoom(event.scale)
            is BoardEvent.ResetBoard -> handleResetBoard()
        }
    }

    private fun handleAddWidget(tapPosition: Offset) {
        _state.update { currentState ->
            val boardPos = (tapPosition - currentState.offset) / currentState.scale
            val snappedPos = boardPos.snapToGrid()
            currentState.copy(
                widgets = currentState.widgets + Widget(currentState.nextId, snappedPos),
                nextId = currentState.nextId + 1
            )
        }
    }

    private fun handleMoveWidget(id: Long, newPosition: Offset) {
        val snappedPos = newPosition.snapToGrid()
        _state.update { currentState ->
            currentState.copy(
                widgets = currentState.widgets.map { if (it.id == id) it.copy(position = snappedPos) else it }
            )
        }
    }

    private fun handleDeleteWidget(id: Long) {
        _state.update { currentState ->
            currentState.copy(
                widgets = currentState.widgets.filter { it.id != id }
            )
        }
    }

    private fun handlePan(delta: Offset) {
        _state.update { currentState ->
            currentState.copy(offset = currentState.offset + delta)
        }
    }

    private fun handleZoom(delta: Float, focusPosition: Offset) {
        _state.update { currentState ->
            val currentZoom = currentState.scale
            val currentIndex = zoomLevels.indexOfFirst { it >= currentZoom }
            
            val newScale = if (delta > 0) {
                // Zoom out
                val newIndex = (currentIndex - 1).coerceAtLeast(0)
                zoomLevels[newIndex]
            } else {
                // Zoom in
                val newIndex = (if (currentIndex == -1) zoomLevels.size - 1 else currentIndex + 1).coerceAtMost(zoomLevels.size - 1)
                zoomLevels[newIndex]
            }
            
            if (newScale == currentZoom) return@update currentState

            val boardFocus = (focusPosition - currentState.offset) / currentZoom
            val newOffset = focusPosition - boardFocus * newScale
            
            currentState.copy(scale = newScale, offset = newOffset)
        }
    }

    private fun handleSetZoom(scale: Float) {
        _state.update { currentState ->
            val newScale = zoomLevels.minByOrNull { kotlin.math.abs(it - scale) } ?: scale
            currentState.copy(scale = newScale)
        }
    }

    private fun handleResetBoard() {
        _state.update { BoardState() }
    }

    private fun Offset.snapToGrid(): Offset {
        return Offset(
            (x / gridSize).roundToInt() * gridSize,
            (y / gridSize).roundToInt() * gridSize
        )
    }
}
