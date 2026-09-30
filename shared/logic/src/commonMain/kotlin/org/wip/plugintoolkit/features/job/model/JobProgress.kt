package org.wip.plugintoolkit.features.job.model

import kotlinx.serialization.Serializable
import org.wip.plugintoolkit.api.ProgressData
import org.wip.plugintoolkit.api.ProgressDisplayMode

/**
 * Detailed progress information for an individual capability, action, or sub-task within a job.
 */
@Serializable
data class CapabilityProgressItem(
    val progress: Float = 0f,
    val message: String? = null,
    val current: Double? = null,
    val total: Double? = null,
    val unit: String? = null,
    val displayMode: ProgressDisplayMode = ProgressDisplayMode.PERCENTAGE,
    val secondaryProgress: Float? = null,
    val secondaryMessage: String? = null,
    val secondaryCurrent: Double? = null,
    val secondaryTotal: Double? = null,
    val secondaryUnit: String? = null,
    val secondaryDisplayMode: ProgressDisplayMode = ProgressDisplayMode.PERCENTAGE
) {
    companion object {
        fun fromProgressData(data: ProgressData): CapabilityProgressItem =
            CapabilityProgressItem(
                progress = data.fraction ?: 0f,
                message = data.message,
                current = data.current,
                total = data.total,
                unit = data.unit,
                displayMode = data.displayMode
            )
    }
}

/**
 * Execution progress snapshot for a background job, holding primary progress, secondary progress,
 * and individual capability/sub-task progresses.
 */
@Serializable
data class JobProgress(
    val mainProgress: Float = 0f,
    val mainMessage: String? = null,
    val mainCurrent: Double? = null,
    val mainTotal: Double? = null,
    val mainUnit: String? = null,
    val mainDisplayMode: ProgressDisplayMode = ProgressDisplayMode.PERCENTAGE,
    val secondaryProgress: Float? = null,
    val secondaryMessage: String? = null,
    val secondaryCurrent: Double? = null,
    val secondaryTotal: Double? = null,
    val secondaryUnit: String? = null,
    val secondaryDisplayMode: ProgressDisplayMode = ProgressDisplayMode.PERCENTAGE,
    val capabilitiesProgress: Map<String, Float> = emptyMap(),
    val capabilitiesDetailedProgress: Map<String, CapabilityProgressItem> = emptyMap()
)
