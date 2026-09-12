# Central Shortcut Management System

The **PluginToolkit** desktop application includes a centralized, context-aware Shortcut Management System designed to handle keyboard shortcuts, pointer gestures, and hybrid chords, detect binding conflicts, allow multi-trigger combinations, provide seamless user customization matching the application's standard settings design, and persist preferences within the application settings.

---

## 1. Overview & Key Capabilities

As features expand (especially in complex visual environments like the Flow Editor), input interactions rapidly proliferate:
- Board panning (e.g. `Right Drag` or `Middle Drag`) vs. box selection vs. connection point dragging vs. wire branching.
- Keyboard toggles (e.g. `P` or `B` for brush tool, `W` for wash, `I` for eyedropper, `M` for structured wire mode).
- Deletion shortcuts (e.g. `Del` or `Backspace` for deleting selected nodes).
- Universal confirmation bypass (`Shift + Click` or user-defined shortcut across destructive dialogs and prompts).
- Dynamic queriable menus: the Flow Editor's "Canvas Shortcuts & Controls" info card directly queries the system to display current user-configured shortcuts.

### Core Architectural Features:
1. **Multi-Trigger Support:** Actions can have multiple alternative triggers (e.g., Canvas Pan works with both `Right Drag` and `Middle Drag`; Paint tool works with both `P` and `B`).
2. **Action-Enforced Input Modes (`ShortcutInputMode`):**
   - The required input medium (`Keyboard`, `Pointer`, or `Hybrid`) is defined at the action declaration level, ensuring actions requiring pointer access cannot be inadvertently converted into keyboard-only triggers.
   - **Keyboard Actions:** Pure keyboard keypresses (`ShortcutKey` + modifiers: `Ctrl`, `Shift`, `Alt`, `Meta`). Supports **ANY** key on the keyboard.
   - **Pointer Actions:** Mouse buttons (`Left`, `Right`, `Middle`, etc.) + gestures (`Click`, `DoubleClick`, `Drag`, `Wheel`) + modifiers.
   - **Hybrid Actions:** Key held down while executing a pointer gesture (e.g., `L + Left Click`).
3. **Contextual Situations (`ShortcutSituation`):** Actions are scoped to specific operational contexts (e.g., `FlowBoard`, `FlowConnectionPoint`, `FlowSelection`, `Global`, `Settings`). Conflicts are strictly evaluated within the same situation, preventing false positive collisions across disjoint UI contexts.
4. **Standard Settings UI & Global Search:** `ShortcutsSettingsView` strictly follows the design pattern of other settings views (`SettingsGroup`, `SettingsItem`), and all shortcuts and gestures are indexed by the global broad settings search.
5. **Interactive Remapping & Segmented Controls:** `RemapShortcutDialog` enforces the declared input mode, provides `SingleChoiceSegmentedButtonRow` for mouse buttons, gesture types, and modifiers, and captures keyboard combinations by direct listening until key release.
6. **Canvas Gesture Binding:** `boardPanGesture` and `boardSelectionBoxGesture` bind directly to active user shortcuts in `ShortcutManager`, allowing users to customize or swap pan and selection interactions.
7. **Dynamic Queriable API:** UI surfaces (such as the canvas info card) query `ShortcutManager` to display real-time formatted triggers (`formatEffectiveTriggers`, `formatEffectiveTriggersCompact`).

---

## 2. Core Architecture & Models

### A. Domain Models (`:shared:core`)
Located in package `org.wip.plugintoolkit.features.shortcuts.model`:

- **`ShortcutKey`**:
  ```kotlin
  @Serializable
  data class ShortcutKey(val code: String, val label: String = code)
  ```
  Provides common constants (`A`..`Z`, `Zero`..`Nine`, `F1`..`F12`, `Escape`, `Delete`, `Backspace`, `Enter`, `Space`, etc.) and `fromCode(code, label)` to support **ANY key on the keyboard**.

- **`ShortcutTrigger`**:
  ```kotlin
  @Serializable
  data class ShortcutTrigger(
      val key: ShortcutKey? = null,
      val isCtrl: Boolean = false,
      val isShift: Boolean = false,
      val isAlt: Boolean = false,
      val isMeta: Boolean = false,
      val pointerButton: ShortcutPointerButton = ShortcutPointerButton.None,
      val gesture: ShortcutGesture = ShortcutGesture.None
  )
  ```
  Canonical formatting via `.format()`: `[ Shift + Left + Click ]`, `[ Right + Drag ]`, `[ P ]`, `[ Ctrl + Z ]`, `[ L + Left + Click ]`.

- **`ShortcutAction` & `ShortcutActionId`**:
  Defines an action, its contextual `ShortcutSituation`, localized title/description strings, and a list of `defaultTriggers: List<ShortcutTrigger>`.
  Standard IDs:
  - `ShortcutActionId.SKIP_CONFIRMATION` (`Global`)
  - `ShortcutActionId.FLOW_PAN_CANVAS` (`FlowBoard`)
  - `ShortcutActionId.FLOW_ZOOM_CANVAS` (`FlowBoard`)
  - `ShortcutActionId.FLOW_SELECT_NODE` (`FlowBoard`)
  - `ShortcutActionId.FLOW_BOX_SELECT` (`FlowBoard`)
  - `ShortcutActionId.FLOW_STRUCTURED_MODE` (`FlowBoard`)
  - `ShortcutActionId.FLOW_PAINT_TOOL` (`FlowBoard`)
  - `ShortcutActionId.FLOW_WASH_TOOL` (`FlowBoard`)
  - `ShortcutActionId.FLOW_EYEDROPPER` (`FlowBoard`)
  - `ShortcutActionId.FLOW_UNDO` (`FlowBoard`)
  - `ShortcutActionId.FLOW_REDO` (`FlowBoard`)
  - `ShortcutActionId.FLOW_MOVE_NODE` (`FlowSelection`)
  - `ShortcutActionId.FLOW_DELETE_SELECTED` (`FlowSelection`)
  - `ShortcutActionId.FLOW_CONNECT_PORT` (`FlowConnectionPoint`)
  - `ShortcutActionId.FLOW_DETACH_CONNECTION` (`FlowConnectionPoint`)
  - `ShortcutActionId.FLOW_BRANCH_WIRE` (`FlowConnectionPoint`)
  - `ShortcutActionId.FLOW_MOVE_POINT` (`FlowConnectionPoint`)
  - `ShortcutActionId.FLOW_CREATE_RAMIFICATION` (`FlowConnectionPoint`)
  - `ShortcutActionId.JOB_FORCE_CANCEL` (`JobTerminal`)
  - `ShortcutActionId.SETTINGS_SCALE_2X_STEP` (`Settings`)

- **`ShortcutSettings`**:
  ```kotlin
  @Serializable
  data class ShortcutSettings(
      val customBindings: Map<String, List<ShortcutTrigger>> = emptyMap(),
      val customRelativePriorities: Map<String, Int> = emptyMap(),
      val priorityMode: ShortcutPriorityMode = ShortcutPriorityMode.ZIndexAndPriority
  )
  ```
- **`ShortcutPriorityMode`**:
  - `ZIndexAndPriority` (default): Combines base situation priority, dynamic composable z-index/elevation offsets, and per-action relative priorities.
  - `PriorityOnly`: Ignores dynamic elevation/z-index overrides, strictly evaluating static base situation priority + relative action priority.
- **`ShortcutPriorities`**:
  Centralized "wall of values" for situation precedence and intra-situation relative priorities:
  ```kotlin
  object ShortcutPriorities {
      object Global {
          const val BASE = 0
          const val MODAL = 50
      }
      object Flow {
          const val BOARD = 10
          const val SELECTION = 20
          const val WIRE = 30
          const val NODE = 30
          const val CONNECTION_POINT = 40
      }
      object Tools {
          const val JOB_TERMINAL = 50
          const val SETTINGS = 50
      }
      object Relative {
          const val LOW = -5
          const val DEFAULT = 0
          const val NORMAL = 0
          const val HIGH = 5
          const val HIGHEST = 10
      }
  }
  ```
- **`ShortcutSituation`**:
  Carries `defaultPriority: Int` referencing `ShortcutPriorities`. Elements with higher priority take precedence when multiple situations are active simultaneously.

### B. Service & Mapping (`:shared:gui`)
Located in package `org.wip.plugintoolkit.features.shortcuts.logic` and `utils`:

- **`KeyMapping`**:
  Translates bidirectionally between Compose `androidx.compose.ui.input.key.Key` and domain `ShortcutKey`. Captures any keycode from key events.
- **`ShortcutManager`**:
  - `matches(...)`: Evaluates pointer or key events against all registered triggers for an action.
  - `matchesPointer(actionId, event, gesture, consume, allowConsumed)`: Evaluates pointer events. Rejects already consumed events (`allowConsumed = false`) and can atomically consume on match.
  - `eat(event, actionId)`: Consumes all pointer changes (`change.consume()`) and publishes live telemetry including effective action priority and active priority mode.
  - `isConsumed(event)`: Checks if any change has been eaten.
  - `tryConsumePointer(actionId, event, gesture)`: Matches and eats in a single atomic call.
  - `lastEatenEvent`: `StateFlow<EatenEventInfo?>` live telemetry exposing which action and zone ate the last pointer event.
  - `getPriorityMode()` / `setPriorityMode(mode)`: Controls priority resolution strategy (`ZIndexAndPriority` vs `PriorityOnly`).
  - `getEffectivePriority(situation)`: Resolves situation priority factoring in dynamic z-index / elevation overrides when `ZIndexAndPriority` is active.
  - `getEffectiveRelativePriority(actionId)`: Returns custom user-configured or action default relative priority.
  - `getEffectiveActionPriority(actionId)`: Computes composite priority `getEffectivePriority(situation) + getEffectiveRelativePriority(actionId)`.
  - `setSituationElevation(situation, elevation)`: Dynamically associates composable z-index / elevation with a situation.
  - `findConflicts()` and `checkConflictForTrigger(actionId, trigger)`: Intra-situation collision validation.
  - `findActiveZoneConflicts()`: Evaluates collisions and modifier subsumption across both cross-zone and intra-element scopes. In intra-element scenarios, `relativePriority` resolves ties: higher relative priority dominates, marking the lower one as cleanly shadowed (`isShadowed = true`) rather than an unmanaged conflict.
  - `formatEffectiveTriggers(actionId)`: E.g., `[ Right + Drag ] / [ Middle + Drag ]`.
  - `formatEffectiveTriggersCompact(actionId)`: E.g., `Right Drag / Middle Drag` or `P / B`.
  - `getActionsForSituation(situation)`: Queriable listing for contextual menus and tooltips.

---

## 3. UI Components & Settings

Located in package `org.wip.plugintoolkit.features.shortcuts.ui`:

### `ShortcutsSettingsView`
Accessible via **Settings -> Shortcuts**:
- **Priority Resolution System Section:** Dedicated dropdown allowing users to select between **Z-Index & Priority (Dynamic Elevation)** and **Static Priority Only**.
- **Action Priority & Relative Offset Subtitle:** Each action card indicates its total effective priority and relative modifier (e.g., `Priority: 45 (relative +5)`).
- **Native Settings Layout:** Uses `SettingsGroup` and `SettingsItem` matching the rest of the application settings.
- **Sidebar Search Integration:** Reacts to the global search input via `LocalSettingsSearchQuery.current` without duplicate search bars.
- **Interactive Badges:** Formatted triggers are rendered as clickable chips that open `RemapShortcutDialog`.
- **Conflict Warning Banner:** Flags collisions at the top of the settings page with exact conflict details.
- **Title Bar Reset Button:** A dedicated "Restore All to Defaults" button is located to the right of the headline title, active whenever customized bindings or relative priorities exist.
- **Per-Action Reset:** Quick reset icon button on each customized action row resetting both triggers and relative priority.

### `RemapShortcutDialog`
- **Enforced Input Mode:** Input mode is strictly dictated by the action definition (`ShortcutAction.inputMode`).
- **Relative Priority Stepper:** Interactive `[-]` and `[+]` counter to tune intra-element relative precedence.
- **Multiple Triggers:** Add alternative shortcuts or remove secondary triggers.
- **Interactive Key Listening:** Keyboard shortcuts are recorded dynamically by listening to key presses and modifiers until the first pressed key is released.
- **Segmented Pointer Controls:** Uses `SingleChoiceSegmentedButtonRow` for mouse buttons (`Left`, `Middle`, `Right`), gesture types (`Click`, `DoubleClick`, `Drag`, `Wheel`), and modifiers (`None`, `Ctrl`, `Shift`, `Alt`).
- **Live Preview & Conflict Detection:** Real-time feedback alerting the user before saving if a trigger collides with another action in the same situation.

### `LiveShortcutZoningOverlay`
Accessible via **Settings -> Debug -> Live Shortcut Zoning**:
- Non-intrusive floating HUD displaying active shortcut situations/zones and pointer hover zones in real time.
- **Priority Mode Badge:** Real-time badge in the HUD header indicating whether `Z-Index` or `Static` priority resolution is currently active.
- **Live "Eaten By" Telemetry:** Real-time row displaying which action and situation currently ate pointer input (e.g. `Eaten by: Connection Point (FLOW_MOVE_POINT)`).
- **Hierarchical Shadowing Display:** Distinguishes between unmanaged peer collisions and intentional hierarchical / relative priority overrides (`Move Connection Point (P:45) eats Left Drag → shadows Move Connection Point (P:40)`).
- Completely interaction-transparent (`pointerInput` non-consuming / pass-through) so underlying canvas workflows remain unaffected.

---

## 4. Developer Integration Guide

### Checking Keyboard Shortcuts
```kotlin
Box(
    modifier = Modifier.onKeyEvent { keyEvent ->
        if (keyEvent.type == KeyEventType.KeyDown) {
            val isPaintTriggered = shortcutManager?.isKeyActionTriggered(
                actionId = ShortcutActionId.FLOW_PAINT_TOOL,
                key = keyEvent.key,
                isCtrl = keyEvent.isCtrlPressed,
                isShift = keyEvent.isShiftPressed,
                isAlt = keyEvent.isAltPressed
            ) ?: false

            if (isPaintTriggered) {
                togglePaintTool()
                true
            } else false
        } else false
    }
)
```

### Checking Pointer Gestures & Modifiers
```kotlin
val isConfirmationBypassed = shortcutManager?.isActionTriggered(
    actionId = ShortcutActionId.SKIP_CONFIRMATION,
    modifiers = event.keyboardModifiers,
    button = ShortcutPointerButton.Left,
    gesture = ShortcutGesture.Click
) ?: event.keyboardModifiers.isShiftPressed
```

### Populating Dynamic Contextual Tooltips
```kotlin
val panBadgeText = shortcutManager?.formatEffectiveTriggersCompact(ShortcutActionId.FLOW_PAN_CANVAS)
    ?: "Right / Middle Drag"

ShortcutRow(
    badgeText = panBadgeText,
    description = stringResource(Res.string.flow_info_pan_desc)
)
```

### Gesture Hierarchy & Event Consumption
On the canvas (`BoardCanvas.kt`), gestures are chained such that interactive element gestures (moving junctions, waypoints, drawing connections via `boardPointerEventGesture`) run before background operations (canvas selection box via `boardSelectionBoxGesture`). Whenever an interactive element gesture handles a move or press, it calls `event.changes.forEach { it.consume() }`. The subsequent selection box gesture inspects `startChange.isConsumed` and hovered connection states to ensure box selection never triggers concurrently with connection point movements.

---

## 5. Automated Tests

The test suite in [ShortcutManagerTest.kt](file:///c:/Users/sgroo/AndroidStudioProjects/CMP_desktop_test/shared/gui/src/jvmTest/kotlin/org/wip/plugintoolkit/features/shortcuts/ShortcutManagerTest.kt) validates:
1. Multi-trigger default actions and canonical formatting (`[ Right + Drag ] / [ Middle + Drag ]`, `[ P ] / [ B ]`, `[ Del ] / [ Backspace ]`).
2. Multi-trigger pointer matching (`Right Drag` and `Middle Drag` match `FLOW_PAN_CANVAS`, `Left Drag` does not).
3. Keyboard key matching with Compose `Key` (`Key.P` and `Key.B` trigger `FLOW_PAINT_TOOL`, `Key.Z` with Ctrl triggers `FLOW_UNDO`).
4. Conflict detection within the same situation (`FlowConnectionPoint`).
5. Absence of false conflicts across separate situations sharing identical gestures when not active concurrently.
6. Trigger subsumption conflict detection (`Left Drag` vs `Ctrl + Left Drag` or `Alt + Left Drag`) where modifier subsets shadow one another.
7. Active zone collision detection (`findActiveZoneConflicts`) across concurrently active situations.
8. Active situation and pointer situation tracking.
9. Full customization, persistence, and reset lifecycle.
10. Dynamic querying of actions by situation (`getActionsForSituation`).
11. Polymorphic JSON serialization/deserialization with `AppSettings`.
12. Centralized priority wall of values (`ShortcutPriorities`) and situation precedence hierarchy.
13. Pointer event consumption (`eat`), live telemetry broadcasting, and rejection of consumed events.
14. Dynamic situation elevation / z-index overrides (`setSituationElevation`).
15. Intra-element relative priority tie-breaking (`testRelativePriorityWithinSameElement`) verifying shadow resolution when two actions share the same element and base elevation.
16. Priority resolution mode switching (`testPriorityResolutionModes`) between `PriorityOnly` and `ZIndexAndPriority`.

