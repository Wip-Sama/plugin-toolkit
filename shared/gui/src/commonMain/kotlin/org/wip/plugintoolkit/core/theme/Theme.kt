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
import androidx.compose.material3.Typography
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.wip.plugintoolkit.core.utils.PlatformUtils
import org.wip.plugintoolkit.features.settings.model.AppTheme
import org.wip.plugintoolkit.features.settings.model.AppearanceSettings

data class Spacing(
    val none: Dp = 0.dp,
    val extraExtraSmall: Dp = 2.dp,
    val xxs: Dp = 2.dp,
    val extraSmall: Dp = 4.dp,
    val xs: Dp = 4.dp,
    val small: Dp = 8.dp,
    val sm: Dp = 8.dp,
    @Deprecated("Violates 8dp/4dp grid. Use sm (8.dp) or md (16.dp)", ReplaceWith("small"))
    val smallMedium: Dp = 10.dp,
    @Deprecated("Violates 8dp/4dp grid. Use sm (8.dp) or md (16.dp)", ReplaceWith("small"))
    val mediumSmall: Dp = 12.dp,
    val medium: Dp = 16.dp,
    val md: Dp = 16.dp,
    @Deprecated("Violates 8dp/4dp grid. Use md (16.dp) or lg (24.dp)", ReplaceWith("medium"))
    val mediumLarge: Dp = 20.dp,
    val large: Dp = 24.dp,
    val lg: Dp = 24.dp,
    val extraLarge: Dp = 32.dp,
    val xl: Dp = 32.dp,
    val huge: Dp = 40.dp,
    val xxl: Dp = 48.dp,
    val massive: Dp = 64.dp,
    val xxxl: Dp = 64.dp,
    @Deprecated("Use xs (4.dp)", ReplaceWith("extraSmall"))
    val badgeHorizontal: Dp = 4.dp,
    @Deprecated("Use xxs (2.dp)", ReplaceWith("extraExtraSmall"))
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
    val borderThin: Dp = 0.5.dp,
    val iconMediumSmall: Dp = 18.dp,
    val emptyStateIconSize: Dp = 80.dp,
    val emptyStateTextWidth: Dp = 420.dp,
    val listIconSize: Dp = 54.dp,
    val listIconContentSize: Dp = 28.dp,
    val sidebarIconSize: Dp = 22.dp,
    val progressBoxSize: Dp = 36.dp,
    val progressIndicatorStroke: Dp = 2.dp,
    val elevationHigh: Dp = 3.dp,
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
    val genericInputWidth: Dp = 100.dp,
    val genericInputWidthLarge: Dp = 140.dp,
    val dialogMaxWidth: Dp = 1200.dp,
    val dialogMaxHeight: Dp = 900.dp,
    val iconExtraSmall: Dp = 14.dp,
    val iconMicro: Dp = 12.dp,
    val menuElevation: Dp = 4.dp,
    val iconExtraLarge: Dp = 64.dp,
    
    // Auto-generated generic/component-specific sized dimensions (deprecated in favor of semantic tokens)
    val ringWidthMedium: Dp = 10.dp,
    val previewRadiusLarge: Dp = 80.dp,
    val ringWidthLarge: Dp = 20.dp,
    val containerWidthLarge: Dp = 220.dp,
    val elevationHighMedium: Dp = 6.dp,
    val containerWidthMediumLarge: Dp = 50.dp,
    val heightMediumLarge: Dp = 30.dp,
    val thumbWidthLarge: Dp = 4.dp,
    val trackHeightSmall: Dp = 16.dp,
    val handleHeightMedium: Dp = 44.dp,
    val containerSizeLarge: Dp = 280.dp,
    val widthSmallExtra: Dp = 5.dp,
    val strokeWidthMedium: Dp = 5.dp,
    val strokeWidthMediumSmall: Dp = 5.dp,
    val strokeWidthThick: Dp = 3.dp,
    val strokeWidthThin: Dp = 3.dp,
    val widthLarge: Dp = 150.dp,
    val offsetLarge: Dp = 20.dp,
    val elevationMedium: Dp = 8.dp,
    val containerHeightLarge: Dp = 180.dp,
    val heightLarge: Dp = 120.dp,
    val heightMaxLarge: Dp = 200.dp,
    val cardHeightLarge: Dp = 320.dp,
    val iconMediumLarge: Dp = 20.dp,
    val elevationMediumHigh: Dp = 6.dp,
    val logoSizeLarge: Dp = 128.dp,
    val contentHeightLarge: Dp = 400.dp,
    val progressIndicatorHeightSmall: Dp = 8.dp,
    val minWidthMedium: Dp = 300.dp,
    val maxWidthLarge: Dp = 500.dp,
    val nodeWidth: Dp = 380.dp,
    val heightSmall: Dp = 8.dp,
    val dialogMaxWidthLarge: Dp = 1280.dp,
    
    @Deprecated("Use ToolkitTheme.shapes.extraSmall", ReplaceWith("ToolkitTheme.shapes.extraSmall"))
    val cornerRadiusExtraSmall: Dp = 4.dp,
    @Deprecated("Use ToolkitTheme.shapes.small", ReplaceWith("ToolkitTheme.shapes.small"))
    val cornerRadiusSmall: Dp = 8.dp,
    @Deprecated("Use ToolkitTheme.shapes.medium", ReplaceWith("ToolkitTheme.shapes.medium"))
    val cornerRadiusMedium: Dp = 12.dp,
    @Deprecated("Use ToolkitTheme.shapes.large", ReplaceWith("ToolkitTheme.shapes.large"))
    val cornerRadiusLarge: Dp = 16.dp,
    @Deprecated("Use ToolkitTheme.shapes.extraLarge", ReplaceWith("ToolkitTheme.shapes.extraLarge"))
    val cornerRadiusExtraLarge: Dp = 24.dp,
    val tooltipVerticalOffset: Dp = 8.dp,
    val dialogMaxWidthMedium: Dp = 540.dp,
    val filterChipHeight: Dp = 32.dp,
    val segmentedButtonHeight: Dp = 36.dp,
    val logHandleHeight: Dp = 24.dp,
    val logHandleWidth: Dp = 40.dp,
    val logHandleBarHeight: Dp = 4.dp,
    val logTerminalMinHeight: Dp = 100.dp,
    val logTerminalMaxHeight: Dp = 800.dp,
    val logTerminalDefaultHeight: Dp = 150.dp,
    val capabilityProgressBarHeight: Dp = 4.dp,
    val dialogUpdateWidth: Dp = 680.dp,
    val dialogUpdateHeight: Dp = 540.dp,
    val dialogUpdateMinWidth: Dp = 500.dp,
    val dialogUpdateMaxHeight: Dp = 620.dp,
    val updateIconContainerSize: Dp = 48.dp
)

data class CustomColors(
    val success: Color = Color(0xFF2E7D32),
    val onSuccess: Color = Color.White,
    val successContainer: Color = Color(0xFFA5D6A7),
    val onSuccessContainer: Color = Color(0xFF003300),
    val warning: Color = Color(0xFFE65100),
    val onWarning: Color = Color.Black,
    val warningContainer: Color = Color(0xFFFFCC80),
    val onWarningContainer: Color = Color(0xFF4E2600),
    val info: Color = Color(0xFF0277BD),
    val onInfo: Color = Color.White,
    val validated: Color = Color(0xFFD0BCFF),
    val onValidated: Color = Color(0xFF381E72),
    val transparent: Color = Color.Transparent,
    val white: Color = Color.White,
    val black: Color = Color.Black,
    @Deprecated("Use MaterialTheme.colorScheme.error", ReplaceWith("MaterialTheme.colorScheme.error"))
    val red: Color = Color(0xFFBA1A1A),
    @Deprecated("Use success", ReplaceWith("success"))
    val green: Color = Color(0xFF2E7D32),
    @Deprecated("Use info", ReplaceWith("info"))
    val blue: Color = Color(0xFF0277BD),
    @Deprecated("Use warning", ReplaceWith("warning"))
    val yellow: Color = Color(0xFFE65100),
    @Deprecated("Use MaterialTheme.colorScheme.outline", ReplaceWith("MaterialTheme.colorScheme.outline"))
    val gray: Color = Color(0xFF757575),
    @Deprecated("Use info", ReplaceWith("info"))
    val cyan: Color = Color(0xFF00838F),
    @Deprecated("Use primary", ReplaceWith("MaterialTheme.colorScheme.primary"))
    val magenta: Color = Color(0xFFAD1457)
)

data class Opacity(
    val transparent: Float = 0.0f,
    val cardBackground: Float = 0.05f,
    val subtleHighlight: Float = 0.08f,
    val borderLow: Float = 0.2f,
    @Deprecated("Glass styling is non-native in Material 3 Expressive. Use semantic surface containers instead.")
    val glassBackground: Float = 0.3f,
    val sidebarBackground: Float = 0.4f,
    val divider: Float = 0.5f,
    val disabled: Float = 0.6f,
    val high: Float = 0.7f,
    val secondaryText: Float = 0.8f,
    val almostOpaque: Float = 0.9f,
    val full: Float = 1.0f,
    val textFieldContainer: Float = 0.1f,
    val textFieldUnfocusedBorder: Float = 0.5f,
    val buttonBackground: Float = 0.15f,
    val settingsItemDefault: Float = 0.25f,
    val settingsItemHover: Float = 0.38f,
    val settingsItemPressed: Float = 0.55f,
    val chipTintedBackground: Float = 0.5f,
    val chipOutlinedBackground: Float = 0.1f,
    val disabledContent: Float = 0.5f
)

val DesktopTypography = Typography(
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium)
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalDimensions = staticCompositionLocalOf { Dimensions() }
val LocalCustomColors = staticCompositionLocalOf { CustomColors() }
val LocalOpacity = staticCompositionLocalOf { Opacity() }
val LocalTypography = staticCompositionLocalOf { DesktopTypography }

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

    val typography: Typography
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.typography

    val codeMedium: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)

    val codeSmall: TextStyle
        @Composable
        @ReadOnlyComposable
        get() = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
}

private val DarkColorScheme = ColorEngine.createStandardScheme(Color(0xFF6750A4), isDark = true, isAmoled = false)
private val LightColorScheme = ColorEngine.createStandardScheme(Color(0xFF6750A4), isDark = false, isAmoled = false)
private val AmoledColorScheme = ColorEngine.createStandardScheme(Color(0xFF6750A4), isDark = true, isAmoled = true)

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

    val isAmoled = appearance.theme == AppTheme.Amoled

    val colorScheme = if (appearance.useAccentInTheme) {
        ColorEngine.createExpressiveScheme(seedColor, isDark = darkTheme, isAmoled = isAmoled)
    } else {
        ColorEngine.createStandardScheme(seedColor, isDark = darkTheme, isAmoled = isAmoled)
    }

    CompositionLocalProvider(
        LocalSpacing provides Spacing(),
        LocalDimensions provides Dimensions(),
        LocalCustomColors provides CustomColors(
            validated = colorScheme.primary,
            onValidated = colorScheme.onPrimary,
            success = if (darkTheme) Color(0xFF81C784) else Color(0xFF2E7D32),
            onSuccess = if (darkTheme) Color(0xFF00390B) else Color.White,
            warning = if (darkTheme) Color(0xFFFFB74D) else Color(0xFFE65100),
            onWarning = if (darkTheme) Color(0xFF4E2600) else Color.Black,
            info = if (darkTheme) Color(0xFF64B5F6) else Color(0xFF0277BD),
            onInfo = if (darkTheme) Color(0xFF003258) else Color.White
        ),
        LocalOpacity provides Opacity(),
        LocalShapes provides ToolkitShapes(),
        LocalTypography provides DesktopTypography
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = DesktopTypography,
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


