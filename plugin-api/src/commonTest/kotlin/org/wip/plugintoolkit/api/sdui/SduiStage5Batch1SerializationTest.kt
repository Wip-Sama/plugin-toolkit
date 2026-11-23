package org.wip.plugintoolkit.api.sdui

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SduiStage5Batch1SerializationTest {

    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    @Test
    fun `batch1 nodes and universal modifiers round-trip cleanly`() {
        val tree = uiTree(version = 2) {
            column(id = "root") {
                text(
                    text = "Copyable truncated header",
                    id = "txt_sel",
                    selectable = true,
                    maxLines = 2,
                    overflowEllipsis = true,
                    modifier = UiModifier
                        .tooltip("Selectable summary")
                        .cornerRadius(ShapeRole.LARGE)
                        .contextMenu {
                            item(key = "copy", label = "Copy", action = "copy_text", iconName = "copy")
                            item(key = "clear", label = "Clear", isDestructive = true, startsSection = true)
                        }
                )
                image(
                    asset = AssetReference.Uri("https://example.com/preview.png"),
                    id = "img_zoom",
                    contentDescription = "Preview page",
                    zoomable = true,
                    modifier = UiModifier.cornerRadius(ShapeRole.PILL).tooltip("Click magnifier to inspect")
                )
                radioGroup(
                    id = "radio_mode",
                    selectedKey = "fast",
                    label = "Upload Mode",
                    horizontal = true
                ) {
                    option(key = "fast", label = "Fast", subtitle = "Parallel upload")
                    option(key = "safe", label = "Safe", subtitle = "Sequential verified", enabled = false)
                }
                numberInput(
                    id = "num_retries",
                    value = 3.0,
                    min = 1.0,
                    max = 10.0,
                    step = 1.0,
                    unitSuffix = "tries",
                    label = "Max Retries"
                )
                searchBar(
                    id = "search_filter",
                    query = "chap",
                    suggestions = listOf("chapter 1", "chapter 2"),
                    placeholder = "Filter entries...",
                    label = "Search",
                    action = "run_search"
                )
                accordion(
                    title = "Advanced Manifest",
                    id = "acc_adv",
                    subtitle = "Inspect serialized JSON payload",
                    expanded = true,
                    leadingIcon = "code",
                    badgeText = "JSON",
                    badgeColor = SemanticColor.INFO
                ) {
                    codeBlock(
                        id = "code_manifest",
                        code = "{\n  \"version\": \"4.1.0-alpha01\"\n}",
                        language = "json",
                        title = "manifest.json",
                        maxHeightDp = 240
                    )
                }
            }
        }

        val encoded = json.encodeToString(UiNodeTree.serializer(), tree)
        val decoded = json.decodeFromString(UiNodeTree.serializer(), encoded)
        assertEquals(tree, decoded)

        val root = assertIs<ColumnNode>(decoded.root)
        val textNode = assertIs<TextNode>(root.children[0])
        assertTrue(textNode.selectable)
        assertEquals(2, textNode.maxLines)
        assertTrue(textNode.overflowEllipsis)
        assertEquals(ShapeRole.LARGE, textNode.modifier.cornerRadius)
        assertEquals("Selectable summary", textNode.modifier.tooltip)
        assertEquals(2, textNode.modifier.contextMenu.size)

        val imageNode = assertIs<ImageNode>(root.children[1])
        assertTrue(imageNode.zoomable)
        assertEquals(ShapeRole.PILL, imageNode.modifier.cornerRadius)

        val radioNode = assertIs<RadioGroupNode>(root.children[2])
        assertTrue(radioNode.horizontal)
        assertEquals(2, radioNode.options.size)

        val numberNode = assertIs<NumberInputNode>(root.children[3])
        assertEquals("tries", numberNode.unitSuffix)

        val searchNode = assertIs<SearchBarNode>(root.children[4])
        assertEquals("run_search", searchNode.action)
        assertEquals(listOf("chapter 1", "chapter 2"), searchNode.suggestions)

        val accordionNode = assertIs<AccordionNode>(root.children[5])
        assertTrue(accordionNode.expanded)
        val codeNode = assertIs<CodeBlockNode>(accordionNode.children.single())
        assertEquals("json", codeNode.language)
        assertEquals(240, codeNode.maxHeightDp)
    }

    @Test
    fun `legacy text and image JSON without batch1 fields decode with safe defaults`() {
        val legacyJson = """
            {
              "version": 1,
              "root": {
                "type": "column",
                "id": "legacy_col",
                "children": [
                  { "type": "text", "id": "legacy_txt", "text": "Hello" },
                  {
                    "type": "image",
                    "id": "legacy_img",
                    "asset": { "type": "uri", "uriString": "https://example.com/a.png" }
                  }
                ]
              }
            }
        """.trimIndent()

        val decoded = json.decodeFromString(UiNodeTree.serializer(), legacyJson)
        val root = assertIs<ColumnNode>(decoded.root)
        val textNode = assertIs<TextNode>(root.children[0])
        assertFalse(textNode.selectable)
        assertEquals(null, textNode.maxLines)
        assertFalse(textNode.overflowEllipsis)
        assertEquals(null, textNode.modifier.cornerRadius)
        assertEquals(null, textNode.modifier.tooltip)
        assertTrue(textNode.modifier.contextMenu.isEmpty())

        val imageNode = assertIs<ImageNode>(root.children[1])
        assertFalse(imageNode.zoomable)
    }
}
