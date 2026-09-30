# Plugin Lifecycle, State, & Host Services Reference

This document details the plugin lifecycle, sandboxed file systems, pause/resume execution, process monitoring, and host infrastructure integrations.

---

## 1. Complete Plugin Lifecycle

Plugins pass through well-defined lifecycle stages managed by the host application:

1. **`initialize(context)`**: Invoked once when the plugin classloader loads the plugin. Use for static wiring or DI registrations.
2. **`performSetup(context)` (`@PluginSetup`)**: Triggered on first-time installation or manual user setup. Used to extract bundled binaries or download initial assets.
3. **`performUpdate(context)` (`@PluginUpdate`)**: Triggered when a new plugin version replaces an older version. Used for data migrations while preserving user files.
4. **`performLoad(context)` (`@PluginLoad`)**: Invoked immediately after initialization or setup/update. Used for runtime dependency verification (e.g. checking if Python or CUDA is available).
5. **`validate(context)` (`@PluginValidate`)**: Runs immediately before execution to ensure the plugin is healthy and configured.
6. **Execution (`@Capability`)**: Handles incoming capability requests.
7. **`shutdown()`**: Invoked before unloading or on application exit. Must release file locks, background threads, and spawned processes.

---

## 2. State Management: Pause & Resume

For long-running tasks, plugins can support pause and resume without blocking host worker threads.

### Implementation Steps:
1. Set `supportsPause = true` in `@Capability`.
2. Add an optional `@ResumeState resumeState: JsonElement?` parameter.
3. Listen for pause signals using `context.signals.onSignal`.
4. Return `ExecutionResult.Paused(encodedState)` when pausing.

```kotlin
@Capability(name = "Batch Downloader", supportsPause = true)
suspend fun batchDownload(
    @CapabilityParam("URL List") urls: List<String>,
    @ResumeState resumeState: JsonElement?,
    context: PluginContext
): ExecutionResult {
    var currentIndex = resumeState?.jsonPrimitive?.intOrNull ?: 0
    var isPaused = false

    context.signals.onSignal { signal ->
        if (signal == PluginSignal.PAUSE) {
            isPaused = true
        }
    }

    while (currentIndex < urls.size) {
        if (isPaused) {
            // Save current progress and exit cleanly
            return ExecutionResult.Paused(JsonPrimitive(currentIndex))
        }

        downloadItem(urls[currentIndex])
        currentIndex++
        context.progress.report(currentIndex.toFloat() / urls.size)
    }

    return ExecutionResult.Success(PluginResponse("All downloads complete"))
}
```

> **Note**: Do not throw `PluginPausedException` (deprecated). Return `ExecutionResult.Paused` for clean structured concurrency.

---

## 3. Host Services & Sandboxed File Systems

The host provides isolated abstractions via `PluginContext`:

### 1. `PluginFileSystem` (`context.fileSystem`)
- **Scope**: Persistent, isolated directory dedicated to this plugin.
- **Persistence**: Preserved across capability executions and application restarts.
- **Operations**: File read/write, directory creation, deleting files, path resolution (`getBasePath()`).

### 2. `ExecutionFileSystem` (`context.executionFileSystem`)
- **Scope**: Ephemeral sandbox scoped to the currently running execution or flow job.
- **Persistence**: Automatically wiped and deleted after the execution finishes.
- **Use Case**: Temporary intermediate files, scratch buffers, transient logs.

### 3. `HostFileSystem` (`context.hostFileSystem`)
- **Scope**: Host machine filesystem.
- **Access Rule**: Restricted strictly to paths explicitly authorized by the user via `@CapabilityInput` (read access) and `@CapabilityOutput` (write access). Arbitrary external file access is blocked by default.

### 4. `PluginStorage` (`context.storage`)
- **Scope**: Internal persistent key-value store (`get`, `put`, `getAll`, `remove`).
- **Use Case**: Storing internal plugin state, migration counters, or feature flags without exposing them in user settings.

### 5. `PluginLogger` (`context.logger`)
- Formats log messages into the host application's central log viewer (`info`, `warn`, `error`, `debug`).

---

## 4. Extracting Bundled Resources

Plugins frequently bundle native binaries, helper scripts, or default templates inside the plugin JAR. Extract them to the persistent plugin folder inside `@PluginSetup`:

```kotlin
@PluginSetup
suspend fun setup(context: PluginContext): Result<Unit> {
    val fs = context.fileSystem
    context.logger.info("Extracting helper tool...")

    return fs.extractResource(
        resourcePath = "bin/helper.exe",
        targetRelativePath = "tools/helper.exe"
    )
}
```

Once extracted, obtain the absolute path for execution:
```kotlin
val absoluteExePath = "${context.fileSystem.getBasePath()}/tools/helper.exe"
```

---

## 5. Process Monitoring & Memory Tracking

Plugins spawning CLI commands, Python runtimes, or child processes can track memory consumption:

### 1. Automatic Descendant Process Sampling
The host application continuously samples all child and grandchild processes spawned by the JVM, incorporating their memory into peak memory telemetry.

### 2. Explicit Process Watcher (`context.watchProcess`)
For detached or explicit external processes, use `context.watchProcess`:

```kotlin
@Capability(name = "Execute Script")
fun runScript(arg: String, context: PluginContext): String {
    val process = ProcessBuilder("python", "script.py", arg).start()

    // Register with host monitoring
    context.watchProcess(process).use { watcher ->
        context.logger.info("Watching PID ${watcher.pid}")
        process.waitFor()
        context.logger.info("Peak memory: ${watcher.getPeakMemoryBytes()} bytes")
    }

    return "Done"
}
```

---

## 6. Dynamic Locks & Feature Gates (`checkLocks`)

Plugins can dynamically lock capabilities or dropdown options until prerequisites are satisfied (e.g. model weights downloaded):

```kotlin
class MyProcessor : DataProcessor {
    suspend fun checkLocks(context: PluginContext): Map<String, Boolean> {
        val modelReady = context.fileSystem.exists("models/weights.onnx")
        return mapOf("model_downloaded" to modelReady)
    }

    @Capability(
        name = "Inference",
        requiredLocks = ["model_downloaded"]
    )
    fun runInference(): String = "Result"
}
```
