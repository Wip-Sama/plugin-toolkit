package com.wip.operations

import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.PluginContext
import org.wip.plugintoolkit.api.ExecutionFileSystem
import org.wip.plugintoolkit.api.HostFileSystem
import org.wip.plugintoolkit.api.PluginFileSystem
import org.wip.plugintoolkit.api.PluginLogger
import org.wip.plugintoolkit.api.PluginSignal
import org.wip.plugintoolkit.api.PluginSignalManager
import org.wip.plugintoolkit.api.ProgressReporter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MathOperationsTest {

    // Simple fake/mock implementation of PluginContext
    private class FakePluginContext(
        override val logger: PluginLogger = FakePluginLogger(),
        override val progress: ProgressReporter,
        override val signals: PluginSignalManager = DefaultPluginSignalManager()
    ) : PluginContext {
        override val fileSystem: PluginFileSystem get() = throw UnsupportedOperationException()
        override val cacheFileSystem: PluginFileSystem get() = throw UnsupportedOperationException()
        override val executionFileSystem: ExecutionFileSystem get() = throw UnsupportedOperationException()
        override val hostFileSystem: HostFileSystem get() = throw UnsupportedOperationException()
        override val settings: Map<String, JsonElement> get() = emptyMap()
        override fun setRequiredAction(actionName: String?) {}
    }

    private class FakePluginLogger : PluginLogger {
        override fun verbose(message: String) { println("VERBOSE: $message") }
        override fun debug(message: String) { println("DEBUG: $message") }
        override fun info(message: String) { println("INFO: $message") }
        override fun warn(message: String) { println("WARN: $message") }
        override fun error(message: String, throwable: Throwable?) {
            println("ERROR: $message")
            throwable?.printStackTrace()
        }
    }

    private class DefaultPluginSignalManager : PluginSignalManager {
        private val signalHandlers = mutableListOf<suspend (PluginSignal) -> Unit>()
        override fun onSignal(handler: suspend (PluginSignal) -> Unit) {
            signalHandlers.add(handler)
        }
        override suspend fun sendSignal(signal: PluginSignal) {
            signalHandlers.forEach { it(signal) }
        }
    }

    @Test
    fun testSlowPausableSum() = runTest {
        val processor = MathProcessor(MathProcessorSettings())
        val values = listOf(10.0, 20.0, 30.0)

        // Setup a fake context that sends a PAUSE signal when progress reaches 1/3 (after processing index 0)
        val signals = DefaultPluginSignalManager()
        val progressReporter = object : ProgressReporter {
            override fun report(progress: Float) {
                // When we process index 0, progress is (0+1)/3 = 0.33333334
                if (progress > 0.3f && progress < 0.4f) {
                    launch {
                        signals.sendSignal(PluginSignal.PAUSE)
                    }
                }
            }
        }
        val context = FakePluginContext(progress = progressReporter, signals = signals)

        // Start execution
        val job = async {
            processor.slowPausableSum(
                values = values,
                stepDelay = 10L,
                resumeState = null,
                context = context
            )
        }

        val result1 = job.await()
        assertTrue(result1 is ExecutionResult.Paused)
        val resumeState = result1.resumeState

        // Verify state is correct: currentIndex should be 1, runningSum should be 10.0
        val state = Json.decodeFromJsonElement(SumProgressState.serializer(), resumeState)
        assertEquals(1, state.currentIndex)
        assertEquals(10.0, state.runningSum)

        // Resume execution with the saved state, but without pausing
        val progressReporter2 = object : ProgressReporter {
            override fun report(progress: Float) {}
        }
        val context2 = FakePluginContext(progress = progressReporter2)
        val result2 = processor.slowPausableSum(
            values = values,
            stepDelay = 10L,
            resumeState = resumeState,
            context = context2
        )

        assertTrue(result2 is ExecutionResult.Success)
        val response = result2.response
        assertEquals(60.0, response.result.jsonPrimitive.double)
    }

    @Test
    fun testSlowPausableFactorial() = runTest {
        val processor = MathProcessor(MathProcessorSettings())

        // Compute 4! = 24
        // Setup a fake context that sends a PAUSE signal when progress is reported
        val signals = DefaultPluginSignalManager()
        val progressReporter = object : ProgressReporter {
            override fun report(progress: Float) {
                // n is 4, target is 4. Loops from 2 to 4.
                // Progress is (currentN - 1) / (target - 1)
                // When currentN is 2, progress is (2-1)/(4-1) = 1/3 = 0.33333334
                if (progress > 0.3f && progress < 0.4f) {
                    launch {
                        signals.sendSignal(PluginSignal.PAUSE)
                    }
                }
            }
        }
        val context = FakePluginContext(progress = progressReporter, signals = signals)

        // Start execution
        val job = async {
            processor.slowPausableFactorial(
                n = 4,
                stepDelay = 10L,
                resumeState = null,
                context = context
            )
        }

        val result1 = job.await()
        assertTrue(result1 is ExecutionResult.Paused)
        val resumeState = result1.resumeState

        // Verify state: currentN should be 3, runningProduct should be 2L
        val state = Json.decodeFromJsonElement(FactorialProgressState.serializer(), resumeState)
        assertEquals(3, state.currentN)
        assertEquals(2L, state.runningProduct)

        // Resume execution
        val progressReporter2 = object : ProgressReporter {
            override fun report(progress: Float) {}
        }
        val context2 = FakePluginContext(progress = progressReporter2)
        val result2 = processor.slowPausableFactorial(
            n = 4,
            stepDelay = 10L,
            resumeState = resumeState,
            context = context2
        )

        assertTrue(result2 is ExecutionResult.Success)
        val response = result2.response
        assertEquals(24L, response.result.jsonPrimitive.long)
    }

    @Test
    fun testAdvancedGeneration() = runTest {
        val processor = MathProcessor(MathProcessorSettings())
        
        val voice = VoiceOption.SERVER
        val config = mapOf("speed" to 1.5, "pitch" to 0.8)
        
        val result = processor.advancedGeneration(voice, config)
        
        assertEquals("test-123", result.id)
        assertEquals("SERVER", result.properties["voice"])
        assertEquals(1, result.metadata["speed"])
        assertEquals(0, result.metadata["pitch"])
    }
    @Test
    fun testProcessComplexObject() = runTest {
        val processor = MathProcessor(MathProcessorSettings())
        val initialObj = ComplexObject("id1", mapOf("key" to "value"), mapOf("meta" to 1))
        
        val result = processor.processComplexObject(initialObj)
        
        assertEquals("id1-processed", result.id)
        assertEquals("value", result.properties["key"])
        assertEquals("true", result.properties["processed"])
        assertEquals(1, result.metadata["meta"])
    }

    @Test
    fun testMergeComplexObjects() = runTest {
        val processor = MathProcessor(MathProcessorSettings())
        val obj1 = ComplexObject("id1", mapOf("k1" to "v1"), mapOf("m1" to 1))
        val obj2 = ComplexObject("id2", mapOf("k2" to "v2"), mapOf("m2" to 2))
        
        val result = processor.mergeComplexObjects(listOf(obj1, obj2))
        
        assertEquals("id1-id2", result.id)
        assertEquals("v1", result.properties["k1"])
        assertEquals("v2", result.properties["k2"])
        assertEquals(1, result.metadata["m1"])
        assertEquals(2, result.metadata["m2"])
    }

    @Test
    fun testProcessDataPoints() = runTest {
        val processor = MathProcessor(MathProcessorSettings())
        val p1 = DataPoint(1.0, 2.0, "p1")
        val p2 = DataPoint(3.0, 4.0, "p2")
        
        val result = processor.processDataPoints(listOf(p1, p2))
        
        assertEquals(4.0, result.x)
        assertEquals(6.0, result.y)
        assertEquals("centroid", result.label)
    }

    @Test
    fun testManifestGeneration() {
        val manifestStream = javaClass.classLoader.getResourceAsStream("META-INF/manifest.json")
        kotlin.test.assertNotNull(manifestStream, "Manifest file should exist")
        val manifestContent = manifestStream.bufferedReader().readText()
        val manifestJson = Json.parseToJsonElement(manifestContent)
        
        // Find multiply capability and check if 'values' is required
        val capabilities = manifestJson.jsonObject["capabilities"]?.jsonArray
        kotlin.test.assertNotNull(capabilities)
        
        val multiplyCap = capabilities.find { 
            it.jsonObject["name"]?.jsonPrimitive?.content == "multiply" 
        }?.jsonObject
        
        kotlin.test.assertNotNull(multiplyCap, "multiply capability not found in manifest")
        
        val valuesParam = multiplyCap["parameters"]?.jsonObject?.get("values")?.jsonObject
        kotlin.test.assertNotNull(valuesParam, "values parameter not found in multiply capability")
        
        val isRequired = valuesParam["required"]?.jsonPrimitive?.content?.toBoolean() ?: false
        println("valuesParam: $valuesParam")
        println("isRequired: $isRequired")
        kotlin.test.assertTrue(isRequired, "values parameter should be required")
    }
}
