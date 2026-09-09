package org.wip.plugintoolkit.ui.titlebar

import co.touchlab.kermit.Logger
import org.wip.plugintoolkit.core.utils.PlatformUtils
import java.awt.Window
import java.io.File
import java.util.concurrent.ConcurrentHashMap

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

    /**
     * Sets the left offset of the draggable title bar in DP.
     * Any area to the left of this offset returns HTCLIENT (allowing normal Compose interactions).
     */
    @JvmStatic external fun setLeftOffset(hwnd: Long, leftOffsetDp: Int): Boolean

    /**
     * Sets non-draggable (interactive) exclusion rectangles in DP.
     * Points within these rectangles return HTCLIENT so Compose handles interaction.
     *
     * @param rects Flat array of [x, y, width, height, ...] in DP.
     */
    @JvmStatic external fun setNonDraggableRects(hwnd: Long, rects: IntArray): Boolean

    /**
     * Sets explicit draggable rectangles in DP.
     * When non-empty, ONLY points within these rectangles return HTCAPTION.
     */
    @JvmStatic external fun setDraggableRects(hwnd: Long, rects: IntArray): Boolean

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

    // ── Dynamic draggable & non-draggable title bar area management ───────────

    data class TitleBarRect(val x: Int, val y: Int, val width: Int, val height: Int)

    private val nonDraggableRectsMap = ConcurrentHashMap<Window, ConcurrentHashMap<Any, TitleBarRect>>()

    /**
     * Dynamically sets the left offset of the draggable title bar in DP.
     * Anything to the left of this offset (e.g. sidebar width) returns HTCLIENT so Compose handles it.
     */
    fun setLeftOffsetFor(window: Window, leftOffsetDp: Int): Boolean {
        if (!PlatformUtils.isWindows) return false
        if (!ensureLoaded()) return false
        val hwnd = WindowFrameUtils.getHwndAsLong(window)
        if (hwnd == 0L) return false
        return try {
            setLeftOffset(hwnd, leftOffsetDp)
        } catch (e: Throwable) {
            Logger.w(e) { "NativeDrag: setLeftOffset failed" }
            false
        }
    }

    /**
     * Sets non-draggable exclusion rectangles in DP for the window.
     */
    fun setNonDraggableRectsFor(window: Window, rects: List<TitleBarRect>): Boolean {
        if (!PlatformUtils.isWindows) return false
        if (!ensureLoaded()) return false
        val hwnd = WindowFrameUtils.getHwndAsLong(window)
        if (hwnd == 0L) return false
        val flatArray = IntArray(rects.size * 4)
        for (i in rects.indices) {
            val r = rects[i]
            val base = i * 4
            flatArray[base] = r.x
            flatArray[base + 1] = r.y
            flatArray[base + 2] = r.width
            flatArray[base + 3] = r.height
        }
        return try {
            setNonDraggableRects(hwnd, flatArray)
        } catch (e: Throwable) {
            Logger.w(e) { "NativeDrag: setNonDraggableRects failed" }
            false
        }
    }

    /**
     * Sets explicit draggable rectangles in DP for the window.
     */
    fun setDraggableRectsFor(window: Window, rects: List<TitleBarRect>): Boolean {
        if (!PlatformUtils.isWindows) return false
        if (!ensureLoaded()) return false
        val hwnd = WindowFrameUtils.getHwndAsLong(window)
        if (hwnd == 0L) return false
        val flatArray = IntArray(rects.size * 4)
        for (i in rects.indices) {
            val r = rects[i]
            val base = i * 4
            flatArray[base] = r.x
            flatArray[base + 1] = r.y
            flatArray[base + 2] = r.width
            flatArray[base + 3] = r.height
        }
        return try {
            setDraggableRects(hwnd, flatArray)
        } catch (e: Throwable) {
            Logger.w(e) { "NativeDrag: setDraggableRects failed" }
            false
        }
    }

    /**
     * Registers a non-draggable (interactive) component bounds in DP.
     */
    fun registerNonDraggableArea(window: Window, key: Any, rect: TitleBarRect) {
        val windowMap = nonDraggableRectsMap.computeIfAbsent(window) { ConcurrentHashMap() }
        windowMap[key] = rect
        syncNonDraggableRects(window)
    }

    /**
     * Unregisters a non-draggable area when a component is disposed.
     */
    fun unregisterNonDraggableArea(window: Window, key: Any) {
        val windowMap = nonDraggableRectsMap[window] ?: return
        if (windowMap.remove(key) != null) {
            syncNonDraggableRects(window)
        }
    }

    private fun syncNonDraggableRects(window: Window) {
        val rects = nonDraggableRectsMap[window]?.values?.toList() ?: emptyList()
        setNonDraggableRectsFor(window, rects)
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
