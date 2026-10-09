package org.wip.plugintoolkit.features.flows.model

/**
 * Scope defining which elements are included in the exported flow image.
 */
enum class FlowImageExportScope {
    /**
     * Exports all nodes, groups, labels, connections, and junctions in the entire flow.
     */
    WholeFlow,

    /**
     * Exports only the currently selected nodes, groups, labels, junctions, and their internal connections.
     */
    SelectedElements
}

/**
 * Resolution multiplier for flow image rasterization.
 * Generates vector text and components at true native pixel density for ultra-sharp readability.
 */
enum class FlowImageExportResolution(val scale: Float) {
    Half(0.5f),
    Standard(1.0f),
    UltraHD(2.0f)
}

/**
 * Background style for the exported flow image.
 */
enum class FlowImageExportBackground {
    /**
     * Theme surface background color with standard canvas dot grid pattern.
     */
    CanvasGrid,

    /**
     * Theme surface background color without grid dots.
     */
    SolidBackground,

    /**
     * Fully transparent background (ideal for embedding in docs and presentations).
     */
    Transparent
}

/**
 * Configuration options for rendering and exporting a flow as an image.
 */
data class FlowImageExportOptions(
    val scope: FlowImageExportScope = FlowImageExportScope.WholeFlow,
    val resolution: FlowImageExportResolution = FlowImageExportResolution.UltraHD,
    val background: FlowImageExportBackground = FlowImageExportBackground.CanvasGrid,
    val padding: Float = 60f
)
