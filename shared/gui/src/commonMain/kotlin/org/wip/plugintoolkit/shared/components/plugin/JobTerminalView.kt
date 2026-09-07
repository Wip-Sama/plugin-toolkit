package org.wip.plugintoolkit.shared.components.plugin

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.terminal_link_click_to_open_path
import plugintoolkit.composeapp.generated.resources.terminal_link_click_to_open_url
import plugintoolkit.composeapp.generated.resources.terminal_link_shift_click_to_open_path
import plugintoolkit.composeapp.generated.resources.terminal_link_shift_click_to_open_url

internal data class CombinedLogData(
    val annotatedText: AnnotatedString,
    val linkSpans: List<TerminalLinkSpan>
)

internal fun buildCombinedLogData(
    logs: List<String>,
    isShiftPressed: Boolean,
    defaultColor: Color,
    linkColor: Color,
    errorColor: Color,
    warnColor: Color
): CombinedLogData {
    val allSpans = mutableListOf<TerminalLinkSpan>()
    val annotatedText = buildAnnotatedString {
        var currentOffset = 0
        logs.forEachIndexed { index, logLine ->
            val baseColor = when {
                logLine.contains("[ERROR]", ignoreCase = true) || logLine.startsWith("ERROR:") -> errorColor
                logLine.contains("[WARN]", ignoreCase = true) || logLine.startsWith("WARN:") -> warnColor
                else -> defaultColor
            }

            val lineSpans = TerminalLinkHelper.findLinkSpans(logLine)
            if (lineSpans.isEmpty()) {
                withStyle(SpanStyle(color = baseColor)) {
                    append(logLine)
                }
            } else {
                var currentIndex = 0
                for (span in lineSpans) {
                    if (span.start > currentIndex) {
                        withStyle(SpanStyle(color = baseColor)) {
                            append(logLine.substring(currentIndex, span.start))
                        }
                    }
                    val style = if (isShiftPressed) {
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        SpanStyle(color = baseColor)
                    }
                    withStyle(style) {
                        append(logLine.substring(span.start, span.end))
                    }
                    allSpans.add(
                        TerminalLinkSpan(
                            start = currentOffset + span.start,
                            end = currentOffset + span.end,
                            target = span.target,
                            isUrl = span.isUrl
                        )
                    )
                    currentIndex = span.end
                }
                if (currentIndex < logLine.length) {
                    withStyle(SpanStyle(color = baseColor)) {
                        append(logLine.substring(currentIndex))
                    }
                }
            }

            currentOffset += logLine.length
            if (index < logs.size - 1) {
                append("\n")
                currentOffset += 1
            }
        }
    }
    return CombinedLogData(annotatedText, allSpans)
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TerminalView(
    logs: List<String>,
    scrollState: ScrollState,
    onOpenUrl: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val errorColor = MaterialTheme.colorScheme.error
    val warnColor = MaterialTheme.colorScheme.tertiary
    val defaultColor = MaterialTheme.colorScheme.onSurfaceVariant
    val linkColor = MaterialTheme.colorScheme.primary

    var isShiftHeld by remember { mutableStateOf(false) }
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    var hoveredSpan by remember { mutableStateOf<TerminalLinkSpan?>(null) }
    var pointerPosition by remember { mutableStateOf(Offset.Zero) }

    val logData = remember(logs, isShiftHeld, defaultColor, linkColor, errorColor, warnColor) {
        buildCombinedLogData(logs, isShiftHeld, defaultColor, linkColor, errorColor, warnColor)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        SelectionContainer {
            Text(
                text = logData.annotatedText,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                onTextLayout = { layoutResult = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerHoverIcon(if (hoveredSpan != null) PointerIcon.Hand else PointerIcon.Default)
                    .onPointerEvent(PointerEventType.Move) { event ->
                        isShiftHeld = event.keyboardModifiers.isShiftPressed
                        if (event.buttons.isPrimaryPressed) {
                            hoveredSpan = null
                            return@onPointerEvent
                        }
                        val pos = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                        pointerPosition = pos
                        val layout = layoutResult
                        if (layout != null && logData.linkSpans.isNotEmpty()) {
                            val offset = layout.getOffsetForPosition(pos)
                            hoveredSpan = logData.linkSpans.firstOrNull { offset in it.start until it.end }
                        } else {
                            hoveredSpan = null
                        }
                    }
                    .onPointerEvent(PointerEventType.Exit) {
                        hoveredSpan = null
                    }
                    .pointerInput(logData.linkSpans) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                if (event.type == PointerEventType.Press && event.keyboardModifiers.isShiftPressed) {
                                    val change = event.changes.firstOrNull() ?: continue
                                    val pos = change.position
                                    val layout = layoutResult ?: continue
                                    val charOffset = layout.getOffsetForPosition(pos)
                                    val clickedSpan = logData.linkSpans.firstOrNull { charOffset in it.start until it.end }
                                    if (clickedSpan != null) {
                                        change.consume()
                                        if (clickedSpan.isUrl) {
                                            onOpenUrl(clickedSpan.target)
                                        } else {
                                            onOpenFolder(clickedSpan.target)
                                        }
                                    }
                                }
                            }
                        }
                    }
            )
        }

        if (hoveredSpan != null) {
            val span = hoveredSpan!!
            val tooltipText = if (isShiftHeld) {
                if (span.isUrl) stringResource(Res.string.terminal_link_click_to_open_url, span.target)
                else stringResource(Res.string.terminal_link_click_to_open_path, span.target)
            } else {
                if (span.isUrl) stringResource(Res.string.terminal_link_shift_click_to_open_url, span.target)
                else stringResource(Res.string.terminal_link_shift_click_to_open_path, span.target)
            }

            DisableSelection {
                Popup(
                    offset = IntOffset(
                        pointerPosition.x.toInt(),
                        (pointerPosition.y + 20f).toInt()
                    ),
                    properties = PopupProperties(focusable = false)
                ) {
                    Surface(
                        shape = ToolkitTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = ToolkitTheme.spacing.small
                    ) {
                        Text(
                            text = tooltipText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(
                                horizontal = ToolkitTheme.spacing.small,
                                vertical = ToolkitTheme.spacing.extraSmall
                            )
                        )
                    }
                }
            }
        }
    }
}
