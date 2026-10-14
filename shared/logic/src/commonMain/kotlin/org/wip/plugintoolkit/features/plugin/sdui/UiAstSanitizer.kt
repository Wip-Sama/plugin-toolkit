package org.wip.plugintoolkit.features.plugin.sdui

import org.wip.plugintoolkit.api.sdui.BoxNode
import org.wip.plugintoolkit.api.sdui.CardNode
import org.wip.plugintoolkit.api.sdui.ColumnNode
import org.wip.plugintoolkit.api.sdui.ErrorBoundaryNode
import org.wip.plugintoolkit.api.sdui.RowNode
import org.wip.plugintoolkit.api.sdui.ScrollableColumnNode
import org.wip.plugintoolkit.api.sdui.SliderNode
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
            if (weight != null && (weight < 0f || weight.isNaN())) {
                return ErrorBoundaryNode(
                    id = node.id,
                    errorMessage = "Invalid layout weight: $weight (must be >= 0.0)",
                    failedNodeType = node::class.simpleName
                )
            }

            val fillMaxWidth = node.modifier.fillMaxWidth
            if (fillMaxWidth != null && (fillMaxWidth < 0f || fillMaxWidth.isNaN())) {
                return ErrorBoundaryNode(
                    id = node.id,
                    errorMessage = "Invalid fillMaxWidth: $fillMaxWidth (must be >= 0.0)",
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

            // Recursively sanitize children for container nodes
            return when (node) {
                is ColumnNode -> node.copy(children = node.children.map { sanitizeNode(it, depth + 1) })
                is RowNode -> node.copy(children = node.children.map { sanitizeNode(it, depth + 1) })
                is BoxNode -> node.copy(children = node.children.map { sanitizeNode(it, depth + 1) })
                is CardNode -> node.copy(children = node.children.map { sanitizeNode(it, depth + 1) })
                is ScrollableColumnNode -> node.copy(children = node.children.map { sanitizeNode(it, depth + 1) })
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
