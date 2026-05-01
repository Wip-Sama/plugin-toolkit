package com.wip.kpm_cpm_wotoolkit.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wip.kpm_cpm_wotoolkit.core.utils.PlatformUtils
import com.wip.kpm_cpm_wotoolkit.features.settings.model.AppTheme
import com.wip.kpm_cpm_wotoolkit.features.settings.model.AppearanceSettings

data class Spacing(
    val none: Dp = 0.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val mediumSmall: Dp = 12.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val huge: Dp = 40.dp,
    val massive: Dp = 64.dp
)

data class Dimensions(
    val sidebarCollapsedWidth: Dp = 80.dp,
    val sidebarExpandedWidth: Dp = 250.dp,
    val iconSmall: Dp = 16.dp,
    val iconMedium: Dp = 24.dp,
    val iconLarge: Dp = 32.dp,
    val pluginIcon: Dp = 48.dp,
    val cardElevation: Dp = 2.dp
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalDimensions = staticCompositionLocalOf { Dimensions() }

object WOTheme {
    val spacing: Spacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current

    val dimensions: Dimensions
        @Composable
        @ReadOnlyComposable
        get() = LocalDimensions.current
}

private val DarkColorScheme =
    darkColorScheme(
        primary = Color(0xFFD0BCFF),
        secondary = Color(0xFFCCC2DC),
        tertiary = Color(0xFFEFB8C8)
    )

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF6750A4),
        secondary = Color(0xFF625b71),
        tertiary = Color(0xFF7D5260)
    )

private val AmoledColorScheme =
    darkColorScheme(
        primary = Color(0xFFD0BCFF),
        secondary = Color(0xFFCCC2DC),
        tertiary = Color(0xFFEFB8C8),
        background = Color.Black,
        surface = Color.Black,
        surfaceVariant = Color(0xFF1C1B1F)
    )

@Composable
fun AppTheme(appearance: AppearanceSettings, content: @Composable () -> Unit) {
    val darkTheme =
        when (appearance.theme) {
            AppTheme.System -> isSystemInDarkTheme()
            AppTheme.Light -> false
            AppTheme.Dark -> true
            AppTheme.Amoled -> true
        }

    val systemAccent = remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(appearance.followSystemAccent) {
        if (appearance.followSystemAccent) {
            systemAccent.value = PlatformUtils.getSystemAccentColor()
        }
    }

    val seedColor =
        if (appearance.followSystemAccent) {
            systemAccent.value ?: Color(appearance.accentColor)
        } else {
            Color(appearance.accentColor)
        }

    val baseScheme =
        when {
            appearance.theme == AppTheme.Amoled -> AmoledColorScheme
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    val colorScheme =
        baseScheme.copy(
            primary = seedColor,
            primaryContainer = seedColor.copy(alpha = 0.2f),
            onPrimaryContainer = seedColor,
            error = Color(0xFFB00020),
            onError = Color.White
        )

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalDimensions provides Dimensions()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = Shapes,
            content = content
        )
    }
}

@Preview
@Composable
private fun ThemePreview() {
    AppTheme(appearance = AppearanceSettings()) {
        Surface {
            Box(modifier = Modifier.padding(16.dp)) {
                Button(onClick = {}) {
                    Text("Theme Preview Button")
                }
            }
        }
    }
}

