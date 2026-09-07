package org.wip.plugintoolkit.core.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ThemeTest {

    @Test
    fun testSpacingGridScale() {
        val spacing = Spacing()
        assertEquals(0.dp, spacing.none)
        assertEquals(2.dp, spacing.xxs)
        assertEquals(4.dp, spacing.xs)
        assertEquals(8.dp, spacing.sm)
        assertEquals(16.dp, spacing.md)
        assertEquals(24.dp, spacing.lg)
        assertEquals(32.dp, spacing.xl)
        assertEquals(48.dp, spacing.xxl)
        assertEquals(64.dp, spacing.xxxl)

        // Backward-compatible aliases
        assertEquals(spacing.sm, spacing.small)
        assertEquals(spacing.md, spacing.medium)
        assertEquals(spacing.lg, spacing.large)
        assertEquals(spacing.xl, spacing.extraLarge)
    }

    @Test
    fun testDesktopTypographyContract() {
        assertNotNull(DesktopTypography.headlineLarge)
        assertEquals(28.sp, DesktopTypography.headlineLarge.fontSize)
        assertEquals(24.sp, DesktopTypography.headlineMedium.fontSize)
        assertEquals(18.sp, DesktopTypography.titleLarge.fontSize)
        assertEquals(14.sp, DesktopTypography.bodyLarge.fontSize)
        assertEquals(13.sp, DesktopTypography.bodyMedium.fontSize)
        assertEquals(12.sp, DesktopTypography.bodySmall.fontSize)
        assertEquals(10.sp, DesktopTypography.labelSmall.fontSize)
    }

    @Test
    fun testCustomColorsWCAGContrastCompliance() {
        fun contrastRatio(fg: Color, bg: Color): Float {
            val l1 = maxOf(fg.luminance(), bg.luminance())
            val l2 = minOf(fg.luminance(), bg.luminance())
            return (l1 + 0.05f) / (l2 + 0.05f)
        }

        // Light palette
        val lightColors = CustomColors()
        val successRatioLight = contrastRatio(lightColors.onSuccess, lightColors.success)
        assertTrue(successRatioLight >= 4.5f, "Light success contrast ratio: $successRatioLight")

        val warningRatioLight = contrastRatio(lightColors.onWarning, lightColors.warning)
        assertTrue(warningRatioLight >= 4.5f, "Light warning contrast ratio: $warningRatioLight")

        val infoRatioLight = contrastRatio(lightColors.onInfo, lightColors.info)
        assertTrue(infoRatioLight >= 4.5f, "Light info contrast ratio: $infoRatioLight")

        // Dark palette
        val darkColors = CustomColors(
            success = Color(0xFF81C784),
            onSuccess = Color(0xFF00390B),
            warning = Color(0xFFFFB74D),
            onWarning = Color(0xFF4E2600),
            info = Color(0xFF64B5F6),
            onInfo = Color(0xFF003258)
        )
        val successRatioDark = contrastRatio(darkColors.onSuccess, darkColors.success)
        assertTrue(successRatioDark >= 4.5f, "Dark success contrast ratio: $successRatioDark")

        val warningRatioDark = contrastRatio(darkColors.onWarning, darkColors.warning)
        assertTrue(warningRatioDark >= 4.5f, "Dark warning contrast ratio: $warningRatioDark")

        val infoRatioDark = contrastRatio(darkColors.onInfo, darkColors.info)
        assertTrue(infoRatioDark >= 4.5f, "Dark info contrast ratio: $infoRatioDark")
    }

    @Test
    fun testAmoledContainerHierarchy() {
        // In AMOLED mode, container levels strictly ascend in luminance from black canvas
        val lowest = Color(0xFF050505)
        val low = Color(0xFF0D0D0D)
        val container = Color(0xFF141414)
        val high = Color(0xFF1F1F1F)
        val highest = Color(0xFF2A2A2A)

        assertTrue(Color.Black.luminance() < lowest.luminance())
        assertTrue(lowest.luminance() < low.luminance())
        assertTrue(low.luminance() < container.luminance())
        assertTrue(container.luminance() < high.luminance())
        assertTrue(high.luminance() < highest.luminance())

        // Verify spacing and dimensions default initialization
        val dimensions = Dimensions()
        assertEquals(80.dp, dimensions.sidebarCollapsedWidth)
        assertEquals(250.dp, dimensions.sidebarExpandedWidth)
        assertEquals(2.dp, dimensions.cardElevation)
    }

    @Test
    fun testOpacityTokens() {
        @Suppress("DEPRECATION")
        val opacity = Opacity()
        assertEquals(0.0f, opacity.transparent)
        assertEquals(1.0f, opacity.full)
        assertEquals(0.3f, opacity.glassBackground)
    }

    @Test
    fun testColorEngineHctConversions() {
        // Red
        val redHct = ColorEngine.colorToHct(Color.Red)
        assertTrue(redHct.hue in 25f..45f || redHct.hue in 350f..360f, "Red hue: ${redHct.hue}")
        assertTrue(redHct.chroma > 50f, "Red chroma: ${redHct.chroma}")

        // Reconstruct pure tone
        val darkTone = ColorEngine.hctToColor(redHct.hue, redHct.chroma, 20f)
        val lightTone = ColorEngine.hctToColor(redHct.hue, redHct.chroma, 80f)
        assertTrue(darkTone.luminance() < lightTone.luminance())
    }

    @Test
    fun testColorEngineExpressiveSchemeContrast() {
        fun contrastRatio(fg: Color, bg: Color): Float {
            val l1 = maxOf(fg.luminance(), bg.luminance())
            val l2 = minOf(fg.luminance(), bg.luminance())
            return (l1 + 0.05f) / (l2 + 0.05f)
        }

        // Test multiple seed colors (Red, Blue, Purple, Green)
        val seeds = listOf(Color(0xFFE91E63), Color(0xFF007ACC), Color(0xFF6200EE), Color(0xFF2E7D32))

        for (seed in seeds) {
            val lightScheme = ColorEngine.createExpressiveScheme(seed, isDark = false)
            val darkScheme = ColorEngine.createExpressiveScheme(seed, isDark = true)

            // Primary / OnPrimary contrast in Light mode
            val lightPrimaryRatio = contrastRatio(lightScheme.onPrimary, lightScheme.primary)
            assertTrue(lightPrimaryRatio >= 4.0f, "Light primary contrast for seed $seed: $lightPrimaryRatio")

            // PrimaryContainer / OnPrimaryContainer contrast in Light mode
            val lightContainerRatio = contrastRatio(lightScheme.onPrimaryContainer, lightScheme.primaryContainer)
            assertTrue(lightContainerRatio >= 4.0f, "Light container contrast for seed $seed: $lightContainerRatio")

            // Primary / OnPrimary contrast in Dark mode
            val darkPrimaryRatio = contrastRatio(darkScheme.onPrimary, darkScheme.primary)
            assertTrue(darkPrimaryRatio >= 4.0f, "Dark primary contrast for seed $seed: $darkPrimaryRatio")

            // PrimaryContainer / OnPrimaryContainer contrast in Dark mode
            val darkContainerRatio = contrastRatio(darkScheme.onPrimaryContainer, darkScheme.primaryContainer)
            assertTrue(darkContainerRatio >= 4.0f, "Dark container contrast for seed $seed: $darkContainerRatio")
        }
    }

    @Test
    fun testColorEngineAmoledHierarchy() {
        val amoledScheme = ColorEngine.createExpressiveScheme(Color(0xFF6200EE), isDark = true, isAmoled = true)
        assertEquals(Color.Black, amoledScheme.background)
        assertEquals(Color.Black, amoledScheme.surface)
        assertEquals(Color.Black, amoledScheme.surfaceContainerLowest)

        assertTrue(amoledScheme.surfaceContainerLowest.luminance() <= amoledScheme.surfaceContainerLow.luminance())
        assertTrue(amoledScheme.surfaceContainerLow.luminance() < amoledScheme.surfaceContainer.luminance())
        assertTrue(amoledScheme.surfaceContainer.luminance() < amoledScheme.surfaceContainerHigh.luminance())
        assertTrue(amoledScheme.surfaceContainerHigh.luminance() < amoledScheme.surfaceContainerHighest.luminance())
    }

    @Test
    fun testAppearanceSettingsUseAccentInTheme() {
        val defaultSettings = org.wip.plugintoolkit.features.settings.model.AppearanceSettings()
        kotlin.test.assertFalse(defaultSettings.useAccentInTheme)

        val updated = defaultSettings.copy(useAccentInTheme = true)
        assertTrue(updated.useAccentInTheme)
    }

    @Test
    fun testMenuDimensionsAndTokens() {
        val dimensions = Dimensions()
        assertEquals(180.dp, dimensions.menuMinWidth)
        assertEquals(4.dp, dimensions.menuElevation)
        assertEquals(40.dp, dimensions.standardButtonHeight)
    }
}
