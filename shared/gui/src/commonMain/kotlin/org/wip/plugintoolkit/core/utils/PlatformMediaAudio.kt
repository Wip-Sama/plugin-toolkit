package org.wip.plugintoolkit.core.utils

/**
 * Multiplatform handle for playing short PCM audio assets inside desktop media controls.
 */
expect class PlatformMediaAudioPlayer() {
    /** Loads encoded audio bytes and returns whether playback is supported on this host. */
    fun load(bytes: ByteArray): Boolean

    /** Starts or resumes playback from the current position. */
    fun play()

    /** Pauses playback while retaining the current playhead offset. */
    fun pause()

    /** Seeks to the given fractional progress in `0f..1f`. */
    fun seekToFraction(fraction: Float)

    /** Mutes or unmutes audio output. */
    fun setMuted(muted: Boolean)

    /** Current playback position in milliseconds. */
    fun currentPositionMs(): Long

    /** Total audio duration in milliseconds, or `0L` if unknown. */
    fun durationMs(): Long

    /** Returns `true` while audio playback is actively advancing. */
    fun isPlaying(): Boolean

    /** Releases underlying native audio lines. */
    fun release()
}
