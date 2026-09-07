package org.wip.plugintoolkit.features.repository.model

import org.wip.plugintoolkit.features.job.model.JobStatus

/**
 * Represents the active installation or lifecycle job state for a repository plugin.
 *
 * @param status The status of the installation/lifecycle job ([JobStatus.Queued] or [JobStatus.Running]).
 * @param progress The main execution progress (0.0 to 1.0).
 */
data class PluginInstallationJobState(
    val status: JobStatus,
    val progress: Float = 0f
)
