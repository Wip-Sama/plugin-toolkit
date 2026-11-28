package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.api.sdui.ErrorBoundaryNode
import org.wip.plugintoolkit.api.sdui.SidebarAttachPoint
import org.wip.plugintoolkit.api.sdui.SidebarNode
import org.wip.plugintoolkit.api.sdui.TextNode
import org.wip.plugintoolkit.api.sdui.UiNodeSerializer
import org.wip.plugintoolkit.api.sdui.UiNodeTree
import org.wip.plugintoolkit.api.sdui.uiTree
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SduiSequelSidebarTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun sidebarNodeSequelFieldsRoundTripAndKeepBackwardCompatibleDefaults() {
        val legacyJson = """{"type":"sidebar","id":"legacy_sb","title":"Legacy"}"""
        val decodedLegacy = json.decodeFromString(UiNodeSerializer, legacyJson) as SidebarNode
        assertEquals("legacy_sb", decodedLegacy.id)
        assertNull(decodedLegacy.sequelTitle)
        assertNull(decodedLegacy.sequelChildren)
        assertTrue(decodedLegacy.sequelVisible)
        assertNull(decodedLegacy.sequelWidthDp)

        val built = uiTree {
            sidebar(id = "main_sb", title = "Primary") {
                text(id = "primary_item", text = "Primary Body")
                sequel(title = "Inspector", visible = true, widthDp = 240) {
                    text(id = "sequel_item", text = "Sequel Body")
                }
            }
        }
        val root = built.root as SidebarNode
        val encoded = json.encodeToString(UiNodeSerializer, root)
        val decoded = json.decodeFromString(UiNodeSerializer, encoded) as SidebarNode
        assertEquals("Inspector", decoded.sequelTitle)
        assertEquals(240, decoded.sequelWidthDp)
        assertTrue(decoded.sequelVisible)
        assertEquals(1, decoded.sequelChildren?.size)
    }

    @Test
    fun sanitizerValidatesSequelChildrenAndWidth() {
        val badWidthTree = UiNodeTree(
            root = SidebarNode(
                id = "bad_sb",
                sequelWidthDp = 0,
                sequelChildren = listOf(TextNode(id = "s1", text = "Hi"))
            )
        )
        val sanitizedBadWidth = UiAstSanitizer.sanitize(badWidthTree)
        assertIs<ErrorBoundaryNode>(sanitizedBadWidth.root)

        val duplicateIdTree = UiNodeTree(
            root = SidebarNode(
                id = "dup_sb",
                children = listOf(TextNode(id = "shared_id", text = "Primary")),
                sequelChildren = listOf(TextNode(id = "shared_id", text = "Sequel"))
            )
        )
        val sanitizedDup = UiAstSanitizer.sanitize(duplicateIdTree)
        val sanitizedSidebar = sanitizedDup.root as SidebarNode
        assertIs<ErrorBoundaryNode>(sanitizedSidebar.sequelChildren?.first())
    }

    @Test
    fun renderSidebarDisplaysAndTogglesSequelPane() = runComposeUiTest {
        val tree = uiTree {
            sidebar(id = "floating_sb", attachPoint = SidebarAttachPoint.LEFT, title = "Primary Pane") {
                text(id = "primary_txt", text = "Primary Content")
                sequel(title = "Sequel Pane", visible = true, widthDp = 240) {
                    text(id = "sequel_txt", text = "Sequel Content")
                }
            }
        }

        setContent {
            AppTheme(appearance = AppearanceSettings()) {
                SafeUiNodeRenderer(
                    node = tree.root,
                    onEvent = {}
                )
            }
        }

        waitForIdle()
        onNodeWithTag("toolkit_floating_sidebar").assertIsDisplayed()
        onNodeWithText("Primary Content").assertIsDisplayed()
        onNodeWithText("Sequel Pane").assertIsDisplayed()
        onNodeWithText("Sequel Content").assertIsDisplayed()

        onNodeWithTag("sequel_sidebar_toggle").performClick()
        waitForIdle()
        onNodeWithText("Sequel Content").assertDoesNotExist()

        onNodeWithTag("sequel_sidebar_toggle").performClick()
        waitForIdle()
        onNodeWithText("Sequel Content").assertIsDisplayed()
    }

    @Test
    fun collapsibleSduiSidebarSupportsDragToCollapseAndDragToExpand() = runComposeUiTest {
        val tree = uiTree {
            sidebar(
                id = "drag_sb",
                attachPoint = SidebarAttachPoint.LEFT,
                title = "Collapsible Sidebar",
                isCollapsible = true
            ) {
                text(id = "body_txt", text = "Sidebar Body Content")
            }
        }

        setContent {
            AppTheme(appearance = AppearanceSettings()) {
                Box(modifier = Modifier.size(900.dp, 600.dp)) {
                    SafeUiNodeRenderer(
                        node = tree.root,
                        onEvent = {}
                    )
                }
            }
        }

        waitForIdle()
        onNodeWithText("Sidebar Body Content").assertIsDisplayed()

        // Drag the resize handle far left past min width + collapse threshold
        onNodeWithTag("sdui_sidebar_resize_handle_drag_sb").performTouchInput {
            down(center)
            moveBy(Offset(-300f, 0f))
            up()
        }
        waitForIdle()
        onNodeWithText("Sidebar Body Content").assertDoesNotExist()

        // Drag outward right to expand back open
        onNodeWithTag("sdui_sidebar_resize_handle_drag_sb").performTouchInput {
            down(center)
            moveBy(Offset(140f, 0f))
            up()
        }
        waitForIdle()
        onNodeWithText("Sidebar Body Content").assertIsDisplayed()
    }
}
