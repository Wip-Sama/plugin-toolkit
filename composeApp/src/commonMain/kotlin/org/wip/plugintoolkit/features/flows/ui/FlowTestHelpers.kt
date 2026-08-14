package org.wip.plugintoolkit.features.flows.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.Node
import org.wip.plugintoolkit.features.flows.viewmodel.FlowEditorState
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings

@Composable
fun RenderTestBoardCanvas(
    state: FlowEditorState = FlowEditorState(),
    flow: Flow = Flow("TestFlow"),
    onZoom: (Float, Offset, Boolean) -> Unit = { _, _, _ -> },
    selectedNodeIds: Set<Long> = emptySet(),
    onDeleteSelectedNodes: () -> Unit = {},
    onUndo: () -> Unit = {}
) {
    AppTheme(AppearanceSettings()) {
        BoardCanvas(
            state = state,
            flow = flow,
            onPan = {},
            onZoom = onZoom,
            isDrawingConnection = false,
            draggingNodeFromPalette = null,
            getPortBoardPosition = { _, _, _ -> null },
            highlightedPortId = null,
            highlightedNodeId = null,
            connectionStartNodeId = null,
            connectionStartPortId = null,
            connectionStartIsOutput = true,
            connectionCurrentPos = Offset.Zero,
            onBoardLayoutCoordinatesChanged = {},
            onBoardSizeChanged = {},
            onDeleteConnection = {},
            onDetachConnection = { _, _, _ -> },
            onConnectionDrag = {},
            onConnectionDrop = {},
            onConnectionCancel = {},
            onMoveConnectionFirst = {},
            onMoveConnectionLast = {},
            selectedNodeIds = selectedNodeIds,
            onSelectNodes = {},
            onClearSelection = {},
            onDeleteSelectedNodes = onDeleteSelectedNodes,
            onCopy = {},
            onPaste = {},
            onUndo = onUndo,
            onRedo = {},
            nodeSizes = emptyMap(),
            content = @Composable { _ -> }
        )
    }
}

@Composable
fun RenderTestNodeComponent(
    node: Node,
    onToggleCollapse: (Long) -> Unit = {},
    onDelete: (Long) -> Unit = {}
) {
    AppTheme(AppearanceSettings()) {
        NodeComponent(
            node = node,
            connectedInputPortIds = emptySet(),
            onMove = { _, _, _, _ -> },
            onEndMove = {},
            onDelete = onDelete,
            onExpand = {},
            onUpdateValue = { _, _, _ -> },
            onStartConnection = { _, _, _ -> },
            onToggleCollapse = onToggleCollapse,
            stateScale = 1f,
            stateOffset = Offset.Zero
        )
    }
}
