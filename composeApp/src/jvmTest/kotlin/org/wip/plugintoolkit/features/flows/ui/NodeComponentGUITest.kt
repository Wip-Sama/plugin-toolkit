package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.core.utils.SemanticRegistry
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.plugin.logic.PluginManager

class NodeComponentGUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setUp() {
        val mockManager = io.mockk.mockk<PluginManager>(relaxed = true)
        io.mockk.every { mockManager.pluginLocksState } returns kotlinx.coroutines.flow.MutableStateFlow(emptyMap())
        startKoin {
            modules(module {
                single { mockManager }
                single { io.mockk.mockk<SemanticRegistry>(relaxed = true) }
            })
        }
    }


    @After
    fun tearDown() {
        stopKoin()
    }

    private val sampleSystemNode = Node.SystemNode(
        id = 2L,
        position = Offset(200f, 200f),
        title = "Log Node",
        systemAction = "log",
        inputs = listOf(InputPort("message", "Message", DataType.Primitive(PrimitiveType.STRING))),
        outputs = listOf(OutputPort("result", "Result", DataType.Primitive(PrimitiveType.ANY)))
    )

    @Test
    fun testNodeCardRendersWithTitle() {
        composeTestRule.setContent {
            RenderTestNodeComponent(node = sampleSystemNode)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("node_card_2").assertIsDisplayed()
        composeTestRule.onNodeWithTag("node_header_2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Log Node").assertIsDisplayed()
    }

    @Test
    fun testCollapseButtonTogglesNodeBody() {
        var collapseToggled = false
        composeTestRule.setContent {
            RenderTestNodeComponent(
                node = sampleSystemNode,
                onToggleCollapse = { collapseToggled = true }
            )
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("collapse_button_2").performClick()
        composeTestRule.waitForIdle()
        assertTrue(collapseToggled)
    }

    @Test
    fun testDeleteButtonShowsConfirmationDialog() {
        composeTestRule.setContent {
            RenderTestNodeComponent(node = sampleSystemNode)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("delete_button_2").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("delete_confirm_dialog", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun testPortCircleRendersForInputAndOutput() {
        composeTestRule.setContent {
            RenderTestNodeComponent(node = sampleSystemNode)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("port_2_message_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("port_2_result_output").assertIsDisplayed()
    }
}
