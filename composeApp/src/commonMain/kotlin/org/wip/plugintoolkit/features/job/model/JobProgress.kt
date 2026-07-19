package org.wip.plugintoolkit.features.job.model

data class JobProgress(
    val mainProgress: Float = 0f,
    val capabilitiesProgress: Map<String, Float> = emptyMap()
)
