---
name: plugin-development
description: >
  Develop, implement, test, and migrate plugins in the Plugin Toolkit ecosystem.
  Use when: creating a new plugin, adding or modifying capabilities (@Capability), configuring
  settings (@PluginSetting), parameter validation, semantic types, migrations (migrations.json),
  lifecycle methods (@PluginSetup, @PluginUpdate, pause/resume), packaging, or standalone testing.
user-invokable: true
argument-hint: "[create|capability|settings|semantic-types|migration|lifecycle|standalone]"
---

# Plugin Toolkit Development Guide

This skill provides an actionable runbook and technical reference for creating, modifying, testing, and maintaining plugins in the Plugin Toolkit ecosystem.

---

## 1. Quick Decision Tree

**What are you building or modifying?**

```text
Creating a new plugin              → See § 2. Workflow: Scaffolding a New Plugin + references/plugin-core.md
Adding/editing capabilities        → See § 3. Workflow: Capabilities & Parameters + references/plugin-core.md
Annotating port types              → See § 4. Workflow: Semantic Types + references/semantic-types.md
Handling long tasks / pause        → See § 5. Workflow: Lifecycle & State + references/lifecycle-and-state.md
Version bumps / API migrations     → See § 6. Workflow: Version Migrations + references/migrations.md
Local testing & standalone run     → See § 7. Workflow: Testing & Standalone Mode + references/repositories-and-packaging.md
Signing, packaging, repositories   → See references/repositories-and-packaging.md
```

---

## 2. Workflow: Scaffolding a New Plugin

1. **Gradle Module Configuration**:
   - Depend on `:plugin-api`.
   - Apply the KSP plugin to enable metadata and manifest generation.
2. **Settings Model**:
   - Define an immutable `data class` for configuration.
   - Annotate configuration properties with `@PluginSetting`.
   - Use constraints (`required`, `regex`, `minValue`, `maxValue`, `secret`).
3. **Main Entrypoint Class**:
   - Annotate the class with `@PluginInfo(id = "...", name = "...", version = "...")`.
   - Inject the settings `data class` into the constructor (KSP generates Koin bindings).
4. **Changelog**:
   - Create `changelog.md` at the module root or in `src/main/resources/changelog.md` with standard version headers and 100-hyphen delimiters.

```kotlin
@PluginInfo(
    id = "sample-plugin",
    name = "Sample Plugin",
    version = "1.0.0",
    description = "Demonstrates standard plugin architecture."
)
class SamplePlugin(val settings: SampleSettings) : DataProcessor

data class SampleSettings(
    @PluginSetting(description = "API Key", secret = true, required = true)
    val apiKey: String,
    @PluginSetting(description = "Worker Threads", minValue = 1.0, maxValue = 16.0, defaultValue = 4.0)
    val workerThreads: Int = 4
)
```

---

## 3. Workflow: Capabilities & Parameters

1. **Annotate Capability Functions**:
   - Mark task functions with `@Capability(name = "...", description = "...", context = CapabilityContext.ANY)`.
   - Use `context = CapabilityContext.FLOW_ONLY` for capabilities meant strictly as nodes in flows (hidden from standalone runner).
   - Mark functions with `suspend` for I/O, heavy computation, or cancellation support.
2. **Select Parameter Annotations**:
   - Standard inputs (primitives, enums, collections, multi-select enum sets): `@CapabilityParam(description = "...", defaultValue = "...")`.
   - Multi-select enum collections (`Collection<MyEnum>`, `Set<MyEnum>`): rendered as multi-select dropdown menus, constrained with `minChoices` and `maxChoices`.
   - File/directory inputs (reads): `@CapabilityInput` (automatically infers `readsFiles = true`).
   - File/directory outputs (writes): `@CapabilityOutput` (automatically infers `writesFiles = true`).
3. **Advanced & Dynamic Parameter Rules**:
   - `isAdvanced = true`: Collapses parameter into the "Advanced Options" drawer.
   - `@DependsOn(param = "...", value = "...")`: Dynamically shows/hides parameter based on another parameter, setting (`setting = "..."`), or lock (`lock = "..."`).
   - Use `@DependsOnAny` for logical OR conditions.
4. **Group Parameters with `@ParameterGroup`**:
   - For 5+ related parameters, group them into a `@Serializable data class`.
   - Use `prefix = "..."` to prevent collisions.
5. **Output Ports**:
   - Single return: annotate function with `@CapabilityResult(name = "...", semanticTypes = [...])`.
   - Multiple returns: return a `@Serializable data class` with `@CapabilityResult` on each property.

---

## 4. Workflow: Semantic Types

Semantic types define data contracts beyond binary Kotlin types (e.g. distinguishing a hex color string from a file path).

1. **Grammar**:
   `[namespace/][name][:variant]` (lowercase, NFKC normalized).
   - Standard delimiter `/` separates namespace and name.
   - Delimiter `:` separates name and variant.
   - Examples: `color:hex`, `file:path`, `image/png`, `video:mp4`.
2. **Port Annotation**:
   ```kotlin
   @CapabilityParam(description = "Color", semanticType = "color:hex")
   // Or multiple semantic types on capability outputs:
   @CapabilityResult(name = "result", semanticTypes = ["image/png", "file:path"])
   ```
3. **Compatibility Matching**:
   - If either port has an empty list, connection is universally compatible.
   - Connections succeed if **at least one** source semantic type matches **at least one** target type.
   - Generalization matches specialization (e.g. `color:rgb` satisfies target `color`).
   - Wildcards match variants (e.g. `image/png` satisfies `image/*`).

---

## 5. Workflow: Lifecycle & State

1. **One-Time Setup (`@PluginSetup`)**:
   - Use for resource extraction (`context.fileSystem.extractResource`).
   - **Caution**: Only runs on first installation or update fallback. If used on update, the toolkit **clears the `files` directory**.
2. **Version Update (`@PluginUpdate`)**:
   - Implement to safely migrate internal files or database schemas without losing data.
   - Must be **version-agnostic** (users may skip intermediate versions).
3. **Runtime Load Check (`@PluginLoad`) & Validation (`@PluginValidate`)**:
   - Inspect environment, native dependencies, or valid credentials before execution.
4. **Pause and Resume**:
   - Set `@Capability(supportsPause = true)`.
   - Add parameter `@ResumeState resumeState: JsonElement?`.
   - Listen to signals: `context.signals.onSignal { if (it == PluginSignal.PAUSE) isPaused = true }`.
   - Return `ExecutionResult.Paused(stateJson)` to pause cleanly.
5. **External Processes & Memory Monitoring**:
   - When spawning external CLIs or runtimes (Python, ffmpeg), use `context.watchProcess(process)`.
   - Wrap in `use { watcher -> ... }` to automatically record peak memory and unregister upon completion.

---

## 6. Workflow: Version Migrations

When releasing new versions that alter capabilities, settings, or port signatures:

1. **Flow Migrations (`migrations.json`)**:
   - Place `migrations.json` in the root of the plugin module.
   - Define version-to-version step mapping (`fromVersion`, `toVersion`).
   - Map renamed capabilities, settings, or complex object properties.
   - Set `"isDropInReplacement": true` for seamless auto-migrations; set `"newName": null` for intentional breaking changes (renders node as broken).
2. **Internal State Migrations (`performUpdate`)**:
   - Defensively query internal files (`fs.exists("v1.json")`) rather than hardcoding assumed previous versions.

---

## 7. Workflow: Testing & Standalone Mode

1. **Unit Testing**:
   - Instantiate processor classes directly with mock settings `data class` instances.
   - Execute test suites with `./gradlew test` (or `./gradlew :plugins:<plugin-name>:test`).
2. **Interactive Standalone GUI Testing**:
   - Run the plugin directly using the standalone runner without launching the full multi-plugin host application:
     ```bash
     ./gradlew :apps:standaloneRunner:run -PtargetPlugin=:plugins:<plugin-name>
     ```
   - Test settings dialogs, capability execution, file read/write permissions, and setup handlers interactively.

---

## 8. Mandatory Quality & Project Rules

Whenever making changes to plugins or `:plugin-api`:

- [ ] **Reference Plugins Alignment**: When modifying or adding `:plugin-api` features, update both `completeExample` and `minimalExample` plugins. `completeExample` must showcase all available API features.
- [ ] **UI Strings & Theming**: In GUI code, use `stringResource()` for UI strings and `ToolkitTheme` properties for styling. No hardcoded `.dp`, magic colors, or raw spacing.
- [ ] **ABI Compatibility**: When modifying API models, assign default values and use `@kotlin.jvm.JvmOverloads constructor(...)` to prevent breaking existing pre-compiled plugin JARs.
- [ ] **Automated Tests**: Execute unit tests via terminal and ensure all tests pass (exit code 0).

---

## 9. Critical Anti-Patterns & Pitfalls

| Anti-Pattern | Why it fails | Correct Solution |
| :--- | :--- | :--- |
| **Missing `@Serializable` on `@ParameterGroup`** | KSP cannot generate the deserialization dispatcher. | Always annotate `@ParameterGroup` data classes with `@Serializable`. |
| **Using `@PluginSetup` for migration updates** | Automatically wipes the plugin's `files` storage directory on upgrade. | Use `@PluginUpdate` to migrate files and preserve user data. |
| **Assuming single-step version upgrades** | Users frequently upgrade across multiple releases (e.g. 1.0.0 to 1.4.0). | Make `performUpdate()` version-agnostic with defensive existence checks. |
| **Throwing `PluginPausedException`** | Deprecated mechanism that disrupts structured concurrency. | Return `ExecutionResult.Paused(state)` from the capability. |
| **Hardcoded file paths outside sandboxes** | Breaches plugin isolation and causes permission denials. | Always use `context.fileSystem`, `ExecutionFileSystem`, or user-granted `@CapabilityInput`/`@CapabilityOutput` paths. |
| **Circular `@DependsOn` dependencies** | Causes infinite dependency loops and KSP compiler errors. | Ensure conditions form a strict Directed Acyclic Graph (DAG). |

---

## 10. Reference Documentation Index

For exhaustive technical schemas, code samples, and specifications:
- [`references/plugin-core.md`](./references/plugin-core.md) — Comprehensive guide to annotations, validation constraints, `@ParameterGroup`, and dynamic conditions.
- [`references/semantic-types.md`](./references/semantic-types.md) — Full grammar specification, standard visual registry, and `isSemanticTypeCompatible` rules.
- [`references/migrations.md`](./references/migrations.md) — `migrations.json` schema, step resolution, and state upgrades.
- [`references/lifecycle-and-state.md`](./references/lifecycle-and-state.md) — Lifecycle handlers, pause/resume, sandboxed filesystems, process monitoring.
- [`references/repositories-and-packaging.md`](./references/repositories-and-packaging.md) — Standalone runner, dual build mode, RSA signing, repository `index.json`.
