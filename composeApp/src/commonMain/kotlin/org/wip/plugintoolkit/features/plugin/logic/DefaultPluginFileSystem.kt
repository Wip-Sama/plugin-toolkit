package org.wip.plugintoolkit.features.plugin.logic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import org.wip.plugintoolkit.api.PluginFileSystem
import java.io.File
import java.util.jar.JarFile

class DefaultPluginFileSystem(
    pluginInstallPath: String,
    private val jarPath: String? = null
) : PluginFileSystem {
    private val basePath: String = Path(pluginInstallPath, "files").toString()
    private val cachePath: String = Path(pluginInstallPath, "cache").toString()

    init {
        SystemFileSystem.createDirectories(Path(basePath))
        SystemFileSystem.createDirectories(Path(cachePath))
    }

    //TODO: need to check better if this should be implemented in platform specific FileSystem like it is for PlatformUtils
    private fun resolvePath(relativePath: String): Path {
        // Prevent path traversal
        val sanitized = relativePath.replace("..", "").replace("\\", "/")
        return Path(basePath, sanitized)
    }

    override suspend fun readFile(relativePath: String): ByteArray? {
        val path = resolvePath(relativePath)
        if (!SystemFileSystem.exists(path)) return null
        return SystemFileSystem.source(path).buffered().use { it.readByteArray() }
    }

    override suspend fun readTextFile(relativePath: String): String? {
        val path = resolvePath(relativePath)
        if (!SystemFileSystem.exists(path)) return null
        return SystemFileSystem.source(path).buffered().use { it.readString() }
    }

    override suspend fun writeFile(relativePath: String, data: ByteArray): Result<Unit> {
        return try {
            val path = resolvePath(relativePath)
            path.parent?.let { SystemFileSystem.createDirectories(it) }
            SystemFileSystem.sink(path).buffered().use { it.write(data) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun writeTextFile(relativePath: String, text: String): Result<Unit> {
        return try {
            val path = resolvePath(relativePath)
            path.parent?.let { SystemFileSystem.createDirectories(it) }
            SystemFileSystem.sink(path).buffered().use { it.writeString(text) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exists(relativePath: String): Boolean {
        return SystemFileSystem.exists(resolvePath(relativePath))
    }

    override suspend fun listFiles(relativePath: String): List<String> {
        val path = resolvePath(relativePath)
        if (!SystemFileSystem.exists(path)) return emptyList()
        val metadata = SystemFileSystem.metadataOrNull(path)
        if (metadata?.isDirectory != true) return emptyList()

        return SystemFileSystem.list(path).map { it.name }
    }

    override suspend fun deleteFile(relativePath: String): Result<Unit> {
        return try {
            val path = resolvePath(relativePath)
            if (SystemFileSystem.exists(path)) {
                SystemFileSystem.delete(path)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun extractResource(resourcePath: String, targetRelativePath: String): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                val jar = jarPath
                    ?: return@withContext Result.failure(Exception("No JAR path configured for resource extraction"))
                val jarFile = JarFile(File(jar))

                val entry = jarFile.getJarEntry(resourcePath)
                    ?: return@withContext Result.failure(Exception("Resource not found in JAR: $resourcePath"))

                val data = jarFile.getInputStream(entry).use { it.readBytes() }
                jarFile.close()

                writeFile(targetRelativePath, data)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getBasePath(): String = basePath

    companion object {
        fun createCacheOnly(pluginInstallPath: String): PluginFileSystem {
            return DefaultPluginFileSystem(pluginInstallPath).let { fs ->
                // Create a variant that uses cachePath as basePath
                object : PluginFileSystem by fs {
                    override fun getBasePath(): String = fs.cachePath
                    override suspend fun readFile(relativePath: String): ByteArray? = fs.readFromCache(relativePath)
                    override suspend fun readTextFile(relativePath: String): String? =
                        fs.readTextFromCache(relativePath)

                    override suspend fun writeFile(relativePath: String, data: ByteArray): Result<Unit> =
                        fs.writeToCache(relativePath, data)

                    override suspend fun writeTextFile(relativePath: String, text: String): Result<Unit> =
                        fs.writeTextToCache(relativePath, text)

                    override suspend fun exists(relativePath: String): Boolean =
                        SystemFileSystem.exists(fs.resolveCachePath(relativePath))

                    override suspend fun deleteFile(relativePath: String): Result<Unit> =
                        fs.deleteFromCache(relativePath)
                }
            }
        }
    }

    private fun resolveCachePath(relativePath: String): Path {
        val sanitized = relativePath.replace("..", "").replace("\\", "/")
        return Path(cachePath, sanitized)
    }

    private suspend fun readFromCache(relativePath: String): ByteArray? {
        val path = resolveCachePath(relativePath)
        if (!SystemFileSystem.exists(path)) return null
        return SystemFileSystem.source(path).buffered().use { it.readByteArray() }
    }

    private suspend fun readTextFromCache(relativePath: String): String? {
        val path = resolveCachePath(relativePath)
        if (!SystemFileSystem.exists(path)) return null
        return SystemFileSystem.source(path).buffered().use { it.readString() }
    }

    private suspend fun writeToCache(relativePath: String, data: ByteArray): Result<Unit> {
        return try {
            val path = resolveCachePath(relativePath)
            path.parent?.let { SystemFileSystem.createDirectories(it) }
            SystemFileSystem.sink(path).buffered().use { it.write(data) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writeTextToCache(relativePath: String, text: String): Result<Unit> {
        return try {
            val path = resolveCachePath(relativePath)
            path.parent?.let { SystemFileSystem.createDirectories(it) }
            SystemFileSystem.sink(path).buffered().use { it.writeString(text) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun deleteFromCache(relativePath: String): Result<Unit> {
        return try {
            val path = resolveCachePath(relativePath)
            if (SystemFileSystem.exists(path)) {
                SystemFileSystem.delete(path)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
