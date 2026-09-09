/**
 * toolkit_drag.cpp
 *
 * Native Windows window-dragging, Aero Snap, and DWM integration for
 * Compose Desktop (AWT/JBR) custom title bars.
 *
 * Key architecture:
 *   1. Subclasses GWLP_WNDPROC on the top-level JFrame AND all child windows
 *      (specifically the SkiaLayer / SunAwtCanvas child window).
 *   2. Child window (SunAwtCanvas) intercept: When WM_NCHITTEST occurs within
 *      the custom title bar region (or outer 8px resize border), the child window
 *      returns HTTRANSPARENT (-1). This causes Windows to pass the hit test
 *      directly to the underlying parent JFrame!
 *   3. Parent JFrame intercept: Returns HTCAPTION for the custom title bar.
 *      When the user clicks and drags, Windows directly initiates TrackMoveSize
 *      (the OS modal move loop) on the JFrame. Full native Aero Snap (top-edge
 *      maximize, side-edge split, quarter-screen snap, and drag-to-restore) is
 *      handled 100% natively by Windows!
 *   4. WM_NCCALCSIZE: Calls CallWindowProc to preserve left, right, and bottom
 *      native borders (ensuring the 1px frame/border stays visible when floating
 *      and when snapped!), while restoring top = originalTop to remove the native caption.
 *   5. Controls area: For the right-side control buttons (Minimize, Maximize, Close),
 *      the child window returns HTCLIENT so Compose handles button interactions normally.
 */

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <dwmapi.h>
#include <jni.h>
#include <mutex>
#include <unordered_map>

#pragma comment(lib, "user32.lib")
#pragma comment(lib, "dwmapi.lib")

#include <vector>

#ifndef GET_X_LPARAM
#define GET_X_LPARAM(lp) ((int)(short)LOWORD(lp))
#endif
#ifndef GET_Y_LPARAM
#define GET_Y_LPARAM(lp) ((int)(short)HIWORD(lp))
#endif

// ── Windows 11 DWM constants ──────────────────────────────────────────────────
#ifndef DWMWA_WINDOW_CORNER_PREFERENCE
#define DWMWA_WINDOW_CORNER_PREFERENCE 33
#endif

#ifndef DWMWCP_ROUND
typedef enum {
    DWMWCP_DEFAULT    = 0,
    DWMWCP_DONOTROUND = 1,
    DWMWCP_ROUND      = 2,
    DWMWCP_ROUNDSMALL = 3
} DWM_WINDOW_CORNER_PREFERENCE;
#endif

static inline HWND to_hwnd(jlong hwnd) {
    return reinterpret_cast<HWND>(static_cast<LONG_PTR>(hwnd));
}

// Title bar dimensions and rectangles in density-independent pixels (DIPs)
static int g_titleBarHeightDp = 38;
static int g_rightControlsWidthDp = 138;

struct TitleBarRect {
    int x;
    int y;
    int width;
    int height;
};

struct WindowConfig {
    int titleBarHeightDp = 38;
    int rightControlsWidthDp = 138;
    int leftOffsetDp = 0;
    std::vector<TitleBarRect> nonDraggableRects;
    std::vector<TitleBarRect> draggableRects;
};

static std::mutex g_configMutex;
static std::unordered_map<HWND, WindowConfig> g_windowConfigs;

static UINT get_window_dpi(HWND hWnd) {
    typedef UINT (WINAPI *GetDpiForWindowFn)(HWND);
    static GetDpiForWindowFn s_pfn = reinterpret_cast<GetDpiForWindowFn>(
        GetProcAddress(GetModuleHandleA("user32.dll"), "GetDpiForWindow")
    );
    if (s_pfn) {
        UINT dpi = s_pfn(hWnd);
        if (dpi > 0) return dpi;
    }
    HDC hdc = GetDC(hWnd);
    if (hdc) {
        int dpi = GetDeviceCaps(hdc, LOGPIXELSY);
        ReleaseDC(hWnd, hdc);
        if (dpi > 0) return static_cast<UINT>(dpi);
    }
    return 96;
}

// Map storing original WNDPROC per HWND for clean delegation and uninstall
static std::mutex g_mapMutex;
static std::unordered_map<HWND, WNDPROC> g_wndProcMap;

// Forward declarations
static LRESULT CALLBACK SubclassWndProc(HWND hWnd, UINT uMsg, WPARAM wParam, LPARAM lParam);
static LRESULT CALLBACK ChildSubclassWndProc(HWND hWndChild, UINT uMsg, WPARAM wParam, LPARAM lParam);
static void hookWindow(HWND h, bool isChild);

static bool is_in_controls_area(HWND hWnd, POINT ptClient) {
    int titleHeightDp = g_titleBarHeightDp;
    int rightControlsDp = g_rightControlsWidthDp;
    {
        std::lock_guard<std::mutex> lock(g_configMutex);
        auto it = g_windowConfigs.find(hWnd);
        if (it != g_windowConfigs.end()) {
            titleHeightDp = it->second.titleBarHeightDp;
            rightControlsDp = it->second.rightControlsWidthDp;
        }
    }

    RECT rc;
    GetClientRect(hWnd, &rc);
    UINT dpi = get_window_dpi(hWnd);
    int titleHeightPx = (titleHeightDp * dpi) / 96;
    int rightControlsWidthPx = (rightControlsDp * dpi) / 96;

    return (ptClient.y >= 0 && ptClient.y < titleHeightPx &&
            ptClient.x >= rc.right - rightControlsWidthPx);
}

static bool is_in_draggable_titlebar(HWND hWnd, POINT ptClient) {
    WindowConfig cfg;
    {
        std::lock_guard<std::mutex> lock(g_configMutex);
        auto it = g_windowConfigs.find(hWnd);
        if (it != g_windowConfigs.end()) {
            cfg = it->second;
        } else {
            cfg.titleBarHeightDp = g_titleBarHeightDp;
            cfg.rightControlsWidthDp = g_rightControlsWidthDp;
            cfg.leftOffsetDp = 0;
        }
    }

    UINT dpi = get_window_dpi(hWnd);
    int titleHeightPx = (cfg.titleBarHeightDp * dpi) / 96;
    if (ptClient.y < 0 || ptClient.y >= titleHeightPx) {
        return false;
    }

    RECT rc;
    GetClientRect(hWnd, &rc);
    int rightControlsWidthPx = (cfg.rightControlsWidthDp * dpi) / 96;
    if (ptClient.x >= rc.right - rightControlsWidthPx) {
        return false; // In control buttons area -> HTCLIENT
    }

    // 1. Check non-draggable exclusion rects (interactive buttons, search bar, etc.)
    for (const auto& r : cfg.nonDraggableRects) {
        int rx = (r.x * dpi) / 96;
        int ry = (r.y * dpi) / 96;
        int rw = (r.width * dpi) / 96;
        int rh = (r.height * dpi) / 96;
        if (ptClient.x >= rx && ptClient.x < rx + rw &&
            ptClient.y >= ry && ptClient.y < ry + rh) {
            return false; // Point is inside an interactive component -> HTCLIENT
        }
    }

    // 2. If explicit draggable rects are set, point must be within one of them
    if (!cfg.draggableRects.empty()) {
        for (const auto& r : cfg.draggableRects) {
            int rx = (r.x * dpi) / 96;
            int ry = (r.y * dpi) / 96;
            int rw = (r.width * dpi) / 96;
            int rh = (r.height * dpi) / 96;
            if (ptClient.x >= rx && ptClient.x < rx + rw &&
                ptClient.y >= ry && ptClient.y < ry + rh) {
                return true;
            }
        }
        return false;
    }

    // 3. Fallback: draggable area starting from leftOffsetDp to right control buttons
    int leftOffsetPx = (cfg.leftOffsetDp * dpi) / 96;
    if (ptClient.x >= leftOffsetPx && ptClient.x < rc.right - rightControlsWidthPx) {
        return true;
    }

    return false;
}

static void hookAllChildren(HWND hParent) {
    EnumChildWindows(hParent, [](HWND hChild, LPARAM lp) -> BOOL {
        hookWindow(hChild, true);
        return TRUE;
    }, 0);
}

static void hookWindow(HWND h, bool isChild) {
    if (!h) return;
    std::lock_guard<std::mutex> lock(g_mapMutex);
    if (g_wndProcMap.find(h) != g_wndProcMap.end()) {
        return; // already hooked
    }

    WNDPROC proc = isChild ? ChildSubclassWndProc : SubclassWndProc;
    WNDPROC oldProc = reinterpret_cast<WNDPROC>(
        SetWindowLongPtr(h, GWLP_WNDPROC, reinterpret_cast<LONG_PTR>(proc))
    );

    if (oldProc) {
        g_wndProcMap[h] = oldProc;
    }
}

/**
 * Child window procedure subclass (for SunAwtCanvas / SkiaLayer).
 * Intercepts WM_NCHITTEST:
 *   - Control buttons area (Minimize, Maximize, Close): strictly kept in client area (HTCLIENT)
 *     so Compose handles clicks, hover animations, and tooltips directly with zero interference.
 *   - Outer 8px resize border: returns HTTRANSPARENT so parent JFrame receives resize handles.
 *   - Title bar draggable region: returns HTTRANSPARENT so parent JFrame receives HTCAPTION
 *     for 100% native Windows window dragging (Aero Snap, top-edge max, half-screen split).
 */
static LRESULT CALLBACK ChildSubclassWndProc(HWND hWndChild, UINT uMsg, WPARAM wParam, LPARAM lParam) {
    WNDPROC oldProc = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_mapMutex);
        auto it = g_wndProcMap.find(hWndChild);
        if (it != g_wndProcMap.end()) {
            oldProc = it->second;
        }
    }

    if (!oldProc) {
        return DefWindowProc(hWndChild, uMsg, wParam, lParam);
    }

    switch (uMsg) {
        case WM_NCHITTEST: {
            HWND hParent = GetAncestor(hWndChild, GA_ROOT);
            if (hParent) {
                POINT ptScreen = { GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam) };
                POINT ptParent = ptScreen;
                ScreenToClient(hParent, &ptParent);

                RECT rcParent;
                GetClientRect(hParent, &rcParent);

                // 1. Controls area (Minimize, Maximize, Close):
                // Keep strictly in client area so Compose handles clicks and hovers directly!
                if (is_in_controls_area(hParent, ptParent)) {
                    return CallWindowProc(oldProc, hWndChild, uMsg, wParam, lParam);
                }

                // 2. If in 8px resize border of the parent window (when not maximized),
                // pass through to parent JFrame for native resize handles
                if (!IsZoomed(hParent)) {
                    const int border = 8;
                    if (ptParent.y < border || ptParent.y >= rcParent.bottom - border ||
                        ptParent.x < border || ptParent.x >= rcParent.right - border) {
                        return HTTRANSPARENT;
                    }
                }

                // 3. If in custom title bar draggable area:
                if (is_in_draggable_titlebar(hParent, ptParent)) {
                    return HTTRANSPARENT;
                }
            }
            break;
        }

        case WM_DESTROY: {
            SetWindowLongPtr(hWndChild, GWLP_WNDPROC, reinterpret_cast<LONG_PTR>(oldProc));
            {
                std::lock_guard<std::mutex> lock(g_mapMutex);
                g_wndProcMap.erase(hWndChild);
            }
            return CallWindowProc(oldProc, hWndChild, uMsg, wParam, lParam);
        }

        default:
            break;
    }

    return CallWindowProc(oldProc, hWndChild, uMsg, wParam, lParam);
}

/**
 * Top-level JFrame window procedure subclass.
 * Handles WM_NCCALCSIZE (removes native caption while preserving side/bottom borders),
 * WM_NCHITTEST (returns HTCAPTION for title bar, resize handles for outer borders),
 * and routes non-client drag events to DefWindowProc.
 */
static LRESULT CALLBACK SubclassWndProc(HWND hWnd, UINT uMsg, WPARAM wParam, LPARAM lParam) {
    WNDPROC oldProc = nullptr;
    {
        std::lock_guard<std::mutex> lock(g_mapMutex);
        auto it = g_wndProcMap.find(hWnd);
        if (it != g_wndProcMap.end()) {
            oldProc = it->second;
        }
    }

    if (!oldProc) {
        return DefWindowProc(hWnd, uMsg, wParam, lParam);
    }

    switch (uMsg) {
        case WM_NCCALCSIZE: {
            if (wParam == TRUE) {
                NCCALCSIZE_PARAMS* params = reinterpret_cast<NCCALCSIZE_PARAMS*>(lParam);

                // Preserve original top
                int originalTop = params->rgrc[0].top;

                // Let default proc calculate left, right, and bottom borders
                // so the 1px native frame/border remains visible when floating and when snapped!
                LRESULT ret = CallWindowProc(oldProc, hWnd, uMsg, wParam, lParam);

                // Remove top caption height
                params->rgrc[0].top = originalTop;

                // When maximized, Windows expands resize borders beyond monitor bounds.
                // Compensate top border so the custom title bar is not clipped.
                if (IsZoomed(hWnd)) {
                    int borderY = GetSystemMetrics(SM_CYSIZEFRAME) + GetSystemMetrics(SM_CXPADDEDBORDER);
                    params->rgrc[0].top += borderY;
                }

                return ret;
            }
            break;
        }

        case WM_NCHITTEST: {
            // Ensure any newly added child windows (like SunAwtCanvas) are hooked
            hookAllChildren(hWnd);

            POINT pt = { GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam) };
            ScreenToClient(hWnd, &pt);

            RECT rc;
            GetClientRect(hWnd, &rc);

            // 1. Controls area: keep as HTCLIENT so Compose processes clicks directly
            if (is_in_controls_area(hWnd, pt)) {
                return HTCLIENT;
            }

            // 2. On non-maximized windows, provide 8-pixel native resize handles around borders
            if (!IsZoomed(hWnd)) {
                const int border = 8;
                if (pt.y < border && pt.x < border) return HTTOPLEFT;
                if (pt.y < border && pt.x >= rc.right - border) return HTTOPRIGHT;
                if (pt.y >= rc.bottom - border && pt.x < border) return HTBOTTOMLEFT;
                if (pt.y >= rc.bottom - border && pt.x >= rc.right - border) return HTBOTTOMRIGHT;
                if (pt.y < border) return HTTOP;
                if (pt.y >= rc.bottom - border) return HTBOTTOM;
                if (pt.x < border) return HTLEFT;
                if (pt.x >= rc.right - border) return HTRIGHT;
            }

            // 3. Custom title bar draggable area
            if (is_in_draggable_titlebar(hWnd, pt)) {
                return HTCAPTION;
            }

            return HTCLIENT;
        }

        case WM_NCLBUTTONDOWN: {
            if (wParam == HTCAPTION) {
                return DefWindowProc(hWnd, uMsg, wParam, lParam);
            }
            break;
        }

        case WM_NCLBUTTONDBLCLK: {
            if (wParam == HTCAPTION) {
                return DefWindowProc(hWnd, uMsg, wParam, lParam);
            }
            break;
        }

        case WM_NCRBUTTONUP: {
            if (wParam == HTCAPTION) {
                return DefWindowProc(hWnd, uMsg, wParam, lParam);
            }
            break;
        }

        case WM_SYSCOMMAND: {
            return DefWindowProc(hWnd, uMsg, wParam, lParam);
        }

        case WM_PARENTNOTIFY: {
            if (LOWORD(wParam) == WM_CREATE) {
                HWND hChild = reinterpret_cast<HWND>(lParam);
                hookWindow(hChild, true);
            }
            break;
        }

        case WM_ACTIVATE: {
            hookAllChildren(hWnd);
            break;
        }

        case WM_DESTROY: {
            SetWindowLongPtr(hWnd, GWLP_WNDPROC, reinterpret_cast<LONG_PTR>(oldProc));
            {
                std::lock_guard<std::mutex> lock(g_mapMutex);
                g_wndProcMap.erase(hWnd);
            }
            {
                std::lock_guard<std::mutex> lock(g_configMutex);
                g_windowConfigs.erase(hWnd);
            }
            return CallWindowProc(oldProc, hWnd, uMsg, wParam, lParam);
        }

        default:
            break;
    }

    return CallWindowProc(oldProc, hWnd, uMsg, wParam, lParam);
}

// ── JNI exports ───────────────────────────────────────────────────────────────
// Package: org.wip.plugintoolkit.ui.titlebar
// Class:   NativeDrag

extern "C" {

/**
 * Initializes the window for custom title bar rendering with full OS Aero Snap support:
 *   - Enables WS_THICKFRAME | WS_CAPTION | WS_SYSMENU | WS_MAXIMIZEBOX | WS_MINIMIZEBOX.
 *   - Subclasses top-level JFrame and all child windows (SunAwtCanvas) for HTTRANSPARENT fallthrough.
 *   - Preserves left/right/bottom window borders on WM_NCCALCSIZE (for floating and snapped states).
 *   - Applies DWM drop shadow and Windows 11 rounded corners.
 *
 * @param hwnd                 Raw Win32 HWND as jlong.
 * @param titleBarHeightDp     Height of the custom title bar in DP.
 * @param rightControlsWidthDp Total width of the right window controls (min/max/close) in DP.
 * @return JNI_TRUE on success, JNI_FALSE on failure.
 */
JNIEXPORT jboolean JNICALL
Java_org_wip_plugintoolkit_ui_titlebar_NativeDrag_initWindow(
    JNIEnv* env, jclass cls, jlong hwnd, jint titleBarHeightDp, jint rightControlsWidthDp)
{
    if (hwnd == 0) return JNI_FALSE;
    HWND hWnd = to_hwnd(hwnd);

    if (titleBarHeightDp > 0) {
        g_titleBarHeightDp = titleBarHeightDp;
    }
    if (rightControlsWidthDp > 0) {
        g_rightControlsWidthDp = rightControlsWidthDp;
    }

    {
        std::lock_guard<std::mutex> lock(g_configMutex);
        auto& cfg = g_windowConfigs[hWnd];
        if (titleBarHeightDp > 0) cfg.titleBarHeightDp = titleBarHeightDp;
        if (rightControlsWidthDp > 0) cfg.rightControlsWidthDp = rightControlsWidthDp;
    }

    // 1. Enable styles required by DefWindowProc for Aero Snap and TrackMoveSize
    LONG style = GetWindowLong(hWnd, GWL_STYLE);
    style |= (WS_THICKFRAME | WS_CAPTION | WS_SYSMENU | WS_MAXIMIZEBOX | WS_MINIMIZEBOX);
    SetWindowLongPtr(hWnd, GWL_STYLE, style);

    // 2. Hook top-level window and all child windows (SunAwtCanvas)
    hookWindow(hWnd, false);
    hookAllChildren(hWnd);

    // 3. Inform Windows that the window frame changed (triggers WM_NCCALCSIZE)
    SetWindowPos(hWnd, NULL, 0, 0, 0, 0,
                 SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED);

    // 4. Enable DWM drop shadow & Windows 11 rounded corners
    MARGINS margins = {1, 1, 1, 1};
    DwmExtendFrameIntoClientArea(hWnd, &margins);

    DWM_WINDOW_CORNER_PREFERENCE pref = DWMWCP_ROUND;
    DwmSetWindowAttribute(hWnd, DWMWA_WINDOW_CORNER_PREFERENCE, &pref, sizeof(pref));

    return JNI_TRUE;
}

/**
 * Sets the left offset of the draggable title bar in DP.
 * Any area to the left of this offset is treated as HTCLIENT.
 */
JNIEXPORT jboolean JNICALL
Java_org_wip_plugintoolkit_ui_titlebar_NativeDrag_setLeftOffset(
    JNIEnv* env, jclass cls, jlong hwnd, jint leftOffsetDp)
{
    if (hwnd == 0) return JNI_FALSE;
    HWND hWnd = to_hwnd(hwnd);
    {
        std::lock_guard<std::mutex> lock(g_configMutex);
        g_windowConfigs[hWnd].leftOffsetDp = leftOffsetDp;
    }
    return JNI_TRUE;
}

/**
 * Sets the non-draggable (interactive) exclusion rectangles in DP.
 * Points inside these rectangles will return HTCLIENT so Compose handles interaction.
 *
 * @param rectsArray Flat array of [x, y, width, height, x, y, width, height, ...] in DP.
 */
JNIEXPORT jboolean JNICALL
Java_org_wip_plugintoolkit_ui_titlebar_NativeDrag_setNonDraggableRects(
    JNIEnv* env, jclass cls, jlong hwnd, jintArray rectsArray)
{
    if (hwnd == 0) return JNI_FALSE;
    HWND hWnd = to_hwnd(hwnd);

    std::vector<TitleBarRect> rects;
    if (rectsArray != nullptr) {
        jsize len = env->GetArrayLength(rectsArray);
        if (len % 4 == 0) {
            jint* elements = env->GetIntArrayElements(rectsArray, nullptr);
            if (elements) {
                rects.reserve(len / 4);
                for (jsize i = 0; i < len; i += 4) {
                    rects.push_back({ elements[i], elements[i + 1], elements[i + 2], elements[i + 3] });
                }
                env->ReleaseIntArrayElements(rectsArray, elements, JNI_ABORT);
            }
        }
    }

    {
        std::lock_guard<std::mutex> lock(g_configMutex);
        g_windowConfigs[hWnd].nonDraggableRects = std::move(rects);
    }
    return JNI_TRUE;
}

/**
 * Sets explicit draggable rectangles in DP.
 * When set (non-empty), ONLY points within these rectangles return HTCAPTION.
 *
 * @param rectsArray Flat array of [x, y, width, height, ...] in DP.
 */
JNIEXPORT jboolean JNICALL
Java_org_wip_plugintoolkit_ui_titlebar_NativeDrag_setDraggableRects(
    JNIEnv* env, jclass cls, jlong hwnd, jintArray rectsArray)
{
    if (hwnd == 0) return JNI_FALSE;
    HWND hWnd = to_hwnd(hwnd);

    std::vector<TitleBarRect> rects;
    if (rectsArray != nullptr) {
        jsize len = env->GetArrayLength(rectsArray);
        if (len % 4 == 0) {
            jint* elements = env->GetIntArrayElements(rectsArray, nullptr);
            if (elements) {
                rects.reserve(len / 4);
                for (jsize i = 0; i < len; i += 4) {
                    rects.push_back({ elements[i], elements[i + 1], elements[i + 2], elements[i + 3] });
                }
                env->ReleaseIntArrayElements(rectsArray, elements, JNI_ABORT);
            }
        }
    }

    {
        std::lock_guard<std::mutex> lock(g_configMutex);
        g_windowConfigs[hWnd].draggableRects = std::move(rects);
    }
    return JNI_TRUE;
}

/**
 * Initiates the native OS interactive window move loop (Aero Snap enabled).
 * Calls ReleaseCapture() followed by DefWindowProc(hWnd, WM_SYSCOMMAND, 0xF012, 0),
 * directly entering Win32's TrackMoveSize modal move loop without AWT interference.
 *
 * @param hwnd  Raw Win32 HWND as jlong.
 * @return JNI_TRUE on success, JNI_FALSE on failure.
 */
JNIEXPORT jboolean JNICALL
Java_org_wip_plugintoolkit_ui_titlebar_NativeDrag_startWindowMove(
    JNIEnv* env, jclass cls, jlong hwnd)
{
    if (hwnd == 0) return JNI_FALSE;
    HWND hWnd = to_hwnd(hwnd);

    // Release mouse capture so Windows OS move loop can capture and track the cursor
    ReleaseCapture();

    // Directly trigger DefWindowProc interactive move
    DefWindowProc(hWnd, WM_SYSCOMMAND, 0xF012, 0);
    return JNI_TRUE;
}

/**
 * Enables DWM drop shadow for an undecorated AWT window.
 * @param hwnd  Raw Win32 HWND as jlong.
 */
JNIEXPORT void JNICALL
Java_org_wip_plugintoolkit_ui_titlebar_NativeDrag_enableDropShadow(
    JNIEnv* env, jclass cls, jlong hwnd)
{
    if (hwnd == 0) return;
    MARGINS margins = {1, 1, 1, 1};
    DwmExtendFrameIntoClientArea(to_hwnd(hwnd), &margins);
}

/**
 * Enables Windows 11 rounded corners (DWMWCP_ROUND) for the window.
 * @param hwnd  Raw Win32 HWND as jlong.
 */
JNIEXPORT void JNICALL
Java_org_wip_plugintoolkit_ui_titlebar_NativeDrag_enableRoundedCorners(
    JNIEnv* env, jclass cls, jlong hwnd)
{
    if (hwnd == 0) return;
    DWM_WINDOW_CORNER_PREFERENCE pref = DWMWCP_ROUND;
    DwmSetWindowAttribute(to_hwnd(hwnd), DWMWA_WINDOW_CORNER_PREFERENCE, &pref, sizeof(pref));
}

} // extern "C"
