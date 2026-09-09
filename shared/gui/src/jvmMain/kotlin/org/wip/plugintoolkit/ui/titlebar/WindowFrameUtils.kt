package org.wip.plugintoolkit.ui.titlebar

import androidx.compose.ui.graphics.Color
import co.touchlab.kermit.Logger
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import org.wip.plugintoolkit.core.utils.PlatformUtils
import java.awt.Window

/**
 * Native DWM margins structure for DwmExtendFrameIntoClientArea.
 */
@Structure.FieldOrder("cxLeftWidth", "cxRightWidth", "cyTopHeight", "cyBottomHeight")
class Margins : Structure() {
    @JvmField var cxLeftWidth: Int = 1
    @JvmField var cxRightWidth: Int = 1
    @JvmField var cyTopHeight: Int = 1
    @JvmField var cyBottomHeight: Int = 1
}

/**
 * Direct JNA mapping for Desktop Window Manager (dwmapi.dll) APIs.
 */
interface DwmapiLib : Library {
    fun DwmExtendFrameIntoClientArea(hwnd: WinDef.HWND, pMarInset: Margins): WinNT.HRESULT
    fun DwmSetWindowAttribute(hwnd: WinDef.HWND, dwAttribute: Int, pvAttribute: Pointer, cbAttribute: Int): WinNT.HRESULT

    companion object {
        val INSTANCE: DwmapiLib? = try {
            if (PlatformUtils.isWindows) {
                Native.load("dwmapi", DwmapiLib::class.java)
            } else {
                null
            }
        } catch (e: Throwable) {
            Logger.w(e) { "DWM: Failed to load dwmapi.dll" }
            null
        }
    }
}

/**
 * Utility functions for platform window framing, DWM drop shadows, and title bar theming.
 */
object WindowFrameUtils {
    // DWM window attribute constants
    private const val DWMWA_USE_IMMERSIVE_DARK_MODE = 20
    private const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    // Corner preference values
    private const val DWMWCP_ROUND = 2

    /**
     * Enables the native Windows drop shadow and rounded corners for an undecorated window.
     * Uses 1px DWM margins to activate shadow rendering without showing any native frame.
     */
    fun enableUndecoratedDropShadow(window: Window) {
        if (!PlatformUtils.isWindows) return
        val dwm = DwmapiLib.INSTANCE ?: return

        try {
            val hwnd = getHwnd(window) ?: return

            // 1px margins enable DWM shadow without visible native frame
            val margins = Margins().apply {
                cxLeftWidth = 1; cxRightWidth = 1; cyTopHeight = 1; cyBottomHeight = 1
            }
            dwm.DwmExtendFrameIntoClientArea(hwnd, margins)

            // Request rounded corners on Windows 11
            val cornerPref = IntByReference(DWMWCP_ROUND)
            dwm.DwmSetWindowAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, cornerPref.pointer, 4)

            Logger.d { "DWM: Drop shadow and rounded corners configured" }
        } catch (e: Throwable) {
            Logger.w(e) { "DWM: Failed to configure drop shadow" }
        }
    }

    /**
     * Configures dark mode and caption colors for decorated (native) Windows frames.
     */
    fun setNativeTitleBarTheme(
        window: Window,
        isDark: Boolean,
        captionColor: Color? = null,
        textColor: Color? = null
    ) {
        if (!PlatformUtils.isWindows) return
        val dwm = DwmapiLib.INSTANCE ?: return

        try {
            val hwnd = getHwnd(window) ?: return

            // Immersive dark mode (Windows 10 18985+ & Windows 11)
            val darkModeVal = IntByReference(if (isDark) 1 else 0)
            dwm.DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, darkModeVal.pointer, 4)

            // Custom caption & text color (Windows 11 build 22000+)
            if (captionColor != null) {
                val captionVal = IntByReference(toColorRef(captionColor))
                dwm.DwmSetWindowAttribute(hwnd, DWMWA_CAPTION_COLOR, captionVal.pointer, 4)
            }

            if (textColor != null) {
                val textVal = IntByReference(toColorRef(textColor))
                dwm.DwmSetWindowAttribute(hwnd, DWMWA_TEXT_COLOR, textVal.pointer, 4)
            }
        } catch (e: Throwable) {
            Logger.w(e) { "DWM: Failed to apply native title bar theme" }
        }
    }

    /**
     * Returns the Win32 HWND for the given AWT window, or null if unavailable.
     */
    fun getHwnd(window: Window): WinDef.HWND? {
        return try {
            val pointer = Native.getWindowPointer(window)
            if (pointer != null) WinDef.HWND(pointer) else null
        } catch (e: Throwable) {
            Logger.w(e) { "DWM: Unable to obtain HWND for window" }
            null
        }
    }

    /**
     * Returns the raw Win32 HWND value as a Long for JNI interop, or 0L if unavailable.
     */
    fun getHwndAsLong(window: Window): Long {
        return try {
            val pointer = Native.getWindowPointer(window) ?: return 0L
            Pointer.nativeValue(pointer)
        } catch (e: Throwable) {
            0L
        }
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Converts a Compose Color to Windows COLORREF format (0x00BBGGRR).
     */
    private fun toColorRef(color: Color): Int {
        val r = (color.red * 255).toInt().coerceIn(0, 255)
        val g = (color.green * 255).toInt().coerceIn(0, 255)
        val b = (color.blue * 255).toInt().coerceIn(0, 255)
        return (b shl 16) or (g shl 8) or r
    }
}
