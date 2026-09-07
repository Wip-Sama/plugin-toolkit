package org.wip.plugintoolkit.features.flows.viewmodel

import org.wip.plugintoolkit.core.ui.UnsavedChangesTracker

open class ActiveFlowEditorTracker : UnsavedChangesTracker() {
    private var discardHandler: (() -> Unit)? = null

    fun registerDiscardHandler(handler: (() -> Unit)?) {
        discardHandler = handler
    }

    fun discardChanges() {
        discardHandler?.invoke()
        setHasUnsavedChanges(false)
    }
}
