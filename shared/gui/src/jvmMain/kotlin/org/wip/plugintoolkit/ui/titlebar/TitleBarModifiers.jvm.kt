package org.wip.plugintoolkit.ui.titlebar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import org.wip.plugintoolkit.core.utils.PlatformUtils
import kotlin.math.roundToInt

@Composable
actual fun Modifier.nonDraggableTitleBar(key: Any?): Modifier {
    if (!PlatformUtils.isWindows) return this

    val window = LocalWindowScope.current?.window ?: return this
    val density = LocalDensity.current
    val itemKey = remember(key) { key ?: Any() }

    DisposableEffect(window, itemKey) {
        onDispose {
            NativeDrag.unregisterNonDraggableArea(window, itemKey)
        }
    }

    return this.onGloballyPositioned { coordinates ->
        if (coordinates.isAttached) {
            val pos = coordinates.positionInWindow()
            val size = coordinates.size
            val xDp = (pos.x / density.density).roundToInt()
            val yDp = (pos.y / density.density).roundToInt()
            val widthDp = (size.width / density.density).roundToInt()
            val heightDp = (size.height / density.density).roundToInt()

            NativeDrag.registerNonDraggableArea(
                window = window,
                key = itemKey,
                rect = NativeDrag.TitleBarRect(
                    x = xDp,
                    y = yDp,
                    width = widthDp,
                    height = heightDp
                )
            )
        }
    }
}

@Composable
actual fun TitleBarLeftOffsetSync(leftOffsetDp: Int) {
    if (!PlatformUtils.isWindows) return
    val window = LocalWindowScope.current?.window ?: return

    LaunchedEffect(window, leftOffsetDp) {
        NativeDrag.setLeftOffsetFor(window, leftOffsetDp)
    }
}
