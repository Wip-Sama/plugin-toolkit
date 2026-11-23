package org.wip.plugintoolkit.features.plugin.sdui

import org.wip.plugintoolkit.api.sdui.AccordionNode
import org.wip.plugintoolkit.api.sdui.ActionMenuItem
import org.wip.plugintoolkit.api.sdui.AssetReference
import org.wip.plugintoolkit.api.sdui.BoardLayer
import org.wip.plugintoolkit.api.sdui.BoardNode
import org.wip.plugintoolkit.api.sdui.BoardShapeType
import org.wip.plugintoolkit.api.sdui.BoardViewportConfig
import org.wip.plugintoolkit.api.sdui.ButtonGroupItem
import org.wip.plugintoolkit.api.sdui.ButtonGroupNode
import org.wip.plugintoolkit.api.sdui.CodeBlockNode
import org.wip.plugintoolkit.api.sdui.ColumnNode
import org.wip.plugintoolkit.api.sdui.ErrorBoundaryNode
import org.wip.plugintoolkit.api.sdui.FloatingAnchor
import org.wip.plugintoolkit.api.sdui.FloatingPanelNode
import org.wip.plugintoolkit.api.sdui.LoadingIndicatorNode
import org.wip.plugintoolkit.api.sdui.NormalizedPoint
import org.wip.plugintoolkit.api.sdui.NormalizedSize
import org.wip.plugintoolkit.api.sdui.NumberInputNode
import org.wip.plugintoolkit.api.sdui.RadioGroupNode
import org.wip.plugintoolkit.api.sdui.RadioOptionItem
import org.wip.plugintoolkit.api.sdui.RowNode
import org.wip.plugintoolkit.api.sdui.ScaffoldNode
import org.wip.plugintoolkit.api.sdui.SemanticColor
import org.wip.plugintoolkit.api.sdui.SidebarAttachPoint
import org.wip.plugintoolkit.api.sdui.SidebarNode
import org.wip.plugintoolkit.api.sdui.SliderNode
import org.wip.plugintoolkit.api.sdui.SplitButtonNode
import org.wip.plugintoolkit.api.sdui.TextNode
import org.wip.plugintoolkit.api.sdui.UiDropTarget
import org.wip.plugintoolkit.api.sdui.UiModifier
import org.wip.plugintoolkit.api.sdui.UiNodeTree
import org.wip.plugintoolkit.api.sdui.UiShortcut
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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

        val invalidFillMaxHeightTree = UiNodeTree(
            root = ColumnNode(
                id = "over_height",
                children = emptyList(),
                modifier = UiModifier(fillMaxHeight = 1.5f)
            )
        )
        val sanitizedFillHeight = invalidFillMaxHeightTree.sanitize()
        val errorFillHeight = assertIs<ErrorBoundaryNode>(sanitizedFillHeight.root)
        assertTrue(errorFillHeight.errorMessage.contains("Invalid fillMaxHeight"))

        val invalidAspectRatioTree = UiNodeTree(
            root = TextNode(
                id = "zero_aspect",
                text = "Zero aspect",
                modifier = UiModifier(aspectRatio = 0f)
            )
        )
        val sanitizedAspect = invalidAspectRatioTree.sanitize()
        val errorAspect = assertIs<ErrorBoundaryNode>(sanitizedAspect.root)
        assertTrue(errorAspect.errorMessage.contains("Invalid aspectRatio"))

        val invalidWidthBoundsTree = UiNodeTree(
            root = TextNode(
                id = "bad_bounds",
                text = "Bad width bounds",
                modifier = UiModifier(minWidthDp = 300, maxWidthDp = 100)
            )
        )
        val sanitizedBounds = invalidWidthBoundsTree.sanitize()
        val errorBounds = assertIs<ErrorBoundaryNode>(sanitizedBounds.root)
        assertTrue(errorBounds.errorMessage.contains("minWidthDp"))

        val invalidNegativeHeightTree = UiNodeTree(
            root = TextNode(
                id = "neg_height_dp",
                text = "Negative height",
                modifier = UiModifier(heightDp = -20)
            )
        )
        val sanitizedNegHeight = invalidNegativeHeightTree.sanitize()
        val errorNegHeight = assertIs<ErrorBoundaryNode>(sanitizedNegHeight.root)
        assertTrue(errorNegHeight.errorMessage.contains("Invalid heightDp"))
    }

    @Test
    fun testBlankNodeIdReplacedWithErrorBoundary() {
        val tree = UiNodeTree(
            root = TextNode(
                id = "   ",
                text = "Blank ID"
            )
        )
        val sanitized = tree.sanitize()
        val errorNode = assertIs<ErrorBoundaryNode>(sanitized.root)
        assertTrue(errorNode.errorMessage.contains("Node ID must not be blank"))
    }

    @Test
    fun testValidExtendedModifierPassesThrough() {
        val tree = UiNodeTree(
            root = TextNode(
                id = "styled_node",
                text = "Styled",
                modifier = UiModifier.None
                    .size(120, 48)
                    .widthIn(min = 80, max = 240)
                    .heightIn(min = 32, max = 96)
                    .background(SemanticColor.SURFACE)
                    .border(SemanticColor.OUTLINE)
                    .clipRounded()
            )
        )
        val sanitized = tree.sanitize()
        assertEquals(tree, sanitized)
    }

    @Test
    fun testSanitizeScaffoldWithPanels() {
        val scaffoldTree = UiNodeTree(
            root = ScaffoldNode(
                id = "scaffold_root",
                content = TextNode(id = "main_content", text = "Content"),
                floatingPanels = listOf(
                    FloatingPanelNode(
                        id = "floating_1",
                        anchor = FloatingAnchor.TOP_START,
                        children = listOf(TextNode(id = "float_child", text = "Panel"))
                    )
                ),
                leftSidebar = SidebarNode(
                    id = "sidebar_1",
                    attachPoint = SidebarAttachPoint.LEFT,
                    children = listOf(TextNode(id = "side_child", text = "Sidebar"))
                )
            )
        )
        val sanitized = scaffoldTree.sanitize()
        val root = assertIs<ScaffoldNode>(sanitized.root)
        assertEquals("scaffold_root", root.id)
        assertIs<TextNode>(root.content)
        assertEquals(1, root.floatingPanels.size)
        assertNotNull(root.leftSidebar)
        assertIs<SidebarNode>(root.leftSidebar)
    }

    @Test
    fun testBoardNodeDuplicateLayerIdsReplacedWithErrorBoundary() {
        val tree = UiNodeTree(
            root = BoardNode(
                id = "board_dup",
                layers = listOf(
                    BoardLayer.TextLayer(id = "layer_1", text = "First", position = NormalizedPoint(0.2f, 0.2f)),
                    BoardLayer.ShapeLayer(
                        id = "layer_1",
                        shapeType = BoardShapeType.RECTANGLE,
                        position = NormalizedPoint(0.4f, 0.4f),
                        size = NormalizedSize(0.2f, 0.2f)
                    )
                )
            )
        )
        val sanitized = tree.sanitize()
        val errorNode = assertIs<ErrorBoundaryNode>(sanitized.root)
        assertTrue(errorNode.errorMessage.contains("Invalid or duplicate BoardLayer ID"))
    }

    @Test
    fun testBoardNodeClampsLayerCoordinatesAndSanitizesComponentLayerContent() {
        val tree = UiNodeTree(
            root = BoardNode(
                id = "board_clamp",
                viewportConfig = BoardViewportConfig(
                    minScale = 0.05f,
                    maxScale = 10.0f,
                    initialScale = 8.0f,
                    gridStep = 0.001f
                ),
                layers = listOf(
                    BoardLayer.ImageLayer(
                        id = "img_out_of_bounds",
                        asset = AssetReference.Relative("page.png"),
                        position = NormalizedPoint(-0.5f, 1.5f),
                        size = NormalizedSize(0.0f, 2.0f),
                        opacity = 2.0f
                    ),
                    BoardLayer.ComponentLayer(
                        id = "comp_with_bad_slider",
                        position = NormalizedPoint(0.5f, 0.5f),
                        size = NormalizedSize(0.3f, 0.3f),
                        content = SliderNode(
                            id = "nested_bad_slider",
                            value = 10.0,
                            min = 50.0,
                            max = 5.0
                        )
                    )
                )
            )
        )

        val sanitized = tree.sanitize()
        val board = assertIs<BoardNode>(sanitized.root)
        assertEquals(0.25f, board.viewportConfig.minScale)
        assertEquals(4.0f, board.viewportConfig.maxScale)
        assertEquals(4.0f, board.viewportConfig.initialScale)
        assertEquals(0.01f, board.viewportConfig.gridStep)

        val imgLayer = assertIs<BoardLayer.ImageLayer>(board.layers[0])
        assertEquals(NormalizedPoint(0.0f, 1.0f), imgLayer.position)
        assertEquals(NormalizedSize(0.01f, 1.0f), imgLayer.size)
        assertEquals(1.0f, imgLayer.opacity)

        val compLayer = assertIs<BoardLayer.ComponentLayer>(board.layers[1])
        val nestedError = assertIs<ErrorBoundaryNode>(compLayer.content)
        assertTrue(nestedError.errorMessage.contains("Malformed slider range"))
    }

    @Test
    fun testShortcutAndDropTargetValidation() {
        val validTree = UiNodeTree(
            root = ColumnNode(
                id = "valid_shortcuts_col",
                modifier = UiModifier.fillMaxSize()
                    .shortcut(key = UiShortcut.KEY_DELETE, action = "delete_page", label = "Delete")
                    .onFileDrop(action = "drop_pages", allowedExtensions = listOf("png"), maxFiles = 10),
                children = listOf(TextNode(id = "ok_label", text = "OK"))
            )
        )
        assertEquals(validTree, validTree.sanitize())

        val invalidTree = UiNodeTree(
            root = ColumnNode(
                id = "invalid_root",
                children = listOf(
                    TextNode(
                        id = "bad_shortcut_key",
                        text = "bad",
                        modifier = UiModifier.shortcut(key = "  ", action = "act")
                    ),
                    TextNode(
                        id = "bad_drop_action",
                        text = "bad",
                        modifier = UiModifier.onFileDrop(action = "")
                    ),
                    TextNode(
                        id = "bad_drop_accept",
                        text = "bad",
                        modifier = UiModifier.dropTarget(
                            UiDropTarget(action = "drop", acceptFiles = false, acceptDirectories = false)
                        )
                    ),
                    TextNode(
                        id = "bad_drop_max",
                        text = "bad",
                        modifier = UiModifier.onFileDrop(action = "drop", maxFiles = 0)
                    )
                )
            )
        )

        val sanitized = invalidTree.sanitize()
        val root = assertIs<ColumnNode>(sanitized.root)
        assertTrue(assertIs<ErrorBoundaryNode>(root.children[0]).errorMessage.contains("Invalid UiShortcut"))
        assertTrue(assertIs<ErrorBoundaryNode>(root.children[1]).errorMessage.contains("action must not be blank"))
        assertTrue(assertIs<ErrorBoundaryNode>(root.children[2]).errorMessage.contains("must accept files or directories"))
        assertTrue(assertIs<ErrorBoundaryNode>(root.children[3]).errorMessage.contains("maxFiles"))
    }

    @Test
    fun testExpressiveButtonGroupAndSplitButtonValidation() {
        val validTree = UiNodeTree(
            root = RowNode(
                id = "expr_row",
                children = listOf(
                    ButtonGroupNode(
                        id = "valid_group",
                        items = listOf(ButtonGroupItem(key = "cubari", label = "Cubari"))
                    ),
                    SplitButtonNode(
                        id = "valid_split",
                        label = "Push",
                        primaryAction = "push_all",
                        menuItems = listOf(ActionMenuItem(key = "alt", label = "Alt"))
                    )
                )
            )
        )
        assertEquals(validTree, validTree.sanitize())

        val invalidTree = UiNodeTree(
            root = ColumnNode(
                id = "bad_expr_col",
                children = listOf(
                    ButtonGroupNode(
                        id = "bad_group",
                        items = listOf(ButtonGroupItem(key = "  ", label = "NoKey"))
                    ),
                    SplitButtonNode(
                        id = "bad_split_action",
                        label = "Push",
                        primaryAction = ""
                    ),
                    SplitButtonNode(
                        id = "bad_split_menu",
                        label = "Push",
                        primaryAction = "push",
                        menuItems = listOf(ActionMenuItem(key = "k1", label = "  "))
                    )
                )
            )
        )

        val sanitized = invalidTree.sanitize()
        val root = assertIs<ColumnNode>(sanitized.root)
        assertTrue(assertIs<ErrorBoundaryNode>(root.children[0]).errorMessage.contains("ButtonGroupNode"))
        assertTrue(assertIs<ErrorBoundaryNode>(root.children[1]).errorMessage.contains("SplitButtonNode"))
        assertTrue(assertIs<ErrorBoundaryNode>(root.children[2]).errorMessage.contains("SplitButtonNode"))
    }

    @Test
    fun testFormCodeAndModifierSanitization() {
        val tree = UiNodeTree(
            root = ColumnNode(
                id = "b1_root",
                children = listOf(
                    TextNode(
                        id = "bad_context_menu",
                        text = "Menu",
                        modifier = UiModifier.contextMenu(listOf(ActionMenuItem(key = "", label = "Copy")))
                    ),
                    TextNode(id = "bad_lines", text = "Truncated", maxLines = 0),
                    RadioGroupNode(
                        id = "bad_radio_dup",
                        options = listOf(
                            RadioOptionItem(key = "a", label = "A"),
                            RadioOptionItem(key = "a", label = "Duplicate")
                        )
                    ),
                    NumberInputNode(
                        id = "bad_num",
                        value = 5.0,
                        min = 10.0,
                        max = 2.0,
                        step = 1.0
                    ),
                    CodeBlockNode(id = "bad_code", code = "val x = 1", maxHeightDp = 0),
                    AccordionNode(
                        id = "good_accordion",
                        title = "Section",
                        children = listOf(
                            TextNode(id = "good_accordion", text = "Colliding ID inside accordion")
                        )
                    )
                )
            )
        )

        val sanitized = tree.sanitize()
        val root = assertIs<ColumnNode>(sanitized.root)
        assertTrue(assertIs<ErrorBoundaryNode>(root.children[0]).errorMessage.contains("contextMenu"))
        assertTrue(assertIs<ErrorBoundaryNode>(root.children[1]).errorMessage.contains("maxLines"))
        assertTrue(assertIs<ErrorBoundaryNode>(root.children[2]).errorMessage.contains("RadioGroupNode"))
        assertTrue(assertIs<ErrorBoundaryNode>(root.children[3]).errorMessage.contains("Malformed number input"))
        assertTrue(assertIs<ErrorBoundaryNode>(root.children[4]).errorMessage.contains("CodeBlockNode"))
        val acc = assertIs<AccordionNode>(root.children[5])
        assertTrue(assertIs<ErrorBoundaryNode>(acc.children.single()).errorMessage.contains("Duplicate node ID collision"))
    }

    @Test
    fun testLoadingIndicatorProgressFractionClampedOrCleared() {
        val tree = UiNodeTree(
            root = ColumnNode(
                id = "loading_col",
                children = listOf(
                    LoadingIndicatorNode(id = "l_over", progress = 1.8f),
                    LoadingIndicatorNode(id = "l_nan", progress = Float.NaN)
                )
            )
        )
        val sanitized = tree.sanitize()
        val root = assertIs<ColumnNode>(sanitized.root)
        assertEquals(1.0f, assertIs<LoadingIndicatorNode>(root.children[0]).progress)
        assertEquals(null, assertIs<LoadingIndicatorNode>(root.children[1]).progress)
    }
}

