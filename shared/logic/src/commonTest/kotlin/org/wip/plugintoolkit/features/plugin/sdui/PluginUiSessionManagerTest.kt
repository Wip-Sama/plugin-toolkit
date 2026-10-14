package org.wip.plugintoolkit.features.plugin.sdui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.ExecutionFileSystem
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.HostFileSystem
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginFileSystem
import org.wip.plugintoolkit.api.PluginLogger
import org.wip.plugintoolkit.api.PluginResponse
import org.wip.plugintoolkit.api.PluginSignal
import org.wip.plugintoolkit.api.PluginSignalManager
import org.wip.plugintoolkit.api.PluginStorage
import org.wip.plugintoolkit.api.ProgressReporter
import org.wip.plugintoolkit.api.RelativePath
import org.wip.plugintoolkit.api.sdui.ButtonNode
import org.wip.plugintoolkit.api.sdui.ColumnNode
import org.wip.plugintoolkit.api.sdui.ErrorBoundaryNode
import org.wip.plugintoolkit.api.sdui.InteractiveUiProcessor
import org.wip.plugintoolkit.api.sdui.TextNode
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.api.sdui.UiNodeTree
import org.wip.plugintoolkit.api.sdui.UiSessionContext
import org.wip.plugintoolkit.api.sdui.uiTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PluginUiSessionManagerTest {

    private class DummyPluginContext : PluginContext {
        override val logger: PluginLogger = object : PluginLogger {
            override fun info(message: String) {}
            override fun warn(message: String) {}
            override fun error(message: String, throwable: Throwable?) {}
            override fun debug(message: String) {}
            override fun verbose(message: String) {}
        }
        override val progress: ProgressReporter = object : ProgressReporter {
            override fun report(progress: Float) {}
            override fun report(progress: Float, message: String?) {}
        }
        override val fileSystem: PluginFileSystem = object : PluginFileSystem {
            override suspend fun readFile(relativePath: RelativePath): ByteArray? = null
            override suspend fun readTextFile(relativePath: RelativePath): String? = null
            override suspend fun writeFile(relativePath: RelativePath, data: ByteArray): Result<Unit> = Result.success(Unit)
            override suspend fun writeTextFile(relativePath: RelativePath, text: String): Result<Unit> = Result.success(Unit)
            override suspend fun exists(relativePath: RelativePath): Boolean = false
            override suspend fun listFiles(relativePath: RelativePath): List<String> = emptyList()
            override suspend fun deleteFile(relativePath: RelativePath): Result<Unit> = Result.success(Unit)
            override fun getBasePath(): String = "/tmp"
            override suspend fun extractResource(resourcePath: String, targetRelativePath: RelativePath): Result<Unit> = Result.success(Unit)
        }
        override val cacheFileSystem: PluginFileSystem get() = fileSystem
        override val executionFileSystem: ExecutionFileSystem = object : ExecutionFileSystem {
            override suspend fun readFile(relativePath: RelativePath): ByteArray? = null
            override suspend fun readTextFile(relativePath: RelativePath): String? = null
            override suspend fun writeFile(relativePath: RelativePath, data: ByteArray): Result<Unit> = Result.success(Unit)
            override suspend fun writeTextFile(relativePath: RelativePath, text: String): Result<Unit> = Result.success(Unit)
            override suspend fun exists(relativePath: RelativePath): Boolean = false
            override suspend fun listFiles(relativePath: RelativePath): List<String> = emptyList()
            override suspend fun deleteFile(relativePath: RelativePath): Result<Unit> = Result.success(Unit)
            override fun getBasePath(): String = "/tmp/exec"
        }
        override val hostFileSystem: HostFileSystem = object : HostFileSystem {
            override suspend fun readFile(absolutePath: String): ByteArray? = null
            override suspend fun readTextFile(absolutePath: String): String? = null
            override suspend fun writeFile(absolutePath: String, data: ByteArray): Result<Unit> = Result.success(Unit)
            override suspend fun writeTextFile(absolutePath: String, text: String): Result<Unit> = Result.success(Unit)
            override suspend fun exists(absolutePath: String): Boolean = false
            override suspend fun listFiles(absolutePath: String): List<String> = emptyList()
            override suspend fun deleteFile(absolutePath: String): Result<Unit> = Result.success(Unit)
            override suspend fun createDirectory(absolutePath: String): Result<Unit> = Result.success(Unit)
        }
        override val settings: Map<String, JsonElement> = emptyMap()
        override val storage: PluginStorage = object : PluginStorage {
            private val map = mutableMapOf<String, JsonElement>()
            override suspend fun get(key: String): JsonElement? = map[key]
            override suspend fun put(key: String, value: JsonElement) { map[key] = value }
            override suspend fun getAll(): Map<String, JsonElement> = map.toMap()
            override suspend fun remove(key: String) { map.remove(key) }
        }
        override val signals: PluginSignalManager = object : PluginSignalManager {
            override fun onSignal(handler: suspend (PluginSignal) -> Unit) {}
            override suspend fun sendSignal(signal: PluginSignal) {}
        }
        var toastShown: String? = null
        override fun showToast(message: String) {
            toastShown = message
        }
        var lastRequiredAction: String? = null
        override fun setRequiredAction(actionName: String?) {
            lastRequiredAction = actionName
        }
    }

    @Test
    fun testSessionLifecycleNormalFlow() = runTest {
        val manager = PluginUiSessionManager(this, UnconfinedTestDispatcher(testScheduler))
        val context = DummyPluginContext()

        val processor = object : InteractiveUiProcessor {
            override fun openUiSession(
                context: UiSessionContext,
                events: Flow<UiEvent>
            ): Flow<UiNodeTree> = flow {
                emit(uiTree {
                    column {
                        text("Counter: 0", id = "counter_text")
                        button(id = "submit_btn", label = "Submit")
                    }
                })

                events.collect { event ->
                    if (event is UiEvent.Click && event.nodeId == "submit_btn") {
                        context.completeSession(PluginResponse(result = JsonPrimitive("success")))
                    }
                }
            }
        }

        val handle = manager.startSession("test.plugin", "demoCap", context, processor)
        assertTrue(handle.isActive)
        assertNotNull(manager.getSession(handle.sessionId))

        // Wait for initial tree
        advanceUntilIdle()
        val currentTree = handle.uiNodeTree.value
        val root = assertIs<ColumnNode>(currentTree.root)
        assertEquals(2, root.children.size)

        // Dispatch click to complete
        handle.dispatchEvent(UiEvent.Click(nodeId = "submit_btn"))
        advanceUntilIdle()

        val result = handle.awaitResult()
        val success = assertIs<ExecutionResult.Success>(result)
        assertEquals(JsonPrimitive("success"), success.response.result)
        assertFalse(handle.isActive)
        assertNull(manager.getSession(handle.sessionId))
    }

    @Test
    fun testSessionCancellation() = runTest {
        val manager = PluginUiSessionManager(this, UnconfinedTestDispatcher(testScheduler))
        val context = DummyPluginContext()

        val processor = object : InteractiveUiProcessor {
            override fun openUiSession(
                context: UiSessionContext,
                events: Flow<UiEvent>
            ): Flow<UiNodeTree> = flow {
                emit(uiTree {
                    text("Running...")
                })
            }
        }

        val handle = manager.startSession("test.plugin", "cancelCap", context, processor)
        advanceUntilIdle()
        assertTrue(handle.isActive)

        handle.cancel("User aborted")
        advanceUntilIdle()

        val result = handle.awaitResult()
        val error = assertIs<ExecutionResult.Error>(result)
        assertEquals("User aborted", error.message)
        assertFalse(handle.isActive)
    }

    @Test
    fun testSessionSanitizationOnEmission() = runTest {
        val manager = PluginUiSessionManager(this, UnconfinedTestDispatcher(testScheduler))
        val context = DummyPluginContext()

        val processor = object : InteractiveUiProcessor {
            override fun openUiSession(
                context: UiSessionContext,
                events: Flow<UiEvent>
            ): Flow<UiNodeTree> = flow {
                // Emit tree with colliding duplicate IDs
                emit(UiNodeTree(
                    root = ColumnNode(
                        id = "root",
                        children = listOf(
                            TextNode(id = "dup", text = "First"),
                            TextNode(id = "dup", text = "Second")
                        )
                    )
                ))
            }
        }

        val handle = manager.startSession("test.plugin", "sanitizeCap", context, processor)
        advanceUntilIdle()

        val tree = handle.uiNodeTree.value
        val root = assertIs<ColumnNode>(tree.root)
        assertEquals(2, root.children.size)
        assertIs<TextNode>(root.children[0])
        val errorNode = assertIs<ErrorBoundaryNode>(root.children[1])
        assertTrue(errorNode.errorMessage.contains("Duplicate node ID collision"))

        handle.close()
    }

    @Test
    fun testSessionManagerCloseAll() = runTest {
        val manager = PluginUiSessionManager(this, UnconfinedTestDispatcher(testScheduler))
        val context = DummyPluginContext()

        val processor = object : InteractiveUiProcessor {
            override fun openUiSession(
                context: UiSessionContext,
                events: Flow<UiEvent>
            ): Flow<UiNodeTree> = flow {
                emit(uiTree { text("Session") })
            }
        }

        val handle1 = manager.startSession("test.p1", "cap1", context, processor)
        val handle2 = manager.startSession("test.p2", "cap2", context, processor)

        advanceUntilIdle()
        assertNotNull(manager.getSession(handle1.sessionId))
        assertNotNull(manager.getSession(handle2.sessionId))

        manager.closeAll()
        assertFalse(handle1.isActive)
        assertFalse(handle2.isActive)
        assertNull(manager.getSession(handle1.sessionId))
        assertNull(manager.getSession(handle2.sessionId))
    }
}
