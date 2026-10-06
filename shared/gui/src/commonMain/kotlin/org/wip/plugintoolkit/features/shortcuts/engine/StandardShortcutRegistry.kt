package org.wip.plugintoolkit.features.shortcuts.engine

import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.wip.plugintoolkit.features.shortcuts.model.KeyChord
import org.wip.plugintoolkit.features.shortcuts.model.KeyConflict
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutCommand
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutKey
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutScope

/**
 * Standard implementation of [ShortcutRegistry] and [ShortcutDispatcher].
 * Manages discrete keyboard commands, active overrides, scope-aware conflict detection,
 * and scoped keyboard event matching.
 */
class StandardShortcutRegistry(
    initialCommands: List<ShortcutCommand> = DefaultShortcutCommands.commands
) : ShortcutRegistry, ShortcutDispatcher {

    private val commandsMap: Map<String, ShortcutCommand> = initialCommands.associateBy { it.id }

    override val allCommands: List<ShortcutCommand> = initialCommands

    private val _userBindings = MutableStateFlow<Map<String, List<KeyChord>>>(emptyMap())
    override val userBindings: StateFlow<Map<String, List<KeyChord>>> = _userBindings.asStateFlow()

    override fun getCommand(id: String): ShortcutCommand? = commandsMap[id]

    override fun getEffectiveChords(commandId: String): List<KeyChord> {
        val custom = _userBindings.value[commandId]
        if (custom != null) return custom
        return commandsMap[commandId]?.defaultChords ?: emptyList()
    }

    override fun getEffectiveChord(commandId: String): KeyChord? {
        return getEffectiveChords(commandId).firstOrNull()
    }

    override fun isCustomized(commandId: String): Boolean {
        return _userBindings.value.containsKey(commandId)
    }

    override fun updateBindings(commandId: String, chords: List<KeyChord>) {
        _userBindings.value = _userBindings.value + (commandId to chords)
        Logger.i { "Updated shortcut chords for $commandId: ${chords.joinToString { it.format() }}" }
    }

    override fun resetBinding(commandId: String) {
        _userBindings.value = _userBindings.value - commandId
        Logger.i { "Reset shortcut chords for $commandId to default" }
    }

    override fun resetAll() {
        _userBindings.value = emptyMap()
        Logger.i { "Reset all shortcut commands to defaults" }
    }

    override fun findConflicts(scope: ShortcutScope?): List<KeyConflict> {
        val targetCommands = if (scope != null) {
            allCommands.filter { it.scope == scope }
        } else {
            allCommands
        }

        val conflicts = mutableListOf<KeyConflict>()
        for (i in targetCommands.indices) {
            val cmd1 = targetCommands[i]
            val chords1 = getEffectiveChords(cmd1.id)
            for (j in i + 1 until targetCommands.size) {
                val cmd2 = targetCommands[j]
                if (cmd1.scope == cmd2.scope) {
                    val chords2 = getEffectiveChords(cmd2.id)
                    for (c1 in chords1) {
                        for (c2 in chords2) {
                            if (c1 == c2) {
                                conflicts.add(KeyConflict(cmd1, cmd2, c1, cmd1.scope))
                            }
                        }
                    }
                }
            }
        }
        return conflicts
    }

    override fun checkConflict(commandId: String, candidateChord: KeyChord): KeyConflict? {
        val targetCommand = getCommand(commandId) ?: return null
        for (other in allCommands) {
            if (other.id != commandId && other.scope == targetCommand.scope) {
                val otherChords = getEffectiveChords(other.id)
                if (otherChords.any { it == candidateChord }) {
                    return KeyConflict(targetCommand, other, candidateChord, targetCommand.scope)
                }
            }
        }
        return null
    }

    override fun resolveMatchingCommand(
        key: ShortcutKey,
        isCtrl: Boolean,
        isShift: Boolean,
        isAlt: Boolean,
        isMeta: Boolean,
        activeScope: ShortcutScope
    ): String? {
        // Evaluate candidates in active scope first, then fall back to Global scope if different
        val candidateScopes = if (activeScope == ShortcutScope.Global) {
            listOf(ShortcutScope.Global)
        } else {
            listOf(activeScope, ShortcutScope.Global)
        }

        for (scope in candidateScopes) {
            val scopeCommands = allCommands.filter { it.scope == scope }
            for (cmd in scopeCommands) {
                val chords = getEffectiveChords(cmd.id)
                if (chords.any { it.matches(key, isCtrl, isShift, isAlt, isMeta) }) {
                    return cmd.id
                }
            }
        }

        return null
    }
}
