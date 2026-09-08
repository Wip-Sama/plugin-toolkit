package org.wip.plugintoolkit.features.job.utils

import com.sun.jna.Native
import com.sun.jna.Structure
import com.sun.jna.platform.win32.BaseTSD
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.win32.StdCallLibrary
import org.wip.plugintoolkit.core.utils.FileUtils
import java.io.File
import kotlin.streams.toList

actual object ProcessMemoryUtils {

    actual fun getProcessMemoryBytes(pid: Long): Long {
        if (pid <= 0L) return 0L
        return when {
            FileUtils.isWindows -> getWindowsProcessMemory(pid)
            FileUtils.isLinux -> getLinuxProcessMemory(pid)
            FileUtils.isMac -> getMacProcessMemory(pid)
            else -> getLinuxProcessMemory(pid)
        }
    }

    actual fun getDescendantPids(): List<Long> {
        return try {
            ProcessHandle.current().descendants()
                .filter { it.isAlive }
                .map { it.pid() }
                .toList()
        } catch (e: Throwable) {
            emptyList()
        }
    }

    actual fun getAllDescendantsMemoryBytes(): Long {
        val pids = getDescendantPids()
        if (pids.isEmpty()) return 0L
        return pids.sumOf { getProcessMemoryBytes(it) }
    }

    actual fun isProcessAlive(pid: Long): Boolean {
        if (pid <= 0L) return false
        return try {
            ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
        } catch (e: Throwable) {
            false
        }
    }

    actual fun getTotalTrackedMemoryBytes(extraPids: Collection<Long>): Long {
        val allPids = (getDescendantPids() + extraPids.filter { it > 0L }).distinct()
        if (allPids.isEmpty()) return 0L
        return allPids.sumOf { getProcessMemoryBytes(it) }
    }

    private fun getWindowsProcessMemory(pid: Long): Long {
        val processQueryInformation = 0x0400
        val processVmRead = 0x0010
        val handle = Kernel32.INSTANCE.OpenProcess(
            processQueryInformation or processVmRead,
            false,
            pid.toInt()
        ) ?: return 0L
        return try {
            val counters = WindowsPsapi.PROCESS_MEMORY_COUNTERS()
            if (WindowsPsapi.INSTANCE.GetProcessMemoryInfo(handle, counters, counters.size())) {
                counters.WorkingSetSize.toLong()
            } else {
                0L
            }
        } catch (e: Throwable) {
            0L
        } finally {
            Kernel32.INSTANCE.CloseHandle(handle)
        }
    }

    private fun getLinuxProcessMemory(pid: Long): Long {
        return try {
            val statusFile = File("/proc/$pid/status")
            if (!statusFile.exists()) return 0L
            val lines = statusFile.readLines()
            val vmRssLine = lines.firstOrNull { it.startsWith("VmRSS:") } ?: return 0L
            val parts = vmRssLine.substringAfter(":").trim().split("\\s+".toRegex())
            val kb = parts.firstOrNull()?.toLongOrNull() ?: 0L
            kb * 1024L
        } catch (e: Throwable) {
            0L
        }
    }

    private fun getMacProcessMemory(pid: Long): Long {
        return try {
            val proc = ProcessBuilder("ps", "-o", "rss=", "-p", pid.toString())
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            val kb = output.toLongOrNull() ?: 0L
            kb * 1024L
        } catch (e: Throwable) {
            0L
        }
    }

    private interface WindowsPsapi : StdCallLibrary {
        @Structure.FieldOrder(
            "cb", "PageFaultCount", "PeakWorkingSetSize", "WorkingSetSize",
            "QuotaPeakPagedPoolUsage", "QuotaPagedPoolUsage",
            "QuotaPeakNonPagedPoolUsage", "QuotaNonPagedPoolUsage",
            "PagefileUsage", "PeakPagefileUsage"
        )
        class PROCESS_MEMORY_COUNTERS : Structure() {
            @JvmField var cb: Int = 0
            @JvmField var PageFaultCount: Int = 0
            @JvmField var PeakWorkingSetSize: BaseTSD.SIZE_T = BaseTSD.SIZE_T()
            @JvmField var WorkingSetSize: BaseTSD.SIZE_T = BaseTSD.SIZE_T()
            @JvmField var QuotaPeakPagedPoolUsage: BaseTSD.SIZE_T = BaseTSD.SIZE_T()
            @JvmField var QuotaPagedPoolUsage: BaseTSD.SIZE_T = BaseTSD.SIZE_T()
            @JvmField var QuotaPeakNonPagedPoolUsage: BaseTSD.SIZE_T = BaseTSD.SIZE_T()
            @JvmField var QuotaNonPagedPoolUsage: BaseTSD.SIZE_T = BaseTSD.SIZE_T()
            @JvmField var PagefileUsage: BaseTSD.SIZE_T = BaseTSD.SIZE_T()
            @JvmField var PeakPagefileUsage: BaseTSD.SIZE_T = BaseTSD.SIZE_T()

            init {
                cb = size()
            }
        }

        fun GetProcessMemoryInfo(
            hProcess: WinNT.HANDLE,
            ppsmemCounters: PROCESS_MEMORY_COUNTERS,
            cb: Int
        ): Boolean

        companion object {
            val INSTANCE: WindowsPsapi by lazy {
                Native.load("psapi", WindowsPsapi::class.java)
            }
        }
    }
}
