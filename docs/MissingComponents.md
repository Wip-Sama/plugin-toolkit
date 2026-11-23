# Missing Components Summary

This document summarizes the UI components, Material 3 Expressive primitives, and Server-Driven UI (`UiNode`) capabilities that are currently **missing** across the project, covering both:

1. **Plugin SDUI (`plugin-api` + `shared/gui` renderer)** — declarative `UiNode` components, modifiers, and events available to `@InteractiveTool` plugins.
2. **Host Application (`shared/gui`)** — shared `ToolkitTheme` / Material 3 Expressive / Compose Desktop components and adaptive scaffolds.

---

## Current Baseline (Verified in Codebase — App `4.1.0-alpha02`)

- **Compose / Material 3 Versions** (`gradle/libs.versions.toml`): `compose-multiplatform = "1.12.0"`, `material3 = "1.12.0-alpha03"`.
- **SDUI `UiNode` Catalog** (`plugin-api/.../sdui/UiNode.kt`): 42 `UiNode` types (40 author-facing + `UnknownNode` and `ErrorBoundaryNode`):
  - **Layout & Shell:** `ColumnNode`, `RowNode`, `BoxNode`, `CardNode`, `ScrollableColumnNode`, `ScrollableRowNode`, `GridNode`, `SplitPaneNode`, `ScaffoldNode`, `SidebarNode`, `FloatingPanelNode`, `TabsNode`, `AccordionNode`, `DialogNode`, `SpacerNode`, `DividerNode`.
  - **Controls & Inputs:** `ButtonNode`, `ButtonGroupNode`, `SplitButtonNode`, `IconButtonNode`, `TextInputNode` (with `isSecret`), `NumberInputNode`, `SearchBarNode`, `RadioGroupNode`, `ToggleNode`, `CheckboxNode`, `DropdownNode`, `SliderNode`, `ChipGroupNode`, `ListItemNode`.
  - **Display, Media & Canvas:** `TextNode` (`selectable`, `maxLines`, `overflowEllipsis`), `CodeBlockNode`, `ImageNode` (`zoomable`), `IconNode`, `BadgeNode`, `ProgressBarNode` (`ProgressBarStyle`), `LoadingIndicatorNode`, `AlertBannerNode`, `EmptyStateNode`, `BoardNode`.
  - **Universal Modifiers (`UiModifier`):** `ShapeRole` (`cornerRadius`), `tooltip`, `contextMenu` (`ActionMenuItem`).
- **Host Theme & Shared Components** (`shared/gui/...`): `MaterialExpressiveTheme`, `MotionScheme.expressive()` + `StandardMotionScope`, 8-step `Shapes`, dual Baseline/`*Emphasized` `DesktopTypography`, `NormalizedMorphShape`, `ToolkitSplitButton` (`SplitButtonLayout`), `ToolkitConnectedToggleGroup`, `ToolkitLoadingIndicator`, `ToolkitLinearProgressIndicator`, `ToolkitCircularProgressIndicator`, `ToolkitButtonGroup`, `ToolkitCard`, `ToolkitChip`, `ToolkitTextField`, `ToolkitDropdownMenu`, `ExpressiveMenu`, `UnscaledExpressiveMenu`, `SelectedButtonGroup`, `SectionHeader`, `ToastHost`, `Tooltip`, `ZoomControls`, `NavigationSidebar` / `SidebarContainer`, `PlatformVerticalScrollbar` / `PlatformHorizontalScrollbar`, `PlatformContextMenuArea`, and parameter input widgets (`ColorInput`, `EnumDropdownInput`, `FileInput`, `PrimitiveInputs`).

---

## Part 1 — Missing Plugin SDUI Components (`plugin-api` + `DynamicUiRenderer`)

### 1.1 Form & Input Controls

| Missing Component | Proposed `UiNode` | What Is Missing Today | Key Capabilities Needed |
| :--- | :--- | :--- | :--- |
| **Radio Group** *(Implemented — Batch 1)* | `RadioGroupNode` | Implemented (`RadioOptionItem`, vertical/horizontal layout, subtitle, `UiEvent.OptionSelected`). | Vertical/horizontal layout, option title + subtitle/description, `UiEvent.OptionSelected`. |
| **Numeric / Stepper Input** *(Implemented — Batch 1)* | `NumberInputNode` | Implemented (`min`, `max`, `step`, `isDecimal`, `unitSuffix`, +/- steppers, Up/Down keys, `UiEvent.ValueChanged`). | `value`, `min`, `max`, `step`, `isDecimal`, `unitSuffix`, `+/-` steppers, arrow-key stepping, `UiEvent.ValueChanged`. |
| **Range Slider** | `RangeSliderNode` | `SliderNode` only supports a single `Double` value. | `startValue`, `endValue`, `min`, `max`, `steps`, dual-thumb dragging, `UiEvent.RangeChanged`. |
| **Search Bar / Autocomplete** *(Implemented — Batch 1)* | `SearchBarNode` | Implemented (`query`, `suggestions` menu, clear button, Enter-to-submit `UiEvent.Click`, `UiEvent.TextChanged`). | `query`, `suggestions`, `placeholder`, `showClearButton`, Enter-to-submit action + `UiEvent.TextChanged`. |
| **Multi-Select Dropdown / ComboBox** | `MultiSelectDropdownNode` | `DropdownNode` supports only a single `selectedKey: String`. Host parameter inputs have multi-select `EnumDropdownInput`, but SDUI does not. | `selectedKeys: Set<String>`, searchable filter, select-all/clear, chip summary. |
| **Inline File / Directory Picker Control** | `FilePickerNode` | `UiSessionContext` exposes `pickFile` / `pickFiles` / `pickDirectory` / `saveFile`, but plugins must manually wire a `ButtonNode` + `TextInputNode` pair. | Mode (`FILE`, `FILES`, `DIRECTORY`, `SAVE`), extension filter, selected path preview, browse/clear actions. |
| **Color Picker Control** | `ColorPickerNode` | Host has `features/colorpicker` and `ColorInput`, but SDUI only exposes `SemanticColor` enums with no user color-selection widget. | Hex/ARGB string value, optional alpha slider, swatch presets, `UiEvent.TextChanged` or `ColorChanged`. |
| **Date / Time Picker** | `DatePickerNode` / `TimePickerNode` | No date, date-range, or time input controls for scheduling, filtering, or metadata editing. | ISO-8601 date/time string or epoch millis, min/max bounds, `24h` toggle. |

### 1.2 Data Display, Code & Structured Productivity

| Missing Component | Proposed `UiNode` | What Is Missing Today | Key Capabilities Needed |
| :--- | :--- | :--- | :--- |
| **Data Table** | `TableNode` | Lists must be faked with `RowNode` + `ColumnNode`, lacking synchronized column widths, sorting, or sticky headers. | `columns` (title, width/weight, sortable), `rows`, `sortColumnKey`, `sortAscending`, row selection, sticky header, vertical + horizontal scrollbars. |
| **Hierarchical Tree View** | `TreeViewNode` | No collapsible parent/child tree component for file systems, JSON ASTs, layers, or nested categories. | `TreeNodeItem` (`key`, `label`, `iconName`, `children`, `expanded`), lazy-load / expand toggle event, selection. |
| **Accordion / Expandable Section** *(Implemented — Batch 1)* | `AccordionNode` | Implemented (`title`, `subtitle`, `expanded`, `leadingIcon`, `badgeText`/`badgeColor`, spring expand/collapse, `UiEvent.ToggleChanged`). | `title`, `subtitle`, `expanded`, `leadingIcon`, `trailingBadge`, collapsible children, `UiEvent.ToggleChanged` or `Click`. |
| **Code / Log Block** *(Implemented — Batch 1)* | `CodeBlockNode` | Implemented (`ToolkitTheme.codeMedium`, `language` pill, line numbers, wrap toggle, copy button, `SelectionContainer`, scrollbars). | Uses `ToolkitTheme.codeMedium`, optional `language`, `showLineNumbers`, `wrapLines`, copy-to-clipboard button, `SelectionContainer`. |
| **Markdown / Rich Text** | `MarkdownTextNode` | `TextNode` only renders a single unformatted string and `TypographyRole`; no headings, bold/italic, inline code, lists, or clickable links. | Safe subset of Markdown (headings, emphasis, code, lists, links emitting `UiEvent.Click` or opening host-approved URLs). |
| **Key-Value / Property List** | `KeyValueListNode` | Inspectors and job summaries repeat `RowNode(Text, Spacer, Text)` manually. | Aligned key/value rows, monospace toggle, per-row copy action, optional semantic badge. |
| **Wrapping Flow Layout** | `FlowRowNode` | `RowNode` does not wrap overflowing items onto new lines; only `ChipGroupNode` wraps internally via `FlowRow`. | Arbitrary `List<UiNode>` children with horizontal & vertical `SpacingSize`. |

### 1.3 Navigation, Wayfinding & Overlays

| Missing Component | Proposed `UiNode` | What Is Missing Today | Key Capabilities Needed |
| :--- | :--- | :--- | :--- |
| **Breadcrumbs** | `BreadcrumbsNode` | No path trail component for nested folder/repository/category navigation. | `items` (`key`, `label`, `iconName`), separator, overflow truncation, click event per segment. |
| **Pagination Bar** | `PaginationNode` | Large datasets require plugins to hand-build page buttons with `RowNode` + `ButtonNode`. | `currentPage`, `totalPages`, optional `pageSize` selector, prev/next/first/last & page jump events. |
| **Stepper / Wizard Progress** | `StepperNode` | Multi-step interactive workflows have no visual step progress indicator. | `steps` (`key`, `label`, `status`: pending/active/completed/error), clickable completed steps. |
| **Side Sheet / Modal Drawer** | `SideSheetNode` | `SidebarNode` only attaches statically to `ScaffoldNode` edges; no sliding overlay/inspector drawer. | `isOpen`, `title`, `attachPoint` (`START`/`END`), dismiss event, max width `<= 400.dp`. |
| **Standalone Popup / Context Menu** | `MenuNode` / `UiModifier.contextMenu` | `UiModifier.contextMenu` (`List<ActionMenuItem>` via `ContextMenuArea`) implemented in Batch 1; standalone `MenuNode` overflow button still missing (Batch 3). | `List<ActionMenuItem>` attached via `UiModifier.contextMenu` (desktop right-click) or standalone overflow menu button. |
| **Rich / Persistent Tooltip** *(Implemented — Batch 1)* | `UiModifier.tooltip` / `RichTooltip` | Universal `UiModifier.tooltip: String?` implemented across SDUI nodes (Batch 1). | Generic `tooltip: String?` (and optional title + body) on `UiModifier`. |

### 1.4 Material 3 Expressive & Media Nodes for SDUI

| Missing Component | Proposed `UiNode` | What Is Missing Today | Key Capabilities Needed |
| :--- | :--- | :--- | :--- |
| **Floating Toolbar** | `FloatingToolbarNode` | Canvas/editor tools must currently use `FloatingPanelNode` + `RowNode` instead of M3 Expressive `HorizontalFloatingToolbar` / `VerticalFloatingToolbar`. | `orientation` (horizontal/vertical), `vibrant: Boolean`, `expanded: Boolean`, optional attached FAB action. |
| **Floating Action Button & FAB Menu** | `FabNode` / `FabMenuNode` | No FAB or `FloatingActionButtonMenu` node for primary workspace creation + 3–6 sub-actions. | Standard/small/extended FAB, or `ToggleFloatingActionButton` expanding `ActionMenuItem`s. |
| **Expressive Loading & Wavy Progress** *(Implemented — Batch 2)* | `LoadingIndicatorNode` / `ProgressBarNode.style` | Implemented (`ProgressBarStyle.STANDARD`/`WAVY`/`MORPHING_INDICATOR`/`CONTAINED_INDICATOR` and standalone `LoadingIndicatorNode`). | `style` (`STANDARD`, `WAVY`, `MORPHING_INDICATOR`, `CONTAINED_INDICATOR`). |
| **Image Comparison / Diff Slider** | `ImageCompareNode` | Image-processing plugins (upscaling, translation, cleaning) have no before/after split-handle viewer. | `beforeAsset`, `afterAsset`, `splitPosition`, synchronized zoom/pan. |
| **Audio / Video Preview** | `MediaPlayerNode` | Only static `ImageNode` is supported; no audio/video playback node for multimedia plugins. | `asset: AssetReference`, play/pause, scrub bar, duration display. |

### 1.5 Missing SDUI Modifiers & `UiNode` Properties

- [x] **`UiModifier.tooltip: String?`** *(Batch 1)* — universal hover tooltip support on any node.
- [x] **`UiModifier.contextMenu: List<ActionMenuItem>`** *(Batch 1)* — right-click `ContextMenuArea` support on list rows, cards, grid tiles, and images.
- [x] **`UiModifier.cornerRadius` / `ShapeRole`** *(Batch 1)* — semantic corner tokens (`NONE`, `EXTRA_SMALL`, `SMALL`, `MEDIUM`, `LARGE`, `EXTRA_LARGE`, `PILL`).
- [x] **`TextNode.selectable: Boolean`** *(Batch 1)* — opt-in `SelectionContainer` wrapper for copyable text/IDs/paths in desktop workflows.
- [x] **`TextNode.maxLines` & `overflowEllipsis`** *(Batch 1)* — ellipsis/truncation control inside dense cards and table-like rows.
- [x] **`ImageNode` zoom/lightbox mode (`zoomable: Boolean`)** *(Batch 1)* — built-in click-to-zoom preview dialog without needing a full `BoardNode`.
- [ ] **Scroll control / anchoring** — ability to request scrolling a `ScrollableColumnNode` or `GridNode` to a specific child index or top/bottom (useful for logs and chat-style tools).

---

## Part 2 — Missing Host App & Material 3 Expressive / Desktop Components (`shared/gui`)

Auditing `shared/gui` against the project's `m3-*` skill suite (`m3-desktop-expressive-overview`, `m3-expressive-components-checklist`, `m3-desktop-layout-adaptive`, `m3-desktop-ergonomics-density`, `m3-expressive-theming-shape`, `m3-expressive-motion-physics`) shows that **none** of the Material 3 `1.5.0` / Compose Multiplatform Expressive & Adaptive APIs are currently wired in `shared/gui`:

### 2.1 Theme, Shape & Motion Infrastructure (`core/theme/Theme.kt`)

| Missing / Legacy Gap | Target API / Token | Current State in `shared/gui` |
| :--- | :--- | :--- |
| **`MaterialExpressiveTheme` wrapper** *(Implemented — Batch 2)* | `MaterialExpressiveTheme(colorScheme, motionScheme, shapes, typography)` | `AppTheme` calls `MaterialExpressiveTheme` with `MotionScheme.expressive()` + `StandardMotionScope`. |
| **`MotionScheme` physics engine** *(Implemented — Batch 2)* | `MotionScheme.expressive()` / `MotionScheme.standard()`, `*SpatialSpec()`, `*EffectsSpec()` | Spring motion wired into `AppScaffold`, `NavigationSidebar`, `SidebarItem`, `ToolkitCard`, `SettingsItem`, `NodeInputSection`, `NodeOutputSection`, `JobResultCard`, `FlowEditorView`, and SDUI controls. |
| **8-step Expressive `Shapes` scale** *(Implemented — Batch 2)* | `Shapes` with `largeIncreased`, `extraLargeIncreased`, `extraExtraLarge` | `ToolkitShapes` defines and passes all 8 corner steps (`4.dp`–`48.dp`) into `MaterialTheme.shapes`. |
| **Dual Typography scale** *(Implemented — Batch 2)* | Baseline + Emphasized typography styles (`*Emphasized`) | `DesktopTypography` defines all 30 baseline and emphasized `TextStyle`s. |
| **`MaterialShapes` morphing** *(Implemented — Batch 2)* | 35 `RoundedPolygon` shapes & `Morph` transitions | `NormalizedMorphShape` and `rememberMorphShape` implemented in `core/theme/NormalizedMorphShape.kt`. |

### 2.2 Adaptive Layout & Navigation Scaffolds

| Missing Component | Target M3 / Adaptive API | Current State in `shared/gui` |
| :--- | :--- | :--- |
| **Adaptive Root Navigation** | `NavigationSuiteScaffold` + `WindowSizeClass` (`currentWindowAdaptiveInfo()`) | Uses custom `NavigationSidebar` / `SidebarContainer` instead of `NavigationSuiteScaffold` driven by `WindowSizeClass` breakpoints. |
| **Expandable M3 Expressive Rail** | `WideNavigationRail` + `WideNavigationRailItem` + `rememberWideNavigationRailState` | Custom `NavigationSidebar` implementation in `shared/components/sidebar/`. |
| **Canonical Multi-Pane Scaffolds** | `NavigableListDetailPaneScaffold` / `NavigableSupportingPaneScaffold` + `VerticalDragHandle` + `rememberPaneExpansionState` | Multi-pane views are built manually per feature instead of adaptive `ThreePaneScaffold`s with 24dp partition spacers. |
| **Overflow-Aware Pane Toolbars** | `AppBarRow` / `AppBarColumn` + `AppBarOverflowIndicator` | Action rows do not automatically collapse into overflow menus on narrow desktop windows. |
| **Flexible Pane App Bars** | `TopAppBar(subtitle = ...)` / `MediumFlexibleTopAppBar` | Missing M3 Expressive pane-scoped app bars with subtitle & scroll behavior. |

### 2.3 Missing Material 3 Expressive Controls in `shared/components`

| Missing Component | Target M3 Expressive API | Current State in `shared/gui` |
| :--- | :--- | :--- |
| **Floating Canvas / Editor Toolbars** | `HorizontalFloatingToolbar` & `VerticalFloatingToolbar` (+ `VibrantFloatingActionButton`) | Not present in `shared/gui` (useful for Flow Editor, BoardNode, and preview panes). |
| **Native M3 Split Button** *(Implemented — Batch 2)* | `SplitButtonLayout` (`LeadingButton` / `TrailingButton`) | Added `ToolkitSplitButton` using `SplitButtonLayout`, `SplitButtonDefaults`, spring chevron rotation, and `ToolkitDropdownMenu`. |
| **FAB & Expandable FAB Menu** | `FloatingActionButtonMenu`, `ToggleFloatingActionButton`, `SmallExtendedFloatingActionButton` | Not present in `shared/components`. |
| **Morphing Loading Indicator** *(Implemented — Batch 2)* | `LoadingIndicator` & `ContainedLoadingIndicator` | Added `ToolkitLoadingIndicator` + SDUI `LoadingIndicatorNode` (`CheckForUpdatesControl`, `DynamicUiRenderer`). |
| **Wavy Progress Indicators** *(Implemented — Batch 2)* | `LinearWavyProgressIndicator` & `CircularWavyProgressIndicator` | Added `ToolkitLinearProgressIndicator` / `ToolkitCircularProgressIndicator` + SDUI `ProgressBarStyle.WAVY` (`JobExecutionInfoSection`, `JobResultCardComponents`). |
| **Overflow-Capable ButtonGroup & Connected `ToggleButton`** *(Implemented — Batch 2)* | `ButtonGroup` (`overflowIndicator`) & `ToggleButton` (`ButtonGroupDefaults.connected*ButtonShapes()`) | Added `ToolkitConnectedToggleGroup` with M3 `ToggleButton` and `ButtonGroupDefaults.connected*ButtonShapes()`; `ButtonGroup` overflow remains for Batch 5/6. |

### 2.4 Missing Reusable Desktop Components in `shared/components`

| Missing Component | Purpose in Host App |
| :--- | :--- |
| **`ToolkitDataTable`** | Sortable, resizable, keyboard-navigable multi-column table with `VerticalScrollbar` + `HorizontalScrollbar` (for Jobs, Logs, Plugin Repositories, Capabilities). |
| **`ToolkitTreeView`** | Reusable collapsible tree with keyboard arrow navigation and context menus (for Flow nodes/files, Plugin file sandboxes). |
| **`ToolkitSearchBar`** | Standardized search field with debounce, clear icon, result count badge, and `Ctrl+F` focus binding across views. |
| **`ToolkitAccordion` / `ExpandableSection`** | Consistent collapsible header + animated body shared across Settings, Inspector drawers, and Job cards. |
| **`ToolkitConfirmDialog` / `ToolkitSideSheet`** | Reusable M3-styled confirmation dialog and `<= 400.dp` co-planar/modal side sheet instead of ad-hoc `AlertDialog`s per feature. |
| **`ToolkitStatusBadge` & `ToolkitEmptyState`** | Currently `BadgeNode` and `EmptyStateNode` are implemented inside SDUI (`DynamicUiRenderer.kt`), while host feature screens lack a single shared `Toolkit*` design-system component. |
| **Unified `ContextMenuArea` & `TooltipBox` Helpers** | `ContextMenuArea` is not yet standardized across `shared/components` (lists, cards, tables, static `SelectionContainer` text), and `Tooltip.kt` still carries deprecated `TooltipArea` / `Modifier.tooltip(String)` alongside M3 `TooltipBox`. |

---

## Part 3 — Recommended Priority Order

1. **High Priority (Immediate Productivity & M3 Alignment)**
   - **SDUI:** `TableNode`, `AccordionNode`, `RadioGroupNode`, `SearchBarNode`, `NumberInputNode`, `CodeBlockNode`, `UiModifier.tooltip`, and `UiModifier.contextMenu`.
   - **Host:** Upgrade `AppTheme` to `MaterialExpressiveTheme` (`MotionScheme` + 8-step `Shapes`), add `ToolkitSplitButton` / `SplitButtonLayout`, `LoadingIndicator` / `WavyProgressIndicator`, `HorizontalFloatingToolbar` (for Flow Editor & `BoardNode`), and shared `ToolkitSearchBar` / `ToolkitDataTable`.
2. **Medium Priority (Workflows & Ergonomics)**
   - **SDUI:** `TreeViewNode`, `FlowRowNode`, `MultiSelectDropdownNode`, `FilePickerNode`, `ColorPickerNode`, `BreadcrumbsNode`, `PaginationNode`, `RangeSliderNode`, `MarkdownTextNode`.
   - **Host:** Adopt `WideNavigationRail` + `NavigationSuiteScaffold` (`WindowSizeClass`), `NavigableListDetailPaneScaffold` / `SupportingPaneScaffold`, `AppBarRow` overflow toolbars, and `ContextMenuArea` wrappers.
3. **Low Priority / Specialized**
   - **SDUI:** `DatePickerNode` / `TimePickerNode`, `StepperNode`, `SideSheetNode`, `ImageCompareNode`, `MediaPlayerNode`, `FloatingToolbarNode`, `FabMenuNode`.
   - **Host:** `FloatingActionButtonMenu`, `MaterialShapes` morphing accents (e.g. badges/avatars/empty states).
