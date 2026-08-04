package org.wip.complete

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.wip.plugintoolkit.api.ExecutionFileSystem
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.HostFileSystem
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.PluginFileSystem
import org.wip.plugintoolkit.api.PluginLogger
import org.wip.plugintoolkit.api.PluginSignal
import org.wip.plugintoolkit.api.PluginSignalManager
import org.wip.plugintoolkit.api.PluginStorage
import org.wip.plugintoolkit.api.ProgressReporter
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompleteExampleTest {

    private class FakePluginLogger : PluginLogger {
        val logs = mutableListOf<String>()
        override fun verbose(message: String) { logs.add("VERBOSE: $message") }
        override fun debug(message: String) { logs.add("DEBUG: $message") }
        override fun info(message: String) { logs.add("INFO: $message") }
        override fun warn(message: String) { logs.add("WARN: $message") }
        override fun error(message: String, throwable: Throwable?) { logs.add("ERROR: $message") }
    }

    private class FakeProgressReporter : ProgressReporter {
        var lastProgress: Float = 0f
        override fun report(progress: Float) { lastProgress = progress }
    }

    private class FakePluginSignalManager : PluginSignalManager {
        private val handlers = mutableListOf<suspend (PluginSignal) -> Unit>()
        override fun onSignal(handler: suspend (PluginSignal) -> Unit) { handlers.add(handler) }
        override suspend fun sendSignal(signal: PluginSignal) { handlers.forEach { it(signal) } }
    }

    private class FakePluginStorage : PluginStorage {
        val data = mutableMapOf<String, JsonElement>()
        override suspend fun get(key: String): JsonElement? = data[key]
        override suspend fun put(key: String, value: JsonElement) { data[key] = value }
        override suspend fun getAll(): Map<String, JsonElement> = data
        override suspend fun remove(key: String) { data.remove(key) }
    }

    private class TestPluginContext(
        override val logger: PluginLogger = FakePluginLogger(),
        override val progress: ProgressReporter = FakeProgressReporter(),
        override val signals: PluginSignalManager = FakePluginSignalManager(),
        override val storage: PluginStorage = FakePluginStorage(),
        override val settings: Map<String, JsonElement> = emptyMap()
    ) : PluginContext {
        override val fileSystem: PluginFileSystem get() = throw UnsupportedOperationException()
        override val cacheFileSystem: PluginFileSystem get() = throw UnsupportedOperationException()
        override val executionFileSystem: ExecutionFileSystem get() = throw UnsupportedOperationException()
        override val hostFileSystem: HostFileSystem get() = throw UnsupportedOperationException()

        override fun setRequiredAction(actionName: String?) {}
    }

    @Test
    fun testPluginValidationSuccess() {
        val settings = CompleteExampleSettings(secretToken = "valid_token")
        val plugin = CompleteExamplePlugin(settings)
        val context = TestPluginContext()
        val result = plugin.validate(context.logger, context)
        assertTrue(result.isSuccess)
    }

    @Test
    fun testPluginValidationFailure() {
        val settings = CompleteExampleSettings(secretToken = "")
        val plugin = CompleteExamplePlugin(settings)
        val context = TestPluginContext()
        val result = plugin.validate(context.logger, context)
        assertTrue(result.isFailure)
    }

    @Test
    fun testCapabilityWithPauseResume() = runTest {
        val settings = CompleteExampleSettings()
        val plugin = CompleteExamplePlugin(settings)
        val signals = FakePluginSignalManager()
        val context = TestPluginContext(signals = signals)

        val signalJob = launch {
            delay(20)
            signals.sendSignal(PluginSignal.PAUSE)
        }

        val result = plugin.capabilityWithPauseResume(
            totalSteps = 5,
            stepDelayMs = 50,
            resumeState = null,
            context = context
        )

        signalJob.join()
        assertTrue(result is ExecutionResult.Paused, "Expected result to be ExecutionResult.Paused but was $result")
    }

    @Test
    fun testCapabilityWithFileAccess() {
        val settings = CompleteExampleSettings()
        val plugin = CompleteExamplePlugin(settings)
        val tempInput = File.createTempFile("test_input", ".txt")
        val tempOutput = File.createTempFile("test_output", ".txt")
        tempInput.writeText("hello world")

        try {
            val result = plugin.capabilityWithFileAccess(tempInput.absolutePath, tempOutput.absolutePath)
            assertEquals(200, result.status)
            assertTrue(tempOutput.readText().contains("HELLO WORLD"))
        } finally {
            tempInput.delete()
            tempOutput.delete()
        }
    }

    @Test
    fun testCapabilityWithDataStorage() = runTest {
        val settings = CompleteExampleSettings()
        val plugin = CompleteExamplePlugin(settings)
        val context = TestPluginContext()

        val response = plugin.capabilityWithDataStorage("user_theme", "dark", context)
        assertTrue(response.contains("dark"))
    }

    @Test
    fun testCapabilityWithFlowContext() {
        val settings = CompleteExampleSettings(userId = "user_123")
        val plugin = CompleteExamplePlugin(settings)
        val response = plugin.capabilityWithFlowContext(FeatureMode.LOCAL, mapOf("key" to "value"))
        assertTrue(response.contains("LOCAL"))
        assertTrue(response.contains("user_123"))
    }
}
