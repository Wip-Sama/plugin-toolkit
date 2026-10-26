package org.wip.plugintoolkit.api.sdui

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UiNodeSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    @Test
    fun testRoundTripComplexTreeSerialization() {
        val tree = uiTree {
            column(id = "main_col", spacing = SpacingSize.MEDIUM) {
                text("Annotate Drawing", id = "title", role = TypographyRole.TITLE_LARGE, color = SemanticColor.PRIMARY)
                textInput(
                    id = "notes_input",
                    text = "Initial note",
                    placeholder = "Type here...",
                    revision = 1,
                    singleLine = false
                )
                board(
                    id = "main_board",
                    backgroundAsset = AssetReference.Relative("images/blueprint.png"),
                    layers = listOf(
                        BoardLayer.PathLayer(
                            id = "stroke_1",
                            path = VectorPathData(
                                strokeId = "stroke_1",
                                points = listOf(
                                    NormalizedPoint(0.1f, 0.1f),
                                    NormalizedPoint(0.5f, 0.5f)
                                ),
                                color = SemanticColor.SECONDARY,
                                strokeWidth = 5f
                            )
                        )
                    ),
                    activeTool = BoardTool.Brush(color = SemanticColor.ERROR, strokeWidth = 3f)
                )
                button(id = "submit_btn", label = "Complete Annotation", isPrimary = true)
            }
        }

        val encoded = json.encodeToString(tree)
        val decoded = json.decodeFromString<UiNodeTree>(encoded)

        assertEquals(tree.version, decoded.version)
        val rootCol = decoded.root as ColumnNode
        assertEquals("main_col", rootCol.id)
        assertEquals(SpacingSize.MEDIUM, rootCol.spacing)
        assertEquals(4, rootCol.children.size)

        val textNode = rootCol.children[0] as TextNode
        assertEquals("Annotate Drawing", textNode.text)
        assertEquals(TypographyRole.TITLE_LARGE, textNode.role)

        val inputNode = rootCol.children[1] as TextInputNode
        assertEquals("Initial note", inputNode.text)
        assertEquals(1, inputNode.revision)

        val boardNode = rootCol.children[2] as BoardNode
        assertEquals("main_board", boardNode.id)
        assertIs<AssetReference.Relative>(boardNode.backgroundAsset)
        assertEquals("images/blueprint.png", (boardNode.backgroundAsset as AssetReference.Relative).path)
        assertEquals(1, boardNode.layers.size)
        assertEquals(false, boardNode.showToolbar)

        val btnNode = rootCol.children[3] as ButtonNode
        assertEquals("Complete Annotation", btnNode.label)
        assertTrue(btnNode.isPrimary)
    }

    @Test
    fun testPolymorphicDeserializationWithUnknownNodeFallback() {
        val jsonString = """
        {
            "root": {
                "type": "future_video_player",
                "id": "player_1",
                "videoUrl": "https://example.com/video.mp4",
                "fallbackText": "Video not supported on this version",
                "modifier": {
                    "padding": "MEDIUM"
                }
            },
            "version": 2
        }
        """.trimIndent()

        val decoded = json.decodeFromString<UiNodeTree>(jsonString)
        assertEquals(2, decoded.version)
        assertIs<UnknownNode>(decoded.root)
        val unknown = decoded.root as UnknownNode
        assertEquals("player_1", unknown.id)
        assertEquals("future_video_player", unknown.rawType)
        assertEquals("Video not supported on this version", unknown.fallbackText)
        assertEquals(SpacingSize.MEDIUM, unknown.modifier.padding)
    }

    @Test
    fun testUiEventSerializationRoundTrip() {
        val clickEvent: UiEvent = UiEvent.Click("btn_submit")
        val pathEvent: UiEvent = UiEvent.PathCommitted(
            nodeId = "board_1",
            path = VectorPathData(
                strokeId = "stroke_123",
                points = listOf(NormalizedPoint(0.2f, 0.3f), NormalizedPoint(0.4f, 0.5f)),
                color = SemanticColor.HIGHLIGHT,
                strokeWidth = 8f
            )
        )
        val textEvent: UiEvent = UiEvent.TextChanged("input_name", "Updated Text")

        val encodedClick = json.encodeToString(clickEvent)
        val decodedClick = json.decodeFromString<UiEvent>(encodedClick)
        assertIs<UiEvent.Click>(decodedClick)
        assertEquals("btn_submit", decodedClick.nodeId)

        val encodedPath = json.encodeToString(pathEvent)
        val decodedPath = json.decodeFromString<UiEvent>(encodedPath)
        assertIs<UiEvent.PathCommitted>(decodedPath)
        val pathCommitted = decodedPath as UiEvent.PathCommitted
        assertEquals("board_1", pathCommitted.nodeId)
        assertEquals("stroke_123", pathCommitted.path.strokeId)
        assertEquals(2, pathCommitted.path.points.size)

        val encodedText = json.encodeToString(textEvent)
        val decodedText = json.decodeFromString<UiEvent>(encodedText)
        assertIs<UiEvent.TextChanged>(decodedText)
        assertEquals("Updated Text", (decodedText as UiEvent.TextChanged).text)
    }

    @Test
    fun testUiModifierBuilder() {
        val modifier = UiModifier.None
            .padding(SpacingSize.LARGE)
            .fillMaxWidth(0.9f)
            .weight(2.0f)
            .align(AlignmentRole.CENTER)
            .enabled(false)

        assertEquals(SpacingSize.LARGE, modifier.padding)
        assertEquals(0.9f, modifier.fillMaxWidth)
        assertEquals(2.0f, modifier.weight)
        assertEquals(AlignmentRole.CENTER, modifier.alignment)
        assertEquals(false, modifier.enabled)
    }

    @Test
    fun testScaffoldWithFloatingPanelAndSidebarSerialization() {
        val tree = uiTree {
            scaffold(id = "editor_scaffold") {
                content {
                    board(id = "drawing_board")
                }
                floatingPanel(
                    id = "tools_floating",
                    anchor = FloatingAnchor.BOTTOM_END,
                    title = "Brush Settings",
                    isCollapsible = true,
                    isCollapsed = false
                ) {
                    slider(id = "size_slider", value = 8.0, min = 1.0, max = 30.0, label = "Stroke")
                    button(id = "reset_btn", label = "Reset")
                }
                sidebar(
                    id = "left_nav",
                    attachPoint = SidebarAttachPoint.LEFT,
                    title = "Toolbox",
                    isCollapsible = true,
                    isCollapsed = false
                ) {
                    text(id = "nav_label", text = "Tools")
                }
            }
        }

        val encoded = json.encodeToString(tree)
        val decoded = json.decodeFromString<UiNodeTree>(encoded)

        assertIs<ScaffoldNode>(decoded.root)
        val scaffold = decoded.root as ScaffoldNode
        assertEquals("editor_scaffold", scaffold.id)
        assertIs<BoardNode>(scaffold.content)
        assertEquals("drawing_board", (scaffold.content as BoardNode).id)

        assertEquals(1, scaffold.floatingPanels.size)
        val panel = scaffold.floatingPanels[0]
        assertEquals("tools_floating", panel.id)
        assertEquals(FloatingAnchor.BOTTOM_END, panel.anchor)
        assertEquals("Brush Settings", panel.title)
        assertTrue(panel.isCollapsible)
        assertEquals(false, panel.isCollapsed)
        assertEquals(2, panel.children.size)

        assertNotNull(scaffold.leftSidebar)
        assertIs<SidebarNode>(scaffold.leftSidebar)
        val sidebar = scaffold.leftSidebar as SidebarNode
        assertEquals("left_nav", sidebar.id)
        assertEquals(SidebarAttachPoint.LEFT, sidebar.attachPoint)
        assertEquals("Toolbox", sidebar.title)
        assertTrue(sidebar.isCollapsible)
        assertEquals(1, sidebar.children.size)
    }
}

