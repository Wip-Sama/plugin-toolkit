package org.wip.plugintoolkit.core.utils

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrashDetectorTest {
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("crash_detector_test").toFile()
        CrashDetector.resetForTesting()
    }

    @AfterTest
    fun tearDown() {
        CrashDetector.recordCleanShutdown()
        CrashDetector.resetForTesting()
        tempDir.deleteRecursively()
    }

    @Test
    fun testFirstLaunchReturnsNullAndWritesMarker() {
        val markerFile = File(tempDir, CrashDetector.DEFAULT_MARKER_FILE_NAME)
        assertFalse(markerFile.exists())

        val result = CrashDetector.checkAndRecordSessionStart(tempDir)
        assertNull(result, "First launch without prior marker should report no crash")
        assertTrue(markerFile.exists(), "Session marker should be written on session start")

        val lines = markerFile.readLines()
        assertEquals(2, lines.size, "Marker file should have PID and timestamp")
        assertEquals(ProcessHandle.current().pid(), lines[0].toLong())
    }

    @Test
    fun testPreviousSessionCrashDetectedWhenMarkerExists() {
        val markerFile = File(tempDir, CrashDetector.DEFAULT_MARKER_FILE_NAME)
        val simulatedPid = 99999L
        val simulatedTimestamp = 1234567890L
        markerFile.writeText("$simulatedPid\n$simulatedTimestamp")

        val result = CrashDetector.checkAndRecordSessionStart(tempDir)
        assertNotNull(result, "Should detect prior unclosed session")
        assertEquals(simulatedPid, result.pid)
        assertEquals(simulatedTimestamp, result.timestamp)
        assertEquals(markerFile.absolutePath, result.markerFile.absolutePath)

        // Verifies the marker was updated with current session's info
        val updatedLines = markerFile.readLines()
        assertEquals(ProcessHandle.current().pid(), updatedLines[0].toLong())
    }

    @Test
    fun testCleanShutdownDeletesMarker() {
        CrashDetector.checkAndRecordSessionStart(tempDir)
        val markerFile = File(tempDir, CrashDetector.DEFAULT_MARKER_FILE_NAME)
        assertTrue(markerFile.exists())

        val cleanShutdownSuccess = CrashDetector.recordCleanShutdown()
        assertTrue(cleanShutdownSuccess)
        assertFalse(markerFile.exists(), "Marker should be deleted upon clean shutdown")

        // Next start should report no crash
        val nextStartResult = CrashDetector.checkAndRecordSessionStart(tempDir)
        assertNull(nextStartResult, "Clean shutdown means subsequent session does not detect a crash")
    }
}
