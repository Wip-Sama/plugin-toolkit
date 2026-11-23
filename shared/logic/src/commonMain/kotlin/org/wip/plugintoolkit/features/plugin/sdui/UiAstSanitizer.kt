package org.wip.plugintoolkit.features.plugin.sdui

import org.wip.plugintoolkit.api.sdui.AccordionNode
import org.wip.plugintoolkit.api.sdui.BoardLayer
import org.wip.plugintoolkit.api.sdui.BoardNode
import org.wip.plugintoolkit.api.sdui.BoxNode
import org.wip.plugintoolkit.api.sdui.BreadcrumbsNode
import org.wip.plugintoolkit.api.sdui.ButtonGroupNode
import org.wip.plugintoolkit.api.sdui.CardNode
import org.wip.plugintoolkit.api.sdui.CodeBlockNode
import org.wip.plugintoolkit.api.sdui.ColorPickerNode
import org.wip.plugintoolkit.api.sdui.ColumnNode
import org.wip.plugintoolkit.api.sdui.DialogNode
import org.wip.plugintoolkit.api.sdui.ErrorBoundaryNode
import org.wip.plugintoolkit.api.sdui.FilePickerNode
import org.wip.plugintoolkit.api.sdui.FloatingPanelNode
import org.wip.plugintoolkit.api.sdui.FlowRowNode
import org.wip.plugintoolkit.api.sdui.GridNode
import org.wip.plugintoolkit.api.sdui.KeyValueListNode
import org.wip.plugintoolkit.api.sdui.ListItemNode
import org.wip.plugintoolkit.api.sdui.LoadingIndicatorNode
import org.wip.plugintoolkit.api.sdui.MarkdownTextNode
import org.wip.plugintoolkit.api.sdui.MenuNode
import org.wip.plugintoolkit.api.sdui.MultiSelectDropdownNode
import org.wip.plugintoolkit.api.sdui.NumberInputNode
import org.wip.plugintoolkit.api.sdui.PaginationNode
import org.wip.plugintoolkit.api.sdui.ProgressBarNode
import org.wip.plugintoolkit.api.sdui.RadioGroupNode
import org.wip.plugintoolkit.api.sdui.RangeSliderNode
import org.wip.plugintoolkit.api.sdui.RowNode
import org.wip.plugintoolkit.api.sdui.ScaffoldNode
import org.wip.plugintoolkit.api.sdui.ScrollableColumnNode
import org.wip.plugintoolkit.api.sdui.ScrollableRowNode
import org.wip.plugintoolkit.api.sdui.SidebarNode
import org.wip.plugintoolkit.api.sdui.SliderNode
import org.wip.plugintoolkit.api.sdui.SplitButtonNode
import org.wip.plugintoolkit.api.sdui.SplitPaneNode
import org.wip.plugintoolkit.api.sdui.StepperNode
import org.wip.plugintoolkit.api.sdui.TableNode
import org.wip.plugintoolkit.api.sdui.TabsNode
import org.wip.plugintoolkit.api.sdui.TextNode
import org.wip.plugintoolkit.api.sdui.TreeNodeItem
import org.wip.plugintoolkit.api.sdui.TreeViewNode
import org.wip.plugintoolkit.api.sdui.UiNode
import org.wip.plugintoolkit.api.sdui.UiNodeTree

/**
 * Pure non-composable AST sanitizer that runs on background dispatchers before an immutable
 * UiNodeTree snapshot is emitted to the Compose Recomposer.
 *
 * Guarantees crash-resilience by enforcing nesting limits, detecting ID collisions that
 * would crash Compose's key(node.id) runtime with an IllegalArgumentException, and validating
 * layout/component invariant ranges.
 */
object UiAstSanitizer {
    const val DEFAULT_MAX_DEPTH = 32
    const val DEFAULT_MAX_NODES = 4000

    fun sanitize(
        tree: UiNodeTree,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        maxNodes: Int = DEFAULT_MAX_NODES
    ): UiNodeTree {
        val seenIds = mutableSetOf<String>()
        var nodeCount = 0

        fun sanitizeNode(node: UiNode, depth: Int): UiNode {
            if (depth > maxDepth) {
                return ErrorBoundaryNode(
                    id = "${node.id}_depth_exceeded",
                    errorMessage = "Maximum UI nesting depth of $maxDepth exceeded",
                    failedNodeType = node::class.simpleName
                )
            }

            if (nodeCount >= maxNodes) {
                return ErrorBoundaryNode(
                    id = "${node.id}_node_count_exceeded",
                    errorMessage = "Maximum UI node count of $maxNodes exceeded",
                    failedNodeType = node::class.simpleName
                )
            }

            nodeCount++

            // Check for blank node IDs
            if (node.id.isBlank()) {
                return ErrorBoundaryNode(
                    id = "blank_id_$nodeCount",
                    errorMessage = "Node ID must not be blank",
                    failedNodeType = node::class.simpleName
                )
            }

            // Check for duplicate node.id collisions
            if (node.id in seenIds) {
                return ErrorBoundaryNode(
                    id = "${node.id}_collision_$nodeCount",
                    errorMessage = "Duplicate node ID collision detected: '${node.id}'",
                    failedNodeType = node::class.simpleName
                )
            }
            seenIds.add(node.id)

            // Validate modifier properties
            val weight = node.modifier.weight
            if (weight != null && (weight <= 0f || weight.isNaN() || weight.isInfinite())) {
                return ErrorBoundaryNode(
                    id = node.id,
                    errorMessage = "Invalid layout weight: $weight (must be > 0.0 and finite)",
                    failedNodeType = node::class.simpleName
                )
            }

            val fillMaxWidth = node.modifier.fillMaxWidth
            if (fillMaxWidth != null && (fillMaxWidth < 0f || fillMaxWidth > 1f || fillMaxWidth.isNaN())) {
                return ErrorBoundaryNode(
                    id = node.id,
                    errorMessage = "Invalid fillMaxWidth: $fillMaxWidth (must be in 0.0..1.0)",
                    failedNodeType = node::class.simpleName
                )
            }

            val fillMaxHeight = node.modifier.fillMaxHeight
            if (fillMaxHeight != null && (fillMaxHeight < 0f || fillMaxHeight > 1f || fillMaxHeight.isNaN())) {
                return ErrorBoundaryNode(
                    id = node.id,
                    errorMessage = "Invalid fillMaxHeight: $fillMaxHeight (must be in 0.0..1.0)",
                    failedNodeType = node::class.simpleName
                )
            }

            val aspectRatio = node.modifier.aspectRatio
            if (aspectRatio != null && (aspectRatio <= 0f || aspectRatio.isNaN() || aspectRatio.isInfinite())) {
                return ErrorBoundaryNode(
                    id = node.id,
                    errorMessage = "Invalid aspectRatio: $aspectRatio (must be > 0.0 and finite)",
                    failedNodeType = node::class.simpleName
                )
            }

            val widthDp = node.modifier.widthDp
            val heightDp = node.modifier.heightDp
            val minWidthDp = node.modifier.minWidthDp
            val maxWidthDp = node.modifier.maxWidthDp
            val minHeightDp = node.modifier.minHeightDp
            val maxHeightDp = node.modifier.maxHeightDp
            if (widthDp != null && widthDp < 0) {
                return ErrorBoundaryNode(
                    id = node.id,
                    errorMessage = "Invalid widthDp: $widthDp (must be >= 0)",
                    failedNodeType = node::class.simpleName
                )
            }
            if (heightDp != null && heightDp < 0) {
                return ErrorBoundaryNode(
                    id = node.id,
                    errorMessage = "Invalid heightDp: $heightDp (must be >= 0)",
                    failedNodeType = node::class.simpleName
                )
            }
            if ((minWidthDp != null && minWidthDp < 0) ||
                (maxWidthDp != null && maxWidthDp < 0) ||
                (minWidthDp != null && maxWidthDp != null && minWidthDp > maxWidthDp)
            ) {
                return ErrorBoundaryNode(
                    id = node.id,
                    errorMessage = "Invalid width bounds: minWidthDp=$minWidthDp, maxWidthDp=$maxWidthDp",
                    failedNodeType = node::class.simpleName
                )
            }
            if ((minHeightDp != null && minHeightDp < 0) ||
                (maxHeightDp != null && maxHeightDp < 0) ||
                (minHeightDp != null && maxHeightDp != null && minHeightDp > maxHeightDp)
            ) {
                return ErrorBoundaryNode(
                    id = node.id,
                    errorMessage = "Invalid height bounds: minHeightDp=$minHeightDp, maxHeightDp=$maxHeightDp",
                    failedNodeType = node::class.simpleName
                )
            }

            for (shortcut in node.modifier.shortcuts) {
                if (shortcut.key.isBlank() || shortcut.action.isBlank()) {
                    return ErrorBoundaryNode(
                        id = node.id,
                        errorMessage = "Invalid UiShortcut on '${node.id}': key and action must not be blank",
                        failedNodeType = node::class.simpleName
                    )
                }
            }

            node.modifier.dropTarget?.let { dropTarget ->
                if (dropTarget.action.isBlank()) {
                    return ErrorBoundaryNode(
                        id = node.id,
                        errorMessage = "Invalid UiDropTarget on '${node.id}': action must not be blank",
                        failedNodeType = node::class.simpleName
                    )
                }
                if (!dropTarget.acceptFiles && !dropTarget.acceptDirectories) {
                    return ErrorBoundaryNode(
                        id = node.id,
                        errorMessage = "Invalid UiDropTarget on '${node.id}': must accept files or directories",
                        failedNodeType = node::class.simpleName
                    )
                }
                val maxFiles = dropTarget.maxFiles
                if (maxFiles != null && maxFiles <= 0) {
                    return ErrorBoundaryNode(
                        id = node.id,
                        errorMessage = "Invalid UiDropTarget on '${node.id}': maxFiles=$maxFiles must be > 0",
                        failedNodeType = node::class.simpleName
                    )
                }
            }

            for (menuItem in node.modifier.contextMenu) {
                if (menuItem.key.isBlank() || menuItem.label.isBlank()) {
                    return ErrorBoundaryNode(
                        id = node.id,
                        errorMessage = "Invalid contextMenu item on '${node.id}': key and label must not be blank",
                        failedNodeType = node::class.simpleName
                    )
                }
            }

            // Validate component-specific invariants
            if (node is TextNode) {
                val maxLines = node.maxLines
                if (maxLines != null && maxLines <= 0) {
                    return ErrorBoundaryNode(
                        id = node.id,
                        errorMessage = "Invalid TextNode '${node.id}': maxLines=$maxLines must be > 0",
                        failedNodeType = "TextNode"
                    )
                }
            }

            if (node is RadioGroupNode) {
                val seenOptionKeys = mutableSetOf<String>()
                for (option in node.options) {
                    if (option.key.isBlank() || option.label.isBlank() || !seenOptionKeys.add(option.key)) {
                        return ErrorBoundaryNode(
                            id = node.id,
                            errorMessage = "Invalid RadioGroupNode '${node.id}': option keys/labels must be non-blank and unique",
                            failedNodeType = "RadioGroupNode"
                        )
                    }
                }
            }

            if (node is NumberInputNode) {
                val invalidFloat = node.value.isNaN() || node.value.isInfinite() ||
                    node.step.isNaN() || node.step.isInfinite() || node.step <= 0.0 ||
                    (node.min != null && (node.min!!.isNaN() || node.min!!.isInfinite())) ||
                    (node.max != null && (node.max!!.isNaN() || node.max!!.isInfinite())) ||
                    (node.min != null && node.max != null && node.min!! > node.max!!)
                if (invalidFloat) {
                    return ErrorBoundaryNode(
                        id = node.id,
                        errorMessage = "Malformed number input range on '${node.id}': min=${node.min}, max=${node.max}, step=${node.step}, value=${node.value}",
                        failedNodeType = "NumberInputNode"
                    )
                }
            }

            if (node is CodeBlockNode) {
                val maxHeight = node.maxHeightDp
                if (maxHeight != null && maxHeight <= 0) {
                    return ErrorBoundaryNode(
                        id = node.id,
                        errorMessage = "Invalid CodeBlockNode '${node.id}': maxHeightDp=$maxHeight must be > 0",
                        failedNodeType = "CodeBlockNode"
                    )
                }
            }

            if (node is SliderNode) {
                if (node.min.isNaN() || node.max.isNaN() || node.value.isNaN() || node.min >= node.max) {
                    return ErrorBoundaryNode(
                        id = node.id,
                        errorMessage = "Malformed slider range: min=${node.min}, max=${node.max}, value=${node.value}",
                        failedNodeType = "SliderNode"
                    )
                }
            }

            if (node is ProgressBarNode) {
                val safeP = sanitizeUnitFraction(node.progress)
                val safeSp = sanitizeUnitFraction(node.secondaryProgress)
                if (safeP != node.progress || safeSp != node.secondaryProgress) {
                    return node.copy(progress = safeP, secondaryProgress = safeSp)
                }
            }

            if (node is LoadingIndicatorNode) {
                val safeP = sanitizeUnitFraction(node.progress)
                if (safeP != node.progress) {
                    return node.copy(progress = safeP)
                }
            }

            if (node is ButtonGroupNode) {
                if (node.items.any { it.key.isBlank() || it.label.isBlank() }) {
                    return ErrorBoundaryNode(
                        id = node.id,
                        errorMessage = "Invalid ButtonGroupNode '${node.id}': item keys and labels must not be blank",
                        failedNodeType = "ButtonGroupNode"
                    )
                }
            }

            if (node is SplitButtonNode) {
                if (node.primaryAction.isBlank() || node.menuItems.any { it.key.isBlank() || it.label.isBlank() }) {
                    return ErrorBoundaryNode(
                        id = node.id,
                        errorMessage = "Invalid SplitButtonNode '${node.id}': primaryAction and menu item keys/labels must not be blank",
                        failedNodeType = "SplitButtonNode"
                    )
                }
            }

            if (node is TableNode) {
                val maxHeight = node.maxHeightDp
                if (maxHeight != null && maxHeight <= 0) {
                    return ErrorBoundaryNode(
                        id = node.id,
                        errorMessage = "Invalid TableNode '${node.id}': maxHeightDp=$maxHeight must be > 0",
                        failedNodeType = "TableNode"
                    )
                }
                val seenColumnKeys = mutableSetOf<String>()
                for (column in node.columns) {
                    val invalidWidth = column.weight.isNaN() || column.weight.isInfinite() || column.weight <= 0f ||
                        (column.widthDp != null && column.widthDp!! <= 0)
                    if (column.key.isBlank() || column.title.isBlank() || !seenColumnKeys.add(column.key) || invalidWidth) {
                        return ErrorBoundaryNode(
                            id = node.id,
                            errorMessage = "Invalid TableNode column '${column.key}' in '${node.id}': keys/titles must be non-blank/unique and dimensions positive",
                            failedNodeType = "TableNode"
                        )
                    }
                }
                val seenRowKeys = mutableSetOf<String>()
                for (row in node.rows) {
                    if (row.key.isBlank() || !seenRowKeys.add(row.key)) {
                        return ErrorBoundaryNode(
                            id = node.id,
                            errorMessage = "Invalid TableNode row '${row.key}' in '${node.id}': row keys must be non-blank and unique",
                            failedNodeType = "TableNode"
                        )
                    }
                }
            }

            if (node is KeyValueListNode) {
                val seenEntryKeys = mutableSetOf<String>()
                for (entry in node.entries) {
                    if (entry.key.isBlank() || entry.label.isBlank() || !seenEntryKeys.add(entry.key)) {
                        return ErrorBoundaryNode(
                            id = node.id,
                            errorMessage = "Invalid KeyValueListNode entry '${entry.key}' in '${node.id}': keys and labels must be non-blank and unique",
                            failedNodeType = "KeyValueListNode"
                        )
                    }
                }
            }

            if (node is MarkdownTextNode) {
                val maxLines = node.maxLines
                if (maxLines != null && maxLines <= 0) {
                    return ErrorBoundaryNode(
                        id = node.id,
                        errorMessage = "Invalid MarkdownTextNode '${node.id}': maxLines=$maxLines must be > 0",
                        failedNodeType = "MarkdownTextNode"
                    )
                }
            }

            if (node is MultiSelectDropdownNode) {
                if (node.options.keys.any { it.isBlank() }) {
                    return ErrorBoundaryNode(
                        id = node.id,
                        errorMessage = "Invalid MultiSelectDropdownNode '${node.id}': option keys must not be blank",
                        failedNodeType = "MultiSelectDropdownNode"
                    )
                }
            }

            if (node is RangeSliderNode) {
                val invalidRange = node.min.isNaN() || node.min.isInfinite() ||
                    node.max.isNaN() || node.max.isInfinite() ||
                    node.startValue.isNaN() || node.startValue.isInfinite() ||
                    node.endValue.isNaN() || node.endValue.isInfinite() ||
                    node.min >= node.max ||
                    node.startValue > node.endValue ||
                    node.steps < 0
                if (invalidRange) {
                    return ErrorBoundaryNode(
                        id = node.id,
                        errorMessage = "Malformed range slider on '${node.id}': min=${node.min}, max=${node.max}, start=${node.startValue}, end=${node.endValue}",
                        failedNodeType = "RangeSliderNode"
                    )
                }
            }

            if (node is TreeViewNode) {
                val maxHeight = node.maxHeightDp
                if (maxHeight != null && maxHeight <= 0) {
                    return ErrorBoundaryNode(
                        id = node.id,
                        errorMessage = "Invalid TreeViewNode '${node.id}': maxHeightDp=$maxHeight must be > 0",
                        failedNodeType = "TreeViewNode"
                    )
                }
                val seenKeys = mutableSetOf<String>()
                fun validateTreeItems(items: List<TreeNodeItem>): String? {
                    for (item in items) {
                        if (item.key.isBlank() || item.label.isBlank() || !seenKeys.add(item.key)) {
                            return item.key
                        }
                        val invalidChild = validateTreeItems(item.children)
                        if (invalidChild != null) return invalidChild
                    }
                    return null
                }
                val badKey = validateTreeItems(node.items)
                if (badKey != null) {
                    return ErrorBoundaryNode(
                        id = node.id,
                        errorMessage = "Invalid or duplicate TreeNodeItem key '$badKey' in TreeViewNode '${node.id}'",
                        failedNodeType = "TreeViewNode"
                    )
                }
            }

            if (node is ColorPickerNode && node.colorHex.isBlank()) {
                return ErrorBoundaryNode(
                    id = node.id,
                    errorMessage = "Invalid ColorPickerNode '${node.id}': colorHex must not be blank",
                    failedNodeType = "ColorPickerNode"
                )
            }

            if (node is BreadcrumbsNode) {
                val seenCrumbKeys = mutableSetOf<String>()
                for (item in node.items) {
                    if (item.key.isBlank() || item.label.isBlank() || !seenCrumbKeys.add(item.key)) {
                        return ErrorBoundaryNode(
                            id = node.id,
                            errorMessage = "Invalid BreadcrumbsNode item '${item.key}' in '${node.id}': keys and labels must be non-blank and unique",
                            failedNodeType = "BreadcrumbsNode"
                        )
                    }
                }
            }

            if (node is PaginationNode) {
                val safeTotal = node.totalPages.coerceAtLeast(1)
                val safePage = node.currentPage.coerceIn(1, safeTotal)
                val pageSize = node.pageSize
                if (pageSize != null && pageSize <= 0) {
                    return ErrorBoundaryNode(
                        id = node.id,
                        errorMessage = "Invalid PaginationNode '${node.id}': pageSize=$pageSize must be > 0",
                        failedNodeType = "PaginationNode"
                    )
                }
                if (safeTotal != node.totalPages || safePage != node.currentPage) {
                    return node.copy(currentPage = safePage, totalPages = safeTotal)
                }
            }

            if (node is StepperNode) {
                val seenStepKeys = mutableSetOf<String>()
                for (step in node.steps) {
                    if (step.key.isBlank() || step.label.isBlank() || !seenStepKeys.add(step.key)) {
                        return ErrorBoundaryNode(
                            id = node.id,
                            errorMessage = "Invalid StepperNode step '${step.key}' in '${node.id}': keys and labels must be non-blank and unique",
                            failedNodeType = "StepperNode"
                        )
                    }
                }
            }

            if (node is MenuNode) {
                for (item in node.items) {
                    if (item.key.isBlank() || item.label.isBlank()) {
                        return ErrorBoundaryNode(
                            id = node.id,
                            errorMessage = "Invalid MenuNode item '${item.key}' in '${node.id}': key and label must not be blank",
                            failedNodeType = "MenuNode"
                        )
                    }
                }
            }

            if (node is FilePickerNode && node.defaultFileName.isBlank()) {
                return node.copy(defaultFileName = "export")
            }

            // Recursively sanitize children for container nodes
            return when (node) {
                is ColumnNode -> node.copy(children = node.children.map { sanitizeNode(it, depth + 1) })
                is RowNode -> node.copy(children = node.children.map { sanitizeNode(it, depth + 1) })
                is ScrollableRowNode -> node.copy(children = node.children.map { sanitizeNode(it, depth + 1) })
                is FlowRowNode -> node.copy(
                    maxItemsInEachRow = node.maxItemsInEachRow?.coerceAtLeast(1),
                    children = node.children.map { sanitizeNode(it, depth + 1) }
                )
                is BoxNode -> node.copy(children = node.children.map { sanitizeNode(it, depth + 1) })
                is CardNode -> node.copy(children = node.children.map { sanitizeNode(it, depth + 1) })
                is ScrollableColumnNode -> node.copy(
                    scrollToIndex = node.scrollToIndex?.coerceAtLeast(0),
                    children = node.children.map { sanitizeNode(it, depth + 1) }
                )
                is GridNode -> {
                    val safeColumns = node.columns?.coerceIn(1, 12)
                    val safeMinWidth = if (node.minItemWidthDp.isNaN() || node.minItemWidthDp.isInfinite()) {
                        156f
                    } else {
                        node.minItemWidthDp.coerceIn(72f, 600f)
                    }
                    node.copy(
                        columns = safeColumns,
                        minItemWidthDp = safeMinWidth,
                        scrollToIndex = node.scrollToIndex?.coerceAtLeast(0),
                        children = node.children.map { sanitizeNode(it, depth + 1) }
                    )
                }
                is BreadcrumbsNode -> node.copy(maxVisibleItems = node.maxVisibleItems.coerceAtLeast(2))
                is TabsNode -> node.copy(content = sanitizeNode(node.content, depth + 1))
                is ListItemNode -> node.copy(trailingContent = node.trailingContent.map { sanitizeNode(it, depth + 1) })
                is DialogNode -> node.copy(children = node.children.map { sanitizeNode(it, depth + 1) })
                is SplitPaneNode -> {
                    val safeFraction = if (node.splitFraction.isNaN() || node.splitFraction.isInfinite()) {
                        0.35f
                    } else {
                        node.splitFraction.coerceIn(0.05f, 0.95f)
                    }
                    node.copy(
                        first = sanitizeNode(node.first, depth + 1),
                        second = sanitizeNode(node.second, depth + 1),
                        splitFraction = safeFraction
                    )
                }
                is ScaffoldNode -> node.copy(
                    content = sanitizeNode(node.content, depth + 1),
                    leftSidebar = node.leftSidebar?.let { sanitizeNode(it, depth + 1) },
                    rightSidebar = node.rightSidebar?.let { sanitizeNode(it, depth + 1) },
                    topBar = node.topBar?.let { sanitizeNode(it, depth + 1) },
                    bottomBar = node.bottomBar?.let { sanitizeNode(it, depth + 1) },
                    floatingPanels = node.floatingPanels.map {
                        sanitizeNode(it, depth + 1) as? FloatingPanelNode ?: it
                    }
                )
                is FloatingPanelNode -> node.copy(children = node.children.map { sanitizeNode(it, depth + 1) })
                is SidebarNode -> node.copy(children = node.children.map { sanitizeNode(it, depth + 1) })
                is AccordionNode -> node.copy(children = node.children.map { sanitizeNode(it, depth + 1) })
                is BoardNode -> {
                    val seenLayerIds = mutableSetOf<String>()
                    val sanitizedLayers = mutableListOf<BoardLayer>()
                    for (layer in node.layers) {
                        if (layer.id.isBlank() || !seenLayerIds.add(layer.id)) {
                            return ErrorBoundaryNode(
                                id = node.id,
                                errorMessage = "Invalid or duplicate BoardLayer ID '${layer.id}' in BoardNode '${node.id}'",
                                failedNodeType = "BoardNode"
                            )
                        }
                        val sanitizedLayer = when (layer) {
                            is BoardLayer.PathLayer -> layer.copy(
                                path = layer.path.copy(
                                    points = layer.path.points.map { it.coercedInBounds() }
                                )
                            )
                            is BoardLayer.TextLayer -> layer.copy(
                                position = layer.position.coercedInBounds(),
                                size = layer.size?.coercedInBounds()
                            )
                            is BoardLayer.ImageLayer -> layer.copy(
                                position = layer.position.coercedInBounds(),
                                size = layer.size.coercedInBounds(),
                                opacity = layer.opacity.coerceIn(0f, 1f)
                            )
                            is BoardLayer.ShapeLayer -> layer.copy(
                                position = layer.position.coercedInBounds(),
                                size = layer.size.coercedInBounds()
                            )
                            is BoardLayer.ComponentLayer -> layer.copy(
                                position = layer.position.coercedInBounds(),
                                size = layer.size?.coercedInBounds(),
                                content = sanitizeNode(layer.content, depth + 1)
                            )
                        }
                        sanitizedLayers.add(sanitizedLayer)
                    }
                    val vp = node.viewportConfig
                    val safeMinScale = if (vp.minScale.isNaN() || vp.minScale.isInfinite()) 0.25f else vp.minScale.coerceIn(0.25f, 4.0f)
                    val safeMaxScale = if (vp.maxScale.isNaN() || vp.maxScale.isInfinite()) 4.0f else vp.maxScale.coerceIn(safeMinScale, 4.0f)
                    val safeInitialScale = if (vp.initialScale.isNaN() || vp.initialScale.isInfinite()) 1.0f else vp.initialScale.coerceIn(safeMinScale, safeMaxScale)
                    val safeGridStep = if (vp.gridStep.isNaN() || vp.gridStep.isInfinite()) 0.025f else vp.gridStep.coerceIn(0.01f, 0.25f)
                    node.copy(
                        layers = sanitizedLayers,
                        viewportConfig = vp.copy(
                            minScale = safeMinScale,
                            maxScale = safeMaxScale,
                            initialScale = safeInitialScale,
                            gridStep = safeGridStep
                        )
                    )
                }
                else -> node
            }
        }

        val sanitizedRoot = sanitizeNode(tree.root, depth = 1)
        return tree.copy(root = sanitizedRoot)
    }

    private fun sanitizeUnitFraction(value: Float?): Float? {
        if (value == null) return null
        return if (value.isNaN() || value.isInfinite()) null else value.coerceIn(0f, 1f)
    }
}

/**
 * Extension function to easily sanitize a [UiNodeTree].
 */
fun UiNodeTree.sanitize(
    maxDepth: Int = UiAstSanitizer.DEFAULT_MAX_DEPTH,
    maxNodes: Int = UiAstSanitizer.DEFAULT_MAX_NODES
): UiNodeTree = UiAstSanitizer.sanitize(this, maxDepth, maxNodes)
