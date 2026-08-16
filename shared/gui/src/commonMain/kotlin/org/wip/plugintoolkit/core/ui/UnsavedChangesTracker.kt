package org.wip.plugintoolkit.core.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

open class UnsavedChangesTracker {
    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    fun setHasUnsavedChanges(value: Boolean) {
        _hasUnsavedChanges.value = value
    }
}
