package org.wip.plugintoolkit.features.shortcuts.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import org.wip.plugintoolkit.features.shortcuts.logic.DefaultShortcutCatalog
import org.wip.plugintoolkit.features.shortcuts.logic.ShortcutManager
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutGesture
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutPointerButton
import org.wip.plugintoolkit.features.shortcuts.utils.KeyMapping

/**
 * Scope for declaring shortcut action handlers on a composable.
 */
interface ShortcutHandlerScope {
    /**
     * Registers a key action trigger. When pressed, automatically checks
     * user-configured triggers (or catalog defaults), eats the event, and invokes [onTrigger].
     */
    fun onKey(
        actionId: String,
        enabled: Boolean = true,
        onTrigger: () -> Unit
    )

    /**
     * Registers a pointer action trigger (Click, DoubleClick, Wheel, etc.).
     */
    fun onPointer(
        actionId: String,
        gesture: ShortcutGesture = ShortcutGesture.Click,
        enabled: Boolean = true,
        onTrigger: (position: Offset) -> Unit
    )

    /**
     * Registers a drag action trigger (e.g. Pan, Box Select, Drag Element).
     */
    fun onDrag(
        actionId: String,
        enabled: Boolean = true,
        onDragStart: (position: Offset) -> Unit = {},
        onDragEnd: () -> Unit = {},
        onDragCancel: () -> Unit = {},
        onDrag: (delta: Offset) -> Unit
    )

    /**
     * Registers a raw key event handler fallback (for ad-hoc keys like Escape, Copy, Paste).
     * If the handler returns true, the key event is considered consumed.
     */
    fun onRawKey(
        handler: (KeyEvent) -> Boolean
    )
}

private class ShortcutHandlerScopeImpl : ShortcutHandlerScope {
    data class KeyEntry(val actionId: String, val enabled: Boolean, val onTrigger: () -> Unit)
    data class PointerEntry(
        val actionId: String,
        val gesture: ShortcutGesture,
        val enabled: Boolean,
        val onTrigger: (Offset) -> Unit
    )
    data class DragEntry(
        val actionId: String,
        val enabled: Boolean,
        val onDragStart: (Offset) -> Unit,
        val onDragEnd: () -> Unit,
        val onDragCancel: () -> Unit,
        val onDrag: (Offset) -> Unit
    )

    val keyEntries = mutableListOf<KeyEntry>()
    val pointerEntries = mutableListOf<PointerEntry>()
    val dragEntries = mutableListOf<DragEntry>()
    val rawKeyHandlers = mutableListOf<(KeyEvent) -> Boolean>()

    override fun onKey(actionId: String, enabled: Boolean, onTrigger: () -> Unit) {
        keyEntries.add(KeyEntry(actionId, enabled, onTrigger))
    }

    override fun onPointer(actionId: String, gesture: ShortcutGesture, enabled: Boolean, onTrigger: (Offset) -> Unit) {
        pointerEntries.add(PointerEntry(actionId, gesture, enabled, onTrigger))
    }

    override fun onDrag(
        actionId: String,
        enabled: Boolean,
        onDragStart: (Offset) -> Unit,
        onDragEnd: () -> Unit,
        onDragCancel: () -> Unit,
        onDrag: (delta: Offset) -> Unit
    ) {
        dragEntries.add(DragEntry(actionId, enabled, onDragStart, onDragEnd, onDragCancel, onDrag))
    }

    override fun onRawKey(handler: (KeyEvent) -> Boolean) {
        rawKeyHandlers.add(handler)
    }
}

/**
 * Attaches a declarative shortcut handler to the current modifier chain.
 * Dispatches key and pointer shortcuts, consumes events automatically via [ShortcutManager.eat],
 * and logs live telemetry.
 */
fun Modifier.shortcutHandler(
    shortcutManager: ShortcutManager?,
    enabled: Boolean = true,
    builder: ShortcutHandlerScope.() -> Unit
): Modifier {
    if (!enabled) return this
    val scope = ShortcutHandlerScopeImpl().apply(builder)

    var mod: Modifier = this

    if (scope.keyEntries.isNotEmpty() || scope.rawKeyHandlers.isNotEmpty()) {
        mod = mod.onKeyEvent { keyEvent ->
            if (keyEvent.type == KeyEventType.KeyDown) {
                for (entry in scope.keyEntries) {
                    if (entry.enabled && matchesKey(
                            shortcutManager = shortcutManager,
                            actionId = entry.actionId,
                            key = keyEvent.key,
                            isCtrl = keyEvent.isCtrlPressed,
                            isShift = keyEvent.isShiftPressed,
                            isAlt = keyEvent.isAltPressed,
                            isMeta = keyEvent.isMetaPressed
                        )
                    ) {
                        entry.onTrigger()
                        return@onKeyEvent true
                    }
                }
            }

            for (rawHandler in scope.rawKeyHandlers) {
                if (rawHandler(keyEvent)) {
                    return@onKeyEvent true
                }
            }

            false
        }
    }

    if (scope.pointerEntries.isNotEmpty() || scope.dragEntries.isNotEmpty()) {
        mod = mod.pointerInput(shortcutManager, scope.pointerEntries, scope.dragEntries) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Press) {
                        var handled = false

                        for (entry in scope.pointerEntries) {
                            if (entry.enabled && matchesPointerEvent(
                                    shortcutManager = shortcutManager,
                                    actionId = entry.actionId,
                                    event = event,
                                    gesture = entry.gesture
                                )
                            ) {
                                eatPointerEvent(shortcutManager, event, entry.actionId)
                                val pos = event.changes.firstOrNull()?.position ?: Offset.Zero
                                entry.onTrigger(pos)
                                handled = true
                                break
                            }
                        }

                        if (handled) continue

                        for (entry in scope.dragEntries) {
                            if (entry.enabled && matchesPointerEvent(
                                    shortcutManager = shortcutManager,
                                    actionId = entry.actionId,
                                    event = event,
                                    gesture = ShortcutGesture.Drag
                                )
                            ) {
                                val change = event.changes.firstOrNull() ?: continue
                                eatPointerEvent(shortcutManager, event, entry.actionId)
                                var lastPos = change.position
                                entry.onDragStart(lastPos)

                                while (true) {
                                    val dragEvent = awaitPointerEvent()
                                    val stillMatches = matchesPointerEvent(
                                        shortcutManager = shortcutManager,
                                        actionId = entry.actionId,
                                        event = dragEvent,
                                        gesture = ShortcutGesture.Drag,
                                        allowConsumed = true
                                    )
                                    if (!stillMatches) {
                                        entry.onDragCancel()
                                        break
                                    }
                                    if (dragEvent.type == PointerEventType.Move) {
                                        val currentPos = dragEvent.changes.firstOrNull()?.position ?: lastPos
                                        val delta = currentPos - lastPos
                                        eatPointerEvent(shortcutManager, dragEvent, entry.actionId)
                                        entry.onDrag(delta)
                                        lastPos = currentPos
                                    } else if (dragEvent.type == PointerEventType.Release) {
                                        entry.onDragEnd()
                                        break
                                    }
                                }
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    return mod
}

/**
 * Dedicated drag gesture modifier driven by [ShortcutManager].
 * Automatically listens for the action's pointer triggers (e.g. Right Drag or Middle Drag),
 * tracks drag delta, consumes changes, and fires lifecycle callbacks.
 */
fun Modifier.shortcutDrag(
    shortcutManager: ShortcutManager?,
    actionId: String,
    enabled: Boolean = true,
    onDragStart: (position: Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (delta: Offset) -> Unit
): Modifier {
    if (!enabled) return this
    return this.pointerInput(shortcutManager, actionId) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Press &&
                    matchesPointerEvent(shortcutManager, actionId, event, ShortcutGesture.Drag)
                ) {
                    val change = event.changes.firstOrNull() ?: continue
                    eatPointerEvent(shortcutManager, event, actionId)
                    var lastPos = change.position
                    onDragStart(lastPos)

                    while (true) {
                        val dragEvent = awaitPointerEvent()
                        val stillMatches = matchesPointerEvent(
                            shortcutManager = shortcutManager,
                            actionId = actionId,
                            event = dragEvent,
                            gesture = ShortcutGesture.Drag,
                            allowConsumed = true
                        )
                        if (!stillMatches) {
                            onDragCancel()
                            break
                        }
                        if (dragEvent.type == PointerEventType.Move) {
                            val currentPos = dragEvent.changes.firstOrNull()?.position ?: lastPos
                            val delta = currentPos - lastPos
                            eatPointerEvent(shortcutManager, dragEvent, actionId)
                            onDrag(delta)
                            lastPos = currentPos
                        } else if (dragEvent.type == PointerEventType.Release) {
                            onDragEnd()
                            break
                        }
                    }
                }
            }
        }
    }
}

/**
 * Convenience modifier to handle a single keyboard action.
 */
fun Modifier.onShortcutKey(
    shortcutManager: ShortcutManager?,
    actionId: String,
    enabled: Boolean = true,
    onTrigger: () -> Unit
): Modifier = shortcutHandler(shortcutManager, enabled) {
    onKey(actionId, enabled = true, onTrigger = onTrigger)
}

/**
 * Convenience modifier to handle a single pointer gesture action (e.g. click).
 */
fun Modifier.onShortcutPointer(
    shortcutManager: ShortcutManager?,
    actionId: String,
    gesture: ShortcutGesture = ShortcutGesture.Click,
    enabled: Boolean = true,
    onTrigger: (position: Offset) -> Unit
): Modifier = shortcutHandler(shortcutManager, enabled) {
    onPointer(actionId, gesture = gesture, enabled = true, onTrigger = onTrigger)
}

internal fun matchesKey(
    shortcutManager: ShortcutManager?,
    actionId: String,
    key: Key,
    isCtrl: Boolean,
    isShift: Boolean,
    isAlt: Boolean,
    isMeta: Boolean
): Boolean {
    if (shortcutManager != null) {
        return shortcutManager.isKeyActionTriggered(
            actionId = actionId,
            key = key,
            isCtrl = isCtrl,
            isShift = isShift,
            isAlt = isAlt,
            isMeta = isMeta
        )
    }
    val defaultAction = DefaultShortcutCatalog.actions.firstOrNull { it.id == actionId } ?: return false
    val shortcutKey = KeyMapping.fromComposeKey(key)
    return defaultAction.defaultTriggers.any { trigger ->
        trigger.matchesModifiers(ctrl = isCtrl, shift = isShift, alt = isAlt, meta = isMeta) &&
            trigger.key == shortcutKey
    }
}

internal fun matchesPointerEvent(
    shortcutManager: ShortcutManager?,
    actionId: String,
    event: PointerEvent,
    gesture: ShortcutGesture,
    allowConsumed: Boolean = false
): Boolean {
    if (shortcutManager != null) {
        return shortcutManager.matchesPointer(
            actionId = actionId,
            event = event,
            gesture = gesture,
            allowConsumed = allowConsumed
        )
    }
    if (!allowConsumed && event.changes.any { it.isConsumed }) return false
    val defaultAction = DefaultShortcutCatalog.actions.firstOrNull { it.id == actionId } ?: return false
    val button = when {
        event.buttons.isPrimaryPressed -> ShortcutPointerButton.Left
        event.buttons.isSecondaryPressed -> ShortcutPointerButton.Right
        event.buttons.isTertiaryPressed -> ShortcutPointerButton.Middle
        else -> ShortcutPointerButton.None
    }
    val keyboardModifiers = event.keyboardModifiers
    return defaultAction.defaultTriggers.any { trigger ->
        trigger.matchesModifiers(
            ctrl = keyboardModifiers.isCtrlPressed,
            shift = keyboardModifiers.isShiftPressed,
            alt = keyboardModifiers.isAltPressed,
            meta = keyboardModifiers.isMetaPressed
        ) && trigger.pointerButton == button && trigger.gesture == gesture
    }
}

internal fun eatPointerEvent(
    shortcutManager: ShortcutManager?,
    event: PointerEvent,
    actionId: String
) {
    if (shortcutManager != null) {
        shortcutManager.eat(event, actionId)
    } else {
        event.changes.forEach { it.consume() }
    }
}
