package org.wip.plugintoolkit.features.shortcuts

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutPriorities
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence
import org.wip.plugintoolkit.features.settings.logic.SettingsRepository
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.DebugSettings
import org.wip.plugintoolkit.features.shortcuts.logic.ShortcutManager
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutActionId
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutGesture
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutInputMode
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutKey
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutPointerButton
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutPriorityMode
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutSettings
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutSituation
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutTrigger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShortcutManagerTest {

    private class FakeSettingsPersistence(
        var currentSettings: AppSettings = AppSettings()
    ) : SettingsPersistence {
        override suspend fun load(): AppSettings = currentSettings
        override suspend fun save(settings: AppSettings) {
            currentSettings = settings
        }
        override fun getSettingsDir(): String = ""
        override fun getJobsDir(): String = ""
        override fun openLogFolder() {}
        override fun openLatestLog() {}
    }

    @Test
    fun testDefaultTriggersAndFormatting() {
        val manager = ShortcutManager()

        // Skip confirmation (Global)
        val skipConfTrigger = manager.getEffectiveTrigger(ShortcutActionId.SKIP_CONFIRMATION)
        assertEquals("[ Shift + Left + Click ]", skipConfTrigger.format())

        // Pan canvas (FlowBoard) has 2 triggers: Right Drag and Middle Drag
        val panTriggers = manager.getEffectiveTriggers(ShortcutActionId.FLOW_PAN_CANVAS)
        assertEquals(2, panTriggers.size)
        assertEquals("[ Right + Drag ]", panTriggers[0].format())
        assertEquals("[ Middle + Drag ]", panTriggers[1].format())
        assertEquals("[ Right + Drag ] / [ Middle + Drag ]", manager.formatEffectiveTriggers(ShortcutActionId.FLOW_PAN_CANVAS))
        assertEquals("Right Drag / Middle Drag", manager.formatEffectiveTriggersCompact(ShortcutActionId.FLOW_PAN_CANVAS))

        // Paint tool has P and B
        val paintTriggers = manager.getEffectiveTriggers(ShortcutActionId.FLOW_PAINT_TOOL)
        assertEquals(2, paintTriggers.size)
        assertEquals("[ P ]", paintTriggers[0].format())
        assertEquals("[ B ]", paintTriggers[1].format())
        assertEquals("P / B", manager.formatEffectiveTriggersCompact(ShortcutActionId.FLOW_PAINT_TOOL))

        // Delete selected has Del and Backspace
        val deleteTriggers = manager.getEffectiveTriggers(ShortcutActionId.FLOW_DELETE_SELECTED)
        assertEquals(2, deleteTriggers.size)
        assertEquals("[ Del ]", deleteTriggers[0].format())
        assertEquals("[ Backspace ]", deleteTriggers[1].format())
        assertEquals("Del / Backspace", manager.formatEffectiveTriggersCompact(ShortcutActionId.FLOW_DELETE_SELECTED))
    }

    @Test
    fun testMultiTriggerPointerMatching() {
        val manager = ShortcutManager()

        // FLOW_PAN_CANVAS matches Right Drag
        assertTrue(
            manager.matches(
                actionId = ShortcutActionId.FLOW_PAN_CANVAS,
                isCtrl = false,
                isShift = false,
                isAlt = false,
                button = ShortcutPointerButton.Right,
                gesture = ShortcutGesture.Drag
            )
        )

        // FLOW_PAN_CANVAS also matches Middle Drag
        assertTrue(
            manager.matches(
                actionId = ShortcutActionId.FLOW_PAN_CANVAS,
                isCtrl = false,
                isShift = false,
                isAlt = false,
                button = ShortcutPointerButton.Middle,
                gesture = ShortcutGesture.Drag
            )
        )

        // FLOW_PAN_CANVAS does NOT match Left Drag (that's box selection)
        assertFalse(
            manager.matches(
                actionId = ShortcutActionId.FLOW_PAN_CANVAS,
                isCtrl = false,
                isShift = false,
                isAlt = false,
                button = ShortcutPointerButton.Left,
                gesture = ShortcutGesture.Drag
            )
        )
    }

    @Test
    fun testKeyboardKeyMatching() {
        val manager = ShortcutManager()

        // Paint tool triggers with Key.P or Key.B without Ctrl
        assertTrue(
            manager.isKeyActionTriggered(
                actionId = ShortcutActionId.FLOW_PAINT_TOOL,
                key = Key.P,
                isCtrl = false,
                isShift = false,
                isAlt = false
            )
        )
        assertTrue(
            manager.isKeyActionTriggered(
                actionId = ShortcutActionId.FLOW_PAINT_TOOL,
                key = Key.B,
                isCtrl = false,
                isShift = false,
                isAlt = false
            )
        )
        // With Ctrl it does not match
        assertFalse(
            manager.isKeyActionTriggered(
                actionId = ShortcutActionId.FLOW_PAINT_TOOL,
                key = Key.P,
                isCtrl = true,
                isShift = false,
                isAlt = false
            )
        )

        // Undo triggers with Ctrl+Z
        assertTrue(
            manager.isKeyActionTriggered(
                actionId = ShortcutActionId.FLOW_UNDO,
                key = Key.Z,
                isCtrl = true,
                isShift = false,
                isAlt = false
            )
        )
        // Undo does not trigger with Z alone
        assertFalse(
            manager.isKeyActionTriggered(
                actionId = ShortcutActionId.FLOW_UNDO,
                key = Key.Z,
                isCtrl = false,
                isShift = false,
                isAlt = false
            )
        )

        // Redo triggers with Ctrl+Y or Ctrl+Shift+Z
        assertTrue(
            manager.isKeyActionTriggered(
                actionId = ShortcutActionId.FLOW_REDO,
                key = Key.Y,
                isCtrl = true,
                isShift = false,
                isAlt = false
            )
        )
        assertTrue(
            manager.isKeyActionTriggered(
                actionId = ShortcutActionId.FLOW_REDO,
                key = Key.Z,
                isCtrl = true,
                isShift = true,
                isAlt = false
            )
        )
    }

    @Test
    fun testNoConflictAcrossDifferentSituations() {
        val manager = ShortcutManager()

        // FLOW_BOX_SELECT is in FlowBoard with [ Left + Drag ]
        // FLOW_MOVE_NODE is in FlowSelection with [ Left + Drag ]
        // FLOW_CONNECT_PORT is in FlowConnectionPoint with [ Left + Drag ]
        // Because their situations are separate, no conflicts exist in default catalog
        val conflicts = manager.findConflicts()
        assertTrue(conflicts.isEmpty(), "Expected no conflicts in default catalog despite shared [ Left + Drag ] across separate situations")
    }

    @Test
    fun testConflictDetectionInSameSituation() {
        val manager = ShortcutManager()

        // Assigning [ Alt + Left Drag ] to FLOW_MOVE_POINT in FlowConnectionPoint
        // conflicts with FLOW_CREATE_RAMIFICATION (which also has [ Alt + Left Drag ] in FlowConnectionPoint)
        val conflictingTrigger = ShortcutTrigger(
            isAlt = true,
            pointerButton = ShortcutPointerButton.Left,
            gesture = ShortcutGesture.Drag
        )

        val conflict = manager.checkConflictForTrigger(ShortcutActionId.FLOW_MOVE_POINT, conflictingTrigger)
        assertNotNull(conflict)
        assertEquals(ShortcutSituation.FlowConnectionPoint, conflict.situation)
        assertEquals(ShortcutActionId.FLOW_MOVE_POINT, conflict.action1.id)
        assertEquals(ShortcutActionId.FLOW_CREATE_RAMIFICATION, conflict.action2.id)
    }

    @Test
    fun testCustomBindingAndResetLifecycle() = runTest {
        val persistence = FakeSettingsPersistence()
        val repository = SettingsRepository(persistence, backgroundScope)
        repository.isLoaded.first { it }

        val manager = ShortcutManager(repository)

        assertFalse(manager.isCustomized(ShortcutActionId.FLOW_PAINT_TOOL))

        // Remap paint tool to [ K ]
        val customTrigger = ShortcutTrigger(key = ShortcutKey.fromCode("K"))
        manager.updateBindings(ShortcutActionId.FLOW_PAINT_TOOL, listOf(customTrigger))

        assertTrue(manager.isCustomized(ShortcutActionId.FLOW_PAINT_TOOL))
        assertEquals("[ K ]", manager.formatEffectiveTriggers(ShortcutActionId.FLOW_PAINT_TOOL))

        // Check matching
        assertTrue(
            manager.isKeyActionTriggered(
                actionId = ShortcutActionId.FLOW_PAINT_TOOL,
                key = Key.K,
                isCtrl = false,
                isShift = false,
                isAlt = false
            )
        )
        assertFalse(
            manager.isKeyActionTriggered(
                actionId = ShortcutActionId.FLOW_PAINT_TOOL,
                key = Key.P,
                isCtrl = false,
                isShift = false,
                isAlt = false
            )
        )

        // Reset single binding
        manager.resetBinding(ShortcutActionId.FLOW_PAINT_TOOL)
        assertFalse(manager.isCustomized(ShortcutActionId.FLOW_PAINT_TOOL))
        assertEquals("[ P ] / [ B ]", manager.formatEffectiveTriggers(ShortcutActionId.FLOW_PAINT_TOOL))

        // Re-apply and then reset all to defaults
        manager.updateBindings(ShortcutActionId.FLOW_PAINT_TOOL, listOf(customTrigger))
        assertTrue(manager.isCustomized(ShortcutActionId.FLOW_PAINT_TOOL))

        manager.resetAllToDefaults()
        assertFalse(manager.isCustomized(ShortcutActionId.FLOW_PAINT_TOOL))
        assertEquals("[ P ] / [ B ]", manager.formatEffectiveTriggers(ShortcutActionId.FLOW_PAINT_TOOL))
    }

    @Test
    fun testQueriableApiForSituations() {
        val manager = ShortcutManager()

        val boardActions = manager.getActionsForSituation(ShortcutSituation.FlowBoard)
        assertTrue(boardActions.any { it.id == ShortcutActionId.FLOW_PAN_CANVAS })
        assertTrue(boardActions.any { it.id == ShortcutActionId.FLOW_ZOOM_CANVAS })
        assertTrue(boardActions.any { it.id == ShortcutActionId.FLOW_PAINT_TOOL })
        assertTrue(boardActions.any { it.id == ShortcutActionId.FLOW_WASH_TOOL })
        assertTrue(boardActions.any { it.id == ShortcutActionId.FLOW_EYEDROPPER })

        val pointActions = manager.getActionsForSituation(ShortcutSituation.FlowConnectionPoint)
        assertTrue(pointActions.any { it.id == ShortcutActionId.FLOW_MOVE_POINT })
        assertTrue(pointActions.any { it.id == ShortcutActionId.FLOW_CREATE_RAMIFICATION })

        val wireActions = manager.getActionsForSituation(ShortcutSituation.FlowWire)
        assertTrue(wireActions.any { it.id == ShortcutActionId.FLOW_DETACH_CONNECTION })
        assertTrue(wireActions.any { it.id == ShortcutActionId.FLOW_BRANCH_WIRE })
    }

    @Test
    fun testSerializationOfShortcutSettings() {
        val json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        val customTriggers = listOf(
            ShortcutTrigger(
                isCtrl = true,
                isAlt = true,
                pointerButton = ShortcutPointerButton.Right,
                gesture = ShortcutGesture.DoubleClick
            ),
            ShortcutTrigger(
                key = ShortcutKey.fromCode("X", "X")
            )
        )

        val settings = AppSettings(
            shortcuts = ShortcutSettings(
                customBindings = mapOf("test.action" to customTriggers),
                priorityMode = ShortcutPriorityMode.PriorityOnly,
                customRelativePriorities = mapOf("test.action" to 5)
            )
        )

        val encoded = json.encodeToString(AppSettings.serializer(), settings)
        val decoded = json.decodeFromString<AppSettings>(encoded)

        assertEquals(1, decoded.shortcuts.customBindings.size)
        assertEquals(ShortcutPriorityMode.PriorityOnly, decoded.shortcuts.priorityMode)
        assertEquals(5, decoded.shortcuts.customRelativePriorities["test.action"])
        val triggers = decoded.shortcuts.customBindings["test.action"]
        assertNotNull(triggers)
        assertEquals(2, triggers.size)

        val t1 = triggers[0]
        assertTrue(t1.isCtrl)
        assertTrue(t1.isAlt)
        assertFalse(t1.isShift)
        assertEquals(ShortcutPointerButton.Right, t1.pointerButton)
        assertEquals(ShortcutGesture.DoubleClick, t1.gesture)
        assertEquals("[ Ctrl + Alt + Right + Double-Click ]", t1.format())

        val t2 = triggers[1]
        assertEquals("X", t2.key?.code)
        assertEquals("[ X ]", t2.format())
    }

    @Test
    fun testSerializationOfDebugSettings() {
        val original = AppSettings(
            debug = DebugSettings(
                liveShortcutZoning = true,
                showPointerZone = true
            )
        )
        val json = Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(AppSettings.serializer(), original)
        val decoded = json.decodeFromString(AppSettings.serializer(), encoded)

        assertTrue(decoded.debug.liveShortcutZoning)
        assertTrue(decoded.debug.showPointerZone)
    }

    @Test
    fun testActionInputModes() {
        val manager = ShortcutManager()

        // Canvas pan and box select must be Pointer mode
        val panAction = manager.getAction(ShortcutActionId.FLOW_PAN_CANVAS)
        assertNotNull(panAction)
        assertEquals(ShortcutInputMode.Pointer, panAction.inputMode)

        val boxSelectAction = manager.getAction(ShortcutActionId.FLOW_BOX_SELECT)
        assertNotNull(boxSelectAction)
        assertEquals(ShortcutInputMode.Pointer, boxSelectAction.inputMode)

        // Paint tool and undo must be Keyboard mode
        val paintAction = manager.getAction(ShortcutActionId.FLOW_PAINT_TOOL)
        assertNotNull(paintAction)
        assertEquals(ShortcutInputMode.Keyboard, paintAction.inputMode)

        val undoAction = manager.getAction(ShortcutActionId.FLOW_UNDO)
        assertNotNull(undoAction)
        assertEquals(ShortcutInputMode.Keyboard, undoAction.inputMode)
    }

    @Test
    fun testExchangingPanAndBoxSelection() = runTest {
        val persistence = FakeSettingsPersistence()
        val repo = SettingsRepository(persistence, backgroundScope)
        repo.isLoaded.first { it }
        val manager = ShortcutManager(repo)

        // Defaults: Pan = Right/Middle Drag, BoxSelect = Left Drag
        assertTrue(manager.matches(ShortcutActionId.FLOW_PAN_CANVAS, false, false, false, button = ShortcutPointerButton.Right, gesture = ShortcutGesture.Drag))
        assertTrue(manager.matches(ShortcutActionId.FLOW_BOX_SELECT, false, false, false, button = ShortcutPointerButton.Left, gesture = ShortcutGesture.Drag))

        // Remap: Exchange Pan (to Left Drag) and BoxSelect (to Right Drag)
        manager.updateBindings(
            ShortcutActionId.FLOW_PAN_CANVAS,
            listOf(ShortcutTrigger(pointerButton = ShortcutPointerButton.Left, gesture = ShortcutGesture.Drag))
        )
        manager.updateBindings(
            ShortcutActionId.FLOW_BOX_SELECT,
            listOf(ShortcutTrigger(pointerButton = ShortcutPointerButton.Right, gesture = ShortcutGesture.Drag))
        )

        // Now Pan triggers on Left Drag!
        assertTrue(manager.matches(ShortcutActionId.FLOW_PAN_CANVAS, false, false, false, button = ShortcutPointerButton.Left, gesture = ShortcutGesture.Drag))
        assertFalse(manager.matches(ShortcutActionId.FLOW_PAN_CANVAS, false, false, false, button = ShortcutPointerButton.Right, gesture = ShortcutGesture.Drag))

        // And BoxSelect triggers on Right Drag!
        assertTrue(manager.matches(ShortcutActionId.FLOW_BOX_SELECT, false, false, false, button = ShortcutPointerButton.Right, gesture = ShortcutGesture.Drag))
        assertFalse(manager.matches(ShortcutActionId.FLOW_BOX_SELECT, false, false, false, button = ShortcutPointerButton.Left, gesture = ShortcutGesture.Drag))
    }

    @Test
    fun testActiveSituationsTracking() {
        val manager = ShortcutManager()
        // Default contains Global
        assertEquals(setOf(ShortcutSituation.Global), manager.activeSituations.value)

        manager.setActiveSituations(setOf(ShortcutSituation.FlowBoard, ShortcutSituation.FlowSelection))
        val active = manager.activeSituations.value
        assertTrue(active.contains(ShortcutSituation.Global))
        assertTrue(active.contains(ShortcutSituation.FlowBoard))
        assertTrue(active.contains(ShortcutSituation.FlowSelection))
        assertEquals(3, active.size)
    }

    @Test
    fun testPointerSituationTracking() {
        val manager = ShortcutManager()
        assertNull(manager.pointerSituation.value)

        manager.setPointerSituation(ShortcutSituation.FlowNode)
        assertEquals(ShortcutSituation.FlowNode, manager.pointerSituation.value)

        manager.setPointerSituation(ShortcutSituation.FlowConnectionPoint)
        assertEquals(ShortcutSituation.FlowConnectionPoint, manager.pointerSituation.value)

        manager.setPointerSituation(null)
        assertNull(manager.pointerSituation.value)
    }

    @Test
    fun testTriggerSubsumptionConflictDetection() {
        val leftDrag = ShortcutTrigger(pointerButton = ShortcutPointerButton.Left, gesture = ShortcutGesture.Drag)
        val ctrlLeftDrag = ShortcutTrigger(isCtrl = true, pointerButton = ShortcutPointerButton.Left, gesture = ShortcutGesture.Drag)
        val altLeftDrag = ShortcutTrigger(isAlt = true, pointerButton = ShortcutPointerButton.Left, gesture = ShortcutGesture.Drag)
        val ctrlShiftLeftDrag = ShortcutTrigger(isCtrl = true, isShift = true, pointerButton = ShortcutPointerButton.Left, gesture = ShortcutGesture.Drag)

        // Exact comparison (intra-situation catalog)
        assertFalse(leftDrag.conflictsWith(ctrlLeftDrag, allowSubsumption = false))
        assertFalse(leftDrag.conflictsWith(altLeftDrag, allowSubsumption = false))
        assertFalse(ctrlLeftDrag.conflictsWith(altLeftDrag, allowSubsumption = false))
        assertTrue(leftDrag.conflictsWith(leftDrag, allowSubsumption = false))

        // Subsumption comparison (cross-zone / active zoning)
        // Left Drag is subsumed by Ctrl + Left Drag and vice-versa
        assertTrue(leftDrag.conflictsWith(ctrlLeftDrag, allowSubsumption = true))
        assertTrue(ctrlLeftDrag.conflictsWith(leftDrag, allowSubsumption = true))

        // Left Drag is subsumed by Alt + Left Drag
        assertTrue(leftDrag.conflictsWith(altLeftDrag, allowSubsumption = true))

        // Ctrl + Left Drag is subsumed by Ctrl + Shift + Left Drag
        assertTrue(ctrlLeftDrag.conflictsWith(ctrlShiftLeftDrag, allowSubsumption = true))

        // Disjoint modifier sets (Ctrl vs Alt) do NOT subsume each other
        assertFalse(ctrlLeftDrag.conflictsWith(altLeftDrag, allowSubsumption = true))

        // Keyboard keys: Key Z vs Ctrl + Key Z
        val keyZ = ShortcutTrigger(key = ShortcutKey.fromCode("Z"))
        val ctrlZ = ShortcutTrigger(isCtrl = true, key = ShortcutKey.fromCode("Z"))
        assertFalse(keyZ.conflictsWith(ctrlZ, allowSubsumption = false))
        assertTrue(keyZ.conflictsWith(ctrlZ, allowSubsumption = true))
    }

    @Test
    fun testCrossZoneConflictSubsumptionDetection() {
        val manager = ShortcutManager()

        // FlowBoard has Box Select (Left Drag)
        // FlowConnectionPoint has Move Point (Left Drag) and Create Ramification (Alt + Left Drag)
        manager.setActiveSituations(setOf(ShortcutSituation.FlowBoard, ShortcutSituation.FlowConnectionPoint))

        val conflicts = manager.findActiveZoneConflicts()
        assertTrue(conflicts.isNotEmpty())

        // Exact conflict between Box Select and Move Point (both Left Drag)
        val exactConflict = conflicts.firstOrNull {
            it.actionA.id == ShortcutActionId.FLOW_BOX_SELECT &&
            it.actionB.id == ShortcutActionId.FLOW_MOVE_POINT
        }
        assertNotNull(exactConflict)
        assertTrue(exactConflict.isExact)
        // Verified priority-based hierarchical shadowing
        assertTrue(exactConflict.isShadowed)
        assertEquals(ShortcutActionId.FLOW_MOVE_POINT, exactConflict.dominantAction.id)
        assertEquals(ShortcutActionId.FLOW_BOX_SELECT, exactConflict.shadowedAction.id)

        // Subsumption conflict between Box Select (Left Drag) and Create Ramification (Alt + Left Drag)
        val subsumptionConflict = conflicts.firstOrNull {
            it.actionA.id == ShortcutActionId.FLOW_BOX_SELECT &&
            it.actionB.id == ShortcutActionId.FLOW_CREATE_RAMIFICATION
        }
        assertNotNull(subsumptionConflict)
        assertFalse(subsumptionConflict.isExact)
        assertTrue(subsumptionConflict.isShadowed)
        assertEquals(ShortcutActionId.FLOW_CREATE_RAMIFICATION, subsumptionConflict.dominantAction.id)
        assertEquals(ShortcutActionId.FLOW_BOX_SELECT, subsumptionConflict.shadowedAction.id)
    }

    @Test
    fun testCentralizedPrioritiesWallOfValues() {
        // Verify explicit priority values from ShortcutPriorities
        assertEquals(0, ShortcutPriorities.Global.BASE)
        assertEquals(10, ShortcutPriorities.Flow.BOARD)
        assertEquals(20, ShortcutPriorities.Flow.SELECTION)
        assertEquals(30, ShortcutPriorities.Flow.WIRE)
        assertEquals(30, ShortcutPriorities.Flow.NODE)
        assertEquals(40, ShortcutPriorities.Flow.CONNECTION_POINT)
        assertEquals(50, ShortcutPriorities.Tools.JOB_TERMINAL)
        assertEquals(50, ShortcutPriorities.Tools.SETTINGS)

        // Verify ShortcutSituation enum links to the wall of values
        assertEquals(ShortcutPriorities.Global.BASE, ShortcutSituation.Global.priority)
        assertEquals(ShortcutPriorities.Flow.BOARD, ShortcutSituation.FlowBoard.priority)
        assertEquals(ShortcutPriorities.Flow.SELECTION, ShortcutSituation.FlowSelection.priority)
        assertEquals(ShortcutPriorities.Flow.WIRE, ShortcutSituation.FlowWire.priority)
        assertEquals(ShortcutPriorities.Flow.NODE, ShortcutSituation.FlowNode.priority)
        assertEquals(ShortcutPriorities.Flow.CONNECTION_POINT, ShortcutSituation.FlowConnectionPoint.priority)

        // Priority ordering checks
        assertTrue(ShortcutSituation.FlowConnectionPoint.priority > ShortcutSituation.FlowNode.priority)
        assertTrue(ShortcutSituation.FlowNode.priority > ShortcutSituation.FlowSelection.priority)
        assertTrue(ShortcutSituation.FlowSelection.priority > ShortcutSituation.FlowBoard.priority)
        assertTrue(ShortcutSituation.FlowBoard.priority > ShortcutSituation.Global.priority)
    }

    @Test
    fun testEventEatingAndRejectionOfConsumedEvents() {
        val manager = ShortcutManager()

        val change = PointerInputChange(
            id = PointerId(1L),
            uptimeMillis = 1000L,
            position = Offset(100f, 100f),
            pressed = true,
            previousUptimeMillis = 950L,
            previousPosition = Offset(100f, 100f),
            previousPressed = false,
            isInitiallyConsumed = false
        )
        val event = PointerEvent(changes = listOf(change))

        // Initially unconsumed
        assertFalse(manager.isConsumed(event))
        assertNull(manager.lastEatenEvent.value)

        // Eat event with Move Point action
        manager.eat(event, ShortcutActionId.FLOW_MOVE_POINT)

        // Event changes are now consumed
        assertTrue(manager.isConsumed(event))
        val eaten = manager.lastEatenEvent.value
        assertNotNull(eaten)
        assertEquals(ShortcutActionId.FLOW_MOVE_POINT, eaten.actionId)
        assertEquals(ShortcutSituation.FlowConnectionPoint, eaten.situation)
        assertEquals(45f, eaten.priority)

        // Downstream check with allowConsumed = false immediately rejects consumed event
        assertFalse(
            manager.matchesPointer(
                actionId = ShortcutActionId.FLOW_BOX_SELECT,
                event = event,
                gesture = ShortcutGesture.Drag,
                allowConsumed = false
            )
        )
    }

    @Test
    fun testDynamicSituationElevationOverride() {
        val manager = ShortcutManager()
        assertEquals(10f, manager.getEffectivePriority(ShortcutSituation.FlowBoard))
        assertEquals(40f, manager.getEffectivePriority(ShortcutSituation.FlowConnectionPoint))

        // Set dynamic elevation (e.g. z-index / elevation) on FlowBoard to 100
        manager.setSituationElevation(ShortcutSituation.FlowBoard, 100f)
        assertEquals(100f, manager.getEffectivePriority(ShortcutSituation.FlowBoard))

        manager.setActiveSituations(setOf(ShortcutSituation.FlowBoard, ShortcutSituation.FlowConnectionPoint))
        val conflicts = manager.findActiveZoneConflicts()
        val exactConflict = conflicts.firstOrNull {
            it.actionA.id == ShortcutActionId.FLOW_BOX_SELECT &&
            it.actionB.id == ShortcutActionId.FLOW_MOVE_POINT
        }
        assertNotNull(exactConflict)
        assertTrue(exactConflict.isShadowed)
        // With elevated priority 100f, FlowBoard now dominates over FlowConnectionPoint (40f)
        assertEquals(ShortcutActionId.FLOW_BOX_SELECT, exactConflict.dominantAction.id)
        assertEquals(ShortcutActionId.FLOW_MOVE_POINT, exactConflict.shadowedAction.id)

        // Reset elevation back to default
        manager.setSituationElevation(ShortcutSituation.FlowBoard, null)
        assertEquals(10f, manager.getEffectivePriority(ShortcutSituation.FlowBoard))
    }

    @Test
    fun testRelativePriorityWithinSameElement() = runTest {
        val persistence = FakeSettingsPersistence()
        val repo = SettingsRepository(persistence, backgroundScope)
        val manager = ShortcutManager(settingsRepository = repo)

        // FLOW_PAN_CANVAS and FLOW_BOX_SELECT are both in FlowBoard
        // FlowBoard base priority is 10.
        // Default relative priorities: FLOW_PAN_CANVAS = 5, FLOW_BOX_SELECT = 0
        assertEquals(15f, manager.getEffectiveActionPriority(ShortcutActionId.FLOW_PAN_CANVAS))
        assertEquals(10f, manager.getEffectiveActionPriority(ShortcutActionId.FLOW_BOX_SELECT))

        // Update relative priority for FLOW_BOX_SELECT to +20
        manager.updateRelativePriority(ShortcutActionId.FLOW_BOX_SELECT, 20)
        assertEquals(20, manager.getEffectiveRelativePriority(ShortcutActionId.FLOW_BOX_SELECT))
        assertEquals(30f, manager.getEffectiveActionPriority(ShortcutActionId.FLOW_BOX_SELECT))
        assertTrue(manager.isCustomized(ShortcutActionId.FLOW_BOX_SELECT))

        // Now FLOW_BOX_SELECT (30f) has higher priority than FLOW_PAN_CANVAS (15f)
        assertTrue(
            manager.getEffectiveActionPriority(ShortcutActionId.FLOW_BOX_SELECT) >
            manager.getEffectiveActionPriority(ShortcutActionId.FLOW_PAN_CANVAS)
        )

        // Reset relative priority
        manager.resetRelativePriority(ShortcutActionId.FLOW_BOX_SELECT)
        assertEquals(ShortcutPriorities.Relative.NORMAL, manager.getEffectiveRelativePriority(ShortcutActionId.FLOW_BOX_SELECT))
        assertEquals(10f, manager.getEffectiveActionPriority(ShortcutActionId.FLOW_BOX_SELECT))
        assertFalse(manager.isCustomized(ShortcutActionId.FLOW_BOX_SELECT))
    }

    @Test
    fun testPriorityResolutionModes() = runTest {
        val persistence = FakeSettingsPersistence()
        val repo = SettingsRepository(persistence, backgroundScope)
        val manager = ShortcutManager(settingsRepository = repo)

        // Default mode is ZIndexAndPriority
        assertEquals(ShortcutPriorityMode.ZIndexAndPriority, manager.getPriorityMode())

        // Set elevation on FlowBoard
        manager.setSituationElevation(ShortcutSituation.FlowBoard, 100f)
        assertEquals(100f, manager.getEffectivePriority(ShortcutSituation.FlowBoard))

        // Switch to PriorityOnly mode: elevation is bypassed, strictly using predefined priority (10f)
        manager.setPriorityMode(ShortcutPriorityMode.PriorityOnly)
        assertEquals(ShortcutPriorityMode.PriorityOnly, manager.getPriorityMode())
        assertEquals(10f, manager.getEffectivePriority(ShortcutSituation.FlowBoard))

        // Switch back to ZIndexAndPriority: dynamic elevation takes effect again
        manager.setPriorityMode(ShortcutPriorityMode.ZIndexAndPriority)
        assertEquals(100f, manager.getEffectivePriority(ShortcutSituation.FlowBoard))
    }
}

