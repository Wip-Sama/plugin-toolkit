package org.wip.complete

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    fun testLifecycleHooks() {
        val settings = CompleteExampleSettings()
        val plugin = CompleteExamplePlugin(settings)
        val logger = FakePluginLogger()
        val context = TestPluginContext(logger = logger)

        assertTrue(plugin.onLoad(logger).isSuccess)
        assertTrue(plugin.validate(logger, context).isSuccess)
        assertTrue(plugin.onUpdate(logger, context).isSuccess)
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
    fun testCapabilityWithFileDefaults() {
        val settings = CompleteExampleSettings()
        val plugin = CompleteExamplePlugin(settings)

        val result = plugin.capabilityWithFileDefaults()
        assertTrue(result.contains("default_input.txt"))
        assertTrue(result.contains("default_output.txt"))
    }

    @Test
    fun testCapabilityWithComplexObjectsAndSemanticTypes() {
        val settings = CompleteExampleSettings()
        val plugin = CompleteExamplePlugin(settings)
        val inputPacket = UserDataPacket("pkt_001", 85.0, listOf("v1", "test"))

        val paletteResult = plugin.capabilityWithComplexObjectsAndSemanticTypes(
            packet = inputPacket,
            baseColor = "rgb(100, 150, 200)"
        )

        assertEquals("rgb(100, 150, 200)", paletteResult.primaryColor)
        assertEquals("rgb(255, 255, 255)", paletteResult.accentColor)
        assertEquals(95.0, paletteResult.packet.score)
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

    @Test
    fun testManifestSettingsRequiredFlag() {
        val manifestStream = javaClass.classLoader.getResourceAsStream("META-INF/manifest.json")
        kotlin.test.assertNotNull(manifestStream, "META-INF/manifest.json resource should exist")
        val content = manifestStream.bufferedReader().readText()
        val jsonElement = Json.parseToJsonElement(content)
        val settingsObj = jsonElement.jsonObject["settings"]?.jsonObject
        val secretTokenObj = settingsObj?.get("secretToken")?.jsonObject
        val isRequired = secretTokenObj?.get("required")?.jsonPrimitive?.booleanOrNull
        assertEquals(true, isRequired, "secretToken should have required=true in manifest.json")
    }

    @Test
    fun testToggleFeatureLockActionAndCausality() = runTest {
        val settings = CompleteExampleSettings()
        val plugin = CompleteExamplePlugin(settings)
        val context = TestPluginContext()

        val initialLocks = plugin.checkLocks(context)
        assertEquals(false, initialLocks["feature_unlocked"])

        plugin.toggleFeatureLock(unlocked = true, context = context)

        val updatedLocks = plugin.checkLocks(context)
        assertEquals(true, updatedLocks["feature_unlocked"])

        val result = plugin.capabilityWithLockRequirement(feature = RestrictedFeatureEnum.STANDARD, context = context)
        assertTrue(result.startsWith("Feature is unlocked and operational!"))

        plugin.toggleFeatureLock(unlocked = false, context = context)
        val relockedLocks = plugin.checkLocks(context)
        assertEquals(false, relockedLocks["feature_unlocked"])
    }
}

