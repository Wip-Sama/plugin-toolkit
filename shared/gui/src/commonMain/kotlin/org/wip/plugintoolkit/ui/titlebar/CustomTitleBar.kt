package org.wip.plugintoolkit.ui.titlebar

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.wip.plugintoolkit.core.theme.ToolkitTheme
import org.wip.plugintoolkit.core.utils.PlatformUtils
import plugintoolkit.composeapp.generated.resources.Res
import plugintoolkit.composeapp.generated.resources.app_logo
import plugintoolkit.composeapp.generated.resources.app_name

/**
 * Custom draggable application title bar integrating seamlessly with the application theme.
 */
@Composable
fun CustomTitleBar(
    modifier: Modifier = Modifier,
    title: String = stringResource(Res.string.app_name),
    icon: Painter? = painterResource(Res.drawable.app_logo),
    showTitleAndIcon: Boolean = true,
    controller: WindowController? = LocalWindowController.current,
    isMac: Boolean = PlatformUtils.isMac
) {
    val titleBarHeight = ToolkitTheme.dimensions.titleBarHeight

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(titleBarHeight)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // macOS: Traffic light controls on the left
        if (isMac && controller != null) {
            Box(
                modifier = Modifier
                    .padding(start = ToolkitTheme.spacing.medium, end = ToolkitTheme.spacing.small)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                MacWindowControls(controller = controller)
            }
        }

        // Draggable main bar area
        WindowDraggableArea(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            onDoubleClick = { controller?.onMaximizeToggle?.invoke() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(horizontal = ToolkitTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showTitleAndIcon) {
                    if (icon != null) {
                        Image(
                            painter = icon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(ToolkitTheme.dimensions.iconSmall)
                                .padding(end = ToolkitTheme.spacing.extraSmall)
                        )
                    }

                    Text(
                        text = title,
                        style = ToolkitTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // Windows / Linux: Controls on the right
        if (!isMac && controller != null) {
            WindowsWindowControls(
                controller = controller,
                modifier = Modifier.fillMaxHeight()
            )
        }
    }
}
