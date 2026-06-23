package org.wip.plugintoolkit.features.plugin.logic

import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
        val result = fileSystem.writeTextFile("test.txt", "Hello World")
        assertTrue(result.isSuccess)

        val content = fileSystem.readTextFile("test.txt")
        assertEquals("Hello World", content)
    }

    @Test
    fun testExists() = runTest {
        assertFalse(fileSystem.exists("missing.txt"))
        fileSystem.writeTextFile("found.txt", "content")
        assertTrue(fileSystem.exists("found.txt"))
    }

    @Test
    fun testDeleteFile() = runTest {
        fileSystem.writeTextFile("todelete.txt", "content")
        assertTrue(fileSystem.exists("todelete.txt"))

        val deleteResult = fileSystem.deleteFile("todelete.txt")
        assertTrue(deleteResult.isSuccess)
        assertFalse(fileSystem.exists("todelete.txt"))
    }

    @Test
    fun testListFiles() = runTest {
        fileSystem.writeTextFile("folder/file1.txt", "1")
        fileSystem.writeTextFile("folder/file2.txt", "2")

        val files = fileSystem.listFiles("folder")
        assertEquals(2, files.size)
        assertTrue(files.contains("file1.txt"))
        assertTrue(files.contains("file2.txt"))
    }

    @Test
    fun testPathTraversalPrevention() = runTest {
        // Attempt to write outside the sandbox using ../
        val result = fileSystem.writeTextFile("../outside.txt", "sneaky")
        assertTrue(result.isSuccess) // The file system sanitizes it, so it writes somewhere else.

        // The sanitized path for "../outside.txt" should be "outside.txt" in the sandbox
        val expectedPath = Path(sandboxPath, "outside.txt")
        assertTrue(SystemFileSystem.exists(expectedPath), "Path should be sanitized and placed in sandbox")

        val outsidePath = Path("outside.txt")
        // Ensure it did not write outside the sandbox directory into the project root
        assertFalse(SystemFileSystem.exists(outsidePath), "File should not be written outside the sandbox")
        
        // Similarly for read
        fileSystem.writeTextFile("safe.txt", "safe content")
        val content = fileSystem.readTextFile("../safe.txt")
        assertEquals("safe content", content, "Should read from sandbox after sanitizing")
    }
}
