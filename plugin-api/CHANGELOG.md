# Changelog - Plugin API

All notable changes to the Plugin API will be documented in this file.

## 4.1.0-alpha03
- Added `TableNode` (`TableColumn`, `TableRow`), `FlowRowNode`, `KeyValueListNode` (`KeyValueEntry`), `MarkdownTextNode`, `MultiSelectDropdownNode`, and `RangeSliderNode` with `@JvmOverloads` constructors and `UiNodeSerializer` support.
- Added `UiEvent.TableSorted`, `UiEvent.RangeChanged`, and `UiEvent.MultiOptionSelected` with `UiEventSerializer` support.
- Added `UiContainerBuilder` DSL builders (`table`, `flowRow`, `keyValueList`, `markdownText`, `multiSelectDropdown`, `rangeSlider`).

## 4.1.0-alpha02
- Added `ProgressBarStyle` (`STANDARD`, `WAVY`, `MORPHING_INDICATOR`, `CONTAINED_INDICATOR`) on `ProgressBarNode` with `@JvmOverloads`.
- Added `LoadingIndicatorNode` and `UiContainerBuilder.loadingIndicator(...)` DSL builder.

## 4.1.0-alpha01
- Added `ShapeRole` and universal `UiModifier` properties (`tooltip`, `contextMenu`, `cornerRadius`) with `@JvmOverloads` and fluent helpers.
- Extended `TextNode` (`selectable`, `maxLines`, `overflowEllipsis`) and `ImageNode` (`zoomable`) with backward-compatible defaults.
- Added `RadioGroupNode` (`RadioOptionItem`), `NumberInputNode`, `SearchBarNode`, `AccordionNode`, and `CodeBlockNode` alongside corresponding DSL builders in `UiContainerBuilder`.

- Initial Maven publication to GitHub Packages.
- Standardized plugin manifest models and loading logic.
- KSP processor for automatic plugin manifest generation.
- Support for `@PluginLoad`, `@PluginSetup`, and `@PluginUpdate` lifecycle hooks.
- Required Action mechanism for plugins needing user intervention.
- Enhanced Changelog parsing support.
- Added CommonSemanticTypes registry for standardized SemanticType definitions.
- Added `@JvmOverloads` across manifest and API data classes (`PluginAction`, `Capability`, `ParameterMetadata`, `SettingMetadata`, `PluginManifest`, etc.) to preserve JVM constructor signatures for precompiled plugins.
- Enhanced generated `PluginEntry.getManifest()` with self-healing fallback to `ManifestLoader.loadFromResources()`.
- Added `ProcessWatcher` interface and `PluginContext.watchProcess(...)` API to monitor memory usage of external child processes (e.g. terminal commands, Python scripts).
- Added `CapabilityContext` support (`ANY`, `FLOW_ONLY`, `STANDALONE_ONLY`) on `@Capability` to scope execution to flows or direct standalone runner.
- Added multi-select dropdown support (`multiSelect`, `minChoices`, `maxChoices`) on `@CapabilityParam` and `@PluginSetting`, with automatic detection for Enum collections (`Collection<Enum>`, `Set<Enum>`, `List<Enum>`, `Iterable<Enum>`).
- Generalized collection support across parameter parsing and validation to accept `Collection<T>`, `Set<T>`, and `Iterable<T>` alongside `List<T>`.
- Added `ProgressData` and `ProgressDisplayMode` supporting percentage, value ratios (e.g. 12.3 / 14.5 MB), indeterminate modes, and dynamic detail strings (download speed, remaining time).
- Expanded `ProgressReporter` with `reportSecondary(...)`, `reportSubProgress(...)`, and ratio/detail overloads with backward-compatible defaults.
- Added `PluginNetworkClient` interface, `PluginContext.networkClient`, and `PluginContext.recordNetworkUsage(...)` for internet throughput and byte tracking.
