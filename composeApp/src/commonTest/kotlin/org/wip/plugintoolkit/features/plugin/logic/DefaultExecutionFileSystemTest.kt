package org.wip.plugintoolkit.features.plugin.logic

import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import org.wip.plugintoolkit.api.RelativePath
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultExecutionFileSystemTest {

    private val sandboxPath = "test_sandbox_dir"
    private lateinit var fileSystem: DefaultExecutionFileSystem

    @BeforeTest
    fun setup() {
        val path = Path(sandboxPath)
        if (SystemFileSystem.exists(path)) {
            // Cleanup from previous run just in case
            cleanup(path)
        }
        fileSystem = DefaultExecutionFileSystem(sandboxPath)
    }

    @AfterTest
    fun tearDown() {
        cleanup(Path(sandboxPath))
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
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    @Test
    fun testWriteAndReadTextFile() = runTest {
        val result = fileSystem.writeTextFile(RelativePath.from("test.txt").getOrThrow(), "Hello World")
        assertTrue(result.isSuccess)

        val content = fileSystem.readTextFile(RelativePath.from("test.txt").getOrThrow())
        assertEquals("Hello World", content)
    }

    @Test
    fun testExists() = runTest {
        assertFalse(fileSystem.exists(RelativePath.from("missing.txt").getOrThrow()))
        fileSystem.writeTextFile(RelativePath.from("found.txt").getOrThrow(), "content")
        assertTrue(fileSystem.exists(RelativePath.from("found.txt").getOrThrow()))
    }

    @Test
    fun testDeleteFile() = runTest {
        fileSystem.writeTextFile(RelativePath.from("todelete.txt").getOrThrow(), "content")
        assertTrue(fileSystem.exists(RelativePath.from("todelete.txt").getOrThrow()))

        val deleteResult = fileSystem.deleteFile(RelativePath.from("todelete.txt").getOrThrow())
        assertTrue(deleteResult.isSuccess)
        assertFalse(fileSystem.exists(RelativePath.from("todelete.txt").getOrThrow()))
    }

    @Test
    fun testListFiles() = runTest {
        fileSystem.writeTextFile(RelativePath.from("folder/file1.txt").getOrThrow(), "1")
        fileSystem.writeTextFile(RelativePath.from("folder/file2.txt").getOrThrow(), "2")

        val files = fileSystem.listFiles(RelativePath.from("folder").getOrThrow())
        assertEquals(2, files.size)
        assertTrue(files.contains("file1.txt"))
        assertTrue(files.contains("file2.txt"))
    }

    @Test
    fun testPathTraversalPrevention() = runTest {
        // Attempt to create a path outside the sandbox using ../
        val maliciousPathResult = RelativePath.from("../outside.txt")
        assertTrue(maliciousPathResult.isFailure, "RelativePath should prevent directory traversal")

        // Ensure it did not write outside the sandbox directory into the project root
        val outsidePath = Path("outside.txt")
        assertFalse(SystemFileSystem.exists(outsidePath), "File should not be written outside the sandbox")
    }
}
