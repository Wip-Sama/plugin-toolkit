package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.wip.plugintoolkit.api.sdui.ButtonNode
import org.wip.plugintoolkit.api.sdui.ColumnNode
import org.wip.plugintoolkit.api.sdui.TextInputNode
import org.wip.plugintoolkit.api.sdui.UiDropTarget
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.api.sdui.UiModifier
import org.wip.plugintoolkit.api.sdui.UiShortcut
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings

class SduiInputAndDropSupportTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = createTempDirectory("sdui-input-drop-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testShortcutMatcherNormalizesAliasesRespectsDepthAndProtectsTextEditing() {
        assertEquals(UiShortcut.KEY_DELETE, SduiShortcutMatcher.normalizeKeyName("Del"))
        assertEquals(UiShortcut.KEY_ESCAPE, SduiShortcutMatcher.normalizeKeyName("esc"))
        assertEquals(UiShortcut.KEY_ARROW_LEFT, SduiShortcutMatcher.normalizeKeyName("left"))
        assertEquals("S", SduiShortcutMatcher.composeKeyToCanonicalName(Key.S))
        assertEquals(UiShortcut.KEY_DELETE, SduiShortcutMatcher.composeKeyToCanonicalName(Key.Delete))

        val parentShortcut = UiShortcut(
            key = UiShortcut.KEY_DELETE,
            action = "parent_delete",
            label = "Parent Delete"
        )
        val childShortcut = UiShortcut(
            key = UiShortcut.KEY_DELETE,
            action = "page_delete",
            label = "Page Delete"
        )
        val saveShortcut = UiShortcut(
            key = "S",
            action = "save_draft",
            ctrl = true,
            allowWhileTyping = false
        )

        val entries = listOf(
            SduiShortcutEntry(nodeId = "root", depth = 0, registrationOrder = 0, shortcuts = listOf(parentShortcut, saveShortcut)),
            SduiShortcutEntry(nodeId = "grid", depth = 3, registrationOrder = 1, shortcuts = listOf(childShortcut))
        )

        val bestDelete = SduiShortcutMatcher.findBestMatchingEntry(
            entries = entries,
            canonicalKey = UiShortcut.KEY_DELETE,
            ctrl = false,
            shift = false,
            alt = false,
            meta = false,
            isTypingInTextInput = false
        )
        assertNotNull(bestDelete)
        assertEquals("grid", bestDelete.first)
        assertEquals("page_delete", bestDelete.second.action)

        // While typing inside TextInputNode, unmodified Delete must NOT hijack backspace/delete editing
        val suppressedDelete = SduiShortcutMatcher.findBestMatchingEntry(
            entries = entries,
            canonicalKey = UiShortcut.KEY_DELETE,
            ctrl = false,
            shift = false,
            alt = false,
            meta = false,
            isTypingInTextInput = true
        )
        assertNull(suppressedDelete)

        // Ctrl+S is non-text-editing command modifier so it continues to work while typing
        val allowedSave = SduiShortcutMatcher.findBestMatchingEntry(
            entries = entries,
            canonicalKey = "S",
            ctrl = true,
            shift = false,
            alt = false,
            meta = false,
            isTypingInTextInput = true
        )
        assertNotNull(allowedSave)
        assertEquals("save_draft", allowedSave.second.action)
    }

    @Test
    fun testDropPathResolverDecodesUrisFiltersExtensionsDirectoriesAndMaxFiles() {
        val dir = File(tempDir, "chapter dir").apply { mkdirs() }
        val imgOne = File(dir, "page 01.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val imgTwo = File(dir, "page_02.WEBP").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val textDoc = File(dir, "notes.txt").apply { writeText("ignore me") }

        val targetFiles = UiDropTarget(
            action = "drop_pages",
            allowedExtensions = listOf("png", ".webp"),
            acceptFiles = true,
            acceptDirectories = false,
            maxFiles = 2
        )

        val rawUris = listOf(
            imgOne.toURI().toString(),
            dir.toURI().toString(),
            textDoc.toURI().toString(),
            imgTwo.toURI().toString(),
            imgOne.toURI().toString()
        )
        val filteredFiles = SduiDropPathResolver.filterDroppedPaths(rawUris, targetFiles)
        assertEquals(listOf(imgOne.absolutePath, imgTwo.absolutePath), filteredFiles)

        val dirOnlyTarget = UiDropTarget(
            action = "drop_repo",
            acceptFiles = false,
            acceptDirectories = true,
            maxFiles = 1
        )
        val filteredDirs = SduiDropPathResolver.filterDroppedPaths(
            listOf(imgOne.toURI().toString(), dir.toURI().toString()),
            dirOnlyTarget
        )
        assertEquals(listOf(dir.absolutePath), filteredDirs)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun testSafeUiNodeRendererDispatchesShortcutsAndGuardsFocusedTextInput() {
        val dispatched = mutableListOf<UiEvent>()
        val treeRoot = ColumnNode(
            id = "workspace_root",
            modifier = UiModifier.fillMaxSize()
                .shortcut(key = UiShortcut.KEY_DELETE, action = "delete_page", label = "Delete")
                .shortcut(key = "S", action = "save_all", ctrl = true, allowWhileTyping = true, label = "Save"),
            children = listOf(
                TextInputNode(id = "title_input", label = "Title Field", text = "Ch 01"),
                ButtonNode(id = "select_btn", label = "Focus Workspace", action = "select_item")
            )
        )

        composeTestRule.setContent {
            AppTheme(appearance = AppearanceSettings()) {
                Box(modifier = Modifier.size(width = 800.dp, height = 500.dp)) {
                    SafeUiNodeRenderer(
                        node = treeRoot,
                        onEvent = { dispatched.add(it) }
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // Focus TextInputNode: unmodified Delete is guarded while typing, Ctrl+S (allowWhileTyping=true) fires
        composeTestRule.onNodeWithText("Title Field").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Title Field").performKeyInput {
            pressKey(Key.Delete)
        }
        assertFalse(
            dispatched.any { it is UiEvent.ShortcutTriggered && it.action == "delete_page" },
            "Delete shortcut must be suppressed while TextInputNode has focus"
        )

        composeTestRule.onNodeWithText("Title Field").performKeyInput {
            withKeyDown(Key.CtrlLeft) {
                pressKey(Key.S)
            }
        }
        assertTrue(
            dispatched.any {
                it is UiEvent.ShortcutTriggered &&
                    it.nodeId == "workspace_root" &&
                    it.action == "save_all" &&
                    it.ctrl
            },
            "Ctrl+S with allowWhileTyping=true should trigger even while editing text"
        )

        // Clicking a non-text control releases text focus to root so Delete shortcut triggers normally
        composeTestRule.onNodeWithText("Focus Workspace").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().performKeyInput {
            pressKey(Key.Delete)
        }
        assertTrue(
            dispatched.any {
                it is UiEvent.ShortcutTriggered &&
                    it.nodeId == "workspace_root" &&
                    it.action == "delete_page"
            },
            "Unmodified Delete should dispatch ShortcutTriggered after leaving TextInputNode focus"
        )
    }
}
