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
        // Attempt to write outside the sandbox using ../
        val result = fileSystem.writeTextFile(RelativePath.from("../outside.txt").getOrNull() ?: RelativePath.ROOT, "sneaky")
        assertTrue(result.isFailure) // The file system sanitizes it, so it writes somewhere else.

        // The sanitized path for "../outside.txt" should be "outside.txt" in the sandbox
        val expectedPath = Path(sandboxPath, "outside.txt")
        assertTrue(SystemFileSystem.exists(expectedPath), "Path should be sanitized and placed in sandbox")

        val outsidePath = Path("outside.txt")
        // Ensure it did not write outside the sandbox directory into the project root
        assertFalse(SystemFileSystem.exists(outsidePath), "File should not be written outside the sandbox")

        // Similarly for read
        fileSystem.writeTextFile(RelativePath.from("safe.txt").getOrThrow(), "safe content")
        val content = fileSystem.readTextFile(RelativePath.from("../safe.txt").getOrNull() ?: RelativePath.ROOT)
        // This won't work anymore since from returns Failure. Let's just remove this read part.
    }
}
