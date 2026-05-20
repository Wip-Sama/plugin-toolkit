package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.wip.plugintoolkit.core.theme.ToolkitTheme

@Composable
fun ToolkitButtonGroup(
    modifier: Modifier = Modifier,
    content: @Composable ToolkitButtonGroupScope.() -> Unit
) {
    val outerCorner = ToolkitTheme.dimensions.buttonGroupOuterCorner
    val innerCorner = ToolkitTheme.dimensions.buttonGroupInnerCorner
    val gap = ToolkitTheme.dimensions.buttonGroupGap

    val scope = ToolkitButtonGroupScopeImpl()
    scope.content()

    val children = scope.children
    if (children.isEmpty()) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        children.forEachIndexed { index, childContent ->
            val isFirst = index == 0
            val isLast = index == children.size - 1

            val shape = when {
                children.size == 1 -> RoundedCornerShape(outerCorner)
                isFirst -> RoundedCornerShape(
                    topStart = outerCorner,
                    bottomStart = outerCorner,
                    topEnd = innerCorner,
                    bottomEnd = innerCorner
                )
                isLast -> RoundedCornerShape(
                    topStart = innerCorner,
                    bottomStart = innerCorner,
                    topEnd = outerCorner,
                    bottomEnd = outerCorner
                )
                else -> RoundedCornerShape(innerCorner)
            }

            childContent(shape, Modifier)
        }
    }
}

interface ToolkitButtonGroupScope {
    fun item(content: @Composable (shape: CornerBasedShape, modifier: Modifier) -> Unit)
}

private class ToolkitButtonGroupScopeImpl : ToolkitButtonGroupScope {
    val children = mutableListOf<@Composable (shape: CornerBasedShape, modifier: Modifier) -> Unit>()

    override fun item(content: @Composable (shape: CornerBasedShape, modifier: Modifier) -> Unit) {
        children.add(content)
    }
}
