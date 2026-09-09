package org.wip.plugintoolkit.shared.components.sidebar

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import org.wip.plugintoolkit.core.theme.ToolkitTheme

/**
 * Standardized container for primary and secondary application sidebars.
 * Provides consistent width, padding, container colors, and a Top-Body-Bottom layout structure.
 */
@Composable
fun SidebarContainer(
    modifier: Modifier = Modifier,
    width: Dp = ToolkitTheme.dimensions.sidebarExpandedWidth,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    shadowElevation: Dp = ToolkitTheme.spacing.none,
    topContent: @Composable (ColumnScope.() -> Unit)? = null,
    bodyContent: @Composable (ColumnScope.() -> Unit)? = null,
    bottomContent: @Composable (ColumnScope.() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .width(width)
            .fillMaxHeight(),
        color = containerColor,
        shadowElevation = shadowElevation
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(ToolkitTheme.spacing.mediumSmall)
        ) {
            if (topContent != null) {
                topContent()
            }
            if (bodyContent != null) {
                bodyContent()
            }
            if (bottomContent != null) {
                bottomContent()
            }
        }
    }
}
