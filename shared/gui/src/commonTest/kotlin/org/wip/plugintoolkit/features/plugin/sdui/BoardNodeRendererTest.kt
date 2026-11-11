package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.test.runTest
import org.wip.plugintoolkit.api.PluginFileSystem
import org.wip.plugintoolkit.api.RelativePath
import org.wip.plugintoolkit.api.sdui.AssetReference
import org.wip.plugintoolkit.api.sdui.NormalizedPoint
import org.wip.plugintoolkit.api.sdui.NormalizedSize
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BoardNodeRendererTest {

    @Test
    fun testCatmullRomPathNeverOvershootsLeftOrTopEdgeOnRapidDrag() {
        val width = 800f
        val height = 600f
        // Rapid drag from inside the board (0.85, 0.75) straight to the left/top edge (0.0, 0.0)
        val points = listOf(
            NormalizedPoint(0.85f, 0.75f),
            NormalizedPoint(0.0f, 0.0f),
            NormalizedPoint(0.0f, 0.1f),
            NormalizedPoint(0.0f, 0.5f)
        )

        val path = createCatmullRomPath(points, width, height)
        val bounds = path.getBounds()

        assertTrue(bounds.left >= 0f, "Expected path left bound >= 0f, but was ${bounds.left}")
        assertTrue(bounds.top >= 0f, "Expected path top bound >= 0f, but was ${bounds.top}")
        assertTrue(bounds.right <= width, "Expected path right bound <= $width, but was ${bounds.right}")
        assertTrue(bounds.bottom <= height, "Expected path bottom bound <= $height, but was ${bounds.bottom}")
    }

    @Test
    fun testCatmullRomPathNeverOvershootsRightOrBottomEdgeOnRapidDrag() {
        val width = 1000f
        val height = 700f
        // Rapid drag from (0.1, 0.1) to (1.0, 1.0)
        val points = listOf(
            NormalizedPoint(0.1f, 0.1f),
            NormalizedPoint(1.0f, 1.0f),
            NormalizedPoint(1.0f, 0.9f),
            NormalizedPoint(0.95f, 1.0f)
        )

        val path = createCatmullRomPath(points, width, height)
        val bounds = path.getBounds()

        assertTrue(bounds.left >= 0f, "Expected path left bound >= 0f, but was ${bounds.left}")
        assertTrue(bounds.top >= 0f, "Expected path top bound >= 0f, but was ${bounds.top}")
        assertTrue(bounds.right <= width, "Expected path right bound <= $width, but was ${bounds.right}")
        assertTrue(bounds.bottom <= height, "Expected path bottom bound <= $height, but was ${bounds.bottom}")
    }

    @Test
    fun testCatmullRomPathClampsOutOfRangeInputCoordinates() {
        val width = 500f
        val height = 400f
        val points = listOf(
            NormalizedPoint(-0.5f, -0.2f),
            NormalizedPoint(0.5f, 0.5f),
            NormalizedPoint(1.4f, 1.8f)
        )

        val path = createCatmullRomPath(points, width, height)
        val bounds = path.getBounds()

        assertTrue(bounds.left >= 0f, "Expected clamped left bound >= 0f, got ${bounds.left}")
        assertTrue(bounds.top >= 0f, "Expected clamped top bound >= 0f, got ${bounds.top}")
        assertTrue(bounds.right <= width, "Expected clamped right bound <= $width, got ${bounds.right}")
        assertTrue(bounds.bottom <= height, "Expected clamped bottom bound <= $height, got ${bounds.bottom}")
    }

    @Test
    fun testRdpSimplifyReducesCollinearPoints() {
        val points = listOf(
            NormalizedPoint(0.1f, 0.1f),
            NormalizedPoint(0.2f, 0.2f),
            NormalizedPoint(0.3f, 0.3f),
            NormalizedPoint(0.4f, 0.4f),
            NormalizedPoint(0.5f, 0.5f)
        )
        val simplified = rdpSimplify(points, epsilon = 0.002f)
        assertEquals(2, simplified.size)
        assertEquals(NormalizedPoint(0.1f, 0.1f), simplified.first())
        assertEquals(NormalizedPoint(0.5f, 0.5f), simplified.last())
    }

    @Test
    fun testSnapPointAndSizeToGrid() {
        val rawPoint = NormalizedPoint(0.123f, 0.479f)
        val snapped = snapPointToGrid(rawPoint, snapToGrid = true, gridStep = 0.05f)
        assertTrue(abs(snapped.x - 0.10f) < 0.001f)
        assertTrue(abs(snapped.y - 0.50f) < 0.001f)

        val unsnapped = snapPointToGrid(rawPoint, snapToGrid = false, gridStep = 0.05f)
        assertEquals(rawPoint, unsnapped)

        val rawSize = NormalizedSize(0.23f, 0.005f)
        val snappedSize = snapSizeToGrid(rawSize, snapToGrid = true, gridStep = 0.05f)
        assertTrue(abs(snappedSize.width - 0.25f) < 0.001f)
        assertTrue(snappedSize.height >= 0.02f)
    }

    @Test
    fun testComputeEffectivePositionAndSizeWithDragAndResizeDeltas() {
        val basePos = NormalizedPoint(0.2f, 0.3f)
        val movedPos = computeEffectivePosition(
            basePos = basePos,
            deltaPx = Offset(100f, -60f),
            boardWidthPx = 1000f,
            boardHeightPx = 600f,
            snapToGrid = false,
            gridStep = 0.05f
        )
        assertTrue(abs(movedPos.x - 0.3f) < 0.001f)
        assertTrue(abs(movedPos.y - 0.2f) < 0.001f)

        val baseSize = NormalizedSize(0.25f, 0.20f)
        val resized = computeEffectiveSize(
            baseSize = baseSize,
            deltaPx = Offset(150f, 120f),
            boardWidthPx = 1000f,
            boardHeightPx = 600f,
            snapToGrid = false,
            gridStep = 0.05f
        )
        assertTrue(abs(resized.width - 0.40f) < 0.001f)
        assertTrue(abs(resized.height - 0.40f) < 0.001f)
    }

    @Test
    fun testComputeZoomAroundPointKeepsCursorAnchoredAndClampsScale() {
        val cursor = Offset(400f, 300f)
        val (zoomedInScale, zoomedInPan) = computeZoomAroundPoint(
            currentScale = 1.0f,
            currentPan = Offset.Zero,
            scrollDeltaY = -2.0f, // scroll up -> zoom in
            cursorPos = cursor,
            minScale = 0.5f,
            maxScale = 3.0f
        )
        assertTrue(zoomedInScale > 1.0f)
        // Board point under cursor before zoom: (400 - 0)/1 = 400
        // Board point under cursor after zoom: (400 - zoomedInPan.x)/zoomedInScale
        val boardXAfter = (cursor.x - zoomedInPan.x) / zoomedInScale
        val boardYAfter = (cursor.y - zoomedInPan.y) / zoomedInScale
        assertTrue(abs(boardXAfter - 400f) < 0.01f)
        assertTrue(abs(boardYAfter - 300f) < 0.01f)
    }

    private class FakePluginFileSystem(initialFiles: Map<String, ByteArray>) : PluginFileSystem {
        private val files = initialFiles.toMutableMap()
        override suspend fun extractResource(resourcePath: String, targetRelativePath: RelativePath): Result<Unit> =
            Result.success(Unit)
        override suspend fun readTextFile(relativePath: RelativePath): String? =
            files[relativePath.value]?.decodeToString()
        override suspend fun writeTextFile(relativePath: RelativePath, text: String): Result<Unit> {
            files[relativePath.value] = text.encodeToByteArray()
            return Result.success(Unit)
        }
        override suspend fun readFile(relativePath: RelativePath): ByteArray? = files[relativePath.value]
        override suspend fun writeFile(relativePath: RelativePath, data: ByteArray): Result<Unit> {
            files[relativePath.value] = data
            return Result.success(Unit)
        }
        override suspend fun exists(relativePath: RelativePath): Boolean = relativePath.value in files
        override suspend fun listFiles(relativePath: RelativePath): List<String> = files.keys.toList()
        override suspend fun deleteFile(relativePath: RelativePath): Result<Unit> {
            files.remove(relativePath.value)
            return Result.success(Unit)
        }
        override fun getBasePath(): String = "/fake"
    }

    @Test
    fun testResolveAssetBytesForRelativeCachedAndDataUri() = runTest {
        val fakeFs = FakePluginFileSystem(
            mapOf("images/panel1.png" to byteArrayOf(1, 2, 3, 4))
        )
        val fakeCacheFs = FakePluginFileSystem(
            mapOf("cached_thumb.png" to byteArrayOf(9, 8, 7))
        )

        val relBytes = resolveAssetBytes(AssetReference.Relative("images/panel1.png"), fakeFs, fakeCacheFs)
        assertNotNull(relBytes)
        assertContentEquals(byteArrayOf(1, 2, 3, 4), relBytes)

        val cachedBytes = resolveAssetBytes(AssetReference.Cached("cached_thumb.png"), fakeFs, fakeCacheFs)
        assertNotNull(cachedBytes)
        assertContentEquals(byteArrayOf(9, 8, 7), cachedBytes)

        // Base64 for "Hello" is "SGVsbG8="
        val dataUriBytes = resolveAssetBytes(
            AssetReference.Uri("data:text/plain;base64,SGVsbG8="),
            fakeFs,
            fakeCacheFs
        )
        assertNotNull(dataUriBytes)
        assertEquals("Hello", dataUriBytes.decodeToString())
    }
}
