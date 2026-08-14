package org.wip.plugintoolkit.features.plugin.logic

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
