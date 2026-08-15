package org.wip.plugintoolkit.features.plugin.logic

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.wip.plugintoolkit.api.RelativePath
import org.wip.plugintoolkit.api.ScopedFileSystem
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SandboxFileSystemSecurityTest {
    private lateinit var testRoot: Path

    @BeforeTest
    fun setUp() {
        testRoot = Files.createTempDirectory("plugin-toolkit-sandbox-")
    }

    @AfterTest
    fun tearDown() {
        if (!Files.exists(testRoot)) return
        Files.walkFileTree(testRoot, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, error: java.io.IOException?): FileVisitResult {
                if (error != null) throw error
                Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }

    @Test
    fun rootAliasesCannotDeleteAnySandboxRoot() = runTest {
        val alias = RelativePath.from("././").getOrThrow()
        val execution = DefaultExecutionFileSystem(testRoot.resolve("execution").toString())
        val pluginInstall = testRoot.resolve("plugin").toString()
        val plugin = DefaultPluginFileSystem(pluginInstall)
        val cache = DefaultPluginFileSystem.createCacheOnly(pluginInstall)

        assertTrue(execution.deleteDirectory(alias, recursive = true).isFailure)
        assertTrue(plugin.deleteDirectory(alias, recursive = true).isFailure)
        assertTrue(cache.deleteDirectory(alias, recursive = true).isFailure)
        assertTrue(Files.isDirectory(testRoot.resolve("execution")))
        assertTrue(Files.isDirectory(testRoot.resolve("plugin/files")))
        assertTrue(Files.isDirectory(testRoot.resolve("plugin/cache")))
    }

    @Test
    fun executionSandboxCannotReadThroughSymlinkAndDoesNotFollowItOnDelete() = runTest {
        val sandbox = testRoot.resolve("execution")
        val outside = createOutsideSecret()
        val fileSystem = DefaultExecutionFileSystem(sandbox.toString())

        verifySymlinkIsContained(fileSystem, sandbox, outside)
    }

    @Test
    fun pluginFilesCannotReadThroughSymlinkAndDoNotFollowItOnDelete() = runTest {
        val install = testRoot.resolve("plugin")
        val outside = createOutsideSecret()
        val fileSystem = DefaultPluginFileSystem(install.toString())

        verifySymlinkIsContained(fileSystem, install.resolve("files"), outside)
    }

    @Test
    fun pluginCacheCannotReadThroughSymlinkAndDoesNotFollowItOnDelete() = runTest {
        val install = testRoot.resolve("plugin")
        val outside = createOutsideSecret()
        val fileSystem = DefaultPluginFileSystem.createCacheOnly(install.toString())

        verifySymlinkIsContained(fileSystem, install.resolve("cache"), outside)
    }

    @Test
    fun cacheOnlyVariantListsAndExtractsResourcesInsideCache() = runTest {
        val install = testRoot.resolve("plugin")
        Files.createDirectories(install)
        val jar = install.resolve("plugin.jar")
        JarOutputStream(Files.newOutputStream(jar)).use { output ->
            output.putNextEntry(JarEntry("assets/example.txt"))
            output.write("resource".encodeToByteArray())
            output.closeEntry()
        }
        val fileSystem = DefaultPluginFileSystem.createCacheOnly(install.toString(), jar.toString())
        val target = RelativePath.from("nested/example.txt").getOrThrow()

        assertTrue(fileSystem.extractResource("assets/example.txt", target).isSuccess)
        assertEquals("resource", fileSystem.readTextFile(target))
        assertEquals(listOf("example.txt"), fileSystem.listFiles(RelativePath.from("nested").getOrThrow()))
        assertTrue(Files.notExists(install.resolve("files/nested/example.txt")))
    }

    @Test
    fun cacheOnlyCompoundAndStreamOperationsNeverTouchPersistentFiles() = runTest {
        val install = testRoot.resolve("plugin")
        val fileSystem = DefaultPluginFileSystem.createCacheOnly(install.toString())
        val source = RelativePath.from("source.bin").getOrThrow()
        val copied = RelativePath.from("copied.bin").getOrThrow()
        val moved = RelativePath.from("moved.bin").getOrThrow()
        val streamed = RelativePath.from("streamed.bin").getOrThrow()

        Files.writeString(install.resolve("files/source.bin"), "persistent")
        assertTrue(fileSystem.writeFile(source, "cache".encodeToByteArray()).isSuccess)

        assertTrue(fileSystem.copyFile(source, copied).isSuccess)
        assertEquals("cache", Files.readString(install.resolve("cache/copied.bin")))
        assertTrue(Files.notExists(install.resolve("files/copied.bin")))

        assertTrue(fileSystem.moveFile(source, moved).isSuccess)
        assertTrue(Files.notExists(install.resolve("cache/source.bin")))
        assertEquals("cache", Files.readString(install.resolve("cache/moved.bin")))
        assertEquals("persistent", Files.readString(install.resolve("files/source.bin")))
        assertTrue(Files.notExists(install.resolve("files/moved.bin")))

        assertTrue(
            fileSystem.writeStream(
                streamed,
                flowOf("stream-".encodeToByteArray(), "cache".encodeToByteArray())
            ).isSuccess
        )
        val chunks = mutableListOf<ByteArray>()
        fileSystem.readStream(streamed).collect { chunks.add(it) }
        assertEquals("stream-cache", chunks.flatMap { it.asIterable() }.toByteArray().decodeToString())
        assertTrue(Files.notExists(install.resolve("files/streamed.bin")))
    }

    @Test
    fun missingSandboxRootUsesReadSemanticsAndRejectsWrites() = runTest {
        val install = testRoot.resolve("plugin")
        val fileSystem = DefaultPluginFileSystem(install.toString())
        val file = RelativePath.from("missing.txt").getOrThrow()
        Files.delete(install.resolve("files"))

        assertNull(fileSystem.readFile(file))
        assertNull(fileSystem.readTextFile(file))
        assertFalse(fileSystem.exists(file))
        assertEquals(emptyList(), fileSystem.listFiles())
        assertTrue(fileSystem.writeTextFile(file, "data").isFailure)
    }

    private fun createOutsideSecret(): Path {
        val outside = Files.createDirectories(testRoot.resolve("outside"))
        Files.writeString(outside.resolve("secret.txt"), "must survive")
        return outside
    }

    private suspend fun verifySymlinkIsContained(
        fileSystem: ScopedFileSystem,
        sandbox: Path,
        outside: Path
    ) {
        val nested = Files.createDirectories(sandbox.resolve("nested"))
        Files.createSymbolicLink(nested.resolve("escape"), outside)
        val escapedFile = RelativePath.from("nested/escape/secret.txt").getOrThrow()

        assertFailsWith<SecurityException> { fileSystem.readTextFile(escapedFile) }
        assertTrue(fileSystem.deleteDirectory(RelativePath.from("nested").getOrThrow(), recursive = true).isSuccess)
        assertTrue(Files.readString(outside.resolve("secret.txt")) == "must survive")
    }
}
