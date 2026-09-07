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

        return if (isDark) {
            val baseBackground = if (isAmoled) Color.Black else neutralTone(6f)
            val baseSurface = if (isAmoled) Color.Black else neutralTone(6f)
            val lowest = if (isAmoled) Color.Black else neutralTone(4f)
            val low = if (isAmoled) neutralTone(4f) else neutralTone(10f)
            val container = if (isAmoled) neutralTone(8f) else neutralTone(12f)
            val high = if (isAmoled) neutralTone(12f) else neutralTone(17f)
            val highest = if (isAmoled) neutralTone(16f) else neutralTone(22f)

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
                background = baseBackground,
                onBackground = neutralTone(90f),
                surface = baseSurface,
                onSurface = neutralTone(90f),
                surfaceVariant = neutralVariantTone(30f),
                onSurfaceVariant = neutralVariantTone(80f),
                surfaceContainerLowest = lowest,
                surfaceContainerLow = low,
                surfaceContainer = container,
                surfaceContainerHigh = high,
                surfaceContainerHighest = highest,
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
                background = neutralTone(98f),
                onBackground = neutralTone(10f),
                surface = neutralTone(98f),
                onSurface = neutralTone(10f),
                surfaceVariant = neutralVariantTone(90f),
                onSurfaceVariant = neutralVariantTone(30f),
                surfaceContainerLowest = neutralTone(100f),
                surfaceContainerLow = neutralTone(96f),
                surfaceContainer = neutralTone(94f),
                surfaceContainerHigh = neutralTone(92f),
                surfaceContainerHighest = neutralTone(90f),
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
     * maintaining clean, uncolored surfaces when dynamic theme accenting is disabled.
     */
    fun createStandardScheme(seedColor: Color, isDark: Boolean, isAmoled: Boolean = false): ColorScheme {
        val hct = colorToHct(seedColor)
        val primaryHue = hct.hue
        val primaryChroma = max(hct.chroma, 40f)

        fun primaryTone(t: Float) = hctToColor(primaryHue, primaryChroma, t)

        return if (isDark) {
            val baseBackground = if (isAmoled) Color.Black else Color(0xFF131314)
            val baseSurface = if (isAmoled) Color.Black else Color(0xFF131314)
            val lowest = if (isAmoled) Color.Black else Color(0xFF0E0E0F)
            val low = if (isAmoled) Color(0xFF0F0F10) else Color(0xFF1B1B1C)
            val container = if (isAmoled) Color(0xFF181819) else Color(0xFF202021)
            val high = if (isAmoled) Color(0xFF222223) else Color(0xFF2A2A2B)
            val highest = if (isAmoled) Color(0xFF2D2D2E) else Color(0xFF353536)

            darkColorScheme(
                primary = primaryTone(80f),
                onPrimary = primaryTone(20f),
                primaryContainer = primaryTone(30f),
                onPrimaryContainer = primaryTone(90f),
                secondary = Color(0xFFC7C6CA),
                onSecondary = Color(0xFF303033),
                secondaryContainer = Color(0xFF47464A),
                onSecondaryContainer = Color(0xFFE4E2E6),
                tertiary = Color(0xFFD4C2D8),
                onTertiary = Color(0xFF382C3D),
                tertiaryContainer = Color(0xFF504355),
                onTertiaryContainer = Color(0xFFF1DEEE),
                background = baseBackground,
                onBackground = Color(0xFFE4E2E6),
                surface = baseSurface,
                onSurface = Color(0xFFE4E2E6),
                surfaceVariant = Color(0xFF45464F),
                onSurfaceVariant = Color(0xFFC5C6D0),
                surfaceContainerLowest = lowest,
                surfaceContainerLow = low,
                surfaceContainer = container,
                surfaceContainerHigh = high,
                surfaceContainerHighest = highest,
                outline = Color(0xFF8F909A),
                outlineVariant = Color(0xFF45464F),
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
                secondary = Color(0xFF5E5E62),
                onSecondary = Color.White,
                secondaryContainer = Color(0xFFE3E2E6),
                onSecondaryContainer = Color(0xFF1B1B1F),
                tertiary = Color(0xFF6B5B70),
                onTertiary = Color.White,
                tertiaryContainer = Color(0xFFF4DDF8),
                onTertiaryContainer = Color(0xFF25182B),
                background = Color(0xFFF9F9FC),
                onBackground = Color(0xFF1A1C1E),
                surface = Color(0xFFF9F9FC),
                onSurface = Color(0xFF1A1C1E),
                surfaceVariant = Color(0xFFE1E2EC),
                onSurfaceVariant = Color(0xFF44474F),
                surfaceContainerLowest = Color.White,
                surfaceContainerLow = Color(0xFFF3F3F6),
                surfaceContainer = Color(0xFFEDEDF0),
                surfaceContainerHigh = Color(0xFFE7E8EB),
                surfaceContainerHighest = Color(0xFFE2E2E5),
                outline = Color(0xFF757780),
                outlineVariant = Color(0xFFC5C6D0),
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
