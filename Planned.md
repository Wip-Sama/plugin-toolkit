# Deferred Architecture Changes

JobHandle.result: Deferred<ExecutionResult> Currently, JobHandle tightly couples the execution contract to kotlinx.coroutines running in the same memory space. If plugins are ever moved out-of-process (e.g., via gRPC or separate processes), Deferred and the Throwable inside ExecutionResult.Error cannot be easily serialized.

### Future Migration Path:

Change val result: Deferred<ExecutionResult> to suspend fun awaitResult(): ExecutionResult.
Replace Throwable in ExecutionResult.Error with a structured ErrorDetail class containing serializable string fields (code, message, stackTrace).

### Memory Management for Undo/Redo in flows

Undo/Redo Command Pattern: Implement a full Command pattern for the history stack (e.g., NodeMovedCommand, NodeAddedCommand) to store diffs instead of deep state snapshots. This will ensure memory usage remains low even with massive graphs and extensive edit histories.

### Support for portable installation

A build of the application to allow for a portable installation

### Support for local repositories

A way to insert a local folder as local repository on the system

### Making flow resum work properly

