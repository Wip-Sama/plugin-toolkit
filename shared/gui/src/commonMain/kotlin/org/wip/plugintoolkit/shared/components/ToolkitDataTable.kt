package org.wip.plugintoolkit.shared.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.StandardMotionScope
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformHorizontalScrollbar
import org.wip.plugintoolkit.core.utils.PlatformVerticalScrollbar
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.sdui_table_empty
import plugintoolkit.composeapp.generated.resources.sdui_table_sort_ascending
import plugintoolkit.composeapp.generated.resources.sdui_table_sort_descending

/**
 * Column metadata for [ToolkitDataTable].
 */
data class ToolkitTableColumn(
    val key: String,
    val title: String,
    val weight: Float = 1f,
    val width: Dp? = null,
    val sortable: Boolean = false
)

/**
 * Row model for [ToolkitDataTable].
 */
data class ToolkitTableRow(
    val key: String,
    val cells: Map<String, String>,
    val badgeColumnKey: String? = null,
    val badgeColor: Color? = null
)

/**
 * Desktop-native Material 3 Expressive data table featuring a sticky sortable header row,
 * fixed or weighted column sizing, zebra-free hairline dividers, keyboard Up/Down/Enter row
 * navigation, bidirectional desktop scrollbars, and a compact 32dp minimum target density.
 */
@Composable
fun ToolkitDataTable(
    columns: List<ToolkitTableColumn>,
    rows: List<ToolkitTableRow>,
    modifier: Modifier = Modifier,
    selectedRowKeys: Set<String> = emptySet(),
    sortColumnKey: String? = null,
    sortAscending: Boolean = true,
    onSortClick: ((columnKey: String, nextAscending: Boolean) -> Unit)? = null,
    onRowClick: ((rowKey: String) -> Unit)? = null,
    emptyText: String? = null,
    maxHeight: Dp = ToolkitTheme.dimensions.sduiTableDefaultMaxHeight
) {
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
                if (columns.isEmpty()) {
                    EmptyTablePlaceholder(emptyText = emptyText)
                    return@Surface
                }

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val horizontalScrollState = rememberScrollState()
                    val verticalScrollState = rememberScrollState()
                    val minColWidth = ToolkitTheme.dimensions.sduiTableColumnMinWidth
                    val columnWidths = remember(columns, maxWidth, minColWidth) {
                        resolveColumnWidths(
                            columns = columns,
                            availableWidth = maxWidth,
                            minWeightedWidth = minColWidth
                        )
                    }
                    val totalTableWidth = columnWidths.fold(ToolkitTheme.spacing.none) { acc, w -> acc + w }
                    val needsHorizontalScroll = totalTableWidth > maxWidth

                    var focusedRowIndex by remember(rows, selectedRowKeys) {
                        val selectedIndex = rows.indexOfFirst { it.key in selectedRowKeys }
                        mutableStateOf(if (selectedIndex >= 0) selectedIndex else 0)
                    }

                    val tableKeyboardModifier = if (rows.isNotEmpty()) {
                        Modifier
                            .focusable()
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                when (keyEvent.key) {
                                    Key.DirectionDown -> {
                                        focusedRowIndex = (focusedRowIndex + 1).coerceAtMost(rows.lastIndex)
                                        true
                                    }

                                    Key.DirectionUp -> {
                                        focusedRowIndex = (focusedRowIndex - 1).coerceAtLeast(0)
                                        true
                                    }

                                    Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                        val row = rows.getOrNull(focusedRowIndex)
                                        if (row != null) {
                                            onRowClick?.invoke(row.key)
                                            true
                                        } else {
                                            false
                                        }
                                    }

                                    else -> false
                                }
                            }
                    } else {
                        Modifier
                    }

                    Column(modifier = Modifier.fillMaxWidth().then(tableKeyboardModifier)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (needsHorizontalScroll) Modifier.horizontalScroll(horizontalScrollState) else Modifier)
                        ) {
                            // Sticky header row
                            TableHeaderRow(
                                columns = columns,
                                columnWidths = columnWidths,
                                totalWidth = totalTableWidth,
                                sortColumnKey = sortColumnKey,
                                sortAscending = sortAscending,
                                onSortClick = onSortClick
                            )

                            HorizontalDivider(
                                thickness = ToolkitTheme.dimensions.borderUnselected,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            if (rows.isEmpty()) {
                                EmptyTablePlaceholder(
                                    emptyText = emptyText,
                                    modifier = Modifier.width(totalTableWidth)
                                )
                            } else {
                                Box(modifier = Modifier.width(totalTableWidth)) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = maxHeight)
                                            .verticalScroll(verticalScrollState)
                                            .padding(end = ToolkitTheme.dimensions.sduiScrollbarEndPadding)
                                    ) {
                                        rows.forEachIndexed { index, row ->
                                            val isSelected = row.key in selectedRowKeys
                                            val isFocused = index == focusedRowIndex
                                            TableDataRow(
                                                columns = columns,
                                                columnWidths = columnWidths,
                                                row = row,
                                                isSelected = isSelected,
                                                isKeyboardFocused = isFocused && selectedRowKeys.isEmpty(),
                                                clickable = onRowClick != null,
                                                onClick = {
                                                    focusedRowIndex = index
                                                    onRowClick?.invoke(row.key)
                                                }
                                            )
                                            if (index < rows.lastIndex) {
                                                HorizontalDivider(
                                                    thickness = ToolkitTheme.dimensions.borderThin,
                                                    color = MaterialTheme.colorScheme.outlineVariant.copy(
                                                        alpha = ToolkitTheme.opacity.divider
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    PlatformVerticalScrollbar(
                                        scrollState = verticalScrollState,
                                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                                    )
                                }
                            }
                        }

                        if (needsHorizontalScroll) {
                            PlatformHorizontalScrollbar(
                                scrollState = horizontalScrollState,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TableHeaderRow(
    columns: List<ToolkitTableColumn>,
    columnWidths: List<Dp>,
    totalWidth: Dp,
    sortColumnKey: String?,
    sortAscending: Boolean,
    onSortClick: ((columnKey: String, nextAscending: Boolean) -> Unit)?
) {
    Row(
        modifier = Modifier
            .width(totalWidth)
            .height(ToolkitTheme.dimensions.sduiTableHeaderHeight)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically
    ) {
        columns.forEachIndexed { index, column ->
            val colWidth = columnWidths[index]
            val isSorted = sortColumnKey == column.key
            val nextAscending = if (isSorted) !sortAscending else true
            val sortDescription = if (isSorted && sortAscending) {
                stringResource(Res.string.sdui_table_sort_descending, column.title)
            } else {
                stringResource(Res.string.sdui_table_sort_ascending, column.title)
            }
            val interactionSource = remember(column.key) { MutableInteractionSource() }
            val isHovered by interactionSource.collectIsHoveredAsState()

            val headerModifier = Modifier
                .width(colWidth)
                .fillMaxHeight()
                .then(
                    if (column.sortable && onSortClick != null) {
                        Modifier
                            .hoverable(interactionSource)
                            .background(
                                if (isHovered) {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = ToolkitTheme.opacity.subtleHighlight)
                                } else {
                                    ToolkitTheme.colors.transparent
                                }
                            )
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Button
                            ) {
                                onSortClick(column.key, nextAscending)
                            }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = ToolkitTheme.spacing.sm)

            Row(
                modifier = headerModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
            ) {
                Text(
                    text = column.title,
                    style = ToolkitTheme.typography.labelLarge,
                    fontWeight = if (isSorted) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSorted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (column.sortable) {
                    val sortIcon = when {
                        isSorted && sortAscending -> Icons.Default.ArrowUpward
                        isSorted && !sortAscending -> Icons.Default.ArrowDownward
                        else -> Icons.Default.SwapVert
                    }
                    Icon(
                        imageVector = sortIcon,
                        contentDescription = sortDescription,
                        tint = if (isSorted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconExtraSmall)
                    )
                }
            }
        }
    }
}

@Composable
private fun TableDataRow(
    columns: List<ToolkitTableColumn>,
    columnWidths: List<Dp>,
    row: ToolkitTableRow,
    isSelected: Boolean,
    isKeyboardFocused: Boolean,
    clickable: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember(row.key) { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = ToolkitTheme.opacity.settingsItemPressed)
        isHovered -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = ToolkitTheme.opacity.divider)
        isKeyboardFocused -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = ToolkitTheme.opacity.subtleHighlight)
        else -> ToolkitTheme.colors.transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ToolkitTheme.dimensions.sduiTableRowHeight)
            .background(backgroundColor)
            .hoverable(interactionSource)
            .semantics {
                if (clickable) {
                    role = Role.Button
                }
                selected = isSelected
            }
            .then(
                if (clickable) {
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = onClick
                        )
                } else {
                    Modifier
                }
            )
            .padding(vertical = ToolkitTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        columns.forEachIndexed { index, column ->
            val colWidth = columnWidths[index]
            val cellValue = row.cells[column.key].orEmpty()
            val showBadge = row.badgeColumnKey == column.key && cellValue.isNotBlank()

            Box(
                modifier = Modifier
                    .width(colWidth)
                    .padding(horizontal = ToolkitTheme.spacing.sm),
                contentAlignment = Alignment.CenterStart
            ) {
                if (showBadge) {
                    val resolvedColor = row.badgeColor ?: MaterialTheme.colorScheme.primary
                    Surface(
                        color = resolvedColor.copy(alpha = ToolkitTheme.opacity.subtleHighlight),
                        shape = ToolkitTheme.shapes.pill,
                        border = BorderStroke(
                            ToolkitTheme.dimensions.borderUnselected,
                            resolvedColor.copy(alpha = ToolkitTheme.opacity.borderLow)
                        )
                    ) {
                        Text(
                            text = cellValue,
                            style = ToolkitTheme.typography.labelSmall,
                            color = resolvedColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(
                                horizontal = ToolkitTheme.spacing.sm,
                                vertical = ToolkitTheme.spacing.xxs
                            )
                        )
                    }
                } else {
                    Text(
                        text = cellValue,
                        style = ToolkitTheme.typography.bodySmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTablePlaceholder(
    emptyText: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ToolkitTheme.spacing.md, vertical = ToolkitTheme.spacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emptyText ?: stringResource(Res.string.sdui_table_empty),
            style = ToolkitTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

internal fun resolveColumnWidths(
    columns: List<ToolkitTableColumn>,
    availableWidth: Dp,
    minWeightedWidth: Dp
): List<Dp> {
    if (columns.isEmpty()) return emptyList()
    val fixedSum = columns.fold(0.dp) { acc, col -> acc + (col.width ?: 0.dp) }
    val weightedColumns = columns.filter { it.width == null }
    val totalWeight = weightedColumns.sumOf { it.weight.coerceAtLeast(0.1f).toDouble() }.toFloat()
    val remaining = (availableWidth - fixedSum).coerceAtLeast(0.dp)

    return columns.map { col ->
        col.width ?: run {
            val fraction = if (totalWeight > 0f) col.weight.coerceAtLeast(0.1f) / totalWeight else 1f / weightedColumns.size
            val proportional = remaining * fraction
            if (proportional > minWeightedWidth) proportional else minWeightedWidth
        }
    }
}
