package org.wip.plugintoolkit.features.plugin.logic

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import org.wip.plugintoolkit.api.HostFileSystem
import java.io.File

class HostFileSystemImpl(
    private val allowedPaths: List<String>,
    private val isDestructiveAllowed: Boolean
) : HostFileSystem {

    private fun isPathAllowed(pathString: String): Boolean {
        // If allowedPaths is empty, it means the capability doesn't have any allowed paths configured
        // (perhaps it wasn't supposed to read/write files based on its parameters)
        if (allowedPaths.isEmpty()) return false

        val file = File(pathString)
        val normalizedPath = try { file.canonicalPath } catch (e: Exception) { file.absolutePath }

        // If the path is inside any of the allowedPaths, it is allowed
        return allowedPaths.any { allowed ->
            val allowedFile = File(allowed)
            val allowedCanonical = try { allowedFile.canonicalPath } catch (e: Exception) { allowedFile.absolutePath }
            
            normalizedPath == allowedCanonical || normalizedPath.startsWith(allowedCanonical + File.separator)
        }
    }

    private fun validateAccess(absolutePath: String, requireDestructive: Boolean = false) {
        if (!isPathAllowed(absolutePath)) {
            throw SecurityException("Access to path '$absolutePath' is denied. It is outside the allowed paths.")
        }
        if (requireDestructive && !isDestructiveAllowed) {
            throw SecurityException("Destructive operations on path '$absolutePath' are denied. Capability is not marked as destructive.")
        }
    }

    override suspend fun readFile(absolutePath: String): ByteArray? {
        validateAccess(absolutePath)
        val path = Path(absolutePath)
        if (!SystemFileSystem.exists(path)) return null
        return SystemFileSystem.source(path).buffered().use { it.readByteArray() }
    }

    override suspend fun readTextFile(absolutePath: String): String? {
        validateAccess(absolutePath)
        val path = Path(absolutePath)
        if (!SystemFileSystem.exists(path)) return null
        return SystemFileSystem.source(path).buffered().use { it.readString() }
    }

    override suspend fun writeFile(absolutePath: String, data: ByteArray): Result<Unit> {
        return try {
            val path = Path(absolutePath)
            val exists = SystemFileSystem.exists(path)
            validateAccess(absolutePath, requireDestructive = exists)

            path.parent?.let { SystemFileSystem.createDirectories(it) }
            SystemFileSystem.sink(path).buffered().use { it.write(data) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun writeTextFile(absolutePath: String, text: String): Result<Unit> {
        return try {
            val path = Path(absolutePath)
            val exists = SystemFileSystem.exists(path)
            validateAccess(absolutePath, requireDestructive = exists)

            path.parent?.let { SystemFileSystem.createDirectories(it) }
            SystemFileSystem.sink(path).buffered().use { it.writeString(text) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun exists(absolutePath: String): Boolean {
        validateAccess(absolutePath)
        return SystemFileSystem.exists(Path(absolutePath))
    }

    override suspend fun listFiles(absolutePath: String): List<String> {
        validateAccess(absolutePath)
        val path = Path(absolutePath)
        if (!SystemFileSystem.exists(path)) return emptyList()
        val metadata = SystemFileSystem.metadataOrNull(path)
        if (metadata?.isDirectory != true) return emptyList()

        return SystemFileSystem.list(path).map { it.name }
    }

    override suspend fun deleteFile(absolutePath: String): Result<Unit> {
        return try {
            validateAccess(absolutePath, requireDestructive = true)
            val path = Path(absolutePath)
            if (SystemFileSystem.exists(path)) {
                SystemFileSystem.delete(path)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createDirectory(absolutePath: String): Result<Unit> {
        return try {
            validateAccess(absolutePath)
            val path = Path(absolutePath)
            SystemFileSystem.createDirectories(path)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
