# Missing Components Summary

This document summarizes the UI components, Material 3 Expressive primitives, and Server-Driven UI (`UiNode`) capabilities that are currently **missing** across the project, covering both:

1. **Plugin SDUI (`plugin-api` + `shared/gui` renderer)** — declarative `UiNode` components, modifiers, and events available to `@InteractiveTool` plugins.
2. **Host Application (`shared/gui`)** — shared `ToolkitTheme` / Material 3 Expressive / Compose Desktop components and adaptive scaffolds.

---

## Current Baseline (Verified in Codebase — App `4.1.0-alpha04`)

- **Compose / Material 3 Versions** (`gradle/libs.versions.toml`): `compose-multiplatform = "1.12.0"`, `material3 = "1.12.0-alpha03"`.
- **SDUI `UiNode` Catalog** (`plugin-api/.../sdui/UiNode.kt`): 55 `UiNode` types (53 author-facing + `UnknownNode` and `ErrorBoundaryNode`):
  - **Layout & Shell:** `ColumnNode`, `RowNode`, `FlowRowNode`, `BoxNode`, `CardNode`, `ScrollableColumnNode` (with `scrollToIndex`/`scrollToEdge`/`scrollRevision`), `ScrollableRowNode`, `GridNode` (with scroll anchoring), `SplitPaneNode`, `ScaffoldNode`, `SidebarNode`, `FloatingPanelNode`, `TabsNode`, `AccordionNode`, `DialogNode`, `SpacerNode`, `DividerNode`.
  - **Controls & Inputs:** `ButtonNode`, `ButtonGroupNode`, `SplitButtonNode`, `IconButtonNode`, `MenuNode`, `TextInputNode` (with `isSecret`), `NumberInputNode`, `SearchBarNode`, `RadioGroupNode`, `ToggleNode`, `CheckboxNode`, `DropdownNode`, `MultiSelectDropdownNode`, `SliderNode`, `RangeSliderNode`, `FilePickerNode`, `ColorPickerNode`, `ChipGroupNode`, `ListItemNode`, `BreadcrumbsNode`, `PaginationNode`, `StepperNode`.
  - **Display, Media & Canvas:** `TextNode` (`selectable`, `maxLines`, `overflowEllipsis`), `MarkdownTextNode`, `CodeBlockNode`, `TableNode`, `TreeViewNode`, `KeyValueListNode`, `ImageNode` (`zoomable`), `IconNode`, `BadgeNode`, `ProgressBarNode` (`ProgressBarStyle`), `LoadingIndicatorNode`, `AlertBannerNode`, `EmptyStateNode`, `BoardNode`.
  - **Universal Modifiers (`UiModifier`):** `ShapeRole` (`cornerRadius`), `tooltip`, `contextMenu` (`ActionMenuItem`).
- **Host Theme & Shared Components** (`shared/gui/...`): `MaterialExpressiveTheme`, `MotionScheme.expressive()` + `StandardMotionScope`, 8-step `Shapes`, dual Baseline/`*Emphasized` `DesktopTypography`, `NormalizedMorphShape`, `ToolkitSplitButton` (`SplitButtonLayout`), `ToolkitConnectedToggleGroup`, `ToolkitLoadingIndicator`, `ToolkitLinearProgressIndicator`, `ToolkitCircularProgressIndicator`, `ToolkitDataTable`, `ToolkitTreeView`, `ToolkitSearchBar`, `ToolkitAccordion`, `ToolkitConfirmDialog`, `ToolkitSideSheet`, `ToolkitStatusBadge`, `ToolkitEmptyState`, `ToolkitTooltipBox` / `ToolkitRichTooltipBox`, `ToolkitButtonGroup`, `ToolkitCard`, `ToolkitChip`, `ToolkitTextField`, `ToolkitDropdownMenu`, `ExpressiveMenu`, `UnscaledExpressiveMenu`, `SelectedButtonGroup`, `SectionHeader`, `ToastHost`, `ZoomControls`, `NavigationSidebar` / `SidebarContainer`, `PlatformVerticalScrollbar` / `PlatformHorizontalScrollbar`, `PlatformContextMenuArea`, and parameter input widgets (`ColorInput`, `EnumDropdownInput`, `FileInput`, `PrimitiveInputs`).

---

## Part 1 — Missing Plugin SDUI Components (`plugin-api` + `DynamicUiRenderer`)

### 1.1 Form & Input Controls

| Missing Component | Proposed `UiNode` | What Is Missing Today | Key Capabilities Needed |
| :--- | :--- | :--- | :--- |
| **Radio Group** *(Implemented — Batch 1)* | `RadioGroupNode` | Implemented (`RadioOptionItem`, vertical/horizontal layout, subtitle, `UiEvent.OptionSelected`). | Vertical/horizontal layout, option title + subtitle/description, `UiEvent.OptionSelected`. |
| **Numeric / Stepper Input** *(Implemented — Batch 1)* | `NumberInputNode` | Implemented (`min`, `max`, `step`, `isDecimal`, `unitSuffix`, +/- steppers, Up/Down keys, `UiEvent.ValueChanged`). | `value`, `min`, `max`, `step`, `isDecimal`, `unitSuffix`, `+/-` steppers, arrow-key stepping, `UiEvent.ValueChanged`. |
| **Range Slider** *(Implemented — Batch 3)* | `RangeSliderNode` | Implemented (`startValue`, `endValue`, `min`, `max`, `steps`, `unitSuffix`, `UiEvent.RangeChanged`). | `startValue`, `endValue`, `min`, `max`, `steps`, dual-thumb dragging, `UiEvent.RangeChanged`. |
| **Search Bar / Autocomplete** *(Implemented — Batch 1)* | `SearchBarNode` | Implemented (`query`, `suggestions` menu, clear button, Enter-to-submit `UiEvent.Click`, `UiEvent.TextChanged`). | `query`, `suggestions`, `placeholder`, `showClearButton`, Enter-to-submit action + `UiEvent.TextChanged`. |
| **Multi-Select Dropdown / ComboBox** *(Implemented — Batch 3)* | `MultiSelectDropdownNode` | Implemented (`selectedKeys: Set<String>`, filter search, Select All / Clear, `InputChip` summary, `UiEvent.MultiOptionSelected`). | `selectedKeys: Set<String>`, searchable filter, select-all/clear, chip summary. |
| **Inline File / Directory Picker Control** *(Implemented — Batch 4)* | `FilePickerNode` | Implemented (`FILE`/`FILES`/`DIRECTORY`/`SAVE`, extension filter, `PlatformUtils` dialogs, `DynamicHostFileSystem.grantAccess` via `UiEvent.FilesPicked`). | Mode (`FILE`, `FILES`, `DIRECTORY`, `SAVE`), extension filter, selected path preview, browse/clear actions. |
| **Color Picker Control** *(Implemented — Batch 4)* | `ColorPickerNode` | Implemented (`ColorPickerDialog` integration, hex input, swatch preview, preset swatches, `UiEvent.ColorChanged`). | Hex/ARGB string value, optional alpha slider, swatch presets, `UiEvent.TextChanged` or `ColorChanged`. |
| **Date / Time Picker** | `DatePickerNode` / `TimePickerNode` | No date, date-range, or time input controls for scheduling, filtering, or metadata editing. | ISO-8601 date/time string or epoch millis, min/max bounds, `24h` toggle. |

### 1.2 Data Display, Code & Structured Productivity

| Missing Component | Proposed `UiNode` | What Is Missing Today | Key Capabilities Needed |
| :--- | :--- | :--- | :--- |
| **Data Table** *(Implemented — Batch 3)* | `TableNode` | Implemented via shared `ToolkitDataTable` (`columns` weight/`widthDp`/sortable, `rows` badges, `selectedRowKeys`, sticky header, keyboard Up/Down/Enter, scrollbars, `UiEvent.TableSorted`). | `columns` (title, width/weight, sortable), `rows`, `sortColumnKey`, `sortAscending`, row selection, sticky header, vertical + horizontal scrollbars. |
| **Hierarchical Tree View** *(Implemented — Batch 4)* | `TreeViewNode` | Implemented via shared `ToolkitTreeView` (`TreeNodeItem`, expand/collapse `UiEvent.TreeNodeToggled`, arrow-key navigation, selection). | `TreeNodeItem` (`key`, `label`, `iconName`, `children`, `expanded`), lazy-load / expand toggle event, selection. |
| **Accordion / Expandable Section** *(Implemented — Batch 1)* | `AccordionNode` | Implemented (`title`, `subtitle`, `expanded`, `leadingIcon`, `badgeText`/`badgeColor`, spring expand/collapse, `UiEvent.ToggleChanged`). | `title`, `subtitle`, `expanded`, `leadingIcon`, `trailingBadge`, collapsible children, `UiEvent.ToggleChanged` or `Click`. |
| **Code / Log Block** *(Implemented — Batch 1)* | `CodeBlockNode` | Implemented (`ToolkitTheme.codeMedium`, `language` pill, line numbers, wrap toggle, copy button, `SelectionContainer`, scrollbars). | Uses `ToolkitTheme.codeMedium`, optional `language`, `showLineNumbers`, `wrapLines`, copy-to-clipboard button, `SelectionContainer`. |
| **Markdown / Rich Text** *(Implemented — Batch 3)* | `MarkdownTextNode` | Implemented (headings `#`–`###`, bullet/numbered lists, bold/italic/strikethrough/inline code, clickable links via `LinkAnnotation`, `SelectionContainer`). | Safe subset of Markdown (headings, emphasis, code, lists, links emitting `UiEvent.Click` or opening host-approved URLs). |
| **Key-Value / Property List** *(Implemented — Batch 3)* | `KeyValueListNode` | Implemented (`KeyValueEntry` with aligned label/value, monospace option, semantic badges, dividers, and clipboard copy). | Aligned key/value rows, monospace toggle, per-row copy action, optional semantic badge. |
| **Wrapping Flow Layout** *(Implemented — Batch 3)* | `FlowRowNode` | Implemented (`FlowRow` container with `horizontalSpacing`, `verticalSpacing`, and `maxItemsInEachRow`). | Arbitrary `List<UiNode>` children with horizontal & vertical `SpacingSize`. |

### 1.3 Navigation, Wayfinding & Overlays

| Missing Component | Proposed `UiNode` | What Is Missing Today | Key Capabilities Needed |
| :--- | :--- | :--- | :--- |
| **Breadcrumbs** *(Implemented — Batch 4)* | `BreadcrumbsNode` | Implemented (`BreadcrumbItem`, custom separator, overflow menu after `maxVisibleItems`, `UiEvent.OptionSelected`). | `items` (`key`, `label`, `iconName`), separator, overflow truncation, click event per segment. |
| **Pagination Bar** *(Implemented — Batch 4)* | `PaginationNode` | Implemented (`currentPage`, `totalPages`, first/prev/next/last, `pageSizeOptions` menu, `UiEvent.PageChanged`). | `currentPage`, `totalPages`, optional `pageSize` selector, prev/next/first/last & page jump events. |
| **Stepper / Wizard Progress** *(Implemented — Batch 4)* | `StepperNode` | Implemented (`StepperItem`, `StepStatus`, horizontal/vertical modes, `UiEvent.OptionSelected`). | `steps` (`key`, `label`, `status`: pending/active/completed/error), clickable completed steps. |
| **Side Sheet / Modal Drawer** | `SideSheetNode` | `ToolkitSideSheet` host component implemented in Batch 4; SDUI `SideSheetNode` scheduled for Batch 6. | `isOpen`, `title`, `attachPoint` (`START`/`END`), dismiss event, max width `<= 400.dp`. |
| **Standalone Popup / Context Menu** *(Implemented — Batch 4)* | `MenuNode` / `UiModifier.contextMenu` | Both `UiModifier.contextMenu` and standalone `MenuNode` (`ToolkitDropdownMenu` + `UiEvent.Click`) implemented. | `List<ActionMenuItem>` attached via `UiModifier.contextMenu` (desktop right-click) or standalone overflow menu button. |
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
- [x] **Scroll control / anchoring** *(Batch 4)* — `scrollToIndex`, `scrollToEdge` (`TOP`/`BOTTOM`), and `scrollRevision` on `ScrollableColumnNode` and `GridNode`.

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
| **`ToolkitDataTable`** *(Implemented — Batch 3)* | Sortable, weighted/fixed multi-column table with sticky header, keyboard Up/Down/Enter navigation, and `VerticalScrollbar` + `HorizontalScrollbar`. |
| **`ToolkitTreeView`** *(Implemented — Batch 4)* | Collapsible tree view with arrow-key navigation, animated disclosure chevrons, and right-click context menus. |
| **`ToolkitSearchBar`** *(Implemented — Batch 4)* | Standardized search field with clear icon, result count badge, and `FocusRequester` (`Ctrl+F`) support. |
| **`ToolkitAccordion` / `ExpandableSection`** *(Implemented — Batch 4)* | Standardized expandable header + spring-animated body via `MotionScheme`. |
| **`ToolkitConfirmDialog` / `ToolkitSideSheet`** *(Implemented — Batch 4)* | Reusable M3 confirmation dialog and `<= 400.dp` co-planar side sheet with scrollbar & Escape dismissal. |
| **`ToolkitStatusBadge` & `ToolkitEmptyState`** *(Implemented — Batch 4)* | Standardized status pill badge and empty-state placeholder in `shared/components`. |
| **Unified `ContextMenuArea` & `TooltipBox` Helpers** *(Implemented — Batch 4)* | Added `ToolkitTooltipBox` / `ToolkitRichTooltipBox` (M3 `TooltipBox`) and migrated legacy `TooltipArea` / string `Modifier.tooltip` callers. |

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
