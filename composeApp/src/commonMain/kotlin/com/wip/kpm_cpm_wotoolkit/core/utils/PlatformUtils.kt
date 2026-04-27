package com.wip.kpm_cpm_wotoolkit.core.utils

import androidx.compose.ui.graphics.Color

expect object PlatformUtils {
    val isWindows: Boolean
    val isLinux: Boolean
    fun getSystemAccentColor(): Color?
    fun pickFolder(): String?
    fun pickFile(): String?
    fun copyFile(source: String, destination: String)
    fun downloadFile(url: String, destination: String): Result<Unit>
    fun unzip(source: String, destination: String, maxDecompressedSize: Long): Result<Unit>
    fun getUnzippedSize(zipPath: String): Long
    fun deleteDirectory(path: String): Boolean
    fun exists(path: String): Boolean
    fun mkdirs(path: String): Boolean
    fun listDirectories(path: String): List<String>
    fun readFile(path: String): String?
}
