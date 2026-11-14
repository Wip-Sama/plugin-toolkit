package org.wip.plugintoolkit.features.plugin.sdui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.ExecutionFileSystem
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.HostFileSystem
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginFileSystem
import org.wip.plugintoolkit.api.PluginLogger
import org.wip.plugintoolkit.api.PluginNetworkClient
import org.wip.plugintoolkit.api.PluginResponse
import org.wip.plugintoolkit.api.PluginSignal
import org.wip.plugintoolkit.api.PluginSignalManager
import org.wip.plugintoolkit.api.PluginStorage
import org.wip.plugintoolkit.api.ProgressReporter
import org.wip.plugintoolkit.api.RelativePath
import org.wip.plugintoolkit.api.sdui.ColumnNode
import org.wip.plugintoolkit.api.sdui.GridNode
import org.wip.plugintoolkit.api.sdui.InteractiveUiProcessor
import org.wip.plugintoolkit.api.sdui.TextNode
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.api.sdui.UiNodeTree
import org.wip.plugintoolkit.api.sdui.UiSessionContext
import org.wip.plugintoolkit.api.sdui.UiShortcut
import org.wip.plugintoolkit.api.sdui.uiTree
import org.wip.plugintoolkit.features.plugin.logic.HostFileSystemImpl
import org.wip.plugintoolkit.features.plugin.logic.ResourceUsageTracker
import org.wip.plugintoolkit.features.plugin.logic.TrackingHostFileSystem
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

private const val LARGE_TREE_ITEM_COUNT = 1600

/**
 * Unit tests verifying Stage 2 SDUI sanitization limits ([GridNode] clamping, 4000-node ceiling)
 * and [PluginUiSessionManager] extensions (lossless [UiEvent.ItemsReordered] lane, host file/dir
 * picker grants via [TrackingHostFileSystem], and cross-tool navigation emission).
 */
class PluginUiSessionStage2Test {

    private lateinit var tempRootDir: File
    private lateinit var initialAllowedDir: File
    private lateinit var pickedExternalDir: File

    @BeforeTest
    fun setUp() {
        tempRootDir = File(System.getProperty("java.io.tmpdir"), "sdui_stage2_test_${System.nanoTime()}").apply {
            mkdirs()
        }
        initialAllowedDir = File(tempRootDir, "initial").apply { mkdirs() }
        pickedExternalDir = File(tempRootDir, "picked_manga_dir").apply { mkdirs() }
    }

    @AfterTest
    fun tearDown() {
        tempRootDir.deleteRecursively()
    }

    @Test
    fun `UiAstSanitizer allows 1600 nodes and clamps GridNode columns and item widths`() {
        val bulkTree = uiTree {
            column(id = "bulk_col") {
                repeat(LARGE_TREE_ITEM_COUNT) { idx ->
                    text("Row $idx", id = "row_$idx")
                }
            }
        }
        val sanitizedBulk = UiAstSanitizer.sanitize(bulkTree)
        val bulkColumn = sanitizedBulk.root as ColumnNode
        assertEquals(LARGE_TREE_ITEM_COUNT, bulkColumn.children.size)

        val dirtyGridTree = UiNodeTree(
            root = GridNode(
                id = "dirty_grid",
                columns = 99,
                minItemWidthDp = Float.NaN,
                children = listOf(TextNode(id = "cell", text = "Item"))
            )
        )
        val clampedHigh = UiAstSanitizer.sanitize(dirtyGridTree).root as GridNode
        assertEquals(12, clampedHigh.columns)
        assertEquals(156f, clampedHigh.minItemWidthDp)

        val tooSmallGridTree = UiNodeTree(
            root = GridNode(
                id = "small_grid",
                columns = 0,
                minItemWidthDp = 12f,
                children = listOf(TextNode(id = "cell", text = "Item"))
            )
        )
        val clampedLow = UiAstSanitizer.sanitize(tooSmallGridTree).root as GridNode
        assertEquals(1, clampedLow.columns)
        assertEquals(72f, clampedLow.minItemWidthDp)

        val tooWideGridTree = UiNodeTree(
            root = GridNode(
                id = "wide_grid",
                columns = 3,
                minItemWidthDp = 950f,
                children = listOf(TextNode(id = "cell", text = "Item"))
            )
        )
        val clampedWidth = UiAstSanitizer.sanitize(tooWideGridTree).root as GridNode
        assertEquals(3, clampedWidth.columns)
        assertEquals(600f, clampedWidth.minItemWidthDp)
    }

    @Test
    fun `PluginUiSessionManager grants dynamic HostFileSystem access on pickers handles saveFile lossless reorder and emits navigation`() =
        runTest {
            val singlePickedFile = File(pickedExternalDir, "cover.png").apply {
                writeBytes(byteArrayOf(1, 2, 3))
            }
            val multiFileOne = File(pickedExternalDir, "p01.png").apply { writeBytes(byteArrayOf(10)) }
            val multiFileTwo = File(pickedExternalDir, "p02.png").apply { writeBytes(byteArrayOf(20)) }
            val exportedFile = File(pickedExternalDir, "cubari.json")

            val tracker = ResourceUsageTracker()
            val trackingHostFs = TrackingHostFileSystem(
                delegate = HostFileSystemImpl(
                    allowedPaths = listOf(initialAllowedDir.canonicalPath),
                    isDestructiveAllowed = true
                ),
                tracker = tracker
            )
            val context = createStage2TestContext(hostFileSystem = trackingHostFs)

            // Confirm outside paths fail prior to picker interaction
            assertFailsWith<SecurityException> {
                trackingHostFs.readFile(singlePickedFile.canonicalPath)
            }

            val hostBridge = object : PluginUiHostBridge {
                override suspend fun pickFile(title: String?, allowedExtensions: List<String>): String? =
                    singlePickedFile.canonicalPath

                override suspend fun pickFiles(title: String?, allowedExtensions: List<String>): List<String> =
                    listOf(multiFileOne.canonicalPath, multiFileTwo.canonicalPath)

                override suspend fun pickDirectory(title: String?): String? =
                    pickedExternalDir.canonicalPath

                override suspend fun saveFile(defaultName: String, extension: String, bytes: ByteArray): String? {
                    exportedFile.writeBytes(bytes)
                    return exportedFile.canonicalPath
                }
            }

            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val manager = PluginUiSessionManager(appScope = scope, dispatcher = Dispatchers.Default)

            val receivedEvents = mutableListOf<UiEvent>()
            var pickedSingleResult: String? = null
            var pickedManyResult: List<String> = emptyList()
            var pickedDirResult: String? = null
            var savedFilePath: String? = null

            val processor = object : InteractiveUiProcessor {
                override fun openUiSession(context: UiSessionContext, events: Flow<UiEvent>): Flow<UiNodeTree> =
                    channelFlow {
                        send(uiTree { text("Ready") })
                        pickedSingleResult = context.pickFile("Pick Cover", listOf("png"))
                        pickedManyResult = context.pickFiles("Pick Pages", listOf("png"))
                        pickedDirResult = context.pickDirectory("Pick Repo")
                        savedFilePath = context.saveFile("cubari", "json", "{\"ok\":true}".encodeToByteArray())
                        context.openInteractiveTool(
                            capabilityName = "pageCompositor",
                            initialParameters = mapOf("seriesId" to JsonPrimitive("series-42"))
                        )
                        events.collect { event ->
                            receivedEvents += event
                            if (receivedEvents.count { it is UiEvent.ItemsReordered } == 3) {
                                context.completeSession(PluginResponse(result = JsonPrimitive("done")))
                            }
                        }
                    }
            }

            val navDeferred = async { manager.navigationRequests.first() }
            val handle = manager.startSession(
                pluginId = "test.uploader",
                capabilityName = "chapterPublisher",
                context = context,
                processor = processor,
                hostBridge = hostBridge
            )

            // Wait for session initialization and bridge calls
            var attempts = 0
            while (savedFilePath == null && attempts < 100) {
                delay(20)
                attempts++
            }

            val navRequest = navDeferred.await()
            assertEquals("test.uploader", navRequest.pluginId)
            assertEquals("pageCompositor", navRequest.capabilityName)
            assertEquals(JsonPrimitive("series-42"), navRequest.initialParameters["seriesId"])

            assertEquals(singlePickedFile.canonicalPath, pickedSingleResult)
            assertEquals(listOf(multiFileOne.canonicalPath, multiFileTwo.canonicalPath), pickedManyResult)
            assertEquals(pickedExternalDir.canonicalPath, pickedDirResult)
            assertEquals(exportedFile.canonicalPath, savedFilePath)

            // Dynamically granted paths must now be readable through HostFileSystem without SecurityException
            assertTrue(trackingHostFs.exists(singlePickedFile.canonicalPath))
            assertTrue(trackingHostFs.exists(multiFileOne.canonicalPath))
            assertTrue(trackingHostFs.exists(multiFileTwo.canonicalPath))
            assertEquals(1, trackingHostFs.readFile(multiFileOne.canonicalPath)?.size)

            // Rapidly dispatch 3 ItemsReordered events and verify lossless delivery
            handle.dispatchEvent(
                UiEvent.ItemsReordered("grid_pages", listOf("p2", "p1", "p3"), fromIndex = 0, toIndex = 1)
            )
            handle.dispatchEvent(
                UiEvent.ItemsReordered("grid_pages", listOf("p2", "p3", "p1"), fromIndex = 1, toIndex = 2)
            )
            handle.dispatchEvent(
                UiEvent.ItemsReordered("grid_pages", listOf("p3", "p2", "p1"), fromIndex = 0, toIndex = 1)
            )

            val result = handle.completionDeferred.await()
            assertIs<ExecutionResult.Success>(result)
            val reorderedEvents = receivedEvents.filterIsInstance<UiEvent.ItemsReordered>()
            assertEquals(3, reorderedEvents.size)
            assertEquals(listOf("p3", "p2", "p1"), reorderedEvents.last().itemIds)
            assertTrue(tracker.bytesRead >= 1L)

            scope.cancel()
        }

    @Test
    fun testFilesDroppedGrantsHostAccessAndShortcutTriggeredDispatches() = runTest {
        val droppedImage = File(pickedExternalDir, "dropped_page_01.png").apply {
            writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        }
        val tracker = ResourceUsageTracker()
        val trackingHostFs = TrackingHostFileSystem(
            delegate = HostFileSystemImpl(allowedPaths = listOf(initialAllowedDir.canonicalPath), isDestructiveAllowed = false),
            tracker = tracker
        )
        val context = createStage2TestContext(trackingHostFs)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager = PluginUiSessionManager(appScope = scope, dispatcher = Dispatchers.Default)

        val receivedEvents = mutableListOf<UiEvent>()
        val processor = object : InteractiveUiProcessor {
            override fun openUiSession(context: UiSessionContext, events: Flow<UiEvent>): Flow<UiNodeTree> =
                channelFlow {
                    send(uiTree { text("Ready for drops & hotkeys") })
                    events.collect { event ->
                        receivedEvents += event
                        if (receivedEvents.size >= 2) {
                            context.completeSession(PluginResponse(result = JsonPrimitive("ok")))
                        }
                    }
                }
        }

        val handle = manager.startSession(
            pluginId = "test.uploader",
            capabilityName = "chapterPublisher",
            context = context,
            processor = processor
        )

        handle.dispatchEvent(
            UiEvent.FilesDropped(
                nodeId = "img_grid_pane",
                action = "drop_page_files",
                paths = listOf(droppedImage.canonicalPath)
            )
        )
        handle.dispatchEvent(
            UiEvent.ShortcutTriggered(
                nodeId = "img_grid_pane",
                action = "remove_selected_page",
                key = UiShortcut.KEY_DELETE
            )
        )

        val completion = handle.completionDeferred.await()
        assertIs<ExecutionResult.Success>(completion)
        assertTrue(trackingHostFs.exists(droppedImage.canonicalPath), "Dropped file path must be granted host read access")
        assertEquals(4, trackingHostFs.readFile(droppedImage.canonicalPath)?.size)
        val dropEvent = receivedEvents.filterIsInstance<UiEvent.FilesDropped>().single()
        assertEquals(listOf(droppedImage.canonicalPath), dropEvent.paths)
        val shortcutEvent = receivedEvents.filterIsInstance<UiEvent.ShortcutTriggered>().single()
        assertEquals(UiShortcut.KEY_DELETE, shortcutEvent.key)
        assertEquals("remove_selected_page", shortcutEvent.action)

        scope.cancel()
    }

    private fun createStage2TestContext(hostFileSystem: HostFileSystem): PluginContext = object : PluginContext {
        override val logger: PluginLogger = object : PluginLogger {
            override fun verbose(message: String) = Unit
            override fun debug(message: String) = Unit
            override fun info(message: String) = Unit
            override fun warn(message: String) = Unit
            override fun error(message: String, throwable: Throwable?) = Unit
        }
        override val progress: ProgressReporter = object : ProgressReporter {
            override fun report(progress: Float) = Unit
        }
        override val fileSystem: PluginFileSystem = createInMemoryPluginFileSystem()
        override val cacheFileSystem: PluginFileSystem = createInMemoryPluginFileSystem()
        override val executionFileSystem: ExecutionFileSystem = object : ExecutionFileSystem,
            PluginFileSystem by createInMemoryPluginFileSystem() {
            override fun getBasePath(): String = ""
        }
        override val hostFileSystem: HostFileSystem = hostFileSystem
        override val settings: Map<String, JsonElement> = emptyMap()
        override val storage: PluginStorage = object : PluginStorage {
            override suspend fun get(key: String): JsonElement? = null
            override suspend fun put(key: String, value: JsonElement) = Unit
            override suspend fun getAll(): Map<String, JsonElement> = emptyMap()
            override suspend fun remove(key: String) = Unit
        }
        override val signals: PluginSignalManager = object : PluginSignalManager {
            override fun onSignal(handler: suspend (PluginSignal) -> Unit) = Unit
            override suspend fun sendSignal(signal: PluginSignal) = Unit
        }
        override val networkClient: PluginNetworkClient = object : PluginNetworkClient {
            override suspend fun get(url: String, headers: Map<String, String>): ByteArray = ByteArray(0)
            override suspend fun getText(url: String, headers: Map<String, String>): String = ""
            override suspend fun post(url: String, body: ByteArray, headers: Map<String, String>): ByteArray = ByteArray(0)
            override suspend fun postText(url: String, body: String, headers: Map<String, String>): String = ""
        }

        override fun setRequiredAction(actionName: String?) = Unit
    }

    private fun createInMemoryPluginFileSystem(): PluginFileSystem = object : PluginFileSystem {
        override suspend fun readFile(relativePath: RelativePath): ByteArray? = null
        override suspend fun readTextFile(relativePath: RelativePath): String? = null
        override suspend fun writeFile(relativePath: RelativePath, data: ByteArray): Result<Unit> = Result.success(Unit)
        override suspend fun writeTextFile(relativePath: RelativePath, text: String): Result<Unit> = Result.success(Unit)
        override suspend fun exists(relativePath: RelativePath): Boolean = false
        override suspend fun listFiles(relativePath: RelativePath): List<String> = emptyList()
        override suspend fun deleteFile(relativePath: RelativePath): Result<Unit> = Result.success(Unit)
        override fun getBasePath(): String = ""
        override suspend fun extractResource(resourcePath: String, targetRelativePath: RelativePath): Result<Unit> =
            Result.success(Unit)
    }
}
