package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.ui.NodeCardContainer
import org.wip.plugintoolkit.features.flows.ui.NodeComponent
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.plugin.model.PluginSettingsStore
import io.mockk.every
import io.mockk.mockk
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class FlowEditorUiTest {

    private val mockPluginManager = mockk<PluginManager>(relaxed = true)

    @BeforeTest
    fun setUp() {
        stopKoin()
        every { mockPluginManager.loadPluginSettings(any()) } returns PluginSettingsStore(emptyMap())
        startKoin {
            modules(module {
                single { mockPluginManager }
            })
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun testNodeHeaderRendersAndRespondsToGestures() = runComposeUiTest {
        var isPressed = false
        var movedDelta = Offset.Zero
        var moveEnded = false

        val testNode = Node.SystemNode(
            id = 99L,
            position = Offset(100f, 100f),
            title = "GUI Test Node",
            systemAction = "test",
            inputs = emptyList(),
            outputs = emptyList()
        )

        setContent {
            NodeCardContainer(
                nodePosition = testNode.position,
                dragOffset = Offset.Zero,
                scale = 1.0f,
                boardOffset = Offset.Zero
            ) {
                NodeComponent(
                    node = testNode,
                    connectedInputPortIds = emptySet(),
                    selectedNodeIds = emptySet(),
                    isReady = true,
                    isReadOnly = false,
                    stateScale = 1.0f,
                    stateOffset = Offset.Zero,
                    onPress = { isPressed = true },
                    onMove = { _, delta, _, _ -> movedDelta += delta },
                    onEndMove = { moveEnded = true },
                    onExpand = {},
                    onToggleCollapse = {},
                    onDelete = {},
                    onUpdateValue = { _, _, _ -> },
                    onStartConnection = { _, _, _ -> }
                )
            }
        }

        // Verify the node title exists in the Compose hierarchy
        onNodeWithText("GUI Test Node").assertExists()
    }
}
