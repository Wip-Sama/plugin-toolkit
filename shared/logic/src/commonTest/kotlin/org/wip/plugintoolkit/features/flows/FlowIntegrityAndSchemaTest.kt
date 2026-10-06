package org.wip.plugintoolkit.features.flows

import org.wip.plugintoolkit.api.DataType
import org.wip.plugintoolkit.api.PrimitiveType
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.FlowGroup
import org.wip.plugintoolkit.features.flows.model.FlowJunction
import org.wip.plugintoolkit.features.flows.model.FlowSchemaMigrator
import org.wip.plugintoolkit.features.flows.model.InputPort
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.model.Offset
import org.wip.plugintoolkit.features.flows.model.OutputPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FlowIntegrityAndSchemaTest {

    @Test
    fun testFlowSchemaMigratorUpgradesLegacySchema() {
        val legacyFlow = Flow(
            name = "LegacyPipeline",
            version = "0.9.0",
            schemaVersion = 0,
            nodes = listOf(
                Node.FlowInputNode(
                    id = 1L,
                    position = Offset(100f, 100f),
                    outputs = listOf(OutputPort("out", "out", DataType.Primitive(PrimitiveType.STRING)))
                ),
                Node.FlowOutputNode(
                    id = 2L,
                    position = Offset(300f, 100f),
                    inputs = listOf(InputPort("in", "in", DataType.Primitive(PrimitiveType.STRING)))
                )
            ),
            connections = listOf(
                Connection(sourceNodeId = 1L, sourcePortId = "out", targetNodeId = 2L, targetPortId = "in"),
                // Duplicate connection that should be healed
                Connection(sourceNodeId = 1L, sourcePortId = "out", targetNodeId = 2L, targetPortId = "in")
            )
        )

        assertEquals(0, legacyFlow.schemaVersion)
        assertEquals(2, legacyFlow.connections.size)

        val migratedFlow = FlowSchemaMigrator.migrate(legacyFlow)

        assertEquals(Flow.CURRENT_SCHEMA_VERSION, migratedFlow.schemaVersion)
        assertEquals(1, migratedFlow.connections.size, "Duplicate connections must be healed during migration")
        assertEquals("LegacyPipeline", migratedFlow.name)
    }

    @Test
    fun testPurgeStrayPointsCleansesDeadGroupNodeIds() {
        val node1 = Node.FlowInputNode(
            id = 1L,
            position = Offset(50f, 50f),
            outputs = listOf(OutputPort("out", "out", DataType.Primitive(PrimitiveType.STRING)))
        )
        val node2 = Node.FlowOutputNode(
            id = 2L,
            position = Offset(200f, 50f),
            inputs = listOf(InputPort("in", "in", DataType.Primitive(PrimitiveType.STRING)))
        )
        val group = FlowGroup(
            id = 10L,
            title = "TestGroup",
            position = Offset(0f, 0f),
            size = Offset(300f, 200f),
            nodeIds = listOf(1L, 2L, 999L, 888L) // 999L and 888L are non-existent nodes
        )

        val flow = Flow(
            name = "GroupSanitizationFlow",
            nodes = listOf(node1, node2),
            groups = listOf(group)
        )

        val purged = flow.purgeStrayPoints()

        assertEquals(1, purged.groups.size)
        val sanitizedGroup = purged.groups.first()
        assertEquals(listOf(1L, 2L), sanitizedGroup.nodeIds, "Stray node IDs 999 and 888 must be purged from group")
    }

    @Test
    fun testFlowGroupContainsPoint() {
        val group = FlowGroup(
            id = 1L,
            title = "BoundsTest",
            position = Offset(100f, 100f),
            size = Offset(200f, 150f)
        )

        // Inside
        assertTrue(group.containsPoint(150f, 150f))
        assertTrue(group.containsPoint(Offset(200f, 200f)))

        // Boundaries
        assertTrue(group.containsPoint(100f, 100f))
        assertTrue(group.containsPoint(300f, 250f))

        // Outside
        assertFalse(group.containsPoint(99f, 100f))
        assertFalse(group.containsPoint(301f, 250f))
        assertFalse(group.containsPoint(Offset(50f, 50f)))
        assertFalse(group.containsPoint(Offset(400f, 400f)))
    }

    @Test
    fun testGroupRelativeCoordinatesPreservedOnMove() {
        val initialGroupPos = Offset(100f, 100f)
        val node1Pos = Offset(120f, 130f)
        val node2Pos = Offset(180f, 220f)

        val rel1 = node1Pos - initialGroupPos // (20, 30)
        val rel2 = node2Pos - initialGroupPos // (80, 120)

        val moveDelta = Offset(75f, 50f)
        val newGroupPos = initialGroupPos + moveDelta
        val newNode1Pos = node1Pos + moveDelta
        val newNode2Pos = node2Pos + moveDelta

        val newRel1 = newNode1Pos - newGroupPos
        val newRel2 = newNode2Pos - newGroupPos

        assertEquals(rel1, newRel1, "Relative coordinate of Node 1 to group origin must remain invariant")
        assertEquals(rel2, newRel2, "Relative coordinate of Node 2 to group origin must remain invariant")
    }
}
