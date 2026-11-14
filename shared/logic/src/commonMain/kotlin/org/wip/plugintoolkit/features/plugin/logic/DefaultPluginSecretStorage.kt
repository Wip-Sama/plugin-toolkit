package org.wip.plugintoolkit.features.plugin.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.wip.plugintoolkit.api.PluginSecretStorage
import org.wip.plugintoolkit.core.utils.FileSystem
import org.wip.plugintoolkit.core.utils.SecureStorage

/**
 * Concrete implementation of [PluginSecretStorage] persisting encrypted plugin secrets
 * to `secrets.json` inside the plugin's install directory via [SecureStorage].
 *
 * When [installPath] is blank (e.g., ephemeral/test plugins), falls back to an isolated in-memory map.
 */
class DefaultPluginSecretStorage(
    private val installPath: String,
    private val fileSystem: FileSystem
) : PluginSecretStorage {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val mutex = Mutex()
    private val ephemeralSecrets = mutableMapOf<String, String>()
    private val secretsFile: String
        get() = "$installPath/secrets.json"

    private suspend fun readSecretsMap(): MutableMap<String, String> {
        if (installPath.isBlank()) return ephemeralSecrets
        val content = fileSystem.readFile(secretsFile) ?: return mutableMapOf()
        return try {
            val obj = json.decodeFromString<JsonObject>(content)
            val result = mutableMapOf<String, String>()
            for ((key, element) in obj) {
                val encrypted = (element as? JsonPrimitive)?.contentOrNull ?: continue
                result[key] = encrypted
            }
            result
        } catch (e: Exception) {
            Logger.e(e) { "Failed to read secrets.json at $secretsFile" }
            mutableMapOf()
        }
    }

    private suspend fun writeSecretsMap(data: Map<String, String>) {
        if (installPath.isBlank()) return
        try {
            val jsonObj = JsonObject(data.mapValues { (_, encrypted) -> JsonPrimitive(encrypted) })
            val serialized = json.encodeToString(JsonObject.serializer(), jsonObj)
            fileSystem.writeFile(secretsFile, serialized)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to write secrets.json at $secretsFile" }
        }
    }

    override suspend fun getSecret(key: String): String? = mutex.withLock {
        val encrypted = readSecretsMap()[key] ?: return@withLock null
        return@withLock runCatching { SecureStorage.decrypt(encrypted) }
            .onFailure { Logger.w(it) { "Failed to decrypt secret for key '$key'" } }
            .getOrNull()
    }

    override suspend fun putSecret(key: String, value: String): Unit = mutex.withLock {
        val map = readSecretsMap()
        map[key] = SecureStorage.encrypt(value)
        if (installPath.isNotBlank()) {
            writeSecretsMap(map)
        }
    }

    override suspend fun removeSecret(key: String): Unit = mutex.withLock {
        val map = readSecretsMap()
        if (map.remove(key) != null && installPath.isNotBlank()) {
            writeSecretsMap(map)
        }
    }

    override suspend fun listSecretKeys(): List<String> = mutex.withLock {
        readSecretsMap().keys.toList()
    }
}
