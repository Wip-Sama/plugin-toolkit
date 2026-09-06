package org.wip.plugintoolkit.features.flows.history

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorState

/**
 * Bounded history manager executing and tracking diff-based [FlowCommand] instances.
 * Guarantees fixed O(diff) heap retention with bounded capacity.
 */
class FlowHistoryManager(
    private val maxStackSize: Int = 100
) {
    private val undoStack = ArrayDeque<FlowCommand>()
    private val redoStack = ArrayDeque<FlowCommand>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    /**
     * Executes a command on [currentState], registers it in the undo stack, and clears redo.
     */
    fun executeCommand(command: FlowCommand, currentState: FlowEditorState): FlowEditorState {
        val newState = command.execute(currentState)
        undoStack.addLast(command)
        if (undoStack.size > maxStackSize) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        syncState()
        return newState
    }

    /**
     * Records an already-applied command directly into the undo history without re-executing it.
     */
    fun recordExecutedCommand(command: FlowCommand) {
        undoStack.addLast(command)
        if (undoStack.size > maxStackSize) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        syncState()
    }

    /**
     * Undoes the last command, returning the reverted state, or null if history is empty.
     */
    fun undo(currentState: FlowEditorState): FlowEditorState? {
        if (undoStack.isEmpty()) return null
        val command = undoStack.removeLast()
        val revertedState = command.undo(currentState)
        redoStack.addLast(command)
        syncState()
        return revertedState
    }

    /**
     * Redoes the last undone command, returning the updated state, or null if redo stack is empty.
     */
    fun redo(currentState: FlowEditorState): FlowEditorState? {
        if (redoStack.isEmpty()) return null
        val command = redoStack.removeLast()
        val redoneState = command.execute(currentState)
        undoStack.addLast(command)
        syncState()
        return redoneState
    }

    /**
     * Clears all history.
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        syncState()
    }

    val undoCount: Int get() = undoStack.size
    val redoCount: Int get() = redoStack.size

    private fun syncState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }
}
