package org.wip.plugintoolkit.features.plugin.logic

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.wip.plugintoolkit.api.Capability
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.ParameterMetadata
import org.wip.plugintoolkit.api.PluginAction
import org.wip.plugintoolkit.core.utils.FileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginStorageAndLocksTest {

    private class MemoryFileSystem : FileSystem {
        private val storage = mutableMapOf<String, String>()

        override fun readFile(path: String): String? {
            return storage[path]
        }

        override fun writeFile(path: String, content: String) {
            storage[path] = content
        }

        override fun saveFile(path: String, content: ByteArray) {
            storage[path] = content.decodeToString()
        }

        override fun exists(path: String): Boolean = storage.containsKey(path)

        override fun mkdirs(path: String): Boolean = true

        override fun copyFile(source: String, destination: String) {
            storage[source]?.let { storage[destination] = it }
        }

        override fun deleteDirectory(path: String): Boolean {
            return storage.keys.removeAll { it.startsWith(path) }
        }

        override fun listFiles(path: String): List<String> = storage.keys.filter { it.startsWith(path) }

        override fun readFileFromZip(zipPath: String, fileName: String): String? = null
    }

    @Test
    fun testDefaultPluginStorageOperations() = runTest {
        val fs = MemoryFileSystem()
        val storage = DefaultPluginStorage("/test/plugin", fs)

        assertNull(storage.get("model_downloaded"))

        storage.put("model_downloaded", JsonPrimitive(true))
        assertEquals(JsonPrimitive(true), storage.get("model_downloaded"))

        storage.put("onnx_path", JsonPrimitive("/data/models/model.onnx"))
        assertEquals(JsonPrimitive("/data/models/model.onnx"), storage.get("onnx_path"))

        val allData = storage.getAll()
        assertEquals(2, allData.size)
        assertEquals(JsonPrimitive(true), allData["model_downloaded"])

        storage.remove("model_downloaded")
        assertNull(storage.get("model_downloaded"))
        assertEquals(1, storage.getAll().size)
    }

    @Test
    fun testEnumLockRequirementsSerialization() {
        val json = Json { ignoreUnknownKeys = true }
        val enumType = DataType.Enum(
            className = "ModelType",
            options = listOf("Standard", "ONNX"),
            optionLockRequirements = mapOf("ONNX" to listOf("onnx_downloaded"))
        )

        val encoded = json.encodeToString(DataType.serializer(), enumType)
        assertTrue(encoded.contains("onnx_downloaded"))

        val decoded = json.decodeFromString(DataType.serializer(), encoded) as DataType.Enum
        assertEquals(listOf("onnx_downloaded"), decoded.optionLockRequirements["ONNX"])
    }

    @Test
    fun testCapabilityRequiredLocksSerialization() {
        val json = Json { ignoreUnknownKeys = true }
        val capability = Capability(
            name = "InferONNX",
            description = "Run ONNX Inference",
            returnType = DataType.Primitive(org.wip.plugintoolkit.api.PrimitiveType.STRING),
            requiredLocks = listOf("onnx_downloaded")
        )

        val encoded = json.encodeToString(Capability.serializer(), capability)
        assertTrue(encoded.contains("requiredLocks"))

        val decoded = json.decodeFromString(Capability.serializer(), encoded)
        assertEquals(listOf("onnx_downloaded"), decoded.requiredLocks)
    }

    @Test
    fun testPluginActionWithParametersSerialization() {
        val json = Json { ignoreUnknownKeys = true }
        val action = PluginAction(
            name = "Download Model",
            description = "Downloads specified ONNX model",
            functionName = "downloadModel",
            parameters = mapOf(
                "modelUrl" to ParameterMetadata(
                    description = "URL of model",
                    type = DataType.Primitive(org.wip.plugintoolkit.api.PrimitiveType.STRING),
                    required = true
                )
            )
        )

        val encoded = json.encodeToString(PluginAction.serializer(), action)
        assertTrue(encoded.contains("modelUrl"))

        val decoded = json.decodeFromString(PluginAction.serializer(), encoded)
        assertNotNull(decoded.parameters)
        assertEquals(true, decoded.parameters!!["modelUrl"]?.required)
    }
}
