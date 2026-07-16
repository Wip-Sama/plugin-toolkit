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
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder.forSessionBus
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.Variant
import java.awt.Desktop
import java.io.File
import org.jetbrains.compose.resources.stringResource
import plugintoolkit.composeapp.generated.resources.*

actual object PlatformUtils {
    actual val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("win")
    actual val isLinux: Boolean = System.getProperty("os.name").lowercase().contains("nux")
    actual val isMac: Boolean = System.getProperty("os.name").lowercase().contains("mac")

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

    private fun getCurrentInstallDir(): String? {
        return try {
            val exePath = ProcessHandle.current().info().command().orElse(null) ?: return null
            val file = File(exePath)
            // Basic check to avoid passing IDE/Java paths
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
                        print(currentInstallDir)
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

            // Give it a moment to start before we kill the current process
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

    actual fun readBytes(path: String): ByteArray? {
        val file = java.io.File(path)
        return if (file.exists()) file.readBytes() else null
    }

    actual fun writeBytes(path: String, bytes: ByteArray) {
        val file = java.io.File(path)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
    }

    actual fun zipEntries(entries: Map<String, String>): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(bos).use { zos ->
            entries.forEach { (name, content) ->
                zos.putNextEntry(java.util.zip.ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    actual fun unzipEntries(bytes: ByteArray): Map<String, String> {
        val result = mutableMapOf<String, String>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val content = zis.bufferedReader(Charsets.UTF_8).readText()
                    result[entry.name] = content
                }
                entry = zis.nextEntry
            }
        }
        return result
    }

    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    actual fun clipEntryOf(text: String): androidx.compose.ui.platform.ClipEntry {
        return androidx.compose.ui.platform.ClipEntry(java.awt.datatransfer.StringSelection(text))
    }

    actual fun calculateFileChecksum(path: String, algorithm: String): String? {
        val file = java.io.File(path)
        if (!file.exists()) return null
        return try {
            val digest = java.security.MessageDigest.getInstance(algorithm)
            file.inputStream().use { fis ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to calculate $algorithm checksum for $path" }
            null
        }
    }

    actual fun openFolder(path: String) {
        try {
            val file = File(path)
            if (file.exists()) {
                Desktop.getDesktop().open(file)
            } else {
                Logger.w { "Folder does not exist: $path" }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to open folder $path" }
        }
    }
}

interface PortalSettings : DBusInterface {
    fun Read(suite: String, key: String): Variant<*>
}
