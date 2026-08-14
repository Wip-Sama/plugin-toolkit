package org.wip.plugintoolkit.features.plugin.logic

import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import org.wip.plugintoolkit.api.PluginFileSystem
import org.wip.plugintoolkit.api.RelativePath
import org.wip.plugintoolkit.core.loomDispatcher

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

    private val filesOperations = SandboxFileOperations(basePath)
    private val cacheOperations = SandboxFileOperations(cachePath)

    private fun resolvePath(relativePath: RelativePath): Path {
        return filesOperations.resolve(relativePath)
    }

    override suspend fun readFile(relativePath: RelativePath): ByteArray? {
        val path = resolvePath(relativePath)
        if (!SystemFileSystem.exists(path)) return null
        return SystemFileSystem.source(path).buffered().use { it.readByteArray() }
    }

    override suspend fun readTextFile(relativePath: RelativePath): String? {
        val path = resolvePath(relativePath)
        if (!SystemFileSystem.exists(path)) return null
        return SystemFileSystem.source(path).buffered().use { it.readString() }
    }

    override suspend fun writeFile(relativePath: RelativePath, data: ByteArray): Result<Unit> {
        return try {
            val path = resolvePath(relativePath)
            path.parent?.let { SystemFileSystem.createDirectories(it) }
            SystemFileSystem.sink(path).buffered().use { it.write(data) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun writeTextFile(relativePath: RelativePath, text: String): Result<Unit> {
        return try {
            val path = resolvePath(relativePath)
            path.parent?.let { SystemFileSystem.createDirectories(it) }
            SystemFileSystem.sink(path).buffered().use { it.writeString(text) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exists(relativePath: RelativePath): Boolean {
        return SystemFileSystem.exists(resolvePath(relativePath))
    }

    override suspend fun listFiles(relativePath: RelativePath): List<String> {
        val path = resolvePath(relativePath)
        if (!SystemFileSystem.exists(path)) return emptyList()
        val metadata = SystemFileSystem.metadataOrNull(path)
        if (metadata?.isDirectory != true) return emptyList()

        return SystemFileSystem.list(path).map { it.name }
    }

    override suspend fun deleteFile(relativePath: RelativePath): Result<Unit> {
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

    override suspend fun createDirectory(relativePath: RelativePath): Result<Unit> = runCatching {
        SystemFileSystem.createDirectories(resolvePath(relativePath))
    }

    override suspend fun deleteDirectory(relativePath: RelativePath, recursive: Boolean): Result<Unit> = runCatching {
        require(relativePath.value.isNotEmpty()) { "The plugin files root cannot be deleted" }
        filesOperations.deleteDirectory(resolvePath(relativePath), recursive)
    }

    override suspend fun extractResource(resourcePath: String, targetRelativePath: RelativePath): Result<Unit> {
        if (resourcePath.contains("..") || resourcePath.startsWith("/") || resourcePath.startsWith("\\") || resourcePath.contains("\u0000")) {
            return Result.failure(SecurityException("Invalid resource path: $resourcePath"))
        }

        return try {
            withContext(loomDispatcher) {
                val jar = jarPath
                    ?: return@withContext Result.failure(Exception("No JAR path configured for resource extraction"))

                val data = org.wip.plugintoolkit.core.utils.PlatformUtils.readBytesFromZip(jar, resourcePath)
                    ?: return@withContext Result.failure(Exception("Resource not found in JAR: $resourcePath"))

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
                    override suspend fun readFile(relativePath: RelativePath): ByteArray? =
                        fs.readFromCache(relativePath)

                    override suspend fun readTextFile(relativePath: RelativePath): String? =
                        fs.readTextFromCache(relativePath)

                    override suspend fun writeFile(relativePath: RelativePath, data: ByteArray): Result<Unit> =
                        fs.writeToCache(relativePath, data)

                    override suspend fun writeTextFile(relativePath: RelativePath, text: String): Result<Unit> =
                        fs.writeTextToCache(relativePath, text)

                    override suspend fun exists(relativePath: RelativePath): Boolean =
                        SystemFileSystem.exists(fs.resolveCachePath(relativePath))

                    override suspend fun deleteFile(relativePath: RelativePath): Result<Unit> =
                        fs.deleteFromCache(relativePath)

                    override suspend fun createDirectory(relativePath: RelativePath): Result<Unit> = runCatching {
                        SystemFileSystem.createDirectories(fs.resolveCachePath(relativePath))
                    }

                    override suspend fun deleteDirectory(relativePath: RelativePath, recursive: Boolean): Result<Unit> =
                        runCatching {
                            require(relativePath.value.isNotEmpty()) { "The plugin cache root cannot be deleted" }
                            fs.cacheOperations.deleteDirectory(fs.resolveCachePath(relativePath), recursive)
                        }
                }
            }
        }
    }

    private fun resolveCachePath(relativePath: RelativePath): Path {
        return cacheOperations.resolve(relativePath)
    }

    private suspend fun readFromCache(relativePath: RelativePath): ByteArray? {
        val path = resolveCachePath(relativePath)
        if (!SystemFileSystem.exists(path)) return null
        return SystemFileSystem.source(path).buffered().use { it.readByteArray() }
    }

    private suspend fun readTextFromCache(relativePath: RelativePath): String? {
        val path = resolveCachePath(relativePath)
        if (!SystemFileSystem.exists(path)) return null
        return SystemFileSystem.source(path).buffered().use { it.readString() }
    }

    private suspend fun writeToCache(relativePath: RelativePath, data: ByteArray): Result<Unit> {
        return try {
            val path = resolveCachePath(relativePath)
            path.parent?.let { SystemFileSystem.createDirectories(it) }
            SystemFileSystem.sink(path).buffered().use { it.write(data) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun writeTextToCache(relativePath: RelativePath, text: String): Result<Unit> {
        return try {
            val path = resolveCachePath(relativePath)
            path.parent?.let { SystemFileSystem.createDirectories(it) }
            SystemFileSystem.sink(path).buffered().use { it.writeString(text) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun deleteFromCache(relativePath: RelativePath): Result<Unit> {
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
