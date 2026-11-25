package org.wip.plugintoolkit.shared.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.shared.components.menu.ToolkitDropdownMenu
import org.wip.plugintoolkit.shared.components.menu.ToolkitDropdownMenuItem

/**
 * Menu entry displayed inside a [ToolkitSplitButton] overflow popup menu.
 */
data class ToolkitSplitMenuItem(
    val key: String,
    val label: String,
    val icon: ImageVector? = null,
    val isDestructive: Boolean = false,
    val enabled: Boolean = true,
    val startsSection: Boolean = false
)

/**
 * Material 3 Expressive Split Button built on [SplitButtonLayout] with spring-driven
 * chevron rotation, accessible hover tooltip, and [ToolkitDropdownMenu] integration.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ToolkitSplitButton(
    label: String,
    onPrimaryClick: () -> Unit,
    menuItems: List<ToolkitSplitMenuItem>,
    onMenuItemClick: (ToolkitSplitMenuItem) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    isTonal: Boolean = false,
    enabled: Boolean = true,
    menuTooltip: String = "More actions",
    colors: ButtonColors = if (isTonal) {
        ButtonDefaults.filledTonalButtonColors()
    } else {
        ButtonDefaults.buttonColors()
    }
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (menuExpanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "splitButtonChevronRotation"
    )
    val pointerModifier = if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier

    Box(modifier = modifier) {
        SplitButtonLayout(
            leadingButton = {
                val content: @Composable RowScope.() -> Unit = {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                        )
                        Spacer(modifier = Modifier.size(ToolkitTheme.spacing.xs))
                    }
                    Text(
                        text = label,
                        style = ToolkitTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isTonal) {
                    SplitButtonDefaults.TonalLeadingButton(
                        onClick = onPrimaryClick,
                        enabled = enabled,
                        colors = colors,
                        modifier = pointerModifier,
                        content = content
                    )
                } else {
                    SplitButtonDefaults.LeadingButton(
                        onClick = onPrimaryClick,
                        enabled = enabled,
                        colors = colors,
                        modifier = pointerModifier,
                        content = content
                    )
                }
            },
            trailingButton = {
                if (menuItems.isNotEmpty()) {
                    ToolkitTooltipBox(
                        tooltip = menuTooltip,
                        position = ToolkitTooltipPosition.Above
                    ) {
                        val trailingContent: @Composable RowScope.() -> Unit = {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(ToolkitTheme.dimensions.denseIconSize)
                                    .graphicsLayer { rotationZ = chevronRotation }
                            )
                        }
                        if (isTonal) {
                            SplitButtonDefaults.TonalTrailingButton(
                                checked = menuExpanded,
                                onCheckedChange = { menuExpanded = it },
                                enabled = enabled,
                                colors = colors,
                                modifier = pointerModifier.semantics { contentDescription = menuTooltip },
                                content = trailingContent
                            )
                        } else {
                            SplitButtonDefaults.TrailingButton(
                                checked = menuExpanded,
                                onCheckedChange = { menuExpanded = it },
                                enabled = enabled,
                                colors = colors,
                                modifier = pointerModifier.semantics { contentDescription = menuTooltip },
                                content = trailingContent
                            )
                        }
                    }
                }
            }
        )

        if (menuItems.isNotEmpty()) {
            ToolkitDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                menuItems.forEachIndexed { index, item ->
                    if (item.startsSection && index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(
                                alpha = ToolkitTheme.opacity.divider
                            )
                        )
                    }
                    ToolkitDropdownMenuItem(
                        text = {
                            Text(
                                text = item.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onMenuItemClick(item)
                        },
                        leadingIcon = item.icon?.let { iconVector ->
                            {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = null,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                )
                            }
                        },
                        enabled = item.enabled,
                        isDestructive = item.isDestructive
                    )
                }
            }
        }
    }
}
