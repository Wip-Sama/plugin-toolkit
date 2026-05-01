import os
import sys
import threading
import multiprocessing
import subprocess
import ctypes
import ctypes.wintypes
import threading as _th
from upscaler_core import run_processing

_here = os.path.dirname(os.path.abspath(__file__))
if _here not in sys.path:
    sys.path.insert(0, _here)

try:
    import dearpygui.dearpygui as dpg
except ModuleNotFoundError:
    subprocess.check_call([sys.executable, "-m", "pip", "install", "dearpygui"])
    import dearpygui.dearpygui as dpg


def run_gui():
    dpg.create_context()

    # Font: Segoe UI da sistema Windows
    with dpg.font_registry():
        _font_path = r"C:\Windows\Fonts\segoeui.ttf"
        if os.path.exists(_font_path):
            with dpg.font(_font_path, 16, tag="font_main"):
                dpg.add_font_range_hint(dpg.mvFontRangeHint_Default)
            dpg.bind_font("font_main")

    def _pick_file():
        ps = (
            "Add-Type -AssemblyName System.Windows.Forms; "
            "$d = New-Object System.Windows.Forms.OpenFileDialog; "
            '$d.Filter = "Immagini|*.png;*.jpg;*.jpeg;*.webp;*.bmp"; '
            '$d.Title = "Seleziona immagine"; '
            'if ($d.ShowDialog() -eq "OK") { $d.FileName }'
        )
        result = subprocess.run(
            ["powershell", "-NoProfile", "-Command", ps], capture_output=True, text=True
        )
        path = result.stdout.strip()
        if path:
            dpg.set_value("path_input", path)

    def _pick_folder():
        ps = (
            "Add-Type -AssemblyName System.Windows.Forms; "
            "$d = New-Object System.Windows.Forms.FolderBrowserDialog; "
            '$d.Description = "Seleziona cartella"; '
            'if ($d.ShowDialog() -eq "OK") { $d.SelectedPath }'
        )
        result = subprocess.run(
            ["powershell", "-NoProfile", "-Command", ps], capture_output=True, text=True
        )
        path = result.stdout.strip()
        if path:
            dpg.set_value("path_input", path)

    def _on_done(success):
        dpg.configure_item("start_btn", enabled=True)
        dpg.set_item_label("start_btn", "  Avvia Upscale")
        if success:
            dpg.set_value(
                "status_text", "Completato! Controlla la console per i dettagli."
            )
            dpg.bind_item_theme("status_text", "theme_ok")
        else:
            dpg.set_value("status_text", "Errore durante l'elaborazione.")
            dpg.bind_item_theme("status_text", "theme_err")

    def _start():
        path = dpg.get_value("path_input").strip()
        if not path or not os.path.exists(path):
            dpg.set_value("status_text", "Seleziona un percorso valido!")
            dpg.bind_item_theme("status_text", "theme_err")
            return

        w_val = dpg.get_value("width_combo").strip().lower()
        SCALE_OPTIONS = ["1x", "x1", "2x", "x2", "4x", "x4", "8x", "x8"]
        target_width = (
            w_val
            if w_val in SCALE_OPTIONS
            else (int(w_val) if w_val.isdigit() else 1000)
        )
        grain = dpg.get_value("grain_input")
        fmt = dpg.get_value("format_combo").lower()

        dpg.configure_item("start_btn", enabled=False)
        dpg.set_item_label("start_btn", "  Elaborazione in corso...")
        dpg.set_value(
            "status_text", "In corso... Controlla la console per l'avanzamento."
        )
        dpg.bind_item_theme("status_text", "theme_neutral")

        threading.Thread(
            target=run_processing,
            args=(path, target_width, grain, fmt, _on_done),
            daemon=True,
        ).start()

    with dpg.theme(tag="theme_ok"):
        with dpg.theme_component(dpg.mvText):
            dpg.add_theme_color(dpg.mvThemeCol_Text, (80, 220, 120))

    with dpg.theme(tag="theme_err"):
        with dpg.theme_component(dpg.mvText):
            dpg.add_theme_color(dpg.mvThemeCol_Text, (240, 80, 80))

    with dpg.theme(tag="theme_neutral"):
        with dpg.theme_component(dpg.mvText):
            dpg.add_theme_color(dpg.mvThemeCol_Text, (180, 180, 180))

    with dpg.theme(tag="global_theme"):
        with dpg.theme_component(dpg.mvAll):
            dpg.add_theme_style(dpg.mvStyleVar_WindowRounding, 8)
            dpg.add_theme_style(dpg.mvStyleVar_FrameRounding, 5)
            dpg.add_theme_style(dpg.mvStyleVar_ItemSpacing, 8, 6)
            dpg.add_theme_style(dpg.mvStyleVar_FramePadding, 6, 4)
            dpg.add_theme_color(dpg.mvThemeCol_WindowBg, (22, 22, 28))
            dpg.add_theme_color(dpg.mvThemeCol_FrameBg, (38, 38, 50))
            dpg.add_theme_color(dpg.mvThemeCol_Button, (60, 100, 200))
            dpg.add_theme_color(dpg.mvThemeCol_ButtonHovered, (80, 120, 230))
            dpg.add_theme_color(dpg.mvThemeCol_ButtonActive, (50, 80, 180))
            dpg.add_theme_color(dpg.mvThemeCol_Header, (60, 100, 200, 180))
            dpg.add_theme_color(dpg.mvThemeCol_CheckMark, (100, 180, 255))

    dpg.bind_theme("global_theme")

    with dpg.window(
        tag="main_window",
        no_title_bar=True,
        no_move=True,
        no_collapse=True,
    ):
        dpg.add_text("VapourSynth AI Upscaler", color=(120, 170, 255))
        dpg.add_separator()
        dpg.add_spacer(height=6)

        dpg.add_text("Percorso (File o Cartella)")
        with dpg.group(horizontal=True):
            dpg.add_input_text(
                tag="path_input",
                hint="Trascina o seleziona...",
                width=-140,
            )
            dpg.add_button(label="File", callback=_pick_file, width=60)
            dpg.add_button(label="Cartella", callback=_pick_folder, width=75)

        dpg.add_spacer(height=10)

        with dpg.group(horizontal=True):
            with dpg.group(width=140):
                dpg.add_text("Upscale / Larghezza")
                dpg.add_combo(
                    ["1x", "2x", "4x", "8x", "800", "1000", "1280", "1920"],
                    default_value="1000",
                    tag="width_combo",
                    width=130,
                )

            dpg.add_spacer(width=10)

            with dpg.group(width=120):
                dpg.add_text("Grain (0-20)")
                dpg.add_input_int(
                    tag="grain_input",
                    default_value=1,
                    min_value=0,
                    max_value=20,
                    width=110,
                )

            dpg.add_spacer(width=10)

            with dpg.group(width=110):
                dpg.add_text("Formato Output")
                dpg.add_combo(
                    ["webp", "png"],
                    default_value="webp",
                    tag="format_combo",
                    width=100,
                )

        dpg.add_spacer(height=14)

        dpg.add_button(
            label="  Avvia Upscale",
            tag="start_btn",
            callback=_start,
            width=-1,
            height=36,
        )

        dpg.add_spacer(height=8)
        dpg.add_text("", tag="status_text")
        dpg.add_spacer(height=4)
        dpg.add_text(
            "Nota: il progresso dettagliato compare nella console.",
            color=(100, 100, 110),
        )

    dpg.create_viewport(
        title="BetterIMG",
        width=700,
        height=600,
        resizable=True,
        min_width=700,
        min_height=600,
    )
    dpg.setup_dearpygui()

    def _setup_win_drop():
        hwnd = ctypes.windll.user32.FindWindowW(None, "BetterIMG")
        if not hwnd:
            return

        # Imposta argtypes/restype 64-bit per le API Win32 usate
        user32 = ctypes.windll.user32
        shell32 = ctypes.windll.shell32

        user32.GetWindowLongPtrW.restype = ctypes.c_longlong
        user32.SetWindowLongPtrW.argtypes = [
            ctypes.c_void_p,
            ctypes.c_int,
            ctypes.c_longlong,
        ]
        user32.SetWindowLongPtrW.restype = ctypes.c_longlong
        user32.CallWindowProcW.argtypes = [
            ctypes.c_longlong,
            ctypes.c_void_p,
            ctypes.c_uint,
            ctypes.c_longlong,
            ctypes.c_longlong,
        ]
        user32.CallWindowProcW.restype = ctypes.c_longlong

        shell32.DragAcceptFiles.argtypes = [ctypes.c_void_p, ctypes.c_bool]
        shell32.DragQueryFileW.argtypes = [
            ctypes.c_void_p,
            ctypes.c_uint,
            ctypes.c_wchar_p,
            ctypes.c_uint,
        ]
        shell32.DragQueryFileW.restype = ctypes.c_uint
        shell32.DragFinish.argtypes = [ctypes.c_void_p]

        shell32.DragAcceptFiles(hwnd, True)

        WM_DROPFILES = 0x0233
        GWL_WNDPROC = -4
        WNDPROCTYPE = ctypes.WINFUNCTYPE(
            ctypes.c_longlong,  # return
            ctypes.c_void_p,  # HWND
            ctypes.c_uint,  # msg
            ctypes.c_longlong,  # WPARAM
            ctypes.c_longlong,  # LPARAM
        )

        original_proc = user32.GetWindowLongPtrW(hwnd, GWL_WNDPROC)

        def wnd_proc(hwnd, msg, wparam, lparam):
            if msg == WM_DROPFILES:
                hdrop = ctypes.c_void_p(wparam)
                count = shell32.DragQueryFileW(hdrop, 0xFFFFFFFF, None, 0)
                if count > 0:
                    buf = ctypes.create_unicode_buffer(260)
                    shell32.DragQueryFileW(hdrop, 0, buf, 260)
                    dpg.set_value("path_input", buf.value)
                shell32.DragFinish(hdrop)
                return 0
            return user32.CallWindowProcW(original_proc, hwnd, msg, wparam, lparam)

        new_proc = WNDPROCTYPE(wnd_proc)
        run_gui._drop_proc = new_proc
        user32.SetWindowLongPtrW(
            hwnd, GWL_WNDPROC, ctypes.cast(new_proc, ctypes.c_void_p).value
        )

    _th.Timer(1.0, _setup_win_drop).start()

    dpg.show_viewport()
    dpg.set_primary_window("main_window", True)
    dpg.start_dearpygui()
    dpg.destroy_context()


if __name__ == "__main__":
    multiprocessing.freeze_support()
    run_gui()
