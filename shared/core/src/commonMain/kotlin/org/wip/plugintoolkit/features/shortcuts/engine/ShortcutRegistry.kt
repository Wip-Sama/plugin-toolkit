package org.wip.plugintoolkit.features.shortcuts.engine

import kotlinx.coroutines.flow.StateFlow
import org.wip.plugintoolkit.features.shortcuts.model.KeyChord
import org.wip.plugintoolkit.features.shortcuts.model.KeyConflict
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutCommand
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutScope

/**
 * Pure keyboard shortcut registry managing command registrations, custom overrides, and conflict checks.
 */
interface ShortcutRegistry {
    val allCommands: List<ShortcutCommand>
    val userBindings: StateFlow<Map<String, List<KeyChord>>>

    fun getCommand(id: String): ShortcutCommand?
    fun getEffectiveChords(commandId: String): List<KeyChord>
    fun getEffectiveChord(commandId: String): KeyChord?
    fun isCustomized(commandId: String): Boolean
    fun updateBindings(commandId: String, chords: List<KeyChord>)
    fun resetBinding(commandId: String)
    fun resetAll()
    fun findConflicts(scope: ShortcutScope? = null): List<KeyConflict>
    fun checkConflict(commandId: String, candidateChord: KeyChord): KeyConflict?
}
