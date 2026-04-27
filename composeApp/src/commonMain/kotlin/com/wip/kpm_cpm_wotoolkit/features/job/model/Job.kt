package com.wip.kpm_cpm_wotoolkit.features.job.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock
import kotlin.time.Clock.System
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
    Flow
}

@Serializable
data class BackgroundJob(
    val id: String,
    val name: String,
    val type: JobType,
    val status: JobStatus = JobStatus.Queued,
    val progress: Float = 0f,
    val enqueuedAt: Instant = Clock.System.now(),
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val errorMessage: String? = null,
    val pluginId: String,
    val capabilityName: String,
    // Using a map of strings for parameters for now to simplify serialization
    // In a real app, we might use JsonObject
    val parameters: Map<String, JsonElement> = emptyMap<String, JsonElement>(),
    val result: String? = null // Serialized result
) {
    val elapsedTime: Long
        get() {
            val end = completedAt ?: Clock.System.now()
            val start = startedAt ?: return 0
            return (end - start).inWholeMilliseconds
        }
}

@Serializable
data class JobHistoryEntry(
    val jobId: String,
    val jobName: String,
    val timestamp: Instant = Clock.System.now(),
    val event: String, // "Started", "Stopped", "Failed", etc.
    val details: String? = null
)
