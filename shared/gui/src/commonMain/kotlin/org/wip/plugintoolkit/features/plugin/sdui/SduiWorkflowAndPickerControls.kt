package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.sdui.BreadcrumbsNode
import org.wip.plugintoolkit.api.sdui.ColorPickerNode
import org.wip.plugintoolkit.api.sdui.FilePickerMode
import org.wip.plugintoolkit.api.sdui.FilePickerNode
import org.wip.plugintoolkit.api.sdui.MenuNode
import org.wip.plugintoolkit.api.sdui.PaginationNode
import org.wip.plugintoolkit.api.sdui.StepStatus
import org.wip.plugintoolkit.api.sdui.StepperNode
import org.wip.plugintoolkit.api.sdui.TreeNodeItem
import org.wip.plugintoolkit.api.sdui.TreeViewNode
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.core.theme.StandardMotionScope
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.colorpicker.ui.ColorPickerDialog
import org.wip.plugintoolkit.features.colorpicker.utils.toHex
import org.wip.plugintoolkit.shared.components.ToolkitTreeItem
import org.wip.plugintoolkit.shared.components.ToolkitTreeView
import org.wip.plugintoolkit.shared.components.menu.ToolkitDropdownDivider
import org.wip.plugintoolkit.shared.components.menu.ToolkitDropdownMenu
import org.wip.plugintoolkit.shared.components.menu.ToolkitDropdownMenuItem
import org.wip.plugintoolkit.shared.components.plugin.inputs.parseColorString
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.sdui_breadcrumbs_overflow
import plugintoolkit.composeapp.generated.resources.sdui_color_hex_label
import plugintoolkit.composeapp.generated.resources.sdui_color_open_picker
import plugintoolkit.composeapp.generated.resources.sdui_file_browse
import plugintoolkit.composeapp.generated.resources.sdui_file_clear
import plugintoolkit.composeapp.generated.resources.sdui_file_placeholder
import plugintoolkit.composeapp.generated.resources.sdui_menu_open
import plugintoolkit.composeapp.generated.resources.sdui_pagination_first
import plugintoolkit.composeapp.generated.resources.sdui_pagination_last
import plugintoolkit.composeapp.generated.resources.sdui_pagination_next
import plugintoolkit.composeapp.generated.resources.sdui_pagination_of
import plugintoolkit.composeapp.generated.resources.sdui_pagination_page_size
import plugintoolkit.composeapp.generated.resources.sdui_pagination_prev

/**
 * Renders a hierarchical [TreeViewNode] backed by [ToolkitTreeView].
 */
@Composable
internal fun RenderTreeViewNode(
    node: TreeViewNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    fun mapItem(item: TreeNodeItem): ToolkitTreeItem = ToolkitTreeItem(
        key = item.key,
        label = item.label,
        icon = item.iconName?.let { SduiIconRegistry.resolve(it) },
        expanded = item.expanded,
        children = item.children.map(::mapItem)
    )

    val items = remember(node.items) { node.items.map(::mapItem) }

    ToolkitTreeView(
        items = items,
        modifier = modifier,
        selectedKey = node.selectedKey,
        onSelect = if (node.modifier.enabled) {
            { selectedKey ->
                onEvent(UiEvent.OptionSelected(nodeId = node.id, selectedKey = selectedKey))
            }
        } else {
            null
        },
        onToggleExpand = if (node.modifier.enabled) {
            { itemKey, expanded ->
                onEvent(UiEvent.TreeNodeToggled(nodeId = node.id, itemKey = itemKey, expanded = expanded))
            }
        } else {
            null
        },
        maxHeight = node.maxHeightDp?.dp ?: ToolkitTheme.dimensions.sduiTreeDefaultMaxHeight
    )
}

/**
 * Renders an inline desktop file/directory picker ([FilePickerNode]) emitting [UiEvent.FilesPicked].
 */
@Composable
internal fun RenderFilePickerNode(
    node: FilePickerNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val enabled = node.modifier.enabled
    val browseLabel = stringResource(Res.string.sdui_file_browse)
    val clearLabel = stringResource(Res.string.sdui_file_clear)
    val placeholderText = node.placeholder ?: stringResource(Res.string.sdui_file_placeholder)

    var localDisplayPath by remember(node.id, node.value, node.paths) {
        mutableStateOf(
            node.value.ifBlank { node.paths.joinToString(", ") }
        )
    }

    fun emitPicked(paths: List<String>) {
        localDisplayPath = paths.joinToString(", ")
        onEvent(UiEvent.FilesPicked(nodeId = node.id, action = node.action, paths = paths))
    }

    Row(
        modifier = modifier.withFormWidthConstraint(node.modifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
    ) {
        OutlinedTextField(
            value = localDisplayPath,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            enabled = enabled,
            label = node.label?.let { { Text(text = it, style = ToolkitTheme.typography.labelMedium) } },
            placeholder = { Text(text = placeholderText, style = ToolkitTheme.typography.bodySmall) },
            trailingIcon = if (localDisplayPath.isNotEmpty() && enabled) {
                {
                    SduiTooltipBox(tooltip = clearLabel) {
                        IconButton(
                            onClick = { emitPicked(emptyList()) },
                            modifier = Modifier
                                .size(ToolkitTheme.dimensions.denseIconButtonSize)
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = clearLabel,
                                modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                            )
                        }
                    }
                }
            } else {
                null
            },
            shape = ToolkitTheme.shapes.medium,
            colors = sduiOutlinedTextFieldColors(),
            modifier = Modifier.weight(1f)
        )

        FilledTonalButton(
            onClick = {
                scope.launch {
                    val dialogTitle = node.label ?: browseLabel
                    val picked: List<String> = when (node.mode) {
                        FilePickerMode.FILE -> listOfNotNull(
                            PlatformUtils.pickFile(dialogTitle, node.allowedExtensions)
                        )
                        FilePickerMode.FILES -> PlatformUtils.pickFiles(dialogTitle, node.allowedExtensions)
                        FilePickerMode.DIRECTORY -> listOfNotNull(PlatformUtils.pickFolder(dialogTitle))
                        FilePickerMode.SAVE -> listOfNotNull(
                            PlatformUtils.saveFile(node.defaultFileName, node.defaultExtension, ByteArray(0))
                        )
                    }
                    if (picked.isNotEmpty()) {
                        emitPicked(picked)
                    }
                }
            },
            enabled = enabled,
            shape = ToolkitTheme.shapes.pill,
            modifier = if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier
        ) {
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = null,
                modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
            )
            Spacer(modifier = Modifier.width(ToolkitTheme.spacing.xs))
            Text(text = browseLabel, style = ToolkitTheme.typography.labelMedium)
        }
    }
}

/**
 * Renders a [ColorPickerNode] wired to the host [ColorPickerDialog] and preset swatches.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RenderColorPickerNode(
    node: ColorPickerNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var showPicker by remember { mutableStateOf(false) }
    var hexValue by remember(node.id, node.colorHex) { mutableStateOf(node.colorHex) }
    val enabled = node.modifier.enabled
    val openPickerLabel = stringResource(Res.string.sdui_color_open_picker)
    val previewColor = remember(hexValue) { parseColorString(hexValue) }

    fun commitColorHex(nextHex: String) {
        hexValue = nextHex
        onEvent(UiEvent.ColorChanged(nodeId = node.id, colorHex = nextHex))
    }

    Column(
        modifier = modifier.withFormWidthConstraint(node.modifier),
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
        ) {
            val swatchTrigger: @Composable () -> Unit = {
                SduiTooltipBox(tooltip = openPickerLabel) {
                    Box(
                        modifier = Modifier
                            .size(ToolkitTheme.dimensions.sduiColorSwatchSize)
                            .clip(ToolkitTheme.shapes.small)
                            .background(previewColor)
                            .border(
                                ToolkitTheme.dimensions.borderUnselected,
                                MaterialTheme.colorScheme.outline,
                                ToolkitTheme.shapes.small
                            )
                            .then(
                                if (enabled) {
                                    Modifier
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable { showPicker = true }
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
            }

            if (node.showHexInput) {
                OutlinedTextField(
                    value = hexValue,
                    onValueChange = { updated ->
                        hexValue = updated
                        if (updated.trim().matches(Regex("^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8}|[0-9A-Fa-f]{3})$"))) {
                            onEvent(UiEvent.ColorChanged(nodeId = node.id, colorHex = updated.trim()))
                        }
                    },
                    enabled = enabled,
                    singleLine = true,
                    label = {
                        Text(
                            text = node.label ?: stringResource(Res.string.sdui_color_hex_label),
                            style = ToolkitTheme.typography.labelMedium
                        )
                    },
                    leadingIcon = {
                        Box(modifier = Modifier.padding(start = ToolkitTheme.spacing.xs)) {
                            swatchTrigger()
                        }
                    },
                    trailingIcon = {
                        SduiTooltipBox(tooltip = openPickerLabel) {
                            IconButton(
                                onClick = { showPicker = true },
                                enabled = enabled,
                                modifier = if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = openPickerLabel,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    shape = ToolkitTheme.shapes.medium,
                    colors = sduiOutlinedTextFieldColors(),
                    modifier = Modifier.weight(1f)
                )
            } else {
                swatchTrigger()
                node.label?.let { labelText ->
                    Text(
                        text = labelText,
                        style = ToolkitTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedButton(
                    onClick = { showPicker = true },
                    enabled = enabled,
                    shape = ToolkitTheme.shapes.pill,
                    modifier = if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier
                ) {
                    Text(text = hexValue, style = ToolkitTheme.typography.labelMedium)
                }
            }
        }

        if (node.presets.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
            ) {
                node.presets.forEach { presetHex ->
                    val swatchColor = remember(presetHex) { parseColorString(presetHex) }
                    val isActive = presetHex.equals(hexValue, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(ToolkitTheme.dimensions.sduiColorPresetSize)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(
                                width = if (isActive) {
                                    ToolkitTheme.dimensions.borderSelected
                                } else {
                                    ToolkitTheme.dimensions.borderUnselected
                                },
                                color = if (isActive) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                },
                                shape = CircleShape
                            )
                            .then(
                                if (enabled) {
                                    Modifier
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .clickable { commitColorHex(presetHex) }
                                } else {
                                    Modifier
                                }
                            )
                    )
                }
            }
        }
    }

    if (showPicker && enabled) {
        ColorPickerDialog(
            show = true,
            onDismissRequest = { showPicker = false },
            onPickedColor = { picked ->
                showPicker = false
                commitColorHex(picked.toHex())
            }
        )
    }
}

/**
 * Renders a [BreadcrumbsNode] path trail with automatic middle segment overflow menu.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RenderBreadcrumbsNode(
    node: BreadcrumbsNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    if (node.items.isEmpty()) return

    val maxVisible = node.maxVisibleItems.coerceAtLeast(2)
    val needsCollapse = node.items.size > maxVisible
    val hiddenMiddle = remember(node.items, maxVisible) {
        if (!needsCollapse) emptyList()
        else node.items.subList(1, node.items.size - (maxVisible - 1))
    }
    val visibleTail = remember(node.items, maxVisible) {
        if (!needsCollapse) node.items
        else listOf(node.items.first()) + node.items.takeLast(maxVisible - 1)
    }
    var overflowExpanded by remember { mutableStateOf(false) }
    val enabled = node.modifier.enabled
    val overflowLabel = stringResource(Res.string.sdui_breadcrumbs_overflow)

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs),
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
    ) {
        if (needsCollapse) {
            BreadcrumbSegmentChip(
                label = visibleTail.first().label,
                iconName = visibleTail.first().iconName,
                isLast = false,
                enabled = enabled,
                onClick = { onEvent(UiEvent.OptionSelected(node.id, visibleTail.first().key)) }
            )
            Text(
                text = node.separator,
                style = ToolkitTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                SduiTooltipBox(tooltip = overflowLabel) {
                    TextButton(
                        onClick = { overflowExpanded = true },
                        enabled = enabled,
                        modifier = if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier
                    ) {
                        Text(text = "...", style = ToolkitTheme.typography.labelMedium)
                    }
                }
                ToolkitDropdownMenu(
                    expanded = overflowExpanded && enabled,
                    onDismissRequest = { overflowExpanded = false }
                ) {
                    hiddenMiddle.forEach { hiddenItem ->
                        ToolkitDropdownMenuItem(
                            text = { Text(text = hiddenItem.label) },
                            leadingIcon = hiddenItem.iconName?.let { iconKey ->
                                {
                                    Icon(
                                        imageVector = SduiIconRegistry.resolve(iconKey),
                                        contentDescription = null,
                                        modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                                    )
                                }
                            },
                            onClick = {
                                overflowExpanded = false
                                onEvent(UiEvent.OptionSelected(node.id, hiddenItem.key))
                            }
                        )
                    }
                }
            }
            Text(
                text = node.separator,
                style = ToolkitTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            val tailItems = visibleTail.drop(1)
            tailItems.forEachIndexed { index, item ->
                val isLast = index == tailItems.lastIndex
                BreadcrumbSegmentChip(
                    label = item.label,
                    iconName = item.iconName,
                    isLast = isLast,
                    enabled = enabled && !isLast,
                    onClick = { onEvent(UiEvent.OptionSelected(node.id, item.key)) }
                )
                if (!isLast) {
                    Text(
                        text = node.separator,
                        style = ToolkitTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        } else {
            visibleTail.forEachIndexed { index, item ->
                val isLast = index == visibleTail.lastIndex
                BreadcrumbSegmentChip(
                    label = item.label,
                    iconName = item.iconName,
                    isLast = isLast,
                    enabled = enabled && !isLast,
                    onClick = { onEvent(UiEvent.OptionSelected(node.id, item.key)) }
                )
                if (!isLast) {
                    Text(
                        text = node.separator,
                        style = ToolkitTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbSegmentChip(
    label: String,
    iconName: String?,
    isLast: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = ToolkitTheme.shapes.small,
        color = if (isLast) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            ToolkitTheme.colors.transparent
        },
        modifier = if (enabled) {
            Modifier
                .clip(ToolkitTheme.shapes.small)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClick = onClick)
        } else {
            Modifier
        }
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = ToolkitTheme.spacing.sm,
                vertical = ToolkitTheme.spacing.xs
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
        ) {
            iconName?.let { icon ->
                Icon(
                    imageVector = SduiIconRegistry.resolve(icon),
                    contentDescription = null,
                    tint = if (isLast) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(ToolkitTheme.dimensions.iconExtraSmall)
                )
            }
            Text(
                text = label,
                style = ToolkitTheme.typography.labelMedium,
                fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isLast) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Renders a [PaginationNode] bar emitting [UiEvent.PageChanged].
 */
@Composable
internal fun RenderPaginationNode(
    node: PaginationNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val total = node.totalPages.coerceAtLeast(1)
    val current = node.currentPage.coerceIn(1, total)
    val enabled = node.modifier.enabled
    var pageSizeExpanded by remember { mutableStateOf(false) }

    val firstDesc = stringResource(Res.string.sdui_pagination_first)
    val prevDesc = stringResource(Res.string.sdui_pagination_prev)
    val nextDesc = stringResource(Res.string.sdui_pagination_next)
    val lastDesc = stringResource(Res.string.sdui_pagination_last)

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = ToolkitTheme.spacing.sm,
                            vertical = ToolkitTheme.spacing.xs
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
                ) {
                    SduiTooltipBox(tooltip = firstDesc) {
                        IconButton(
                            onClick = { onEvent(UiEvent.PageChanged(node.id, 1, node.pageSize)) },
                            enabled = enabled && current > 1,
                            modifier = Modifier
                                .size(ToolkitTheme.dimensions.denseIconButtonSize)
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FirstPage,
                                contentDescription = firstDesc,
                                modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                            )
                        }
                    }

                    SduiTooltipBox(tooltip = prevDesc) {
                        IconButton(
                            onClick = { onEvent(UiEvent.PageChanged(node.id, current - 1, node.pageSize)) },
                            enabled = enabled && current > 1,
                            modifier = Modifier
                                .size(ToolkitTheme.dimensions.denseIconButtonSize)
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = prevDesc,
                                modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                            )
                        }
                    }

                    Text(
                        text = stringResource(Res.string.sdui_pagination_of, current, total),
                        style = ToolkitTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = ToolkitTheme.spacing.sm)
                    )

                    SduiTooltipBox(tooltip = nextDesc) {
                        IconButton(
                            onClick = { onEvent(UiEvent.PageChanged(node.id, current + 1, node.pageSize)) },
                            enabled = enabled && current < total,
                            modifier = Modifier
                                .size(ToolkitTheme.dimensions.denseIconButtonSize)
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = nextDesc,
                                modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                            )
                        }
                    }

                    SduiTooltipBox(tooltip = lastDesc) {
                        IconButton(
                            onClick = { onEvent(UiEvent.PageChanged(node.id, total, node.pageSize)) },
                            enabled = enabled && current < total,
                            modifier = Modifier
                                .size(ToolkitTheme.dimensions.denseIconButtonSize)
                                .pointerHoverIcon(PointerIcon.Hand)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LastPage,
                                contentDescription = lastDesc,
                                modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (node.pageSizeOptions.isNotEmpty() && node.pageSize != null) {
                        Box {
                            OutlinedButton(
                                onClick = { pageSizeExpanded = true },
                                enabled = enabled,
                                shape = ToolkitTheme.shapes.pill,
                                modifier = if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier
                            ) {
                                Text(
                                    text = stringResource(Res.string.sdui_pagination_page_size, node.pageSize!!),
                                    style = ToolkitTheme.typography.labelMedium
                                )
                            }
                            ToolkitDropdownMenu(
                                expanded = pageSizeExpanded && enabled,
                                onDismissRequest = { pageSizeExpanded = false }
                            ) {
                                node.pageSizeOptions.forEach { sizeOption ->
                                    ToolkitDropdownMenuItem(
                                        text = {
                                            Text(text = stringResource(Res.string.sdui_pagination_page_size, sizeOption))
                                        },
                                        isSelected = sizeOption == node.pageSize,
                                        onClick = {
                                            pageSizeExpanded = false
                                            onEvent(UiEvent.PageChanged(node.id, 1, sizeOption))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders a multi-step wizard progress indicator ([StepperNode]) emitting [UiEvent.OptionSelected].
 */
@Composable
internal fun RenderStepperNode(
    node: StepperNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    if (node.steps.isEmpty()) return
    val groupEnabled = node.modifier.enabled

    if (node.horizontal) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
        ) {
            node.steps.forEachIndexed { index, step ->
                val stepClickable = groupEnabled && step.enabled
                Row(
                    modifier = Modifier
                        .clip(ToolkitTheme.shapes.medium)
                        .then(
                            if (stepClickable) {
                                Modifier
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable { onEvent(UiEvent.OptionSelected(node.id, step.key)) }
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = ToolkitTheme.spacing.sm, vertical = ToolkitTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
                ) {
                    StepCircleIndicator(index = index + 1, status = step.status)
                    Column {
                        Text(
                            text = step.label,
                            style = ToolkitTheme.typography.labelMedium,
                            fontWeight = if (step.status == StepStatus.ACTIVE) FontWeight.SemiBold else FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        step.subtitle?.let { sub ->
                            Text(
                                text = sub,
                                style = ToolkitTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (index < node.steps.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        thickness = ToolkitTheme.dimensions.borderUnselected,
                        color = if (step.status == StepStatus.COMPLETED) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                }
            }
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
        ) {
            node.steps.forEachIndexed { index, step ->
                val stepClickable = groupEnabled && step.enabled
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ToolkitTheme.shapes.medium)
                        .then(
                            if (stepClickable) {
                                Modifier
                                    .pointerHoverIcon(PointerIcon.Hand)
                                    .clickable { onEvent(UiEvent.OptionSelected(node.id, step.key)) }
                            } else {
                                Modifier
                            }
                        )
                        .padding(horizontal = ToolkitTheme.spacing.sm, vertical = ToolkitTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
                ) {
                    StepCircleIndicator(index = index + 1, status = step.status)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = step.label,
                            style = ToolkitTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        step.subtitle?.let { sub ->
                            Text(
                                text = sub,
                                style = ToolkitTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCircleIndicator(
    index: Int,
    status: StepStatus
) {
    val containerColor = when (status) {
        StepStatus.COMPLETED, StepStatus.ACTIVE -> MaterialTheme.colorScheme.primary
        StepStatus.ERROR -> MaterialTheme.colorScheme.error
        StepStatus.PENDING -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = when (status) {
        StepStatus.COMPLETED, StepStatus.ACTIVE -> MaterialTheme.colorScheme.onPrimary
        StepStatus.ERROR -> MaterialTheme.colorScheme.onError
        StepStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .size(ToolkitTheme.dimensions.iconMedium)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        when (status) {
            StepStatus.COMPLETED -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(ToolkitTheme.dimensions.iconExtraSmall)
            )

            StepStatus.ERROR -> Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(ToolkitTheme.dimensions.iconExtraSmall)
            )

            else -> Text(
                text = index.toString(),
                style = ToolkitTheme.typography.labelSmall,
                color = contentColor
            )
        }
    }
}

/**
 * Renders a standalone overflow/action [MenuNode] backed by [ToolkitDropdownMenu].
 */
@Composable
internal fun RenderMenuNode(
    node: MenuNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val enabled = node.modifier.enabled
    val defaultMenuTooltip = node.tooltip ?: stringResource(Res.string.sdui_menu_open)

    Box(modifier = modifier) {
        if (!node.triggerLabel.isNullOrBlank()) {
            SduiTooltipBox(tooltip = node.tooltip) {
                OutlinedButton(
                    onClick = { expanded = true },
                    enabled = enabled,
                    shape = ToolkitTheme.shapes.pill,
                    modifier = if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier
                ) {
                    node.triggerIconName?.let { iconKey ->
                        Icon(
                            imageVector = SduiIconRegistry.resolve(iconKey),
                            contentDescription = null,
                            modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                        )
                        Spacer(modifier = Modifier.width(ToolkitTheme.spacing.xs))
                    }
                    Text(text = node.triggerLabel!!, style = ToolkitTheme.typography.labelMedium)
                }
            }
        } else {
            SduiTooltipBox(tooltip = defaultMenuTooltip) {
                IconButton(
                    onClick = { expanded = true },
                    enabled = enabled,
                    modifier = if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier
                ) {
                    Icon(
                        imageVector = SduiIconRegistry.resolve(node.triggerIconName ?: "more_vert"),
                        contentDescription = defaultMenuTooltip
                    )
                }
            }
        }

        ToolkitDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            node.items.forEachIndexed { index, item ->
                if (item.startsSection && index > 0) {
                    ToolkitDropdownDivider()
                }
                ToolkitDropdownMenuItem(
                    text = { Text(text = item.label) },
                    leadingIcon = item.iconName?.let { iconKey ->
                        {
                            Icon(
                                imageVector = SduiIconRegistry.resolve(iconKey),
                                contentDescription = null,
                                modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                            )
                        }
                    },
                    enabled = item.enabled,
                    isDestructive = item.isDestructive,
                    onClick = {
                        expanded = false
                        onEvent(UiEvent.Click(nodeId = node.id, action = item.action ?: item.key))
                    }
                )
            }
        }
    }
}
