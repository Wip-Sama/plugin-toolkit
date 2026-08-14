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
import androidx.navigation3.runtime.NavKey
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.features.navigation.GlobalRouter
import org.wip.plugintoolkit.features.navigation.LocalGlobalRouter
import org.wip.plugintoolkit.features.navigation.model.Screen
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.dialog_cancel
import plugintoolkit.composeapp.generated.resources.dialog_unsaved_changes

/**
 * CompositionLocal providing global navigation to a plugin setting page across the application.
 */
@Deprecated(
    message = "You're using LocalNavigateToPluginSetting. Migarate to LocalGlobalRouter.",
    replaceWith = ReplaceWith("LocalGlobalRouter", "org.wip.plugintoolkit.features.navigation.LocalGlobalRouter")
)
val LocalNavigateToPluginSetting = staticCompositionLocalOf<((pluginId: String, settingKey: String) -> Unit)?> { null }

/**
 * Custom Compose Modifier that intercepts tap gestures on locked elements even when
 * their child UI composables are natively disabled (e.g. `enabled = false`).
 *
 * Intercepts events during [PointerEventPass.Initial] to capture tap events before disabled children swallow them:
 * 1. If [hasUnsavedChanges] is true, shows a warning confirmation dialog first.
 * 2. Calls [currentRouter].navigateTo([targetScreen]) once confirmed or directly if no unsaved data.
 *
 * @param isLocked Whether the element is locked and should intercept clicks.
 * @param targetScreen The type-safe [NavKey] destination (e.g. [Screen.PluginManager]) to navigate to.
 * @param hasUnsavedChanges Whether there are unsaved changes that should warn the user first.
 * @param router Explicit [GlobalRouter] instance. If null, falls back to [LocalGlobalRouter].
 */
fun Modifier.lockedClickInterceptor(
    isLocked: Boolean,
    targetScreen: NavKey? = null,
    hasUnsavedChanges: Boolean = false,
    router: GlobalRouter? = null
): Modifier = composed {
    val currentRouter = router ?: LocalGlobalRouter.current
    if (!isLocked || targetScreen == null) return@composed this

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
                        currentRouter.navigateTo(targetScreen)
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

    this.pointerInput(isLocked, hasUnsavedChanges, targetScreen, currentRouter) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull()
                if (change != null && change.pressed) {
                    change.consume()
                    if (hasUnsavedChanges) {
                        showDialog = true
                    } else {
                        currentRouter.navigateTo(targetScreen)
                    }
                }
            }
        }
    }
}

/**
 * Deprecated overload to support legacy code while guiding developers toward type-safe navigation.
 */
@Deprecated(
    message = "You're using multiple parameters (pluginId, targetSettingKey). Migrate to targetScreen using Type-Safe @Serializable objects.",
    replaceWith = ReplaceWith(
        "lockedClickInterceptor(isLocked = isLocked, targetScreen = Screen.PluginManager(pluginId = pluginId, scrollToSetting = targetSettingKey), hasUnsavedChanges = hasUnsavedChanges)",
        "org.wip.plugintoolkit.features.navigation.model.Screen"
    )
)
fun Modifier.lockedClickInterceptor(
    isLocked: Boolean,
    pluginId: String = "",
    targetSettingKey: String = "",
    hasUnsavedChanges: Boolean = false,
    onNavigateToPluginSetting: ((pluginId: String, settingKey: String) -> Unit)? = null
): Modifier = composed {
    val legacyCallback = onNavigateToPluginSetting ?: LocalNavigateToPluginSetting.current
    if (legacyCallback != null) {
        if (!isLocked) return@composed this

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
                            legacyCallback(pluginId, targetSettingKey)
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

        this.pointerInput(isLocked, hasUnsavedChanges, pluginId, targetSettingKey, legacyCallback) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull()
                    if (change != null && change.pressed) {
                        change.consume()
                        if (hasUnsavedChanges) {
                            showDialog = true
                        } else {
                            legacyCallback(pluginId, targetSettingKey)
                        }
                    }
                }
            }
        }
    } else {
        val targetScreen = if (pluginId.isNotEmpty() || targetSettingKey.isNotEmpty()) {
            Screen.PluginManager(pluginId = pluginId.ifEmpty { null }, scrollToSetting = targetSettingKey.ifEmpty { null })
        } else {
            null
        }
        this.then(
            Modifier.lockedClickInterceptor(
                isLocked = isLocked,
                targetScreen = targetScreen,
                hasUnsavedChanges = hasUnsavedChanges
            )
        )
    }
}
