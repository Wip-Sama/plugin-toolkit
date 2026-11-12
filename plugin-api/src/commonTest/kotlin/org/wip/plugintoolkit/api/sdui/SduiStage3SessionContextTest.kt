package org.wip.plugintoolkit.api.sdui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.PluginResponse
import org.wip.plugintoolkit.api.ProgressData
import org.wip.plugintoolkit.api.ProgressDisplayMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SduiStage3SessionContextTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    @Test
    fun testCapabilityJobSnapshotSerialization() {
        val snapshot = CapabilityJobSnapshot(
            jobId = "job_test_123",
            capabilityName = "processChapter",
            status = CapabilityJobStatus.RUNNING,
            primaryProgress = ProgressData.percentage(0.45f, "Processing page 9/20"),
            secondaryProgress = ProgressData.ratio(9.0, 20.0, "pages", "Page progress"),
            recentLogs = listOf("Initialized job", "Loaded images", "Running upscale"),
            startedAtEpochMs = 1700000000000L
        )

        assertFalse(snapshot.isTerminal)
        assertTrue(snapshot.isRunning)

        val serialized = json.encodeToString(snapshot)
        val deserialized = json.decodeFromString<CapabilityJobSnapshot>(serialized)

        assertEquals(snapshot.jobId, deserialized.jobId)
        assertEquals(snapshot.capabilityName, deserialized.capabilityName)
        assertEquals(CapabilityJobStatus.RUNNING, deserialized.status)
        assertEquals(0.45f, deserialized.primaryProgress?.fraction)
        assertEquals("Processing page 9/20", deserialized.primaryProgress?.message)
        assertEquals(9.0, deserialized.secondaryProgress?.current)
        assertEquals(20.0, deserialized.secondaryProgress?.total)
        assertEquals(3, deserialized.recentLogs.size)
        assertEquals(1700000000000L, deserialized.startedAtEpochMs)
    }

    @Test
    fun testCapabilityJobSnapshotTerminalStatus() {
        val completedSnap = CapabilityJobSnapshot(
            jobId = "job_done",
            capabilityName = "uploadPages",
            status = CapabilityJobStatus.COMPLETED,
            result = ExecutionResult.Success(PluginResponse(result = JsonPrimitive("ok"))),
            completedAtEpochMs = 1700000050000L
        )
        assertTrue(completedSnap.isTerminal)
        assertFalse(completedSnap.isRunning)

        val failedSnap = CapabilityJobSnapshot(
            jobId = "job_err",
            capabilityName = "uploadPages",
            status = CapabilityJobStatus.FAILED,
            errorMessage = "Network timeout"
        )
        assertTrue(failedSnap.isTerminal)

        val cancelledSnap = CapabilityJobSnapshot(
            jobId = "job_cancel",
            capabilityName = "uploadPages",
            status = CapabilityJobStatus.CANCELLED
        )
        assertTrue(cancelledSnap.isTerminal)
    }

    @Test
    fun testInteractiveUiSessionBuilderEmitsInitialTree() = runBlocking(Dispatchers.Default) {
        val dummyContext = object : UiSessionContext {
            override val logger get() = throw UnsupportedOperationException()
            override val progress get() = throw UnsupportedOperationException()
            override val fileSystem get() = throw UnsupportedOperationException()
            override val cacheFileSystem get() = throw UnsupportedOperationException()
            override val executionFileSystem get() = throw UnsupportedOperationException()
            override val hostFileSystem get() = throw UnsupportedOperationException()
            override val settings get() = mapOf("author" to JsonPrimitive("Studio"))
            override val storage get() = throw UnsupportedOperationException()
            override val signals get() = throw UnsupportedOperationException()

            override fun completeSession(response: PluginResponse) {}
            override fun cancelSession(reason: String) {}
            override fun setRequiredAction(actionName: String?) {}
        }

        data class DemoState(val count: Int)

        val eventsFlow = MutableSharedFlow<UiEvent>()
        val trees = mutableListOf<UiNodeTree>()

        val job = launch {
            interactiveUiSession(
                initialState = DemoState(count = 0),
                context = dummyContext,
                events = eventsFlow,
                onEvent = { event ->
                    if (event is UiEvent.Click && event.nodeId == "btn_increment") {
                        updateState { it.copy(count = it.count + 1) }
                    }
                },
                render = {
                    uiTree {
                        text("Count: $count", id = "count_lbl")
                    }
                }
            ).collect {
                trees.add(it)
            }
        }

        // Wait for initial tree emission
        withTimeout(3000) {
            while (trees.isEmpty()) {
                delay(10)
            }
        }
        assertEquals(1, trees.size)
        assertEquals("Count: 0", (trees[0].root as TextNode).text)

        // Dispatch user interaction event
        eventsFlow.emit(UiEvent.Click("btn_increment"))

        // Wait for reactive updated tree emission
        withTimeout(3000) {
            while (trees.size < 2) {
                delay(10)
            }
        }
        assertEquals(2, trees.size)
        assertEquals("Count: 1", (trees[1].root as TextNode).text)

        job.cancel()
    }
}
