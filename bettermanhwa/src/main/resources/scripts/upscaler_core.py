import argparse
import atexit
import ctypes
import os
import signal
import sys
import multiprocessing
from pathlib import Path
from time import sleep
from tqdm import tqdm

_active_pool = None
_job_handle = None


def _setup_windows_job_object():
    """
    Crea un Job Object Windows con JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE.
    Quando il processo principale muore (anche via TerminateProcess da Kotlin),
    l'OS chiude tutti i suoi handle → il job si chiude → tutti i processi
    nel job vengono uccisi automaticamente.
    """
    if sys.platform != "win32":
        return None

    kernel32 = ctypes.windll.kernel32

    job = kernel32.CreateJobObjectW(None, None)
    if not job:
        return None

    class _BASIC(ctypes.Structure):
        _fields_ = [
            ("PerProcessUserTimeLimit", ctypes.c_longlong),
            ("PerJobUserTimeLimit", ctypes.c_longlong),
            ("LimitFlags", ctypes.c_ulong),
            ("MinimumWorkingSetSize", ctypes.c_size_t),
            ("MaximumWorkingSetSize", ctypes.c_size_t),
            ("ActiveProcessLimit", ctypes.c_ulong),
            ("Affinity", ctypes.c_size_t),
            ("PriorityClass", ctypes.c_ulong),
            ("SchedulingClass", ctypes.c_ulong),
        ]

    class _IO(ctypes.Structure):
        _fields_ = [
            (f, ctypes.c_ulonglong)
            for f in (
                "ReadOperationCount",
                "WriteOperationCount",
                "OtherOperationCount",
                "ReadTransferCount",
                "WriteTransferCount",
                "OtherTransferCount",
            )
        ]

    class _EXT(ctypes.Structure):
        _fields_ = [
            ("BasicLimitInformation", _BASIC),
            ("IoInfo", _IO),
            ("ProcessMemoryLimit", ctypes.c_size_t),
            ("JobMemoryLimit", ctypes.c_size_t),
            ("PeakProcessMemoryUsed", ctypes.c_size_t),
            ("PeakJobMemoryUsed", ctypes.c_size_t),
        ]

    JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x2000
    JobObjectExtendedLimitInformation = 9

    info = _EXT()
    info.BasicLimitInformation.LimitFlags = JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE
    if not kernel32.SetInformationJobObject(
        job, JobObjectExtendedLimitInformation, ctypes.byref(info), ctypes.sizeof(info)
    ):
        kernel32.CloseHandle(job)
        return None

    kernel32.AssignProcessToJobObject(job, kernel32.GetCurrentProcess())

    return job


def _assign_pid_to_job(job_handle, pid):
    if job_handle is None or sys.platform != "win32" or pid is None:
        return
    kernel32 = ctypes.windll.kernel32
    PROCESS_ALL_ACCESS = 0x1F0FFF
    handle = kernel32.OpenProcess(PROCESS_ALL_ACCESS, False, pid)
    if handle:
        kernel32.AssignProcessToJobObject(job_handle, handle)
        kernel32.CloseHandle(handle)


def _cleanup():
    global _active_pool
    if _active_pool is not None:
        try:
            _active_pool.terminate()
            _active_pool.join()
        except Exception:
            pass
        _active_pool = None


def _signal_handler(signum, frame):
    _cleanup()
    sys.exit(1)


current_dir = os.path.dirname(os.path.abspath(__file__))
if current_dir not in sys.path:
    sys.path.insert(0, current_dir)

portable_dir = os.path.dirname(sys.executable)
core_plugins_dir = os.path.join(portable_dir, "vs-coreplugins")

if portable_dir not in os.environ.get("PATH", ""):
    os.environ["PATH"] = f"{portable_dir};{os.environ.get('PATH', '')}"

if hasattr(os, "add_dll_directory"):
    try:
        os.add_dll_directory(portable_dir)
        if os.path.exists(core_plugins_dir):
            os.add_dll_directory(core_plugins_dir)
    except Exception as e:
        print(f"Avviso directory DLL: {e}")

import vapoursynth as vs
from vsscale import ArtCNN
from vsdeband import placebo_deband
from vsdehalo import fine_dehalo
from vskernels import Bilinear
from vstools import depth, DitherType
from vsmlrt import BackendV2

core = vs.core


def get_image_files(input_path):
    valid_extensions = (".png", ".jpg", ".jpeg", ".webp", ".bmp")
    if os.path.isfile(input_path):
        if input_path.lower().endswith(valid_extensions):
            return [input_path]
        return []
    elif os.path.isdir(input_path):
        files = []
        for item in os.listdir(input_path):
            if item.lower().endswith(valid_extensions):
                full_path = os.path.join(input_path, item)
                if os.path.isfile(full_path):
                    files.append(full_path)
        return files
    return []


def get_best_backend_name():
    dummy_clip = core.std.BlankClip(format=vs.RGBS, width=128, height=128, length=1)
    print("\nRicerca del miglior backend AI supportato dal sistema...")
    try:
        backend = BackendV2.TRT(
            static_shape=True,
            fp16=True,
            use_cuda_graph=True,
            min_shapes=(128, 128),
            opt_shapes=(128, 128),
            max_shapes=(128, 128),
        )
        ArtCNN.R8F64_JPEG444(
            kernel=Bilinear, tilesize=[128, 128], overlap=[8, 8], backend=backend
        ).scale(dummy_clip, width=128, height=128).get_frame(0)
        print("[AI] Backend selezionato: TensorRT RTX (NVIDIA RTX)")
        return "TRT_RTX"
    except Exception:
        pass

    try:
        backend = BackendV2.TRT(
            static_shape=True,
            fp16=True,
            min_shapes=(128, 128),
            opt_shapes=(128, 128),
            max_shapes=(128, 128),
        )
        ArtCNN.R8F64_JPEG444(
            kernel=Bilinear, tilesize=[128, 128], overlap=[8, 8], backend=backend
        ).scale(dummy_clip, width=128, height=128).get_frame(0)
        print("[AI] Backend selezionato: TensorRT (NVIDIA)")
        return "TRT"
    except Exception:
        pass

    try:
        backend = BackendV2.ORT_CUDA(fp16=True)
        ArtCNN.R8F64_JPEG444(
            kernel=Bilinear, tilesize=[128, 128], overlap=[8, 8], backend=backend
        ).scale(dummy_clip, width=128, height=128).get_frame(0)
        print("[AI] Backend selezionato: ONNX-CUDA (NVIDIA Fallback)")
        return "CUDA"
    except Exception:
        pass

    try:
        backend = BackendV2.ORT_DML(fp16=True)
        ArtCNN.R8F64_JPEG444(
            kernel=Bilinear, tilesize=[128, 128], overlap=[8, 8], backend=backend
        ).scale(dummy_clip, width=128, height=128).get_frame(0)
        print("[AI] Backend selezionato: ONNX-DirectML (AMD/Intel GPU)")
        return "DML"
    except Exception:
        pass

    try:
        backend = BackendV2.NCNN_VK(fp16=True)
        ArtCNN.R8F64_JPEG444(
            kernel=Bilinear, tilesize=[128, 128], overlap=[8, 8], backend=backend
        ).scale(dummy_clip, width=128, height=128).get_frame(0)
        print("[AI] Backend selezionato: NCNN-Vulkan (GPU Universale)")
        return "NCNN"
    except Exception:
        pass

    print("[AI] Backend selezionato: CPU (Lento ma universale)")
    return "CPU"


def process_image(
    input_file,
    output_file,
    backend_name,
    output_format="WEBP",
    target_width=1000,
    grain=5,
):
    clip = core.imwri.Read(input_file)
    clip = core.resize.Point(clip, format=vs.RGBS)

    if backend_name == "TRT_RTX":
        backend_cfg = BackendV2.TRT(
            static_shape=True,
            fp16=True,
            use_cuda_graph=True,
            min_shapes=(128, 128),
            opt_shapes=(128, 128),
            max_shapes=(128, 128),
        )
    elif backend_name == "TRT":
        backend_cfg = BackendV2.TRT(
            static_shape=True,
            fp16=True,
            min_shapes=(128, 128),
            opt_shapes=(128, 128),
            max_shapes=(128, 128),
        )
    elif backend_name == "CUDA":
        backend_cfg = BackendV2.ORT_CUDA(fp16=True)
    elif backend_name == "DML":
        backend_cfg = BackendV2.ORT_DML(fp16=True)
    elif backend_name == "NCNN":
        backend_cfg = BackendV2.NCNN_VK(fp16=True)
    else:
        backend_cfg = BackendV2.ORT_CPU()

    MULTI_SCALE_MAP = {
        "4x": 4,
        "x4": 4,
        "8x": 8,
        "x8": 8,
        "16x": 16,
        "x16": 16,
    }

    if isinstance(target_width, str):
        tw_lower = target_width.lower()
        if tw_lower in ["1x", "x1"]:
            target_width, target_height, ai_scaled_clip = clip.width, clip.height, clip
        elif tw_lower in ["2x", "x2"]:
            target_width, target_height = clip.width * 2, clip.height * 2
            ai_scaled_clip = ArtCNN.R8F64_JPEG444(
                kernel=Bilinear,
                tilesize=[128, 128],
                overlap=[8, 8],
                backend=backend_cfg,
            ).scale(clip, width=target_width, height=target_height)
        elif tw_lower in MULTI_SCALE_MAP:
            passes = MULTI_SCALE_MAP[tw_lower].bit_length() - 1
            ai_scaled_clip = clip
            for _ in range(passes):
                ai_scaled_clip = ArtCNN.R8F64_JPEG444(
                    kernel=Bilinear,
                    tilesize=[128, 128],
                    overlap=[8, 8],
                    backend=backend_cfg,
                ).scale(
                    ai_scaled_clip,
                    width=ai_scaled_clip.width * 2,
                    height=ai_scaled_clip.height * 2,
                )
            target_width = ai_scaled_clip.width
            target_height = ai_scaled_clip.height
        else:
            target_width = int(target_width) if target_width.isdigit() else 1000
            target_height = int(round(clip.height * (target_width / clip.width)))
            ai_scaled_clip = ArtCNN.R8F64_JPEG444(
                kernel=Bilinear,
                tilesize=[128, 128],
                overlap=[8, 8],
                backend=backend_cfg,
            ).scale(clip, width=target_width, height=target_height)
    else:
        target_height = int(round(clip.height * (target_width / clip.width)))
        ai_scaled_clip = ArtCNN.R8F64_JPEG444(
            kernel=Bilinear, tilesize=[128, 128], overlap=[8, 8], backend=backend_cfg
        ).scale(clip, width=target_width, height=target_height)

    dehaloed = [
        fine_dehalo(
            core.std.ShufflePlanes(ai_scaled_clip, planes=[i], colorfamily=vs.GRAY),
            brightstr=1,
            exclude=False,
            planes=[0],
        )
        for i in range(3)
    ]
    dehalo = core.std.ShufflePlanes(
        clips=dehaloed, planes=[0, 0, 0], colorfamily=vs.RGB
    )
    deband = placebo_deband(
        dehalo,
        radius=14.0,
        thr=2,
        iterations=3,
        grain=[grain, grain, grain],
        planes=[0, 1, 2],
    )
    cas = core.cas.CAS(deband, sharpness=0.8, opt=0)

    temp_output = f"{output_file}_%d.{output_format.lower()}"
    clip = core.imwri.Write(
        clip=depth(
            cas,
            10 if output_format.lower() == "webp" else 8,
            dither_type=DitherType.NONE,
        ),
        imgformat=output_format.upper(),
        filename=temp_output,
        firstnum=0,
        quality=100,
        dither=False,
    )
    clip.get_frame(0)
    sleep(2)

    file_creato = temp_output.replace("%d", "0")
    if os.path.exists(file_creato):
        if os.path.exists(output_file):
            os.remove(output_file)
        os.rename(file_creato, output_file)


def run_processing(
    INPUT_PATH,
    target_width,
    grain,
    output_format,
    on_complete=None,
    output_path=None,
    cli_mode=False,
):
    if output_path:
        OUTPUT_FOLDER = output_path
    else:
        base_dir = (
            os.path.dirname(INPUT_PATH) if os.path.isfile(INPUT_PATH) else INPUT_PATH
        )
        OUTPUT_FOLDER = os.path.join(base_dir, "upscaled")
    os.makedirs(OUTPUT_FOLDER, exist_ok=True)

    vs_max_workers = (
        int(multiprocessing.cpu_count() / 2) if multiprocessing.cpu_count() > 2 else 1
    )
    images = get_image_files(INPUT_PATH)

    if not images:
        print("Nessuna immagine trovata da processare.")
        if on_complete:
            on_complete(False)
        return

    best_backend_name = get_best_backend_name()
    if not cli_mode:
        print(
            f"\nAvvio VapourSynth su {len(images)} immagini... ({vs_max_workers} worker)"
        )

    completed = [0]
    total = len(images)

    def _on_done(_):
        completed[0] += 1
        if cli_mode:
            print(f"{completed[0] / total:.2f}", flush=True)
        else:
            pbar.update(1)

    def _on_error(e):
        completed[0] += 1
        if cli_mode:
            sys.stderr.write(f"Errore: {e}\n")
            print(f"{completed[0] / total:.2f}", flush=True)
        else:
            tqdm.write(f"Errore: {e}")
            pbar.update(1)

    global _active_pool
    pool = multiprocessing.Pool(processes=vs_max_workers, maxtasksperchild=1)
    _active_pool = pool
    # Assegna esplicitamente ogni worker al Job Object (fallback se l'ereditarietà non basta)
    for worker in pool._pool:
        _assign_pid_to_job(_job_handle, worker.pid)
    pool_closed = False
    try:
        if not cli_mode:
            pbar = tqdm(total=total, desc="VapourSynth", unit="img")
        for img_path in images:
            out_path = os.path.join(
                OUTPUT_FOLDER, f"{Path(img_path).stem}_upscaled.{output_format.lower()}"
            )
            pool.apply_async(
                process_image,
                args=(
                    img_path,
                    out_path,
                    best_backend_name,
                    output_format,
                    target_width,
                    grain,
                ),
                callback=_on_done,
                error_callback=_on_error,
            )
        pool.close()
        pool.join()
        pool_closed = True
        if not cli_mode:
            pbar.close()
    except (KeyboardInterrupt, SystemExit):
        if not pool_closed:
            pool.terminate()
            pool.join()
        raise
    finally:
        _active_pool = None
        if not pool_closed:
            pool.terminate()

    if not cli_mode:
        print(f"\nFinito! {len(images)} immagini processate.\n")
    if on_complete:
        on_complete(True)


def run_cli():
    print("Configurazione Upscaler (CLI)\n" + "-" * 30)
    while True:
        INPUT_PATH = (
            input(
                "Inserisci il PATH dell'immagine o della cartella (con apici). Puoi anche trascinare il file/cartella: "
            )
            .strip()
            .strip('"')
        )
        if INPUT_PATH and os.path.exists(INPUT_PATH):
            break
        print("[ERRORE] Percorso non valido.")

    w_in = (
        input(
            "Larghezza [1x, 2x, 4x, 8x, o numero - default 1000]\nATTENZIONE: Non sono resposabile se l'immagine finale è troppo grande e manda in crash il vostro computer, fate i calcoli prima di usare 8x: "
        )
        .strip()
        .lower()
    )
    t_width = (
        w_in
        if w_in in ["1x", "2x", "4x", "8x", "x1", "x2", "x4", "x8"]
        else (int(w_in) if w_in.isdigit() else 1000)
    )

    g_in = input("Grain [Premi INVIO per default: 1]: ").strip()
    grain = int(g_in) if g_in.isdigit() else 1

    o_fmt = input("Formato (webp/png) [default: webp]: ").strip().lower()
    o_fmt = o_fmt if o_fmt in ["webp", "png"] else "webp"

    print("-" * 30)
    run_processing(INPUT_PATH, t_width, grain, o_fmt)


if __name__ == "__main__":
    multiprocessing.freeze_support()

    # Job Object Windows: garantisce il kill dei worker anche con TerminateProcess() da Kotlin
    _job_handle = _setup_windows_job_object()

    # Fallback segnali per Ctrl+C / Ctrl+Break interattivi
    atexit.register(_cleanup)
    signal.signal(signal.SIGINT, _signal_handler)
    if hasattr(signal, "SIGBREAK"):
        signal.signal(signal.SIGBREAK, _signal_handler)

    parser = argparse.ArgumentParser(
        prog="upscaler_core",
        description="BetterManhwa Upscaler",
    )
    parser.add_argument(
        "input",
        nargs="?",
        metavar="INPUT",
        help="Percorso dell'immagine o della cartella da processare.",
    )
    parser.add_argument(
        "-w",
        "--width",
        default="1000",
        metavar="LARGHEZZA",
        help="Larghezza target: numero di pixel (es. 1000) oppure moltiplicatore (1x, 2x, 4x, 8x). [default: 1000]",
    )
    parser.add_argument(
        "-g",
        "--grain",
        type=int,
        default=1,
        metavar="GRAIN",
        help="Intensità del grain da aggiungere (intero). [default: 1]",
    )
    parser.add_argument(
        "-f",
        "--format",
        choices=["webp", "png"],
        default="webp",
        dest="fmt",
        metavar="FORMATO",
        help="Formato di output: webp o png. [default: webp]",
    )
    parser.add_argument(
        "-o",
        "--output",
        default=None,
        metavar="OUTPUT",
        help="Cartella di destinazione per le immagini processate. [default: <input>/upscaled]",
    )

    args = parser.parse_args()

    if args.input:
        if not os.path.exists(args.input):
            parser.error(f"Percorso non valido: {args.input}")

        if args.output and not os.path.exists(args.output):
            try:
                os.makedirs(args.output, exist_ok=True)
            except Exception as e:
                parser.error(f"Impossibile creare la cartella di output: {e}")

        w_in = args.width.strip().lower()
        SCALE_KEYWORDS = ["1x", "2x", "4x", "8x", "x1", "x2", "x4", "x8"]
        t_width = (
            w_in if w_in in SCALE_KEYWORDS else (int(w_in) if w_in.isdigit() else 1000)
        )

        run_processing(
            args.input,
            t_width,
            args.grain,
            args.fmt,
            output_path=args.output,
            cli_mode=True,
        )
    else:
        run_cli()
