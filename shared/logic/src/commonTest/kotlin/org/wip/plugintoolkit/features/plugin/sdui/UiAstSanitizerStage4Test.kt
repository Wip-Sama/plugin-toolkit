package org.wip.plugintoolkit.features.plugin.sdui

import org.wip.plugintoolkit.api.sdui.ButtonNode
import org.wip.plugintoolkit.api.sdui.DialogNode
import org.wip.plugintoolkit.api.sdui.ErrorBoundaryNode
import org.wip.plugintoolkit.api.sdui.ListItemNode
import org.wip.plugintoolkit.api.sdui.ProgressBarNode
import org.wip.plugintoolkit.api.sdui.ScrollableRowNode
import org.wip.plugintoolkit.api.sdui.SplitPaneNode
import org.wip.plugintoolkit.api.sdui.TabItem
import org.wip.plugintoolkit.api.sdui.TabsNode
import org.wip.plugintoolkit.api.sdui.TextNode
import org.wip.plugintoolkit.api.sdui.UiNodeTree
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UiAstSanitizerStage4Test {

    @Test
    fun testProgressBarCoercion() {
        val malformedOver = UiNodeTree(
            root = ProgressBarNode(
                id = "prog_1",
                progress = 2.5f,
                secondaryProgress = -0.5f
            )
        )
        val sanitizedOver = UiAstSanitizer.sanitize(malformedOver)
        val nodeOver = sanitizedOver.root as ProgressBarNode
        assertEquals(1.0f, nodeOver.progress)
        assertEquals(0.0f, nodeOver.secondaryProgress)

        val malformedNaN = UiNodeTree(
            root = ProgressBarNode(
                id = "prog_2",
                progress = Float.NaN,
                secondaryProgress = Float.POSITIVE_INFINITY
            )
        )
        val sanitizedNaN = UiAstSanitizer.sanitize(malformedNaN)
        val nodeNaN = sanitizedNaN.root as ProgressBarNode
        assertNull(nodeNaN.progress)
        assertNull(nodeNaN.secondaryProgress)
    }

    @Test
    fun testSplitPaneFractionSanitization() {
        val malformedFraction = UiNodeTree(
            root = SplitPaneNode(
                id = "split_1",
                first = TextNode(id = "txt_1", text = "Pane 1"),
                second = TextNode(id = "txt_2", text = "Pane 2"),
                splitFraction = 0.01f
            )
        )
        val sanitized = UiAstSanitizer.sanitize(malformedFraction)
        val splitNode = sanitized.root as SplitPaneNode
        assertEquals(0.05f, splitNode.splitFraction)

        val malformedNaN = UiNodeTree(
            root = SplitPaneNode(
                id = "split_2",
                first = TextNode(id = "txt_3", text = "Pane 1"),
                second = TextNode(id = "txt_4", text = "Pane 2"),
                splitFraction = Float.NaN
            )
        )
        val sanitizedNaN = UiAstSanitizer.sanitize(malformedNaN)
        val splitNaN = sanitizedNaN.root as SplitPaneNode
        assertEquals(0.35f, splitNaN.splitFraction)
    }

    @Test
    fun testDuplicateIdDetectionInCompositeNodes() {
        val treeWithDuplicateInTabs = UiNodeTree(
            root = TabsNode(
                id = "dup_id",
                selectedTabKey = "t1",
                tabs = listOf(TabItem(key = "t1", label = "Tab 1")),
                content = TextNode(id = "dup_id", text = "Conflicting child")
            )
        )
        val sanitized = UiAstSanitizer.sanitize(treeWithDuplicateInTabs)
        assertIs<TabsNode>(sanitized.root)
        val tabs = sanitized.root as TabsNode
        assertIs<ErrorBoundaryNode>(tabs.content)
        assertTrue((tabs.content as ErrorBoundaryNode).errorMessage.contains("Duplicate node ID collision"))

        val treeWithDuplicateInDialog = UiNodeTree(
            root = DialogNode(
                id = "dialog_id",
                title = "Modal",
                children = listOf(
                    TextNode(id = "item_id", text = "First"),
                    TextNode(id = "item_id", text = "Duplicate")
                )
            )
        )
        val sanitizedDlg = UiAstSanitizer.sanitize(treeWithDuplicateInDialog)
        val dlg = sanitizedDlg.root as DialogNode
        assertEquals(2, dlg.children.size)
        assertIs<TextNode>(dlg.children[0])
        assertIs<ErrorBoundaryNode>(dlg.children[1])
    }

    @Test
    fun testRecursiveSanitizationInListItemAndScrollableRow() {
        val tree = UiNodeTree(
            root = ScrollableRowNode(
                id = "row_1",
                children = listOf(
                    ListItemNode(
                        id = "item_1",
                        title = "Item 1",
                        trailingContent = listOf(
                            ButtonNode(id = "btn_1", label = "Action 1")
                        )
                    )
                )
            )
        )
        val sanitized = UiAstSanitizer.sanitize(tree)
        assertIs<ScrollableRowNode>(sanitized.root)
        val row = sanitized.root as ScrollableRowNode
        assertEquals(1, row.children.size)
        assertIs<ListItemNode>(row.children[0])
        val item = row.children[0] as ListItemNode
        assertEquals(1, item.trailingContent.size)
        assertIs<ButtonNode>(item.trailingContent[0])
    }
}
