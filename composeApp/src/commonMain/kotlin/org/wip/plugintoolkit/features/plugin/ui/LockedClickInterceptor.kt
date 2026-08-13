package org.wip.plugintoolkit.features.plugin.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import org.jetbrains.compose.resources.stringResource
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.dialog_cancel
import plugintoolkit.composeapp.generated.resources.dialog_unsaved_changes

/**
 * CompositionLocal providing global navigation to a plugin setting page across the application.
 */
val LocalNavigateToPluginSetting = staticCompositionLocalOf<((pluginId: String, settingKey: String) -> Unit)?> { null }

/**
 * Custom Compose Modifier that intercepts tap gestures on locked elements even when
 * their child UI composables are natively disabled (e.g. `enabled = false`).
 *
 * Intercepts events during [PointerEventPass.Initial] to capture tap events before disabled children swallow them:
 * 1. If [hasUnsavedChanges] is true, shows a warning confirmation dialog first.
 * 2. Calls [onNavigateToPluginSetting] (or [LocalNavigateToPluginSetting]) with ([pluginId], [targetSettingKey]) once confirmed or directly if no unsaved data.
 *
 * @param isLocked Whether the element is locked and should intercept clicks.
 * @param pluginId The plugin ID to navigate to.
 * @param targetSettingKey The specific setting key to deep-link to inside the plugin settings.
 * @param hasUnsavedChanges Whether there are unsaved changes that should warn the user first.
 * @param onNavigateToPluginSetting Navigation callback invoked with (pluginId, settingKey).
 *        If null, falls back to [LocalNavigateToPluginSetting].
 */
fun Modifier.lockedClickInterceptor(
    isLocked: Boolean,
    pluginId: String = "",
    targetSettingKey: String = "",
    hasUnsavedChanges: Boolean = false,
    onNavigateToPluginSetting: ((pluginId: String, settingKey: String) -> Unit)? = null
): Modifier = composed {
    val navCallback = onNavigateToPluginSetting ?: LocalNavigateToPluginSetting.current
    if (!isLocked || navCallback == null) return@composed this

    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(Res.string.dialog_unsaved_changes)) },
            text = { Text("All the unsaved data will be lost. Are you sure you want to exit?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        navCallback(pluginId, targetSettingKey)
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(Res.string.dialog_cancel))
                }
            }
        )
    }

    this.pointerInput(isLocked, hasUnsavedChanges, pluginId, targetSettingKey, navCallback) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull()
                if (change != null && change.pressed) {
                    change.consume()
                    if (hasUnsavedChanges) {
                        showDialog = true
                    } else {
                        navCallback(pluginId, targetSettingKey)
                    }
                }
            }
        }
    }
}
