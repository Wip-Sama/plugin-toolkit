package org.wip.plugintoolkit.features.flows

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.ui.canvas.ConnectionHitTester
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectionSpatialHitTest {

    @Test
    fun testScreenBoundingBoxComputation() {
        val points = listOf(
            Offset(100f, 200f),
            Offset(50f, 350f),
            Offset(300f, 150f),
            Offset(250f, 400f)
        )
        val box = ConnectionHitTester.computeScreenBoundingBox(points)

        assertEquals(50f, box.minX)
        assertEquals(300f, box.maxX)
        assertEquals(150f, box.minY)
        assertEquals(400f, box.maxY)

        // Inside box
        assertTrue(box.containsWithMargin(Offset(200f, 250f), margin = 10f))

        // Outside box but within margin
        assertTrue(box.containsWithMargin(Offset(45f, 250f), margin = 10f))

        // Completely outside margin
        assertFalse(box.containsWithMargin(Offset(35f, 250f), margin = 10f))
        assertFalse(box.containsWithMargin(Offset(200f, 500f), margin = 20f))
    }

    @Test
    fun testSpatialPruningEliminatesDistantConnections() {
        val conn1 = Connection(sourceNodeId = 1L, sourcePortId = "out", targetNodeId = 2L, targetPortId = "in")
        val conn2 = Connection(sourceNodeId = 3L, sourcePortId = "out", targetNodeId = 4L, targetPortId = "in")

        val portPositions = mapOf(
            Pair(1L, "out") to Offset(100f, 100f),
            Pair(2L, "in") to Offset(200f, 100f),
            Pair(3L, "out") to Offset(5000f, 5000f),
            Pair(4L, "in") to Offset(5200f, 5000f)
        )

        val getPortBoardPos: (Long, String, Boolean) -> Offset? = { nodeId, portId, _ ->
            portPositions[Pair(nodeId, portId)]
        }

        // Test hit near conn1 (Offset(150f, 100f))
        val hit1 = ConnectionHitTester.findClosestConnection(
            position = Offset(150f, 100f),
            connections = listOf(conn1, conn2),
            getPortBoardPosition = getPortBoardPos,
            scale = 1f,
            offset = Offset.Zero
        )
        assertNotNull(hit1)
        assertEquals(conn1, hit1)

        // Test position nowhere near either connection (e.g. (1000f, 1000f))
        val hitNone = ConnectionHitTester.findClosestConnection(
            position = Offset(1000f, 1000f),
            connections = listOf(conn1, conn2),
            getPortBoardPosition = getPortBoardPos,
            scale = 1f,
            offset = Offset.Zero
        )
        assertNull(hitNone)

        // Test projection hit near conn1
        val proj = ConnectionHitTester.findClosestConnectionWithProjection(
            position = Offset(150f, 100f),
            connections = listOf(conn1, conn2),
            getPortBoardPosition = getPortBoardPos,
            scale = 1f,
            offset = Offset.Zero
        )
        assertNotNull(proj)
        assertEquals(conn1, proj.connection)
    }
}
