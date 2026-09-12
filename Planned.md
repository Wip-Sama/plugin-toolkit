# Deferred Architecture Changes

JobHandle.result: Deferred<ExecutionResult> Currently, JobHandle tightly couples the execution contract to kotlinx.coroutines running in the same memory space. If plugins are ever moved out-of-process (e.g., via gRPC or separate processes), Deferred and the Throwable inside ExecutionResult.Error cannot be easily serialized.

### Future Migration Path:

Change val result: Deferred<ExecutionResult> to suspend fun awaitResult(): ExecutionResult.
Replace Throwable in ExecutionResult.Error with a structured ErrorDetail class containing serializable string fields (code, message, stackTrace).

### Memory Management for Undo/Redo in flows [COMPLETED]

- [x] **Undo/Redo Command Pattern:** Implemented bounded diff-based Command pattern (`FlowCommand`, `MoveNodesCommand`, `AddNodeCommand`, `DeleteNodesCommand`, `ConnectPortsCommand`, `DisconnectPortsCommand`, `UpdateNodeCommand`, `CompositeCommand`, `FlowHistoryManager`) storing delta differences instead of deep state snapshots. Bounded to 100 history items with O(diff) heap footprint.

### Central Shortcut Management System [COMPLETED]

- [x] **Central Shortcut Management & Remapping:** Implemented extensible application-wide shortcut system (`ShortcutModel`, `ShortcutManager`, `DefaultShortcutCatalog`, `ShortcutsSettingsView`, `RemapShortcutDialog`) supporting situation-scoped conflict detection, customizable trigger remapping, canonical trigger formatting (`[ modifier + button + gesture ]`), and persistence in `AppSettings`.

### Support for local repositories

A way to insert a local folder as local repository on the system

### Making flow resume work properly

### MILESTONES
- Plugin Standalone Compilation
- Modules