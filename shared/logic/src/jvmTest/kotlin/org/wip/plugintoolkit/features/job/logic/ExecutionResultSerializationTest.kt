package org.wip.plugintoolkit.features.job.logic

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.ErrorDetail
import org.wip.plugintoolkit.api.ExecutionResult
import org.wip.plugintoolkit.api.JobHandle
import org.wip.plugintoolkit.api.PluginResponse
import org.wip.plugintoolkit.api.asDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExecutionResultSerializationTest {

    private val json = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    @Test
    fun testSerializeSuccessResult() {
        val original: ExecutionResult = ExecutionResult.Success(
            PluginResponse(result = JsonPrimitive("hello world"))
        )
        val encoded = json.encodeToString(ExecutionResult.serializer(), original)
        assertTrue(encoded.contains("\"type\":\"Success\""))

        val decoded = json.decodeFromString(ExecutionResult.serializer(), encoded)
        assertTrue(decoded is ExecutionResult.Success)
        assertEquals("hello world", decoded.response.result.toString().replace("\"", ""))
    }

    @Test
    fun testSerializeErrorResultWithErrorDetail() {
        val original: ExecutionResult = ExecutionResult.Error(
            error = ErrorDetail(
                message = "Validation failed for input",
                exceptionType = "IllegalArgumentException",
                details = mapOf("field" to "username", "constraint" to "not_empty")
            )
        )
        val encoded = json.encodeToString(ExecutionResult.serializer(), original)
        assertTrue(encoded.contains("\"type\":\"Error\""))
        assertTrue(encoded.contains("Validation failed for input"))
        assertTrue(encoded.contains("IllegalArgumentException"))
        assertTrue(encoded.contains("\"field\":\"username\""))

        val decoded = json.decodeFromString(ExecutionResult.serializer(), encoded)
        assertTrue(decoded is ExecutionResult.Error)
        assertEquals("Validation failed for input", decoded.error.message)
        assertEquals("IllegalArgumentException", decoded.error.exceptionType)
        assertEquals("username", decoded.error.details["field"])
        assertEquals("Validation failed for input", decoded.throwable.message)
    }

    @Test
    fun testErrorResultSecondaryConstructorBackwardCompatibility() {
        val ex = IllegalStateException("Something went wrong in capability")
        val errorResult = ExecutionResult.Error(ex)

        assertEquals("Something went wrong in capability", errorResult.error.message)
        assertEquals("IllegalStateException", errorResult.error.exceptionType)
        assertEquals(ex.message, errorResult.throwable.message)

        val encoded = json.encodeToString(ExecutionResult.serializer(), errorResult)
        val decoded = json.decodeFromString(ExecutionResult.serializer(), encoded)
        assertTrue(decoded is ExecutionResult.Error)
        assertEquals("Something went wrong in capability", decoded.error.message)
    }

    @Test
    fun testSerializePausedResult() {
        val original: ExecutionResult = ExecutionResult.Paused(
            resumeState = JsonPrimitive(42)
        )
        val encoded = json.encodeToString(ExecutionResult.serializer(), original)
        assertTrue(encoded.contains("\"type\":\"Paused\""))

        val decoded = json.decodeFromString(ExecutionResult.serializer(), encoded)
        assertTrue(decoded is ExecutionResult.Paused)
        assertNotNull(decoded.resumeState)
        assertEquals("42", decoded.resumeState.toString())
    }

    @Test
    fun testJobHandleAwaitResultAndAsDeferred() = runTest {
        val deferredResult = CompletableDeferred<ExecutionResult>()
        val handle = object : JobHandle {
            override suspend fun awaitResult(): ExecutionResult = deferredResult.await()
            override fun cancel(force: Boolean) {
                deferredResult.cancel()
            }
            override fun pause() {}
        }

        val deferred = handle.asDeferred(this)
        val expected = ExecutionResult.Success(PluginResponse(result = JsonPrimitive("ok")))
        deferredResult.complete(expected)

        val actual = handle.awaitResult()
        assertEquals(expected, actual)
        assertEquals(expected, deferred.await())
    }
}
