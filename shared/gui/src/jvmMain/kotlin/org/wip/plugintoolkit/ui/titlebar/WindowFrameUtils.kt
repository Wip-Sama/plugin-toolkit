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
    private const val DWMWA_BORDER_COLOR = 34
    private const val DWMWA_CAPTION_COLOR = 35
    private const val DWMWA_TEXT_COLOR = 36

    // Corner preference values
    private const val DWMWCP_ROUND = 2

    /**
     * Enables the native Windows drop shadow and rounded corners for an undecorated window.
     */
    fun enableUndecoratedDropShadow(window: Window) {
        if (!PlatformUtils.isWindows) return
        val dwm = DwmapiLib.INSTANCE ?: return

        try {
            val hwnd = getHwnd(window) ?: return
            val margins = Margins().apply {
                cxLeftWidth = 1
                cxRightWidth = 1
                cyTopHeight = 1
                cyBottomHeight = 1
            }
            dwm.DwmExtendFrameIntoClientArea(hwnd, margins)

            // Request rounded corners on Windows 11
            val cornerPref = IntByReference(DWMWCP_ROUND)
            dwm.DwmSetWindowAttribute(
                hwnd,
                DWMWA_WINDOW_CORNER_PREFERENCE,
                cornerPref.pointer,
                4
            )
            Logger.d { "DWM: Native drop shadow and corner preference configured for window" }
        } catch (e: Throwable) {
            Logger.w(e) { "DWM: Failed to configure drop shadow on window" }
        }
    }

    /**
     * Configures dark mode and caption colors for decorated (native) Windows frames.
     */
    fun setNativeTitleBarTheme(window: Window, isDark: Boolean, captionColor: Color? = null, textColor: Color? = null) {
        if (!PlatformUtils.isWindows) return
        val dwm = DwmapiLib.INSTANCE ?: return

        try {
            val hwnd = getHwnd(window) ?: return

            // 1. Immersive dark mode (Windows 10 18985+ & Windows 11)
            val darkModeVal = IntByReference(if (isDark) 1 else 0)
            dwm.DwmSetWindowAttribute(
                hwnd,
                DWMWA_USE_IMMERSIVE_DARK_MODE,
                darkModeVal.pointer,
                4
            )

            // 2. Custom caption & text color (Windows 11 build 22000+)
            if (captionColor != null) {
                val colorRef = toColorRef(captionColor)
                val captionVal = IntByReference(colorRef)
                dwm.DwmSetWindowAttribute(hwnd, DWMWA_CAPTION_COLOR, captionVal.pointer, 4)
            }

            if (textColor != null) {
                val colorRef = toColorRef(textColor)
                val textVal = IntByReference(colorRef)
                dwm.DwmSetWindowAttribute(hwnd, DWMWA_TEXT_COLOR, textVal.pointer, 4)
            }
        } catch (e: Throwable) {
            Logger.w(e) { "DWM: Failed to apply native title bar theme" }
        }
    }

    private fun getHwnd(window: Window): WinDef.HWND? {
        return try {
            val pointer = Native.getWindowPointer(window)
            if (pointer != null) WinDef.HWND(pointer) else null
        } catch (e: Throwable) {
            Logger.w(e) { "DWM: Unable to obtain HWND for window" }
            null
        }
    }

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
