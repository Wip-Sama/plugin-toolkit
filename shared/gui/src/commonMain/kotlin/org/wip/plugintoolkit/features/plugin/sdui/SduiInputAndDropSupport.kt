package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.focusable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.sdui.UiDropTarget
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.api.sdui.UiShortcut
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.shortcuts.utils.KeyMapping
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.sdui_drop_files_hint

internal data class SduiShortcutEntry(
    val nodeId: String,
    val depth: Int,
    val registrationOrder: Int,
    val shortcuts: List<UiShortcut>
)

internal class SduiTextInputFocusTracker {
    private val focusedInputIds = mutableSetOf<String>()

    val isTyping: Boolean
        get() = focusedInputIds.isNotEmpty()

    fun setFocused(nodeId: String, focused: Boolean) {
        if (focused) {
            focusedInputIds.add(nodeId)
        } else {
            focusedInputIds.remove(nodeId)
        }
    }
}

internal class SduiShortcutRegistry {
    private var nextOrder = 0
    private val entries = mutableStateMapOf<String, SduiShortcutEntry>()

    fun register(nodeId: String, depth: Int, shortcuts: List<UiShortcut>) {
        if (shortcuts.isEmpty()) {
            entries.remove(nodeId)
            return
        }
        val order = entries[nodeId]?.registrationOrder ?: nextOrder++
        entries[nodeId] = SduiShortcutEntry(
            nodeId = nodeId,
            depth = depth,
            registrationOrder = order,
            shortcuts = shortcuts
        )
    }

    fun unregister(nodeId: String) {
        entries.remove(nodeId)
    }

    fun snapshot(): List<SduiShortcutEntry> = entries.values.toList()
}

internal val LocalSduiTextInputFocusTracker = compositionLocalOf<SduiTextInputFocusTracker?> { null }
internal val LocalSduiShortcutRegistry = compositionLocalOf<SduiShortcutRegistry?> { null }
internal val LocalSduiNodeDepth = compositionLocalOf { 0 }
internal val LocalSduiRootFocusRequest = compositionLocalOf { {} }

/**
 * Normalizes and matches declarative [UiShortcut] definitions against Compose desktop keyboard events,
 * preserving normal typing inside focused [org.wip.plugintoolkit.api.sdui.TextInputNode] controls.
 */
internal object SduiShortcutMatcher {
    private val KEY_ALIASES = mapOf(
        "DEL" to UiShortcut.KEY_DELETE,
        "DELETE" to UiShortcut.KEY_DELETE,
        "FORWARD_DEL" to UiShortcut.KEY_DELETE,
        "BACK" to UiShortcut.KEY_BACKSPACE,
        "BACKSPACE" to UiShortcut.KEY_BACKSPACE,
        "ESC" to UiShortcut.KEY_ESCAPE,
        "ESCAPE" to UiShortcut.KEY_ESCAPE,
        "RETURN" to UiShortcut.KEY_ENTER,
        "ENTER" to UiShortcut.KEY_ENTER,
        "NUMPAD_ENTER" to UiShortcut.KEY_ENTER,
        "SPACE" to UiShortcut.KEY_SPACE,
        "SPACEBAR" to UiShortcut.KEY_SPACE,
        "LEFT" to UiShortcut.KEY_ARROW_LEFT,
        "ARROW_LEFT" to UiShortcut.KEY_ARROW_LEFT,
        "DPAD_LEFT" to UiShortcut.KEY_ARROW_LEFT,
        "DIRECTION_LEFT" to UiShortcut.KEY_ARROW_LEFT,
        "RIGHT" to UiShortcut.KEY_ARROW_RIGHT,
        "ARROW_RIGHT" to UiShortcut.KEY_ARROW_RIGHT,
        "DPAD_RIGHT" to UiShortcut.KEY_ARROW_RIGHT,
        "DIRECTION_RIGHT" to UiShortcut.KEY_ARROW_RIGHT,
        "UP" to UiShortcut.KEY_ARROW_UP,
        "ARROW_UP" to UiShortcut.KEY_ARROW_UP,
        "DPAD_UP" to UiShortcut.KEY_ARROW_UP,
        "DIRECTION_UP" to UiShortcut.KEY_ARROW_UP,
        "DOWN" to UiShortcut.KEY_ARROW_DOWN,
        "ARROW_DOWN" to UiShortcut.KEY_ARROW_DOWN,
        "DPAD_DOWN" to UiShortcut.KEY_ARROW_DOWN,
        "DIRECTION_DOWN" to UiShortcut.KEY_ARROW_DOWN,
        "PGUP" to UiShortcut.KEY_PAGE_UP,
        "PAGEUP" to UiShortcut.KEY_PAGE_UP,
        "PAGE_UP" to UiShortcut.KEY_PAGE_UP,
        "PGDN" to UiShortcut.KEY_PAGE_DOWN,
        "PAGEDOWN" to UiShortcut.KEY_PAGE_DOWN,
        "PAGE_DOWN" to UiShortcut.KEY_PAGE_DOWN,
        "MOVE_HOME" to UiShortcut.KEY_HOME,
        "MOVE_END" to UiShortcut.KEY_END,
        "PLUS" to "=",
        "EQUALS" to "=",
        "MINUS" to "-"
    )

    private val TEXT_EDIT_COMMAND_KEYS = setOf(
        "A", "C", "V", "X", "Z", "Y",
        UiShortcut.KEY_BACKSPACE,
        UiShortcut.KEY_DELETE,
        UiShortcut.KEY_ARROW_LEFT,
        UiShortcut.KEY_ARROW_RIGHT,
        UiShortcut.KEY_HOME,
        UiShortcut.KEY_END
    )

    private val COMPOSE_KEY_TO_NAME = mapOf(
        Key.Delete to UiShortcut.KEY_DELETE,
        Key.Backspace to UiShortcut.KEY_BACKSPACE,
        Key.Escape to UiShortcut.KEY_ESCAPE,
        Key.Enter to UiShortcut.KEY_ENTER,
        Key.NumPadEnter to UiShortcut.KEY_ENTER,
        Key.Spacebar to UiShortcut.KEY_SPACE,
        Key.Tab to UiShortcut.KEY_TAB,
        Key.Insert to "INSERT",
        Key.MoveHome to UiShortcut.KEY_HOME,
        Key.MoveEnd to UiShortcut.KEY_END,
        Key.PageUp to UiShortcut.KEY_PAGE_UP,
        Key.PageDown to UiShortcut.KEY_PAGE_DOWN,
        Key.DirectionLeft to UiShortcut.KEY_ARROW_LEFT,
        Key.DirectionRight to UiShortcut.KEY_ARROW_RIGHT,
        Key.DirectionUp to UiShortcut.KEY_ARROW_UP,
        Key.DirectionDown to UiShortcut.KEY_ARROW_DOWN
    )

    fun normalizeKeyName(raw: String): String {
        val cleaned = raw.trim()
            .uppercase()
            .replace('-', '_')
            .replace(' ', '_')
        if (cleaned == "_") return "_"
        return KEY_ALIASES[cleaned] ?: cleaned
    }

    fun composeKeyToCanonicalName(key: Key): String {
        COMPOSE_KEY_TO_NAME[key]?.let { return it }
        return normalizeKeyName(KeyMapping.fromComposeKey(key).code)
    }

    fun shouldSuppressWhileTyping(shortcut: UiShortcut, isTypingInTextInput: Boolean): Boolean {
        if (!isTypingInTextInput || shortcut.allowWhileTyping) return false
        val hasCommandModifier = shortcut.ctrl || shortcut.alt || shortcut.meta
        if (!hasCommandModifier) return true
        val canonical = normalizeKeyName(shortcut.key)
        return canonical in TEXT_EDIT_COMMAND_KEYS && !shortcut.alt
    }

    fun matches(
        shortcut: UiShortcut,
        canonicalKey: String,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
        meta: Boolean,
        isTypingInTextInput: Boolean
    ): Boolean {
        if (shouldSuppressWhileTyping(shortcut, isTypingInTextInput)) return false
        return normalizeKeyName(shortcut.key) == canonicalKey &&
            shortcut.ctrl == ctrl &&
            shortcut.shift == shift &&
            shortcut.alt == alt &&
            shortcut.meta == meta
    }

    fun findMatchingShortcut(
        shortcuts: List<UiShortcut>,
        canonicalKey: String,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
        meta: Boolean,
        isTypingInTextInput: Boolean
    ): UiShortcut? = shortcuts.firstOrNull { shortcut ->
        matches(
            shortcut = shortcut,
            canonicalKey = canonicalKey,
            ctrl = ctrl,
            shift = shift,
            alt = alt,
            meta = meta,
            isTypingInTextInput = isTypingInTextInput
        )
    }

    fun findBestMatchingEntry(
        entries: List<SduiShortcutEntry>,
        canonicalKey: String,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
        meta: Boolean,
        isTypingInTextInput: Boolean
    ): Pair<String, UiShortcut>? {
        val sorted = entries.sortedWith(
            compareByDescending<SduiShortcutEntry> { it.depth }
                .thenByDescending { it.registrationOrder }
        )
        for (entry in sorted) {
            val match = findMatchingShortcut(
                shortcuts = entry.shortcuts,
                canonicalKey = canonicalKey,
                ctrl = ctrl,
                shift = shift,
                alt = alt,
                meta = meta,
                isTypingInTextInput = isTypingInTextInput
            )
            if (match != null) {
                return entry.nodeId to match
            }
        }
        return null
    }
}

/**
 * Decodes and filters dropped OS desktop file URIs/paths against declarative [UiDropTarget] rules.
 */
internal object SduiDropPathResolver {
    fun decodeDroppedPath(uriOrPath: String): String {
        val trimmed = uriOrPath.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("file:", ignoreCase = true)) {
            runCatching {
                return File(URI(trimmed)).absolutePath
            }
            val withoutScheme = trimmed.substringAfter(':').trimStart('/')
            val decoded = URLDecoder.decode(withoutScheme, StandardCharsets.UTF_8)
            val isWindowsDrive = decoded.length >= 2 && decoded[0].isLetter() && decoded[1] == ':'
            return if (isWindowsDrive) {
                decoded.replace('/', File.separatorChar)
            } else {
                File("/$decoded").absolutePath
            }
        }
        return trimmed
    }

    fun matchesExtension(fileName: String, allowedExtensions: List<String>): Boolean {
        if (allowedExtensions.isEmpty()) return true
        val normalized = allowedExtensions
            .map { it.trim().removePrefix(".").lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (normalized.isEmpty()) return true
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext.isNotEmpty() && ext in normalized
    }

    fun filterDroppedPaths(rawUrisOrPaths: List<String>, target: UiDropTarget): List<String> {
        if (!target.acceptFiles && !target.acceptDirectories) return emptyList()
        val decoded = rawUrisOrPaths
            .asSequence()
            .map(::decodeDroppedPath)
            .filter { it.isNotEmpty() }
            .distinct()
            .filter { path ->
                val file = File(path)
                val isDir = file.exists() && file.isDirectory
                if (isDir) {
                    target.acceptDirectories
                } else {
                    target.acceptFiles && matchesExtension(file.name.ifEmpty { path }, target.allowedExtensions)
                }
            }
            .toList()
        val max = target.maxFiles
        return if (max != null && max > 0) decoded.take(max) else decoded
    }
}

/**
 * Registers focus changes of an SDUI [org.wip.plugintoolkit.api.sdui.TextInputNode] so single-key
 * workspace shortcuts do not interrupt active text editing.
 */
internal fun Modifier.trackSduiTextInputFocus(nodeId: String): Modifier = composed {
    val tracker = LocalSduiTextInputFocusTracker.current
    DisposableEffect(nodeId, tracker) {
        onDispose {
            tracker?.setFocused(nodeId, false)
        }
    }
    if (tracker == null) {
        this
    } else {
        onFocusChanged { focusState ->
            tracker.setFocused(nodeId, focusState.isFocused)
        }
    }
}

/**
 * Registers node-declared keyboard shortcuts and handles focused subtree key events directly
 * before bubbling up to the root shortcut dispatcher.
 */
internal fun Modifier.sduiShortcuts(
    nodeId: String,
    shortcuts: List<UiShortcut>,
    enabled: Boolean,
    onEvent: (UiEvent) -> Unit
): Modifier = composed {
    val registry = LocalSduiShortcutRegistry.current
    val tracker = LocalSduiTextInputFocusTracker.current
    val depth = LocalSduiNodeDepth.current

    DisposableEffect(nodeId, depth, shortcuts, enabled, registry) {
        if (enabled && shortcuts.isNotEmpty()) {
            registry?.register(nodeId, depth, shortcuts)
        } else {
            registry?.unregister(nodeId)
        }
        onDispose {
            registry?.unregister(nodeId)
        }
    }

    if (!enabled || shortcuts.isEmpty()) {
        this
    } else {
        onKeyEvent { keyEvent ->
            if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
            val canonicalKey = SduiShortcutMatcher.composeKeyToCanonicalName(keyEvent.key)
            val matched = SduiShortcutMatcher.findMatchingShortcut(
                shortcuts = shortcuts,
                canonicalKey = canonicalKey,
                ctrl = keyEvent.isCtrlPressed,
                shift = keyEvent.isShiftPressed,
                alt = keyEvent.isAltPressed,
                meta = keyEvent.isMetaPressed,
                isTypingInTextInput = tracker?.isTyping == true
            ) ?: return@onKeyEvent false

            onEvent(
                UiEvent.ShortcutTriggered(
                    nodeId = nodeId,
                    action = matched.action,
                    key = SduiShortcutMatcher.normalizeKeyName(matched.key),
                    ctrl = matched.ctrl,
                    shift = matched.shift,
                    alt = matched.alt,
                    meta = matched.meta
                )
            )
            true
        }
    }
}

/**
 * Dispatches root-level SDUI key events when no inner focused subtree consumed the shortcut.
 */
internal fun Modifier.sduiRootShortcutHost(
    registry: SduiShortcutRegistry,
    tracker: SduiTextInputFocusTracker,
    onEvent: (UiEvent) -> Unit
): Modifier = this
    .focusable()
    .onKeyEvent { keyEvent: KeyEvent ->
        if (keyEvent.type != KeyEventType.KeyDown) return@onKeyEvent false
        val canonicalKey = SduiShortcutMatcher.composeKeyToCanonicalName(keyEvent.key)
        val matched = SduiShortcutMatcher.findBestMatchingEntry(
            entries = registry.snapshot(),
            canonicalKey = canonicalKey,
            ctrl = keyEvent.isCtrlPressed,
            shift = keyEvent.isShiftPressed,
            alt = keyEvent.isAltPressed,
            meta = keyEvent.isMetaPressed,
            isTypingInTextInput = tracker.isTyping
        ) ?: return@onKeyEvent false

        val (targetNodeId, shortcut) = matched
        onEvent(
            UiEvent.ShortcutTriggered(
                nodeId = targetNodeId,
                action = shortcut.action,
                key = SduiShortcutMatcher.normalizeKeyName(shortcut.key),
                ctrl = shortcut.ctrl,
                shift = shortcut.shift,
                alt = shortcut.alt,
                meta = shortcut.meta
            )
        )
        true
    }

/**
 * Attaches an OS desktop external drag-and-drop receiver for files/folders with Material 3 hover feedback.
 */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.sduiDropTarget(
    nodeId: String,
    dropTarget: UiDropTarget?,
    enabled: Boolean,
    clipRounded: Boolean,
    onEvent: (UiEvent) -> Unit
): Modifier = composed {
    if (dropTarget == null || !enabled) return@composed this

    var isHovering by remember(nodeId) { mutableStateOf(false) }
    val hoverAlpha by animateFloatAsState(
        targetValue = if (isHovering) 1f else 0f,
        animationSpec = spring()
    )
    val defaultHint = stringResource(Res.string.sdui_drop_files_hint)
    val hintText = dropTarget.hint?.takeIf { it.isNotBlank() } ?: defaultHint
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainerColor = MaterialTheme.colorScheme.onPrimaryContainer
    val labelStyle = ToolkitTheme.typography.labelSmall.copy(color = onPrimaryContainerColor)
    val dropShape = if (clipRounded) ToolkitTheme.shapes.medium else ToolkitTheme.shapes.small
    val borderWidthDp = ToolkitTheme.dimensions.borderSelected
    val pillPadH = ToolkitTheme.spacing.sm
    val pillPadV = ToolkitTheme.spacing.xxs
    val overlayAlpha = ToolkitTheme.opacity.textFieldContainer
    val badgeBgAlpha = ToolkitTheme.opacity.almostOpaque

    val target = remember(nodeId, dropTarget, onEvent) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                isHovering = true
            }

            override fun onExited(event: DragAndDropEvent) {
                isHovering = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                isHovering = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                isHovering = false
                val filesData = event.dragData() as? DragData.FilesList ?: return false
                val accepted = SduiDropPathResolver.filterDroppedPaths(filesData.readFiles(), dropTarget)
                if (accepted.isEmpty()) return false
                onEvent(
                    UiEvent.FilesDropped(
                        nodeId = nodeId,
                        action = dropTarget.action,
                        paths = accepted
                    )
                )
                return true
            }
        }
    }

    this
        .dragAndDropTarget(
            shouldStartDragAndDrop = { startEvent ->
                startEvent.dragData() is DragData.FilesList
            },
            target = target
        )
        .drawWithContent {
            drawContent()
            if (hoverAlpha > 0.01f) {
                val cornerPx = dropShape.topStart.toPx(size, density)
                val strokePx = with(density) { borderWidthDp.toPx() }
                drawRoundRect(
                    color = primaryColor.copy(alpha = overlayAlpha * hoverAlpha),
                    cornerRadius = CornerRadius(cornerPx, cornerPx)
                )
                drawRoundRect(
                    color = primaryColor.copy(alpha = hoverAlpha),
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                    style = Stroke(width = strokePx)
                )

                val measured = textMeasurer.measure(hintText, style = labelStyle)
                val padX = with(density) { pillPadH.toPx() }
                val padY = with(density) { pillPadV.toPx() }
                val badgeW = (measured.size.width + padX * 2f).coerceAtMost(size.width)
                val badgeH = measured.size.height + padY * 2f
                val badgeLeft = ((size.width - badgeW) / 2f).coerceAtLeast(0f)
                val badgeTop = ((size.height - badgeH) / 2f).coerceAtLeast(0f)
                val badgeRadius = badgeH / 2f
                drawRoundRect(
                    color = primaryContainerColor.copy(alpha = badgeBgAlpha * hoverAlpha),
                    topLeft = Offset(badgeLeft, badgeTop),
                    size = Size(badgeW, badgeH),
                    cornerRadius = CornerRadius(badgeRadius, badgeRadius)
                )
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        x = badgeLeft + ((badgeW - measured.size.width) / 2f).coerceAtLeast(0f),
                        y = badgeTop + padY
                    ),
                    alpha = hoverAlpha
                )
            }
        }
}
