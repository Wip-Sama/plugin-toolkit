package org.wip.plugintoolkit.features.plugin.logic

import kotlinx.io.files.Path
import org.wip.plugintoolkit.api.RelativePath
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path as NioPath
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

internal class SandboxFileOperations(root: String) {
    private val base = Paths.get(root).toAbsolutePath().normalize()
    private val realBase = base.toRealPath()

    fun resolve(relativePath: RelativePath): Path {
        if (base.toRealPath() != realBase) {
            throw SecurityException("The sandbox root changed after it was initialized")
        }
        val candidate = base.resolve(relativePath.value).normalize()
        if (!candidate.startsWith(base)) {
            throw SecurityException("Access to path '${relativePath.value}' is outside the sandbox")
        }

        var current = base
        base.relativize(candidate).forEach { segment ->
            current = current.resolve(segment)
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                val realCurrent = current.toRealPath()
                if (!realCurrent.startsWith(realBase)) {
                    throw SecurityException("Access to path '${relativePath.value}' escapes the sandbox through a symbolic link")
                }
            }
        }
        return Path(candidate.toString())
    }

    fun resolveIfRootExists(relativePath: RelativePath): Path? = try {
        resolve(relativePath)
    } catch (_: NoSuchFileException) {
        null
    }

    fun deleteDirectory(path: Path, recursive: Boolean) {
        val nioPath = Paths.get(path.toString())
        if (!Files.exists(nioPath, LinkOption.NOFOLLOW_LINKS)) return
        require(Files.isDirectory(nioPath, LinkOption.NOFOLLOW_LINKS)) { "Path is not a directory: $path" }

        if (!recursive) {
            Files.delete(nioPath)
            return
        }

        Files.walkFileTree(nioPath, object : SimpleFileVisitor<NioPath>() {
            override fun visitFile(file: NioPath, attrs: BasicFileAttributes): FileVisitResult {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: NioPath, error: java.io.IOException?): FileVisitResult {
                if (error != null) throw error
                Files.delete(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
}
