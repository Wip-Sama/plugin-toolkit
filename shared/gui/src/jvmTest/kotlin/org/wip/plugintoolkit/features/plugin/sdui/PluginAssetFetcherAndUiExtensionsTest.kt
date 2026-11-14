package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.MouseButton
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.test.runTest
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.wip.plugintoolkit.api.HostFileSystem
import org.wip.plugintoolkit.api.HttpMethod
import org.wip.plugintoolkit.api.HttpRequest
import org.wip.plugintoolkit.api.HttpResponse
import org.wip.plugintoolkit.api.PluginNetworkClient
import org.wip.plugintoolkit.api.sdui.AssetReference
import org.wip.plugintoolkit.api.sdui.ButtonNode
import org.wip.plugintoolkit.api.sdui.CardNode
import org.wip.plugintoolkit.api.sdui.GridNode
import org.wip.plugintoolkit.api.sdui.ImageNode
import org.wip.plugintoolkit.api.sdui.ListItemNode
import org.wip.plugintoolkit.api.sdui.ScrollableColumnNode
import org.wip.plugintoolkit.api.sdui.SpacingSize
import org.wip.plugintoolkit.api.sdui.SplitPaneNode
import org.wip.plugintoolkit.api.sdui.TextInputNode
import org.wip.plugintoolkit.api.sdui.TextNode
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.api.sdui.UiModifier
import org.wip.plugintoolkit.api.sdui.UiNode
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.features.plugin.logic.HostFileSystemImpl
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.sdui_drag_reorder
import plugintoolkit.composeapp.generated.resources.sdui_hide_secret
import plugintoolkit.composeapp.generated.resources.sdui_resize_pane
import plugintoolkit.composeapp.generated.resources.sdui_show_secret
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val TINY_PNG_DATA_URI =
    "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
private const val OVERSIZED_IMAGE_BYTES = (25 * 1024 * 1024) + 1

/**
 * Unit and Compose Desktop UI tests covering [resolveAssetBytes] and new SDUI extensions
 * ([GridNode] + drag reorder, secret [TextInputNode], interactive [ImageNode], [ListItemNode] row vs trailing actions,
 * and resizable [SplitPaneNode]).
 */
class PluginAssetFetcherAndUiExtensionsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "plugin_asset_ui_test_${System.nanoTime()}").apply {
            mkdirs()
        }
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `resolveAssetBytes fetches HTTPS images and enforces 25MB limit`() = runTest {
        val sampleImageBytes = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
        val capturedMethods = mutableListOf<HttpMethod>()
        val client = object : PluginNetworkClient {
            override suspend fun get(url: String, headers: Map<String, String>): ByteArray = ByteArray(0)
            override suspend fun getText(url: String, headers: Map<String, String>): String = ""
            override suspend fun post(url: String, body: ByteArray, headers: Map<String, String>): ByteArray = ByteArray(0)
            override suspend fun postText(url: String, body: String, headers: Map<String, String>): String = ""
            override suspend fun request(request: HttpRequest): HttpResponse {
                capturedMethods += request.method
                return if (request.url.contains("huge")) {
                    HttpResponse(statusCode = 200, body = ByteArray(OVERSIZED_IMAGE_BYTES))
                } else {
                    HttpResponse(statusCode = 200, body = sampleImageBytes)
                }
            }
        }

        val resolved = resolveAssetBytes(
            data = AssetReference.Uri("https://cdn.example.com/page01.png"),
            fileSystem = null,
            networkClient = client
        )
        assertContentEquals(sampleImageBytes, resolved)
        assertEquals(listOf(HttpMethod.GET), capturedMethods)

        val rejected = resolveAssetBytes(
            data = AssetReference.Uri("https://cdn.example.com/huge.png"),
            fileSystem = null,
            networkClient = client
        )
        assertNull(rejected, "Images exceeding 25MB must be rejected")
    }

    @Test
    fun `resolveAssetBytes resolves granted HostFileSystem URIs and safely returns null when denied`() = runTest {
        val allowedDir = File(tempDir, "allowed").apply { mkdirs() }
        val deniedDir = File(tempDir, "denied").apply { mkdirs() }
        val allowedFile = File(allowedDir, "page.png").apply { writeBytes(byteArrayOf(42, 43, 44)) }
        val deniedFile = File(deniedDir, "secret.png").apply { writeBytes(byteArrayOf(99, 100)) }

        val hostFs: HostFileSystem = HostFileSystemImpl(
            allowedPaths = listOf(allowedDir.canonicalPath),
            isDestructiveAllowed = false
        )

        val uriPath = allowedFile.toURI().toString()
        val fromFileUri = resolveAssetBytes(
            data = AssetReference.Uri(uriPath),
            fileSystem = null,
            hostFileSystem = hostFs
        )
        assertContentEquals(byteArrayOf(42, 43, 44), fromFileUri)

        val fromAbsolutePath = resolveAssetBytes(
            data = AssetReference.Uri(allowedFile.canonicalPath),
            fileSystem = null,
            hostFileSystem = hostFs
        )
        assertContentEquals(byteArrayOf(42, 43, 44), fromAbsolutePath)

        val fromDeniedPath = resolveAssetBytes(
            data = AssetReference.Uri(deniedFile.canonicalPath),
            fileSystem = null,
            hostFileSystem = hostFs
        )
        assertNull(fromDeniedPath, "Denied HostFileSystem paths must fail safely with null")
    }

    @Test
    fun `GridNode lays out multi-column tiles and drag gesture emits ItemsReordered`() {
        val events = mutableListOf<UiEvent>()
        val gridNode = GridNode(
            id = "pages_grid",
            columns = 2,
            spacing = SpacingSize.SMALL,
            reorderable = true,
            modifier = UiModifier(fillMaxWidth = 1f),
            children = listOf(
                CardNode(id = "page_1", children = listOf(TextNode(id = "lbl_1", text = "Page 01"))),
                CardNode(id = "page_2", children = listOf(TextNode(id = "lbl_2", text = "Page 02"))),
                CardNode(id = "page_3", children = listOf(TextNode(id = "lbl_3", text = "Page 03")))
            )
        )

        lateinit var dragLabel: String
        renderSduiNode(
            node = gridNode,
            width = 640.dp,
            height = 480.dp,
            onResolveStrings = { labels -> dragLabel = labels.dragReorder },
            onEvent = { events += it }
        )

        val page1Bounds = composeTestRule.onNodeWithText("Page 01").getBoundsInRoot()
        val page2Bounds = composeTestRule.onNodeWithText("Page 02").getBoundsInRoot()
        val page3Bounds = composeTestRule.onNodeWithText("Page 03").getBoundsInRoot()

        assertTrue(
            page2Bounds.left > page1Bounds.right,
            "2-column GridNode should place Page 02 to the right of Page 01"
        )
        assertTrue(
            page3Bounds.top > page1Bounds.bottom,
            "2-column GridNode should wrap Page 03 onto the second row"
        )

        // Outer ReorderableSduiItemSlot for tile 0 precedes its child icon in semantics tree order.
        // Step incrementally across touchSlop and past tile 2's horizontal midpoint inside its bounds.
        composeTestRule.onAllNodesWithContentDescription(dragLabel)[0].performMouseInput {
            moveTo(center)
            press(MouseButton.Primary)
            repeat(10) {
                moveBy(Offset(x = 38f, y = 0f))
            }
            release(MouseButton.Primary)
        }
        composeTestRule.waitForIdle()

        val reordered = events.filterIsInstance<UiEvent.ItemsReordered>()
        assertTrue(reordered.isNotEmpty(), "Expected UiEvent.ItemsReordered after dragging tile right")
        assertEquals("pages_grid", reordered.last().nodeId)
        assertEquals(0, reordered.last().fromIndex)
        assertEquals(1, reordered.last().toIndex)
        assertEquals(listOf("page_2", "page_1", "page_3"), reordered.last().itemIds)
    }

    @Test
    fun `GridNode vertical drag reorders across rows inside ScrollableColumnNode and preserves clickable thumbnails`() {
        val events = mutableListOf<UiEvent>()
        val container = ScrollableColumnNode(
            id = "outer_scroll",
            spacing = SpacingSize.SMALL,
            modifier = UiModifier.fillMaxSize(),
            children = listOf(
                GridNode(
                    id = "vertical_pages_grid",
                    columns = 2,
                    spacing = SpacingSize.SMALL,
                    reorderable = true,
                    modifier = UiModifier.fillMaxWidth(),
                    children = (1..4).map { idx ->
                        CardNode(
                            id = "page_$idx",
                            children = listOf(
                                ImageNode(
                                    id = "thumb_$idx",
                                    asset = AssetReference.Uri(TINY_PNG_DATA_URI),
                                    contentDescription = "Tile Image $idx",
                                    clickable = true,
                                    action = "select_$idx",
                                    modifier = UiModifier.fillMaxWidth().height(72)
                                ),
                                TextNode(id = "lbl_$idx", text = "Tile 0$idx")
                            )
                        )
                    }
                )
            )
        )

        renderSduiNode(
            node = container,
            width = 640.dp,
            height = 600.dp,
            onEvent = { events += it }
        )

        // Inner clickable thumbnail click must still emit UiEvent.Click cleanly
        composeTestRule.onNodeWithContentDescription("Tile Image 1").performClick()
        composeTestRule.waitForIdle()
        val clicks = events.filterIsInstance<UiEvent.Click>()
        assertTrue(clicks.any { it.nodeId == "thumb_1" && it.action == "select_1" })

        events.clear()
        val thumb1Bounds = composeTestRule.onNodeWithContentDescription("Tile Image 1").getBoundsInRoot()
        val thumb3Bounds = composeTestRule.onNodeWithContentDescription("Tile Image 3").getBoundsInRoot()
        val thumb1CenterY = (thumb1Bounds.top + thumb1Bounds.bottom) / 2f
        val thumb3CenterY = (thumb3Bounds.top + thumb3Bounds.bottom) / 2f
        val verticalDistancePx = (thumb3CenterY - thumb1CenterY).value * composeTestRule.density.density

        composeTestRule.onNodeWithContentDescription("Tile Image 1").performMouseInput {
            moveTo(center)
            press(MouseButton.Primary)
            val steps = 12
            val stepY = verticalDistancePx / steps
            repeat(steps) {
                moveBy(Offset(x = 0f, y = stepY))
            }
            release(MouseButton.Primary)
        }
        composeTestRule.waitForIdle()

        val reordered = events.filterIsInstance<UiEvent.ItemsReordered>()
        assertTrue(reordered.isNotEmpty(), "Expected vertical cross-row drag to emit UiEvent.ItemsReordered")
        assertEquals("vertical_pages_grid", reordered.last().nodeId)
        assertEquals(0, reordered.last().fromIndex)
        assertEquals(2, reordered.last().toIndex)
        assertEquals(listOf("page_2", "page_3", "page_1", "page_4"), reordered.last().itemIds)
        assertTrue(
            events.filterIsInstance<UiEvent.Click>().isEmpty(),
            "Releasing a cross-row drag must not trigger a spurious thumbnail click"
        )
    }

    @Test
    fun `secret TextInputNode toggles visibility mask on trailing button click`() {
        val secretInput = TextInputNode(
            id = "token_input",
            text = "s3cr3tToken",
            label = "MangaDex Password",
            isSecret = true
        )

        lateinit var showLabel: String
        lateinit var hideLabel: String
        renderSduiNode(
            node = secretInput,
            width = 480.dp,
            height = 160.dp,
            onResolveStrings = { labels ->
                showLabel = labels.showSecret
                hideLabel = labels.hideSecret
            }
        )

        composeTestRule.onNodeWithContentDescription(showLabel).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(hideLabel).assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(showLabel).assertIsDisplayed()
    }

    @Test
    fun `ImageNode displays badgeText and emits Click action when clickable`() {
        val events = mutableListOf<UiEvent>()
        val imageNode = ImageNode(
            id = "cover_preview",
            asset = AssetReference.Uri(TINY_PNG_DATA_URI),
            contentDescription = "Cover Preview",
            clickable = true,
            action = "select_cover",
            selected = true,
            badgeText = "P1",
            modifier = UiModifier(widthDp = 140, heightDp = 180)
        )

        renderSduiNode(node = imageNode, width = 240.dp, height = 240.dp) { events += it }

        composeTestRule.onNodeWithText("P1").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Cover Preview").performClick()

        val click = events.filterIsInstance<UiEvent.Click>().single()
        assertEquals("cover_preview", click.nodeId)
        assertEquals("select_cover", click.action)
    }

    @Test
    fun `ListItemNode row click and trailing button emit distinct events`() {
        val events = mutableListOf<UiEvent>()
        val listItem = ListItemNode(
            id = "chapter_row",
            title = "Chapter 104",
            subtitle = "Synced with Cubari",
            action = "open_chapter",
            trailingContent = listOf(
                ButtonNode(id = "sync_btn", label = "Sync", action = "sync_chapter")
            )
        )

        renderSduiNode(node = listItem, width = 640.dp, height = 200.dp) { events += it }

        composeTestRule.onNodeWithText("Sync").performClick()
        composeTestRule.onNodeWithText("Chapter 104").performClick()

        val clicks = events.filterIsInstance<UiEvent.Click>()
        assertEquals(listOf("sync_chapter", "open_chapter"), clicks.map { it.action })
    }

    @Test
    fun `resizable SplitPaneNode emits updated ValueChanged fraction when divider is dragged`() {
        val events = mutableListOf<UiEvent>()
        val splitNode = SplitPaneNode(
            id = "workspace_split",
            splitFraction = 0.5f,
            isHorizontal = true,
            resizable = true,
            modifier = UiModifier(fillMaxWidth = 1f, fillMaxHeight = 1f),
            first = TextNode(id = "left_pane", text = "Series List"),
            second = TextNode(id = "right_pane", text = "Page Inspector")
        )

        lateinit var resizeLabel: String
        renderSduiNode(
            node = splitNode,
            width = 800.dp,
            height = 400.dp,
            onResolveStrings = { labels -> resizeLabel = labels.resizePane },
            onEvent = { events += it }
        )

        composeTestRule.onNodeWithContentDescription(resizeLabel).performMouseInput {
            moveTo(center)
            press(MouseButton.Primary)
            moveBy(Offset(x = 160f, y = 0f))
            release(MouseButton.Primary)
        }
        composeTestRule.waitForIdle()

        val valueEvents = events.filterIsInstance<UiEvent.ValueChanged>()
        assertTrue(valueEvents.isNotEmpty(), "Expected UiEvent.ValueChanged after dragging SplitPane handle")
        assertEquals("workspace_split", valueEvents.last().nodeId)
        assertTrue(valueEvents.last().value > 0.55, "Dragging divider right should increase split fraction")
    }

    private data class LocalizedSduiLabels(
        val dragReorder: String,
        val showSecret: String,
        val hideSecret: String,
        val resizePane: String
    )

    private fun renderSduiNode(
        node: UiNode,
        width: Dp,
        height: Dp,
        onResolveStrings: (LocalizedSduiLabels) -> Unit = {},
        onEvent: (UiEvent) -> Unit = {}
    ) {
        composeTestRule.setContent {
            val labels = LocalizedSduiLabels(
                dragReorder = stringResource(Res.string.sdui_drag_reorder),
                showSecret = stringResource(Res.string.sdui_show_secret),
                hideSecret = stringResource(Res.string.sdui_hide_secret),
                resizePane = stringResource(Res.string.sdui_resize_pane)
            )
            onResolveStrings(labels)
            AppTheme(appearance = AppearanceSettings()) {
                Box(modifier = Modifier.size(width, height)) {
                    SafeUiNodeRenderer(node = node, onEvent = onEvent)
                }
            }
        }
    }
}
