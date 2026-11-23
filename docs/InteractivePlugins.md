# Interactive Plugins (SDUI) Developer Guide

**Interactive Tools** (`@InteractiveTool`) allow plugins to render rich, reactive, multi-pane desktop user interfaces inside the host application without bundling Compose, Swing, or JavaFX UI toolkits. Your plugin emits immutable **Server-Driven UI (`UiNodeTree`)** snapshots over a Kotlin `Flow`, and the host renders them natively in Material 3 Expressive Desktop styling while streaming typed **`UiEvent`** interactions back to your session.

---

## Table of Contents

1. [When to Use `@InteractiveTool` vs `@Capability`](#1-when-to-use-interactivetool-vs-capability)
2. [Architecture & Package Imports](#2-architecture--package-imports)
3. [Reactive State Loop (`interactiveUiSession` & `channelFlow`)](#3-reactive-state-loop-interactiveuisession--channelflow)
4. [Single Packed Workspace vs Multi-Tool Navigation](#4-single-packed-workspace-vs-multi-tool-navigation)
5. [Layout & Component DSL Catalog](#5-layout--component-dsl-catalog)
6. [Modifiers, Spacing, Typography & Semantic Tokens](#6-modifiers-spacing-typography--semantic-tokens)
7. [Supported Material Icons (`SduiIconRegistry`)](#7-supported-material-icons-sduiiconregistry)
8. [Images, Local File Previews & Drag-and-Drop Reordering](#8-images-local-file-previews--drag-and-drop-reordering)
9. [Host Services Inside `UiSessionContext`](#9-host-services-inside-uisessioncontext)
10. [Events (`UiEvent`) & Delivery Guarantees](#10-events-uievent--delivery-guarantees)
11. [Spatial Board & Vector Canvas (`BoardNode`)](#11-spatial-board--vector-canvas-boardnode)
12. [Host Rendering Constraints & Safety Guardrails](#12-host-rendering-constraints--safety-guardrails)
13. [Testing Interactive Sessions](#13-testing-interactive-sessions)

---

## 1. When to Use `@InteractiveTool` vs `@Capability`

| Mechanism | Best For | Lifecycle |
| :--- | :--- | :--- |
| `@Capability` | Automated pipeline steps, batch uploads, background syncs, Node Editor nodes | Runs once per request, reports `ProgressReporter`, returns `ExecutionResult` |
| `@Capability` + `ExecutionResult.Paused` | One-off user pause/resume checkpoints | Suspends mid-capability until resumed |
| `@InteractiveTool` | Dashboards, series/chapter browsers, page orderers, image inspectors, form wizards, canvas editors | Long-lived reactive session (`openUiSession`) until closed or `completeSession` / `cancelSession` |

**Recommended Pattern for Complex Plugins (e.g., Manga / Publishing Workflows):**
Keep heavy network/Git/upload logic inside `@Capability` functions (so users can also queue them or run them in background jobs), and build your `@InteractiveTool` as a thin orchestration UI that observes `context.capabilityJobs`, `context.settingsFlow`, and `context.locksFlow`, dispatching work via `context.launchCapability(...)` or `context.executeCapability(...)`.

---

## 2. Architecture & Package Imports

Plugins depend on `plugin-api` and implement `InteractiveUiProcessor` on the `@PluginInfo` class:

- **Annotations:** `org.wip.plugintoolkit.api.annotations.*` (`@PluginInfo`, `@InteractiveTool`, `@Capability`, `@CapabilityParam`, `@PluginAction`, `@PluginSetting`, `@PluginLocks`, etc.)
- **Core Context, HTTP & Secrets:** `org.wip.plugintoolkit.api.*` (`PluginContext`, `PluginResponse`, `PluginSecretStorage`, `PluginNotificationType`, `HttpMethod`, `HttpRequest`, `HttpResponse`, `MultipartPart`, `ExecutionResult`)
- **Declarative SDUI & Events:** `org.wip.plugintoolkit.api.sdui.*` (`InteractiveUiProcessor`, `UiSessionContext`, `InteractiveSessionScope`, `UiNodeTree`, `UiNode`, `UiEvent`, `UiModifier`, `UiShortcut`, `UiDropTarget`, `uiTree`, `interactiveUiSession`, `BoardTool`, `BoardLayer`, `AssetReference`, `ImageScale`, `SpacingSize`, `TypographyRole`, `SemanticColor`, `ControlSize`, `AlignmentRole`, `FloatingAnchor`, `SidebarAttachPoint`, etc.)

> [!IMPORTANT]
> There is **no** `org.wip.plugintoolkit.api.sdui.dsl` package. All DSL builders (`uiTree`, `UiContainerBuilder`, `interactiveUiSession`, etc.) live directly in `org.wip.plugintoolkit.api.sdui`. Always prefer explicit top-level imports.

```kotlin
package org.example.myplugin

import kotlinx.coroutines.flow.Flow
import org.wip.plugintoolkit.api.PluginResponse
import org.wip.plugintoolkit.api.annotations.InteractiveTool
import org.wip.plugintoolkit.api.annotations.PluginInfo
import org.wip.plugintoolkit.api.sdui.InteractiveUiProcessor
import org.wip.plugintoolkit.api.sdui.SemanticColor
import org.wip.plugintoolkit.api.sdui.SpacingSize
import org.wip.plugintoolkit.api.sdui.TypographyRole
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.api.sdui.UiModifier
import org.wip.plugintoolkit.api.sdui.UiNodeTree
import org.wip.plugintoolkit.api.sdui.UiSessionContext
import org.wip.plugintoolkit.api.sdui.interactiveUiSession
import org.wip.plugintoolkit.api.sdui.uiTree

@PluginInfo(
    id = "org.example.myplugin",
    name = "My Interactive Plugin",
    description = "Demonstrates declarative desktop UI tools",
    version = "1.0.0",
    author = "Plugin Author"
)
class MyPlugin : InteractiveUiProcessor {

    @InteractiveTool(
        name = "mainWorkspace",
        description = "Opens the main interactive workspace."
    )
    fun mainWorkspace(): String = "Main workspace ready"

    override fun openUiSession(
        context: UiSessionContext,
        events: Flow<UiEvent>
    ): Flow<UiNodeTree> = when (context.capabilityName) {
        "mainWorkspace" -> runWorkspaceSession(context, events)
        else -> runFallbackSession(context)
    }
}
```

---

## 3. Reactive State Loop (`interactiveUiSession` & `channelFlow`)

For most interactive tools, use `interactiveUiSession`, which automatically collects `events`, combines local state with `context.capabilityJobs`, `context.settingsFlow`, and `context.locksFlow`, and emits rebuilt `UiNodeTree` snapshots:

```kotlin
import kotlinx.serialization.json.JsonPrimitive

private data class WorkspaceState(
    val title: String = "Chapter 01",
    val revision: Int = 0
)

internal fun runWorkspaceSession(
    context: UiSessionContext,
    events: Flow<UiEvent>
) = interactiveUiSession(
    initialState = WorkspaceState(),
    context = context,
    events = events,
    onEvent = { event ->
        when (event) {
            is UiEvent.TextChanged -> if (event.nodeId == "title_input") {
                updateState { it.copy(title = event.text) }
            }
            is UiEvent.Click -> when (event.action ?: event.nodeId) {
                "finish" -> context.completeSession(
                    PluginResponse(result = JsonPrimitive("Saved ${state.title}"))
                )
            }
            else -> Unit
        }
    }
) { sessionContext ->
    uiTree(version = 1) {
        column(id = "root", spacing = SpacingSize.MEDIUM, modifier = UiModifier.fillMaxSize().padding(SpacingSize.MEDIUM)) {
            text(text = "Active tool: ${sessionContext.capabilityName}", role = TypographyRole.TITLE_LARGE)
            textInput(
                id = "title_input",
                text = title,
                label = "Chapter Title",
                revision = revision,
                modifier = UiModifier.fillMaxWidth()
            )
            button(label = "Complete", id = "finish_btn", action = "finish", isPrimary = true)
        }
    }
}
```

### Dual-Loop (`channelFlow`) Pattern for Long Async Work

If your UI needs initialization (`secrets`, initial directory scan, API sync) or background workers pushing state updates independently of user events, use `channelFlow` + `MutableStateFlow` + `combine` (see [`InteractiveChapterPublisherDemo.kt`](../plugins/completeExample/src/main/kotlin/org/wip/complete/InteractiveChapterPublisherDemo.kt) for the full production pattern):

1. Hold a `MutableStateFlow(MyState())`.
2. `launch { /* initial async load from context.secrets / context.fileSystem / context.networkClient */ }`.
3. `launch { events.collect { event -> /* mutate state or spawn background jobs */ } }`.
4. `combine(stateFlow, context.capabilityJobs, context.settingsFlow, context.locksFlow) { ... -> buildTree(...) }.collect { send(it) }`.

### TextInput Revision Synchronization (`revision` & `forceOverwrite`)

To avoid race conditions between user typing and async state emissions, text fields track a monotonic integer `revision`:
- Keep `draftRevision` in your state unchanged when handling normal `UiEvent.TextChanged` events so user typing is never interrupted mid-keystroke.
- Increment `draftRevision` (and optionally pass `forceOverwrite = true`) whenever the plugin programmatically resets, clears, loads from disk, or switches the selected item displayed by that `TextInputNode`.

---

## 4. Single Packed Workspace vs Multi-Tool Navigation

You can structure large plugins either as **one packed multi-frame workspace** (`ScaffoldNode` + `TabsNode` + `SplitPaneNode`) or as **multiple focused `@InteractiveTool` sub-capabilities** that navigate between each other:

1. **Packed Single-Frame Workspace**:
   - Use `scaffold { topBar { ... }; leftSidebar { ... }; content { ... }; rightSidebar { ... }; bottomBar { ... }; floatingPanel { ... } }` or nested `tabs` + `splitPane` (`isHorizontal = true` for side-by-side panes, `isHorizontal = false` for stacked panes).
2. **Splitting into Multiple `@InteractiveTool`s (`openInteractiveTool`)**:
   - Declare multiple `@InteractiveTool` functions on your `@PluginInfo` class (e.g., `seriesDashboard`, `chapterUploadStudio`, `cubariRepoBrowser`).
   - Route `context.capabilityName` inside `openUiSession(context, events)`.
   - Navigate between sub-capabilities programmatically from any button/row event using:

```kotlin
context.openInteractiveTool(
    capabilityName = "chapterUploadStudio",
    initialParameters = mapOf(
        "seriesId" to JsonPrimitive("series_42"),
        "chapterNumber" to JsonPrimitive("12.5")
    )
)
```

- Read handed-off parameters in the destination session from `context.initialParameters`.
- Both the main host Interactive Tools view and standalone single-plugin host support `openInteractiveTool(...)` within the same plugin.

---

## 5. Layout & Component DSL Catalog

Every DSL call inside `uiTree { ... }` builds immutable `UiNode` models with deterministic IDs. Always assign explicit `id` values on stateful inputs, containers with dynamic children, tabs, split panes, reorderable lists/grids, and clickable controls.

### Layout & Shell Nodes

| DSL Builder | Node Class | Key Parameters |
| :--- | :--- | :--- |
| `column(...) { ... }` | `ColumnNode` | `id`, `spacing: SpacingSize = SpacingSize.NONE`, `modifier`, `reorderable: Boolean = false` |
| `row(...) { ... }` | `RowNode` | `id`, `spacing: SpacingSize = SpacingSize.NONE`, `modifier` |
| `box(...) { ... }` | `BoxNode` | `id`, `modifier` |
| `card(...) { ... }` | `CardNode` | `id`, `title: String? = null`, `surfaceRole: SemanticColor? = null` (solid M3 tonal fill), `outlined: Boolean = false`, `modifier` |
| `scrollableColumn(...) { ... }` | `ScrollableColumnNode` | `id`, `spacing: SpacingSize = SpacingSize.NONE`, `modifier`, `reorderable: Boolean = false` |
| `scrollableRow(...) { ... }` | `ScrollableRowNode` | `id`, `spacing: SpacingSize = SpacingSize.NONE`, `modifier` |
| `grid(...) { ... }` | `GridNode` | `id`, `columns: Int? = null`, `minItemWidthDp: Float = 156f`, `spacing: SpacingSize = SpacingSize.SMALL`, `reorderable: Boolean = false`, `modifier` |
| `splitPane(...)` | `SplitPaneNode` | `id`, `splitFraction: Float = 0.35f`, `isHorizontal: Boolean = true`, `resizable: Boolean = true`, `modifier`, `first = { ... }`, `second = { ... }` |
| `scaffold(...) { ... }` | `ScaffoldNode` | `content`, `leftSidebar`, `rightSidebar`, `sidebar`, `topBar`, `bottomBar`, `floatingPanel` |
| `sidebar(...) { ... }` | `SidebarNode` | `id`, `attachPoint: SidebarAttachPoint`, `title`, `isCollapsible = true`, `isCollapsed = false`, `modifier` |
| `floatingPanel(...) { ... }` | `FloatingPanelNode` | `id`, `anchor: FloatingAnchor`, `title`, `isCollapsible = true`, `isCollapsed = false`, `compact: Boolean = false`, `modifier` |
| `accordion(...) { ... }` | `AccordionNode` | `title`, `id`, `subtitle`, `expanded = false`, `leadingIcon`, `badgeText`, `badgeColor`, `modifier` -> `UiEvent.ToggleChanged(nodeId, expanded)` |
| `tabs(...) { ... }` | `TabsNode` | `selectedTabKey: String`, `tabs: List<TabItem>`, `id`, `modifier`, `contentBlock` |
| `dialog(...) { ... }` | `DialogNode` | `title`, `id`, `isOpen = true`, `confirmLabel = "OK"`, `dismissLabel = "Cancel"`, `isDestructive = false`, `modifier` |
| `spacer(...)` | `SpacerNode` | `size: SpacingSize = SpacingSize.MEDIUM`, `id`, `modifier` |
| `divider(...)` | `DividerNode` | `id`, `modifier` |

> [!TIP]
> Avoid nesting `ColumnNode(reorderable = true)` or scrollable containers inside another scrollable container without bounded heights. Give inner `ScrollableColumnNode`, `GridNode`, or `SplitPaneNode` nodes explicit heights (`height(...)` / `weight(1f)` / `fillMaxHeight()` / `fillMaxSize()`).

### Interactive Controls

| DSL Builder | Node Class | Key Parameters & Emitted Event |
| :--- | :--- | :--- |
| `button(...)` | `ButtonNode` | `label`, `id`, `action: String? = null`, `isPrimary: Boolean = true`, `isDestructive: Boolean = false`, `isTonal: Boolean = false`, `iconName: String? = null`, `modifier` -> `UiEvent.Click(nodeId, action)` |
| `buttonGroup(...) { item(...) }` | `ButtonGroupNode` | `id`, `selectionMode: Boolean = true`, `modifier`, items (`key`, `label`, `action`, `iconName`, `selected`, `color: SemanticColor?`, `enabled`, `tooltip`) -> `UiEvent.Click(nodeId, action ?: key)` |
| `splitButton(...) { item(...) }` | `SplitButtonNode` | `label`, `primaryAction`, `id`, `iconName`, `isTonal = false`, `menuTooltip`, `modifier`, popup menu items (`key`, `label`, `action`, `iconName`, `isDestructive`, `enabled`, `startsSection`) -> `UiEvent.Click(nodeId, action ?: key)` |
| `iconButton(...)` | `IconButtonNode` | `iconName`, `id`, `action: String? = null`, `tooltip: String? = null`, `tint: SemanticColor? = null`, `isTonal: Boolean = false`, `modifier` -> `UiEvent.Click(nodeId, action)` |
| `textInput(...)` | `TextInputNode` | `id`, `text = ""`, `label`, `placeholder`, `singleLine = true`, `debounceMs = 150L`, `revision = 0`, `forceOverwrite = false`, `regexPattern = null`, `maxLength = null`, `isSecret = false`, `modifier` -> `UiEvent.TextChanged(nodeId, text)` |
| `searchBar(...)` | `SearchBarNode` | `id`, `query = ""`, `suggestions: List<String>`, `placeholder`, `label`, `showClearButton = true`, `action`, `debounceMs = 150L`, `revision = 0`, `forceOverwrite = false`, `modifier` -> `UiEvent.TextChanged` + `UiEvent.Click` |
| `numberInput(...)` | `NumberInputNode` | `id`, `value = 0.0`, `min`, `max`, `step = 1.0`, `isDecimal = false`, `unitSuffix`, `label`, `placeholder`, `revision = 0`, `forceOverwrite = false`, `modifier` -> `UiEvent.ValueChanged(nodeId, value)` |
| `radioGroup(...) { option(...) }` | `RadioGroupNode` | `id`, `selectedKey`, `label`, `horizontal = false`, `modifier`, options (`key`, `label`, `subtitle`, `enabled`) -> `UiEvent.OptionSelected(nodeId, key)` |
| `toggle(...)` | `ToggleNode` | `id`, `checked: Boolean`, `label`, `modifier` -> `UiEvent.ToggleChanged(nodeId, checked)` |
| `checkbox(...)` | `CheckboxNode` | `checked: Boolean`, `id`, `label`, `modifier` -> `UiEvent.ToggleChanged(nodeId, checked)` |
| `dropdown(...)` | `DropdownNode` | `id`, `selectedKey: String`, `options: Map<String, String>`, `label`, `modifier` -> `UiEvent.OptionSelected(nodeId, selectedKey)` |
| `slider(...)` | `SliderNode` | `id`, `value: Double`, `min = 0.0`, `max = 1.0`, `steps = 0`, `label`, `modifier` -> `UiEvent.ValueChanged(nodeId, value)` |
| `chipGroup(...)` | `ChipGroupNode` | `chips: List<ChipItem>`, `id`, `selectedKeys: Set<String> = emptySet()`, `multiSelect = false`, `modifier` -> `UiEvent.ChipSelected(nodeId, chipKey, isSelected)` |
| `listItem(...)` | `ListItemNode` | `title`, `id`, `subtitle`, `leadingIcon`, `leadingAsset: AssetReference?`, `badgeText`, `badgeColor`, `selected = false`, `selectable = false`, `action: String? = null`, `modifier`, `trailingBlock` |

- **Secret Inputs (`isSecret = true`):** Renders masked characters with a built-in visibility toggle icon for tokens, passwords, and API keys.
- **List Items (`ListItemNode`):** Clicking the main row fires `UiEvent.Click(nodeId, action)` when `action != null` (or if `!selectable`), or `UiEvent.ToggleChanged` when `selectable && action == null`. Controls inside `trailingBlock` (buttons, iconButtons, checkboxes) emit their own independent events without triggering the row click.

### Display, Status & Media

| DSL Builder | Node Class | Key Parameters |
| :--- | :--- | :--- |
| `text(...)` | `TextNode` | `text`, `id`, `role: TypographyRole = TypographyRole.BODY_MEDIUM`, `color: SemanticColor? = null`, `modifier`, `selectable = false`, `maxLines = null`, `overflowEllipsis = false` |
| `codeBlock(...)` | `CodeBlockNode` | `code`, `id`, `language`, `title`, `showLineNumbers = true`, `wrapLines = false`, `showCopyButton = true`, `maxHeightDp`, `modifier` |
| `image(...)` | `ImageNode` | `asset: AssetReference`, `id`, `contentDescription`, `contentScale: ImageScale = ImageScale.FIT`, `clickable = false`, `action = null`, `selected = false`, `badgeText = null`, `modifier`, `zoomable = false` |
| `icon(...)` | `IconNode` | `name: String`, `id`, `tint: SemanticColor? = null`, `size: ControlSize = ControlSize.MEDIUM`, `modifier` |
| `badge(...)` | `BadgeNode` | `text: String`, `id`, `color: SemanticColor = SemanticColor.PRIMARY`, `modifier` |
| `progressBar(...)` | `ProgressBarNode` | `id`, `progress: Float? = null` (`null` = indeterminate), `secondaryProgress: Float? = null`, `label`, `statusText`, `isCircular = false`, `color: SemanticColor = SemanticColor.PRIMARY`, `modifier` |
| `alertBanner(...)` | `AlertBannerNode` | `message`, `id`, `title`, `severity: SemanticColor = SemanticColor.INFO`, `iconName`, `actionLabel`, `modifier` |
| `emptyState(...)` | `EmptyStateNode` | `title`, `id`, `description`, `iconName = "info"`, `actionLabel`, `modifier` |

---

## 6. Modifiers, Spacing, Typography & Semantic Tokens

Plugins never hardcode pixel colors or theme objects; all visual styling uses host Material 3 tokens via `UiModifier` and semantic enums.

### `UiModifier` Chain API

```kotlin
UiModifier
    .padding(SpacingSize.MEDIUM)       // NONE, EXTRA_SMALL, SMALL, MEDIUM_SMALL, MEDIUM, LARGE, EXTRA_LARGE
    .fillMaxWidth(1.0f)
    .fillMaxHeight(1.0f)
    .fillMaxSize(1.0f)
    .weight(1f)                        // Weighted share inside RowNode or ColumnNode
    .width(280)                        // dp (Int)
    .height(180)                       // dp (Int)
    .size(widthDp = 120, heightDp = 160)
    .widthIn(min = 120, max = 480)
    .heightIn(min = 48, max = 320)
    .aspectRatio(0.707f)               // e.g. portrait manga page ratio
    .align(AlignmentRole.CENTER_END)
    .controlSize(ControlSize.SMALL)    // SMALL, MEDIUM, LARGE
    .background(SemanticColor.SURFACE)
    .border(SemanticColor.OUTLINE)
    .clipRounded(true)
    .cornerRadius(ShapeRole.LARGE)     // NONE, EXTRA_SMALL, SMALL, MEDIUM, LARGE, EXTRA_LARGE, PILL
    .tooltip("Hover help text")        // Universal hover/focus tooltip
    .contextMenu {                     // Native desktop right-click ContextMenuArea
        item(key = "copy", label = "Copy", action = "copy_action")
    }
    .enabled(true)
    .visible(true)
    .shortcut(key = UiShortcut.KEY_DELETE, action = "delete_page", label = "Del")
    .shortcut(key = "S", action = "save_draft", ctrl = true, allowWhileTyping = true, label = "Ctrl+S")
    .onFileDrop(
        action = "drop_page_files",
        allowedExtensions = listOf("png", "jpg", "jpeg", "webp"),
        hint = "Drop page images here"
    )
```

### Enum Reference

- **`SpacingSize`**: `NONE`, `EXTRA_SMALL`, `SMALL`, `MEDIUM_SMALL`, `MEDIUM`, `LARGE`, `EXTRA_LARGE`
- **`TypographyRole`**: `DISPLAY`, `HEADLINE`, `TITLE_LARGE`, `TITLE_MEDIUM`, `BODY_LARGE`, `BODY_MEDIUM`, `BODY_SMALL`, `LABEL`
- **`SemanticColor`**:
  - Brand & Emphasis: `PRIMARY`, `PRIMARY_CONTAINER`, `SECONDARY`, `SECONDARY_CONTAINER`, `TERTIARY`, `TERTIARY_CONTAINER`, `ERROR`, `ERROR_CONTAINER`, `ACCENT`, `HIGHLIGHT`
  - Solid Tonal Elevation & Text Hierarchy: `SURFACE`, `SURFACE_CONTAINER_LOWEST`, `SURFACE_CONTAINER_LOW`, `SURFACE_CONTAINER`, `SURFACE_CONTAINER_HIGH`, `SURFACE_CONTAINER_HIGHEST`, `ON_SURFACE`, `ON_SURFACE_VARIANT`, `OUTLINE`
  - Semantic Status Fills & Containers: `INFO`, `INFO_CONTAINER`, `SUCCESS`, `SUCCESS_CONTAINER`, `WARNING`, `WARNING_CONTAINER`
- **`ControlSize`**: `SMALL`, `MEDIUM`, `LARGE`
- **`ShapeRole`**: `NONE`, `EXTRA_SMALL`, `SMALL`, `MEDIUM`, `LARGE`, `EXTRA_LARGE`, `PILL`
- **`ImageScale`**: `FIT`, `CROP`, `FILL_BOUNDS`, `INSIDE`
- **`FloatingAnchor`**: `TOP_START`, `TOP_CENTER`, `TOP_END`, `BOTTOM_START`, `BOTTOM_CENTER`, `BOTTOM_END`, `CENTER_START`, `CENTER_END`, `CENTER`
- **`SidebarAttachPoint`**: `LEFT`, `TOP`, `RIGHT`, `BOTTOM`
- **`AlignmentRole`**: `START`, `CENTER`, `END`, `TOP`, `BOTTOM`, `TOP_START`, `TOP_CENTER`, `TOP_END`, `BOTTOM_START`, `BOTTOM_CENTER`, `BOTTOM_END`, `CENTER_START`, `CENTER_END`

---

## 7. Supported Material Icons (`SduiIconRegistry`)

The host resolves icon names case-insensitively after trimming and normalizing `-` and spaces to `_`. Unknown names safely fall back to the `info` icon. Use any of these exact supported icon keys in `icon(...)`, `iconButton(...)`, `listItem(leadingIcon = ...)`, `tab(iconName = ...)`, `chipGroup`, `alertBanner`, and `emptyState`:

- **Files, Media & Content:** `folder`, `folder_open`, `image`, `article`, `description`, `book`, `chapter`, `code`, `list`, `grid_view`, `grid`, `layers`
- **Upload, Sync & Cloud:** `upload`, `download`, `sync`, `cloud_done`, `cloud_upload`, `cloud_download`, `publish`, `refresh`, `send`, `open_in_new`
- **Actions & Editing:** `add`, `plus`, `edit`, `delete`, `trash`, `save`, `close`, `clear`, `copy`, `content_copy`, `search`, `filter`, `filter_list`, `undo`, `redo`, `more_vert`
- **Ordering & Navigation:** `drag_indicator`, `drag`, `reorder`, `swap_vert`, `arrow_back`, `arrow_forward`, `arrow_upward`, `arrow_downward`, `expand_more`, `expand_less`, `chevron_right`
- **Status & Notifications:** `check`, `done`, `check_circle`, `warning`, `error`, `info`, `notifications`, `schedule`, `history`, `star`
- **Security, Visibility & Transport:** `lock`, `unlock`, `lock_open`, `key`, `visibility`, `eye`, `visibility_off`, `settings`, `play`, `pause`, `stop`
- **Spatial Board Tools:** `zoom_in`, `zoom_out`, `fit_screen`, `pan_tool`, `pan`, `select`, `brush`, `crop_square`, `rectangle`, `text_fields`, `text`, `chat_bubble`, `callout`

---

## 8. Images, Local File Previews & Drag-and-Drop Reordering

### `AssetReference` Resolution Rules

`ImageNode.asset`, `ListItemNode.leadingAsset`, `BoardNode.backgroundAsset`, and `BoardLayer.ImageLayer.asset` all accept `AssetReference`:

| Variant | Supported Sources & Limits |
| :--- | :--- |
| `AssetReference.Relative(path)` | Resolved safely inside the plugin's `ScopedFileSystem` (`fileSystem` / data folder). Paths with traversal (`..`) are rejected. |
| `AssetReference.Uri(uriString)` | **1.** `data:` base64 image URIs (`data:image/png;base64,...`).<br>**2.** `http://` and `https://` URLs fetched through the plugin's metered `networkClient.request` (capped at **25 MB** per image).<br>**3.** `file://`, `file:/...`, or OS-native absolute local file paths, **only** if previously permitted in `hostFileSystem` (e.g., picked by `pickFile` / `pickFiles` / `pickDirectory`). |
| `AssetReference.Cached(assetId)` | Reserved cached asset key (currently returns uncached/empty on host decode unless written to `Relative` or `Uri`). |

### Interactive Clickable Thumbnails (`ImageNode`)

For dense page/cover galleries, enable thumbnail clicks, selection highlights, cropping, and corner badges:

```kotlin
image(
    asset = AssetReference.Uri(page.localPath),
    id = page.nodeId,
    contentDescription = page.label,
    contentScale = ImageScale.CROP,
    clickable = true,
    action = "preview_page:${page.id}",
    selected = page.id == selectedPageId,
    badgeText = "#${index + 1}",
    modifier = UiModifier.fillMaxWidth().aspectRatio(0.71f)
)
```

Clicking triggers `UiEvent.Click(nodeId = page.nodeId, action = "preview_page:${page.id}")`.

### Drag-and-Drop Reordering (`GridNode`, `ColumnNode`, `ScrollableColumnNode`)

Set `reorderable = true` on `grid`, `column`, or `scrollableColumn`, and ensure **every child node has a stable unique `id`**. When the user drags a tile or row in the host UI, the host commits the new sequence by emitting `UiEvent.ItemsReordered`:

```kotlin
grid(
    id = "page_order_grid",
    minItemWidthDp = 132f,
    spacing = SpacingSize.SMALL,
    reorderable = true,
    modifier = UiModifier.fillMaxWidth().height(380)
) {
    pages.forEachIndexed { index, page ->
        card(id = page.id, modifier = UiModifier.fillMaxWidth()) {
            column(id = "${page.id}_col", spacing = SpacingSize.EXTRA_SMALL, modifier = UiModifier.padding(SpacingSize.EXTRA_SMALL)) {
                image(
                    asset = page.asset,
                    id = "${page.id}_img",
                    contentScale = ImageScale.CROP,
                    clickable = true,
                    action = "select_page:${page.id}",
                    selected = page.id == selectedPageId,
                    badgeText = "#${index + 1}",
                    modifier = UiModifier.fillMaxWidth().aspectRatio(0.72f)
                )
                text(text = page.filename, id = "${page.id}_lbl", role = TypographyRole.BODY_SMALL)
            }
        }
    }
}
```

Handle `UiEvent.ItemsReordered` in `onEvent`:

```kotlin
is UiEvent.ItemsReordered -> if (event.nodeId == "page_order_grid") {
    val byId = state.pages.associateBy { it.id }
    val reordered = event.itemIds.mapNotNull { byId[it] }
    if (reordered.size == state.pages.size) {
        updateState { it.copy(pages = reordered) }
    }
}
```

> [!NOTE]
> `UiEvent.ItemsReordered` is dispatched on the **lossless** host event lane alongside `Click`, `TextChanged`, `DialogAction`, `TabSelected`, `ShortcutTriggered`, `FilesDropped`, and other discrete events—rapid consecutive reorders are never conflated or dropped.

### External OS File & Folder Drag-and-Drop (`UiModifier.onFileDrop` / `UiModifier.dropTarget`)

Desktop users can drag image files or repository folders straight from their OS file manager onto any SDUI container or tile annotated with `.onFileDrop(...)` or `.dropTarget(UiDropTarget(...))`:

```kotlin
scrollableColumn(
    id = "pages_pane",
    spacing = SpacingSize.SMALL,
    modifier = UiModifier.fillMaxSize()
        .onFileDrop(
            action = "drop_page_files",
            allowedExtensions = listOf("png", "jpg", "jpeg", "webp"),
            maxFiles = 200,
            hint = "Drop PNG, JPG, or WEBP page files here"
        )
) {
    // ...
}
```

- When valid files or directories are dropped, the host automatically grants `hostFileSystem` read access for every returned path and emits `UiEvent.FilesDropped(nodeId, action, paths)`.
- Any local images inside `paths` can immediately be rendered in `ImageNode(AssetReference.Uri(path))` without calling `pickFiles(...)`.
- To accept only folders (for example a local Cubari Git clone dropzone), pass `.dropTarget(UiDropTarget(action = "drop_repo_folder", acceptFiles = false, acceptDirectories = true, maxFiles = 1, hint = "Drop repository folder"))`.

### Keyboard Shortcuts & Hotkeys (`UiModifier.shortcut`)

Attach desktop hotkeys directly to workspace roots, split panes, or specific panels using `.shortcut(...)` / `.shortcuts(...)`:

```kotlin
scaffold(
    id = "workspace_root",
    modifier = UiModifier.fillMaxSize()
        .shortcut(key = "S", action = "save_draft", ctrl = true, allowWhileTyping = true, label = "Ctrl+S")
        .shortcut(key = UiShortcut.KEY_ESCAPE, action = "dismiss_or_back", label = "Esc")
        .shortcut(key = UiShortcut.KEY_DELETE, action = "delete_selected_pages", label = "Del")
        .shortcut(key = UiShortcut.KEY_ARROW_LEFT, action = "move_page_prev", label = "Left")
        .shortcut(key = UiShortcut.KEY_ARROW_RIGHT, action = "move_page_next", label = "Right")
) { /* ... */ }
```

- When pressed, the host emits `UiEvent.ShortcutTriggered(nodeId, action, key, ctrl, shift, alt, meta)` targeting the deepest declared matching node.
- **Typing Protection:** While a `TextInputNode` holds focus, unmodified keys (`Delete`, `Backspace`, arrows, single letters) and standard text-editing combos (`Ctrl+A/C/V/X/Z/Y`) continue editing text instead of firing canvas hotkeys. Set `allowWhileTyping = true` (or use non-text command chords like `Ctrl+S`) for shortcuts that should fire even while typing.
- Standard key constants on `UiShortcut`: `KEY_DELETE`, `KEY_BACKSPACE`, `KEY_ESCAPE`, `KEY_ENTER`, `KEY_SPACE`, `KEY_TAB`, `KEY_ARROW_LEFT`, `KEY_ARROW_RIGHT`, `KEY_ARROW_UP`, `KEY_ARROW_DOWN`, `KEY_HOME`, `KEY_END`, `KEY_PAGE_UP`, `KEY_PAGE_DOWN`.

---

## 9. Host Services Inside `UiSessionContext`

`UiSessionContext` extends `PluginContext` with session-specific UI and filesystem bridges:

### Native File & Directory Pickers

- `suspend fun pickFile(title: String? = null, allowedExtensions: List<String> = emptyList()): String?`
- `suspend fun pickFiles(title: String? = null, allowedExtensions: List<String> = emptyList()): List<String>`
- `suspend fun pickDirectory(title: String? = null): String?`
- `suspend fun saveFile(defaultName: String, extension: String, bytes: ByteArray): String?`

Calling `pickFile` or `pickFiles` automatically grants **read access** on `context.hostFileSystem` for the returned file paths (and lets `ImageNode(AssetReference.Uri(path))` render those local images immediately). Calling `pickDirectory` automatically grants **read + write access** for that directory subtree on `context.hostFileSystem`, enabling local Git repo navigation or batch image reads.

### Encrypted Secret Storage (`context.secrets`)

Never store API keys, refresh tokens, passwords, or webhook secrets in plain `settings` or `storage`. Use `context.secrets` (`PluginSecretStorage`), which encrypts secrets at rest per plugin:

- `suspend fun getSecret(key: String): String?`
- `suspend fun putSecret(key: String, value: String)`
- `suspend fun removeSecret(key: String)`
- `suspend fun listSecretKeys(): List<String>`
- `suspend fun clearSecrets()`

### Full HTTP Client & Multipart Uploads (`context.networkClient`)

`context.networkClient` tracks traffic metrics automatically and supports both convenience helpers (`get`, `getText`, `post`, `postText`) and full HTTP requests via `request(HttpRequest)`:

```kotlin
val response = context.networkClient.request(
    HttpRequest(
        url = "https://api.example.com/upload",
        method = HttpMethod.POST,
        headers = mapOf("Authorization" to "Bearer $token"),
        multipartParts = listOf(
            MultipartPart(
                name = "file",
                data = pageBytes,
                filename = "001.png",
                contentType = "image/png"
            ),
            MultipartPart(
                name = "metadata",
                data = """{"chapter":"42"}""".encodeToByteArray(),
                contentType = "application/json"
            )
        ),
        timeoutMs = 30_000L
    )
)

if (!response.isSuccess) {
    // Non-2xx statuses (e.g. 401, 404, 429) return normally so you can read headers/body
    val retryAfter = response.header("Retry-After")
    context.logger.warn("HTTP ${response.statusCode}: ${response.bodyAsText()} (retryAfter=$retryAfter)")
}
```

Supported `HttpMethod` values: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`.

### Host Toasts, Notifications & Popups

- **Toasts:** `context.showToast(message)` (or `context.toast(message)`) displays a transient host toast via `ToastHost` at the application root.
- **Notifications:** `context.showNotification(title, message, type)` records a persistent entry in the host notification center and surfaces critical alerts using `PluginNotificationType.INFO`, `SUCCESS`, `WARNING`, or `ERROR`.
- **Popups & Overlays:** Use `dialog(...)` (`DialogNode`) for modal confirmation/preview dialogs, `splitButton(...)` (`SplitButtonNode`) for anchored contextual action menus (`ToolkitDropdownMenu`), `dropdown(...)` (`DropdownNode`) for inline option selectors, and `floatingPanel(...)` (`FloatingPanelNode`) for canvas overlays.

### Live Settings Synchronization (`settingsFlow`, `updateSetting`, `clearSetting`, `locksFlow`)

Inside `UiSessionContext`, your session reads AND writes `@PluginSetting` configuration bidirectionally through the host settings controller (`PluginLifecycleManager`):

- `context.initialParameters: Map<String, JsonElement>` — parameters passed from a caller or `openInteractiveTool(...)`.
- `context.settingsFlow: StateFlow<Map<String, JsonElement>>` — live effective settings map (manifest `@PluginSetting` `defaultValue`s merged with user overrides).
- `context.updateSetting(key, value)` / `context.updateSettings(map)` — writes directly through the host settings pipeline: encrypts `@PluginSetting(secret = true)` keys at rest in `settings.json`, updates `settingsFlow` AND Host Native Plugin Settings simultaneously, and automatically re-evaluates `@PluginLocks` (`refreshLocks`).
- `context.clearSetting(key)` — clears a user override so the setting reverts to its manifest `@PluginSetting(defaultValue = ...)` and refreshes locks.
- `context.locksFlow: StateFlow<Map<String, Boolean>>` — live capability lock states (`@PluginLocks`).
- `context.capabilityJobs: StateFlow<List<CapabilityJobSnapshot>>` — live progress (`0f..1f`), logs, status (`QUEUED`, `RUNNING`, `PAUSE_REQUESTED`, `PAUSED`, `COMPLETED`, `FAILED`, `CANCELLED`), and results of capabilities launched from this session.
- `context.launchCapability(capabilityName, parameters = emptyMap(), runInBackgroundQueue = true): String` — returns `jobId`.
- `context.executeCapability(capabilityName, parameters = emptyMap()): ExecutionResult` — awaits result directly.
- `context.launchAction(actionName, parameters = emptyMap()): Result<Unit>` — invokes `@PluginAction` methods (e.g., to refresh locks).
- `context.observeCapabilityJob(jobId)`, `pauseCapabilityJob(jobId)`, `resumeCapabilityJob(jobId)`, `cancelCapabilityJob(jobId)`.

```kotlin
// Inside an InteractiveUiProcessor event handler:
is UiEvent.TextChanged -> if (event.nodeId == "preview_hours_input") {
    context.updateSetting("previewWindowHours", JsonPrimitive(event.text))
}
is UiEvent.Click -> if (event.action == "reset_preview_window") {
    context.clearSetting("previewWindowHours")
}
```

---

## 10. Events (`UiEvent`) & Delivery Guarantees

The host splits incoming events into two queues so high-frequency pointer/slider events never drop button clicks or typed characters:
- **Lossless queue:** `Click`, `OptionSelected`, `ToggleChanged`, `TextChanged`, `ItemsReordered`, `ShortcutTriggered`, `FilesDropped`, `BoardLayerSelected`, `BoardLayerMoved`, `BoardLayerResized`, `BoardTextEdited`, `BoardTextPlaced`, `BoardShapeCommitted`, `BoardLayerDeleted`, `BoardCanvasClicked`, `TabSelected`, `ChipSelected`, `DialogAction`, `PathCommitted`, `Custom`.
- **Conflated high-frequency queue:** `ValueChanged` (live slider / split-pane drag updates).

| Event Class | Fields | Source Control(s) |
| :--- | :--- | :--- |
| `UiEvent.Click` | `nodeId`, `action: String?` | `ButtonNode`, `ButtonGroupNode`, `SplitButtonNode`, `IconButtonNode`, clickable `ImageNode`, `ListItemNode`, `AlertBannerNode`, `EmptyStateNode` |
| `UiEvent.TextChanged` | `nodeId`, `text: String` | `TextInputNode` (debounced by `debounceMs`) |
| `UiEvent.ToggleChanged` | `nodeId`, `checked: Boolean` | `ToggleNode`, `CheckboxNode`, selectable `ListItemNode` without `action` |
| `UiEvent.OptionSelected` | `nodeId`, `selectedKey: String` | `DropdownNode` |
| `UiEvent.ValueChanged` | `nodeId`, `value: Double` | `SliderNode`, resizable `SplitPaneNode` (`splitFraction`) |
| `UiEvent.ItemsReordered` | `nodeId`, `itemIds: List<String>`, `fromIndex: Int`, `toIndex: Int` | `GridNode`, `ColumnNode`, `ScrollableColumnNode` with `reorderable = true` |
| `UiEvent.ShortcutTriggered` | `nodeId`, `action: String`, `key: String`, `ctrl`, `shift`, `alt`, `meta` | Any `UiNode` declaring `UiModifier.shortcut(...)` / `UiModifier.shortcuts(...)` |
| `UiEvent.FilesDropped` | `nodeId`, `action: String`, `paths: List<String>` | Any `UiNode` declaring `UiModifier.onFileDrop(...)` / `UiModifier.dropTarget(...)` |
| `UiEvent.TabSelected` | `nodeId`, `tabKey: String` | `TabsNode` |
| `UiEvent.ChipSelected` | `nodeId`, `chipKey: String`, `isSelected: Boolean` | `ChipGroupNode` |
| `UiEvent.DialogAction` | `nodeId`, `isConfirmed: Boolean` | `DialogNode` |
| `UiEvent.PathCommitted` | `nodeId`, `path: VectorPathData` | `BoardNode` (`Brush`, `Highlighter`, `Rectangle`, `Eraser`) |
| `UiEvent.BoardLayerSelected` | `nodeId`, `selectedLayerIds: List<String>` | `BoardNode` |
| `UiEvent.BoardLayerMoved` | `nodeId`, `layerId: String`, `position: NormalizedPoint` | `BoardNode` |
| `UiEvent.BoardLayerResized` | `nodeId`, `layerId: String`, `position: NormalizedPoint`, `size: NormalizedSize` | `BoardNode` |
| `UiEvent.BoardTextEdited` | `nodeId`, `layerId: String`, `text: String` | `BoardNode` (`TextLayer`, `ShapeLayer`) |
| `UiEvent.BoardTextPlaced` | `nodeId`, `position: NormalizedPoint`, `text: String` | `BoardNode` (`BoardTool.PlaceText`) |
| `UiEvent.BoardShapeCommitted` | `nodeId`, `shapeType`, `position`, `size`, `strokeColor`, `fillColor`, `strokeWidth` | `BoardNode` (`BoardTool.Shape`, `BoardTool.Rectangle`) |
| `UiEvent.BoardLayerDeleted` | `nodeId`, `layerIds: List<String>` | `BoardNode` |
| `UiEvent.BoardCanvasClicked` | `nodeId`, `position: NormalizedPoint` | `BoardNode` |
| `UiEvent.Custom` | `nodeId`, `action: String`, `payload: JsonElement?` | Custom host/forward-compatible payloads |

---

## 11. Spatial Board & Vector Canvas (`BoardNode`)

Use `board(...)` for visual cover composition, manga page typesetting/annotation, callout bubbles, stamps, or spatial inspection:

- **Coordinates:** Always normalized in `0.0f..1.0f` (`NormalizedPoint(x, y)` and `NormalizedSize(width, height)`), making layouts resolution-independent across desktop window sizes.
- **Layer Types (`BoardLayer`):**
  - `ImageLayer` (`imageLayer(...)`)
  - `TextLayer` (`textLayer(...)`)
  - `ShapeLayer` (`shapeLayer(...)` with `BoardShapeType`: `RECTANGLE`, `ROUNDED_RECT`, `ELLIPSE`, `CALLOUT_BUBBLE`, `ARROW`)
  - `PathLayer` (`pathLayer(...)`)
  - `ComponentLayer` (`componentLayer(...) { ... }` — embeds any SDUI `UiNode` tree directly on the board!)
- **Tools (`BoardTool`):** `BoardTool.Select(...)`, `BoardTool.Pan`, `BoardTool.PlaceText(...)`, `BoardTool.Shape(...)`, `BoardTool.Brush(...)`, `BoardTool.Highlighter(...)`, `BoardTool.Rectangle(...)`, `BoardTool.Eraser(...)`.
- **Viewport Config (`BoardViewportConfig`):** `zoom = 1f`, `minZoom = 0.25f`, `maxZoom = 8f`, `pan = NormalizedPoint(0f, 0f)`, `gridStyle = BoardGridStyle.DOTS` (`NONE`, `DOTS`, `LINES`, `CROSSHAIRS`), `snapToGrid = false`, `gridStep = 0.05f`, `canvasAspectRatio: Float? = null`.

```kotlin
board(
    id = "page_compositor",
    backgroundAsset = AssetReference.Relative("covers/page_01.png"),
    viewportConfig = BoardViewportConfig(
        gridStyle = BoardGridStyle.DOTS,
        snapToGrid = true,
        canvasAspectRatio = 0.71f
    ),
    selectedLayerIds = state.selectedLayerIds,
    activeTool = BoardTool.Select(),
    showToolbar = true,
    showZoomControls = true,
    modifier = UiModifier.fillMaxSize()
) {
    textLayer(
        id = "title_caption",
        text = "Chapter 42 — Opening",
        position = NormalizedPoint(0.08f, 0.06f),
        color = SemanticColor.ON_SURFACE,
        backgroundColor = SemanticColor.SURFACE
    )
    shapeLayer(
        id = "sfx_callout",
        shapeType = BoardShapeType.CALLOUT_BUBBLE,
        position = NormalizedPoint(0.55f, 0.25f),
        size = NormalizedSize(0.32f, 0.14f),
        strokeColor = SemanticColor.PRIMARY,
        fillColor = SemanticColor.SURFACE,
        text = "Boom!"
    )
}
```

See [`InteractiveBoardCompositorDemo.kt`](../plugins/completeExample/src/main/kotlin/org/wip/complete/InteractiveBoardCompositorDemo.kt) for full undo/redo, layer reordering, inline text editing, shape drawing, and export capability wiring.

---

## 12. Host Rendering Constraints & Safety Guardrails

Every emitted `UiNodeTree` passes through the host `UiAstSanitizer` before rendering:
- **Maximum Tree Depth:** `32` levels (`DEFAULT_MAX_DEPTH = 32`). Deeper subtrees are truncated into an `ErrorBoundaryNode`.
- **Maximum Total Node Count:** `4000` nodes per frame (`DEFAULT_MAX_NODES = 4000`). If rendering huge series/chapter lists, paginate or filter visible rows instead of dumping thousands of nodes in one frame.
- **`GridNode` Bounds:** `columns` is clamped to `1..12`; `minItemWidthDp` is clamped to `72f..600f` (defaulting to `156f` if non-finite).
- **Deterministic IDs:** Assign stable, meaningful `id` strings to interactive controls and reorderable children so focus, cursor position, scroll offsets, and drag state survive UI updates smoothly.

---

## 13. Testing Interactive Sessions

Interactive sessions run on standard Kotlin `Flow`s and can be unit-tested in pure JVM tests with `kotlinx-coroutines-test` by implementing a lightweight fake `UiSessionContext`:

- Assert the initial rendered `UiNodeTree` structure and node IDs.
- Send synthetic `UiEvent`s (`Click`, `TextChanged`, `ItemsReordered`, `ShortcutTriggered`, `FilesDropped`, `OptionSelected`, `DialogAction`) through a `MutableSharedFlow<UiEvent>`.
- Verify state transitions, `completeSession` / `cancelSession`, `updateSetting` / `clearSetting`, `openInteractiveTool` navigation calls, `secrets` storage, and `networkClient.request` payloads.

Reference implementations:
- Full multi-pane chapter workspace + local repo & grid reordering + secrets + HTTP webhook: [`InteractiveChapterPublisherDemo.kt`](../plugins/completeExample/src/main/kotlin/org/wip/complete/InteractiveChapterPublisherDemo.kt)
- Full spatial compositor & vector canvas: [`InteractiveBoardCompositorDemo.kt`](../plugins/completeExample/src/main/kotlin/org/wip/complete/InteractiveBoardCompositorDemo.kt)
- Automated session & capability tests: [`CompleteExampleTest.kt`](../plugins/completeExample/src/test/kotlin/org/wip/complete/CompleteExampleTest.kt)
