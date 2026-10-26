# Interactive Plugins & Server-Driven UI (SDUI)

**Version:** 2.2.0  
**Status:** Active Production  
**Target Modules:** `plugin-api`, `shared:logic`, `shared:gui`

---

## 1. Executive Overview

Interactive Plugins empower developers to build dynamic, reactive, full-featured user interfaces (including live forms, dashboards, and studio-grade vector drawing boards) while remaining **completely decoupled from Jetpack Compose Multiplatform, Skiko, and AWT/Swing**.

All user interface layouts and mutations are represented as declarative, serializable Kotlin data structures (`UiNode`, `UiModifier`, and `UiEvent`) exchanged across an asynchronous coroutine boundary (`InteractiveUiProcessor`). The host application (`shared:gui`) renders these AST trees safely inside a sandboxed viewport with pre-composition AST sanitization, high-frequency local gesture handling, and structured lifecycle cleanup.

```
┌─────────────────────────────────────────────────────────────┐
│                 Plugin Process / Sandbox                    │
│                                                             │
│  @InteractiveTool                                           │
│  InteractiveUiProcessor ──[ Flow<UiNodeTree> ]──────────┐   │
│                         ◄──[ Flow<UiEvent> ]────────┐   │   │
└─────────────────────────────────────────────────────│───│───┘
                                                      │   │
                                    Dual-Lane Channels│   │ AST Tree
                                                      ▼   │
┌─────────────────────────────────────────────────────────│───┐
│               Host Application (shared:logic)           │   │
│                                                         │   │
│  PluginUiSessionManager                                 │   │
│    ├── Confinement to Dispatchers.Default               │   │
│    ├── Lossless (Clicks/Paths) & Lossy (Debounce) Lanes │   │
│    └── Pre-Composition AST Sanitizer (UiAstSanitizer) ◄─┘   │
└─────────────────────────────────────────────────────────────┘
                                  │
                                  ▼ Safe Immutable AST
┌─────────────────────────────────────────────────────────────┐
│               Host Renderer (shared:gui)                    │
│                                                             │
│  Runner -> Interactive Tab                                  │
│    ├── InteractiveToolsScreen / InteractiveToolsViewModel   │
│    ├── PluginCustomUiCard / DynamicUiRenderer               │
│    │     ├── 120 FPS Local Gesture Machine                  │
│    │     ├── Catmull-Rom & RDP Vector Splines               │
│    │     └── Out-of-Band Coil 3 PluginFileSystem Bypass    │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Capabilities vs. Interactive Tools

A critical distinction exists between automated plugin **Capabilities** and **Interactive Tools**:

| Dimension | Automated Capabilities | Interactive Tools |
| :--- | :--- | :--- |
| **Execution Model** | One-shot request/response (`DataProcessor.process(...)` or suspend function). | Long-running bidirectional coroutine session (`InteractiveUiProcessor.openUiSession(...)`). |
| **User Interaction** | Headless / automated batch execution without user intervention. | Continuous, real-time bidirectional user interaction (clicks, drags, keyboard inputs). |
| **State Lifespan** | Ephemeral: executes parameters, writes outputs, and terminates. | Persistent state machine active as long as the session viewport is mounted. |
| **Visual Flow Suitability** | **Supported:** Primary building blocks for automated flow pipelines. | **NOT Allowed:** Cannot be placed in flows because flows execute headlessly without user intervention. |
| **Context Annotation** | `@Capability(context = ANY / FLOW_ONLY / STANDALONE_ONLY)` | `@InteractiveTool` or `@Capability(isInteractive = true, context = INTERACTIVE_ONLY)` |
| **Host Navigation** | **Runner -> Capabilities** | **Runner -> Interactive** |

### Why Visual Flows Cannot Use Interactive Tools
Visual Flows are batch execution pipelines orchestrated by `FlowEngine`. Flow nodes run asynchronously in background workers (`JobWorker`), processing input connections and forwarding outputs to downstream nodes without requiring a human operator at the screen.

Because interactive SDUI tools require real-time human interaction (drawing vector strokes, filling forms, clicking custom buttons), executing an interactive tool inside an automated flow would stall the entire background pipeline indefinitely waiting for user input. Therefore:
1. `Capability.isInteractive` and `CapabilityContext.INTERACTIVE_ONLY` are **strictly excluded** from the Flow Editor palette (`PaletteSidebar`, `FlowEditorView`).
2. Flow readiness checkers (`Node.CapabilityNode.isReady`, `Flow.isBroken`) treat any interactive capability node as broken and non-executable.
3. `FlowEngine` and `JobWorker` throw an `IllegalStateException` if an interactive tool is submitted as a headless job.

---

## 3. Host Navigation: The "Interactive" Tab

The Host application provides a dedicated workspace for running interactive plugin GUIs, located under the **Runner** sidebar section:

- **Runner -> Capabilities:** Tests automated headless capabilities with input parameter fields and job progress trackers.
- **Runner -> Flows:** Orchestrates and runs multi-node automated visual pipelines.
- **Runner -> Interactive:** Dedicated full-viewport canvas for interactive SDUI plugins.

### Interactive Viewport Features
1. **Interactive Tools Sidebar:** Lists all installed plugins that expose interactive tools. Includes real-time search filtering by plugin name, tool name, or description.
2. **Dedicated Canvas Viewport (`InteractiveToolsScreen`):** Full-width, full-height container hosting `PluginCustomUiCard`.
3. **Session Status Pill:** Displays live connectivity state (`Active Session` with green indicator or `Idle`).
4. **Session Controls:**
   - **Restart Session (`action_restart_session`):** Resets the plugin's state machine and requests a fresh initial UI node tree.
   - **Close Session (`action_close_session`):** Cancels the coroutine session handle, freeing background resources.
   - **Launch Session (`action_launch_session`):** Launches or reconnects to an interactive session from an idle state.
5. **Automatic Lifecycle Cleanup:** Backed by Compose's `DisposableEffect(handle.sessionId)`, navigating away from the screen or unmounting the card automatically closes the event channels and cancels coroutine scopes on `Dispatchers.Default`, eliminating zombie coroutine leaks.

---

## 4. Plugin Developer Guide

### 4.1 Declaring an Interactive Tool

Plugins declare interactive tools on their main class using either `@InteractiveTool` or `@Capability(isInteractive = true)`:

```kotlin
package com.example.myplugin

import org.wip.plugintoolkit.api.annotations.InteractiveTool
import org.wip.plugintoolkit.api.annotations.PluginInfo
import org.wip.plugintoolkit.api.sdui.InteractiveUiProcessor

@PluginInfo(
    id = "com.example.myplugin",
    name = "Vector Sketch & Forms",
    version = "1.0.0",
    description = "Interactive drawing and forms showcase"
)
class MyInteractivePlugin : InteractiveUiProcessor {

    @InteractiveTool(
        name = "sketchpad",
        description = "Interactive vector canvas with live controls"
    )
    fun sketchpad(): String {
        return "Interactive session active"
    }

    override fun openUiSession(
        context: UiSessionContext,
        events: Flow<UiEvent>
    ): Flow<UiNodeTree> = channelFlow {
        // Session implementation
    }
}
```

The KSP code generator automatically configures:
- `isInteractive = true`
- `context = CapabilityContext.INTERACTIVE_ONLY`
- Generates `getInteractiveProcessor()` on `PluginEntry` returning `Result.success(this)`.

---

### 4.2 Building SDUI Layouts with the Kotlin DSL

The `plugin-api` provides a declarative, type-safe DSL for constructing `UiNodeTree` layouts without importing any UI framework:

```kotlin
import org.wip.plugintoolkit.api.sdui.dsl.*
import org.wip.plugintoolkit.api.sdui.SemanticColor

fun renderUi(counter: Int, text: String): UiNodeTree = uiTree {
    column(modifier = fillMaxWidth().padding(16.0)) {
        text("Interactive Counter: $counter", style = TextStyle.TITLE_LARGE)
        
        row(modifier = fillMaxWidth().padding(8.0), horizontalArrangement = Arrangement.CENTER) {
            button("Increment", onClick = "btn_inc", color = SemanticColor.PRIMARY)
            button("Reset", onClick = "btn_reset", color = SemanticColor.SECONDARY)
        }

        textInput(
            id = "input_notes",
            label = "Live Notes",
            text = text,
            placeholder = "Type here..."
        )
    }
}
```

#### Supported UI Primitives:
- **Containers:** `column`, `row`, `box`, `card`, `scrollableColumn`.
- **Display Nodes:** `text`, `image`, `icon`, `divider`, `badge`.
- **Inputs & Controls:** `button`, `textInput`, `slider`, `toggle`, `dropdown`.
- **Vector Canvas:** `boardNode`.
- **Modifiers:** `fillMaxWidth()`, `fillMaxHeight()`, `padding()`, `weight()`, `alignment()`, `enabled()`, `visible()`.

---

### 4.3 High-Frequency Vector Canvas (`BoardNode`)

To eliminate latency and brush lag, `BoardNode` uses a local-first client-side architecture:
1. Pointer movements at 120 FPS are rendered directly on the Compose UI canvas.
2. In-progress strokes apply **Catmull-Rom cubic spline interpolation** and **Ramer-Douglas-Peucker (RDP) polygon simplification** locally.
3. Upon pointer release (`onPointerUp`), the finalized stroke is dispatched across the coroutine boundary as `UiEvent.PathCommitted`.
4. The plugin adds the committed stroke to its persistent layers and emits an updated `UiNodeTree`.

```kotlin
boardNode(
    id = "main_canvas",
    modifier = fillMaxWidth().weight(1.0),
    activeTool = BoardTool.BRUSH,
    brushWidth = 4.0,
    brushColor = SemanticColor.PRIMARY,
    smoothing = true,
    layers = layers
)
```

---

### 4.4 Dual-Lane Session Coroutines

The interactive event loop in `openUiSession` handles incoming `UiEvent` items:

```kotlin
override fun openUiSession(
    context: UiSessionContext,
    events: Flow<UiEvent>
): Flow<UiNodeTree> = channelFlow {
    var count = 0
    var note = ""
    val layers = mutableListOf<BoardLayer>()

    // Emit initial layout
    send(buildTree(count, note, layers))

    // Collect user events
    events.collect { event ->
        when (event) {
            is UiEvent.Click -> {
                if (event.nodeId == "btn_inc") count++
                if (event.nodeId == "btn_reset") count = 0
                send(buildTree(count, note, layers))
            }
            is UiEvent.TextChanged -> {
                note = event.text
                send(buildTree(count, note, layers))
            }
            is UiEvent.PathCommitted -> {
                layers.add(BoardLayer.PathLayer(id = event.pathData.id, stroke = event.pathData))
                send(buildTree(count, note, layers))
            }
            else -> {}
        }
    }
}
```

---

## 5. Sandboxing & Fault Tolerance

1. **Pre-Composition Sanitization:** Before any `UiNodeTree` reaches Jetpack Compose, `UiAstSanitizer.sanitize()` validates tree depth (`maxDepth = 32`), node count (`maxNodes = 1000`), duplicate `nodeId` keys, and numeric slider constraints. Malformed branches are safely replaced with an inline `ErrorBoundaryNode` before composition, preventing host window crashes.
2. **Crash Resilience:** Uncaught exceptions inside a plugin's event reducer are caught by `PluginUiSessionManager`'s `CoroutineExceptionHandler` on `Dispatchers.Default`, emitting a fallback error card with a retry button instead of crashing the desktop app.
3. **Out-of-Band Asset Streaming:** Raster images are referenced via `AssetReference` handles. The host's Coil 3 `PluginAssetFetcher` resolves image paths directly through `PluginFileSystem` to GPU memory, completely bypassing JSON serialization.
