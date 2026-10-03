package org.wip.plugintoolkit.features.job.utils

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.PointerByReference
import org.wip.plugintoolkit.core.utils.FileUtils
import java.io.File
import kotlin.streams.toList

internal interface NvmlLib : Library {
    @Structure.FieldOrder("total", "free", "used")
    class NvmlMemory : Structure() {
        @JvmField var total: Long = 0L
        @JvmField var free: Long = 0L
        @JvmField var used: Long = 0L
    }

    @Structure.FieldOrder("pid", "usedGpuMemory")
    class NvmlProcessInfo : Structure() {
        @JvmField var pid: Int = 0
        @JvmField var usedGpuMemory: Long = 0L
    }

    fun nvmlInit_v2(): Int
    fun nvmlShutdown(): Int
    fun nvmlDeviceGetCount_v2(deviceCount: IntArray): Int
    fun nvmlDeviceGetHandleByIndex_v2(index: Int, device: PointerByReference): Int
    fun nvmlDeviceGetMemoryInfo(device: Pointer, memory: NvmlMemory): Int
    fun nvmlDeviceGetComputeRunningProcesses(device: Pointer, infoCount: IntArray, infos: Array<NvmlProcessInfo>?): Int
    fun nvmlDeviceGetGraphicsRunningProcesses(device: Pointer, infoCount: IntArray, infos: Array<NvmlProcessInfo>?): Int
}

actual object ProcessGpuUtils {

    private val nvml: NvmlLib? by lazy {
        try {
            val lib = Native.load("nvml", NvmlLib::class.java)
            val res = lib.nvmlInit_v2()
            if (res == 0) lib else null
        } catch (_: Throwable) {
            null
        }
    }

    actual fun isVramMonitoringSupported(): Boolean {
        if (FileUtils.isWindows) return true
        if (nvml != null) return true
        if (FileUtils.isLinux && hasLinuxAmdVramSysfs()) return true
        return false
    }

    actual fun getProcessVramBytes(pid: Long): Long {
        if (pid <= 0L) return 0L
        var vram = 0L
        if (FileUtils.isWindows) {
            vram = maxOf(vram, getWindowsProcessVram(listOf(pid)))
        }
        val lib = nvml
        if (lib != null) {
            vram = maxOf(vram, getNvmlProcessVram(lib, pid))
        }
        return vram
    }

    actual fun getTotalTrackedVramBytes(extraPids: Collection<Long>): Long {
        val currentPid = try { ProcessHandle.current().pid() } catch (_: Throwable) { 0L }
        val descendantPids = ProcessMemoryUtils.getDescendantPids()
        val allPids = (listOf(currentPid) + descendantPids + extraPids.filter { it > 0L }).filter { it > 0L }.distinct()
        if (allPids.isEmpty()) return 0L

        var totalVram = 0L
        if (FileUtils.isWindows) {
            totalVram = maxOf(totalVram, getWindowsProcessVram(allPids))
        }

        val lib = nvml
        if (lib != null) {
            val processMap = getAllNvmlProcessVram(lib)
            val nvmlVram = allPids.sumOf { processMap[it] ?: 0L }
            totalVram = maxOf(totalVram, nvmlVram)
        }

        return totalVram
    }

    actual fun getSystemVramBytes(): Pair<Long, Long>? {
        val lib = nvml
        if (lib != null) {
            val nvmlSys = getNvmlSystemVram(lib)
            if (nvmlSys != null) return nvmlSys
        }

        if (FileUtils.isLinux) {
            val amdSys = getLinuxAmdSystemVram()
            if (amdSys != null) return amdSys
        }

        return null
    }

    @Volatile
    private var lastPdhTime = 0L
    @Volatile
    private var cachedPdhMap = emptyMap<Long, Long>()

    private fun getWindowsProcessVram(pids: Collection<Long>): Long {
        if (!FileUtils.isWindows) return 0L
        val validPids = pids.filter { it > 0L }.toSet()
        if (validPids.isEmpty()) return 0L

        val now = System.currentTimeMillis()
        if (now - lastPdhTime < CACHE_TTL_MS && cachedPdhMap.isNotEmpty()) {
            return validPids.sumOf { cachedPdhMap[it] ?: 0L }
        }

        return try {
            val items = com.sun.jna.platform.win32.PdhUtil.PdhEnumObjectItems(null, null, "GPU Process Memory", 100)
            val instances = items.instances ?: return 0L
            val targetInstances = mutableListOf<Pair<Long, String>>()
            for (inst in instances) {
                if (!inst.startsWith("pid_")) continue
                val nextUnder = inst.indexOf('_', 4)
                if (nextUnder <= 4) continue
                val pid = inst.substring(4, nextUnder).toLongOrNull() ?: continue
                if (pid in validPids) {
                    targetInstances.add(Pair(pid, inst))
                }
            }
            if (targetInstances.isEmpty()) return 0L

            val pdh = com.sun.jna.platform.win32.Pdh.INSTANCE
            val hQuery = com.sun.jna.platform.win32.WinNT.HANDLEByReference()
            if (pdh.PdhOpenQuery(null, null, hQuery) != 0) return 0L

            val resultMap = mutableMapOf<Long, Long>()
            try {
                val counterList = mutableListOf<Pair<Long, com.sun.jna.platform.win32.WinNT.HANDLEByReference>>()
                for ((pid, inst) in targetInstances) {
                    val hCounter = com.sun.jna.platform.win32.WinNT.HANDLEByReference()
                    if (pdh.PdhAddEnglishCounter(hQuery.value, "\\GPU Process Memory($inst)\\Dedicated Usage", null, hCounter) == 0) {
                        counterList.add(Pair(pid, hCounter))
                    }
                }
                if (counterList.isNotEmpty() && pdh.PdhCollectQueryData(hQuery.value) == 0) {
                    val raw = com.sun.jna.platform.win32.Pdh.PDH_RAW_COUNTER()
                    for ((pid, hCounter) in counterList) {
                        if (pdh.PdhGetRawCounterValue(hCounter.value, null, raw) == 0) {
                            val v = raw.FirstValue
                            if (v > 0L) {
                                resultMap[pid] = (resultMap[pid] ?: 0L) + v
                            }
                        }
                    }
                }
            } finally {
                pdh.PdhCloseQuery(hQuery.value)
            }

            cachedPdhMap = resultMap
            lastPdhTime = now
            validPids.sumOf { resultMap[it] ?: 0L }
        } catch (_: Throwable) {
            0L
        }
    }

    @Volatile
    private var lastProcessVramTime = 0L
    @Volatile
    private var cachedProcessVramMap = emptyMap<Long, Long>()

    @Volatile
    private var lastSysVramTime = 0L
    @Volatile
    private var cachedSysVram: Pair<Long, Long>? = null

    private const val CACHE_TTL_MS = 500L

    private val devicePointers: List<Pointer> by lazy {
        val lib = nvml ?: return@lazy emptyList()
        try {
            val count = IntArray(1)
            if (lib.nvmlDeviceGetCount_v2(count) == 0 && count[0] > 0) {
                val list = mutableListOf<Pointer>()
                for (i in 0 until count[0]) {
                    val ref = PointerByReference()
                    if (lib.nvmlDeviceGetHandleByIndex_v2(i, ref) == 0) {
                        list.add(ref.value)
                    }
                }
                list
            } else emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun getNvmlSystemVram(lib: NvmlLib): Pair<Long, Long>? {
        val now = System.currentTimeMillis()
        val cached = cachedSysVram
        if (now - lastSysVramTime < CACHE_TTL_MS && cached != null) {
            return cached
        }

        return try {
            val devices = devicePointers
            if (devices.isEmpty()) return null
            var totalUsed = 0L
            var totalCap = 0L

            for (device in devices) {
                val mem = NvmlLib.NvmlMemory()
                if (lib.nvmlDeviceGetMemoryInfo(device, mem) == 0) {
                    totalUsed += mem.used
                    totalCap += mem.total
                }
            }

            val result = if (totalCap > 0L) Pair(totalUsed, totalCap) else null
            cachedSysVram = result
            lastSysVramTime = now
            result
        } catch (_: Throwable) {
            null
        }
    }

    private fun getNvmlProcessVram(lib: NvmlLib, pid: Long): Long {
        val all = getAllNvmlProcessVram(lib)
        return all[pid] ?: 0L
    }

    private fun getAllNvmlProcessVram(lib: NvmlLib): Map<Long, Long> {
        val now = System.currentTimeMillis()
        val cached = cachedProcessVramMap
        if (now - lastProcessVramTime < CACHE_TTL_MS && cached.isNotEmpty()) {
            return cached
        }

        val result = mutableMapOf<Long, Long>()
        try {
            val devices = devicePointers
            if (devices.isEmpty()) return emptyMap()

            for (device in devices) {
                // Compute processes (CUDA / OpenCL / AI / ML runtimes)
                collectProcesses(device, result) { dev, cnt, arr ->
                    lib.nvmlDeviceGetComputeRunningProcesses(dev, cnt, arr)
                }
                // Graphics processes (DirectX / Vulkan / OpenGL)
                collectProcesses(device, result) { dev, cnt, arr ->
                    lib.nvmlDeviceGetGraphicsRunningProcesses(dev, cnt, arr)
                }
            }
            cachedProcessVramMap = result
            lastProcessVramTime = now
        } catch (_: Throwable) {
            // Ignore NVML polling errors
        }
        return result
    }

    private inline fun collectProcesses(
        device: Pointer,
        map: MutableMap<Long, Long>,
        query: (Pointer, IntArray, Array<NvmlLib.NvmlProcessInfo>?) -> Int
    ) {
        val infoCount = IntArray(1)
        val initialRes = query(device, infoCount, null)
        // 0 = Success, 7 = NVML_ERROR_INSUFFICIENT_SIZE
        if ((initialRes == 0 || initialRes == 7) && infoCount[0] > 0) {
            val count = infoCount[0]
            val sample = NvmlLib.NvmlProcessInfo()
            @Suppress("UNCHECKED_CAST")
            val array = sample.toArray(count) as Array<NvmlLib.NvmlProcessInfo>
            if (query(device, infoCount, array) == 0) {
                for (proc in array) {
                    val pidLong = proc.pid.toLong()
                    val vramLong = proc.usedGpuMemory
                    // On Windows WDDM, usedGpuMemory is -1 (NVML_VALUE_NOT_AVAILABLE) for graphics
                    if (pidLong > 0L && vramLong > 0L) {
                        map[pidLong] = (map[pidLong] ?: 0L) + vramLong
                    }
                }
            }
        }
    }

    private fun hasLinuxAmdVramSysfs(): Boolean {
        return try {
            val drmDir = File("/sys/class/drm")
            drmDir.exists() && (drmDir.listFiles()?.any {
                File(it, "device/mem_info_vram_total").exists()
            } == true)
        } catch (_: Throwable) {
            false
        }
    }

    private fun getLinuxAmdSystemVram(): Pair<Long, Long>? {
        return try {
            val drmDir = File("/sys/class/drm")
            if (!drmDir.exists()) return null
            var totalUsed = 0L
            var totalCap = 0L

            drmDir.listFiles()?.forEach { card ->
                val totalFile = File(card, "device/mem_info_vram_total")
                val usedFile = File(card, "device/mem_info_vram_used")
                if (totalFile.exists() && usedFile.exists()) {
                    val cap = totalFile.readText().trim().toLongOrNull() ?: 0L
                    val used = usedFile.readText().trim().toLongOrNull() ?: 0L
                    totalCap += cap
                    totalUsed += used
                }
            }

            if (totalCap > 0L) Pair(totalUsed, totalCap) else null
        } catch (_: Throwable) {
            null
        }
    }
}
