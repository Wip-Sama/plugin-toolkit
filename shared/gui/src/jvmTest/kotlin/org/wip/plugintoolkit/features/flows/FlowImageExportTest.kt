package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.core.utils.SemanticRegistry
import org.wip.plugintoolkit.features.flows.export.FlowImageExporter
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowImageExportBackground
import org.wip.plugintoolkit.features.flows.model.FlowImageExportOptions
import org.wip.plugintoolkit.features.flows.model.FlowImageExportResolution
import org.wip.plugintoolkit.features.flows.model.FlowImageExportScope
import org.wip.plugintoolkit.features.flows.model.FlowJunction
import org.wip.plugintoolkit.features.flows.model.FlowLabel
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset as ModelOffset
import org.wip.plugintoolkit.features.flows.model.OutputPort
import org.wip.plugintoolkit.features.flows.utils.FlowImageExportUtils
import org.wip.plugintoolkit.features.plugin.logic.PluginManager
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowImageExportTest {

    private lateinit var sampleFlow: Flow
    private lateinit var node1: Node
    private lateinit var node2: Node
    private lateinit var node3: Node
    private lateinit var conn12: Connection
    private lateinit var conn23: Connection
    private lateinit var group1: FlowGroup
    private lateinit var label1: FlowLabel
    private lateinit var junction1: FlowJunction

    @BeforeTest
    fun setUp() {
        stopKoin()
        val mockPluginManager = mockk<PluginManager>(relaxed = true)
        every { mockPluginManager.pluginLocksState } returns MutableStateFlow(emptyMap())
        val mockSemanticRegistry = mockk<SemanticRegistry>(relaxed = true)
        startKoin {
            modules(
                module {
                    single { mockPluginManager }
                    single { mockSemanticRegistry }
                }
            )
        }

        node1 = Node.SystemNode(
            id = 1L,
            position = ModelOffset(100f, 100f),
            title = "Start Node",
            systemAction = "save",
            inputs = emptyList(),
            outputs = listOf(OutputPort(id = "out1", name = "Out", dataType = DataType.Primitive(PrimitiveType.STRING)))
        )
        node2 = Node.SystemNode(
            id = 2L,
            position = ModelOffset(600f, 100f),
            title = "Processing Node",
            systemAction = "load",
            inputs = listOf(InputPort(id = "in1", name = "In", dataType = DataType.Primitive(PrimitiveType.STRING))),
            outputs = listOf(OutputPort(id = "out2", name = "Out 2", dataType = DataType.Primitive(PrimitiveType.STRING)))
        )
        node3 = Node.SystemNode(
            id = 3L,
            position = ModelOffset(1100f, 100f),
            title = "End Node",
            systemAction = "save",
            inputs = listOf(InputPort(id = "in2", name = "In 2", dataType = DataType.Primitive(PrimitiveType.STRING))),
            outputs = emptyList()
        )
        conn12 = Connection(
            sourceNodeId = 1L,
            sourcePortId = "out1",
            targetNodeId = 2L,
            targetPortId = "in1"
        )
        conn23 = Connection(
            sourceNodeId = 2L,
            sourcePortId = "out2",
            targetNodeId = 3L,
            targetPortId = "in2"
        )
        group1 = FlowGroup(
            id = 100L,
            title = "Main Group",
            position = ModelOffset(80f, 60f),
            size = ModelOffset(950f, 400f),
            nodeIds = listOf(1L, 2L)
        )
        label1 = FlowLabel(
            id = 200L,
            text = "Data Pipeline Stage",
            position = ModelOffset(120f, 70f)
        )
        junction1 = FlowJunction(
            id = 10L,
            position = ModelOffset(550f, 150f)
        )

        sampleFlow = Flow(
            name = "Test Export Flow",
            nodes = listOf(node1, node2, node3),
            connections = listOf(conn12, conn23),
            groups = listOf(group1),
            labels = listOf(label1),
            junctions = listOf(junction1)
        )
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun testFilterElementsWholeFlowReturnsAll() {
        val result = FlowImageExportUtils.filterElements(
            flow = sampleFlow,
            scope = FlowImageExportScope.WholeFlow
        )
        assertEquals(3, result.nodes.size)
        assertEquals(2, result.connections.size)
        assertEquals(1, result.groups.size)
        assertEquals(1, result.labels.size)
        assertEquals(1, result.junctions.size)
        assertFalse(result.isEmpty)
        assertEquals(6, result.totalElementsCount)
    }

    @Test
    fun testFilterElementsSelectedElementsExcludesNonSelected() {
        // Select only node1 and node2
        val result = FlowImageExportUtils.filterElements(
            flow = sampleFlow,
            scope = FlowImageExportScope.SelectedElements,
            selectedNodeIds = setOf(1L, 2L)
        )
        assertEquals(2, result.nodes.size)
        assertTrue(result.nodes.any { it.id == 1L })
        assertTrue(result.nodes.any { it.id == 2L })
        assertFalse(result.nodes.any { it.id == 3L })

        // Connection 1->2 should be included because both endpoints are in selection
        assertEquals(1, result.connections.size)
        assertEquals(conn12, result.connections.first())

        // Groups and labels were not selected
        assertTrue(result.groups.isEmpty())
        assertTrue(result.labels.isEmpty())
    }

    @Test
    fun testFilterElementsSelectingGroupIncludesContainedNodes() {
        // When group1 is selected, nodes 1 and 2 are automatically included
        val result = FlowImageExportUtils.filterElements(
            flow = sampleFlow,
            scope = FlowImageExportScope.SelectedElements,
            selectedGroupIds = setOf(100L)
        )
        assertEquals(1, result.groups.size)
        assertEquals(2, result.nodes.size)
        assertTrue(result.nodes.any { it.id == 1L })
        assertTrue(result.nodes.any { it.id == 2L })
        assertEquals(1, result.connections.size)
        assertEquals(conn12, result.connections.first())
    }

    @Test
    fun testComputeExportBoundsEmptyReturnsNull() {
        val emptyElements = FlowImageExportUtils.filterElements(
            flow = sampleFlow,
            scope = FlowImageExportScope.SelectedElements,
            selectedNodeIds = emptySet()
        )
        val bounds = FlowImageExportUtils.computeExportBounds(emptyElements)
        assertNull(bounds)
    }

    @Test
    fun testComputeExportBoundsEncompassesElementsWithPadding() {
        val elements = FlowImageExportUtils.filterElements(
            flow = sampleFlow,
            scope = FlowImageExportScope.WholeFlow
        )
        val padding = 60f
        val bounds = FlowImageExportUtils.computeExportBounds(elements, padding = padding)
        assertNotNull(bounds)

        // group1 starts at x=80, y=60
        assertTrue(bounds.left <= 80f - padding)
        assertTrue(bounds.top <= 60f - padding)

        // node3 is at x=1100 with default width 400 = 1500
        assertTrue(bounds.right >= 1500f + padding)
    }

    @Test
    fun testResolvePortPositionMeasuredAndFallback() {
        // 1. Fallback calculation for output port
        val outputPos = FlowImageExportUtils.resolvePortPosition(
            nodeId = 1L,
            portId = "out1",
            isOutput = true,
            node = node1,
            measuredPortPositions = null,
            nodeWidth = 400f
        )
        assertNotNull(outputPos)
        assertEquals(100f + 400f, outputPos.x) // node1.x + nodeWidth

        // 2. Fallback calculation for input port
        val inputPos = FlowImageExportUtils.resolvePortPosition(
            nodeId = 2L,
            portId = "in1",
            isOutput = false,
            node = node2,
            measuredPortPositions = null
        )
        assertNotNull(inputPos)
        assertEquals(600f, inputPos.x) // node2.x

        // 3. Measured port position preference
        val customMeasured = mapOf(Triple(1L, "out1", true) to Offset(999f, 888f))
        val resolvedMeasured = FlowImageExportUtils.resolvePortPosition(
            nodeId = 1L,
            portId = "out1",
            isOutput = true,
            node = node1,
            measuredPortPositions = customMeasured
        )
        assertEquals(Offset(999f, 888f), resolvedMeasured)
    }

    @Test
    fun testRenderFlowToPngWholeFlowGeneratesValidPng() {
        val options = FlowImageExportOptions(
            scope = FlowImageExportScope.WholeFlow,
            resolution = FlowImageExportResolution.Standard,
            background = FlowImageExportBackground.CanvasGrid,
            padding = 40f
        )

        val bytes = FlowImageExporter.renderFlowToPng(
            flow = sampleFlow,
            appearance = AppearanceSettings(),
            options = options
        )

        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())

        // Verify PNG magic bytes: 0x89, 'P', 'N', 'G' (0x89, 0x50, 0x4E, 0x47)
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals('P'.code.toByte(), bytes[1])
        assertEquals('N'.code.toByte(), bytes[2])
        assertEquals('G'.code.toByte(), bytes[3])
    }

    @Test
    fun testRenderFlowToPngUltraHD2xGeneratesValidPng() {
        val options = FlowImageExportOptions(
            scope = FlowImageExportScope.WholeFlow,
            resolution = FlowImageExportResolution.UltraHD,
            background = FlowImageExportBackground.SolidBackground,
            padding = 40f
        )

        val bytes = FlowImageExporter.renderFlowToPng(
            flow = sampleFlow,
            appearance = AppearanceSettings(),
            options = options
        )

        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())
        assertEquals(0x89.toByte(), bytes[0])
    }

    @Test
    fun testRenderFlowToPngSelectedElementsOnly() {
        val options = FlowImageExportOptions(
            scope = FlowImageExportScope.SelectedElements,
            resolution = FlowImageExportResolution.Standard,
            background = FlowImageExportBackground.Transparent,
            padding = 30f
        )

        val bytes = FlowImageExporter.renderFlowToPng(
            flow = sampleFlow,
            appearance = AppearanceSettings(),
            options = options,
            selectedNodeIds = setOf(1L, 2L)
        )

        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())
        assertEquals(0x89.toByte(), bytes[0])
    }

    @Test
    fun testRenderFlowToPngEmptySelectionReturnsNull() {
        val options = FlowImageExportOptions(
            scope = FlowImageExportScope.SelectedElements,
            resolution = FlowImageExportResolution.Standard,
            background = FlowImageExportBackground.Transparent
        )

        val bytes = FlowImageExporter.renderFlowToPng(
            flow = sampleFlow,
            appearance = AppearanceSettings(),
            options = options,
            selectedNodeIds = emptySet()
        )

        assertNull(bytes)
    }

    @Test
    fun testCopyImageToClipboardWithValidPng() {
        val options = FlowImageExportOptions(
            scope = FlowImageExportScope.WholeFlow,
            resolution = FlowImageExportResolution.Half
        )
        val bytes = FlowImageExporter.renderFlowToPng(
            flow = sampleFlow,
            appearance = AppearanceSettings(),
            options = options
        )
        assertNotNull(bytes)

        // Clipboard copy should succeed or handle gracefully in headless environments
        val result = FlowImageExporter.copyImageToClipboard(bytes)
        // If headless display is available in JVM environment, result is true
        assertTrue(result || !java.awt.GraphicsEnvironment.isHeadless())
    }
}
