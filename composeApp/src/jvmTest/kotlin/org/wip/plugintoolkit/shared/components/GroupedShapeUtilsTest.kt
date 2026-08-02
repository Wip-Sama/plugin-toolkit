package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class GroupedShapeUtilsTest {

    @Test
    fun testSingleItemShape() {
        val shape = computeGroupedShape(
            index = 0,
            totalCount = 1,
            outerCorner = 20.dp,
            innerCorner = 4.dp,
            orientation = GroupOrientation.Horizontal
        )
        assertEquals(CornerSize(20.dp), shape.topStart)
        assertEquals(CornerSize(20.dp), shape.topEnd)
        assertEquals(CornerSize(20.dp), shape.bottomStart)
        assertEquals(CornerSize(20.dp), shape.bottomEnd)
    }

    @Test
    fun testHorizontalGroupShapes() {
        val firstShape = computeGroupedShape(
            index = 0,
            totalCount = 3,
            outerCorner = 20.dp,
            innerCorner = 4.dp,
            orientation = GroupOrientation.Horizontal
        )
        assertEquals(CornerSize(20.dp), firstShape.topStart)
        assertEquals(CornerSize(20.dp), firstShape.bottomStart)
        assertEquals(CornerSize(4.dp), firstShape.topEnd)
        assertEquals(CornerSize(4.dp), firstShape.bottomEnd)

        val middleShape = computeGroupedShape(
            index = 1,
            totalCount = 3,
            outerCorner = 20.dp,
            innerCorner = 4.dp,
            orientation = GroupOrientation.Horizontal
        )
        assertEquals(CornerSize(4.dp), middleShape.topStart)
        assertEquals(CornerSize(4.dp), middleShape.bottomStart)
        assertEquals(CornerSize(4.dp), middleShape.topEnd)
        assertEquals(CornerSize(4.dp), middleShape.bottomEnd)

        val lastShape = computeGroupedShape(
            index = 2,
            totalCount = 3,
            outerCorner = 20.dp,
            innerCorner = 4.dp,
            orientation = GroupOrientation.Horizontal
        )
        assertEquals(CornerSize(4.dp), lastShape.topStart)
        assertEquals(CornerSize(4.dp), lastShape.bottomStart)
        assertEquals(CornerSize(20.dp), lastShape.topEnd)
        assertEquals(CornerSize(20.dp), lastShape.bottomEnd)
    }

    @Test
    fun testVerticalGroupShapes() {
        val firstShape = computeGroupedShape(
            index = 0,
            totalCount = 3,
            outerCorner = 20.dp,
            innerCorner = 4.dp,
            orientation = GroupOrientation.Vertical
        )
        assertEquals(CornerSize(20.dp), firstShape.topStart)
        assertEquals(CornerSize(20.dp), firstShape.topEnd)
        assertEquals(CornerSize(4.dp), firstShape.bottomStart)
        assertEquals(CornerSize(4.dp), firstShape.bottomEnd)

        val middleShape = computeGroupedShape(
            index = 1,
            totalCount = 3,
            outerCorner = 20.dp,
            innerCorner = 4.dp,
            orientation = GroupOrientation.Vertical
        )
        assertEquals(CornerSize(4.dp), middleShape.topStart)
        assertEquals(CornerSize(4.dp), middleShape.topEnd)
        assertEquals(CornerSize(4.dp), middleShape.bottomStart)
        assertEquals(CornerSize(4.dp), middleShape.bottomEnd)

        val lastShape = computeGroupedShape(
            index = 2,
            totalCount = 3,
            outerCorner = 20.dp,
            innerCorner = 4.dp,
            orientation = GroupOrientation.Vertical
        )
        assertEquals(CornerSize(4.dp), lastShape.topStart)
        assertEquals(CornerSize(4.dp), lastShape.topEnd)
        assertEquals(CornerSize(20.dp), lastShape.bottomStart)
        assertEquals(CornerSize(20.dp), lastShape.bottomEnd)
    }
}
