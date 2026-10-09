package org.wip.plugintoolkit.features.flows.export

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.EncodedImageFormat
import org.wip.plugintoolkit.core.theme.AppTheme
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.FlowImageExportBackground
import org.wip.plugintoolkit.features.flows.model.FlowImageExportOptions
import org.wip.plugintoolkit.features.flows.ui.FlowGroupComponent
import org.wip.plugintoolkit.features.flows.ui.FlowLabelComponent
import org.wip.plugintoolkit.features.flows.ui.NodeComponent
import org.wip.plugintoolkit.features.flows.ui.canvas.ConnectionHitTester
import org.wip.plugintoolkit.features.flows.ui.toComposeOffset
import org.wip.plugintoolkit.features.flows.utils.FlowImageExportUtils
import org.wip.plugintoolkit.features.flows.utils.SplineMathUtils
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings
import org.wip.plugintoolkit.features.settings.model.ConnectionCurveStyle
import org.wip.plugintoolkit.features.settings.model.OrthogonalStepMode
import org.wip.plugintoolkit.shared.components.plugin.inputs.parseColorString
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.datatransfer.UnsupportedFlavorException
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.math.roundToInt

actual object FlowImageExporter {

    actual fun renderFlowToPng(
        flow: Flow,
        appearance: AppearanceSettings,
        options: FlowImageExportOptions,
        selectedNodeIds: Set<Long>,
        selectedGroupIds: Set<Long>,
        selectedLabelIds: Set<Long>,
        selectedPointIds: Set<Long>,
        measuredPortPositions: Map<Triple<Long, String, Boolean>, Offset>,
        measuredNodeSizes: Map<Long, IntSize>
    ): ByteArray? {
        val elements = FlowImageExportUtils.filterElements(
            flow = flow,
            scope = options.scope,
            selectedNodeIds = selectedNodeIds,
            selectedGroupIds = selectedGroupIds,
            selectedLabelIds = selectedLabelIds,
            selectedPointIds = selectedPointIds
        )

        if (elements.isEmpty) return null

        val bounds = FlowImageExportUtils.computeExportBounds(
            elements = elements,
            nodeSizes = measuredNodeSizes,
            padding = options.padding
        ) ?: return null

        val scale = options.resolution.scale
        val pixelWidth = (bounds.width * scale).roundToInt().coerceIn(100, 16384)
        val pixelHeight = (bounds.height * scale).roundToInt().coerceIn(100, 16384)

        val scene = ImageComposeScene(
            width = pixelWidth,
            height = pixelHeight,
            density = Density(scale)
        )

        return try {
            scene.setContent {
                AppTheme(appearance = appearance) {
                    val dimensions = ToolkitTheme.dimensions
                    val connectionColor = MaterialTheme.colorScheme.primary
                    val surfaceColor = MaterialTheme.colorScheme.surface
                    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    val junctionMap = remember(flow.junctions) {
                        flow.junctions.associate { it.id to it.position.toComposeOffset() }
                    }
                    val allNodesMap = remember(flow.nodes) {
                        flow.nodes.associateBy { it.id }
                    }
                    val exportOrigin = Offset(bounds.left, bounds.top)

                    Box(modifier = Modifier.size(bounds.width.dp, bounds.height.dp)) {
                        // 1. Background layer
                        when (options.background) {
                            FlowImageExportBackground.SolidBackground -> {
                                Box(modifier = Modifier.fillMaxSize().background(surfaceColor))
                            }
                            FlowImageExportBackground.CanvasGrid -> {
                                Box(modifier = Modifier.fillMaxSize().background(surfaceColor))
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val gridSize = dimensions.gridStep.toPx()
                                    if (gridSize > 0f) {
                                        val cols = (size.width / gridSize).toInt() + 1
                                        val rows = (size.height / gridSize).toInt() + 1
                                        val points = ArrayList<Offset>((cols + 1) * (rows + 1))
                                        for (i in 0..cols) {
                                            for (j in 0..rows) {
                                                points.add(Offset(i * gridSize, j * gridSize))
                                            }
                                        }
                                        drawPoints(
                                            points = points,
                                            pointMode = PointMode.Points,
                                            color = gridColor,
                                            strokeWidth = 2.dp.toPx(),
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }
                            FlowImageExportBackground.Transparent -> {
                                // Transparent background
                            }
                        }

                        // 2. Groups layer
                        elements.groups.forEach { group ->
                            Box(
                                modifier = Modifier
                                    .offset((group.position.x - bounds.left).dp, (group.position.y - bounds.top).dp)
                                    .size(group.size.x.dp, group.size.y.dp)
                            ) {
                                FlowGroupComponent(
                                    group = group,
                                    stateScale = 1f,
                                    stateOffset = Offset.Zero,
                                    isReadOnly = true,
                                    onUpdateGroup = {},
                                    onDeleteGroup = {}
                                )
                            }
                        }

                        // 3. Connections & Junctions Canvas
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val curveStyle = flow.connectionCurveStyle ?: ConnectionCurveStyle.CardinalSpline
                            val roundness = flow.connectionRoundness ?: 0.5f
                            val stepMode = flow.orthogonalStepMode ?: OrthogonalStepMode.Auto
                            val orthogonalPortLead = flow.orthogonalPortLead ?: false

                            elements.connections.forEach { connection ->
                                val boardPoints = ConnectionHitTester.getConnectionBoardPoints(
                                    connection = connection,
                                    getPortBoardPosition = { nodeId, portId, isOutput ->
                                        val node = allNodesMap[nodeId]
                                        FlowImageExportUtils.resolvePortPosition(
                                            nodeId,
                                            portId,
                                            isOutput,
                                            node,
                                            measuredPortPositions
                                        )
                                    },
                                    junctionMap = junctionMap,
                                    groups = elements.groups,
                                    density = this.density
                                )

                                if (boardPoints != null && boardPoints.size >= 2) {
                                    val screenPoints = boardPoints.map { (it - exportOrigin) * this.density }
                                    val (startIsHorizontal, endIsHorizontal) = ConnectionHitTester.getConnectionOrientations(
                                        connection = connection,
                                        connections = elements.connections,
                                        junctionMap = junctionMap,
                                        getPortBoardPosition = { nodeId, portId, isOutput ->
                                            val node = allNodesMap[nodeId]
                                            FlowImageExportUtils.resolvePortPosition(
                                                nodeId,
                                                portId,
                                                isOutput,
                                                node,
                                                measuredPortPositions
                                            )
                                        },
                                        groups = elements.groups,
                                        density = this.density,
                                        stepMode = stepMode,
                                        orthogonalPortLead = orthogonalPortLead,
                                        nodes = flow.nodes
                                    )

                                    val filletParams = ConnectionHitTester.getJunctionFilletParams(
                                        connection = connection,
                                        connections = elements.connections,
                                        junctionMap = junctionMap,
                                        getPortBoardPosition = { nodeId, portId, isOutput ->
                                            val node = allNodesMap[nodeId]
                                            FlowImageExportUtils.resolvePortPosition(
                                                nodeId,
                                                portId,
                                                isOutput,
                                                node,
                                                measuredPortPositions
                                            )
                                        },
                                        groups = elements.groups,
                                        density = this.density,
                                        scale = 1f,
                                        offset = -exportOrigin * this.density,
                                        tension = roundness,
                                        stepMode = stepMode,
                                        orthogonalPortLead = orthogonalPortLead,
                                        nodes = flow.nodes
                                    )

                                    val startBorderX = ConnectionHitTester.getSourceNodeBorderX(connection, flow.nodes)
                                        ?.let { (it - exportOrigin.x) * this.density }
                                    val endBorderX = ConnectionHitTester.getTargetNodeBorderX(connection, flow.nodes)
                                        ?.let { (it - exportOrigin.x) * this.density }

                                    val path = SplineMathUtils.buildConnectionPath(
                                        points = screenPoints,
                                        style = curveStyle,
                                        tension = roundness,
                                        startHorizontal = startIsHorizontal,
                                        endHorizontal = endIsHorizontal,
                                        scale = 1f,
                                        canvasOffset = Offset.Zero,
                                        stepMode = stepMode,
                                        startFilletLeadIn = filletParams.startFilletLeadIn,
                                        endTrimDistance = filletParams.endTrimDistance,
                                        useMiddleRouteForDirectConnection = ConnectionHitTester.usesMiddleRouteForDirectConnection(
                                            connection,
                                            orthogonalPortLead
                                        ),
                                        startPortLead = ConnectionHitTester.hasStartPortLead(
                                            connection,
                                            orthogonalPortLead
                                        ),
                                        endPortLead = ConnectionHitTester.hasEndPortLead(
                                            connection,
                                            orthogonalPortLead
                                        ),
                                        startBorderX = startBorderX,
                                        endBorderX = endBorderX
                                    )

                                    val connColor = connection.color
                                    val effectiveColor = if (!connColor.isNullOrBlank()) {
                                        parseColorString(connColor)
                                    } else {
                                        connectionColor
                                    }

                                    drawPath(
                                        path = path,
                                        color = effectiveColor,
                                        style = Stroke(width = dimensions.strokeWidthThin.toPx())
                                    )
                                }
                            }

                            // Junctions
                            elements.junctions.forEach { junction ->
                                val center = (junction.position.toComposeOffset() - exportOrigin) * this.density
                                val juncColor = junction.color
                                val baseColor = if (!juncColor.isNullOrBlank()) {
                                    parseColorString(juncColor)
                                } else {
                                    connectionColor
                                }
                                drawCircle(
                                    color = baseColor,
                                    radius = 5.5f * this.density,
                                    center = center
                                )
                                drawCircle(
                                    color = surfaceColor,
                                    radius = 5.5f * this.density,
                                    center = center,
                                    style = Stroke(width = 1.5f * this.density)
                                )
                            }
                        }

                        // 4. Labels Layer
                        elements.labels.forEach { label ->
                            Box(
                                modifier = Modifier
                                    .offset((label.position.x - bounds.left).dp, (label.position.y - bounds.top).dp)
                            ) {
                                FlowLabelComponent(
                                    label = label,
                                    stateScale = 1f,
                                    stateOffset = Offset.Zero,
                                    isReadOnly = true,
                                    onUpdateLabel = {},
                                    onDeleteLabel = {}
                                )
                            }
                        }

                        // 5. Nodes Layer
                        elements.nodes.forEach { node ->
                            val connectedInputs = remember(elements.connections, node.id) {
                                elements.connections.filter { it.targetNodeId == node.id }.map { it.targetPortId }.toSet()
                            }
                            val connectedOutputs = remember(elements.connections, node.id) {
                                elements.connections.filter { it.sourceNodeId == node.id }.map { it.sourcePortId }.toSet()
                            }
                            Box(
                                modifier = Modifier.offset(
                                    (node.position.x - bounds.left).dp,
                                    (node.position.y - bounds.top).dp
                                )
                            ) {
                                NodeComponent(
                                    node = node,
                                    connectedInputPortIds = connectedInputs,
                                    connectedOutputPortIds = connectedOutputs,
                                    onMove = { _, _, _, _ -> },
                                    onEndMove = {},
                                    onDelete = {},
                                    onExpand = {},
                                    onUpdateValue = { _, _, _ -> },
                                    onStartConnection = { _, _, _ -> },
                                    stateScale = 1f,
                                    stateOffset = Offset.Zero,
                                    isReadOnly = true
                                )
                            }
                        }
                    }
                }
            }

            val image = scene.render()
            val pngData = image.encodeToData(EncodedImageFormat.PNG)
            pngData?.bytes
        } finally {
            scene.close()
        }
    }

    actual fun copyImageToClipboard(bytes: ByteArray): Boolean {
        return try {
            val image = ImageIO.read(ByteArrayInputStream(bytes)) ?: return false
            val transferable = object : Transferable {
                override fun getTransferDataFlavors(): Array<DataFlavor> =
                    arrayOf(DataFlavor.imageFlavor)

                override fun isDataFlavorSupported(flavor: DataFlavor): Boolean =
                    flavor == DataFlavor.imageFlavor

                override fun getTransferData(flavor: DataFlavor): Any {
                    if (flavor == DataFlavor.imageFlavor) return image
                    throw UnsupportedFlavorException(flavor)
                }
            }
            Toolkit.getDefaultToolkit().systemClipboard.setContents(transferable, null)
            true
        } catch (e: Exception) {
            false
        }
    }
}
