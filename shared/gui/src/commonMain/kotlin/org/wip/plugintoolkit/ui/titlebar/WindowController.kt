package org.wip.plugintoolkit.ui.titlebar

import androidx.compose.runtime.compositionLocalOf

/**
 * Controller interface allowing custom UI components to invoke window lifecycle actions.
 */
data class WindowController(
    val isMaximized: Boolean,
    val onMinimize: () -> Unit,
    val onMaximizeToggle: () -> Unit,
    val onClose: () -> Unit
)

/**
 * CompositionLocal providing access to the current window's WindowController.
 */
val LocalWindowController = compositionLocalOf<WindowController?> { null }
