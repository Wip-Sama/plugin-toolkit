package org.wip.plugintoolkit.core.utils

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.DarkDefaultContextMenuRepresentation
import androidx.compose.foundation.LightDefaultContextMenuRepresentation
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.luminance

private const val DARK_SURFACE_LUMINANCE_THRESHOLD = 0.5f

@Composable
actual fun PlatformContextMenuArea(
    items: List<PlatformContextMenuItem>,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    if (!enabled || items.isEmpty()) {
        content()
        return
    }

    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < DARK_SURFACE_LUMINANCE_THRESHOLD
    val representation = if (isDarkSurface) {
        DarkDefaultContextMenuRepresentation
    } else {
        LightDefaultContextMenuRepresentation
    }

    CompositionLocalProvider(LocalContextMenuRepresentation provides representation) {
        ContextMenuArea(
            items = {
                items.map { item ->
                    ContextMenuItem(label = item.label, onClick = item.onClick)
                }
            },
            enabled = true,
            content = content
        )
    }
}
