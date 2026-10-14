package org.wip.plugintoolkit.features.plugin.sdui

import org.wip.plugintoolkit.api.sdui.NormalizedPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BoardMathTest {

    @Test
    fun testRdpSimplifyWithShortListReturnsUnmodified() {
        val empty = emptyList<NormalizedPoint>()
        assertEquals(empty, rdpSimplify(empty, 0.01f))

        val single = listOf(NormalizedPoint(0.1f, 0.1f))
        assertEquals(single, rdpSimplify(single, 0.01f))

        val pair = listOf(NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.9f, 0.9f))
        assertEquals(pair, rdpSimplify(pair, 0.01f))
    }

    @Test
    fun testRdpSimplifyRemovesCollinearPoints() {
        val line = listOf(
            NormalizedPoint(0.0f, 0.0f),
            NormalizedPoint(0.2f, 0.2f),
            NormalizedPoint(0.4f, 0.4f),
            NormalizedPoint(0.6f, 0.6f),
            NormalizedPoint(0.8f, 0.8f),
            NormalizedPoint(1.0f, 1.0f)
        )
        val simplified = rdpSimplify(line, epsilon = 0.01f)
        assertEquals(2, simplified.size)
        assertEquals(NormalizedPoint(0.0f, 0.0f), simplified.first())
        assertEquals(NormalizedPoint(1.0f, 1.0f), simplified.last())
    }

    @Test
    fun testRdpSimplifyPreservesSharpCorners() {
        val triangle = listOf(
            NormalizedPoint(0.0f, 0.0f),
            NormalizedPoint(0.5f, 0.8f),
            NormalizedPoint(1.0f, 0.0f)
        )
        val simplified = rdpSimplify(triangle, epsilon = 0.01f)
        assertEquals(3, simplified.size)
        assertEquals(NormalizedPoint(0.5f, 0.8f), simplified[1])
    }

    @Test
    fun testCatmullRomPathConstructionDoesNotThrow() {
        val emptyPath = createCatmullRomPath(emptyList(), 800f, 600f)
        assertTrue(emptyPath.isEmpty)

        val singlePointPath = createCatmullRomPath(listOf(NormalizedPoint(0.5f, 0.5f)), 800f, 600f)
        // MoveTo makes it non-empty or empty depending on compose path behavior, but must not crash
        assert(singlePointPath != null)

        val twoPointPath = createCatmullRomPath(
            listOf(NormalizedPoint(0.0f, 0.0f), NormalizedPoint(1.0f, 1.0f)),
            800f,
            600f
        )
        assert(twoPointPath != null)

        val multiPointPath = createCatmullRomPath(
            listOf(
                NormalizedPoint(0.1f, 0.1f),
                NormalizedPoint(0.2f, 0.4f),
                NormalizedPoint(0.5f, 0.3f),
                NormalizedPoint(0.8f, 0.9f)
            ),
            800f,
            600f
        )
        assert(multiPointPath != null)
    }
}
