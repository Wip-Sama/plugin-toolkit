package com.wip.kpm_cpm_wotoolkit.core.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

import com.wip.kpm_cpm_wotoolkit.features.plugin.logic.ChangelogVersion

class DialogService {
    private val _dialogState = MutableStateFlow<DialogData?>(null)
    val dialogState: StateFlow<DialogData?> = _dialogState.asStateFlow()

    fun showConfirmation(title: String, message: String, onConfirm: () -> Unit) {
        _dialogState.value = DialogData.Confirmation(title, message, onConfirm)
    }

    fun showLocationPicker(title: String, folders: List<String>, onSelected: (String) -> Unit) {
        _dialogState.value = DialogData.LocationPicker(title, folders, onSelected)
    }

    fun showWarning(title: String, message: String, onConfirm: () -> Unit) {
        _dialogState.value = DialogData.Warning(title, message, onConfirm)
    }

    fun showChangelog(moduleName: String, versions: List<ChangelogVersion>) {
        _dialogState.value = DialogData.Changelog(moduleName, versions)
    }

    fun dismiss() {
        _dialogState.value = null
    }
}

sealed class DialogData {
    data class Confirmation(val title: String, val message: String, val onConfirm: () -> Unit) : DialogData()
    data class Warning(val title: String, val message: String, val onConfirm: () -> Unit) : DialogData()
    data class LocationPicker(val title: String, val folders: List<String>, val onSelected: (String) -> Unit) : DialogData()
    data class Changelog(val moduleName: String, val versions: List<ChangelogVersion>) : DialogData()
}
