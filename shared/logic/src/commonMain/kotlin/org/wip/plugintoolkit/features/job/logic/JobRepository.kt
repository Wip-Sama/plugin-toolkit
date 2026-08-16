package org.wip.plugintoolkit.features.job.logic

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json
import org.wip.plugintoolkit.features.job.model.BackgroundJob
import org.wip.plugintoolkit.features.job.model.JobStatus
import org.wip.plugintoolkit.features.settings.logic.SettingsPersistence

class JobRepository(
    private val settingsPersistence: SettingsPersistence
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun getJobsFile(): Path {
        val jobsDir = settingsPersistence.getJobsDir()
        if (!SystemFileSystem.exists(Path(jobsDir))) {
            SystemFileSystem.createDirectories(Path(jobsDir))
        }
        return Path("$jobsDir/jobs.json")
    }

    suspend fun loadJobs(): List<BackgroundJob> = withContext(Dispatchers.IO) {
        val file = getJobsFile()
        if (!SystemFileSystem.exists(file)) {
            return@withContext emptyList()
        }

        try {
            val content = SystemFileSystem.source(file).buffered().use { it.readString() }
            if (content.isBlank()) return@withContext emptyList()

            val jobs = json.decodeFromString<List<BackgroundJob>>(content)

            // Adjust states for recovered jobs
            return@withContext jobs.map { job ->
                if (job.status == JobStatus.Running || job.status == JobStatus.Queued) {
                    Logger.w { "Recovered job ${job.id} was ${job.status}, marking as Failed/Interrupted." }
                    job.copy(
                        status = JobStatus.Failed,
                        errorMessage = "Job was interrupted due to application termination."
                    )
                } else {
                    job
                }
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to load jobs from repository" }
            emptyList()
        }
    }

    suspend fun saveJobs(jobs: List<BackgroundJob>) = withContext(Dispatchers.IO) {
        val file = getJobsFile()
        try {
            val content = json.encodeToString(jobs)
            SystemFileSystem.sink(file).buffered().use { it.writeString(content) }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to save jobs to repository" }
        }
    }
}
