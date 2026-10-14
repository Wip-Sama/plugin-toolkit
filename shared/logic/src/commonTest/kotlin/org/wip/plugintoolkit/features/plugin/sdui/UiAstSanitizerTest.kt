package org.wip.plugintoolkit.features.plugin.sdui

import org.wip.plugintoolkit.api.sdui.ColumnNode
import org.wip.plugintoolkit.api.sdui.ErrorBoundaryNode
import org.wip.plugintoolkit.api.sdui.RowNode
import org.wip.plugintoolkit.api.sdui.SliderNode
import org.wip.plugintoolkit.api.sdui.TextNode
import org.wip.plugintoolkit.api.sdui.UiModifier
import org.wip.plugintoolkit.api.sdui.UiNodeTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UiAstSanitizerTest {

    @Test
    fun testValidTreePassesThrough() {
        val tree = UiNodeTree(
            root = ColumnNode(
                id = "col1",
                children = listOf(
                    TextNode(id = "text1", text = "Hello"),
                    TextNode(id = "text2", text = "World")
                )
            )
        )
        val sanitized = tree.sanitize()
        assertEquals(tree, sanitized)
    }

    @Test
    fun testDuplicateIdCollisionReplacedWithErrorBoundary() {
        val tree = UiNodeTree(
            root = ColumnNode(
                id = "root_col",
                children = listOf(
                    TextNode(id = "duplicate_id", text = "First"),
                    TextNode(id = "duplicate_id", text = "Second")
                )
            )
        )
        val sanitized = tree.sanitize()
        val root = assertIs<ColumnNode>(sanitized.root)
        assertEquals(2, root.children.size)
        assertIs<TextNode>(root.children[0])
        val errorNode = assertIs<ErrorBoundaryNode>(root.children[1])
        assertTrue(errorNode.errorMessage.contains("Duplicate node ID collision"))
    }

    @Test
    fun testMaxDepthExceededReplacedWithErrorBoundary() {
        // Build a chain of 5 nested columns with maxDepth = 3
        val level4 = TextNode(id = "leaf", text = "Leaf")
        val level3 = ColumnNode(id = "col3", children = listOf(level4))
        val level2 = ColumnNode(id = "col2", children = listOf(level3))
        val level1 = ColumnNode(id = "col1", children = listOf(level2))
        val tree = UiNodeTree(root = level1)

        val sanitized = tree.sanitize(maxDepth = 3)
        val root = assertIs<ColumnNode>(sanitized.root)
        val child2 = assertIs<ColumnNode>(root.children.first())
        val child3 = assertIs<ColumnNode>(child2.children.first())
        val errorNode = assertIs<ErrorBoundaryNode>(child3.children.first())
        assertTrue(errorNode.errorMessage.contains("Maximum UI nesting depth of 3 exceeded"))
    }

    @Test
    fun testMaxNodesExceededReplacedWithErrorBoundary() {
        val children = (1..10).map { i -> TextNode(id = "t_$i", text = "Item $i") }
        val tree = UiNodeTree(root = ColumnNode(id = "root", children = children))

        val sanitized = tree.sanitize(maxNodes = 5)
        val root = assertIs<ColumnNode>(sanitized.root)
        assertEquals(10, root.children.size)
        // Nodes up to 5 (root + t_1..t_4) are valid, 6th and beyond become ErrorBoundaryNode
        assertIs<TextNode>(root.children[0])
        assertIs<TextNode>(root.children[1])
        assertIs<TextNode>(root.children[2])
        assertIs<TextNode>(root.children[3])
        val errorNode = assertIs<ErrorBoundaryNode>(root.children[4])
        assertTrue(errorNode.errorMessage.contains("Maximum UI node count of 5 exceeded"))
    }

    @Test
    fun testMalformedSliderRangeReplacedWithErrorBoundary() {
        val tree = UiNodeTree(
            root = SliderNode(
                id = "bad_slider",
                value = 50.0,
                min = 100.0,
                max = 0.0
            )
        )
        val sanitized = tree.sanitize()
        val errorNode = assertIs<ErrorBoundaryNode>(sanitized.root)
        assertTrue(errorNode.errorMessage.contains("Malformed slider range"))
    }

    @Test
    fun testInvalidModifierPropertiesReplacedWithErrorBoundary() {
        val invalidWeightTree = UiNodeTree(
            root = TextNode(
                id = "neg_weight",
                text = "Negative weight",
                modifier = UiModifier(weight = -1.5f)
            )
        )
        val sanitizedWeight = invalidWeightTree.sanitize()
        val errorWeight = assertIs<ErrorBoundaryNode>(sanitizedWeight.root)
        assertTrue(errorWeight.errorMessage.contains("Invalid layout weight"))

        val invalidFillMaxWidthTree = UiNodeTree(
            root = RowNode(
                id = "neg_width",
                children = emptyList(),
                modifier = UiModifier(fillMaxWidth = -0.5f)
            )
        )
        val sanitizedFill = invalidFillMaxWidthTree.sanitize()
        val errorFill = assertIs<ErrorBoundaryNode>(sanitizedFill.root)
        assertTrue(errorFill.errorMessage.contains("Invalid fillMaxWidth"))
    }
}
