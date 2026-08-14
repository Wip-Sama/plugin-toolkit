package org.wip.plugintoolkit.features.repository.viewmodel

import org.wip.plugintoolkit.features.repository.model.RepoValidationResult

enum class RepoTypeTab {
    All,
    Remote,
    Local
}

enum class RepoStatusFilter {
    All,
    Synced,
    DevDrafts,
    HasUpdates
}

enum class PluginSortMode {
    NameAsc,
    NameDesc,
    StatusInstalledFirst,
    LatestVersion
}

enum class PluginChipFilter {
    All,
    Installed,
    UpdateAvailable,
    NotInstalled,
    Incompatible
}

data class AddRepoDialogState(
    val isOpen: Boolean = false,
    val isLocalMode: Boolean = false,
    val targetInput: String = "",
    val validationResult: RepoValidationResult = RepoValidationResult.Idle
)
