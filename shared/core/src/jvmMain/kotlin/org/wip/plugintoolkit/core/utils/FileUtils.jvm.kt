package org.wip.plugintoolkit.core.utils

import co.touchlab.kermit.Logger
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.io.readString
import kotlinx.io.writeString
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

actual object FileUtils {
    actual val isWindows: Boolean = System.getProperty("os.name", "").lowercase().contains("win")
    actual val isLinux: Boolean = System.getProperty("os.name", "").lowercase().contains("nux")
    actual val isMac: Boolean = System.getProperty("os.name", "").lowercase().contains("mac")

    actual fun copyFile(source: String, destination: String) {
        Logger.d { "Copying file from $source to $destination" }
        val src = Path(source)
        val dst = Path(destination)
        SystemFileSystem.source(src).buffered().use { bufferedSource ->
            SystemFileSystem.sink(dst).buffered().use { bufferedSink ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = bufferedSource.readAtMostTo(buffer)
                    if (read == -1) break
                    bufferedSink.write(buffer, 0, read)
                }
            }
        }
    }

    actual fun downloadFile(url: String, destination: String): Result<Unit> {
        return try {
            java.net.URI(url).toURL().openStream().use { input ->
                val dst = Path(destination)
                dst.parent?.let { SystemFileSystem.createDirectories(it) }
                SystemFileSystem.sink(dst).buffered().use { bufferedSink ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        bufferedSink.write(buffer, 0, read)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to download file from $url to $destination" }
            Result.failure(e)
        }
    }

    actual fun unzip(source: String, destination: String, maxDecompressedSize: Long): Result<Unit> {
        return try {
            val totalSize = getUnzippedSize(source)
            if (totalSize > maxDecompressedSize) {
                Logger.e { "ZIP content too large: $totalSize bytes (max $maxDecompressedSize) for $source" }
                return Result.failure(Exception("ZIP content too large: $totalSize bytes (max $maxDecompressedSize)"))
            }

            Logger.d { "Unzipping $source to $destination" }
            ZipFile(source).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    val path = Path(destination, entry.name)
                    if (entry.isDirectory) {
                        SystemFileSystem.createDirectories(path)
                    } else {
                        path.parent?.let { SystemFileSystem.createDirectories(it) }
                        zip.getInputStream(entry).use { input ->
                            SystemFileSystem.sink(path).buffered().use { bufferedSink ->
                                val buffer = ByteArray(8192)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read == -1) break
                                    bufferedSink.write(buffer, 0, read)
                                }
                            }
                        }
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to unzip $source to $destination" }
            Result.failure(e)
        }
    }

    actual fun getUnzippedSize(zipPath: String): Long {
        return try {
            ZipFile(zipPath).use { zip ->
                zip.entries().asSequence().sumOf { it.size }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Error calculating unzipped size for $zipPath" }
            0L
        }
    }

    actual fun deleteDirectory(path: String): Boolean {
        Logger.d { "Deleting directory: $path" }
        return try {
            deleteRecursively(Path(path))
            true
        } catch (e: Exception) {
            Logger.e(e) { "Error deleting directory $path: ${e.message}" }
            false
        }
    }

    private fun deleteRecursively(path: Path) {
        if (SystemFileSystem.exists(path)) {
            val metadata = SystemFileSystem.metadataOrNull(path)
            if (metadata?.isDirectory == true) {
                SystemFileSystem.list(path).forEach { child ->
                    deleteRecursively(child)
                }
            }
            SystemFileSystem.delete(path)
        }
    }

    actual fun exists(path: String): Boolean {
        return SystemFileSystem.exists(Path(path))
    }

    actual fun mkdirs(path: String): Boolean {
        SystemFileSystem.createDirectories(Path(path))
        return true
    }

    actual fun listDirectories(path: String): List<String> {
        return SystemFileSystem.list(Path(path))
            .filter { SystemFileSystem.metadataOrNull(it)?.isDirectory == true }
            .map { it.toString() }
    }

    actual fun listFiles(path: String): List<String> {
        return SystemFileSystem.list(Path(path))
            .filter { SystemFileSystem.metadataOrNull(it)?.isDirectory == false }
            .map { it.toString() }
    }

    actual fun readFile(path: String): String? {
        val p = Path(path)
        return if (SystemFileSystem.exists(p)) {
            SystemFileSystem.source(p).buffered().use { it.readString() }
        } else null
    }

    actual fun writeFile(path: String, content: String) {
        val p = Path(path)
        p.parent?.let { SystemFileSystem.createDirectories(it) }
        SystemFileSystem.sink(p).buffered().use { it.writeString(content) }
    }

    actual fun readBytes(path: String): ByteArray? {
        val p = Path(path)
        return if (SystemFileSystem.exists(p)) {
            SystemFileSystem.source(p).buffered().use { it.readByteArray() }
        } else null
    }

    actual fun readFileFromZip(zipPath: String, fileName: String): String? {
        return try {
            ZipFile(zipPath).use { zip ->
                val entry = zip.getEntry(fileName) ?: zip.entries().asSequence()
                    .find { it.name.equals(fileName, ignoreCase = true) }
                ?: return null
                zip.getInputStream(entry).use { it.bufferedReader().readText() }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Error reading $fileName from $zipPath" }
            null
        }
    }

    actual fun readBytesFromZip(zipPath: String, fileName: String): ByteArray? {
        return try {
            ZipFile(zipPath).use { zip ->
                val entry = zip.getEntry(fileName) ?: zip.entries().asSequence()
                    .find { it.name.equals(fileName, ignoreCase = true) }
                ?: return null
                zip.getInputStream(entry).use { it.readBytes() }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Error reading bytes for $fileName from $zipPath" }
            null
        }
    }

    actual fun zipEntries(entries: Map<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for ((name, bytes) in entries) {
                val entry = ZipEntry(name)
                zos.putNextEntry(entry)
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    actual fun unzipEntries(zipBytes: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val content = zis.bufferedReader().readText()
                    result[entry.name] = content
                }
                entry = zis.nextEntry
            }
        }
        return result
    }

    actual fun unzipBytesEntries(zipBytes: ByteArray): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    result[entry.name] = zis.readBytes()
                }
                entry = zis.nextEntry
            }
        }
        return result
    }

    actual fun calculateFileChecksum(path: String): String? {

        return try {
            val file = File(path)
            if (!file.exists()) return null

            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Logger.e(e) { "Error calculating checksum for $path" }
            null
        }
    }
}
