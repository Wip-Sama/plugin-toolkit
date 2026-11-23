package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.sdui.KeyValueListNode
import org.wip.plugintoolkit.api.sdui.MarkdownTextNode
import org.wip.plugintoolkit.api.sdui.MultiSelectDropdownNode
import org.wip.plugintoolkit.api.sdui.RangeSliderNode
import org.wip.plugintoolkit.api.sdui.TableNode
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.core.theme.StandardMotionScope
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.shared.components.ToolkitDataTable
import org.wip.plugintoolkit.shared.components.ToolkitTableColumn
import org.wip.plugintoolkit.shared.components.ToolkitTableRow
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.sdui_kv_copied
import plugintoolkit.composeapp.generated.resources.sdui_kv_copy_value
import plugintoolkit.composeapp.generated.resources.sdui_multi_select_all
import plugintoolkit.composeapp.generated.resources.sdui_multi_select_clear
import plugintoolkit.composeapp.generated.resources.sdui_multi_select_count
import plugintoolkit.composeapp.generated.resources.sdui_multi_select_filter_placeholder
import plugintoolkit.composeapp.generated.resources.sdui_multi_select_no_matches
import plugintoolkit.composeapp.generated.resources.sdui_multi_select_placeholder

private const val KV_COPIED_FEEDBACK_MS = 1800L
private const val RANGE_DISPLAY_SCALE = 100.0
private val MARKDOWN_BULLET_REGEX = Regex("^[-*]\\s+(.*)$")
private val MARKDOWN_NUMBERED_REGEX = Regex("^(\\d+\\.)\\s+(.*)$")

/**
 * Renders a [TableNode] using the shared desktop [ToolkitDataTable] component.
 */
@Composable
internal fun RenderTableNode(
    node: TableNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeColors = node.rows.mapNotNull { it.badgeColor }.distinct()
    val resolvedColorMap: Map<org.wip.plugintoolkit.api.sdui.SemanticColor, Color> = badgeColors.associateWith {
        it.toComposeColor()
    }

    val columns = remember(node.columns) {
        node.columns.map { col ->
            ToolkitTableColumn(
                key = col.key,
                title = col.title,
                weight = col.weight,
                width = col.widthDp?.dp,
                sortable = col.sortable
            )
        }
    }
    val rows = node.rows.map { row ->
        ToolkitTableRow(
            key = row.key,
            cells = row.cells,
            badgeColumnKey = row.badgeColumnKey,
            badgeColor = row.badgeColor?.let { resolvedColorMap[it] }
        )
    }

    val rowAction = node.action
    ToolkitDataTable(
        columns = columns,
        rows = rows,
        modifier = modifier,
        selectedRowKeys = node.selectedRowKeys,
        sortColumnKey = node.sortColumnKey,
        sortAscending = node.sortAscending,
        onSortClick = if (node.modifier.enabled) {
            { colKey, ascending ->
                onEvent(UiEvent.TableSorted(nodeId = node.id, columnKey = colKey, ascending = ascending))
            }
        } else {
            null
        },
        onRowClick = if (node.modifier.enabled) {
            { rowKey ->
                onEvent(
                    UiEvent.Click(
                        nodeId = node.id,
                        action = "${rowAction ?: "select_row"}:$rowKey"
                    )
                )
            }
        } else {
            null
        },
        emptyText = node.emptyText,
        maxHeight = node.maxHeightDp?.dp ?: ToolkitTheme.dimensions.sduiTableDefaultMaxHeight
    )
}

/**
 * Renders an aligned two-column property list ([KeyValueListNode]) with optional badges and copy buttons.
 */
@Composable
internal fun RenderKeyValueListNode(
    node: KeyValueListNode,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copiedKey by remember(node.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(copiedKey) {
        if (copiedKey != null) {
            delay(KV_COPIED_FEEDBACK_MS)
            copiedKey = null
        }
    }

    StandardMotionScope {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = ToolkitTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(
                ToolkitTheme.dimensions.borderUnselected,
                MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                node.title?.let { headerTitle ->
                    Text(
                        text = headerTitle,
                        style = ToolkitTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(
                                horizontal = ToolkitTheme.spacing.md,
                                vertical = ToolkitTheme.spacing.sm
                            )
                    )
                    HorizontalDivider(
                        thickness = ToolkitTheme.dimensions.borderUnselected,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }

                node.entries.forEachIndexed { index, entry ->
                    val entryCopied = copiedKey == entry.key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = ToolkitTheme.spacing.md,
                                vertical = ToolkitTheme.spacing.sm
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.md)
                    ) {
                        Text(
                            text = entry.label,
                            style = ToolkitTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.width(ToolkitTheme.dimensions.sduiKeyValueLabelWidth)
                        )

                        val badgeColor = entry.badgeColor?.toComposeColor()
                        if (badgeColor != null) {
                            Surface(
                                color = badgeColor.copy(alpha = ToolkitTheme.opacity.subtleHighlight),
                                shape = ToolkitTheme.shapes.pill,
                                border = BorderStroke(
                                    ToolkitTheme.dimensions.borderUnselected,
                                    badgeColor.copy(alpha = ToolkitTheme.opacity.borderLow)
                                ),
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Text(
                                    text = entry.value,
                                    style = ToolkitTheme.typography.labelMedium,
                                    color = badgeColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(
                                        horizontal = ToolkitTheme.spacing.sm,
                                        vertical = ToolkitTheme.spacing.xxs
                                    )
                                )
                            }
                        } else {
                            val valueStyle = if (entry.monospace) {
                                ToolkitTheme.codeMedium
                            } else {
                                ToolkitTheme.typography.bodyMedium
                            }
                            SelectionContainer(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.value,
                                    style = valueStyle,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (entry.copyable) {
                            val copyTooltip = stringResource(
                                if (entryCopied) Res.string.sdui_kv_copied else Res.string.sdui_kv_copy_value
                            )
                            CompositionLocalProvider(
                                LocalMinimumInteractiveComponentSize provides ToolkitTheme.dimensions.desktopTouchTargetSize
                            ) {
                                SduiTooltipBox(tooltip = copyTooltip) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                clipboard.setClipEntry(PlatformUtils.clipEntryOf(entry.value))
                                                copiedKey = entry.key
                                            }
                                        },
                                        modifier = Modifier
                                            .size(ToolkitTheme.dimensions.denseIconButtonSize)
                                            .pointerHoverIcon(PointerIcon.Hand)
                                    ) {
                                        Icon(
                                            imageVector = if (entryCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                            contentDescription = copyTooltip,
                                            tint = if (entryCopied) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (node.showDividers && index < node.entries.lastIndex) {
                        HorizontalDivider(
                            thickness = ToolkitTheme.dimensions.borderThin,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ToolkitTheme.opacity.divider)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders lightweight Markdown content ([MarkdownTextNode]) supporting headings,
 * bullet/numbered lists, bold, italic, strikethrough, inline monospace code, and clickable links.
 */
@Composable
internal fun RenderMarkdownTextNode(
    node: MarkdownTextNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHighest
    val codeColor = MaterialTheme.colorScheme.onSurface
    val headingColor = MaterialTheme.colorScheme.onSurface
    val h1Style = ToolkitTheme.typography.titleLarge
    val h2Style = ToolkitTheme.typography.titleMedium
    val h3Style = ToolkitTheme.typography.titleSmall
    val bodyStyle = ToolkitTheme.typography.bodyMedium
    val codeFamily = ToolkitTheme.codeFontFamily

    val annotatedText = remember(
        node.markdown,
        node.linkAction,
        primaryColor,
        codeBackground,
        codeColor,
        headingColor,
        h1Style,
        h2Style,
        h3Style,
        bodyStyle,
        codeFamily
    ) {
        buildMarkdownAnnotatedString(
            markdown = node.markdown,
            h1Style = h1Style.toSpanStyle().copy(color = headingColor, fontWeight = FontWeight.Bold),
            h2Style = h2Style.toSpanStyle().copy(color = headingColor, fontWeight = FontWeight.SemiBold),
            h3Style = h3Style.toSpanStyle().copy(color = headingColor, fontWeight = FontWeight.Medium),
            linkColor = primaryColor,
            codeBackground = codeBackground,
            codeColor = codeColor,
            codeFontFamily = codeFamily,
            onLinkClick = { url ->
                onEvent(
                    UiEvent.Click(
                        nodeId = node.id,
                        action = "${node.linkAction ?: "open_link"}:$url"
                    )
                )
            }
        )
    }

    val maxLines = node.maxLines?.coerceAtLeast(1) ?: Int.MAX_VALUE
    val overflow = if (node.maxLines != null) TextOverflow.Ellipsis else TextOverflow.Clip

    if (node.selectable) {
        SelectionContainer(modifier = modifier) {
            Text(
                text = annotatedText,
                style = bodyStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = maxLines,
                overflow = overflow,
                modifier = Modifier.pointerHoverIcon(PointerIcon.Text)
            )
        }
    } else {
        Text(
            text = annotatedText,
            style = bodyStyle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = overflow,
            modifier = modifier
        )
    }
}

/**
 * Renders a [MultiSelectDropdownNode] with searchable popup filtering, batch select-all / clear,
 * and interactive summary chips.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RenderMultiSelectDropdownNode(
    node: MultiSelectDropdownNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var filterQuery by remember { mutableStateOf("") }
    var currentSelection by remember(node.id, node.selectedKeys) {
        mutableStateOf(node.selectedKeys.filter { it in node.options.keys }.toSet())
    }
    val enabled = node.modifier.enabled

    val placeholderText = node.placeholder ?: stringResource(Res.string.sdui_multi_select_placeholder)
    val selectedLabels = node.options.entries
        .filter { it.key in currentSelection }
        .map { it.value }
    val displayValue = when {
        selectedLabels.isEmpty() -> placeholderText
        selectedLabels.size <= 2 -> selectedLabels.joinToString(", ")
        else -> stringResource(Res.string.sdui_multi_select_count, selectedLabels.size)
    }

    val filteredOptions = remember(node.options, filterQuery) {
        val query = filterQuery.trim().lowercase()
        if (query.isEmpty()) {
            node.options.entries.toList()
        } else {
            node.options.entries.filter { (key, label) ->
                label.lowercase().contains(query) || key.lowercase().contains(query)
            }
        }
    }

    fun updateSelection(nextKeys: Set<String>) {
        currentSelection = nextKeys
        onEvent(UiEvent.MultiOptionSelected(nodeId = node.id, selectedKeys = nextKeys))
    }

    Column(
        modifier = modifier.withFormWidthConstraint(node.modifier),
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = {
                if (enabled) {
                    expanded = !expanded
                    if (!expanded) {
                        filterQuery = ""
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = displayValue,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                enabled = enabled,
                label = node.label?.let { { Text(it, style = ToolkitTheme.typography.labelMedium) } },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
                shape = ToolkitTheme.shapes.medium,
                colors = sduiOutlinedTextFieldColors(),
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled)
                    .fillMaxWidth()
                    .then(if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier)
            )

            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = {
                    expanded = false
                    filterQuery = ""
                },
                shape = ToolkitTheme.shapes.medium,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                StandardMotionScope {
                    CompositionLocalProvider(
                        LocalMinimumInteractiveComponentSize provides ToolkitTheme.dimensions.desktopTouchTargetSize
                    ) {
                        if (node.searchable) {
                            OutlinedTextField(
                                value = filterQuery,
                                onValueChange = { filterQuery = it },
                                singleLine = true,
                                placeholder = {
                                    Text(
                                        text = stringResource(Res.string.sdui_multi_select_filter_placeholder),
                                        style = ToolkitTheme.typography.bodySmall
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(ToolkitTheme.dimensions.denseIconSize)
                                    )
                                },
                                shape = ToolkitTheme.shapes.small,
                                colors = sduiOutlinedTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(
                                        horizontal = ToolkitTheme.spacing.sm,
                                        vertical = ToolkitTheme.spacing.xs
                                    )
                            )
                        }

                        if (node.showSelectAll && node.options.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = ToolkitTheme.spacing.sm),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = { updateSelection(node.options.keys.toSet()) },
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text(
                                        text = stringResource(Res.string.sdui_multi_select_all),
                                        style = ToolkitTheme.typography.labelMedium
                                    )
                                }
                                TextButton(
                                    onClick = { updateSelection(emptySet()) },
                                    enabled = currentSelection.isNotEmpty(),
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                ) {
                                    Text(
                                        text = stringResource(Res.string.sdui_multi_select_clear),
                                        style = ToolkitTheme.typography.labelMedium
                                    )
                                }
                            }
                            HorizontalDivider(
                                thickness = ToolkitTheme.dimensions.borderThin,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }

                        if (filteredOptions.isEmpty()) {
                            Text(
                                text = stringResource(Res.string.sdui_multi_select_no_matches),
                                style = ToolkitTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    horizontal = ToolkitTheme.spacing.md,
                                    vertical = ToolkitTheme.spacing.sm
                                )
                            )
                        } else {
                            filteredOptions.forEach { (key, optionLabel) ->
                                val isChecked = key in currentSelection
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = optionLabel,
                                            style = ToolkitTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    leadingIcon = {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = null
                                        )
                                    },
                                    onClick = {
                                        val next = if (isChecked) currentSelection - key else currentSelection + key
                                        updateSelection(next)
                                    },
                                    modifier = Modifier.pointerHoverIcon(PointerIcon.Hand)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (currentSelection.isNotEmpty()) {
            CompositionLocalProvider(
                LocalMinimumInteractiveComponentSize provides ToolkitTheme.dimensions.desktopTouchTargetSize
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs)
                ) {
                    node.options.entries
                        .filter { it.key in currentSelection }
                        .forEach { (key, optionLabel) ->
                            InputChip(
                                selected = true,
                                onClick = {
                                    if (enabled) {
                                        updateSelection(currentSelection - key)
                                    }
                                },
                                enabled = enabled,
                                label = {
                                    Text(
                                        text = optionLabel,
                                        style = ToolkitTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = ToolkitTheme.dimensions.sduiBadgeMaxWidth)
                                    )
                                },
                                trailingIcon = if (enabled) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = null,
                                            modifier = Modifier.size(ToolkitTheme.dimensions.iconExtraSmall)
                                        )
                                    }
                                } else {
                                    null
                                },
                                modifier = if (enabled) Modifier.pointerHoverIcon(PointerIcon.Hand) else Modifier
                            )
                        }
                }
            }
        }
    }
}

/**
 * Renders a dual-thumb [RangeSliderNode] emitting [UiEvent.RangeChanged].
 */
@Composable
internal fun RenderRangeSliderNode(
    node: RangeSliderNode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val minF = node.min.toFloat()
    val maxF = node.max.toFloat()
    var sliderPosition by remember(node.id, node.startValue, node.endValue, node.min, node.max) {
        val clampedStart = node.startValue.toFloat().coerceIn(minF, maxF)
        val clampedEnd = node.endValue.toFloat().coerceIn(clampedStart, maxF)
        mutableStateOf(clampedStart..clampedEnd)
    }

    val rangeText = remember(sliderPosition, node.unitSuffix) {
        val startFormatted = formatRangeValue(sliderPosition.start.toDouble())
        val endFormatted = formatRangeValue(sliderPosition.endInclusive.toDouble())
        val suffix = node.unitSuffix?.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()
        "$startFormatted \u2013 $endFormatted$suffix"
    }

    Column(
        modifier = modifier.withFormWidthConstraint(node.modifier),
        verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xxs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            node.label?.let { labelText ->
                Text(
                    text = labelText,
                    style = ToolkitTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = rangeText,
                style = ToolkitTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        RangeSlider(
            value = sliderPosition,
            onValueChange = { nextRange ->
                sliderPosition = nextRange
                onEvent(
                    UiEvent.RangeChanged(
                        nodeId = node.id,
                        startValue = nextRange.start.toDouble(),
                        endValue = nextRange.endInclusive.toDouble()
                    )
                )
            },
            valueRange = minF..maxF,
            steps = node.steps.coerceAtLeast(0),
            enabled = node.modifier.enabled,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun formatRangeValue(value: Double): String {
    val rounded = (value * RANGE_DISPLAY_SCALE).roundToLong()
    val whole = rounded / RANGE_DISPLAY_SCALE.toLong()
    val frac = kotlin.math.abs(rounded % RANGE_DISPLAY_SCALE.toLong())
    return if (frac == 0L) {
        whole.toString()
    } else {
        (rounded / RANGE_DISPLAY_SCALE).toString()
    }
}

internal fun buildMarkdownAnnotatedString(
    markdown: String,
    h1Style: SpanStyle,
    h2Style: SpanStyle,
    h3Style: SpanStyle,
    linkColor: Color,
    codeBackground: Color,
    codeColor: Color,
    codeFontFamily: FontFamily,
    onLinkClick: (String) -> Unit
): AnnotatedString = buildAnnotatedString {
    val lines = markdown.replace("\r\n", "\n").split("\n")
    lines.forEachIndexed { index, rawLine ->
        val trimmed = rawLine.trimEnd()
        when {
            trimmed.startsWith("### ") -> {
                withStyle(h3Style) {
                    appendInlineMarkdown(
                        text = trimmed.removePrefix("### "),
                        linkColor = linkColor,
                        codeBackground = codeBackground,
                        codeColor = codeColor,
                        codeFontFamily = codeFontFamily,
                        onLinkClick = onLinkClick
                    )
                }
            }

            trimmed.startsWith("## ") -> {
                withStyle(h2Style) {
                    appendInlineMarkdown(
                        text = trimmed.removePrefix("## "),
                        linkColor = linkColor,
                        codeBackground = codeBackground,
                        codeColor = codeColor,
                        codeFontFamily = codeFontFamily,
                        onLinkClick = onLinkClick
                    )
                }
            }

            trimmed.startsWith("# ") -> {
                withStyle(h1Style) {
                    appendInlineMarkdown(
                        text = trimmed.removePrefix("# "),
                        linkColor = linkColor,
                        codeBackground = codeBackground,
                        codeColor = codeColor,
                        codeFontFamily = codeFontFamily,
                        onLinkClick = onLinkClick
                    )
                }
            }

            else -> {
                val bulletMatch = MARKDOWN_BULLET_REGEX.matchEntire(trimmed)
                val numberedMatch = MARKDOWN_NUMBERED_REGEX.matchEntire(trimmed)
                when {
                    bulletMatch != null -> {
                        append("  \u2022  ")
                        appendInlineMarkdown(
                            text = bulletMatch.groupValues[1],
                            linkColor = linkColor,
                            codeBackground = codeBackground,
                            codeColor = codeColor,
                            codeFontFamily = codeFontFamily,
                            onLinkClick = onLinkClick
                        )
                    }

                    numberedMatch != null -> {
                        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                            append("  ${numberedMatch.groupValues[1]} ")
                        }
                        appendInlineMarkdown(
                            text = numberedMatch.groupValues[2],
                            linkColor = linkColor,
                            codeBackground = codeBackground,
                            codeColor = codeColor,
                            codeFontFamily = codeFontFamily,
                            onLinkClick = onLinkClick
                        )
                    }

                    else -> {
                        appendInlineMarkdown(
                            text = rawLine,
                            linkColor = linkColor,
                            codeBackground = codeBackground,
                            codeColor = codeColor,
                            codeFontFamily = codeFontFamily,
                            onLinkClick = onLinkClick
                        )
                    }
                }
            }
        }
        if (index < lines.lastIndex) {
            append("\n")
        }
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    linkColor: Color,
    codeBackground: Color,
    codeColor: Color,
    codeFontFamily: FontFamily,
    onLinkClick: (String) -> Unit
) {
    var i = 0
    while (i < text.length) {
        when {
            text[i] == '\\' && i + 1 < text.length -> {
                append(text[i + 1])
                i += 2
            }

            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendInlineMarkdown(
                            text = text.substring(i + 2, end),
                            linkColor = linkColor,
                            codeBackground = codeBackground,
                            codeColor = codeColor,
                            codeFontFamily = codeFontFamily,
                            onLinkClick = onLinkClick
                        )
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }

            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        appendInlineMarkdown(
                            text = text.substring(i + 2, end),
                            linkColor = linkColor,
                            codeBackground = codeBackground,
                            codeColor = codeColor,
                            codeFontFamily = codeFontFamily,
                            onLinkClick = onLinkClick
                        )
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }

            text[i] == '_' || text[i] == '*' -> {
                val delimiter = text[i]
                val end = text.indexOf(delimiter, i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInlineMarkdown(
                            text = text.substring(i + 1, end),
                            linkColor = linkColor,
                            codeBackground = codeBackground,
                            codeColor = codeColor,
                            codeFontFamily = codeFontFamily,
                            onLinkClick = onLinkClick
                        )
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }

            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = codeFontFamily,
                            color = codeColor,
                            background = codeBackground
                        )
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }

            text[i] == '[' -> {
                val labelEnd = text.indexOf(']', i + 1)
                if (labelEnd != -1 && labelEnd + 1 < text.length && text[labelEnd + 1] == '(') {
                    val urlEnd = text.indexOf(')', labelEnd + 2)
                    if (urlEnd != -1) {
                        val label = text.substring(i + 1, labelEnd)
                        val url = text.substring(labelEnd + 2, urlEnd)
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = url,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = linkColor,
                                        textDecoration = TextDecoration.Underline,
                                        fontWeight = FontWeight.Medium
                                    )
                                ),
                                linkInteractionListener = { onLinkClick(url) }
                            )
                        ) {
                            append(label)
                        }
                        i = urlEnd + 1
                    } else {
                        append(text[i])
                        i++
                    }
                } else {
                    append(text[i])
                    i++
                }
            }

            else -> {
                append(text[i])
                i++
            }
        }
    }
}
