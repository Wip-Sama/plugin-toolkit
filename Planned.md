# Deferred Architecture Changes

JobHandle.result: Deferred<ExecutionResult> Currently, JobHandle tightly couples the execution contract to kotlinx.coroutines running in the same memory space. If plugins are ever moved out-of-process (e.g., via gRPC or separate processes), Deferred and the Throwable inside ExecutionResult.Error cannot be easily serialized.

### Future Migration Path:

Change val result: Deferred<ExecutionResult> to suspend fun awaitResult(): ExecutionResult.
Replace Throwable in ExecutionResult.Error with a structured ErrorDetail class containing serializable string fields (code, message, stackTrace).