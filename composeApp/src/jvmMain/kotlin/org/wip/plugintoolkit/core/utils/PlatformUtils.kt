package org.wip.plugintoolkit.core.utils


import androidx.compose.ui.graphics.Color
import co.touchlab.kermit.Logger
import com.sun.jna.platform.win32.Advapi32Util.registryGetIntValue
import com.sun.jna.platform.win32.Advapi32Util.registryValueExists
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.openDirectoryPicker
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.toKotlinxIoPath
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder.forSessionBus
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.Variant

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

    actual suspend fun pickFolder(): String? {
        return FileKit.openDirectoryPicker(
            dialogSettings = FileKitDialogSettings(title = "Select Folder")
        )?.toKotlinxIoPath()?.toString()
    }

    actual suspend fun pickFile(): String? {
        return FileKit.openFilePicker(
            dialogSettings = FileKitDialogSettings(title = "Select Plugin File")
        )?.toKotlinxIoPath()?.toString()
    }


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
            java.net.URL(url).openStream().use { input ->
                val dst = Path(destination)
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
            java.util.zip.ZipFile(source).use { zip ->
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

    actual fun readFileFromZip(zipPath: String, fileName: String): String? {
        return try {
            java.util.zip.ZipFile(zipPath).use { zip ->
                val entry = zip.getEntry(fileName) ?: zip.entries().asSequence().find { it.name.equals(fileName, ignoreCase = true) }
                ?: return null
                zip.getInputStream(entry).use { it.bufferedReader().readText() }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Error reading $fileName from $zipPath" }
            null
        }
    }

    actual fun installUpdate(path: String) {
        val file = java.io.File(path)
        if (!file.exists()) return

        try {
            if (isWindows) {
                if (path.endsWith(".msi")) {
                    ProcessBuilder("msiexec", "/i", path).start()
                } else {
                    java.awt.Desktop.getDesktop().open(file)
                }
            } else if (isLinux) {
                if (path.endsWith(".deb")) {
                    // Try to use a system tool to install it
                    ProcessBuilder("pkexec", "dpkg", "-i", path).start()
                } else {
                    java.awt.Desktop.getDesktop().open(file)
                }
            } else {
                java.awt.Desktop.getDesktop().open(file)
            }
            // Exit the application to allow the installer to replace files
            System.exit(0)
        } catch (e: Exception) {
            Logger.e(e) { "Failed to launch installer" }
        }
    }
}

interface PortalSettings : DBusInterface {
    fun Read(suite: String, key: String): Variant<*>
}
