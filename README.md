# PluginToolkit

This is a modular Kotlin Multiplatform project targeting Desktop (JVM) and Headless CLI.

## Module Structure

- [**`:shared:core`**](./shared/core): Foundational pure Kotlin data models, IO utilities, system configurations, and coroutine dispatchers. Zero UI dependencies.
- [**`:shared:logic`**](./shared/logic): Headless core business domain logic including `PluginManager`, `JobManager`, `FlowEngine`, security, sandboxing, and repositories.
- [**`:shared:gui`**](./shared/gui): Compose Multiplatform UI components, themes (`ToolkitTheme`), screens, ViewModels, and localized Compose resources.
- [**`:desktopApp`**](./desktopApp): Standalone desktop runner with AWT/Swing splash screen, system tray support, and native packaging.
- [**`:cliApp`**](./cliApp): Standalone headless command-line interface powered by Clikt (`--capabilities`, `--plugins`, `--help`).
- [**`:plugin-api`**](./plugin-api): Public API interfaces and contracts for developing 3rd-party plugins.

---

### Running the Desktop Application

To build and run the development version of the desktop application:
- **macOS / Linux:**
  ```shell
  ./gradlew :desktopApp:run
  ```
- **Windows:**
  ```shell
  .\gradlew.bat :desktopApp:run
  ```

### Running the Headless CLI

To run the command-line interface:
- **Help / Commands:**
  ```shell
  .\gradlew.bat :cliApp:run --args="--help"
  ```
- **List Installed Plugins:**
  ```shell
  .\gradlew.bat :cliApp:run --args="--plugins"
  ```
- **Query Plugin Capabilities:**
  ```shell
  .\gradlew.bat :cliApp:run --args="--capabilities"
  ```

---

## Execution Engine & Concurrency (PluginToolkit)

The internal job execution engine (`FlowEngine` and `JobWorker`) enforces strict concurrency policies to maintain stability:

- **Cooperative Cancellation**: Long-running synchronous system flows implement `yield()` at each step, ensuring that UI or user-requested cancellations tear down coroutines instantly without leaking resources.
- **Resource Starvation Prevention**: All 3rd-party plugin invocations run in isolated `Dispatchers.IO` threads.
- **Recursion Depth Limits**: Deep subflow execution limits stack frame depth to prevent JVM StackOverflow.
- **Configurable Capabilities Policies**: Transient network execution failures in plugins automatically back off and retry up to `maxRetries` (configurable in app settings) with strict execution timeouts.