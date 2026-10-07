package org.wip.plugintoolkit.features.controls.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import org.wip.plugintoolkit.features.controls.model.CanvasControlScheme

val LocalCanvasControlScheme = staticCompositionLocalOf<CanvasControlScheme> { CanvasControlScheme() }

/**
 * Provides [CanvasControlScheme] to the Composable hierarchy via [LocalCanvasControlScheme].
 */
@Composable
fun CanvasControlSchemeProvider(
    scheme: CanvasControlScheme,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalCanvasControlScheme provides scheme) {
        content()
    }
}
