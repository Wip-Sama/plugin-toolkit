package org.wip.plugintoolkit.api.sdui

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.wip.plugintoolkit.api.ExecutionFileSystem
import org.wip.plugintoolkit.api.HostFileSystem
import org.wip.plugintoolkit.api.HttpMethod
import org.wip.plugintoolkit.api.HttpRequest
import org.wip.plugintoolkit.api.HttpResponse
import org.wip.plugintoolkit.api.MultipartPart
import org.wip.plugintoolkit.api.NoOpPluginSecretStorage
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginFileSystem
import org.wip.plugintoolkit.api.PluginLogger
import org.wip.plugintoolkit.api.PluginNetworkClient
import org.wip.plugintoolkit.api.PluginNotificationType
import org.wip.plugintoolkit.api.PluginResponse
import org.wip.plugintoolkit.api.PluginSignalManager
import org.wip.plugintoolkit.api.PluginStorage
import org.wip.plugintoolkit.api.ProgressReporter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SduiExtensionsAndHttpTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    @Test
    fun testGridNodeAndExtendedNodesSerializationRoundTrip() {
        val tree = uiTree {
            column(id = "root_col", spacing = SpacingSize.SMALL, reorderable = true) {
                textInput(
                    id = "secret_input",
                    text = "my-secret",
                    label = "Token",
                    isSecret = true
                )
                listItem(
                    id = "row_1",
                    title = "Series A",
                    leadingAsset = AssetReference.Relative("covers/series_a.png"),
                    action = "open_series:a",
                    selected = true
                )
                splitPane(
                    id = "main_split",
                    splitFraction = 0.4f,
                    isHorizontal = true,
                    resizable = true,
                    first = { text(id = "left_txt", text = "Left") },
                    second = {
                        grid(
                            id = "page_grid",
                            columns = 3,
                            minItemWidthDp = 140f,
                            spacing = SpacingSize.MEDIUM,
                            reorderable = true
                        ) {
                            image(
                                id = "img_1",
                                asset = AssetReference.Uri("https://example.org/p1.png"),
                                contentDescription = "Page 1",
                                contentScale = ImageScale.CROP,
                                clickable = true,
                                action = "preview:1",
                                selected = true,
                                badgeText = "#1"
                            )
                        }
                    }
                )
            }
        }

        val encoded = json.encodeToString(UiNodeTree.serializer(), tree)
        val decoded = json.decodeFromString(UiNodeTree.serializer(), encoded)

        val col = assertIs<ColumnNode>(decoded.root)
        assertTrue(col.reorderable)

        val secretInput = assertIs<TextInputNode>(col.children[0])
        assertTrue(secretInput.isSecret)

        val item = assertIs<ListItemNode>(col.children[1])
        assertEquals(AssetReference.Relative("covers/series_a.png"), item.leadingAsset)
        assertEquals("open_series:a", item.action)

        val split = assertIs<SplitPaneNode>(col.children[2])
        assertTrue(split.resizable)
        val boxSecond = assertIs<BoxNode>(split.second)
        val grid = assertIs<GridNode>(boxSecond.children.first())
        assertEquals(3, grid.columns)
        assertEquals(140f, grid.minItemWidthDp)
        assertEquals(SpacingSize.MEDIUM, grid.spacing)
        assertTrue(grid.reorderable)

        val image = assertIs<ImageNode>(grid.children.first())
        assertEquals(ImageScale.CROP, image.contentScale)
        assertTrue(image.clickable)
        assertEquals("preview:1", image.action)
        assertTrue(image.selected)
        assertEquals("#1", image.badgeText)
    }

    @Test
    fun testItemsReorderedEventSerializationRoundTrip() {
        val event = UiEvent.ItemsReordered(
            nodeId = "page_grid",
            itemIds = listOf("p2", "p1", "p3"),
            fromIndex = 1,
            toIndex = 0
        )
        val serialized = json.encodeToString(UiEventSerializer, event)
        assertTrue(serialized.contains("\"type\":\"items_reordered\""))

        val decoded = json.decodeFromString(UiEventSerializer, serialized)
        val reordered = assertIs<UiEvent.ItemsReordered>(decoded)
        assertEquals("page_grid", reordered.nodeId)
        assertEquals(listOf("p2", "p1", "p3"), reordered.itemIds)
        assertEquals(1, reordered.fromIndex)
        assertEquals(0, reordered.toIndex)
    }

    @Test
    fun testHttpRequestResponseAndContextDefaults() = runTest {
        val part = MultipartPart(
            name = "file",
            data = byteArrayOf(1, 2, 3),
            filename = "page.png",
            contentType = "image/png"
        )
        val req1 = HttpRequest(
            url = "https://api.example.org/upload",
            method = HttpMethod.PUT,
            headers = mapOf("Authorization" to "Bearer abc"),
            body = byteArrayOf(10, 20),
            multipartParts = listOf(part),
            timeoutMs = 5000L
        )
        val req2 = req1.copy(body = byteArrayOf(10, 20))
        assertEquals(req1, req2)
        assertEquals(req1.hashCode(), req2.hashCode())

        val resp = HttpResponse(
            statusCode = 201,
            headers = mapOf("X-RateLimit-Remaining" to listOf("42")),
            body = "{\"ok\":true}".encodeToByteArray()
        )
        assertTrue(resp.isSuccess)
        assertEquals("{\"ok\":true}", resp.bodyAsText())
        assertEquals("42", resp.header("x-ratelimit-remaining"))
        assertNull(resp.header("Missing-Header"))

        val legacyClient = object : PluginNetworkClient {
            override suspend fun get(url: String, headers: Map<String, String>): ByteArray = "hello".encodeToByteArray()
            override suspend fun getText(url: String, headers: Map<String, String>): String = "hello"
            override suspend fun post(url: String, body: ByteArray, headers: Map<String, String>): ByteArray = body
            override suspend fun postText(url: String, body: String, headers: Map<String, String>): String = body
        }
        val legacyGet = legacyClient.request(HttpRequest("https://example.org", HttpMethod.GET))
        assertTrue(legacyGet.isSuccess)
        assertEquals("hello", legacyGet.bodyAsText())

        val legacyDelete = legacyClient.request(HttpRequest("https://example.org", HttpMethod.DELETE))
        assertFalse(legacyDelete.isSuccess)
        assertEquals(501, legacyDelete.statusCode)

        // Verify NoOp secret storage and Context/SessionContext defaults
        assertNull(NoOpPluginSecretStorage.getSecret("k"))
        NoOpPluginSecretStorage.putSecret("k", "v")
        assertTrue(NoOpPluginSecretStorage.listSecretKeys().isEmpty())

        val toasts = mutableListOf<String>()
        val fakeCtx = object : PluginContext {
            override val logger: PluginLogger get() = throw UnsupportedOperationException()
            override val progress: ProgressReporter get() = throw UnsupportedOperationException()
            override val fileSystem: PluginFileSystem get() = throw UnsupportedOperationException()
            override val cacheFileSystem: PluginFileSystem get() = throw UnsupportedOperationException()
            override val executionFileSystem: ExecutionFileSystem get() = throw UnsupportedOperationException()
            override val hostFileSystem: HostFileSystem get() = throw UnsupportedOperationException()
            override val settings: Map<String, JsonElement> = emptyMap()
            override val storage: PluginStorage get() = throw UnsupportedOperationException()
            override val signals: PluginSignalManager get() = throw UnsupportedOperationException()
            override fun setRequiredAction(actionName: String?) {}
            override fun showToast(message: String) { toasts.add(message) }
        }
        fakeCtx.showNotification("Upload", "Done", PluginNotificationType.SUCCESS)
        assertEquals(listOf("Upload: Done"), toasts)

        val sessionCtx = object : UiSessionContext, PluginContext by fakeCtx {
            override fun completeSession(response: PluginResponse) {}
            override fun cancelSession(reason: String) {}
        }
        assertNull(sessionCtx.pickFile())
        assertTrue(sessionCtx.pickFiles().isEmpty())
        assertNull(sessionCtx.pickDirectory())
        assertNull(sessionCtx.saveFile("a.json", "json", byteArrayOf(1)))
    }
}
