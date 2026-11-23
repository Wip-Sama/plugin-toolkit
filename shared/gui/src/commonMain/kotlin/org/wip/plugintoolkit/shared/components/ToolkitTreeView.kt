package org.wip.plugintoolkit.shared.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.StandardMotionScope
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformContextMenuArea
import org.wip.plugintoolkit.core.utils.PlatformContextMenuItem
import org.wip.plugintoolkit.core.utils.PlatformVerticalScrollbar
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.sdui_tree_collapse
import plugintoolkit.composeapp.generated.resources.sdui_tree_empty
import plugintoolkit.composeapp.generated.resources.sdui_tree_expand

/**
 * Hierarchical item descriptor rendered inside [ToolkitTreeView].
 */
data class ToolkitTreeItem(
    val key: String,
    val label: String,
    val icon: ImageVector? = null,
    val expanded: Boolean = false,
    val children: List<ToolkitTreeItem> = emptyList(),
    val contextMenuItems: List<PlatformContextMenuItem> = emptyList()
)

private data class FlattenedTreeEntry(
    val item: ToolkitTreeItem,
    val depth: Int,
    val isExpanded: Boolean
)

/**
 * Reusable collapsible desktop tree view featuring keyboard arrow navigation,
 * animated disclosure chevrons, right-click context menus, and compact 32dp target density.
 */
@Composable
fun ToolkitTreeView(
    items: List<ToolkitTreeItem>,
    modifier: Modifier = Modifier,
    selectedKey: String? = null,
    onSelect: ((String) -> Unit)? = null,
    onToggleExpand: ((key: String, expanded: Boolean) -> Unit)? = null,
    emptyText: String? = null,
    maxHeight: Dp = ToolkitTheme.dimensions.sduiTreeDefaultMaxHeight
) {
    var expandedOverrides by remember(items) { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    val visibleEntries = remember(items, expandedOverrides) {
        buildVisibleTreeEntries(items, expandedOverrides)
    }
    var focusedIndex by remember(visibleEntries, selectedKey) {
        val selectedIdx = visibleEntries.indexOfFirst { it.item.key == selectedKey }
        mutableStateOf(if (selectedIdx >= 0) selectedIdx else 0)
    }

    fun toggleNode(entry: FlattenedTreeEntry, nextExpanded: Boolean) {
        expandedOverrides = expandedOverrides + (entry.item.key to nextExpanded)
        onToggleExpand?.invoke(entry.item.key, nextExpanded)
    }

    StandardMotionScope {
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides ToolkitTheme.dimensions.desktopTouchTargetSize
        ) {
            Surface(
                modifier = modifier.fillMaxWidth(),
                shape = ToolkitTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(
                    ToolkitTheme.dimensions.borderUnselected,
                    MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                if (visibleEntries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ToolkitTheme.spacing.md, vertical = ToolkitTheme.spacing.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emptyText ?: stringResource(Res.string.sdui_tree_empty),
                            style = ToolkitTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    return@Surface
                }

                val scrollState = rememberScrollState()
                val scrollbarReserve = if (scrollState.maxValue > 0) {
                    ToolkitTheme.dimensions.sduiScrollbarEndPadding
                } else {
                    ToolkitTheme.spacing.none
                }

                val keyHandlerModifier = Modifier
                    .focusable()
                    .onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyDown || visibleEntries.isEmpty()) {
                            return@onPreviewKeyEvent false
                        }
                        val currentEntry = visibleEntries.getOrNull(focusedIndex)
                        when (keyEvent.key) {
                            Key.DirectionDown -> {
                                focusedIndex = (focusedIndex + 1).coerceAtMost(visibleEntries.lastIndex)
                                true
                            }

                            Key.DirectionUp -> {
                                focusedIndex = (focusedIndex - 1).coerceAtLeast(0)
                                true
                            }

                            Key.DirectionRight -> {
                                if (currentEntry != null && currentEntry.item.children.isNotEmpty() && !currentEntry.isExpanded) {
                                    toggleNode(currentEntry, true)
                                    true
                                } else {
                                    false
                                }
                            }

                            Key.DirectionLeft -> {
                                if (currentEntry != null && currentEntry.item.children.isNotEmpty() && currentEntry.isExpanded) {
                                    toggleNode(currentEntry, false)
                                    true
                                } else {
                                    false
                                }
                            }

                            Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                if (currentEntry != null) {
                                    onSelect?.invoke(currentEntry.item.key)
                                    true
                                } else {
                                    false
                                }
                            }

                            else -> false
                        }
                    }

                Box(modifier = Modifier.fillMaxWidth().then(keyHandlerModifier)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = maxHeight)
                            .verticalScroll(scrollState)
                            .padding(
                                start = ToolkitTheme.spacing.xs,
                                top = ToolkitTheme.spacing.xs,
                                bottom = ToolkitTheme.spacing.xs,
                                end = ToolkitTheme.spacing.xs + scrollbarReserve
                            ),
                        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xxs)
                    ) {
                        visibleEntries.forEachIndexed { index, entry ->
                            val isSelected = entry.item.key == selectedKey
                            TreeNodeRow(
                                entry = entry,
                                isSelected = isSelected,
                                isFocused = index == focusedIndex && selectedKey == null,
                                onToggleExpand = { toggleNode(entry, !entry.isExpanded) },
                                onClick = {
                                    focusedIndex = index
                                    onSelect?.invoke(entry.item.key)
                                }
                            )
                        }
                    }

                    PlatformVerticalScrollbar(
                        scrollState = scrollState,
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
private fun TreeNodeRow(
    entry: FlattenedTreeEntry,
    isSelected: Boolean,
    isFocused: Boolean,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit
) {
    val item = entry.item
    val hasChildren = item.children.isNotEmpty()
    val interactionSource = remember(item.key) { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val chevronRotation by animateFloatAsState(
        targetValue = if (entry.isExpanded) 90f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()
    )

    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer
        isHovered -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = ToolkitTheme.opacity.divider)
        isFocused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = ToolkitTheme.opacity.subtleHighlight)
        else -> ToolkitTheme.colors.transparent
    }
    val contentColor = if (isSelected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val toggleLabel = stringResource(
        if (entry.isExpanded) Res.string.sdui_tree_collapse else Res.string.sdui_tree_expand
    )

    PlatformContextMenuArea(items = item.contextMenuItems) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ToolkitTheme.dimensions.sduiTreeRowHeight)
                .clip(ToolkitTheme.shapes.small)
                .background(backgroundColor)
                .hoverable(interactionSource)
                .semantics {
                    role = Role.Button
                    selected = isSelected
                }
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick
                )
                .padding(
                    start = ToolkitTheme.spacing.xs + (ToolkitTheme.dimensions.sduiTreeIndent * entry.depth),
                    end = ToolkitTheme.spacing.sm
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
        ) {
            if (hasChildren) {
                ToolkitTooltipBox(tooltip = toggleLabel) {
                    IconButton(
                        onClick = onToggleExpand,
                        modifier = Modifier
                            .size(ToolkitTheme.dimensions.denseIconButtonSize)
                            .pointerHoverIcon(PointerIcon.Hand)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = toggleLabel,
                            tint = contentColor,
                            modifier = Modifier
                                .size(ToolkitTheme.dimensions.denseIconSize)
                                .graphicsLayer { rotationZ = chevronRotation }
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(ToolkitTheme.dimensions.denseIconButtonSize))
            }

            item.icon?.let { iconVector ->
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                )
            }

            Text(
                text = item.label,
                style = ToolkitTheme.typography.bodySmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun buildVisibleTreeEntries(
    items: List<ToolkitTreeItem>,
    expandedOverrides: Map<String, Boolean>,
    depth: Int = 0
): List<FlattenedTreeEntry> {
    val entries = mutableListOf<FlattenedTreeEntry>()
    items.forEach { item ->
        val isExpanded = expandedOverrides[item.key] ?: item.expanded
        entries.add(FlattenedTreeEntry(item = item, depth = depth, isExpanded = isExpanded))
        if (isExpanded && item.children.isNotEmpty()) {
            entries.addAll(buildVisibleTreeEntries(item.children, expandedOverrides, depth + 1))
        }
    }
    return entries
}
