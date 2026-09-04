package org.wip.plugintoolkit.features.settings.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Subject
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.JobSettings
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey
import org.wip.plugintoolkit.features.settings.utils.SettingText
import org.wip.plugintoolkit.features.settings.utils.SettingsRegistryBuilder
import org.wip.plugintoolkit.features.settings.utils.bindGroup
import plugintoolkit.composeapp.generated.resources.*

/**
 * Registers background job engine configuration settings.
 */
fun SettingsRegistryBuilder.jobDefinitions() {
    nav(SettingNavKey.SystemSettings) {
        section(Res.string.section_jobs) {
            bindGroup(AppSettings::jobs, { copy(jobs = it) }) {
                numeric(
                    JobSettings::maxConcurrentJobs,
                    Res.string.setting_max_concurrent_jobs,
                    Icons.Default.AvTimer,
                    range = 1..10,
                    subtitle = SettingText.Resource(Res.string.setting_max_concurrent_jobs_subtitle)
                ) { copy(maxConcurrentJobs = it) }

                switch(
                    JobSettings::saveHistory,
                    Res.string.setting_save_job_history,
                    Icons.Default.Save,
                    subtitle = SettingText.Resource(Res.string.setting_save_job_history_subtitle)
                ) { copy(saveHistory = it) }

                numeric(
                    JobSettings::maxHistoryLength,
                    Res.string.setting_max_history_length,
                    Icons.Default.History,
                    range = 50..1000,
                    subtitle = SettingText.Resource(Res.string.setting_max_history_length_subtitle),
                    enabled = { it.jobs.saveHistory }
                ) { copy(maxHistoryLength = it) }

                numeric(
                    JobSettings::maxEndedJobs,
                    Res.string.setting_max_ended_jobs,
                    Icons.Default.PlaylistAddCheck,
                    range = 5..100,
                    subtitle = SettingText.Resource(Res.string.setting_max_ended_jobs_subtitle)
                ) { copy(maxEndedJobs = it) }

                numeric(
                    JobSettings::maxLogLines,
                    Res.string.setting_max_log_lines,
                    Icons.Default.Subject,
                    range = -1..10000,
                    subtitle = SettingText.Resource(Res.string.setting_max_log_lines_subtitle)
                ) { copy(maxLogLines = it) }

                numeric(
                    JobSettings::maxLogLineLength,
                    Res.string.setting_max_log_line_length,
                    Icons.Default.Subject,
                    range = -1..10000,
                    subtitle = SettingText.Resource(Res.string.setting_max_log_line_length_subtitle)
                ) { copy(maxLogLineLength = it) }

                longNumeric(
                    JobSettings::pluginTimeoutMs,
                    Res.string.setting_plugin_timeout,
                    Icons.Default.AvTimer,
                    range = -1..3600000,
                    subtitle = SettingText.Raw("Maximum execution time for plugins (-1 for infinite)")
                ) { copy(pluginTimeoutMs = it) }

                switch(
                    JobSettings::enableTransientRetries,
                    Res.string.setting_enable_transient_retries,
                    Icons.Default.History,
                    subtitle = SettingText.Raw("Automatically retry plugins on transient network failures")
                ) { copy(enableTransientRetries = it) }

                numeric(
                    JobSettings::maxRetries,
                    Res.string.setting_max_retries,
                    Icons.Default.History,
                    range = 1..10,
                    subtitle = SettingText.Raw("Maximum number of automatic retries"),
                    enabled = { it.jobs.enableTransientRetries }
                ) { copy(maxRetries = it) }
            }
        }
    }
}
