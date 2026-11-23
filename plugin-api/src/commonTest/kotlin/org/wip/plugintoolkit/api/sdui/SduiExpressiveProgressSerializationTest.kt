package org.wip.plugintoolkit.api.sdui

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SduiExpressiveProgressSerializationTest {

    private val json = Json {
        encodeDefaults = false
        ignoreUnknownKeys = true
    }

    @Test
    fun `progress bar styles and loading indicator round-trip cleanly`() {
        val tree = uiTree(version = 1) {
            column(id = "progress_root") {
                progressBar(
                    id = "wavy_linear",
                    progress = 0.64f,
                    label = "Uploading chapters",
                    statusText = "64%",
                    style = ProgressBarStyle.WAVY
                )
                progressBar(
                    id = "morph_progress",
                    label = "Preparing archive",
                    style = ProgressBarStyle.MORPHING_INDICATOR
                )
                loadingIndicator(
                    id = "contained_loader",
                    label = "Connecting",
                    contained = true,
                    progress = 0.25f,
                    color = SemanticColor.TERTIARY,
                    size = ControlSize.LARGE
                )
            }
        }

        val encoded = json.encodeToString(UiNodeTree.serializer(), tree)
        val decoded = json.decodeFromString(UiNodeTree.serializer(), encoded)
        assertEquals(tree, decoded)

        val root = assertIs<ColumnNode>(decoded.root)
        val wavy = assertIs<ProgressBarNode>(root.children[0])
        assertEquals(ProgressBarStyle.WAVY, wavy.style)
        val morph = assertIs<ProgressBarNode>(root.children[1])
        assertEquals(ProgressBarStyle.MORPHING_INDICATOR, morph.style)
        val loader = assertIs<LoadingIndicatorNode>(root.children[2])
        assertEquals(true, loader.contained)
        assertEquals(0.25f, loader.progress)
        assertEquals(ControlSize.LARGE, loader.size)
    }

    @Test
    fun `legacy progress bar JSON defaults style to STANDARD`() {
        val legacyJson = """
            {
              "version": 1,
              "root": {
                "type": "progress_bar",
                "id": "legacy_pb",
                "progress": 0.4
              }
            }
        """.trimIndent()

        val decoded = json.decodeFromString(UiNodeTree.serializer(), legacyJson)
        val node = assertIs<ProgressBarNode>(decoded.root)
        assertEquals(ProgressBarStyle.STANDARD, node.style)
        assertEquals(0.4f, node.progress)
    }
}
