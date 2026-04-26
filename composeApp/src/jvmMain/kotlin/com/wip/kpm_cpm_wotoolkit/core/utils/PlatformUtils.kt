package com.wip.kpm_cpm_wotoolkit.core.utils

import androidx.compose.ui.graphics.Color
import com.sun.jna.platform.win32.Advapi32Util.registryGetIntValue
import com.sun.jna.platform.win32.Advapi32Util.registryValueExists
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
            null
        }
    }
}

interface PortalSettings : DBusInterface {
    fun Read(suite: String, key: String): Variant<*>
}
