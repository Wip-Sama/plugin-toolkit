package org.wip.plugintoolkit.features.settings.model

import org.wip.plugintoolkit.core.update.UpdateInfo

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    data class UpdateAvailable(val info: UpdateInfo) : UpdateState()
    data class NeedsConfirmation(val info: UpdateInfo, val runningJobsCount: Int) : UpdateState()
    object UpToDate : UpdateState()
    object Error : UpdateState()
}

sealed class SettingsEvent {
    data class ShowToast(val toast: SettingsToast, val args: List<Any> = emptyList()) : SettingsEvent()
}

enum class SettingsToast {
    UpdateCheckStarted,
    UpdateNewVersionFound,
    UpdateNoUpdates,
    UpdateCheckFailed
}
