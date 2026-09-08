package org.wip.plugintoolkit.features.job.utils

import kotlin.test.Test
import kotlin.test.assertTrue

class ProcessMemoryUtilsTest {

    @Test
    fun testCurrentProcessMemoryIsPositive() {
        val currentPid = ProcessHandle.current().pid()
        val memBytes = ProcessMemoryUtils.getProcessMemoryBytes(currentPid)
        assertTrue(memBytes > 0L, "Current process memory should be > 0 bytes, but was $memBytes")
    }

    @Test
    fun testInvalidPidReturnsZero() {
        val memBytes = ProcessMemoryUtils.getProcessMemoryBytes(-1L)
        assertTrue(memBytes == 0L, "Invalid PID memory should be 0")
    }

    @Test
    fun testDescendantPidsCanBeQueried() {
        // Just verify it runs safely without throwing
        val pids = ProcessMemoryUtils.getDescendantPids()
        assertTrue(pids.size >= 0)
        val totalMem = ProcessMemoryUtils.getAllDescendantsMemoryBytes()
        assertTrue(totalMem >= 0L)
    }
}
