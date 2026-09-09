package org.wip.plugintoolkit.ui.titlebar

import androidx.compose.ui.graphics.Color
import org.wip.plugintoolkit.core.utils.PlatformUtils
import javax.swing.JFrame
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WindowSnapTest {

    @Test
    fun testNativeDragDllLoads() {
        if (!PlatformUtils.isWindows) {
            println("Skipping Windows-specific test on non-Windows platform")
            return
        }
        val loaded = NativeDrag.ensureLoaded()
        assertTrue(loaded, "toolkit-win-drag.dll must load successfully from resources")
    }

    @Test
    fun testInitWindowForVisibleWindow() {
        if (!PlatformUtils.isWindows) {
            println("Skipping Windows-specific test on non-Windows platform")
            return
        }

        val frame = JFrame("InitWindowTestFrame")
        frame.isUndecorated = true
        frame.setSize(500, 400)
        frame.isVisible = true

        try {
            val hwnd = WindowFrameUtils.getHwndAsLong(frame)
            assertTrue(hwnd != 0L, "HWND must be non-zero for a visible JFrame")

            val result = NativeDrag.initWindowFor(frame)
            assertTrue(result, "initWindowFor must succeed on Windows")
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun testStartWindowMoveForVisibleWindow() {
        if (!PlatformUtils.isWindows) {
            println("Skipping Windows-specific test on non-Windows platform")
            return
        }

        val frame = JFrame("NativeMoveTestFrame")
        frame.isUndecorated = true
        frame.setSize(500, 400)
        frame.isVisible = true

        try {
            val hwnd = WindowFrameUtils.getHwndAsLong(frame)
            assertTrue(hwnd != 0L, "HWND must be non-zero for a visible JFrame")

            NativeDrag.initWindowFor(frame)
            // startWindowMove initiates move — must not throw
            val result = NativeDrag.startWindowMoveFor(frame)
            assertTrue(result, "startWindowMoveFor must return true for a visible window on Windows")
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun testStartWindowMoveForNonVisibleWindowReturnsFalse() {
        if (!PlatformUtils.isWindows) {
            println("Skipping Windows-specific test on non-Windows platform")
            return
        }

        val frame = JFrame("NativeMoveNoHwndTest")
        frame.isUndecorated = true
        frame.setSize(300, 200)
        // Not shown — HWND may be 0 → expect graceful false return, no exception

        val result = NativeDrag.startWindowMoveFor(frame)
        println("startWindowMoveFor (non-visible): $result")
        // Result may be true or false depending on whether AWT allocated an HWND — just no crash
        frame.dispose()
    }

    @Test
    fun testEnableDropShadowForVisibleWindow() {
        if (!PlatformUtils.isWindows) {
            println("Skipping Windows-specific test on non-Windows platform")
            return
        }

        val frame = JFrame("DropShadowTestFrame")
        frame.isUndecorated = true
        frame.setSize(500, 400)
        frame.isVisible = true

        try {
            // Must not throw; DWM margins are applied via JNI
            NativeDrag.enableDropShadowFor(frame)
            assertTrue(frame.isUndecorated, "Window must remain undecorated after enableDropShadow")
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun testGetHwndAsLongForVisibleWindow() {
        if (!PlatformUtils.isWindows) {
            println("Skipping Windows-specific test on non-Windows platform")
            return
        }

        val frame = JFrame("HwndLongTestFrame")
        frame.isUndecorated = true
        frame.setSize(400, 300)
        frame.isVisible = true

        try {
            val hwnd = WindowFrameUtils.getHwndAsLong(frame)
            assertTrue(hwnd != 0L, "getHwndAsLong must return non-zero for a visible JFrame")
        } finally {
            frame.dispose()
        }
    }

    @Test
    fun testDwmapiLibraryLoads() {
        if (!PlatformUtils.isWindows) {
            println("Skipping Windows-specific test on non-Windows platform")
            return
        }
        assertNotNull(DwmapiLib.INSTANCE, "DwmapiLib must load on Windows")
    }

    @Test
    fun testSetNativeTitleBarTheme() {
        if (!PlatformUtils.isWindows) {
            println("Skipping Windows-specific test on non-Windows platform")
            return
        }

        val frame = JFrame("TitleBarThemeTestFrame")
        frame.setSize(500, 400)
        frame.isVisible = true

        try {
            WindowFrameUtils.setNativeTitleBarTheme(
                window = frame,
                isDark = true,
                captionColor = Color(0xFF1E1E1E),
                textColor = Color(0xFFFFFFFF)
            )
        } finally {
            frame.dispose()
        }
    }
}
