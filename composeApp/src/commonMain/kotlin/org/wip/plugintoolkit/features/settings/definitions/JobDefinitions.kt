package org.wip.plugintoolkit.features.settings.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Save
import org.wip.plugintoolkit.features.settings.model.AppSettings
import org.wip.plugintoolkit.features.settings.model.JobSettings
import org.wip.plugintoolkit.features.settings.model.SettingDefinition.NumericSetting
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey
import org.wip.plugintoolkit.features.settings.utils.SettingText
import org.wip.plugintoolkit.features.settings.utils.SettingsRegistryBuilder
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.setting_enable_transient_retries
import plugintoolkit.composeapp.generated.resources.setting_max_retries
import plugintoolkit.composeapp.generated.resources.setting_plugin_timeout

fun SettingsRegistryBuilder.jobDefinitions() {
    nav(SettingNavKey.SystemSettings) {
        section(SettingText.Raw("Jobs")) {
            SettingNumeric(
                p1 = AppSettings::jobs,
                p2 = JobSettings::maxConcurrentJobs,
                title = SettingText.Raw("Max Concurrent Jobs"),
                subtitle = SettingText.Raw("Maximum number of background tasks to run simultaneously"),
                icon = Icons.Default.AvTimer,
                valueRange = 1..10,
                setValue = { s, v -> s.copy(jobs = s.jobs.copy(maxConcurrentJobs = v)) }
            )

            SettingSwitch(
                p1 = AppSettings::jobs,
                p2 = JobSettings::saveHistory,
                title = SettingText.Raw("Save Job History"),
                subtitle = SettingText.Raw("Keep a record of completed, failed, and cancelled jobs"),
                icon = Icons.Default.Save,
                setValue = { s, v -> s.copy(jobs = s.jobs.copy(saveHistory = v)) }
            )

            SettingNumeric(
                p1 = AppSettings::jobs,
                p2 = JobSettings::maxHistoryLength,
                title = SettingText.Raw("Max History Length"),
                subtitle = SettingText.Raw("Maximum number of job events to keep in the history log"),
                icon = Icons.Default.History,
                enabled = { it.jobs.saveHistory },
                valueRange = 50..1000,
                setValue = { s, v -> s.copy(jobs = s.jobs.copy(maxHistoryLength = v)) }
            )

            SettingNumeric(
                p1 = AppSettings::jobs,
                p2 = JobSettings::maxEndedJobs,
                title = SettingText.Raw("Max Ended Jobs"),
                subtitle = SettingText.Raw("Maximum number of finished jobs to show in the UI dashboard"),
                icon = Icons.Default.PlaylistAddCheck,
                valueRange = 5..100,
                setValue = { s, v -> s.copy(jobs = s.jobs.copy(maxEndedJobs = v)) }
            )

            // We must map Long to Int since the Numeric UI component only accepts Ints.
            // 3600000 is 1 hour in ms.
            val timeoutDef = NumericSetting(
                id = "jobs.pluginTimeoutMs",
                title = SettingText.Resource(Res.string.setting_plugin_timeout),
                subtitle = SettingText.Raw("Maximum execution time for plugins (-1 for infinite)"),
                icon = Icons.Default.AvTimer,
                sectionTitle = SettingText.Raw("Jobs"),
                navKey = SettingNavKey.SystemSettings,
                getValue = { it.jobs.pluginTimeoutMs.toInt() },
                setValue = { s, v -> s.copy(jobs = s.jobs.copy(pluginTimeoutMs = v.toLong())) },
                valueRange = -1..3600000
            )
            add(timeoutDef)

            SettingSwitch(
                p1 = AppSettings::jobs,
                p2 = JobSettings::enableTransientRetries,
                title = SettingText.Resource(Res.string.setting_enable_transient_retries),
                subtitle = SettingText.Raw("Automatically retry plugins on transient network failures"),
                icon = Icons.Default.History,
                setValue = { s, v -> s.copy(jobs = s.jobs.copy(enableTransientRetries = v)) }
            )

            SettingNumeric(
                p1 = AppSettings::jobs,
                p2 = JobSettings::maxRetries,
                title = SettingText.Resource(Res.string.setting_max_retries),
                subtitle = SettingText.Raw("Maximum number of automatic retries"),
                icon = Icons.Default.History,
                enabled = { it.jobs.enableTransientRetries },
                valueRange = 1..10,
                setValue = { s, v -> s.copy(jobs = s.jobs.copy(maxRetries = v)) }
            )
        }
    }
}
