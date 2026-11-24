package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformMediaAudioPlayer
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.sdui_image_compare_after
import plugintoolkit.composeapp.generated.resources.sdui_image_compare_before
import plugintoolkit.composeapp.generated.resources.sdui_image_compare_slider_desc
import plugintoolkit.composeapp.generated.resources.sdui_media_audio_unavailable
import plugintoolkit.composeapp.generated.resources.sdui_media_mute
import plugintoolkit.composeapp.generated.resources.sdui_media_pause
import plugintoolkit.composeapp.generated.resources.sdui_media_play
import plugintoolkit.composeapp.generated.resources.sdui_media_unmute
import plugintoolkit.composeapp.generated.resources.sdui_media_video_preview_only
import kotlin.math.roundToInt

private const val KEYBOARD_SPLIT_STEP = 0.05f
private const val MEDIA_POLL_INTERVAL_MS = 100L
private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L
private val COMPARE_DEFAULT_HEIGHT = 240.dp
private val COMPARE_HANDLE_WIDTH = 28.dp
private val COMPARE_HANDLE_HEIGHT = 44.dp
private val COMPARE_DIVIDER_WIDTH = 2.dp
private val FALLBACK_SIMULATED_DURATION_MS = 30_000L

/**
 * Interactive before/after split-handle comparison canvas for raster painters with pointer drag,
 * tap-to-jump, and Left/Right keyboard stepping.
 */
@Composable
fun ToolkitImageCompare(
    beforePainter: Painter?,
    afterPainter: Painter?,
    splitPosition: Float,
    onSplitPositionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    beforeLabel: String = stringResource(Res.string.sdui_image_compare_before),
    afterLabel: String = stringResource(Res.string.sdui_image_compare_after),
    enabled: Boolean = true
) {
    ToolkitImageCompare(
        beforeContent = {
            if (beforePainter != null) {
                Image(
                    painter = beforePainter,
                    contentDescription = beforeLabel,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        afterContent = {
            if (afterPainter != null) {
                Image(
                    painter = afterPainter,
                    contentDescription = afterLabel,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        splitPosition = splitPosition,
        onSplitPositionChange = onSplitPositionChange,
        modifier = modifier,
        beforeLabel = beforeLabel,
        afterLabel = afterLabel,
        enabled = enabled
    )
}

/**
 * Interactive before/after split-handle comparison container accepting arbitrary slot content
 * for before and after imagery, with pointer drag, tap-to-jump, and Left/Right keyboard stepping.
 */
@Composable
fun ToolkitImageCompare(
    beforeContent: @Composable () -> Unit,
    afterContent: @Composable () -> Unit,
    splitPosition: Float,
    onSplitPositionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    beforeLabel: String = stringResource(Res.string.sdui_image_compare_before),
    afterLabel: String = stringResource(Res.string.sdui_image_compare_after),
    enabled: Boolean = true
) {
    val sliderDesc = stringResource(Res.string.sdui_image_compare_slider_desc)
    val clampedSplit = splitPosition.coerceIn(0f, 1f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(COMPARE_DEFAULT_HEIGHT),
        shape = ToolkitTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            ToolkitTheme.dimensions.borderUnselected,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = sliderDesc }
                .onPreviewKeyEvent { event ->
                    if (!enabled || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onSplitPositionChange((clampedSplit - KEYBOARD_SPLIT_STEP).coerceIn(0f, 1f))
                            true
                        }
                        Key.DirectionRight -> {
                            onSplitPositionChange((clampedSplit + KEYBOARD_SPLIT_STEP).coerceIn(0f, 1f))
                            true
                        }
                        else -> false
                    }
                }
                .focusable(enabled)
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
            val splitShape = remember(clampedSplit) {
                GenericShape { size, _ ->
                    val clipWidth = size.width * clampedSplit
                    moveTo(0f, 0f)
                    lineTo(clipWidth, 0f)
                    lineTo(clipWidth, size.height)
                    lineTo(0f, size.height)
                    close()
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                afterContent()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(splitShape)
            ) {
                beforeContent()
            }

            // Pointer overlay for tapping or dragging across the comparison canvas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                    .pointerInput(enabled, widthPx) {
                        if (!enabled) return@pointerInput
                        detectTapGestures { offset ->
                            onSplitPositionChange((offset.x / widthPx).coerceIn(0f, 1f))
                        }
                    }
                    .pointerInput(enabled, widthPx) {
                        if (!enabled) return@pointerInput
                        detectHorizontalDragGestures { change, _ ->
                            change.consume()
                            onSplitPositionChange((change.position.x / widthPx).coerceIn(0f, 1f))
                        }
                    }
            )

            // Vertical divider line & tactile drag handle
            val dividerOffsetPx = (widthPx * clampedSplit).roundToInt()
            Box(
                modifier = Modifier
                    .offset { IntOffset(dividerOffsetPx - with(density) { (COMPARE_DIVIDER_WIDTH / 2).roundToPx() }, 0) }
                    .width(COMPARE_DIVIDER_WIDTH)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary)
            )
            Surface(
                shape = ToolkitTheme.shapes.pill,
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = ToolkitTheme.dimensions.elevationMedium,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(dividerOffsetPx - with(density) { (COMPARE_HANDLE_WIDTH / 2).roundToPx() }, 0) }
                    .size(width = COMPARE_HANDLE_WIDTH, height = COMPARE_HANDLE_HEIGHT)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.DragHandle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                    )
                }
            }

            // Before / After corner labels
            ToolkitStatusBadge(
                text = beforeLabel,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(ToolkitTheme.spacing.sm)
            )
            ToolkitStatusBadge(
                text = afterLabel,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(ToolkitTheme.spacing.sm)
            )
        }
    }
}

/**
 * Reusable desktop media playback card supporting PCM WAV/AIFF/AU audio playback via
 * [PlatformMediaAudioPlayer] and interactive scrub/duration controls for embedded preview streams.
 */
@Composable
fun ToolkitMediaPlayer(
    title: String,
    isVideo: Boolean,
    mediaBytes: ByteArray?,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    showControls: Boolean = true,
    enabled: Boolean = true,
    onPlayStateChanged: ((Boolean) -> Unit)? = null,
    onPositionFractionChanged: ((Float) -> Unit)? = null
) {
    val player = remember { PlatformMediaAudioPlayer() }
    var audioReady by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(FALLBACK_SIMULATED_DURATION_MS) }
    var scrubOverride by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(mediaBytes, isVideo, autoPlay) {
        audioReady = if (!isVideo && mediaBytes != null && mediaBytes.isNotEmpty()) {
            player.load(mediaBytes)
        } else {
            player.release()
            false
        }
        durationMs = if (audioReady && player.durationMs() > 0L) {
            player.durationMs()
        } else {
            FALLBACK_SIMULATED_DURATION_MS
        }
        positionMs = 0L
        if (autoPlay && enabled) {
            if (audioReady) player.play()
            isPlaying = true
        }
    }

    LaunchedEffect(isPlaying, audioReady, durationMs) {
        while (isPlaying) {
            delay(MEDIA_POLL_INTERVAL_MS)
            if (audioReady) {
                positionMs = player.currentPositionMs()
                if (!player.isPlaying() && positionMs >= durationMs) {
                    isPlaying = false
                    onPlayStateChanged?.invoke(false)
                }
            } else {
                val next = (positionMs + MEDIA_POLL_INTERVAL_MS).coerceAtMost(durationMs)
                positionMs = next
                if (next >= durationMs) {
                    isPlaying = false
                    onPlayStateChanged?.invoke(false)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    val playLabel = stringResource(if (isPlaying) Res.string.sdui_media_pause else Res.string.sdui_media_play)
    val muteLabel = stringResource(if (isMuted) Res.string.sdui_media_unmute else Res.string.sdui_media_mute)
    val statusBadgeText = when {
        isVideo -> stringResource(Res.string.sdui_media_video_preview_only)
        !audioReady -> stringResource(Res.string.sdui_media_audio_unavailable)
        else -> null
    }
    val progress = if (scrubOverride >= 0f) {
        scrubOverride
    } else if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = ToolkitTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            ToolkitTheme.dimensions.borderUnselected,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(ToolkitTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
            ) {
                Icon(
                    imageVector = if (isVideo) Icons.Default.Movie else Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                )
                Text(
                    text = title,
                    style = ToolkitTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                statusBadgeText?.let { badge ->
                    ToolkitStatusBadge(
                        text = badge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            if (showControls) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
                ) {
                    ToolkitTooltipBox(tooltip = playLabel) {
                        FilledTonalIconButton(
                            onClick = {
                                val nextPlaying = !isPlaying
                                isPlaying = nextPlaying
                                if (audioReady) {
                                    if (nextPlaying) player.play() else player.pause()
                                }
                                onPlayStateChanged?.invoke(nextPlaying)
                            },
                            enabled = enabled,
                            modifier = Modifier.pointerHoverIcon(
                                if (enabled) PointerIcon.Hand else PointerIcon.Default
                            )
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = playLabel
                            )
                        }
                    }

                    Slider(
                        value = progress,
                        onValueChange = { next ->
                            scrubOverride = next.coerceIn(0f, 1f)
                            val nextPos = (durationMs * scrubOverride).toLong()
                            positionMs = nextPos
                            if (audioReady) player.seekToFraction(scrubOverride)
                        },
                        onValueChangeFinished = {
                            if (scrubOverride >= 0f) {
                                onPositionFractionChanged?.invoke(scrubOverride)
                                scrubOverride = -1f
                            }
                        },
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = "${formatMediaTime(positionMs)} / ${formatMediaTime(durationMs)}",
                        style = ToolkitTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!isVideo) {
                        ToolkitTooltipBox(tooltip = muteLabel) {
                            IconButton(
                                onClick = {
                                    val nextMuted = !isMuted
                                    isMuted = nextMuted
                                    player.setMuted(nextMuted)
                                },
                                enabled = enabled && audioReady,
                                modifier = Modifier.pointerHoverIcon(
                                    if (enabled && audioReady) PointerIcon.Hand else PointerIcon.Default
                                )
                            ) {
                                Icon(
                                    imageVector = if (isMuted) {
                                        Icons.AutoMirrored.Filled.VolumeOff
                                    } else {
                                        Icons.AutoMirrored.Filled.VolumeUp
                                    },
                                    contentDescription = muteLabel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMediaTime(millis: Long): String {
    val totalSeconds = (millis / MILLIS_PER_SECOND).coerceAtLeast(0L)
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}
