package org.wip.plugintoolkit.features.plugin.logic

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import org.wip.plugintoolkit.api.ExecutionFileSystem
import org.wip.plugintoolkit.api.RelativePath

class DefaultExecutionFileSystem(
    private val sandboxPath: String
) : ExecutionFileSystem {

    init {
        SystemFileSystem.createDirectories(Path(sandboxPath))
    }

    private fun resolvePath(relativePath: RelativePath): Path {
        val resolved = Path(sandboxPath, relativePath.value)
        val file = java.io.File(resolved.toString())
        val normalized = try { file.canonicalPath } catch (e: Exception) { file.absolutePath }
        val baseFile = java.io.File(sandboxPath)
        val baseCanonical = try { baseFile.canonicalPath } catch (e: Exception) { baseFile.absolutePath }
        
        if (normalized != baseCanonical && !normalized.startsWith(baseCanonical + java.io.File.separator)) {
            throw SecurityException("Access to path '${relativePath.value}' is denied. It is outside the sandbox.")
        }
        return resolved
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

    override fun getBasePath(): String = sandboxPath
}
