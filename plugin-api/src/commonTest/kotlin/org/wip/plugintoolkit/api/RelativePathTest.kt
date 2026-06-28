package org.wip.plugintoolkit.api

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class RelativePathTest {

    @Test
    fun testValidRelativePaths() {
        assertTrue("foo.txt".toRelativePath().isSuccess)
        assertTrue("dir/foo.txt".toRelativePath().isSuccess)
        assertTrue("dir/subdir/foo.txt".toRelativePath().isSuccess)
        assertTrue("foo".toRelativePath().isSuccess)
        assertTrue("".toRelativePath().isSuccess)
    }

    @Test
    fun testAbsolutePathsBlocked() {
        assertFalse("/foo.txt".toRelativePath().isSuccess, "Should block leading slash")
        assertFalse("\\foo.txt".toRelativePath().isSuccess, "Should block leading backslash")
        assertFalse("~/.ssh".toRelativePath().isSuccess, "Should block home directory expansion")
        assertFalse("C:\\Windows\\System32".toRelativePath().isSuccess, "Should block Windows drive paths")
        assertFalse("D:/data".toRelativePath().isSuccess, "Should block Windows drive paths with forward slash")
    }

    @Test
    fun testTraversalBlocked() {
        assertFalse("../foo.txt".toRelativePath().isSuccess, "Should block starting traversing up")
        assertFalse("dir/../../foo.txt".toRelativePath().isSuccess, "Should block mid-traversing up")
        assertFalse("..".toRelativePath().isSuccess, "Should block bare ..")
        assertFalse("dir/..".toRelativePath().isSuccess, "Should block trailing ..")
        
        assertFalse("..\\foo.txt".toRelativePath().isSuccess, "Should block Windows traversal up")
        assertFalse("dir\\..\\..\\foo.txt".toRelativePath().isSuccess, "Should block Windows mid-traversal")
    }

    @Test
    fun testNullBytesBlocked() {
        assertFalse("foo.txt\u0000.sh".toRelativePath().isSuccess, "Should block null bytes")
    }

    @Test
    fun testNormalization() {
        val path = "  foo/bar.txt  ".toRelativePath()
        assertTrue(path.isSuccess)
        assertEquals("foo/bar.txt", path.getOrNull()?.value, "Should trim whitespace")
    }
}
