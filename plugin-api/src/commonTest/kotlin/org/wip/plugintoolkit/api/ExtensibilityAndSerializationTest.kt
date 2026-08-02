package org.wip.plugintoolkit.api

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtensibilityAndSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun testDataTypeUnknownFallback() {
        val unknownJson = """{"type": "future_quantum_type", "foo": "bar"}"""
        val dataType = json.decodeFromString<DataType>(unknownJson)
        assertTrue(dataType is DataType.Unknown)
        assertEquals("future_quantum_type", dataType.rawType)
        assertFalse(dataType.isProvided(JsonPrimitive("test")))
    }

    @Test
    fun testEnumSafeFallback() {
        assertEquals(OS.UNKNOWN, json.decodeFromString<OS>("\"ANDROID\""))
        assertEquals(PrimitiveType.UNKNOWN, json.decodeFromString<PrimitiveType>("\"COMPLEX_NUMBER\""))
        assertEquals(ParameterRole.UNKNOWN, json.decodeFromString<ParameterRole>("\"FUTURE_ROLE\""))
        assertEquals(CapabilityContext.UNKNOWN, json.decodeFromString<CapabilityContext>("\"CLOUD_ONLY\""))

        assertEquals(OS.WINDOWS, json.decodeFromString<OS>("\"WINDOWS\""))
        assertEquals(PrimitiveType.STRING, json.decodeFromString<PrimitiveType>("\"STRING\""))
    }

    @Test
    fun testPluginRequestAndResponseSerializationAndBuilder() {
        val request = PluginRequest.Builder("compute")
            .setParameters(mapOf("input" to JsonPrimitive("data")))
            .setResumeState(JsonPrimitive(123))
            .build()

        assertEquals("compute", request.method)
        assertEquals(JsonPrimitive("data"), request.parameters["input"])
        assertEquals(JsonPrimitive(123), request.resumeState)

        val encodedRequest = json.encodeToString(request)
        val decodedRequest = json.decodeFromString<PluginRequest>(encodedRequest)
        assertEquals(request, decodedRequest)

        val response = PluginResponse.Builder(JsonPrimitive("success"))
            .setMetadata(mapOf("duration" to "10ms"))
            .build()

        assertEquals(JsonPrimitive("success"), response.result)
        assertEquals("10ms", response.metadata?.get("duration"))

        val encodedResponse = json.encodeToString(response)
        val decodedResponse = json.decodeFromString<PluginResponse>(encodedResponse)
        assertEquals(response, decodedResponse)
    }

    @Test
    fun testScopedFileSystemDefaultOperations() = runBlocking {
        val storage = mutableMapOf<String, ByteArray>()
        val fs = object : ScopedFileSystem {
            override suspend fun readFile(relativePath: RelativePath): ByteArray? = storage[relativePath.value]
            override suspend fun readTextFile(relativePath: RelativePath): String? = storage[relativePath.value]?.decodeToString()
            override suspend fun writeFile(relativePath: RelativePath, data: ByteArray): Result<Unit> {
                storage[relativePath.value] = data
                return Result.success(Unit)
            }
            override suspend fun writeTextFile(relativePath: RelativePath, text: String): Result<Unit> = writeFile(relativePath, text.encodeToByteArray())
            override suspend fun exists(relativePath: RelativePath): Boolean = storage.containsKey(relativePath.value)
            override suspend fun listFiles(relativePath: RelativePath): List<String> = storage.keys.toList()
            override suspend fun deleteFile(relativePath: RelativePath): Result<Unit> {
                storage.remove(relativePath.value)
                return Result.success(Unit)
            }
            override fun getBasePath(): String = "/tmp"
        }

        val src = RelativePath.from("src.txt").getOrThrow()
        val dst = RelativePath.from("dst.txt").getOrThrow()

        fs.writeFile(src, "Hello Stream".encodeToByteArray())
        assertTrue(fs.exists(src))

        // Test stream read
        val chunks = fs.readStream(src).toList()
        assertEquals(1, chunks.size)
        assertEquals("Hello Stream", chunks.first().decodeToString())

        // Test copy
        val copyResult = fs.copyFile(src, dst)
        assertTrue(copyResult.isSuccess)
        assertTrue(fs.exists(dst))
        assertEquals("Hello Stream", fs.readTextFile(dst))

        // Test move
        val moved = RelativePath.from("moved.txt").getOrThrow()
        val moveResult = fs.moveFile(dst, moved)
        assertTrue(moveResult.isSuccess)
        assertFalse(fs.exists(dst))
        assertTrue(fs.exists(moved))
        assertEquals("Hello Stream", fs.readTextFile(moved))

        // Test writeStream
        val streamFile = RelativePath.from("stream.txt").getOrThrow()
        val writeStreamResult = fs.writeStream(streamFile, flowOf("Chunk1".encodeToByteArray(), "Chunk2".encodeToByteArray()))
        assertTrue(writeStreamResult.isSuccess)
        assertEquals("Chunk1Chunk2", fs.readTextFile(streamFile))
    }
}
