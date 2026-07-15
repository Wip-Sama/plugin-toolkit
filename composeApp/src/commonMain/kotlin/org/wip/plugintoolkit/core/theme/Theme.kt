package org.wip.plugintoolkit.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.settings.model.AppTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings

data class Spacing(
    val none: Dp = 0.dp,
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val mediumSmall: Dp = 12.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val huge: Dp = 40.dp,
    val massive: Dp = 64.dp,
    val badgeHorizontal: Dp = 6.dp,
    val badgeVertical: Dp = 2.dp
)

data class Dimensions(
    val sidebarCollapsedWidth: Dp = 80.dp,
    val sidebarExpandedWidth: Dp = 250.dp,
    val menuItem: Dp = 36.dp,
    val iconSmall: Dp = 16.dp,
    val iconMedium: Dp = 24.dp,
    val iconLarge: Dp = 32.dp,
    val pluginIcon: Dp = 48.dp,
    val cardElevation: Dp = 2.dp,
    val repositorySidebarWidth: Dp = 340.dp,
    val textFieldHeight: Dp = 56.dp,
    val borderSelected: Dp = 3.dp,
    val borderUnselected: Dp = 1.dp,
    val iconMediumSmall: Dp = 18.dp,
    val emptyStateIconSize: Dp = 80.dp,
    val emptyStateTextWidth: Dp = 420.dp,
    val listIconSize: Dp = 54.dp,
    val listIconContentSize: Dp = 28.dp,
    val progressBoxSize: Dp = 36.dp,
    val progressIndicatorStroke: Dp = 2.dp,
    val progressIndicatorStrokeMedium: Dp = 3.dp,
    val textFieldCornerRadius: Dp = 20.dp,
    val expressiveCardCornerRadius: Dp = 24.dp,
    val expressiveButtonCornerRadius: Dp = 24.dp,
    val buttonGroupGap: Dp = 2.dp,
    val buttonGroupInnerCorner: Dp = 4.dp,
    val buttonGroupOuterCorner: Dp = 20.dp,
    val settingsIconContainerSize: Dp = 40.dp,
    val settingsIconSize: Dp = 20.dp,
    val settingsIconCornerRadius: Dp = 10.dp,
    val toggleButtonIconSize: Dp = 16.dp,
    val standardButtonHeight: Dp = 40.dp,
    val circularProgressStrokeWidth: Dp = 2.dp,
    val circularProgressSize: Dp = 16.dp,
    val genericInputWidth: Dp = 100.dp
)

data class CustomColors(
    val success: Color = Color(0xFF4CAF50),
    val warning: Color = Color(0xFFFF9800),
    val info: Color = Color(0xFF2196F3),
    val validated: Color = Color(0xFFD0BCFF)
) {
    val onSuccess: Color = if (success.luminance() > 0.5f) Color.Black else Color.White
    val onWarning: Color = if (warning.luminance() > 0.5f) Color.Black else Color.White
    val onInfo: Color = if (info.luminance() > 0.5f) Color.Black else Color.White
    val onValidated: Color = if (validated.luminance() > 0.5f) Color.Black else Color.White
}

data class Opacity(
    val transparent: Float = 0.0f,
    val cardBackground: Float = 0.05f,
    val borderLow: Float = 0.2f,
    val glassBackground: Float = 0.3f,
    val sidebarBackground: Float = 0.4f,
    val divider: Float = 0.5f,
    val disabled: Float = 0.6f,
    val secondaryText: Float = 0.8f,
    val full: Float = 1.0f,
    val textFieldContainer: Float = 0.1f,
    val textFieldUnfocusedBorder: Float = 0.5f,
    val buttonBackground: Float = 0.15f,
    val settingsItemDefault: Float = 0.25f,
    val settingsItemHover: Float = 0.38f,
    val settingsItemPressed: Float = 0.55f
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalDimensions = staticCompositionLocalOf { Dimensions() }
val LocalCustomColors = staticCompositionLocalOf { CustomColors() }
val LocalOpacity = staticCompositionLocalOf { Opacity() }
data class ToolkitShapes(
    val extraSmall: CornerBasedShape = RoundedCornerShape(4.dp),
    val small: CornerBasedShape = RoundedCornerShape(8.dp),
    val medium: CornerBasedShape = RoundedCornerShape(12.dp),
    val large: CornerBasedShape = RoundedCornerShape(16.dp),
    val extraLarge: CornerBasedShape = RoundedCornerShape(24.dp),
    val startActionRow: CornerBasedShape = RoundedCornerShape(
        topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 4.dp
    ),
    val middleActionRow: CornerBasedShape = RoundedCornerShape(4.dp),
    val endActionRow: CornerBasedShape = RoundedCornerShape(
        topStart = 4.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp
    ),
    val standAloneActionRow: CornerBasedShape = RoundedCornerShape(16.dp)
) {
    val material = androidx.compose.material3.Shapes(
        extraSmall = extraSmall,
        small = small,
        medium = medium,
        large = large,
        extraLarge = extraLarge
    )
}

val LocalShapes = staticCompositionLocalOf { ToolkitShapes() }

object ToolkitTheme {
    val spacing: Spacing
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacing.current

    val dimensions: Dimensions
        @Composable
        @ReadOnlyComposable
        get() = LocalDimensions.current

    val colors: CustomColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCustomColors.current

    val opacity: Opacity
        @Composable
        @ReadOnlyComposable
        get() = LocalOpacity.current

    val shapes: ToolkitShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalShapes.current
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

    val isLightPrimary = seedColor.luminance() > 0.5f
    val onPrimaryColor = if (isLightPrimary) Color.Black else Color.White

    val colorScheme =
        baseScheme.copy(
            primary = seedColor,
            onPrimary = onPrimaryColor,
            primaryContainer = seedColor.copy(alpha = 0.2f),
            onPrimaryContainer = seedColor,
            error = Color(0xFFB00020),
            onError = Color.White
        )

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalDimensions provides Dimensions(),
        LocalCustomColors provides CustomColors(
            validated = seedColor,
            success = Color(0xFF4CAF50) //TODO: Could be tuned based on theme
        ),
        LocalOpacity provides Opacity(),
        LocalShapes provides ToolkitShapes()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = LocalShapes.current.material,
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

