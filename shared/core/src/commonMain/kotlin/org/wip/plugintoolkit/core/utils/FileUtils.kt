package org.wip.plugintoolkit.core.utils

expect object FileUtils {
    val isWindows: Boolean
    val isLinux: Boolean
    val isMac: Boolean

    fun copyFile(source: String, destination: String)
    fun downloadFile(url: String, destination: String): Result<Unit>
    fun unzip(source: String, destination: String, maxDecompressedSize: Long = 100 * 1024 * 1024): Result<Unit>
    fun getUnzippedSize(zipPath: String): Long
    fun deleteDirectory(path: String): Boolean
    fun exists(path: String): Boolean
    fun mkdirs(path: String): Boolean
    fun listDirectories(path: String): List<String>
    fun listFiles(path: String): List<String>
    fun readFile(path: String): String?
    fun writeFile(path: String, content: String)
    fun readFileFromZip(zipPath: String, fileName: String): String?
    fun readBytesFromZip(zipPath: String, fileName: String): ByteArray?
    fun readBytes(path: String): ByteArray?
    fun zipEntries(entries: Map<String, ByteArray>): ByteArray
    fun unzipEntries(zipBytes: ByteArray): Map<String, String>
    fun unzipBytesEntries(zipBytes: ByteArray): Map<String, ByteArray>
    fun calculateFileChecksum(path: String): String?
}

