package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.sdui.ButtonGroupNode
import org.wip.plugintoolkit.api.sdui.SplitButtonNode
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.shared.components.ToolkitButtonGroup
import org.wip.plugintoolkit.shared.components.menu.ToolkitDropdownMenu
import org.wip.plugintoolkit.shared.components.menu.ToolkitDropdownMenuItem
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.sdui_more_actions

/**
 * Shared button content renderer displaying an optional leading vector icon alongside a single-line label.
 */
@Composable
internal fun RenderButtonLabel(
    label: String,
    iconName: String?,
    iconSize: Dp = ToolkitTheme.dimensions.iconSmall
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
    ) {
        iconName?.takeIf { it.isNotBlank() }?.let { resolvedName ->
            Icon(
                imageVector = SduiIconRegistry.resolve(resolvedName),
                contentDescription = null,
                modifier = Modifier.size(iconSize)
            )
        }
        Text(
            text = label,
            style = ToolkitTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Renders a Material 3 Expressive connected [ButtonGroupNode] with pill active-state morphing and
 * optional solid semantic container color coding.
 */
@Composable
internal fun RenderButtonGroupNode(
    node: ButtonGroupNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    if (node.items.isEmpty()) return

    val useDenseHeight = LocalInDenseActionBar.current || LocalInCompactSduiSurface.current
    val itemHeight = if (useDenseHeight) {
        ToolkitTheme.dimensions.segmentedButtonHeight
    } else {
        ToolkitTheme.dimensions.standardButtonHeight
    }

    ToolkitButtonGroup(modifier = modifier) {
        node.items.forEach { groupItem ->
            item(visible = true) { connectedShape, itemModifier ->
                val isEnabled = groupItem.enabled && node.modifier.enabled
                val isActive = node.selectionMode && groupItem.selected
                val itemShape = if (isActive) ToolkitTheme.shapes.pill else connectedShape

                val (baseBg, baseFg) = when {
                    groupItem.color != null -> groupItem.color!!.toContainerPair()
                    isActive -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurfaceVariant
                }
                val bgColor = if (isEnabled) baseBg else baseBg.copy(alpha = ToolkitTheme.opacity.settingsItemHover)
                val fgColor = if (isEnabled) baseFg else baseFg.copy(alpha = ToolkitTheme.opacity.settingsItemHover)

                SduiTooltipBox(tooltip = groupItem.tooltip) {
                    Surface(
                        onClick = { onEvent(UiEvent.Click(node.id, groupItem.action ?: groupItem.key)) },
                        enabled = isEnabled,
                        shape = itemShape,
                        color = bgColor,
                        contentColor = fgColor,
                        modifier = itemModifier
                            .heightIn(min = itemHeight)
                            .then(if (isEnabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                    ) {
                        Box(
                            modifier = Modifier.padding(
                                horizontal = ToolkitTheme.dimensions.sduiCompactSpacing,
                                vertical = ToolkitTheme.spacing.xs
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            RenderButtonLabel(
                                label = groupItem.label,
                                iconName = groupItem.iconName,
                                iconSize = ToolkitTheme.dimensions.iconSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders a Material 3 Expressive [SplitButtonNode] with a primary pill trigger and an anchored
 * contextual [ToolkitDropdownMenu] action sheet.
 */
@Composable
internal fun RenderSplitButtonNode(
    node: SplitButtonNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember(node.id) { mutableStateOf(false) }
    val isEnabled = node.modifier.enabled
    val useDenseHeight = LocalInDenseActionBar.current || LocalInCompactSduiSurface.current
    val itemHeight = if (useDenseHeight) {
        ToolkitTheme.dimensions.segmentedButtonHeight
    } else {
        ToolkitTheme.dimensions.standardButtonHeight
    }

    val (baseBg, baseFg) = if (node.isTonal) {
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    }
    val bgColor = if (isEnabled) baseBg else baseBg.copy(alpha = ToolkitTheme.opacity.settingsItemHover)
    val fgColor = if (isEnabled) baseFg else baseFg.copy(alpha = ToolkitTheme.opacity.settingsItemHover)
    val menuTooltip = node.menuTooltip?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.sdui_more_actions)

    Box(modifier = modifier) {
        ToolkitButtonGroup {
            item(visible = true) { connectedShape, itemModifier ->
                Surface(
                    onClick = { onEvent(UiEvent.Click(node.id, node.primaryAction)) },
                    enabled = isEnabled,
                    shape = connectedShape,
                    color = bgColor,
                    contentColor = fgColor,
                    modifier = itemModifier
                        .heightIn(min = itemHeight)
                        .then(if (isEnabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                ) {
                    Box(
                        modifier = Modifier.padding(
                            horizontal = ToolkitTheme.spacing.md,
                            vertical = ToolkitTheme.spacing.xs
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        RenderButtonLabel(
                            label = node.label,
                            iconName = node.iconName,
                            iconSize = ToolkitTheme.dimensions.iconSmall
                        )
                    }
                }
            }

            item(visible = node.menuItems.isNotEmpty()) { connectedShape, itemModifier ->
                val chevronShape = if (expanded) ToolkitTheme.shapes.pill else connectedShape
                SduiTooltipBox(tooltip = menuTooltip) {
                    Surface(
                        onClick = { expanded = !expanded },
                        enabled = isEnabled,
                        shape = chevronShape,
                        color = bgColor,
                        contentColor = fgColor,
                        modifier = itemModifier
                            .heightIn(min = itemHeight)
                            .semantics { contentDescription = menuTooltip }
                            .then(if (isEnabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
                    ) {
                        Box(
                            modifier = Modifier.padding(
                                horizontal = ToolkitTheme.spacing.sm,
                                vertical = ToolkitTheme.spacing.xs
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                            )
                        }
                    }
                }
            }
        }

        if (node.menuItems.isNotEmpty()) {
            ToolkitDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                node.menuItems.forEachIndexed { index, menuItem ->
                    if (menuItem.startsSection && index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = ToolkitTheme.spacing.xxs),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                    val itemEnabled = menuItem.enabled && isEnabled
                    ToolkitDropdownMenuItem(
                        text = {
                            Text(
                                text = menuItem.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            expanded = false
                            onEvent(UiEvent.Click(node.id, menuItem.action ?: menuItem.key))
                        },
                        leadingIcon = menuItem.iconName?.takeIf { it.isNotBlank() }?.let { iconName ->
                            {
                                Icon(
                                    imageVector = SduiIconRegistry.resolve(iconName),
                                    contentDescription = null,
                                    modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall)
                                )
                            }
                        },
                        enabled = itemEnabled,
                        isDestructive = menuItem.isDestructive
                    )
                }
            }
        }
    }
}
