package org.wip.plugintoolkit.core.utils

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppLockManagerTest {
    private lateinit var tempDir: File

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("plugintoolkit_lock_test").toFile()
    }

    @AfterTest
    fun tearDown() {
        AppLockManager.releaseLock()
        tempDir.deleteRecursively()
    }

    @Test
    fun testAcquireAndReleaseLock() {
        val result = AppLockManager.acquireLock(tempDir)
        assertTrue(result is LockResult.Acquired, "Expected lock to be acquired successfully")
        assertTrue(AppLockManager.isLockAcquired(), "isLockAcquired should return true")

        val lockFile = File(tempDir, ".lock")
        assertTrue(lockFile.exists(), "Lock file should be created on disk")
        val currentPid = AppLockManager.getCurrentLockPid()
        assertNotNull(currentPid, "PID should be tracked by AppLockManager")
        assertEquals(ProcessHandle.current().pid(), currentPid, "PID should match current process")

        AppLockManager.releaseLock()
        assertFalse(AppLockManager.isLockAcquired(), "isLockAcquired should return false after release")
        assertFalse(lockFile.exists(), "Lock file should be deleted on release")
    }

    @Test
    fun testAlreadyRunningWhenLockHeld() {
        val firstResult = AppLockManager.acquireLock(tempDir)
        assertTrue(firstResult is LockResult.Acquired)

        // Attempting to acquire again while already held should return AlreadyRunning or Acquired depending on reentrancy
        // With FileChannel/AppLockManager, a second call checks the held lock and returns AlreadyRunning
        val secondResult = AppLockManager.acquireLock(tempDir)
        assertTrue(secondResult is LockResult.AlreadyRunning, "Subsequent acquire on same lock should indicate AlreadyRunning")
    }

    @Test
    fun testForceClearAndAcquire() {
        val initialAcquire = AppLockManager.acquireLock(tempDir)
        assertTrue(initialAcquire is LockResult.Acquired)

        val forceResult = AppLockManager.forceClearAndAcquire(tempDir)
        assertTrue(forceResult is LockResult.Acquired, "Force clear and acquire should successfully re-acquire the lock")
        assertTrue(AppLockManager.isLockAcquired())
    }
}
