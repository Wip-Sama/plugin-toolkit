package org.wip.plugintoolkit.core.utils

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

interface FileSystem {
    fun exists(path: String): Boolean
    fun mkdirs(path: String): Boolean
    fun copyFile(source: String, destination: String)
    fun deleteDirectory(path: String): Boolean
    fun readFileFromZip(zipPath: String, fileName: String): String?
    fun listFiles(path: String): List<String>
    fun saveFile(path: String, content: ByteArray)
    fun readFile(path: String): String?
    fun writeFile(path: String, content: String)
}

class RealFileSystem : FileSystem {
    override fun exists(path: String): Boolean = FileUtils.exists(path)
    override fun mkdirs(path: String): Boolean = FileUtils.mkdirs(path)
    override fun copyFile(source: String, destination: String) = FileUtils.copyFile(source, destination)
    override fun deleteDirectory(path: String): Boolean = FileUtils.deleteDirectory(path)
    override fun readFileFromZip(zipPath: String, fileName: String): String? =
        FileUtils.readFileFromZip(zipPath, fileName)

    override fun listFiles(path: String): List<String> = FileUtils.listFiles(path)

    override fun saveFile(path: String, content: ByteArray) {
        val p = Path(path)
        p.parent?.let { SystemFileSystem.createDirectories(it) }
        SystemFileSystem.sink(p).buffered().use { sink ->
            sink.write(content)
        }
    }

    override fun readFile(path: String): String? = FileUtils.readFile(path)
    override fun writeFile(path: String, content: String) = FileUtils.writeFile(path, content)
}
