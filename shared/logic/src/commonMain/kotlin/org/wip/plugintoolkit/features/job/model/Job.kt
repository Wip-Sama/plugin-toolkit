package org.wip.plugintoolkit.features.job.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock
import kotlin.time.Instant

@Serializable
enum class JobStatus {
    Queued,
    Running,
    Paused,
    Completed,
    Failed,
    Cancelled
}

@Serializable
enum class JobType {
    Capability,
    Flow,
    Setup,
    Update,
    Validation,
    PluginAction,
    PluginInstallation
}

@Serializable
data class BackgroundJob(
    val id: String,
    val name: String,
    val type: JobType,
    val status: JobStatus = JobStatus.Queued,
    val enqueuedAt: Instant = Clock.System.now(),
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val errorMessage: String? = null,
    val pluginId: String,
    val capabilityName: String,
    // Using a map of strings for parameters for now to simplify serialization
    // In a real app, we might use JsonObject
    val parameters: Map<String, JsonElement> = emptyMap(),
    val result: String? = null, // Serialized result
    val resumeState: JsonElement? = null,
    val keepResult: Boolean = true,
    val isPausable: Boolean = false,
    val isCancellable: Boolean = true,
    val executionMetrics: JobExecutionMetrics? = null
)

@Serializable
data class CapabilityExecutionMetric(
    val capabilityName: String,
    val durationMs: Long,
    val memoryUsageBytes: Long? = null
)

@Serializable
data class JobExecutionMetrics(
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val totalDurationMs: Long = 0L,
    val memoryUsageBytes: Long? = null,
    val capabilityMetrics: List<CapabilityExecutionMetric> = emptyList()
) {
    val totalDurationPerCapability: Map<String, Long>
        get() = capabilityMetrics
            .groupBy { it.capabilityName }
            .mapValues { (_, metrics) -> metrics.sumOf { it.durationMs } }

    val executionCountPerCapability: Map<String, Int>
        get() = capabilityMetrics
            .groupBy { it.capabilityName }
            .mapValues { (_, metrics) -> metrics.size }
}


@Serializable
data class JobHistoryEntry(
    val jobId: String,
    val jobName: String,
    val timestamp: Instant = Clock.System.now(),
    val event: String, // "Started", "Stopped", "Failed", etc.
    val details: String? = null
)
