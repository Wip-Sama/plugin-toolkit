package org.wip.plugintoolkit.features.shortcuts.model

import kotlinx.serialization.Serializable
import org.wip.plugintoolkit.core.model.LocalizedString
import org.wip.plugintoolkit.core.utils.FileUtils

/**
 * Pure keyboard modifier mask representing modifier keys held during a key chord.
 */
@Serializable
data class ModifierMask(
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false
) {
    companion object {
        val None = ModifierMask()

        /**
         * Resolves the primary desktop command modifier:
         * Meta (Command) on macOS, Ctrl on Windows and Linux.
         */
        fun primary(
            shift: Boolean = false,
            alt: Boolean = false,
            isMac: Boolean = FileUtils.isMac
        ): ModifierMask = ModifierMask(
            ctrl = !isMac,
            meta = isMac,
            shift = shift,
            alt = alt
        )
    }

    fun matches(
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
        meta: Boolean
    ): Boolean = this.ctrl == ctrl &&
        this.shift == shift &&
        this.alt == alt &&
        this.meta == meta
}

/**
 * Represents a discrete keyboard shortcut chord (e.g. `Ctrl + Z`, `Cmd + Shift + P`).
 */
@Serializable
data class KeyChord(
    val key: ShortcutKey,
    val modifiers: ModifierMask = ModifierMask.None
) {
    companion object {
        fun primary(
            key: ShortcutKey,
            shift: Boolean = false,
            alt: Boolean = false,
            isMac: Boolean = FileUtils.isMac
        ): KeyChord = KeyChord(
            key = key,
            modifiers = ModifierMask.primary(shift = shift, alt = alt, isMac = isMac)
        )
    }

    /**
     * Formats the chord canonically for the active platform.
     */
    fun format(isMac: Boolean = FileUtils.isMac): String {
        val parts = mutableListOf<String>()
        if (modifiers.meta) parts.add(if (isMac) "Cmd" else "Win")
        if (modifiers.ctrl) parts.add("Ctrl")
        if (modifiers.alt) parts.add(if (isMac) "Option" else "Alt")
        if (modifiers.shift) parts.add("Shift")
        parts.add(key.label)
        return if (parts.isEmpty()) "[ None ]" else "[ ${parts.joinToString(" + ")} ]"
    }

    fun matches(
        testKey: ShortcutKey,
        ctrl: Boolean,
        shift: Boolean,
        alt: Boolean,
        meta: Boolean
    ): Boolean = key == testKey && modifiers.matches(ctrl, shift, alt, meta)
}

/**
 * Hierarchical focus scope for discrete keyboard shortcut dispatching.
 * Higher priority rank takes precedence during scoped event dispatch.
 */
@Serializable
enum class ShortcutScope(val priorityRank: Int) {
    Modal(400),
    FocusedWidget(300),
    FeatureCanvas(200),
    Global(100)
}

/**
 * Definition of a discrete keyboard accelerator command.
 */
@Serializable
data class ShortcutCommand(
    val id: String,
    val title: LocalizedString,
    val description: LocalizedString,
    val scope: ShortcutScope,
    val defaultChords: List<KeyChord>,
    val isConfigurable: Boolean = true
) {
    val defaultChord: KeyChord
        get() = defaultChords.firstOrNull() ?: KeyChord(key = ShortcutKey.fromCode(""))
}

/**
 * Conflict where two commands within the same [ShortcutScope] share an identical [KeyChord].
 */
data class KeyConflict(
    val command1: ShortcutCommand,
    val command2: ShortcutCommand,
    val chord: KeyChord,
    val scope: ShortcutScope
)
