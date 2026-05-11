# Plugin Generators Documentation

This document explains the architecture and responsibilities of the specialized generator classes used in the Plugin Toolkit's KSP (Kotlin Symbol Processing) layer.

## Architecture Overview

The code generation process is orchestrated by `KotlinGenerator`, which delegates specialized tasks to dedicated generator classes. This separation of concerns ensures that changes to the plugin manifest structure or dispatching logic don't destabilize the entire processor.

### 1. ManifestGenerator
**Responsibility**: Generates the static `PluginManifest` object.

- **Metadata Extraction**: Scans classes for `@Capability`, `@PluginSetting`, and `@PluginAction` annotations.
- **Type Mapping**: Converts Kotlin types to the internal `DataType` system.
- **Constraints**: Handles parameter constraints (min/max values, regex, etc.) defined in `@CapabilityParam`.
- **Infrastructure Filtering**: Automatically identifies and excludes infrastructure types (Logger, FileSystem, etc.) from the public manifest.

### 2. DispatcherGenerator
**Responsibility**: Generates the `DataProcessor` implementation (the "Bridge").

- **Request Routing**: Generates a map of handlers that route `PluginRequest` methods to the actual plugin implementation.
- **Parameter Injection**:
    - Decodes JSON parameters from the request.
    - Injects infrastructure types (e.g., `PluginLogger`) from the `PluginContext`.
    - Handles `@ResumeState` for pausable/cancellable jobs.
- **Result Handling**: Wraps execution results into `PluginResponse` and encodes them to JSON.
- **Async Execution**: Implements `processAsync` using Coroutines, managing `JobHandle` for cancellation and pausing.

### 3. EntryGenerator
**Responsibility**: Generates the `PluginEntry` class.

- **Koin Integration**: Generates the `getKoinModule()` function to register the plugin and its processor in the Dependency Injection container.
- **Lifecycle Management**: Maps `@PluginSetup`, `@PluginValidate`, `@PluginLoad`, and `@PluginUpdate` annotations to the corresponding `PluginEntry` lifecycle methods.
- **Entry Point**: Acts as the primary interface between the host application and the plugin logic.

### 4. ActionRegistryGenerator
**Responsibility**: Generates a constants registry for actions.

- **Type Safety**: Provides a static object (e.g., `MyPluginActions`) containing string constants for all defined `@PluginAction` methods.
- **Avoids Magic Strings**: Ensures that both the plugin and host can refer to actions using safe, compiled constants.

### 5. ProcessorConstants
**Responsibility**: Centralized dependency management for the generators.

- **ClassNames & MemberNames**: Defines all `com.squareup.kotlinpoet.ClassName` and `MemberName` references used across generators.
- **Infrastructure Types**: Maintains the source of truth for what constitutes an "Infrastructure" class (e.g., `PluginLogger`, `ProgressReporter`).

## Orchestration Flow

1. `ManifestProcessor` identifies classes annotated with `@PluginInfo`.
2. `KotlinGenerator.generate()` is called with the metadata.
3. Generators are invoked in sequence:
    - `ManifestGenerator` builds the manifest object.
    - `DispatcherGenerator` builds the request router.
    - `EntryGenerator` builds the main entry point.
    - `ActionRegistryGenerator` builds the action constants.
4. The final `FileSpec` is written to the generated source directory.
