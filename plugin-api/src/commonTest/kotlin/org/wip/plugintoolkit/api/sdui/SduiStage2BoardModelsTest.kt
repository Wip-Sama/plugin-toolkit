package org.wip.plugintoolkit.api.sdui

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SduiStage2BoardModelsTest {

    @Test
    fun testNormalizedPointAndSizeCoercion() {
        val rawPt = NormalizedPoint(-0.4f, 1.8f)
        val clampedPt = rawPt.coercedInBounds()
        assertEquals(0.0f, clampedPt.x)
        assertEquals(1.0f, clampedPt.y)

        val nanPt = NormalizedPoint(Float.NaN, Float.POSITIVE_INFINITY).coercedInBounds()
        assertEquals(0.0f, nanPt.x)
        assertEquals(0.0f, nanPt.y)

        val rawSize = NormalizedSize(0.0f, 2.5f)
        val clampedSize = rawSize.coercedInBounds()
        assertEquals(0.01f, clampedSize.width)
        assertEquals(1.0f, clampedSize.height)
    }

    @Test
    fun testBoardDslAndLayerSerialization() {
        val tree = uiTree {
            board(
                id = "compositor_board",
                backgroundAsset = AssetReference.Relative("assets/bg.png"),
                activeTool = BoardTool.Shape(
                    shapeType = BoardShapeType.CALLOUT_BUBBLE,
                    strokeColor = SemanticColor.PRIMARY,
                    fillColor = SemanticColor.SURFACE
                ),
                viewportConfig = BoardViewportConfig(
                    panEnabled = true,
                    zoomEnabled = true,
                    minScale = 0.5f,
                    maxScale = 3.0f,
                    initialScale = 1.25f,
                    gridStyle = BoardGridStyle.DOTS,
                    snapToGrid = true,
                    gridStep = 0.05f,
                    artboardAspectRatio = 1.5f
                ),
                selectedLayerIds = setOf("txt_1", "comp_1"),
                showToolbar = true,
                showZoomControls = true
            ) {
                imageLayer(
                    id = "img_1",
                    asset = AssetReference.Cached("panels/page1.png"),
                    position = NormalizedPoint(0.1f, 0.1f),
                    size = NormalizedSize(0.8f, 0.8f),
                    zIndex = 0
                )
                shapeLayer(
                    id = "shape_1",
                    shapeType = BoardShapeType.CALLOUT_BUBBLE,
                    position = NormalizedPoint(0.2f, 0.2f),
                    size = NormalizedSize(0.3f, 0.15f),
                    text = "Hello World!",
                    zIndex = 1
                )
                textLayer(
                    id = "txt_1",
                    text = "Chapter 1",
                    position = NormalizedPoint(0.25f, 0.25f),
                    size = NormalizedSize(0.2f, 0.08f),
                    role = TypographyRole.TITLE_LARGE,
                    zIndex = 2
                )
                pathLayer(
                    id = "path_1",
                    path = VectorPathData(
                        strokeId = "s_1",
                        points = listOf(NormalizedPoint(0.1f, 0.1f), NormalizedPoint(0.4f, 0.4f)),
                        color = SemanticColor.ACCENT,
                        strokeWidth = 5f
                    ),
                    zIndex = 3
                )
                componentLayer(
                    id = "comp_1",
                    position = NormalizedPoint(0.6f, 0.6f),
                    size = NormalizedSize(0.3f, 0.25f),
                    zIndex = 4
                ) {
                    card(id = "nested_card", title = "Inspector") {
                        text(id = "nested_txt", text = "Layer properties")
                        button(id = "nested_btn", label = "Apply", action = "apply_props")
                    }
                }
            }
        }

        val json = Json.encodeToString(UiNodeTree.serializer(), tree)
        val decoded = Json.decodeFromString(UiNodeTree.serializer(), json)
        val board = assertIs<BoardNode>(decoded.root)

        assertEquals("compositor_board", board.id)
        assertEquals(BoardGridStyle.DOTS, board.viewportConfig.gridStyle)
        assertTrue(board.viewportConfig.snapToGrid)
        assertEquals(1.25f, board.viewportConfig.initialScale)
        assertEquals(setOf("txt_1", "comp_1"), board.selectedLayerIds)
        assertIs<BoardTool.Shape>(board.activeTool)
        assertEquals(5, board.layers.size)

        val imgLayer = assertIs<BoardLayer.ImageLayer>(board.layers[0])
        assertEquals("img_1", imgLayer.id)
        assertEquals(AssetReference.Cached("panels/page1.png"), imgLayer.asset)

        val shapeLayer = assertIs<BoardLayer.ShapeLayer>(board.layers[1])
        assertEquals(BoardShapeType.CALLOUT_BUBBLE, shapeLayer.shapeType)
        assertEquals("Hello World!", shapeLayer.text)

        val textLayer = assertIs<BoardLayer.TextLayer>(board.layers[2])
        assertEquals("Chapter 1", textLayer.text)
        assertEquals(TypographyRole.TITLE_LARGE, textLayer.role)

        val pathLayer = assertIs<BoardLayer.PathLayer>(board.layers[3])
        assertEquals("s_1", pathLayer.path.strokeId)

        val compLayer = assertIs<BoardLayer.ComponentLayer>(board.layers[4])
        val card = assertIs<CardNode>(compLayer.content)
        assertEquals("Inspector", card.title)
        assertEquals(2, card.children.size)
    }

    @Test
    fun testAllStage2BoardEventsSerialization() {
        val events: List<UiEvent> = listOf(
            UiEvent.BoardLayerSelected(nodeId = "board1", selectedLayerIds = listOf("l1", "l2")),
            UiEvent.BoardLayerMoved(nodeId = "board1", layerId = "l1", position = NormalizedPoint(0.35f, 0.45f)),
            UiEvent.BoardLayerResized(
                nodeId = "board1",
                layerId = "l1",
                position = NormalizedPoint(0.35f, 0.45f),
                size = NormalizedSize(0.5f, 0.25f)
            ),
            UiEvent.BoardTextEdited(nodeId = "board1", layerId = "txt1", text = "Updated dialogue"),
            UiEvent.BoardTextPlaced(nodeId = "board1", position = NormalizedPoint(0.5f, 0.5f), text = "New Note"),
            UiEvent.BoardShapeCommitted(
                nodeId = "board1",
                shapeType = BoardShapeType.ELLIPSE,
                position = NormalizedPoint(0.2f, 0.3f),
                size = NormalizedSize(0.4f, 0.2f),
                strokeColor = SemanticColor.PRIMARY,
                fillColor = SemanticColor.SURFACE,
                strokeWidth = 3f
            ),
            UiEvent.BoardLayerDeleted(nodeId = "board1", layerIds = listOf("l1")),
            UiEvent.BoardCanvasClicked(nodeId = "board1", position = NormalizedPoint(0.75f, 0.85f))
        )

        for (event in events) {
            val encoded = Json.encodeToString(UiEvent.serializer(), event)
            val decoded = Json.decodeFromString(UiEvent.serializer(), encoded)
            assertEquals(event, decoded)
        }
    }
}
