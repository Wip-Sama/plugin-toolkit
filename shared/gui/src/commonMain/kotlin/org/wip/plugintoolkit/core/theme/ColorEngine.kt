package org.wip.plugintoolkit.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Material 3 Expressive Color Engine.
 * 
 * Implements perceptual color generation using the CIELAB / HCT (Hue, Chroma, Tone)
 * color space model to generate harmonious, accessible Material 3 tonal palettes
 * from any seed color.
 */
object ColorEngine {

    /**
     * HCT representation:
     * - [hue]: 0.0 .. 360.0 (perceptual hue angle)
     * - [chroma]: >= 0.0 (colorfulness / saturation)
     * - [tone]: 0.0 .. 100.0 (perceptual lightness L*)
     */
    data class Hct(val hue: Float, val chroma: Float, val tone: Float)

    /**
     * Convert an sRGB Compose [Color] to its perceptual [Hct] coordinates.
     */
    fun colorToHct(color: Color): Hct {
        val r = srgbToLinear(color.red)
        val g = srgbToLinear(color.green)
        val b = srgbToLinear(color.blue)

        // D65 reference white
        val x = (0.4124564f * r + 0.3575761f * g + 0.1804375f * b) / 0.95047f
        val y = (0.2126729f * r + 0.7151522f * g + 0.0721750f * b) / 1.00000f
        val z = (0.0193339f * r + 0.1191920f * g + 0.9503041f * b) / 1.08883f

        val fx = labF(x)
        val fy = labF(y)
        val fz = labF(z)

        val l = max(0f, 116f * fy - 16f)
        val a = 500f * (fx - fy)
        val bVal = 200f * (fy - fz)

        val chroma = sqrt(a * a + bVal * bVal)
        var hue = (atan2(bVal.toDouble(), a.toDouble()) * 180.0 / PI).toFloat()
        if (hue < 0f) hue += 360f

        return Hct(hue = hue, chroma = chroma, tone = l)
    }

    /**
     * Generate an sRGB Compose [Color] from [hue] (0..360), [chroma] (>=0), and [tone] (0..100).
     */
    fun hctToColor(hue: Float, chroma: Float, tone: Float): Color {
        val clampedTone = tone.coerceIn(0f, 100f)
        if (clampedTone <= 0.01f) return Color.Black
        if (clampedTone >= 99.99f) return Color.White

        var currentChroma = chroma.coerceAtLeast(0f)
        val rad = (hue * PI / 180.0).toFloat()
        val fy = (clampedTone + 16f) / 116f

        // Search for highest in-gamut chroma up to requested chroma
        for (attempt in 0..8) {
            val a = currentChroma * cos(rad)
            val bVal = currentChroma * sin(rad)

            val fx = a / 500f + fy
            val fz = fy - bVal / 200f

            val x = labInvF(fx) * 0.95047f
            val y = labInvF(fy) * 1.00000f
            val z = labInvF(fz) * 1.08883f

            val rLin = 3.2404542f * x - 1.5371385f * y - 0.4985314f * z
            val gLin = -0.9692660f * x + 1.8760108f * y + 0.0415560f * z
            val bLin = 0.0556434f * x - 0.2040259f * y + 1.0572252f * z

            val inGamut = rLin in -0.001f..1.001f && gLin in -0.001f..1.001f && bLin in -0.001f..1.001f
            if (inGamut || currentChroma < 1f) {
                return Color(
                    red = linearToSrgb(rLin).coerceIn(0f, 1f),
                    green = linearToSrgb(gLin).coerceIn(0f, 1f),
                    blue = linearToSrgb(bLin).coerceIn(0f, 1f)
                )
            }
            currentChroma *= 0.8f
        }

        // Fallback to pure tone grayscale if chroma search fails
        val grayLinear = labInvF(fy)
        val graySrgb = linearToSrgb(grayLinear).coerceIn(0f, 1f)
        return Color(graySrgb, graySrgb, graySrgb)
    }

    private const val TONE_DARK_LOWEST = 4f
    private const val TONE_DARK_SURFACE = 6f
    private const val TONE_DARK_LOW = 10f
    private const val TONE_DARK_CONTAINER = 12f
    private const val TONE_DARK_HIGH = 17f
    private const val TONE_DARK_HIGHEST = 22f

    private const val TONE_AMOLED_LOW = 4f
    private const val TONE_AMOLED_CONTAINER = 8f
    private const val TONE_AMOLED_HIGH = 12f
    private const val TONE_AMOLED_HIGHEST = 16f

    private const val TONE_LIGHT_LOWEST = 100f
    private const val TONE_LIGHT_SURFACE = 98f
    private const val TONE_LIGHT_LOW = 96f
    private const val TONE_LIGHT_CONTAINER = 94f
    private const val TONE_LIGHT_HIGH = 92f
    private const val TONE_LIGHT_HIGHEST = 90f

    private const val STANDARD_NEUTRAL_HUE = 0f
    private const val STANDARD_NEUTRAL_CHROMA = 0f
    private const val STANDARD_NEUTRAL_VARIANT_HUE = 260f
    private const val STANDARD_NEUTRAL_VARIANT_CHROMA = 2f

    private data class TonalSurfacePalette(
        val background: Color,
        val surface: Color,
        val lowest: Color,
        val low: Color,
        val container: Color,
        val high: Color,
        val highest: Color
    )

    private fun buildTonalSurfacePalette(
        neutralTone: (Float) -> Color,
        isDark: Boolean,
        isAmoled: Boolean
    ): TonalSurfacePalette = if (isDark) {
        TonalSurfacePalette(
            background = if (isAmoled) Color.Black else neutralTone(TONE_DARK_SURFACE),
            surface = if (isAmoled) Color.Black else neutralTone(TONE_DARK_SURFACE),
            lowest = if (isAmoled) Color.Black else neutralTone(TONE_DARK_LOWEST),
            low = neutralTone(if (isAmoled) TONE_AMOLED_LOW else TONE_DARK_LOW),
            container = neutralTone(if (isAmoled) TONE_AMOLED_CONTAINER else TONE_DARK_CONTAINER),
            high = neutralTone(if (isAmoled) TONE_AMOLED_HIGH else TONE_DARK_HIGH),
            highest = neutralTone(if (isAmoled) TONE_AMOLED_HIGHEST else TONE_DARK_HIGHEST)
        )
    } else {
        TonalSurfacePalette(
            background = neutralTone(TONE_LIGHT_SURFACE),
            surface = neutralTone(TONE_LIGHT_SURFACE),
            lowest = neutralTone(TONE_LIGHT_LOWEST),
            low = neutralTone(TONE_LIGHT_LOW),
            container = neutralTone(TONE_LIGHT_CONTAINER),
            high = neutralTone(TONE_LIGHT_HIGH),
            highest = neutralTone(TONE_LIGHT_HIGHEST)
        )
    }

    /**
     * Creates a Material 3 Expressive [ColorScheme] dynamically generated from [seedColor].
     */
    fun createExpressiveScheme(seedColor: Color, isDark: Boolean, isAmoled: Boolean = false): ColorScheme {
        val hct = colorToHct(seedColor)
        val primaryHue = hct.hue
        val primaryChroma = max(hct.chroma, 42f)

        // Expressive Secondary: Same hue, restrained chroma
        val secondaryHue = primaryHue
        val secondaryChroma = 16f

        // Expressive Tertiary: Complementary / rotated hue (+120 degrees) with expressive chroma
        val tertiaryHue = (primaryHue + 120f) % 360f
        val tertiaryChroma = 32f

        // Harmonious Neutral surfaces with subtle hue tint
        val neutralHue = primaryHue
        val neutralChroma = 5f

        // Neutral Variant with slightly higher chroma for borders/subtle states
        val neutralVariantChroma = 8f

        // Standard M3 Error palette
        val errorHue = 25f
        val errorChroma = 84f

        fun primaryTone(t: Float) = hctToColor(primaryHue, primaryChroma, t)
        fun secondaryTone(t: Float) = hctToColor(secondaryHue, secondaryChroma, t)
        fun tertiaryTone(t: Float) = hctToColor(tertiaryHue, tertiaryChroma, t)
        fun neutralTone(t: Float) = hctToColor(neutralHue, neutralChroma, t)
        fun neutralVariantTone(t: Float) = hctToColor(neutralHue, neutralVariantChroma, t)
        fun errorTone(t: Float) = hctToColor(errorHue, errorChroma, t)

        val surfaces = buildTonalSurfacePalette(::neutralTone, isDark = isDark, isAmoled = isAmoled)

        return if (isDark) {
            darkColorScheme(
                primary = primaryTone(80f),
                onPrimary = primaryTone(20f),
                primaryContainer = primaryTone(30f),
                onPrimaryContainer = primaryTone(90f),
                secondary = secondaryTone(80f),
                onSecondary = secondaryTone(20f),
                secondaryContainer = secondaryTone(30f),
                onSecondaryContainer = secondaryTone(90f),
                tertiary = tertiaryTone(80f),
                onTertiary = tertiaryTone(20f),
                tertiaryContainer = tertiaryTone(30f),
                onTertiaryContainer = tertiaryTone(90f),
                background = surfaces.background,
                onBackground = neutralTone(90f),
                surface = surfaces.surface,
                onSurface = neutralTone(90f),
                surfaceVariant = neutralVariantTone(30f),
                onSurfaceVariant = neutralVariantTone(80f),
                surfaceContainerLowest = surfaces.lowest,
                surfaceContainerLow = surfaces.low,
                surfaceContainer = surfaces.container,
                surfaceContainerHigh = surfaces.high,
                surfaceContainerHighest = surfaces.highest,
                outline = neutralVariantTone(60f),
                outlineVariant = neutralVariantTone(30f),
                error = errorTone(80f),
                onError = errorTone(20f),
                errorContainer = errorTone(30f),
                onErrorContainer = errorTone(90f),
                surfaceTint = Color.Transparent
            )
        } else {
            lightColorScheme(
                primary = primaryTone(40f),
                onPrimary = primaryTone(100f),
                primaryContainer = primaryTone(90f),
                onPrimaryContainer = primaryTone(10f),
                secondary = secondaryTone(40f),
                onSecondary = secondaryTone(100f),
                secondaryContainer = secondaryTone(90f),
                onSecondaryContainer = secondaryTone(10f),
                tertiary = tertiaryTone(40f),
                onTertiary = tertiaryTone(100f),
                tertiaryContainer = tertiaryTone(90f),
                onTertiaryContainer = tertiaryTone(10f),
                background = surfaces.background,
                onBackground = neutralTone(10f),
                surface = surfaces.surface,
                onSurface = neutralTone(10f),
                surfaceVariant = neutralVariantTone(90f),
                onSurfaceVariant = neutralVariantTone(30f),
                surfaceContainerLowest = surfaces.lowest,
                surfaceContainerLow = surfaces.low,
                surfaceContainer = surfaces.container,
                surfaceContainerHigh = surfaces.high,
                surfaceContainerHighest = surfaces.highest,
                outline = neutralVariantTone(50f),
                outlineVariant = neutralVariantTone(80f),
                error = errorTone(40f),
                onError = errorTone(100f),
                errorContainer = errorTone(90f),
                onErrorContainer = errorTone(10f),
                surfaceTint = Color.Transparent
            )
        }
    }

    /**
     * Creates a standard neutral scheme with [seedColor] applied only to primary accents,
     * deriving all surface elevation containers from the same perceptual HCT tonal ladder
     * as [createExpressiveScheme] (using achromatic neutral chroma) so Light, Dark, and AMOLED
     * maintain mathematically consistent tonal contrast.
     */
    fun createStandardScheme(seedColor: Color, isDark: Boolean, isAmoled: Boolean = false): ColorScheme {
        val hct = colorToHct(seedColor)
        val primaryHue = hct.hue
        val primaryChroma = max(hct.chroma, 40f)

        fun primaryTone(t: Float) = hctToColor(primaryHue, primaryChroma, t)
        fun neutralTone(t: Float) = hctToColor(STANDARD_NEUTRAL_HUE, STANDARD_NEUTRAL_CHROMA, t)
        fun neutralVariantTone(t: Float) = hctToColor(STANDARD_NEUTRAL_VARIANT_HUE, STANDARD_NEUTRAL_VARIANT_CHROMA, t)

        val surfaces = buildTonalSurfacePalette(::neutralTone, isDark = isDark, isAmoled = isAmoled)

        return if (isDark) {
            darkColorScheme(
                primary = primaryTone(80f),
                onPrimary = primaryTone(20f),
                primaryContainer = primaryTone(30f),
                onPrimaryContainer = primaryTone(90f),
                secondary = neutralTone(80f),
                onSecondary = neutralTone(20f),
                secondaryContainer = neutralTone(30f),
                onSecondaryContainer = neutralTone(90f),
                tertiary = Color(0xFFD4C2D8),
                onTertiary = Color(0xFF382C3D),
                tertiaryContainer = Color(0xFF504355),
                onTertiaryContainer = Color(0xFFF1DEEE),
                background = surfaces.background,
                onBackground = neutralTone(90f),
                surface = surfaces.surface,
                onSurface = neutralTone(90f),
                surfaceVariant = neutralVariantTone(30f),
                onSurfaceVariant = neutralVariantTone(80f),
                surfaceContainerLowest = surfaces.lowest,
                surfaceContainerLow = surfaces.low,
                surfaceContainer = surfaces.container,
                surfaceContainerHigh = surfaces.high,
                surfaceContainerHighest = surfaces.highest,
                outline = neutralVariantTone(60f),
                outlineVariant = neutralVariantTone(30f),
                error = Color(0xFFFFB4AB),
                onError = Color(0xFF690005),
                errorContainer = Color(0xFF93000A),
                onErrorContainer = Color(0xFFFFDAD6),
                surfaceTint = Color.Transparent
            )
        } else {
            lightColorScheme(
                primary = primaryTone(40f),
                onPrimary = primaryTone(100f),
                primaryContainer = primaryTone(90f),
                onPrimaryContainer = primaryTone(10f),
                secondary = neutralTone(40f),
                onSecondary = neutralTone(100f),
                secondaryContainer = neutralTone(90f),
                onSecondaryContainer = neutralTone(10f),
                tertiary = Color(0xFF6B5B70),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFF4DDF8),
                onTertiaryContainer = Color(0xFF25182B),
                background = surfaces.background,
                onBackground = neutralTone(10f),
                surface = surfaces.surface,
                onSurface = neutralTone(10f),
                surfaceVariant = neutralVariantTone(90f),
                onSurfaceVariant = neutralVariantTone(30f),
                surfaceContainerLowest = surfaces.lowest,
                surfaceContainerLow = surfaces.low,
                surfaceContainer = surfaces.container,
                surfaceContainerHigh = surfaces.high,
                surfaceContainerHighest = surfaces.highest,
                outline = neutralVariantTone(50f),
                outlineVariant = neutralVariantTone(80f),
                error = Color(0xFFBA1A1A),
                onError = Color.White,
                errorContainer = Color(0xFFFFDAD6),
                onErrorContainer = Color(0xFF410002),
                surfaceTint = Color.Transparent
            )
        }
    }

    private fun srgbToLinear(c: Float): Float =
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

    private fun linearToSrgb(c: Float): Float =
        if (c <= 0.0031308f) 12.92f * c else 1.055f * c.pow(1f / 2.4f) - 0.055f

    private fun labF(t: Float): Float =
        if (t > 0.00885645f) t.pow(1f / 3f) else 7.787037f * t + 16f / 116f

    private fun labInvF(t: Float): Float =
        if (t > 0.2068966f) t.pow(3f) else (t - 16f / 116f) / 7.787037f
}
