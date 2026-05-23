package org.wip.plugintoolkit.features.flows.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ActiveFlowEditorTracker {
    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    fun setHasUnsavedChanges(value: Boolean) {
        _hasUnsavedChanges.value = value
    }
}
