package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.wip.plugintoolkit.api.PluginStorage
import org.wip.plugintoolkit.core.utils.FileSystem

/**
 * Concrete implementation of [PluginStorage] that persists key-value Json data
 * to a `storage.json` file inside the plugin's install directory.
 */
class DefaultPluginStorage(
    private val installPath: String,
    private val fileSystem: FileSystem
) : PluginStorage {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mutex = Mutex()
    private val storageFile: String
        get() = "$installPath/storage.json"

    private suspend fun readStorageFile(): MutableMap<String, JsonElement> {
        if (installPath.isBlank()) return mutableMapOf()
        val content = fileSystem.readFile(storageFile) ?: return mutableMapOf()
        return try {
            json.decodeFromString<JsonObject>(content).toMutableMap()
        } catch (e: Exception) {
            Logger.e(e) { "Failed to read storage.json at $storageFile" }
            mutableMapOf()
        }
    }

    private suspend fun writeStorageFile(data: Map<String, JsonElement>) {
        if (installPath.isBlank()) return
        try {
            val jsonObject = JsonObject(data)
            val content = json.encodeToString(JsonObject.serializer(), jsonObject)
            fileSystem.writeFile(storageFile, content)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to write storage.json at $storageFile" }
        }
    }

    override suspend fun get(key: String): JsonElement? = mutex.withLock {
        readStorageFile()[key]
    }

    override suspend fun put(key: String, value: JsonElement): Unit = mutex.withLock {
        val current = readStorageFile()
        current[key] = value
        writeStorageFile(current)
    }

    override suspend fun getAll(): Map<String, JsonElement> = mutex.withLock {
        readStorageFile()
    }

    override suspend fun remove(key: String): Unit = mutex.withLock {
        val current = readStorageFile()
        if (current.remove(key) != null) {
            writeStorageFile(current)
        }
    }
}
