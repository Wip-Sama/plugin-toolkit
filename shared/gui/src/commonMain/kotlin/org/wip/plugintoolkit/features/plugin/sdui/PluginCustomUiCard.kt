package org.wip.plugintoolkit.features.plugin.sdui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.api.sdui.UiEvent
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.sdui_close_session
import plugintoolkit.composeapp.generated.resources.sdui_interactive_mode

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

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ToolkitTheme.dimensions.expressiveCardCornerRadius))
            .border(
                width = ToolkitTheme.dimensions.borderUnselected,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(ToolkitTheme.dimensions.expressiveCardCornerRadius)
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(ToolkitTheme.dimensions.expressiveCardCornerRadius)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ToolkitTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.sm)
        ) {
            // Header: Status Badge & Capability Info & Close Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(ToolkitTheme.dimensions.buttonGroupInnerCorner)
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = ToolkitTheme.spacing.xs,
                                vertical = ToolkitTheme.spacing.xxs
                            ),
                            horizontalArrangement = Arrangement.spacedBy(ToolkitTheme.spacing.xxs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ElectricBolt,
                                contentDescription = null,
                                modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(Res.string.sdui_interactive_mode),
                                style = ToolkitTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Text(
                        text = "${handle.pluginId} • ${handle.capabilityName}",
                        style = ToolkitTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        handle.cancel("User closed interface")
                        onClose?.invoke()
                    },
                    modifier = Modifier.size(ToolkitTheme.dimensions.standardButtonHeight)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.sdui_close_session),
                        modifier = Modifier.size(ToolkitTheme.dimensions.iconSmall),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Render the SDUI node tree root
            SafeUiNodeRenderer(
                node = treeState.root,
                onEvent = onEvent,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
