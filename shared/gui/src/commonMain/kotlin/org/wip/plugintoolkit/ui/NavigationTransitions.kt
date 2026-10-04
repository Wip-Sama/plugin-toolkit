package org.wip.plugintoolkit.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene

/**
 * Standard smooth desktop transition (100ms crossfade with incoming content on top).
 */
fun <T : Any> desktopNavTransitionSpec(): AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    ContentTransform(
        targetContentEnter = fadeIn(animationSpec = tween(100)),
        initialContentExit = fadeOut(animationSpec = tween(100)),
        targetContentZIndex = 1f
    )
}

/**
 * Creates a [NavEntry] with an opaque background to prevent visual bleed-through and ghosting.
 */
fun <T : Any> opaqueNavEntry(
    key: T,
    content: @Composable () -> Unit
): NavEntry<T> = NavEntry(key) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        content()
    }
}
