package org.wip.plugintoolkit.core.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * CompositionLocal that holds the current language code.
 * Used to trigger recomposition of all UI elements when language changes.
 */
val LocalLanguage = staticCompositionLocalOf { "en" }

