package org.wip.plugintoolkit.features.shortcuts.engine

import org.wip.plugintoolkit.core.model.localized
import org.wip.plugintoolkit.features.shortcuts.model.KeyChord
import org.wip.plugintoolkit.features.shortcuts.model.ModifierMask
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutActionId
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutCommand
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutKey
import org.wip.plugintoolkit.features.shortcuts.model.ShortcutScope
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_copy
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_copy_desc
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_paste
import plugintoolkit.composeapp.generated.resources.shortcut_action_flow_paste_desc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StandardShortcutRegistryTest {

    @Test
    fun testPlatformPrimaryAcceleratorParity() {
        val macPrimary = ModifierMask.primary(isMac = true)
        assertTrue(macPrimary.meta)
        assertFalse(macPrimary.ctrl)

        val winPrimary = ModifierMask.primary(isMac = false)
        assertTrue(winPrimary.ctrl)
        assertFalse(winPrimary.meta)

        val macChord = KeyChord.primary(ShortcutKey.Z, isMac = true)
        val winChord = KeyChord.primary(ShortcutKey.Z, isMac = false)

        assertTrue(macChord.format(isMac = true).contains("Cmd"))
        assertTrue(winChord.format(isMac = false).contains("Ctrl"))
    }

    @Test
    fun testDefaultCommandsCatalogCompleteness() {
        val registry = StandardShortcutRegistry()
        val ids = registry.allCommands.map { it.id }.toSet()

        assertTrue(ids.contains(ShortcutActionId.FLOW_UNDO))
        assertTrue(ids.contains(ShortcutActionId.FLOW_REDO))
        assertTrue(ids.contains(ShortcutActionId.FLOW_DELETE_SELECTED))
        assertTrue(ids.contains(ShortcutActionId.FLOW_COPY))
        assertTrue(ids.contains(ShortcutActionId.FLOW_PASTE))
        assertTrue(ids.contains(ShortcutActionId.FLOW_ESCAPE))
        assertTrue(ids.contains(ShortcutActionId.FLOW_STRUCTURED_MODE))
        assertTrue(ids.contains(ShortcutActionId.FLOW_PAINT_TOOL))
        assertTrue(ids.contains(ShortcutActionId.FLOW_WASH_TOOL))
        assertTrue(ids.contains(ShortcutActionId.FLOW_EYEDROPPER))
    }

    @Test
    fun testScopedKeyDispatchResolution() {
        val registry = StandardShortcutRegistry()

        // Test Canvas Undo matching
        val undoId = registry.resolveMatchingCommand(
            key = ShortcutKey.Z,
            isCtrl = true,
            isShift = false,
            isAlt = false,
            isMeta = false,
            activeScope = ShortcutScope.FeatureCanvas
        )
        // On non-mac environments, Ctrl+Z matches undo
        if (!org.wip.plugintoolkit.core.utils.FileUtils.isMac) {
            assertEquals(ShortcutActionId.FLOW_UNDO, undoId)
        }

        // Test Delete key
        val deleteId = registry.resolveMatchingCommand(
            key = ShortcutKey.Delete,
            isCtrl = false,
            isShift = false,
            isAlt = false,
            isMeta = false,
            activeScope = ShortcutScope.FeatureCanvas
        )
        assertEquals(ShortcutActionId.FLOW_DELETE_SELECTED, deleteId)

        // Test Escape key
        val escId = registry.resolveMatchingCommand(
            key = ShortcutKey.Escape,
            isCtrl = false,
            isShift = false,
            isAlt = false,
            isMeta = false,
            activeScope = ShortcutScope.FeatureCanvas
        )
        assertEquals(ShortcutActionId.FLOW_ESCAPE, escId)

        // Unmatched key returns null
        val noneId = registry.resolveMatchingCommand(
            key = ShortcutKey.fromCode("NonExistent"),
            isCtrl = false,
            isShift = false,
            isAlt = false,
            isMeta = false,
            activeScope = ShortcutScope.FeatureCanvas
        )
        assertNull(noneId)
    }

    @Test
    fun testRebindingAndResetLifecycle() {
        val registry = StandardShortcutRegistry()
        assertFalse(registry.isCustomized(ShortcutActionId.FLOW_COPY))

        val customChord = KeyChord(
            key = ShortcutKey.K,
            modifiers = ModifierMask(ctrl = true, shift = true)
        )
        registry.updateBindings(ShortcutActionId.FLOW_COPY, listOf(customChord))

        assertTrue(registry.isCustomized(ShortcutActionId.FLOW_COPY))
        assertEquals(listOf(customChord), registry.getEffectiveChords(ShortcutActionId.FLOW_COPY))

        registry.resetBinding(ShortcutActionId.FLOW_COPY)
        assertFalse(registry.isCustomized(ShortcutActionId.FLOW_COPY))
        assertTrue(registry.getEffectiveChords(ShortcutActionId.FLOW_COPY).isNotEmpty())
    }

    @Test
    fun testIntraScopeConflictDetection() {
        val customCommands = listOf(
            ShortcutCommand(
                id = "cmd.1",
                title = Res.string.shortcut_action_flow_copy.localized,
                description = Res.string.shortcut_action_flow_copy_desc.localized,
                scope = ShortcutScope.FeatureCanvas,
                defaultChords = listOf(KeyChord(ShortcutKey.A))
            ),
            ShortcutCommand(
                id = "cmd.2",
                title = Res.string.shortcut_action_flow_paste.localized,
                description = Res.string.shortcut_action_flow_paste_desc.localized,
                scope = ShortcutScope.FeatureCanvas,
                defaultChords = listOf(KeyChord(ShortcutKey.A))
            )
        )

        val registry = StandardShortcutRegistry(customCommands)
        val conflicts = registry.findConflicts(ShortcutScope.FeatureCanvas)
        assertEquals(1, conflicts.size)
        assertEquals("cmd.1", conflicts[0].command1.id)
        assertEquals("cmd.2", conflicts[0].command2.id)

        // Candidate check
        val conflict = registry.checkConflict("cmd.1", KeyChord(ShortcutKey.A))
        assertNotNull(conflict)
        assertEquals("cmd.2", conflict.command2.id)
    }

    @Test
    fun testCrossScopeNonInterference() {
        val customCommands = listOf(
            ShortcutCommand(
                id = "modal.confirm",
                title = Res.string.shortcut_action_flow_copy.localized,
                description = Res.string.shortcut_action_flow_copy_desc.localized,
                scope = ShortcutScope.Modal,
                defaultChords = listOf(KeyChord(ShortcutKey.Enter))
            ),
            ShortcutCommand(
                id = "canvas.confirm",
                title = Res.string.shortcut_action_flow_paste.localized,
                description = Res.string.shortcut_action_flow_paste_desc.localized,
                scope = ShortcutScope.FeatureCanvas,
                defaultChords = listOf(KeyChord(ShortcutKey.Enter))
            )
        )

        val registry = StandardShortcutRegistry(customCommands)
        // Scoped conflict check for Modal should find 0 conflicts
        val modalConflicts = registry.findConflicts(ShortcutScope.Modal)
        assertTrue(modalConflicts.isEmpty())

        val canvasConflicts = registry.findConflicts(ShortcutScope.FeatureCanvas)
        assertTrue(canvasConflicts.isEmpty())
    }
}
