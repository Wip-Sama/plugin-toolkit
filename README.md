# PluginToolkit

This is a modular Kotlin Multiplatform project targeting Desktop (JVM) and Headless CLI.

## Module Structure

### Shared Modules
- [**`:shared:core`**](./shared/core): Foundational pure Kotlin data models, IO utilities, system configurations, and coroutine dispatchers. Zero UI dependencies.
- [**`:shared:logic`**](./shared/logic): Headless core business domain logic including `PluginManager`, `JobManager`, `FlowEngine`, security, sandboxing, and repositories.
- [**`:shared:gui`**](./shared/gui): Compose Multiplatform UI components, themes (`ToolkitTheme`), screens, ViewModels, and localized Compose resources.

### Applications
- [**`:apps:desktopApp`**](./apps/desktopApp): Standalone Compose Multiplatform desktop runner with AWT/Swing splash screen, system tray support, and native packaging.
- [**`:apps:cliApp`**](./apps/cliApp): Standalone headless command-line interface powered by Clikt (`--capabilities`, `--plugins`, `--help`).

### Plugin API & Examples
- [**`:plugin-api`**](./plugin-api): Public API interfaces, models, and KSP processor contracts for developing 3rd-party plugins.
- [**`:plugins:minimalExample`**](./plugins/minimalExample): Minimal sample plugin demonstrating baseline metadata, lifecycle hooks, and simple capabilities.
- [**`:plugins:completeExample`**](./plugins/completeExample): Full-featured reference plugin showcasing all available Plugin API features (settings, workflows, custom capabilities, security sandboxing).

---

### Running the Desktop Application

To build and run the development version of the desktop application:
- **macOS / Linux:**
  ```shell
  ./gradlew :apps:desktopApp:run
  ```
- **Windows:**
  ```shell
  .\gradlew.bat :apps:desktopApp:run
  ```

### Running the Headless CLI

To run the command-line interface:
- **Help / Commands:**
  ```shell
  .\gradlew.bat :apps:cliApp:run --args="--help"
  ```
- **List Installed Plugins:**
  ```shell
  .\gradlew.bat :apps:cliApp:run --args="--plugins"
  ```
- **Query Plugin Capabilities:**
  ```shell
  .\gradlew.bat :apps:cliApp:run --args="--capabilities"
  ```

---

### Example Plugins

Example plugin implementations are located in the [`plugins/`](./plugins) directory:
- [**`plugins/minimalExample`**](./plugins/minimalExample): A minimal working plugin implementation.
- [**`plugins/completeExample`**](./plugins/completeExample): A complete reference plugin showcasing all capabilities and configurations.

To build a plugin JAR:
```shell
.\gradlew.bat :plugins:completeExample:jar
.\gradlew.bat :plugins:minimalExample:jar
```
The compiled plugin JARs will be generated in `plugins/<pluginName>/build/libs/`.

---

## Execution Engine & Concurrency (PluginToolkit)

The internal job execution engine (`FlowEngine` and `JobWorker`) enforces strict concurrency policies to maintain stability:

- **Cooperative Cancellation**: Long-running synchronous system flows implement `yield()` at each step, ensuring that UI or user-requested cancellations tear down coroutines instantly without leaking resources.
- **Resource Starvation Prevention**: All 3rd-party plugin invocations run in isolated `Dispatchers.IO` threads.
- **Recursion Depth Limits**: Deep subflow execution limits stack frame depth to prevent JVM StackOverflow.
- **Configurable Capabilities Policies**: Transient network execution failures in plugins automatically back off and retry up to `maxRetries` (configurable in app settings) with strict execution timeouts.