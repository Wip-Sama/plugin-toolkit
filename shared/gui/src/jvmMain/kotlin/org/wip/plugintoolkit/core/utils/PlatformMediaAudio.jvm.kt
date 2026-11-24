package org.wip.plugintoolkit.core.utils

import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.FloatControl

/**
 * JVM desktop audio player backed by `javax.sound.sampled.Clip`.
 */
actual class PlatformMediaAudioPlayer {
    private var clip: Clip? = null
    private var totalMicros: Long = 0L
    private var muted: Boolean = false

    actual fun load(bytes: ByteArray): Boolean {
        release()
        if (bytes.isEmpty()) return false
        return runCatching {
            val rawStream: AudioInputStream = AudioSystem.getAudioInputStream(ByteArrayInputStream(bytes))
            rawStream.use { stream ->
                val activeClip = AudioSystem.getClip()
                activeClip.open(stream)
                totalMicros = activeClip.microsecondLength.coerceAtLeast(0L)
                clip = activeClip
                applyMute(activeClip, muted)
                true
            }
        }.getOrDefault(false)
    }

    actual fun play() {
        val active = clip ?: return
        if (totalMicros > 0L && active.microsecondPosition >= totalMicros) {
            active.microsecondPosition = 0L
        }
        active.start()
    }

    actual fun pause() {
        clip?.stop()
    }

    actual fun seekToFraction(fraction: Float) {
        val active = clip ?: return
        if (totalMicros <= 0L) return
        val clamped = fraction.coerceIn(0f, 1f)
        active.microsecondPosition = (totalMicros * clamped).toLong().coerceIn(0L, totalMicros)
    }

    actual fun setMuted(muted: Boolean) {
        this.muted = muted
        clip?.let { applyMute(it, muted) }
    }

    actual fun currentPositionMs(): Long {
        val active = clip ?: return 0L
        return (active.microsecondPosition / MICROS_PER_MILLI).coerceAtLeast(0L)
    }

    actual fun durationMs(): Long = (totalMicros / MICROS_PER_MILLI).coerceAtLeast(0L)

    actual fun isPlaying(): Boolean = clip?.isRunning == true

    actual fun release() {
        runCatching {
            clip?.stop()
            clip?.close()
        }
        clip = null
        totalMicros = 0L
    }

    private fun applyMute(active: Clip, mute: Boolean) {
        runCatching {
            if (active.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                val gain = active.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
                gain.value = if (mute) gain.minimum else 0f.coerceIn(gain.minimum, gain.maximum)
            }
        }
    }

    private companion object {
        const val MICROS_PER_MILLI = 1000L
    }
}
