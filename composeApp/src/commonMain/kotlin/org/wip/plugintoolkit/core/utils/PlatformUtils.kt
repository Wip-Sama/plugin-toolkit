package org.wip.plugintoolkit.core.utils

import androidx.compose.ui.graphics.Color

expect object PlatformUtils {
    val isWindows: Boolean
    val isLinux: Boolean
    val isMac: Boolean
    fun getSystemAccentColor(): Color?
    suspend fun pickFolder(): String?
    suspend fun pickFile(): String?
    fun copyFile(source: String, destination: String)
    fun downloadFile(url: String, destination: String): Result<Unit>
    fun unzip(source: String, destination: String, maxDecompressedSize: Long): Result<Unit>
    fun getUnzippedSize(zipPath: String): Long
    fun deleteDirectory(path: String): Boolean
    fun exists(path: String): Boolean
    fun mkdirs(path: String): Boolean
    fun listDirectories(path: String): List<String>
    fun listFiles(path: String): List<String>
    fun readFile(path: String): String?
    fun writeFile(path: String, content: String)
    fun readFileFromZip(zipPath: String, fileName: String): String?
    fun installUpdate(path: String)
}
