package org.wip.plugintoolkit.features.shortcuts.utils

import androidx.compose.ui.input.key.Key
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutKey

/**
 * Utility for bidirectional mapping between Compose [Key] and domain [ShortcutKey].
 * Supports all standard keys and gracefully adapts any keycode.
 */
object KeyMapping {

    private val keyToShortcutMap = mapOf(
        Key.A to ShortcutKey.A,
        Key.B to ShortcutKey.B,
        Key.C to ShortcutKey.C,
        Key.D to ShortcutKey.D,
        Key.E to ShortcutKey.E,
        Key.F to ShortcutKey.F,
        Key.G to ShortcutKey.G,
        Key.H to ShortcutKey.H,
        Key.I to ShortcutKey.I,
        Key.J to ShortcutKey.J,
        Key.K to ShortcutKey.K,
        Key.L to ShortcutKey.L,
        Key.M to ShortcutKey.M,
        Key.N to ShortcutKey.N,
        Key.O to ShortcutKey.O,
        Key.P to ShortcutKey.P,
        Key.Q to ShortcutKey.Q,
        Key.R to ShortcutKey.R,
        Key.S to ShortcutKey.S,
        Key.T to ShortcutKey.T,
        Key.U to ShortcutKey.U,
        Key.V to ShortcutKey.V,
        Key.W to ShortcutKey.W,
        Key.X to ShortcutKey.X,
        Key.Y to ShortcutKey.Y,
        Key.Z to ShortcutKey.Z,

        Key.Zero to ShortcutKey.Zero,
        Key.One to ShortcutKey.One,
        Key.Two to ShortcutKey.Two,
        Key.Three to ShortcutKey.Three,
        Key.Four to ShortcutKey.Four,
        Key.Five to ShortcutKey.Five,
        Key.Six to ShortcutKey.Six,
        Key.Seven to ShortcutKey.Seven,
        Key.Eight to ShortcutKey.Eight,
        Key.Nine to ShortcutKey.Nine,

        Key.F1 to ShortcutKey.F1,
        Key.F2 to ShortcutKey.F2,
        Key.F3 to ShortcutKey.F3,
        Key.F4 to ShortcutKey.F4,
        Key.F5 to ShortcutKey.F5,
        Key.F6 to ShortcutKey.F6,
        Key.F7 to ShortcutKey.F7,
        Key.F8 to ShortcutKey.F8,
        Key.F9 to ShortcutKey.F9,
        Key.F10 to ShortcutKey.F10,
        Key.F11 to ShortcutKey.F11,
        Key.F12 to ShortcutKey.F12,

        Key.Escape to ShortcutKey.Escape,
        Key.Delete to ShortcutKey.Delete,
        Key.Backspace to ShortcutKey.Backspace,
        Key.Enter to ShortcutKey.Enter,
        Key.Spacebar to ShortcutKey.Space,
        Key.Tab to ShortcutKey.Tab,
        Key.Insert to ShortcutKey.Insert,
        Key.MoveHome to ShortcutKey.Home,
        Key.MoveEnd to ShortcutKey.End,
        Key.PageUp to ShortcutKey.PageUp,
        Key.PageDown to ShortcutKey.PageDown,
        Key.DirectionUp to ShortcutKey.ArrowUp,
        Key.DirectionDown to ShortcutKey.ArrowDown,
        Key.DirectionLeft to ShortcutKey.ArrowLeft,
        Key.DirectionRight to ShortcutKey.ArrowRight,

        Key.Minus to ShortcutKey("-", "-"),
        Key.Equals to ShortcutKey("=", "="),
        Key.LeftBracket to ShortcutKey("[", "["),
        Key.RightBracket to ShortcutKey("]", "]"),
        Key.Backslash to ShortcutKey("\\", "\\"),
        Key.Semicolon to ShortcutKey(";", ";"),
        Key.Apostrophe to ShortcutKey("'", "'"),
        Key.Comma to ShortcutKey(",", ","),
        Key.Period to ShortcutKey(".", "."),
        Key.Slash to ShortcutKey("/", "/")
    )

    private val shortcutToKeyMap: Map<String, Key> = keyToShortcutMap.entries.associate { (k, v) -> v.code to k }

    /**
     * Converts a Compose [Key] to a domain [ShortcutKey].
     * Never fails: falls back to dynamic code if not in standard map.
     */
    fun fromComposeKey(key: Key): ShortcutKey {
        return keyToShortcutMap[key] ?: run {
            val keyString = key.toString()
            val cleanCode = if (keyString.startsWith("Key: ")) keyString.removePrefix("Key: ") else keyString
            ShortcutKey(code = cleanCode, label = cleanCode)
        }
    }

    /**
     * Converts a [ShortcutKey] to a Compose [Key], if recognized.
     */
    fun toComposeKey(shortcutKey: ShortcutKey): Key? {
        return shortcutToKeyMap[shortcutKey.code]
    }

    /**
     * Common selectable keys for key dropdowns / lists in the UI.
     */
    val commonKeys: List<ShortcutKey> by lazy {
        listOf(
            ShortcutKey.A, ShortcutKey.B, ShortcutKey.C, ShortcutKey.D, ShortcutKey.E,
            ShortcutKey.F, ShortcutKey.G, ShortcutKey.H, ShortcutKey.I, ShortcutKey.J,
            ShortcutKey.K, ShortcutKey.L, ShortcutKey.M, ShortcutKey.N, ShortcutKey.O,
            ShortcutKey.P, ShortcutKey.Q, ShortcutKey.R, ShortcutKey.S, ShortcutKey.T,
            ShortcutKey.U, ShortcutKey.V, ShortcutKey.W, ShortcutKey.X, ShortcutKey.Y, ShortcutKey.Z,
            ShortcutKey.Zero, ShortcutKey.One, ShortcutKey.Two, ShortcutKey.Three, ShortcutKey.Four,
            ShortcutKey.Five, ShortcutKey.Six, ShortcutKey.Seven, ShortcutKey.Eight, ShortcutKey.Nine,
            ShortcutKey.Delete, ShortcutKey.Backspace, ShortcutKey.Escape, ShortcutKey.Enter,
            ShortcutKey.Space, ShortcutKey.Tab, ShortcutKey.F1, ShortcutKey.F2, ShortcutKey.F3,
            ShortcutKey.F4, ShortcutKey.F5, ShortcutKey.F6, ShortcutKey.F7, ShortcutKey.F8,
            ShortcutKey.F9, ShortcutKey.F10, ShortcutKey.F11, ShortcutKey.F12,
            ShortcutKey.ArrowUp, ShortcutKey.ArrowDown, ShortcutKey.ArrowLeft, ShortcutKey.ArrowRight
        )
    }
}
