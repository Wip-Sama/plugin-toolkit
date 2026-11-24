package org.wip.plugintoolkit.shared.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import org.jetbrains.compose.resources.stringResource
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.sdui_fab_menu_toggle

/**
 * Menu item entry rendered inside [ToolkitFabMenu].
 */
data class ToolkitFabMenuItem(
    val key: String,
    val label: String,
    val icon: ImageVector? = null,
    val enabled: Boolean = true,
    val isDestructive: Boolean = false
)

/**
 * Material 3 Expressive [FloatingActionButtonMenu] paired with [ToggleFloatingActionButton]
 * and desktop hover tooltips.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolkitFabMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    items: List<ToolkitFabMenuItem>,
    onItemClick: (ToolkitFabMenuItem) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Add,
    tooltip: String? = null,
    horizontalAlignment: Alignment.Horizontal = Alignment.End
) {
    val toggleDescription = tooltip ?: stringResource(Res.string.sdui_fab_menu_toggle)

    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        button = {
            ToolkitTooltipBox(tooltip = toggleDescription) {
                ToggleFloatingActionButton(
                    checked = expanded,
                    onCheckedChange = onExpandedChange,
                    containerColor = ToggleFloatingActionButtonDefaults.containerColor(),
                    containerSize = ToggleFloatingActionButtonDefaults.containerSize(),
                    containerCornerRadius = ToggleFloatingActionButtonDefaults.containerCornerRadius(),
                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                ) {
                    val activeIcon = if (checkedProgress > 0.5f) Icons.Default.Close else icon
                    Icon(
                        imageVector = activeIcon,
                        contentDescription = toggleDescription
                    )
                }
            }
        }
    ) {
        items.forEach { item ->
            val containerColor = if (item.isDestructive) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
            val contentColor = if (item.isDestructive) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            }
            FloatingActionButtonMenuItem(
                onClick = {
                    if (item.enabled) {
                        onExpandedChange(false)
                        onItemClick(item)
                    }
                },
                text = { Text(text = item.label) },
                icon = {
                    Icon(
                        imageVector = item.icon ?: Icons.Default.Add,
                        contentDescription = item.label
                    )
                },
                containerColor = containerColor,
                contentColor = contentColor,
                modifier = Modifier.pointerHoverIcon(
                    if (item.enabled) PointerIcon.Hand else PointerIcon.Default
                )
            )
        }
    }
}
