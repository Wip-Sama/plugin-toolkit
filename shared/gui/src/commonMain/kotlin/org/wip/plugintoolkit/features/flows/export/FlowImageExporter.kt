package org.wip.plugintoolkit.features.flows.export

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.wip.plugintoolkit.features.flows.model.Flow
import org.wip.plugintoolkit.features.flows.model.FlowImageExportOptions
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings

/**
 * Headless platform exporter for generating high-definition raster images of flows.
 */
expect object FlowImageExporter {
    /**
     * Renders a flow or selected elements to a PNG byte array at the requested resolution.
     */
    fun renderFlowToPng(
        flow: Flow,
        appearance: AppearanceSettings = AppearanceSettings(),
        options: FlowImageExportOptions = FlowImageExportOptions(),
        selectedNodeIds: Set<Long> = emptySet(),
        selectedGroupIds: Set<Long> = emptySet(),
        selectedLabelIds: Set<Long> = emptySet(),
        selectedPointIds: Set<Long> = emptySet(),
        measuredPortPositions: Map<Triple<Long, String, Boolean>, Offset> = emptyMap(),
        measuredNodeSizes: Map<Long, IntSize> = emptyMap()
    ): ByteArray?

    /**
     * Copies the given image byte array directly to the system clipboard.
     */
    fun copyImageToClipboard(bytes: ByteArray): Boolean
}
