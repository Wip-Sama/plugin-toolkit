package com.wip.kpm_cpm_wotoolkit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import com.wip.kpm_cpm_wotoolkit.PlatformUtils
import com.wip.kpm_cpm_wotoolkit.settings.AppTheme
import com.wip.kpm_cpm_wotoolkit.settings.AppearanceSettings

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
                    onPrimaryContainer = seedColor
            )

    MaterialTheme(colorScheme = colorScheme, content = content)
}
