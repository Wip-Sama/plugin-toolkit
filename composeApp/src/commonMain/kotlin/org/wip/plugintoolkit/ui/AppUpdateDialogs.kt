package org.wip.plugintoolkit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.wip.plugintoolkit.features.settings.model.UpdateState
import org.wip.plugintoolkit.features.settings.viewmodel.SettingsViewModel
import org.wip.plugintoolkit.features.settings.ui.UpdateConfirmationDialog
import org.wip.plugintoolkit.features.settings.ui.UpdateDialog

@Composable
fun AppUpdateDialogs(viewModel: SettingsViewModel) {
    val availableUpdate = viewModel.availableUpdate
    val updateState by viewModel.updateState.collectAsState()
    val isDownloading = viewModel.isDownloadingUpdate
    val downloadProgress by viewModel.downloadProgress.collectAsState()

    if (availableUpdate != null && updateState !is UpdateState.NeedsConfirmation) {
        UpdateDialog(
            updateInfo = availableUpdate,
            isDownloading = isDownloading,
            progress = downloadProgress,
            onDownload = { viewModel.downloadAndInstallUpdate() },
            onDismiss = { viewModel.dismissUpdate() }
        )
    }

    if (updateState is UpdateState.NeedsConfirmation) {
        val state = updateState as UpdateState.NeedsConfirmation
        UpdateConfirmationDialog(
            runningJobsCount = state.runningJobsCount,
            onConfirm = { force -> viewModel.confirmUpdate(force) },
            onDismiss = { viewModel.dismissUpdate() }
        )
    }
}
