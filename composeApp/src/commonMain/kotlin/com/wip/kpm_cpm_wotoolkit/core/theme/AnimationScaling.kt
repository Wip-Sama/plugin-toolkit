package com.wip.kpm_cpm_wotoolkit.core.theme

import androidx.compose.runtime.*

/**
 * A [MonotonicFrameClock] that scales the time passed to its frames.
 * Used for global animation speed control.
 */
class ScaledFrameClock(
    private val baseClock: MonotonicFrameClock,
    private val enabledProvider: () -> Boolean
) : MonotonicFrameClock {

    private var lastActualTimeNanos: Long = -1L
    private var scaledTimeNanos: Long = 0L

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
        return baseClock.withFrameNanos { actualTimeNanos ->
            if (lastActualTimeNanos == -1L) {
                lastActualTimeNanos = actualTimeNanos
                scaledTimeNanos = actualTimeNanos
            } else {
                val delta = actualTimeNanos - lastActualTimeNanos
                // Use a very high scale if disabled to make it effectively instant (1,000,000x)
                val effectiveScale = if (enabledProvider()) 1f else 1_000_000f 
                scaledTimeNanos += (delta * effectiveScale).toLong()
                lastActualTimeNanos = actualTimeNanos
            }
            onFrame(scaledTimeNanos)
        }
    }
}
