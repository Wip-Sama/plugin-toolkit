package org.wip.plugintoolkit.shared.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import org.wip.plugintoolkit.core.theme.ToolkitTheme

/**
 * Option entry for a Material 3 Expressive connected toggle button bar.
 */
data class ToolkitToggleOption(
    val key: String,
    val label: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true
)

/**
 * Material 3 Expressive connected toggle button group replacing legacy segmented rows.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolkitConnectedToggleGroup(
    options: List<ToolkitToggleOption>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return

    Row(
        modifier = modifier.animateContentSize(
            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
        ),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = selectedKey == option.key
            val shapes = when {
                options.size == 1 -> ToggleButtonDefaults.shapes()
                index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                index == options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            ToggleButton(
                checked = isSelected,
                onCheckedChange = { checked -> if (checked) onSelect(option.key) },
                enabled = option.enabled,
                shapes = shapes,
                modifier = if (option.enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier
            ) {
                val iconToShow = if (isSelected) option.icon ?: Icons.Default.Check else option.icon
                if (iconToShow != null) {
                    Icon(
                        imageVector = iconToShow,
                        contentDescription = null,
                        modifier = Modifier.size(ToggleButtonDefaults.IconSize)
                    )
                    Spacer(modifier = Modifier.size(ToggleButtonDefaults.IconSpacing))
                }
                Text(text = option.label, style = ToolkitTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolkitButtonGroup(
    modifier: Modifier = Modifier,
    content: @Composable ToolkitButtonGroupScope.() -> Unit
) {
    val outerCorner = ToolkitTheme.dimensions.buttonGroupOuterCorner
    val innerCorner = ToolkitTheme.dimensions.buttonGroupInnerCorner
    val gap = ToolkitTheme.dimensions.buttonGroupGap
    val containerPadding = ToolkitTheme.spacing.extraSmall

    val scope = ToolkitButtonGroupScopeImpl()
    scope.content()

    val children = scope.children
    if (children.isEmpty()) return

    Row(
        modifier = modifier
            .padding(containerPadding)
            .animateContentSize(),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        children.forEachIndexed { index, childContent ->
            key(index) {
                // Subtract padding from outer corner radius so the inner button corners run parallel to container corners
                val buttonOuterCorner = (outerCorner - containerPadding).coerceAtLeast(ToolkitTheme.spacing.none)

                val shape = computeGroupedShape(
                    index = index,
                    totalCount = children.size,
                    outerCorner = buttonOuterCorner,
                    innerCorner = innerCorner,
                    orientation = GroupOrientation.Horizontal
                )

                childContent(shape, Modifier.clip(shape))
            }
        }
    }
}

interface ToolkitButtonGroupScope {
    fun item(
        visible: Boolean = true,
        content: @Composable (shape: CornerBasedShape, modifier: Modifier) -> Unit
    )
}

private class ToolkitButtonGroupScopeImpl : ToolkitButtonGroupScope {
    val children = mutableListOf<@Composable (shape: CornerBasedShape, modifier: Modifier) -> Unit>()

    override fun item(
        visible: Boolean,
        content: @Composable (shape: CornerBasedShape, modifier: Modifier) -> Unit
    ) {
        if (visible) {
            children.add(content)
        }
    }
}
