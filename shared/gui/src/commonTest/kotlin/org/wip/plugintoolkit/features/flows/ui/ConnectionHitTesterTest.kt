package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.features.flows.model.Connection
import org.wip.plugintoolkit.features.flows.ui.canvas.ConnectionHitTester
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class ConnectionHitTesterTest {

    @Test
    fun testFindClosestConnectionReturnsNullWhenNoConnections() {
        val result = ConnectionHitTester.findClosestConnection(
            position = Offset(100f, 100f),
            connections = emptyList(),
            getPortBoardPosition = { _, _, _ -> Offset.Zero },
            scale = 1f,
            offset = Offset.Zero
        )
        assertNull(result)
    }

    @Test
    fun testFindClosestConnectionFindsNearbyConnection() {
        val conn = Connection(1L, "out", 2L, "in")
        val getPortBoardPos: (Long, String, Boolean) -> Offset? = { nodeId, _, isOutput ->
            if (nodeId == 1L && isOutput) Offset(0f, 0f)
            else if (nodeId == 2L && !isOutput) Offset(100f, 0f)
            else null
        }

        // Point right on the straight wire between (0,0) and (100,0)
        val result = ConnectionHitTester.findClosestConnection(
            position = Offset(50f, 0f),
            connections = listOf(conn),
            getPortBoardPosition = getPortBoardPos,
            scale = 1f,
            offset = Offset.Zero
        )
        assertEquals(conn, result)
    }

    @Test
    fun testFindClosestConnectionIgnoresDistantWires() {
        val conn = Connection(1L, "out", 2L, "in")
        val getPortBoardPos: (Long, String, Boolean) -> Offset? = { nodeId, _, isOutput ->
            if (nodeId == 1L && isOutput) Offset(0f, 0f)
            else if (nodeId == 2L && !isOutput) Offset(100f, 0f)
            else null
        }

        // Point far away from wire
        val result = ConnectionHitTester.findClosestConnection(
            position = Offset(50f, 500f),
            connections = listOf(conn),
            getPortBoardPosition = getPortBoardPos,
            scale = 1f,
            offset = Offset.Zero
        )
        assertNull(result)
    }

    @Test
    fun testDetermineCloserEndReturnsSourceWhenCloser() {
        val isSource = ConnectionHitTester.determineCloserEnd(
            position = Offset(10f, 10f),
            sourceScreenPos = Offset(0f, 0f),
            targetScreenPos = Offset(100f, 100f)
        )
        assertTrue(isSource)
    }

    @Test
    fun testDetermineCloserEndReturnsTargetWhenCloser() {
        val isSource = ConnectionHitTester.determineCloserEnd(
            position = Offset(90f, 90f),
            sourceScreenPos = Offset(0f, 0f),
            targetScreenPos = Offset(100f, 100f)
        )
        assertFalse(isSource)
    }
}
