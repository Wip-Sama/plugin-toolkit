This is a Kotlin Multiplatform project targeting Desktop (JVM).

## Plugin-defined pages

Plugins can organize capabilities into pages rendered by the host, without bundling Compose UI binaries:

```kotlin
@PluginUiPage(
    id = "convert",
    title = "Convert media",
    description = "Choose an operation to begin.",
    capabilityNames = ["Convert image", "Convert video"]
)
@PluginInfo(/* ... */)
class MediaPlugin
```

Unknown capability names are ignored. The declarative contract stays usable across host UI upgrades and
can also be interpreted by future web or command-line front ends.

* [/composeApp](./composeApp/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./composeApp/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./composeApp/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./composeApp/src/jvmMain/kotlin)
    folder is the appropriate location.

### Build and Run Desktop (JVM) Application

To build and run the development version of the desktop app, use the run configuration from the run widget
in your IDE’s toolbar or run it directly from the terminal:
- on macOS/Linux
  ```shell
  ./gradlew :composeApp:run
  ```
- on Windows
  ```shell
  .\gradlew.bat :composeApp:run
  ```

---

## Execution Engine & Concurrency (PluginToolkit)

The internal job execution engine (`FlowEngine` and `JobWorker`) enforces strict concurrency policies to maintain stability:

- **Cooperative Cancellation**: Long-running synchronous system flows (e.g. infinite `while` loops) implement `yield()` at each step, ensuring that UI or user-requested cancellations tear down the coroutine instantly without leaking resources.
- **Resource Starvation Prevention**: All 3rd-party plugin invocations run in isolated `Dispatchers.IO` threads. If a plugin intentionally blocks the thread, it will not hijack the main worker dispatcher.
- **Recursion Depth Limits**: Deep subflow execution limits the stack frame depth to 50 iterations. Attempting to create an infinitely recursive subflow safely fails before hitting a JVM StackOverflow.
- **Configurable Capabilities Policies**: Transient network execution failures in plugins automatically back off and retry up to `maxRetries` (configurable in app settings). Executions are also bound by a strict `pluginTimeoutMs` to prevent hung plugins.

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…

## Standalone plugin JARs

JVM plugin modules can build a self-contained executable artifact by applying the bundled script:

```kotlin
apply(from = rootProject.file("scripts/standalone-plugin.gradle.kts"))
```

Run `./gradlew :yourPlugin:standaloneJar`, then inspect the plugin without the desktop host:

```shell
java -jar yourPlugin/build/libs/yourPlugin-version-standalone.jar --info
```

The generated JAR contains runtime dependencies, merges `META-INF/services` providers, and leaves KSP/compiler
dependencies in the separate `plugin-processor` build-time artifact.

External plugin builds need both artifacts at the same toolkit version:

```kotlin
dependencies {
    implementation("org.wip.plugintoolkit:plugin-api:<toolkit-version>")
    ksp("org.wip.plugintoolkit:plugin-processor:<toolkit-version>")
}
```
