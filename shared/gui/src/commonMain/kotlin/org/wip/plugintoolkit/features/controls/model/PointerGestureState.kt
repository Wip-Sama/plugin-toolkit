package org.wip.plugintoolkit.features.controls.model

import androidx.compose.ui.geometry.Offset

/**
 * Lifecycle states of a continuous spatial pointer gesture on a canvas.
 */
sealed interface PointerGestureState {
    val isInteracting: Boolean get() = this is Started || this is Tracking

    /**
     * Initial resting state when no gesture is active.
     */
    data object Idle : PointerGestureState

    /**
     * Pointer has pressed down on the canvas matching a gesture trigger, but has not yet exceeded drag threshold.
     */
    data class Started(
        val startPosition: Offset,
        val button: PointerButton,
        val timeMillis: Long = 0L
    ) : PointerGestureState

    /**
     * Continuous drag in progress, actively receiving delta movements.
     */
    data class Tracking(
        val startPosition: Offset,
        val currentPosition: Offset,
        val delta: Offset,
        val totalDelta: Offset,
        val button: PointerButton,
        val timeMillis: Long = 0L
    ) : PointerGestureState

    /**
     * Successfully finished gesture when pointer was released.
     */
    data class Committed(
        val startPosition: Offset,
        val endPosition: Offset,
        val totalDelta: Offset,
        val button: PointerButton,
        val timeMillis: Long = 0L
    ) : PointerGestureState

    /**
     * Cancelled gesture (e.g. lost focus, interrupted by another pointer, or modifier released prematurely).
     */
    data class Cancelled(
        val reason: String? = null
    ) : PointerGestureState
}

/**
 * State machine driver for managing continuous pointer gestures.
 */
class PointerGestureStateMachine(
    initialState: PointerGestureState = PointerGestureState.Idle
) {
    var currentState: PointerGestureState = initialState
        private set

    fun onPointerDown(position: Offset, button: PointerButton, timeMillis: Long = 0L): PointerGestureState.Started {
        val next = PointerGestureState.Started(position, button, timeMillis)
        currentState = next
        return next
    }

    fun onPointerMove(currentPosition: Offset, timeMillis: Long = 0L): PointerGestureState? {
        val next = when (val state = currentState) {
            is PointerGestureState.Started -> {
                val delta = currentPosition - state.startPosition
                PointerGestureState.Tracking(
                    startPosition = state.startPosition,
                    currentPosition = currentPosition,
                    delta = delta,
                    totalDelta = delta,
                    button = state.button,
                    timeMillis = timeMillis
                )
            }
            is PointerGestureState.Tracking -> {
                val delta = currentPosition - state.currentPosition
                val totalDelta = currentPosition - state.startPosition
                PointerGestureState.Tracking(
                    startPosition = state.startPosition,
                    currentPosition = currentPosition,
                    delta = delta,
                    totalDelta = totalDelta,
                    button = state.button,
                    timeMillis = timeMillis
                )
            }
            else -> null
        }
        if (next != null) {
            currentState = next
        }
        return next
    }

    fun onPointerUp(endPosition: Offset? = null, timeMillis: Long = 0L): PointerGestureState.Committed? {
        val next = when (val state = currentState) {
            is PointerGestureState.Started -> {
                val end = endPosition ?: state.startPosition
                PointerGestureState.Committed(
                    startPosition = state.startPosition,
                    endPosition = end,
                    totalDelta = end - state.startPosition,
                    button = state.button,
                    timeMillis = timeMillis
                )
            }
            is PointerGestureState.Tracking -> {
                val end = endPosition ?: state.currentPosition
                PointerGestureState.Committed(
                    startPosition = state.startPosition,
                    endPosition = end,
                    totalDelta = end - state.startPosition,
                    button = state.button,
                    timeMillis = timeMillis
                )
            }
            else -> null
        }
        currentState = next ?: PointerGestureState.Idle
        return next
    }

    fun onCancel(reason: String? = null): PointerGestureState.Cancelled {
        val next = PointerGestureState.Cancelled(reason)
        currentState = next
        return next
    }

    fun reset() {
        currentState = PointerGestureState.Idle
    }
}
