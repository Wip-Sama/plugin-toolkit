package org.wip.plugintoolkit.features.job.logic

import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import org.wip.plugintoolkit.features.settings.model.GeneralSettings
import org.wip.plugintoolkit.features.settings.model.ScratchDiskConfig
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScratchDiskManagerTest {

    @Test
    fun testResolveScratchRootDefaultFallback() {
        val fallbackDir = "build/test_fallback_${Random.nextInt(100000)}"
        val manager = ScratchDiskManager(
            customScratchDisksProvider = { emptyList() },
            customFallbackDirProvider = { fallbackDir }
        )

        val resolved = manager.resolveScratchRoot()
        assertEquals(fallbackDir.replace('\\', '/'), resolved)
    }

    @Test
    fun testResolveScratchRootPriorityOrder() {
        val rootHigh = "build/test_scratch_high_${Random.nextInt(100000)}"
        val rootLow = "build/test_scratch_low_${Random.nextInt(100000)}"
        val fallbackDir = "build/test_fallback_${Random.nextInt(100000)}"

        val disks = listOf(
            ScratchDiskConfig(id = "low", path = rootLow, priority = 10, enabled = true),
            ScratchDiskConfig(id = "high", path = rootHigh, priority = 1, enabled = true)
        )

        val manager = ScratchDiskManager(
            customScratchDisksProvider = { disks },
            customFallbackDirProvider = { fallbackDir }
        )

        try {
            val resolved = manager.resolveScratchRoot()
            assertEquals(rootHigh.replace('\\', '/'), resolved)
        } finally {
            deleteRecursively(Path(rootHigh))
            deleteRecursively(Path(rootLow))
        }
    }

    @Test
    fun testResolveScratchRootSkipsDisabled() {
        val rootHighDisabled = "build/test_scratch_disabled_${Random.nextInt(100000)}"
        val rootActive = "build/test_scratch_active_${Random.nextInt(100000)}"
        val fallbackDir = "build/test_fallback_${Random.nextInt(100000)}"

        val disks = listOf(
            ScratchDiskConfig(id = "disabled", path = rootHighDisabled, priority = 1, enabled = false),
            ScratchDiskConfig(id = "active", path = rootActive, priority = 5, enabled = true)
        )

        val manager = ScratchDiskManager(
            customScratchDisksProvider = { disks },
            customFallbackDirProvider = { fallbackDir }
        )

        try {
            val resolved = manager.resolveScratchRoot()
            assertEquals(rootActive.replace('\\', '/'), resolved)
        } finally {
            deleteRecursively(Path(rootHighDisabled))
            deleteRecursively(Path(rootActive))
        }
    }

    @Test
    fun testResolveScratchRootSkipsInvalidOrBlank() {
        val rootActive = "build/test_scratch_valid_${Random.nextInt(100000)}"
        val fallbackDir = "build/test_fallback_${Random.nextInt(100000)}"

        val disks = listOf(
            ScratchDiskConfig(id = "blank", path = "   ", priority = 1, enabled = true),
            ScratchDiskConfig(id = "invalid", path = "Z:\\non_existent_drive_98765\\invalid", priority = 2, enabled = true),
            ScratchDiskConfig(id = "valid", path = rootActive, priority = 3, enabled = true)
        )

        val manager = ScratchDiskManager(
            customScratchDisksProvider = { disks },
            customFallbackDirProvider = { fallbackDir }
        )

        try {
            val resolved = manager.resolveScratchRoot()
            assertEquals(rootActive.replace('\\', '/'), resolved)
        } finally {
            deleteRecursively(Path(rootActive))
        }
    }

    @Test
    fun testJobSandboxResolutionAndSubpaths() {
        val root = "build/test_scratch_paths_${Random.nextInt(100000)}"
        val disks = listOf(ScratchDiskConfig(id = "d1", path = root, priority = 1, enabled = true))
        val manager = ScratchDiskManager(
            customScratchDisksProvider = { disks },
            customFallbackDirProvider = { "fallback" }
        )

        try {
            val sandbox = manager.getJobSandboxDir("job-123")
            val normalizedRoot = root.replace('\\', '/')
            assertEquals("$normalizedRoot/jobs/job-123/sandbox", sandbox)

            val nodeSandbox = manager.getJobNodeSandboxDir("job-123", "node_5")
            assertEquals("$normalizedRoot/jobs/job-123/sandbox/node_5", nodeSandbox)
        } finally {
            deleteRecursively(Path(root))
        }
    }

    @Test
    fun testExistingSandboxDiscoveryForResumedJob() {
        val rootA = "build/test_scratch_a_${Random.nextInt(100000)}"
        val rootB = "build/test_scratch_b_${Random.nextInt(100000)}"

        val disks = listOf(
            ScratchDiskConfig(id = "dA", path = rootA, priority = 1, enabled = true),
            ScratchDiskConfig(id = "dB", path = rootB, priority = 2, enabled = true)
        )

        val manager = ScratchDiskManager(
            customScratchDisksProvider = { disks },
            customFallbackDirProvider = { "fallback" }
        )

        val existingSandbox = Path("$rootB/jobs/job-resumed-777/sandbox")
        SystemFileSystem.createDirectories(existingSandbox)

        try {
            // Even though rootA has higher priority (1), job-resumed-777 already has its sandbox on rootB
            val resolvedSandbox = manager.getJobSandboxDir("job-resumed-777")
            assertEquals(existingSandbox.toString().replace('\\', '/'), resolvedSandbox)
        } finally {
            deleteRecursively(Path(rootA))
            deleteRecursively(Path(rootB))
        }
    }

    @Test
    fun testDeleteJobSandbox() {
        val root = "build/test_scratch_del_${Random.nextInt(100000)}"
        val disks = listOf(ScratchDiskConfig(id = "d", path = root, priority = 1, enabled = true))
        val manager = ScratchDiskManager(
            customScratchDisksProvider = { disks },
            customFallbackDirProvider = { "fallback" }
        )

        val sandboxPath = Path("$root/jobs/job-del/sandbox")
        SystemFileSystem.createDirectories(sandboxPath)
        val dummyFile = Path(sandboxPath, "temp.dat")
        SystemFileSystem.sink(dummyFile).buffered().use { it.writeString("temp") }

        assertTrue(SystemFileSystem.exists(sandboxPath))
        try {
            manager.deleteJobSandbox("job-del")
            assertFalse(SystemFileSystem.exists(sandboxPath), "Sandbox directory should be deleted")
        } finally {
            deleteRecursively(Path(root))
        }
    }

    @Test
    fun testCleanOrphanSandboxesAcrossMultipleScratchDisks() = runTest {
        val root1 = "build/test_orphan_disk1_${Random.nextInt(100000)}"
        val root2 = "build/test_orphan_disk2_${Random.nextInt(100000)}"

        val disks = listOf(
            ScratchDiskConfig(id = "d1", path = root1, priority = 1, enabled = true),
            ScratchDiskConfig(id = "d2", path = root2, priority = 2, enabled = true)
        )

        val manager = ScratchDiskManager(
            customScratchDisksProvider = { disks },
            customFallbackDirProvider = { "build/fallback_unused" }
        )

        val activeJobSandbox = Path("$root1/jobs/active-job/sandbox")
        val orphanJob1Sandbox = Path("$root1/jobs/orphan-job-1/sandbox")
        val orphanJob2Sandbox = Path("$root2/jobs/orphan-job-2/sandbox")

        SystemFileSystem.createDirectories(activeJobSandbox)
        SystemFileSystem.createDirectories(orphanJob1Sandbox)
        SystemFileSystem.createDirectories(orphanJob2Sandbox)

        try {
            assertTrue(SystemFileSystem.exists(activeJobSandbox))
            assertTrue(SystemFileSystem.exists(orphanJob1Sandbox))
            assertTrue(SystemFileSystem.exists(orphanJob2Sandbox))

            val cleanupManager = SandboxCleanupManager()
            cleanupManager.cleanOrphanSandboxes(manager, setOf("active-job"))

            assertTrue(SystemFileSystem.exists(activeJobSandbox), "Active job sandbox should be preserved")
            assertFalse(SystemFileSystem.exists(orphanJob1Sandbox), "Orphan sandbox on disk 1 should be deleted")
            assertFalse(SystemFileSystem.exists(orphanJob2Sandbox), "Orphan sandbox on disk 2 should be deleted")
        } finally {
            deleteRecursively(Path(root1))
            deleteRecursively(Path(root2))
        }
    }

    @Test
    fun testGeneralSettingsSortedScratchDisks() {
        val general = GeneralSettings(
            scratchDisks = listOf(
                ScratchDiskConfig(id = "3", path = "/z_path", priority = 5),
                ScratchDiskConfig(id = "1", path = "/a_path", priority = 1),
                ScratchDiskConfig(id = "2", path = "/b_path", priority = 1)
            )
        )

        val sorted = general.sortedScratchDisks()
        assertEquals(3, sorted.size)
        assertEquals("/a_path", sorted[0].path)
        assertEquals(1, sorted[0].priority)
        assertEquals("/b_path", sorted[1].path)
        assertEquals(1, sorted[1].priority)
        assertEquals("/z_path", sorted[2].path)
        assertEquals(5, sorted[2].priority)
    }
}
