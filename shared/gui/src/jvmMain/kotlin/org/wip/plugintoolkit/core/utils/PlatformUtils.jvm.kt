package org.wip.plugintoolkit.core.utils

import androidx.compose.ui.graphics.Color
import co.touchlab.kermit.Logger
import com.sun.jna.platform.win32.Advapi32Util.registryGetIntValue
import com.sun.jna.platform.win32.Advapi32Util.registryValueExists
import com.sun.jna.platform.win32.Shell32
import com.sun.jna.platform.win32.WinUser
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.dialogs.openFileSaver
import io.github.vinceglb.filekit.toKotlinxIoPath
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder.forSessionBus
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.Variant
import java.awt.Desktop
import java.io.File
import plugintoolkit.composeapp.generated.resources.*

actual object PlatformUtils {
    actual val isWindows: Boolean = FileUtils.isWindows
    actual val isLinux: Boolean = FileUtils.isLinux
    actual val isMac: Boolean = FileUtils.isMac

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

    actual suspend fun pickFolder(): String? {
        return FileKit.openDirectoryPicker(
            dialogSettings = FileKitDialogSettings(title = org.jetbrains.compose.resources.getString(Res.string.dialog_select_folder))
        )?.toKotlinxIoPath()?.toString()
    }

    actual suspend fun pickFile(): String? {
        return FileKit.openFilePicker(
            dialogSettings = FileKitDialogSettings(title = org.jetbrains.compose.resources.getString(Res.string.dialog_select_plugin))
        )?.toKotlinxIoPath()?.toString()
    }

    actual fun copyFile(source: String, destination: String) = FileUtils.copyFile(source, destination)
    actual fun downloadFile(url: String, destination: String): Result<Unit> = FileUtils.downloadFile(url, destination)
    actual fun unzip(source: String, destination: String, maxDecompressedSize: Long): Result<Unit> =
        FileUtils.unzip(source, destination, maxDecompressedSize)
    actual fun getUnzippedSize(zipPath: String): Long = FileUtils.getUnzippedSize(zipPath)
    actual fun deleteDirectory(path: String): Boolean = FileUtils.deleteDirectory(path)
    actual fun exists(path: String): Boolean = FileUtils.exists(path)
    actual fun mkdirs(path: String): Boolean = FileUtils.mkdirs(path)
    actual fun listDirectories(path: String): List<String> = FileUtils.listDirectories(path)
    actual fun listFiles(path: String): List<String> = FileUtils.listFiles(path)
    actual fun readFile(path: String): String? = FileUtils.readFile(path)
    actual fun writeFile(path: String, content: String) = FileUtils.writeFile(path, content)
    actual fun readFileFromZip(zipPath: String, fileName: String): String? = FileUtils.readFileFromZip(zipPath, fileName)
    actual fun readBytesFromZip(zipPath: String, fileName: String): ByteArray? = FileUtils.readBytesFromZip(zipPath, fileName)

    private fun getCurrentInstallDir(): String? {
        return try {
            val exePath = ProcessHandle.current().info().command().orElse(null) ?: return null
            val file = File(exePath)
            val name = file.name.lowercase()
            if (name.contains("java") || name.contains("idea") || name.contains("kotlinc")) {
                return null
            }
            file.parentFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    actual fun installUpdate(path: String) {
        val file = File(path)
        if (!file.exists()) {
            Logger.e { "Installer file not found at $path" }
            return
        }

        val currentInstallDir = getCurrentInstallDir()
        Logger.i { "Launching installer: $path (Target Dir: ${currentInstallDir ?: "Default"})" }

        try {
            if (isWindows) {
                val normalizedPath = path.replace("/", "\\")
                val params = when {
                    normalizedPath.endsWith(".msi") -> {
                        val base = "/i \"$normalizedPath\""
                        if (currentInstallDir != null) "$base INSTALLDIR=\"$currentInstallDir\"" else base
                    }

                    normalizedPath.endsWith(".exe") -> {
                        if (currentInstallDir != null) "INSTALLDIR=\"$currentInstallDir\"" else ""
                    }

                    else -> ""
                }

                val executable = if (normalizedPath.endsWith(".msi")) "msiexec.exe" else normalizedPath

                Logger.i { "Executing ShellExecute: $executable $params" }

                val result = Shell32.INSTANCE.ShellExecute(
                    null,
                    "open",
                    executable,
                    params,
                    null,
                    WinUser.SW_SHOWNORMAL
                )

                val resultCode = com.sun.jna.Pointer.nativeValue(result.toPointer())
                if (resultCode <= 32) {
                    throw Exception("ShellExecute failed with code $resultCode")
                }
            } else if (isLinux) {
                if (path.endsWith(".deb")) {
                    ProcessBuilder("pkexec", "dpkg", "-i", path).start()
                } else {
                    Desktop.getDesktop().open(file)
                }
            } else {
                Desktop.getDesktop().open(file)
            }

            Thread.sleep(1500)
            Logger.i { "Application exiting for update installation" }
            System.exit(0)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to launch installer at $path" }
        }
    }

    actual suspend fun saveFile(baseName: String, extension: String, bytes: ByteArray): String? {
        return try {
            val file = FileKit.openFileSaver(
                suggestedName = baseName,
                extension = extension
            )
            if (file != null) {
                val pathStr = file.toKotlinxIoPath().toString()
                java.io.File(pathStr).writeBytes(bytes)
                pathStr
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to save file using FileKit" }
            null
        }
    }

    actual suspend fun pickFile(title: String, allowedExtensions: List<String>): String? {
        val type = if (allowedExtensions.isEmpty()) {
            FileKitType.File()
        } else {
            FileKitType.File(extensions = allowedExtensions)
        }
        return FileKit.openFilePicker(
            type = type,
            dialogSettings = FileKitDialogSettings(title = title)
        )?.toKotlinxIoPath()?.toString()
    }

    actual fun readBytes(path: String): ByteArray? = FileUtils.readBytes(path)
    actual fun writeBytes(path: String, bytes: ByteArray) {
        val file = java.io.File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    actual fun zipEntries(entries: Map<String, String>): ByteArray =
        FileUtils.zipEntries(entries.mapValues { it.value.encodeToByteArray() })

    actual fun unzipEntries(bytes: ByteArray): Map<String, String> =
        FileUtils.unzipEntries(bytes)

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    actual fun clipEntryOf(text: String): androidx.compose.ui.platform.ClipEntry {
        return androidx.compose.ui.platform.ClipEntry(java.awt.datatransfer.StringSelection(text))
    }

    actual fun calculateFileChecksum(path: String, algorithm: String): String? =
        FileUtils.calculateFileChecksum(path)

    actual fun openFolder(path: String) {
        try {
            val cleanPath = path.trim('"', '\'')
            val file = File(cleanPath)
            if (file.exists()) {
                val isWindows = System.getProperty("os.name").lowercase().contains("win")
                if (file.isDirectory) {
                    try {
                        Desktop.getDesktop().open(file)
                    } catch (e: Exception) {
                        if (isWindows) {
                            ProcessBuilder("explorer.exe", file.absolutePath).start()
                        } else {
                            throw e
                        }
                    }
                } else {
                    if (isWindows) {
                        try {
                            ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
                        } catch (_: Exception) {
                            Desktop.getDesktop().open(file)
                        }
                    } else {
                        try {
                            Desktop.getDesktop().open(file)
                        } catch (_: Exception) {
                            file.parentFile?.let { Desktop.getDesktop().open(it) }
                        }
                    }
                }
            } else {
                Logger.w { "Folder or file does not exist: $path" }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to open folder $path" }
        }
    }

    actual fun horizontalResizePointerIcon(): androidx.compose.ui.input.pointer.PointerIcon =
        androidx.compose.ui.input.pointer.PointerIcon(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.E_RESIZE_CURSOR))

    actual fun verticalResizePointerIcon(): androidx.compose.ui.input.pointer.PointerIcon =
        androidx.compose.ui.input.pointer.PointerIcon(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.S_RESIZE_CURSOR))

    actual fun diagonalResizePointerIcon(): androidx.compose.ui.input.pointer.PointerIcon =
        androidx.compose.ui.input.pointer.PointerIcon(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.SE_RESIZE_CURSOR))
}

interface PortalSettings : DBusInterface {
    fun Read(suite: String, key: String): Variant<*>
}
