package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.wip.plugintoolkit.api.sdui.UiEvent

/**
 * Top-level container hosting an active SDUI session.
 * Manages root event callback memoization, automatic lifecycle disposal on exit,
 * and session cancellation controls.
 */
@Composable
fun PluginCustomUiCard(
    handle: PluginUiSessionHandle,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    // Memoize the event dispatcher to prevent unnecessary child recompositions
    val onEvent: (UiEvent) -> Unit = remember(handle) {
        { event -> handle.dispatchEvent(event) }
    }

    // Automatically terminate and clean up the session coroutines when removed from composition
    DisposableEffect(handle.sessionId) {
        onDispose {
            if (handle.isActive) {
                handle.cancel("Interface closed")
            }
        }
    }

    val treeState by handle.uiNodeTree.collectAsState()

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        // Render the SDUI node tree root filling the entire available section
        SafeUiNodeRenderer(
            node = treeState.root,
            onEvent = onEvent,
            modifier = Modifier.fillMaxSize()
        )
    }
}
