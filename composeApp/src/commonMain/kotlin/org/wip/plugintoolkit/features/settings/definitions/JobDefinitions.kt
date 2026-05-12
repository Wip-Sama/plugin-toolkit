package org.wip.plugintoolkit.features.settings.definitions

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Save
import org.wip.plugintoolkit.features.settings.model.*
import org.wip.plugintoolkit.features.settings.ui.SettingNavKey
import org.wip.plugintoolkit.features.settings.utils.*

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
        }
    }
}
