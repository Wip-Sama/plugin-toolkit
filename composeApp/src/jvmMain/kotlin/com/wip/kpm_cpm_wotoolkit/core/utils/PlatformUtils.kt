package com.wip.kpm_cpm_wotoolkit.core.utils

import androidx.compose.ui.graphics.Color
import com.sun.jna.platform.win32.Advapi32Util.registryGetIntValue
import com.sun.jna.platform.win32.Advapi32Util.registryValueExists
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder.forSessionBus
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.Variant
import co.touchlab.kermit.Logger

actual object PlatformUtils {
    actual val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("win")
    actual val isLinux: Boolean = System.getProperty("os.name").lowercase().contains("nux")

    actual fun getSystemAccentColor(): Color? {
        return when {
            isWindows -> getWindowsAccentColor()
            isLinux -> getLinuxAccentColor()
            else -> null
        }
    }

    private fun getWindowsAccentColor(): Color? {
        return try {
            val hKey = com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER
            val path = "Software\\Microsoft\\Windows\\DWM"
            val key = "AccentColor"

            if (registryValueExists(hKey, path, key)) {
                val accent: Int = registryGetIntValue(hKey, path, key)

                val r = (accent and 0xFF).toFloat() / 255f
                val g = ((accent shr 8) and 0xFF).toFloat() / 255f
                val b = ((accent shr 16) and 0xFF).toFloat() / 255f

                Color(red = r, green = g, blue = b, alpha = 1f)
            } else null
        } catch (e: Exception) {
            Logger.e(e) { "Error getting Windows accent color" }
            null
        }
    }

    private fun getLinuxAccentColor(): Color? {
        return try {
            forSessionBus().build().use { conn ->
                val settings =
                        conn.getRemoteObject(
                                "org.freedesktop.portal.Desktop",
                                "/org/freedesktop/portal/desktop",
                                PortalSettings::class.java
                        )

                val result = settings.Read("org.freedesktop.appearance", "accent-color")
                val value = result.value

                if (value is Array<*> && value.size >= 3) {
                    var r = (value[0] as Number).toDouble()
                    var g = (value[1] as Number).toDouble()
                    var b = (value[2] as Number).toDouble()
                    if (r > 1.0 || g > 1.0 || b > 1.0) {
                        r /= 255.0
                        g /= 255.0
                        b /= 255.0
                    }
                    Color(red = r.toFloat(), green = g.toFloat(), blue = b.toFloat(), alpha = 1f)
                } else null
            }
        } catch (e: Exception) {
            Logger.e(e) { "Error getting Linux accent color" }
            null
        }
    }

    actual fun pickFolder(): String? {
        val chooser = javax.swing.JFileChooser().apply {
            fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Select Folder"
        }
        val result = chooser.showOpenDialog(null)
        return if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.absolutePath
        } else null
    }

    actual fun pickFile(): String? {
        val chooser = javax.swing.JFileChooser().apply {
            fileSelectionMode = javax.swing.JFileChooser.FILES_ONLY
            dialogTitle = "Select Module File"
        }
        val result = chooser.showOpenDialog(null)
        return if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.absolutePath
        } else null
    }

    actual fun copyFile(source: String, destination: String) {
        Logger.d { "Copying file from $source to $destination" }
        java.io.File(source).copyTo(java.io.File(destination), overwrite = true)
    }

    actual fun downloadFile(url: String, destination: String): Result<Unit> {
        return try {
            java.net.URL(url).openStream().use { input ->
                java.io.File(destination).outputStream().use { output ->
                    input.copyTo(output)
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
            java.util.zip.ZipFile(source).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    val file = java.io.File(destination, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            file.outputStream().use { output ->
                                input.copyTo(output)
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
            java.util.zip.ZipFile(zipPath).use { zip ->
                zip.entries().asSequence().sumOf { it.size }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Error calculating unzipped size for $zipPath" }
            0L
        }
    }

    actual fun deleteDirectory(path: String): Boolean {
        Logger.d { "Deleting directory: $path" }
        return java.io.File(path).deleteRecursively()
    }

    actual fun exists(path: String): Boolean {
        return java.io.File(path).exists()
    }

    actual fun mkdirs(path: String): Boolean {
        return java.io.File(path).mkdirs()
    }

    actual fun listDirectories(path: String): List<String> {
        return java.io.File(path).listFiles()?.filter { it.isDirectory }?.map { it.absolutePath } ?: emptyList()
    }

    actual fun readFile(path: String): String? {
        val file = java.io.File(path)
        return if (file.exists()) file.readText() else null
    }
    
    actual fun readFileFromZip(zipPath: String, fileName: String): String? {
        return try {
            java.util.zip.ZipFile(zipPath).use { zip ->
                val entry = zip.getEntry(fileName) ?: return null
                zip.getInputStream(entry).use { it.bufferedReader().readText() }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Error reading $fileName from $zipPath" }
            null
        }
    }
}

interface PortalSettings : DBusInterface {
    fun Read(suite: String, key: String): Variant<*>
}
