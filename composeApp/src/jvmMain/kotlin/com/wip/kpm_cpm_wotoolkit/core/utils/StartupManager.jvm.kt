package com.wip.kpm_cpm_wotoolkit.core.utils

import com.wip.kpm_cpm_wotoolkit.core.KeepTrack
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import java.util.Locale

actual object StartupManager {
    private val osName = System.getProperty("os.name").lowercase(Locale.ENGLISH)
    private val isWindows = osName.contains("win")
    private val isLinux = osName.contains("linux")

    private val appName = KeepTrack.STARTUP_APP_NAME
    private val backgroundFlag = KeepTrack.STARTUP_FLAG_BACKGROUND

    actual fun setLaunchAtStartup(enabled: Boolean, minimized: Boolean) {
        if (isWindows) {
            setWindowsStartup(enabled, minimized)
        } else if (isLinux) {
            setLinuxStartup(enabled, minimized)
        }
    }

    actual fun isLaunchAtStartupEnabled(): Boolean {
        return if (isWindows) {
            isWindowsStartupEnabled()
        } else if (isLinux) {
            isLinuxStartupEnabled()
        } else {
            false
        }
    }

    private fun getExecutablePath(): String? {
        // Try to get the current running command
        val command = ProcessHandle.current().info().command().orElse(null)
        if (command != null && SystemFileSystem.exists(Path(command))) {
            return command
        }
        return null
    }

    private fun setWindowsStartup(enabled: Boolean, minimized: Boolean) {
        val exePath = getExecutablePath() ?: return
        val commandLine = if (minimized) "\"$exePath\" $backgroundFlag" else "\"$exePath\""
        val command = if (enabled) {
            arrayOf(
                "reg",
                "add",
                KeepTrack.WINDOWS_STARTUP_REGISTRY_PATH,
                "/v",
                appName,
                "/t",
                "REG_SZ",
                "/d",
                commandLine,
                "/f"
            )
        } else {
            arrayOf("reg", "delete", KeepTrack.WINDOWS_STARTUP_REGISTRY_PATH, "/v", appName, "/f")
        }

        try {
            ProcessBuilder(*command).start().waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun isWindowsStartupEnabled(): Boolean {
        try {
            val process = ProcessBuilder("reg", "query", KeepTrack.WINDOWS_STARTUP_REGISTRY_PATH, "/v", appName).start()
            return process.waitFor() == 0
        } catch (e: Exception) {
            return false
        }
    }

    private fun setLinuxStartup(enabled: Boolean, minimized: Boolean) {
        val exePath = getExecutablePath() ?: return
        val autostartDirPath = "${System.getProperty("user.home")}/${KeepTrack.LINUX_AUTOSTART_DIR}"
        val autostartDir = Path(autostartDirPath)
        if (!SystemFileSystem.exists(autostartDir)) {
            SystemFileSystem.createDirectories(autostartDir)
        }
        val desktopFile = Path("$autostartDirPath/${KeepTrack.LINUX_DESKTOP_FILENAME}")

        if (enabled) {
            val execCommand = if (minimized) "$exePath $backgroundFlag" else exePath
            val content = """
                [Desktop Entry]
                Type=Application
                Exec=$execCommand
                Hidden=false
                NoDisplay=false
                X-GNOME-Autostart-enabled=true
                Name=$appName
                Comment=Start $appName at login
            """.trimIndent()
            SystemFileSystem.sink(desktopFile).use { it.buffered().writeString(content) }
        } else {
            if (SystemFileSystem.exists(desktopFile)) {
                SystemFileSystem.delete(desktopFile)
            }
        }
    }

    private fun isLinuxStartupEnabled(): Boolean {
        val autostartDirPath = "${System.getProperty("user.home")}/${KeepTrack.LINUX_AUTOSTART_DIR}"
        val desktopFile = Path("$autostartDirPath/${KeepTrack.LINUX_DESKTOP_FILENAME}")
        return SystemFileSystem.exists(desktopFile)
    }
}
