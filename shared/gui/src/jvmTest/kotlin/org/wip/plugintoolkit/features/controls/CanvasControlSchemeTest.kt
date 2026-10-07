package org.wip.plugintoolkit.features.controls

import androidx.compose.ui.geometry.Offset
import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.core.utils.FileUtils
import org.wip.plugintoolkit.features.controls.model.CanvasControlScheme
import org.wip.plugintoolkit.features.controls.model.PointerBinding
import org.wip.plugintoolkit.features.controls.model.PointerButton
import org.wip.plugintoolkit.features.controls.model.PointerGestureState
import org.wip.plugintoolkit.features.controls.model.PointerGestureStateMachine
import org.wip.plugintoolkit.features.settings.model.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CanvasControlSchemeTest {

    @Test
    fun testDefaultControlSchemeConfiguration() {
        val scheme = CanvasControlScheme()
        assertEquals("Default", scheme.name)
        assertEquals(2, scheme.panBindings.size)
        assertTrue(scheme.panBindings.any { it.button == PointerButton.Secondary })
        assertTrue(scheme.panBindings.any { it.button == PointerButton.Tertiary })
        assertEquals(PointerButton.Primary, scheme.boxSelectBinding.button)
        assertFalse(scheme.boxSelectBinding.requireCtrl)
        assertFalse(scheme.boxSelectBinding.requireShift)

        assertEquals(PointerButton.Primary, scheme.toggleSelectBinding.button)
        if (FileUtils.isMac) {
            assertTrue(scheme.toggleSelectBinding.requireMeta)
            assertFalse(scheme.toggleSelectBinding.requireCtrl)
        } else {
            assertTrue(scheme.toggleSelectBinding.requireCtrl)
            assertFalse(scheme.toggleSelectBinding.requireMeta)
        }

        assertTrue(scheme.zoomWithWheel)
        assertFalse(scheme.zoomRequiresCtrl)
        assertFalse(scheme.invertZoomDirection)
        assertEquals(1.0f, scheme.zoomSensitivity)
    }

    @Test
    fun testPanTriggerMatching() {
        val scheme = CanvasControlScheme()

        // Right (Secondary) drag should trigger pan
        assertTrue(
            scheme.isPanTriggered(
                button = PointerButton.Secondary,
                ctrl = false,
                shift = false,
                alt = false,
                meta = false
            )
        )

        // Middle (Tertiary) drag should trigger pan
        assertTrue(
            scheme.isPanTriggered(
                button = PointerButton.Tertiary,
                ctrl = false,
                shift = false,
                alt = false,
                meta = false
            )
        )

        // Left (Primary) drag should NOT trigger pan
        assertFalse(
            scheme.isPanTriggered(
                button = PointerButton.Primary,
                ctrl = false,
                shift = false,
                alt = false,
                meta = false
            )
        )

        // Modifier press (e.g. Shift) should NOT match unshifted pan binding
        assertFalse(
            scheme.isPanTriggered(
                button = PointerButton.Secondary,
                ctrl = false,
                shift = true,
                alt = false,
                meta = false
            )
        )
    }

    @Test
    fun testBoxSelectTriggerMatching() {
        val scheme = CanvasControlScheme()

        // Plain Left (Primary) drag matches box select
        assertTrue(
            scheme.isBoxSelectTriggered(
                button = PointerButton.Primary,
                ctrl = false,
                shift = false,
                alt = false,
                meta = false
            )
        )

        // Right click should NOT match box select
        assertFalse(
            scheme.isBoxSelectTriggered(
                button = PointerButton.Secondary,
                ctrl = false,
                shift = false,
                alt = false,
                meta = false
            )
        )

        // Ctrl + Left click should NOT match standard box select
        assertFalse(
            scheme.isBoxSelectTriggered(
                button = PointerButton.Primary,
                ctrl = true,
                shift = false,
                alt = false,
                meta = false
            )
        )
    }

    @Test
    fun testToggleSelectMatching() {
        val macBinding = PointerBinding.primary(PointerButton.Primary, isMac = true)
        assertTrue(macBinding.requireMeta)
        assertFalse(macBinding.requireCtrl)
        assertTrue(macBinding.matches(PointerButton.Primary, ctrl = false, shift = false, alt = false, meta = true))
        assertFalse(macBinding.matches(PointerButton.Primary, ctrl = true, shift = false, alt = false, meta = false))

        val winBinding = PointerBinding.primary(PointerButton.Primary, isMac = false)
        assertTrue(winBinding.requireCtrl)
        assertFalse(winBinding.requireMeta)
        assertTrue(winBinding.matches(PointerButton.Primary, ctrl = true, shift = false, alt = false, meta = false))
        assertFalse(winBinding.matches(PointerButton.Primary, ctrl = false, shift = false, alt = false, meta = true))
    }

    @Test
    fun testZoomEvaluation() {
        val defaultScheme = CanvasControlScheme()
        // Wheel zoom enabled, no Ctrl required
        assertTrue(defaultScheme.shouldZoom(ctrl = false, shift = false, alt = false, meta = false))
        assertTrue(defaultScheme.shouldZoom(ctrl = true, shift = false, alt = false, meta = false))

        val ctrlRequiredScheme = CanvasControlScheme(zoomRequiresCtrl = true)
        if (FileUtils.isMac) {
            assertFalse(ctrlRequiredScheme.shouldZoom(ctrl = false, shift = false, alt = false, meta = false))
            assertTrue(ctrlRequiredScheme.shouldZoom(ctrl = false, shift = false, alt = false, meta = true))
        } else {
            assertFalse(ctrlRequiredScheme.shouldZoom(ctrl = false, shift = false, alt = false, meta = false))
            assertTrue(ctrlRequiredScheme.shouldZoom(ctrl = true, shift = false, alt = false, meta = false))
        }

        val disabledScheme = CanvasControlScheme(zoomWithWheel = false)
        assertFalse(disabledScheme.shouldZoom(ctrl = true, shift = true, alt = true, meta = true))
    }

    @Test
    fun testPointerButtonFromButtons() {
        assertEquals(PointerButton.Primary, PointerButton.fromButtons(isPrimary = true, isSecondary = false, isTertiary = false))
        assertEquals(PointerButton.Secondary, PointerButton.fromButtons(isPrimary = false, isSecondary = true, isTertiary = false))
        assertEquals(PointerButton.Tertiary, PointerButton.fromButtons(isPrimary = false, isSecondary = false, isTertiary = true))
        assertEquals(PointerButton.Back, PointerButton.fromButtons(isPrimary = false, isSecondary = false, isTertiary = false, isBack = true))
        assertEquals(PointerButton.Forward, PointerButton.fromButtons(isPrimary = false, isSecondary = false, isTertiary = false, isForward = true))
    }

    @Test
    fun testPointerBindingFormatting() {
        val simple = PointerBinding(PointerButton.Secondary)
        assertEquals("[ Right ]", simple.format())

        val withCtrl = PointerBinding(PointerButton.Primary, requireCtrl = true)
        assertEquals("[ Ctrl + Left ]", withCtrl.format())

        val withShiftAlt = PointerBinding(PointerButton.Tertiary, requireShift = true, requireAlt = true)
        assertEquals("[ Alt + Shift + Middle ]", withShiftAlt.format())
    }

    @Test
    fun testPointerGestureStateMachineLifecycle() {
        val sm = PointerGestureStateMachine()
        assertEquals(PointerGestureState.Idle, sm.currentState)
        assertFalse(sm.currentState.isInteracting)

        // 1. Press down
        val startPos = Offset(100f, 100f)
        val started = sm.onPointerDown(startPos, PointerButton.Secondary, timeMillis = 1000L)
        assertEquals(started, sm.currentState)
        assertTrue(sm.currentState.isInteracting)
        assertEquals(startPos, started.startPosition)
        assertEquals(PointerButton.Secondary, started.button)

        // 2. Drag / Move
        val movePos1 = Offset(120f, 110f)
        val tracking1 = sm.onPointerMove(movePos1, timeMillis = 1016L)
        assertNotNull(tracking1)
        assertIs<PointerGestureState.Tracking>(tracking1)
        assertEquals(Offset(20f, 10f), tracking1.delta)
        assertEquals(Offset(20f, 10f), tracking1.totalDelta)

        val movePos2 = Offset(130f, 115f)
        val tracking2 = sm.onPointerMove(movePos2, timeMillis = 1032L)
        assertNotNull(tracking2)
        assertIs<PointerGestureState.Tracking>(tracking2)
        assertEquals(Offset(10f, 5f), tracking2.delta)
        assertEquals(Offset(30f, 15f), tracking2.totalDelta)

        // 3. Release / Commit
        val endPos = Offset(135f, 120f)
        val committed = sm.onPointerUp(endPos, timeMillis = 1048L)
        assertNotNull(committed)
        assertEquals(startPos, committed.startPosition)
        assertEquals(endPos, committed.endPosition)
        assertEquals(Offset(35f, 20f), committed.totalDelta)
        assertEquals(PointerButton.Secondary, committed.button)

        // 4. Cancel flow
        sm.onPointerDown(startPos, PointerButton.Primary)
        val cancelled = sm.onCancel("Interrupted by gesture")
        assertEquals("Interrupted by gesture", cancelled.reason)
        assertFalse(sm.currentState.isInteracting)

        sm.reset()
        assertEquals(PointerGestureState.Idle, sm.currentState)
    }

    @Test
    fun testAppSettingsSerializationRoundtrip() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val customScheme = CanvasControlScheme(
            name = "CustomArtist",
            zoomSensitivity = 1.8f,
            invertZoomDirection = true,
            zoomRequiresCtrl = true
        )
        val originalSettings = AppSettings(controls = customScheme)

        val serialized = json.encodeToString(AppSettings.serializer(), originalSettings)
        val deserialized = json.decodeFromString(AppSettings.serializer(), serialized)

        assertEquals("CustomArtist", deserialized.controls.name)
        assertEquals(1.8f, deserialized.controls.zoomSensitivity)
        assertTrue(deserialized.controls.invertZoomDirection)
        assertTrue(deserialized.controls.zoomRequiresCtrl)
        assertEquals(originalSettings.controls, deserialized.controls)
    }
}
