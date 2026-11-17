package org.wip.plugintoolkit.features.plugin.logic

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.update
import kotlinx.collections.immutable.persistentListOf
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import org.wip.plugintoolkit.api.HostFileSystem
import java.io.File

/**
 * Internal host filesystem extension enabling dynamic session-scoped grants when a user explicitly
 * selects files or folders via native host dialogs.
 */
interface DynamicHostFileSystem : HostFileSystem {
    fun grantAccess(absolutePath: String)
}

class HostFileSystemImpl(
    allowedPaths: List<String>,
    private val isDestructiveAllowed: Boolean
) : DynamicHostFileSystem {

    private val allowedPathsState = atomic(
        persistentListOf<String>().builder().apply { addAll(allowedPaths) }.build()
    )

    override fun grantAccess(absolutePath: String) {
        val normalized = normalizePath(absolutePath) ?: return
        allowedPathsState.update { current ->
            if (current.any { normalizePath(it) == normalized }) current else current.add(normalized)
        }
    }

    /**
     * Resolves [pathString] to a canonical path with forward slashes so equivalent representations
     * (such as Windows 8.3 short paths, mixed separators, symlinks, or `.`/`..` segments) match
     * consistently against [allowedPathsState] without allowing traversal escapes.
     */
    private fun normalizePath(pathString: String): String? {
        if (pathString.isBlank()) return null
        return try {
            File(pathString).canonicalPath.replace('\\', '/')
        } catch (_: Exception) {
            null
        }
    }

    private fun isPathAllowed(pathString: String): Boolean {
        val currentAllowed = allowedPathsState.value
        // If allowedPaths is empty, it means the capability doesn't have any allowed paths configured
        if (currentAllowed.isEmpty()) return false

        val normalizedPath = normalizePath(pathString) ?: return false

        // If the path is inside any of the allowedPaths, it is allowed
        return currentAllowed.any { allowed ->
            val allowedCanonical = normalizePath(allowed) ?: return@any false

            normalizedPath == allowedCanonical || normalizedPath.startsWith(if (allowedCanonical.endsWith("/")) allowedCanonical else "$allowedCanonical/")
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

        return SystemFileSystem.list(path)
            .filter { isPathAllowed(it.toString()) }
            .map { it.name }
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
