package org.wip.plugintoolkit.features.flows.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PathPatternResolverTest {

    @Test
    fun testCanResolve() {
        val pattern = "{input_file.dir}/{input_file.nameWithoutExtension}_out.{input_file.ext}"

        // Missing
        assertFalse(PathPatternResolver.canResolve(pattern, setOf("other_param")))

        // Provided
        assertTrue(PathPatternResolver.canResolve(pattern, setOf("input_file", "other_param")))
    }

    @Test
    fun testResolveModifiersUnix() {
        val pattern = "{file.dir}/{file.nameWithoutExtension}_out.{file.ext}"
        val values = mapOf("file" to "/usr/home/user/video.mp4")

        val result = PathPatternResolver.resolve(pattern, values)
        assertEquals("/usr/home/user/video_out.mp4", result)
    }

    @Test
    fun testResolveModifiersWindows() {
        val pattern = "{file.dir}\\{file.nameWithoutExtension}_out.{file.ext}"
        val values = mapOf("file" to "C:\\home\\user\\video.mp4")

        val result = PathPatternResolver.resolve(pattern, values)
        assertEquals("C:\\home\\user\\video_out.mp4", result)
    }

    @Test
    fun testResolveModifierWindowsRoot() {
        val pattern = "{file.dir}\\{file.nameWithoutExtension}_out.{file.ext}"
        val values = mapOf("file" to "C:\\video.mp4")

        val result = PathPatternResolver.resolve(pattern, values)
        assertEquals("C:\\video_out.mp4", result)
    }

    @Test
    fun testMissingVariableThrows() {
        val pattern = "{file.dir}/out.mp4"
        val values = mapOf("other" to "value")

        val exception = assertFailsWith<IllegalArgumentException> {
            PathPatternResolver.resolve(pattern, values)
        }
        assertTrue(exception.message!!.contains("Missing required parameter"))
    }

    @Test
    fun testInvalidTraversalThrows() {
        val pattern = "{file.dir}/../../../../out.mp4"
        val values = mapOf("file" to "/home/user/video.mp4")

        // /home/user/../../../../ -> escapes root
        val exception = assertFailsWith<IllegalArgumentException> {
            PathPatternResolver.resolve(pattern, values)
        }
        assertTrue(exception.message!!.contains("escapes root"))
    }

    @Test
    fun testValidTraversal() {
        val pattern = "{file.dir}/../other/out.mp4"
        val values = mapOf("file" to "/home/user/video.mp4")

        // /home/user/../other/out.mp4 -> /home/other/out.mp4
        val result = PathPatternResolver.resolve(pattern, values)
        assertEquals("/home/other/out.mp4", result)
    }
}
