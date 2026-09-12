package org.wip.plugintoolkit.features.shortcuts.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import org.wip.plugintoolkit.features.shortcuts.logic.ShortcutManager

val LocalShortcutManager = staticCompositionLocalOf<ShortcutManager?> { null }

/**
 * Provides [ShortcutManager] to the Composable hierarchy via [LocalShortcutManager].
 */
@Composable
fun ShortcutProvider(
    shortcutManager: ShortcutManager,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalShortcutManager provides shortcutManager) {
        content()
    }
}
