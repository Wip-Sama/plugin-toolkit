package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import org.wip.plugintoolkit.core.theme.ToolkitTheme

enum class GroupOrientation { Horizontal, Vertical }

@Composable
fun rememberGroupedShape(
    index: Int,
    totalCount: Int,
    outerCorner: Dp = ToolkitTheme.dimensions.buttonGroupOuterCorner,
    innerCorner: Dp = ToolkitTheme.dimensions.buttonGroupInnerCorner,
    orientation: GroupOrientation = GroupOrientation.Horizontal
): Shape {
    return remember(index, totalCount, outerCorner, innerCorner, orientation) {
        computeGroupedShape(index, totalCount, outerCorner, innerCorner, orientation)
    }
}

fun computeGroupedShape(
    index: Int,
    totalCount: Int,
    outerCorner: Dp,
    innerCorner: Dp,
    orientation: GroupOrientation
): RoundedCornerShape {
    return when {
        totalCount <= 1 -> RoundedCornerShape(outerCorner)
        index == 0 -> when (orientation) {
            GroupOrientation.Horizontal -> RoundedCornerShape(
                topStart = outerCorner, bottomStart = outerCorner,
                topEnd = innerCorner, bottomEnd = innerCorner
            )
            GroupOrientation.Vertical -> RoundedCornerShape(
                topStart = outerCorner, topEnd = outerCorner,
                bottomStart = innerCorner, bottomEnd = innerCorner
            )
        }
        index == totalCount - 1 -> when (orientation) {
            GroupOrientation.Horizontal -> RoundedCornerShape(
                topStart = innerCorner, bottomStart = innerCorner,
                topEnd = outerCorner, bottomEnd = outerCorner
            )
            GroupOrientation.Vertical -> RoundedCornerShape(
                topStart = innerCorner, topEnd = innerCorner,
                bottomStart = outerCorner, bottomEnd = outerCorner
            )
        }
        else -> RoundedCornerShape(innerCorner)
    }
}
