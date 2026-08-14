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
        val normalized = try {
            file.canonicalPath
        } catch (e: Exception) {
            throw SecurityException("Failed to resolve canonical path for '${relativePath.value}': ${e.message}")
        }
        val baseFile = java.io.File(sandboxPath)
        val baseCanonical = try {
            baseFile.canonicalPath
        } catch (e: Exception) {
            throw SecurityException("Failed to resolve base canonical path for '$sandboxPath': ${e.message}")
        }

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

    override suspend fun createDirectory(relativePath: RelativePath): Result<Unit> = runCatching {
        SystemFileSystem.createDirectories(resolvePath(relativePath))
    }

    override suspend fun deleteDirectory(relativePath: RelativePath, recursive: Boolean): Result<Unit> = runCatching {
        require(relativePath != RelativePath.ROOT) { "The execution sandbox root cannot be deleted" }
        deleteDirectory(resolvePath(relativePath), recursive)
    }

    private fun deleteDirectory(path: Path, recursive: Boolean) {
        if (!SystemFileSystem.exists(path)) return
        require(SystemFileSystem.metadataOrNull(path)?.isDirectory == true) { "Path is not a directory: $path" }
        val children = SystemFileSystem.list(path)
        require(recursive || children.isEmpty()) { "Directory is not empty: $path" }
        if (recursive) {
            children.forEach { child ->
                if (SystemFileSystem.metadataOrNull(child)?.isDirectory == true) deleteDirectory(child, true)
                else SystemFileSystem.delete(child)
            }
        }
        SystemFileSystem.delete(path)
    }

    override fun getBasePath(): String = sandboxPath
}
