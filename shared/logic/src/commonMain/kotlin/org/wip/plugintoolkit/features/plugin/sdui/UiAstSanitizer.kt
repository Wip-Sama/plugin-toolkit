package org.wip.plugintoolkit.features.plugin.sdui

import org.wip.plugintoolkit.api.sdui.BoardLayer
import org.wip.plugintoolkit.api.sdui.BoardNode
import org.wip.plugintoolkit.api.sdui.BoxNode
import org.wip.plugintoolkit.api.sdui.CardNode
import org.wip.plugintoolkit.api.sdui.ColumnNode
import org.wip.plugintoolkit.api.sdui.DialogNode
import org.wip.plugintoolkit.api.sdui.ErrorBoundaryNode
import org.wip.plugintoolkit.api.sdui.FloatingPanelNode
import org.wip.plugintoolkit.api.sdui.ListItemNode
import org.wip.plugintoolkit.api.sdui.ProgressBarNode
import org.wip.plugintoolkit.api.sdui.RowNode
import org.wip.plugintoolkit.api.sdui.ScaffoldNode
import org.wip.plugintoolkit.api.sdui.ScrollableColumnNode
import org.wip.plugintoolkit.api.sdui.ScrollableRowNode
import org.wip.plugintoolkit.api.sdui.SidebarNode
import org.wip.plugintoolkit.api.sdui.SliderNode
import org.wip.plugintoolkit.api.sdui.SplitPaneNode
import org.wip.plugintoolkit.api.sdui.TabsNode
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
    const val DEFAULT_MAX_NODES = 1000

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

            // Validate component-specific invariants
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
                val p = node.progress
                val sp = node.secondaryProgress
                val invalidP = p != null && (p.isNaN() || p.isInfinite() || p < 0f || p > 1f)
                val invalidSp = sp != null && (sp.isNaN() || sp.isInfinite() || sp < 0f || sp > 1f)
                if (invalidP || invalidSp) {
                    val safeP = if (p == null || p.isNaN() || p.isInfinite()) null else p.coerceIn(0f, 1f)
                    val safeSp = if (sp == null || sp.isNaN() || sp.isInfinite()) null else sp.coerceIn(0f, 1f)
                    return node.copy(progress = safeP, secondaryProgress = safeSp)
                }
            }

            // Recursively sanitize children for container nodes
            return when (node) {
                is ColumnNode -> node.copy(children = node.children.map { sanitizeNode(it, depth + 1) })
                is RowNode -> node.copy(children = node.children.map { sanitizeNode(it, depth + 1) })
                is ScrollableRowNode -> node.copy(children = node.children.map { sanitizeNode(it, depth + 1) })
                is BoxNode -> node.copy(children = node.children.map { sanitizeNode(it, depth + 1) })
                is CardNode -> node.copy(children = node.children.map { sanitizeNode(it, depth + 1) })
                is ScrollableColumnNode -> node.copy(children = node.children.map { sanitizeNode(it, depth + 1) })
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
}

/**
 * Extension function to easily sanitize a [UiNodeTree].
 */
fun UiNodeTree.sanitize(
    maxDepth: Int = UiAstSanitizer.DEFAULT_MAX_DEPTH,
    maxNodes: Int = UiAstSanitizer.DEFAULT_MAX_NODES
): UiNodeTree = UiAstSanitizer.sanitize(this, maxDepth, maxNodes)
