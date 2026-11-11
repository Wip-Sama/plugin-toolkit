package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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

    val assetContext = remember(handle.fileSystem, handle.cacheFileSystem) {
        PluginAssetContext(
            fileSystem = handle.fileSystem,
            cacheFileSystem = handle.cacheFileSystem
        )
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

    CompositionLocalProvider(LocalPluginAssetContext provides assetContext) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            // Render the SDUI node tree root filling the entire available section
            SafeUiNodeRenderer(
                node = treeState.root,
                onEvent = onEvent,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
