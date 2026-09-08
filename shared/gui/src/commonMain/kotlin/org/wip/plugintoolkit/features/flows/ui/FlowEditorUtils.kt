package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node

@Composable
internal fun NodeComponentPlaceholder(node: Node, height: Dp = ToolkitTheme.dimensions.heightLarge) {
    Surface(
        modifier = Modifier.width(ToolkitTheme.dimensions.nodeWidth).height(height),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = ToolkitTheme.opacity.textFieldContainer),
        border = BorderStroke(ToolkitTheme.dimensions.progressIndicatorStroke, MaterialTheme.colorScheme.onSurface.copy(alpha = ToolkitTheme.opacity.borderLow))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(node.title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = ToolkitTheme.opacity.disabled))
        }
    }
}

internal fun getPortRelativeOffset(node: Node, portId: String, density: androidx.compose.ui.unit.Density): Offset {
    val headerHeight = 48f
    val bodyTopPadding = 12f
    val rowHeight = 48f
    val spacing = 12f
    val dividerHeight = 0.5f

    val isInput = node.inputs.any { it.id == portId }
    val isOutput = node.outputs.any { it.id == portId }

    val xDp = if (isInput) 19f else 281f

    var yDp = headerHeight + bodyTopPadding
    val numInputs = node.inputs.size

    if (isInput) {
        val index = node.inputs.indexOfFirst { it.id == portId }
        if (index != -1) {
            yDp += index * (rowHeight + spacing) + (rowHeight / 2f)
        }
    } else if (isOutput) {
        val index = node.outputs.indexOfFirst { it.id == portId }
        if (index != -1) {
            var precedingHeight = numInputs * rowHeight
            var numGaps = numInputs
            if (numInputs > 0) {
                precedingHeight += dividerHeight
                numGaps += 1
            }
            yDp += precedingHeight + (numGaps + index) * spacing + index * rowHeight + (rowHeight / 2f)
        }
    }

    return with(density) {
        Offset(xDp.dp.toPx(), yDp.dp.toPx())
    }
}

internal fun findClosestPort(
    boardPosition: Offset,
    flow: Flow,
    connectionStartIsOutput: Boolean,
    scale: Float,
    getPortBoardPosition: (Long, String, Boolean) -> Offset?,
    isPortActiveOrConnected: ((Node, String, Boolean) -> Boolean)? = null
): Pair<Long?, String?> {
    var closestPortId: String? = null
    var closestNodeId: Long? = null
    var minDistance = 30f / scale

    flow.nodes.forEach { n ->
        val portsToTrack = if (connectionStartIsOutput) n.inputs else n.outputs
        portsToTrack.forEach { port ->
            val isOutput = !connectionStartIsOutput
            if (isPortActiveOrConnected != null && !isPortActiveOrConnected(n, port.id, isOutput)) {
                return@forEach
            }
            val portBoardPos = getPortBoardPosition(n.id, port.id, isOutput) ?: return@forEach
            val dist = (boardPosition - portBoardPos).getDistance()
            if (dist < minDistance) {
                minDistance = dist
                closestPortId = port.id
                closestNodeId = n.id
            }
        }
    }
    return Pair(closestNodeId, closestPortId)
}
