package org.wip.plugintoolkit.ui.titlebar

import co.touchlab.kermit.Logger
import org.wip.plugintoolkit.core.utils.PlatformUtils
import java.awt.Window
import java.io.File

/**
 * JNI bridge to the bundled toolkit-win-drag.dll native library.
 *
 * Provides native Windows window dragging (Aero Snap) by calling
 * PostMessage(WM_NCLBUTTONDOWN, HTCAPTION) directly in Win32, which triggers
 * the OS modal move loop (TrackMoveSize) without requiring WS_CAPTION style or
 * any WndProc subclassing.
 *
 * The DLL is bundled as a classpath resource at /windows/toolkit-win-drag.dll
 * and extracted to a stable temp directory on first use.
 *
 * On non-Windows platforms all methods are no-ops that return false/Unit.
 */
object NativeDrag {

    private const val DLL_RESOURCE_PATH = "/windows/toolkit-win-drag.dll"
    private const val DLL_FILE_NAME = "toolkit-win-drag.dll"

    @Volatile private var loadState: LoadState = LoadState.UNLOADED

    private sealed interface LoadState {
        data object UNLOADED : LoadState
        data object LOADED : LoadState
        data object FAILED : LoadState
    }

    // ── JNI external declarations ──────────────────────────────────────────────

    /**
     * Initializes the window for custom title bar rendering with native Aero Snap support:
     * enables necessary Win32 styles, subclasses WndProc for WM_NCCALCSIZE (removing native title bar),
     * enables WM_NCHITTEST title bar hit-testing, and activates DWM drop shadow + rounded corners.
     */
    @JvmStatic external fun initWindow(hwnd: Long, titleBarHeightDp: Int, rightControlsWidthDp: Int): Boolean

    /**
     * Initiates the native OS interactive window move loop (Aero Snap).
     * Triggers SC_MOVE + 2 in DefWindowProc to enter TrackMoveSize.
     *
     * @param hwnd  Raw Win32 HWND as a Long (from [WindowFrameUtils.getHwndAsLong]).
     * @return true if the move was initiated, false on error.
     */
    @JvmStatic external fun startWindowMove(hwnd: Long): Boolean

    /**
     * Enables the DWM drop shadow for an undecorated window (1px margin trick).
     * @param hwnd  Raw Win32 HWND as a Long.
     */
    @JvmStatic external fun enableDropShadow(hwnd: Long)

    /**
     * Enables Windows 11 rounded corners (DWMWCP_ROUND). No-op on Windows 10.
     * @param hwnd  Raw Win32 HWND as a Long.
     */
    @JvmStatic external fun enableRoundedCorners(hwnd: Long)

    // ── High-level safe wrappers ───────────────────────────────────────────────

    /**
     * Initializes the AWT window with custom chrome styles, WndProc subclassing,
     * title bar hit testing, drop shadow, and rounded corners.
     */
    fun initWindowFor(
        window: Window,
        titleBarHeightDp: Int = 38,
        rightControlsWidthDp: Int = 138
    ): Boolean {
        if (!PlatformUtils.isWindows) return false
        if (!ensureLoaded()) return false
        val hwnd = WindowFrameUtils.getHwndAsLong(window)
        if (hwnd == 0L) return false
        return try {
            initWindow(hwnd, titleBarHeightDp, rightControlsWidthDp)
        } catch (e: Throwable) {
            Logger.w(e) { "NativeDrag: initWindow failed" }
            false
        }
    }

    /**
     * Initiates the native OS window move loop for the given AWT Window.
     * Extracts and loads the DLL on first call. Falls back gracefully on failure.
     *
     * @return true if the move was initiated, false if unsupported or failed.
     */
    fun startWindowMoveFor(window: Window): Boolean {
        if (!PlatformUtils.isWindows) return false
        if (!ensureLoaded()) return false
        val hwnd = WindowFrameUtils.getHwndAsLong(window)
        if (hwnd == 0L) return false
        return try {
            startWindowMove(hwnd)
        } catch (e: Throwable) {
            Logger.w(e) { "NativeDrag: startWindowMove failed" }
            false
        }
    }

    /**
     * Enables DWM drop shadow for the given AWT Window.
     * Extracts and loads the DLL on first call.
     */
    fun enableDropShadowFor(window: Window) {
        if (!PlatformUtils.isWindows) return
        if (!ensureLoaded()) return
        val hwnd = WindowFrameUtils.getHwndAsLong(window)
        if (hwnd == 0L) return
        try {
            enableDropShadow(hwnd)
            enableRoundedCorners(hwnd)
        } catch (e: Throwable) {
            Logger.w(e) { "NativeDrag: enableDropShadow failed" }
        }
    }

    // ── DLL loading ───────────────────────────────────────────────────────────

    /**
     * Ensures the native DLL is loaded. Thread-safe and idempotent.
     * @return true if the DLL is loaded and usable.
     */
    fun ensureLoaded(): Boolean {
        return when (loadState) {
            LoadState.LOADED -> true
            LoadState.FAILED -> false
            LoadState.UNLOADED -> synchronized(this) {
                if (loadState != LoadState.UNLOADED) {
                    loadState == LoadState.LOADED
                } else {
                    val success = extractAndLoad()
                    loadState = if (success) LoadState.LOADED else LoadState.FAILED
                    success
                }
            }
        }
    }

    private fun extractAndLoad(): Boolean {
        return try {
            val stream = NativeDrag::class.java.getResourceAsStream(DLL_RESOURCE_PATH)
                ?: run {
                    println("NativeDrag: Resource not found on classpath: $DLL_RESOURCE_PATH")
                    Logger.w { "NativeDrag: Resource not found on classpath: $DLL_RESOURCE_PATH" }
                    return false
                }

            val tempDir = File(System.getProperty("java.io.tmpdir"), "plugintoolkit-native")
            tempDir.mkdirs()
            val dllFile = File(tempDir, DLL_FILE_NAME)

            val fileToLoad = try {
                stream.use { input ->
                    dllFile.outputStream().use { output -> input.copyTo(output) }
                }
                dllFile
            } catch (ioe: Throwable) {
                val altFile = File(tempDir, "toolkit-win-drag-${System.currentTimeMillis()}.dll")
                NativeDrag::class.java.getResourceAsStream(DLL_RESOURCE_PATH)?.use { input ->
                    altFile.outputStream().use { output -> input.copyTo(output) }
                }
                altFile
            }

            System.load(fileToLoad.absolutePath)
            Logger.d { "NativeDrag: Loaded $DLL_FILE_NAME from $fileToLoad" }
            true
        } catch (e: Throwable) {
            println("NativeDrag: Failed to extract/load $DLL_FILE_NAME: ${e.message}")
            e.printStackTrace()
            Logger.w(e) { "NativeDrag: Failed to extract/load $DLL_FILE_NAME" }
            false
        }
    }
}
