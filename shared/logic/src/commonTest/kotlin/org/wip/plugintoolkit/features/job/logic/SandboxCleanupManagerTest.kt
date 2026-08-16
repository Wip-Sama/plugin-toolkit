package org.wip.plugintoolkit.features.job.logic

import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SandboxCleanupManagerTest {

    @Test
    fun testCleanOrphanSandboxesDeletesInactiveJobDirectories() = runTest {
        val manager = SandboxCleanupManager()
        val tempRootDir = Path("build/test_sandboxes_${kotlin.random.Random.nextInt(100000)}")
        val jobsDir = Path(tempRootDir, "jobs")
        
        try {
            if (!SystemFileSystem.exists(jobsDir)) {
                SystemFileSystem.createDirectories(jobsDir)
            }

            val activeJobDir = Path(jobsDir, "job-active-1")
            val orphanJobDir = Path(jobsDir, "job-orphan-2")
            SystemFileSystem.createDirectories(activeJobDir)
            SystemFileSystem.createDirectories(orphanJobDir)

            val fileInOrphan = Path(orphanJobDir, "file.txt")
            SystemFileSystem.sink(fileInOrphan).buffered().use { sink ->
                sink.writeString("test content")
            }

            assertTrue(SystemFileSystem.exists(activeJobDir))
            assertTrue(SystemFileSystem.exists(orphanJobDir))

            val activeJobIds = setOf("job-active-1")
            manager.cleanOrphanSandboxes(tempRootDir.toString(), activeJobIds)

            assertTrue(SystemFileSystem.exists(activeJobDir), "Active job directory should not be deleted")
            assertFalse(SystemFileSystem.exists(orphanJobDir), "Orphan job directory should be deleted")
        } finally {
            if (SystemFileSystem.exists(tempRootDir)) {
                deleteRecursively(tempRootDir)
            }
        }
    }

    @Test
    fun testRegisterFailedDeletionRetriesSuccessfully() = runTest {
        val manager = SandboxCleanupManager()
        val tempRootDir = Path("build/test_failed_del_${kotlin.random.Random.nextInt(100000)}")
        val dummyPath = Path(tempRootDir, "sandbox_locked")

        try {
            SystemFileSystem.createDirectories(dummyPath)
            assertTrue(SystemFileSystem.exists(dummyPath))

            manager.registerFailedDeletion(dummyPath.toString())
            manager.cleanOrphanSandboxes(tempRootDir.toString(), emptySet())

            assertFalse(SystemFileSystem.exists(dummyPath), "Pending failed path should be deleted during cleanup sweep")
        } finally {
            if (SystemFileSystem.exists(tempRootDir)) {
                deleteRecursively(tempRootDir)
            }
        }
    }
}
