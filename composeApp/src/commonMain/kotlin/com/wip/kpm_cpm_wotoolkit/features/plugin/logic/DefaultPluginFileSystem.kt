package com.wip.kpm_cpm_wotoolkit.features.plugin.logic

import com.wip.plugin.api.PluginFileSystem
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString

class DefaultPluginFileSystem(
    private val basePath: String
) : PluginFileSystem {
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
}
