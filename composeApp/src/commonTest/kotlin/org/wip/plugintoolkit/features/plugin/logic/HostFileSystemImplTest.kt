package org.wip.plugintoolkit.features.plugin.logic

import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.wip.plugintoolkit.features.job.logic.SystemPathSecurity
import org.wip.plugintoolkit.features.settings.model.FileAccessMode
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HostFileSystemImplTest {

    private val allowedDir = File("test_host_dir").canonicalPath

    @BeforeTest
    fun setup() {
        val path = Path(allowedDir)
        if (!SystemFileSystem.exists(path)) {
            SystemFileSystem.createDirectories(path)
        }
    }

    @AfterTest
    fun tearDown() {
        cleanup(Path(allowedDir))
    }

    private fun cleanup(path: Path) {
        if (SystemFileSystem.exists(path)) {
            val metadata = SystemFileSystem.metadataOrNull(path)
            if (metadata?.isDirectory == true) {
                SystemFileSystem.list(path).forEach { child ->
                    cleanup(child)
                }
            }
            try {
                SystemFileSystem.delete(path)
            } catch (_: Exception) {}
        }
    }

    @Test
    fun testOverwriteRequiresDestructiveAllowed() = runTest {
        val filePath = File(allowedDir, "existing_file.txt").canonicalPath
        File(filePath).writeText("initial data")

        val nonDestructiveFs = HostFileSystemImpl(allowedPaths = listOf(allowedDir), isDestructiveAllowed = false)

        val writeResult = nonDestructiveFs.writeFile(filePath, "new data".toByteArray())
        assertTrue(writeResult.isFailure, "Overwrite should fail when isDestructiveAllowed is false")

        val deleteResult = nonDestructiveFs.deleteFile(filePath)
        assertTrue(deleteResult.isFailure, "Delete should fail when isDestructiveAllowed is false")

        val destructiveFs = HostFileSystemImpl(allowedPaths = listOf(allowedDir), isDestructiveAllowed = true)
        val successResult = destructiveFs.writeFile(filePath, "new data".toByteArray())
        assertTrue(successResult.isSuccess, "Overwrite should succeed when isDestructiveAllowed is true")
    }

    @Test
    fun testSystemPathSecurityBlacklist() {
        assertFalse(SystemPathSecurity.isPathAllowed("C:\\Windows\\System32\\cmd.exe", FileAccessMode.Blacklist))
        assertFalse(SystemPathSecurity.isPathAllowed("/etc/passwd", FileAccessMode.Blacklist))
        assertFalse(SystemPathSecurity.isPathAllowed("/usr/bin/bash", FileAccessMode.Blacklist))
        assertTrue(SystemPathSecurity.isPathAllowed(allowedDir, FileAccessMode.Blacklist))
    }

    @Test
    fun testSystemPathSecurityCustomBlacklist() {
        val customBlacklist = listOf(allowedDir)
        assertFalse(
            SystemPathSecurity.isPathAllowed(
                allowedDir,
                FileAccessMode.Blacklist,
                customBlacklist = customBlacklist
            ),
            "Custom blacklisted directory should be denied"
        )
    }

    @Test
    fun testSystemPathSecurityWhitelist() {
        assertFalse(SystemPathSecurity.isPathAllowed("C:\\Users\\test", FileAccessMode.Whitelist, customWhitelist = listOf(allowedDir)))
        assertTrue(SystemPathSecurity.isPathAllowed(allowedDir, FileAccessMode.Whitelist, customWhitelist = listOf(allowedDir)))
    }

    @Test
    fun testSystemPathSecurityUnrestricted() {
        assertTrue(
            SystemPathSecurity.isPathAllowed("C:\\Windows\\System32\\cmd.exe", FileAccessMode.Unrestricted),
            "Unrestricted mode should allow system paths"
        )
        assertTrue(
            SystemPathSecurity.isPathAllowed("/etc/passwd", FileAccessMode.Unrestricted),
            "Unrestricted mode should allow /etc paths"
        )
        assertTrue(
            SystemPathSecurity.isPathAllowed(allowedDir, FileAccessMode.Unrestricted),
            "Unrestricted mode should allow user paths"
        )
    }
}
