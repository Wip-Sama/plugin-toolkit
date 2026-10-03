package org.wip.plugintoolkit.features.job.utils

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HardwareResourceUtilsTest {

    @Test
    fun testProcessCpuUtilsBasic() {
        val cores = ProcessCpuUtils.getAvailableProcessors()
        assertTrue(cores >= 1, "Cores should be at least 1")

        val sysLoad = ProcessCpuUtils.getSystemCpuUsagePercent()
        // sysLoad can be null or >= 0.0
        if (sysLoad != null) {
            assertTrue(sysLoad in 0.0..100.0, "System CPU load should be between 0 and 100")
        }

        val procLoad = ProcessCpuUtils.getProcessCpuUsagePercent()
        if (procLoad != null) {
            assertTrue(procLoad in 0.0..100.0, "Process CPU load should be between 0 and 100")
        }
    }

    @Test
    fun testProcessGpuUtilsFallbackSafety() {
        // These calls must never throw an unhandled exception regardless of hardware
        val isSupported = ProcessGpuUtils.isVramMonitoringSupported()
        val trackedVram = ProcessGpuUtils.getTotalTrackedVramBytes()
        assertTrue(trackedVram >= 0L, "Tracked VRAM should be non-negative")

        val sysVram = ProcessGpuUtils.getSystemVramBytes()
        if (sysVram != null) {
            val (used, total) = sysVram
            assertTrue(total > 0L, "Total system VRAM should be positive when present")
            assertTrue(used in 0L..total, "Used VRAM should be within total VRAM")
        }
    }

    @Test
    fun testPdhGpuProcessMemory() {
        if (!org.wip.plugintoolkit.core.utils.FileUtils.isWindows) return
        val currentPid = ProcessHandle.current().pid()
        val tracked = ProcessGpuUtils.getTotalTrackedVramBytes()
        assertTrue(tracked >= 0L, "Tracked VRAM should be non-negative")
        val procVram = ProcessGpuUtils.getProcessVramBytes(currentPid)
        assertTrue(procVram >= 0L, "Current process VRAM should be non-negative")
    }
}
