package org.wip.plugintoolkit.features.flows.ui.canvas

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset as ModelOffset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoardGestureModifiersTest {

    private fun createTestNode(id: Long, position: ModelOffset): Node.SystemNode {
        return Node.SystemNode(
            id = id,
            position = position,
            title = "Test Node $id",
            systemAction = "action",
            inputs = emptyList(),
            outputs = emptyList()
        )
    }

    @Test
    fun testPointerOverNodeReturnsTrueWhenInsideBounds() {
        val node = createTestNode(1L, ModelOffset(100f, 100f))
        val nodeSizes = mapOf(1L to IntSize(200, 150))

        val isOver = isPointerOverAnyNode(
            screenPos = Offset(150f, 150f),
            nodes = listOf(node),
            nodeSizes = nodeSizes,
            scale = 1f,
            offset = Offset.Zero,
            defaultNodeWidthPx = 200f,
            densityValue = 1f
        )

        assertTrue(isOver)
    }

    @Test
    fun testPointerOverNodeRespectsScaleAndOffset() {
        val node = createTestNode(1L, ModelOffset(100f, 100f))
        val nodeSizes = mapOf(1L to IntSize(200, 150))
        // Screen pos = (boardPos * scale) + offset = (150 * 2) + 50 = 350
        val screenPos = Offset(350f, 350f)

        val isOver = isPointerOverAnyNode(
            screenPos = screenPos,
            nodes = listOf(node),
            nodeSizes = nodeSizes,
            scale = 2f,
            offset = Offset(50f, 50f),
            defaultNodeWidthPx = 200f,
            densityValue = 1f
        )

        assertTrue(isOver)
    }

    @Test
    fun testPointerOverNodeReturnsFalseWhenOutsideBounds() {
        val node = createTestNode(1L, ModelOffset(100f, 100f))
        val nodeSizes = mapOf(1L to IntSize(200, 150))

        val isOver = isPointerOverAnyNode(
            screenPos = Offset(50f, 50f),
            nodes = listOf(node),
            nodeSizes = nodeSizes,
            scale = 1f,
            offset = Offset.Zero,
            defaultNodeWidthPx = 200f,
            densityValue = 1f
        )

        assertFalse(isOver)
    }

    @Test
    fun testPointerOverNodeReturnsFalseWhenNodeIsInCollapsedGroup() {
        val node = createTestNode(1L, ModelOffset(100f, 100f))
        val nodeSizes = mapOf(1L to IntSize(200, 150))

        val isOver = isPointerOverAnyNode(
            screenPos = Offset(150f, 150f),
            nodes = listOf(node),
            nodeSizes = nodeSizes,
            scale = 1f,
            offset = Offset.Zero,
            defaultNodeWidthPx = 200f,
            densityValue = 1f,
            collapsedGroupNodeIds = setOf(1L)
        )

        assertFalse(isOver)
    }

    @Test
    fun testPointerOverNodeReturnsTrueForHoveredNodeWithinBounds() {
        val node = createTestNode(1L, ModelOffset(100f, 100f))
        val nodeSizes = mapOf(1L to IntSize(200, 150))

        val isOver = isPointerOverAnyNode(
            screenPos = Offset(150f, 150f),
            nodes = listOf(node),
            nodeSizes = nodeSizes,
            scale = 1f,
            offset = Offset.Zero,
            defaultNodeWidthPx = 200f,
            densityValue = 1f,
            hoveredNodeId = 1L
        )

        assertTrue(isOver)
    }

    @Test
    fun testPointerOverNodeReturnsFalseWhenNodesEmpty() {
        val isOver = isPointerOverAnyNode(
            screenPos = Offset(100f, 100f),
            nodes = emptyList(),
            nodeSizes = emptyMap(),
            scale = 1f,
            offset = Offset.Zero,
            defaultNodeWidthPx = 200f,
            densityValue = 1f
        )

        assertFalse(isOver)
    }
}
